package br.com.stringtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String refreshToken;
    private long refreshExpiresIn;

    public static LoginResponse bearer(
            String accessToken,
            long expiresIn,
            String refreshToken,
            long refreshExpiresIn
    ) {
        return new LoginResponse(
                accessToken,
                "Bearer",
                expiresIn,
                refreshToken,
                refreshExpiresIn
        );
    }
}
