package br.com.stringtracker.service.job;

import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.payment.RefundService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.util.List;

/** Repete a cada 15 minutos os reembolsos pendentes que já venceram a espera (CANC-05). */
@ApplicationScoped
public class RefundRetryJob {

    private static final Logger LOG = Logger.getLogger(RefundRetryJob.class);

    @Inject
    RefundService refundService;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    Clock clock;

    @Scheduled(every = "15m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP, identity = "refund-retry")
    void scheduled() {
        retryDue();
    }

    /** Cada reembolso numa transação própria: um que falha não impede os outros. Devolve quantos tentou. */
    public int retryDue() {
        List<Long> due = paymentRepository.listIdsWithRefundDue(clock.instant());
        for (long paymentId : due) {
            try {
                refundService.retry(paymentId);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Reembolso do pagamento %d não pôde ser tentado", paymentId);
            }
        }
        return due.size();
    }
}
