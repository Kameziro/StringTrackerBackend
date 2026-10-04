package br.com.stringtracker.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateCoachOffersRequest(
        @NotNull Boolean singles,
        @NotNull Boolean doubles,
        @NotNull Boolean group
) {
}
