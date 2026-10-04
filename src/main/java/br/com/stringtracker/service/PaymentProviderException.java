package br.com.stringtracker.service;

/** O provedor de pagamento recusou ou não respondeu a uma chamada de conexão da conta. Vira HTTP 502. */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
