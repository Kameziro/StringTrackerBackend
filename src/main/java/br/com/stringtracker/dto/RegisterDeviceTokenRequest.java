package br.com.stringtracker.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterDeviceTokenRequest(
        @NotBlank String expoPushToken,
        @NotBlank String platform
) {
}
