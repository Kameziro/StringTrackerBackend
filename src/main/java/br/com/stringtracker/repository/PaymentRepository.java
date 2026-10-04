package br.com.stringtracker.repository;

import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.model.schedule.RefundStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PaymentRepository implements PanacheRepository<Payment> {

    // O filtro `active` é explícito: o Hibernate não aplica filtros herdados da BaseEntity (@MappedSuperclass).
    public Optional<Payment> findByBookingId(long bookingId) {
        return find("booking.id = ?1 and active = true", bookingId).firstResultOptional();
    }

    /**
     * Reserva dona do pagamento. Devolve só o id: quem muda o pagamento trava primeiro a linha da reserva
     * (ela é o lock do agregado) e só então carrega o pagamento, para não ler um estado já ultrapassado.
     */
    public Optional<Long> findBookingIdByProviderPaymentId(String providerPaymentId) {
        return getEntityManager().createQuery(
                        "select p.booking.id from Payment p where p.providerPaymentId = :id and p.active = true",
                        Long.class)
                .setParameter("id", providerPaymentId)
                .getResultStream().findFirst();
    }

    public Optional<Long> findBookingIdByPaymentId(long paymentId) {
        return getEntityManager().createQuery(
                        "select p.booking.id from Payment p where p.id = :id and p.active = true", Long.class)
                .setParameter("id", paymentId)
                .getResultStream().findFirst();
    }

    /** Ids dos pagamentos com reembolso pendente cuja próxima tentativa já venceu. */
    public List<Long> listIdsWithRefundDue(Instant now) {
        return getEntityManager().createQuery("""
                        select p.id from Payment p
                        where p.active = true and p.booking.refundStatus = :pending
                          and p.nextRefundAt is not null and p.nextRefundAt <= :now
                        order by p.nextRefundAt
                        """, Long.class)
                .setParameter("pending", RefundStatus.PENDING)
                .setParameter("now", now)
                .getResultList();
    }
}
