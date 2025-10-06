package com.stockshift.backend.api.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestControllerTest {

    private final TestController testController = new TestController();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicEndpointShouldReturnMessage() {
        ResponseEntity<Map<String, String>> response = testController.publicEndpoint();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "This is a public endpoint");
    }

    @Test
    void authenticatedEndpointShouldIncludePrincipalDetails() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("user", "pass");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        ResponseEntity<Map<String, Object>> response = testController.authenticatedEndpoint();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("username", "user");
        assertThat(response.getBody()).containsEntry("message", "This is an authenticated endpoint");
    }

    @Test
    void adminEndpointShouldReturnMessage() {
        ResponseEntity<Map<String, String>> response = testController.adminEndpoint();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "This is an admin-only endpoint");
    }

    @Test
    void managerEndpointShouldReturnMessage() {
        ResponseEntity<Map<String, String>> response = testController.managerEndpoint();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "This is a manager endpoint");
    }
}
