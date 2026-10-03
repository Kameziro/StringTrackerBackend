package br.com.stringtracker.model.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Notificação do provedor já recebida. A chave primária garante que cada evento é processado uma vez. */
@Entity
@Table(name = "payment_events")
@Getter
@NoArgsConstructor
public class PaymentEvent {

    @Id
    @Column(name = "provider_event_id", length = 64)
    private String providerEventId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public static PaymentEvent create(String providerEventId) {
        PaymentEvent event = new PaymentEvent();
        event.providerEventId = providerEventId;
        event.receivedAt = Instant.now();
        return event;
    }
}
