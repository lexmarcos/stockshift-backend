package com.stockshift.backend.infrastructure.config;

import com.stockshift.backend.application.service.TestUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestUserInitializer implements ApplicationRunner {

    private final TestUserService testUserService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            testUserService.createTestUserIfNeeded();
        } catch (Exception e) {
            log.error("Failed to create test user", e);
        }
    }
}
