package br.com.stringtracker.service;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.repository.ClubAdminRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Pushes da agenda de professores (NOTIF-01..04). O aluno recebe a confirmação e o cancelamento da aula; o professor
 * recebe a reserva, o cancelamento e o bloqueio de dia feitos pelo aluno ou pelo admin do clube (o que o próprio
 * professor faz não o avisa, e desvincular o professor também não); admins do clube nunca recebem (NOTIF-04). O push
 * sai depois do commit, para não avisar de algo que acabou desfeito, e uma falha dele é só
 * registrada: o estado da aula não muda. Aluno convidado, sem conta, não tem push. As mensagens são montadas na hora
 * da chamada, enquanto a reserva ainda está carregada, e só o envio fica para depois do commit.
 */
@ApplicationScoped
public class LessonNotifier {

    private static final Logger LOG = Logger.getLogger(LessonNotifier.class);
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", PT_BR).withZone(ClockProducer.ZONE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM", PT_BR);

    @Inject
    ExpoPushService push;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    TransactionSynchronizationRegistry transactions;

    /** Pix pago a tempo ou reserva manual com conta (NOTIF-01). */
    public void bookingConfirmed(Booking booking) {
        LessonSlot slot = booking.getLessonSlot();
        toStudent(booking, "lesson_confirmed", "Aula confirmada", "%s · %s · %s".formatted(
                slot.getCoach().getUser().getName(), when(slot), slot.getClubCoach().getClub().getName()));
    }

    /** Aula cancelada pelo clube ou pelo professor, dizendo se houve reembolso (NOTIF-02). */
    public void bookingCancelledByStaff(Booking booking) {
        LessonSlot slot = booking.getLessonSlot();
        toStudent(booking, "lesson_cancelled", "Aula cancelada", "Sua aula com %s de %s foi cancelada.%s".formatted(
                slot.getCoach().getUser().getName(), when(slot), refundSentence(booking)));
    }

    /** Pix aprovado depois que a vaga foi liberada: o valor é devolvido (BOOK-04). */
    public void latePaymentRefunded(Booking booking) {
        String outcome = booking.getRefundStatus() == RefundStatus.DONE ? "valor devolvido" : "valor será devolvido";
        toStudent(booking, "lesson_refunded", "Pagamento fora do prazo", "Pagamento recebido após o prazo: " + outcome);
    }

    /** Reembolso que tinha ficado pendente e acabou de ser concluído numa nova tentativa. */
    public void refundCompleted(Booking booking) {
        toStudent(booking, "lesson_refunded", "Reembolso concluído",
                "O valor de %s da aula de %s foi devolvido.".formatted(
                        money(booking.getRefundAmountCents()), when(booking.getLessonSlot())));
    }

    /** Pix do aluno confirmado: o professor ganhou uma aula (NOTIF-03). */
    public void coachNewBooking(Booking booking) {
        toCoach(booking, "coach_booking", "Nova aula reservada", bookingSummary(booking));
    }

    /** O admin do clube registrou uma reserva manual numa aula do professor (NOTIF-03). */
    public void coachNewBookingByClub(Booking booking) {
        toCoach(booking, "coach_booking", "Nova aula reservada pelo clube", bookingSummary(booking));
    }

    /** O aluno cancelou a aula (NOTIF-03). */
    public void coachBookingCancelled(Booking booking) {
        toCoach(booking, "coach_booking_cancelled", "Aula cancelada pelo aluno", "%s cancelou a aula de %s.".formatted(
                booking.studentName(), when(booking.getLessonSlot())));
    }

    /** O admin do clube cancelou uma aula confirmada do professor (NOTIF-03). */
    public void coachBookingCancelledByClub(Booking booking) {
        toCoach(booking, "coach_booking_cancelled", "Aula cancelada pelo clube",
                "A aula de %s com %s foi cancelada pelo clube.".formatted(
                        when(booking.getLessonSlot()), booking.studentName()));
    }

    /**
     * O admin do clube bloqueou um dia do professor (NOTIF-03): um único aviso por bloqueio, com quantas reservas
     * ele cancelou, em vez de um push por reserva.
     */
    public void coachDayBlockedByClub(Club club, Coach coach, LocalDate day, int cancelledBookings) {
        String cancelled = switch (cancelledBookings) {
            case 0 -> "";
            case 1 -> " 1 reserva foi cancelada.";
            default -> " %d reservas foram canceladas.".formatted(cancelledBookings);
        };
        send(club.getId(), coach.getUser(), Map.of("type", "coach_day_blocked", "date", day.toString()),
                "Dia bloqueado pelo clube",
                "%s bloqueou %s na sua agenda.%s".formatted(club.getName(), DAY.format(day), cancelled));
    }

    private void toStudent(Booking booking, String type, String title, String body) {
        send(clubIdOf(booking), booking.getStudentUser(), bookingData(booking, type), title, body);
    }

    private void toCoach(Booking booking, String type, String title, String body) {
        send(clubIdOf(booking), booking.getLessonSlot().getCoach().getUser(), bookingData(booking, type), title, body);
    }

    private void send(long clubId, User recipient, Map<String, Object> data, String title, String body) {
        try {
            if (recipient == null || clubAdminRepository.isAdmin(clubId, recipient.getId())) {
                return;
            }
            long recipientId = recipient.getId();
            afterCommit(() -> push.notifyUser(recipientId, title, body, data));
        } catch (RuntimeException e) {
            LOG.warnf(e, "Push %s não foi preparado", data);
        }
    }

    private static Map<String, Object> bookingData(Booking booking, String type) {
        return Map.of("type", type, "bookingId", String.valueOf(booking.getId()));
    }

    private static long clubIdOf(Booking booking) {
        return booking.getLessonSlot().getClubCoach().getClub().getId();
    }

    private static String bookingSummary(Booking booking) {
        LessonSlot slot = booking.getLessonSlot();
        return "%s · %s · %s".formatted(booking.studentName(), when(slot), slot.getClubCoach().getClub().getName());
    }

    /** Executa o envio depois do commit; sem transação em curso, executa na hora. */
    private void afterCommit(Runnable send) {
        if (transactions.getTransactionStatus() != Status.STATUS_ACTIVE) {
            runQuietly(send);
            return;
        }
        transactions.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    runQuietly(send);
                }
            }
        });
    }

    private static void runQuietly(Runnable send) {
        try {
            send.run();
        } catch (RuntimeException e) {
            LOG.warn("Push de aula não foi enviado", e);
        }
    }

    private static String when(LessonSlot slot) {
        return WHEN.format(slot.getStartsAt());
    }

    private static String refundSentence(Booking booking) {
        return switch (booking.getRefundStatus()) {
            case DONE -> " O valor de %s foi devolvido.".formatted(money(booking.getRefundAmountCents()));
            case PENDING, FAILED -> " O reembolso de %s está sendo processado.".formatted(
                    money(booking.getRefundAmountCents()));
            case NONE, RESOLVED_MANUALLY -> "";
        };
    }

    private static String money(Long cents) {
        return String.format(PT_BR, "R$ %,.2f", (cents == null ? 0 : cents) / 100.0);
    }
}
