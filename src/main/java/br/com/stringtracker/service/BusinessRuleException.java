package br.com.stringtracker.service;

/** Pedido válido que fere uma regra de negócio (limite, pré-condição). Vira HTTP 422. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
