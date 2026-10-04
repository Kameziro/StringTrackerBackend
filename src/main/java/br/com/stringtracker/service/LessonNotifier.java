package br.com.stringtracker.service;

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

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Pushes da agenda de professores (NOTIF-01..04). O aluno recebe a confirmação e o cancelamento da aula, o professor
 * recebe a reserva e o cancelamento feitos pelo aluno, e admins do clube nunca recebem (NOTIF-04). O push sai depois
 * do commit, para não avisar de algo que acabou desfeito, e uma falha dele é só registrada: o estado da aula não muda.
 * Aluno convidado, sem conta, não tem push. As mensagens são montadas na hora da chamada, enquanto a reserva ainda
 * está carregada, e só o envio fica para depois do commit.
 */
@ApplicationScoped
public class LessonNotifier {

    private static final Logger LOG = Logger.getLogger(LessonNotifier.class);
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", PT_BR).withZone(ClockProducer.ZONE);

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
        LessonSlot slot = booking.getLessonSlot();
        toCoach(booking, "coach_booking", "Nova aula reservada", "%s · %s · %s".formatted(
                booking.studentName(), when(slot), slot.getClubCoach().getClub().getName()));
    }

    /** O aluno cancelou a aula (NOTIF-03). */
    public void coachBookingCancelled(Booking booking) {
        toCoach(booking, "coach_booking_cancelled", "Aula cancelada pelo aluno", "%s cancelou a aula de %s.".formatted(
                booking.studentName(), when(booking.getLessonSlot())));
    }

    private void toStudent(Booking booking, String type, String title, String body) {
        send(booking, booking.getStudentUser(), type, title, body);
    }

    private void toCoach(Booking booking, String type, String title, String body) {
        send(booking, booking.getLessonSlot().getCoach().getUser(), type, title, body);
    }

    private void send(Booking booking, User recipient, String type, String title, String body) {
        try {
            if (recipient == null || isAdminOfTheClub(booking, recipient)) {
                return;
            }
            long recipientId = recipient.getId();
            Map<String, Object> data = Map.of("type", type, "bookingId", String.valueOf(booking.getId()));
            afterCommit(() -> push.notifyUser(recipientId, title, body, data));
        } catch (RuntimeException e) {
            LOG.warnf(e, "Push %s da reserva %d não foi preparado", type, booking.getId());
        }
    }

    private boolean isAdminOfTheClub(Booking booking, User user) {
        long clubId = booking.getLessonSlot().getClubCoach().getClub().getId();
        return clubAdminRepository.isAdmin(clubId, user.getId());
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
            case NONE -> "";
        };
    }

    private static String money(Long cents) {
        return String.format(PT_BR, "R$ %,.2f", (cents == null ? 0 : cents) / 100.0);
    }
}
