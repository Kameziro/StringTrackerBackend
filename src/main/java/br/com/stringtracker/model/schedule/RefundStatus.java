package br.com.stringtracker.model.schedule;

public enum RefundStatus {
    NONE,
    PENDING,
    DONE,
    FAILED,
    /** Reembolso que falhou e o admin do clube resolveu por fora do provedor. */
    RESOLVED_MANUALLY
}
