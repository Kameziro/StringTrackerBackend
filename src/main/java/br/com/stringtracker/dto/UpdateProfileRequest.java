package br.com.stringtracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotNull
    @Min(1)
    @Max(8)
    private Integer category;

    @NotNull
    private Long cityId;

    @NotNull
    private Boolean availableToday;
}
