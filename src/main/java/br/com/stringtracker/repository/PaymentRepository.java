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

    public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
        return find("providerPaymentId = ?1 and active = true", providerPaymentId).firstResultOptional();
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
