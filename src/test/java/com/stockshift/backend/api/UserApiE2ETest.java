package com.stockshift.backend.api;

import com.stockshift.backend.api.dto.auth.LoginRequest;
import com.stockshift.backend.api.dto.auth.LoginResponse;
import com.stockshift.backend.api.dto.user.CreateUserRequest;
import com.stockshift.backend.api.dto.user.UpdateUserRequest;
import com.stockshift.backend.api.dto.user.UserResponse;
import com.stockshift.backend.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:stockshift-test-db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class UserApiE2ETest {

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testpass123";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void configureRestTemplate() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @Test
    void shouldExecuteUserLifecycleSuccessfully() {
        String accessToken = authenticateTestUser();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "e2e-user-" + uniqueSuffix;
        String email = username + "@example.com";

        CreateUserRequest createRequest = new CreateUserRequest(
                username,
                email,
                "InitialPass!123",
                UserRole.MANAGER
        );

        HttpEntity<CreateUserRequest> createEntity = new HttpEntity<>(createRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<UserResponse> createResponse = restTemplate.postForEntity(
                url("/api/v1/users"),
                createEntity,
                UserResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserResponse createdUser = createResponse.getBody();
        assertThat(createdUser).isNotNull();
        assertThat(createdUser.getId()).isNotNull();
        assertThat(createdUser.getUsername()).isEqualTo(username);
        assertThat(createdUser.getEmail()).isEqualTo(email);
        assertThat(createdUser.getRole()).isEqualTo(UserRole.MANAGER.name());
        assertThat(createdUser.getActive()).isTrue();

        UUID userId = createdUser.getId();

        UserResponse fetchedById = getUserById(accessToken, userId);
        assertThat(fetchedById.getUsername()).isEqualTo(username);
        assertThat(fetchedById.getEmail()).isEqualTo(email);
        assertThat(fetchedById.getRole()).isEqualTo(UserRole.MANAGER.name());
        assertThat(fetchedById.getActive()).isTrue();

        UpdateUserRequest updateRequest = new UpdateUserRequest();
        updateRequest.setEmail("updated-" + email);
        updateRequest.setPassword("UpdatedPass!123");
        updateRequest.setRole(UserRole.SELLER);

        HttpEntity<UpdateUserRequest> updateEntity = new HttpEntity<>(updateRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<UserResponse> updateResponse = restTemplate.exchange(
                url("/api/v1/users/" + userId),
                HttpMethod.PUT,
                updateEntity,
                UserResponse.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse updatedUser = updateResponse.getBody();
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getEmail()).isEqualTo("updated-" + email);
        assertThat(updatedUser.getRole()).isEqualTo(UserRole.SELLER.name());
        assertThat(updatedUser.getActive()).isTrue();

        ResponseEntity<Void> deactivateResponse = restTemplate.exchange(
                url("/api/v1/users/" + userId),
                HttpMethod.DELETE,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                Void.class
        );

        assertThat(deactivateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        UserResponse inactiveUser = getUserById(accessToken, userId);
        assertThat(inactiveUser.getActive()).isFalse();

        ResponseEntity<UserResponse> activateResponse = restTemplate.exchange(
                url("/api/v1/users/" + userId + "/activate"),
                HttpMethod.PATCH,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                UserResponse.class
        );

        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse reactivatedUser = activateResponse.getBody();
        assertThat(reactivatedUser).isNotNull();
        assertThat(reactivatedUser.getActive()).isTrue();
    }

    private String authenticateTestUser() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(
                new LoginRequest(TEST_USERNAME, TEST_PASSWORD),
                headers
        );

        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                url("/api/v1/auth/login"),
                loginEntity,
                LoginResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAccessToken()).isNotBlank();
        return body.getAccessToken();
    }

    private UserResponse getUserById(String accessToken, UUID userId) {
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/v1/users/" + userId),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                UserResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    private HttpHeaders authorizedJsonHeaders(String accessToken) {
        HttpHeaders headers = authorizedHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authorizedHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
