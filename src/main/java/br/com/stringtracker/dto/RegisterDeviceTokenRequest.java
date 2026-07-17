package br.com.stringtracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDeviceTokenRequest {

    @NotBlank
    private String expoPushToken;

    @NotBlank
    private String platform;
}
