package br.com.stringtracker.service.job;

import br.com.stringtracker.repository.ScheduleBlockRepository;
import br.com.stringtracker.service.ScheduleService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/** Mantém a janela de 28 dias de cada bloco ativo aberta: roda todo dia às 03:00 em São Paulo (AGND-02). */
@ApplicationScoped
public class SlotGenerationJob {

    private static final Logger LOG = Logger.getLogger(SlotGenerationJob.class);

    @Inject
    ScheduleService scheduleService;

    @Inject
    ScheduleBlockRepository scheduleBlockRepository;

    @Scheduled(cron = "0 0 3 * * ?", timeZone = "America/Sao_Paulo",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP, identity = "slot-generation")
    void scheduled() {
        extendAll();
    }

    /** Estende cada bloco na sua própria transação: um bloco que falha não impede os outros. Devolve quantos horários criou. */
    public int extendAll() {
        int created = 0;
        for (long blockId : scheduleBlockRepository.listGeneratingIds()) {
            try {
                created += scheduleService.extendBlock(blockId);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Bloco %d não foi estendido", blockId);
            }
        }
        return created;
    }
}
