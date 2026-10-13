package br.com.stringtracker.repository;

import br.com.stringtracker.model.schedule.PaymentEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PaymentEventRepository implements PanacheRepositoryBase<PaymentEvent, String> {
}
