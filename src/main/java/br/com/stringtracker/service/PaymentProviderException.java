package br.com.stringtracker.service;

/** O provedor de pagamento recusou uma chamada (conexão da conta, Pix, reembolso) ou devolveu algo inesperado. Vira HTTP 502. */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
