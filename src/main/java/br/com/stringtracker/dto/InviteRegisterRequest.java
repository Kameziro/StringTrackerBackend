package br.com.stringtracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro na página do convite: o e-mail vem do convite, e a categoria é opcional. */
public record InviteRegisterRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Size(min = 6, max = 128) String password,
        @NotNull Long cityId,
        @Min(1) @Max(8) Integer category
) {
}
