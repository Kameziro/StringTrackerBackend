package br.com.stringtracker.dto;

import br.com.stringtracker.model.ClubPaymentStatus;

/** Situação da conta de recebimento; os tokens nunca fazem parte da resposta. */
public record PaymentAccountResponse(ClubPaymentStatus paymentStatus) {
}
