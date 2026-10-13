package br.com.stringtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Troca completa do perfil: endereço e WhatsApp em branco limpam o campo. */
public record UpdateClubProfileRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @Size(max = 255) String address,
        @Size(max = 30) String whatsapp
) {
}
