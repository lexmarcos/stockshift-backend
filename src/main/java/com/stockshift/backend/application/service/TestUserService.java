package com.stockshift.backend.application.service;

import com.stockshift.backend.domain.user.RefreshToken;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import com.stockshift.backend.infrastructure.config.TestUserProperties;
import com.stockshift.backend.infrastructure.repository.RefreshTokenRepository;
import com.stockshift.backend.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestUserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TestUserProperties testUserProperties;
    private final Environment environment;

    /**
     * Creates a test user if it doesn't exist and if we're in development environment
     */
    @Transactional
    public void createTestUserIfNeeded() {
        if (!isTestUserEnabled()) {
            return;
        }

        log.info("Checking if test user exists...");
        
        Optional<User> existingUser = userRepository.findByUsername(testUserProperties.getUsername());
        if (existingUser.isPresent()) {
            log.info("Test user already exists: {}", testUserProperties.getUsername());
            ensureTestUserHasFixedTokens(existingUser.get());
            return;
        }

        log.info("Creating test user for development environment...");
        User testUser = createTestUser();
        createFixedRefreshToken(testUser);
        
        log.info("Test user created successfully: {}", testUser.getUsername());
        log.info("Use these credentials for testing:");
        log.info("  Username: {}", testUserProperties.getUsername());
        log.info("  Password: {}", testUserProperties.getPassword());
        log.info("  Access Token: {}", testUserProperties.getAccessToken());
        log.info("  Refresh Token: {}", testUserProperties.getRefreshToken());
    }

    /**
     * Get the test user credentials for API testing
     */
    public TestUserCredentials getTestUserCredentials() {
        if (!isTestUserEnabled()) {
            throw new IllegalStateException("Test user is not enabled in this environment");
        }
        
        return TestUserCredentials.builder()
                .username(testUserProperties.getUsername())
                .password(testUserProperties.getPassword())
                .accessToken(testUserProperties.getAccessToken())
                .refreshToken(testUserProperties.getRefreshToken())
                .build();
    }

    private boolean isTestUserEnabled() {
        boolean isDevelopment = isDevelopmentEnvironment();
        boolean isEnabled = testUserProperties.isEnabled();
        
        if (!isDevelopment) {
            log.debug("Test user creation skipped - not in development environment");
            return false;
        }
        
        if (!isEnabled) {
            log.debug("Test user creation skipped - disabled in configuration");
            return false;
        }
        
        return true;
    }

    private boolean isDevelopmentEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        
        // Check if 'dev' profile is active
        for (String profile : activeProfiles) {
            if ("dev".equals(profile)) {
                return true;
            }
        }
        
        // If no profiles are set, assume development (default behavior)
        if (activeProfiles.length == 0) {
            return true;
        }
        
        return false;
    }

    private User createTestUser() {
        User testUser = new User();
        testUser.setUsername(testUserProperties.getUsername());
        testUser.setEmail(testUserProperties.getEmail());
        testUser.setPassword(passwordEncoder.encode(testUserProperties.getPassword()));
        testUser.setRole(UserRole.valueOf(testUserProperties.getRole()));
        testUser.setActive(true);
        
        return userRepository.save(testUser);
    }

    private void createFixedRefreshToken(User user) {
        // Delete existing refresh tokens for this user
        refreshTokenRepository.deleteByUser(user);
        
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(testUserProperties.getRefreshToken());
        refreshToken.setUser(user);
        // Set expiration to 1 year in the future for development
        refreshToken.setExpiresAt(OffsetDateTime.now().plusDays(365));
        refreshToken.setRevoked(false);
        
        refreshTokenRepository.save(refreshToken);
    }

    private void ensureTestUserHasFixedTokens(User user) {
        Optional<RefreshToken> existingToken = refreshTokenRepository.findByToken(testUserProperties.getRefreshToken());
        
        if (existingToken.isEmpty() || !existingToken.get().getUser().getId().equals(user.getId())) {
            log.info("Creating fixed refresh token for existing test user...");
            createFixedRefreshToken(user);
        }
    }

    @lombok.Builder
    @lombok.Data
    public static class TestUserCredentials {
        private String username;
        private String password;
        private String accessToken;
        private String refreshToken;
    }
}
