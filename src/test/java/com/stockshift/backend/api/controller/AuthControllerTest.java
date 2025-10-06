package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.auth.LoginRequest;
import com.stockshift.backend.api.dto.auth.LoginResponse;
import com.stockshift.backend.api.dto.auth.LogoutRequest;
import com.stockshift.backend.api.dto.auth.RefreshTokenRequest;
import com.stockshift.backend.application.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void loginShouldReturnResponseFromService() {
        LoginRequest request = new LoginRequest("user", "pass");
        LoginResponse expected = new LoginResponse();
        when(authService.login(request)).thenReturn(expected);

        ResponseEntity<LoginResponse> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void refreshTokenShouldReturnResponseFromService() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("token");
        LoginResponse expected = new LoginResponse();
        when(authService.refreshToken(request)).thenReturn(expected);

        ResponseEntity<LoginResponse> response = authController.refreshToken(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void logoutShouldInvokeServiceWhenRefreshTokenPresent() {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refresh-token");

        ResponseEntity<Void> response = authController.logout(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).logout("refresh-token");
    }

    @Test
    void logoutShouldSkipServiceWhenTokenAbsent() {
        LogoutRequest request = new LogoutRequest();

        ResponseEntity<Void> response = authController.logout(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService, never()).logout(null);
    }
}
