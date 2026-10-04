package br.com.stringtracker.service.payment;

import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.BookingService;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
import br.com.stringtracker.service.payment.PaymentGateway.ProviderPayment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

/**
 * Processa uma notificação do Mercado Pago já autenticada. O estado do pedido é sempre lido no provedor, nunca
 * do corpo do webhook. O evento é registrado na mesma transação que o efeito: se o processamento falhar, nada
 * fica registrado e a nova entrega do provedor é processada de verdade (BOOK-09).
 */
@ApplicationScoped
public class PaymentWebhookService {

    @Inject
    EntityManager entityManager;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentGateway gateway;

    @Inject
    BookingService bookingService;

    @Transactional
    public void handle(String eventId, String providerOrderId) {
        if (!recordEvent(eventId)) {
            return;
        }
        paymentRepository.findBookingIdByProviderPaymentId(providerOrderId).ifPresent(bookingId -> {
            // A reserva é o lock do agregado; o pagamento só é carregado depois de travá-la.
            Booking booking = bookingRepository.findById(bookingId, LockModeType.PESSIMISTIC_WRITE);
            Payment payment = paymentRepository.findByBookingId(bookingId).orElseThrow();
            ClubCredentials credentials = gateway.refreshIfNeeded(booking.getLessonSlot().getClubCoach().getClub());
            ProviderPayment remote = gateway.getOrder(credentials, providerOrderId);
            bookingService.applyPaymentStatus(payment, remote.status());
        });
    }

    /** Grava o id do evento; falso se ele já tinha sido recebido (a chave primária decide, mesmo com entregas simultâneas). */
    private boolean recordEvent(String eventId) {
        return entityManager.createNativeQuery("""
                        insert into payment_events (provider_event_id, received_at)
                        values (:id, current_timestamp)
                        on conflict do nothing
                        """)
                .setParameter("id", eventId)
                .executeUpdate() == 1;
    }
}
