package br.com.stockshift.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private Long expiresIn; // in milliseconds
    private UUID userId;
    private String email;
    private String fullName;

    /**
     * Indicates if captcha is required for the next login attempt.
     * Set to true when multiple login attempts are detected from the same IP.
     */
    @Builder.Default
    private Boolean requiresCaptcha = false;
}
