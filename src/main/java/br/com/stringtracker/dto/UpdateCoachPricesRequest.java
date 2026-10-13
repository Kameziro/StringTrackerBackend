package br.com.stringtracker.dto;

import jakarta.validation.constraints.Positive;

/** Preços em centavos; nulo significa sem preço para o tipo, e valor informado precisa ser maior que zero. */
public record UpdateCoachPricesRequest(
        @Positive Long singlesCents,
        @Positive Long doublesCents,
        @Positive Long groupCents
) {
}
