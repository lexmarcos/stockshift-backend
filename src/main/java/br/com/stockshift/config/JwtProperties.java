package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {
    private String secret;
    private Long accessExpiration; // in milliseconds
    private Long refreshExpiration; // in milliseconds

    // Grace window (ms) during which a just-rotated refresh token is still
    // accepted, so concurrent refresh requests sharing the same cookie do not
    // invalidate each other and force a logout. See RefreshTokenService.
    private Long refreshRotationGracePeriod; // in milliseconds
}
