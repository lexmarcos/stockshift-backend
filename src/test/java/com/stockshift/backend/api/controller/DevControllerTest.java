package com.stockshift.backend.api.controller;

import com.stockshift.backend.application.service.TestUserService;
import com.stockshift.backend.application.service.TestUserService.TestUserCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevControllerTest {

    @Mock
    private TestUserService testUserService;

    @Mock
    private Environment environment;

    @InjectMocks
    private DevController devController;

    @Test
    void shouldReturnCredentialsWhenInDevProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        TestUserCredentials credentials = TestUserCredentials.builder()
                .username("testuser")
                .password("password")
                .accessToken("access")
                .refreshToken("refresh")
                .build();
        when(testUserService.getTestUserCredentials()).thenReturn(credentials);

        ResponseEntity<?> response = devController.getTestUserCredentials();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(credentials);
    }

    @Test
    void shouldReturnNotFoundWhenNotInDevProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ResponseEntity<?> response = devController.getTestUserCredentials();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
