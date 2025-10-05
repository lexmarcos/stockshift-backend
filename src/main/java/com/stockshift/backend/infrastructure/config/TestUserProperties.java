package com.stockshift.backend.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.test-user")
@Data
public class TestUserProperties {
    
    /**
     * Enable/disable the automatic creation of test user for development
     */
    private boolean enabled = false;
    
    /**
     * Username for the test user
     */
    private String username = "testuser";
    
    /**
     * Email for the test user
     */
    private String email = "test@stockshift.com";
    
    /**
     * Password for the test user
     */
    private String password = "testpass123";
    
    /**
     * Role for the test user
     */
    private String role = "ADMIN";
    
    /**
     * Fixed access token for testing (long-lived in development)
     */
    private String accessToken = "dev-access-token-12345678901234567890123456789012345678901234567890";
    
    /**
     * Fixed refresh token for testing (long-lived in development)
     */
    private String refreshToken = "dev-refresh-token-12345678901234567890123456789012345678901234567890";
}
