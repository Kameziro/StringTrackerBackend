package br.com.stringtracker.service.job;

import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.service.BookingService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.util.List;

/** A cada 30 segundos libera as vagas seguradas cujo prazo de 10 minutos venceu (BOOK-03). */
@ApplicationScoped
public class HoldExpiryJob {

    private static final Logger LOG = Logger.getLogger(HoldExpiryJob.class);

    @Inject
    BookingService bookingService;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    Clock clock;

    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP, identity = "hold-expiry")
    void scheduled() {
        expireDue();
    }

    /** Cada reserva numa transação própria: uma que falha não impede as outras. Devolve quantas examinou. */
    public int expireDue() {
        List<Long> due = bookingRepository.listExpiredHoldIds(clock.instant());
        for (long bookingId : due) {
            try {
                bookingService.expireHold(bookingId);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Reserva %d não pôde ser expirada", bookingId);
            }
        }
        return due.size();
    }
}
