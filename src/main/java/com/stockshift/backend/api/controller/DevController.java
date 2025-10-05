package com.stockshift.backend.api.controller;

import com.stockshift.backend.application.service.TestUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevController {

    private final TestUserService testUserService;
    private final Environment environment;

    /**
     * Get test user credentials for development/testing purposes
     * Only available in development environment
     */
    @GetMapping("/test-user")
    public ResponseEntity<?> getTestUserCredentials() {
        if (!isDevelopmentEnvironment()) {
            return ResponseEntity.notFound().build();
        }

        try {
            TestUserService.TestUserCredentials credentials = testUserService.getTestUserCredentials();
            return ResponseEntity.ok(credentials);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body("Test user is not enabled: " + e.getMessage());
        }
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
        return activeProfiles.length == 0;
    }
}
