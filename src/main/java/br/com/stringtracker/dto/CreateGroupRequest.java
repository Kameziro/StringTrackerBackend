package br.com.stringtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank @Size(min = 2, max = 120) String name
) {
}
