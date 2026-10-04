package br.com.stringtracker.service;

/** O provedor de pagamento não respondeu (timeout, falha de rede ou 5xx). Vira HTTP 503. */
public class PaymentProviderUnavailableException extends RuntimeException {

    public PaymentProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
