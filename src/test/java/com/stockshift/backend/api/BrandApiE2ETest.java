package com.stockshift.backend.api;

import com.stockshift.backend.api.dto.auth.LoginRequest;
import com.stockshift.backend.api.dto.auth.LoginResponse;
import com.stockshift.backend.api.dto.brand.BrandResponse;
import com.stockshift.backend.api.dto.brand.CreateBrandRequest;
import com.stockshift.backend.api.dto.brand.UpdateBrandRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
class BrandApiE2ETest {

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
    void shouldExecuteBrandLifecycleSuccessfully() {
        String accessToken = authenticateTestUser();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String brandName = "E2E Brand " + uniqueSuffix;
        String brandDescription = "Initial description for " + uniqueSuffix;

        CreateBrandRequest createRequest = new CreateBrandRequest(
                brandName,
                brandDescription
        );

        HttpEntity<CreateBrandRequest> createEntity = new HttpEntity<>(createRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<BrandResponse> createResponse = restTemplate.postForEntity(
                url("/api/v1/brands"),
                createEntity,
                BrandResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BrandResponse createdBrand = createResponse.getBody();
        assertThat(createdBrand).isNotNull();
        assertThat(createdBrand.getId()).isNotNull();
        assertThat(createdBrand.getName()).isEqualTo(brandName);
        assertThat(createdBrand.getDescription()).isEqualTo(brandDescription);
        assertThat(createdBrand.getActive()).isTrue();

        UUID brandId = createdBrand.getId();

        BrandResponse fetchedById = getBrandById(accessToken, brandId);
        assertThat(fetchedById.getName()).isEqualTo(brandName);
        assertThat(fetchedById.getDescription()).isEqualTo(brandDescription);
        assertThat(fetchedById.getActive()).isTrue();

        BrandResponse fetchedByName = getBrandByName(accessToken, brandName);
        assertThat(fetchedByName.getId()).isEqualTo(brandId);

        String updatedName = brandName + " Updated";
        String updatedDescription = "Updated description for " + uniqueSuffix;

        UpdateBrandRequest updateRequest = new UpdateBrandRequest(
                updatedName,
                updatedDescription,
                null
        );

        HttpEntity<UpdateBrandRequest> updateEntity = new HttpEntity<>(updateRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<BrandResponse> updateResponse = restTemplate.exchange(
                url("/api/v1/brands/" + brandId),
                HttpMethod.PUT,
                updateEntity,
                BrandResponse.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        BrandResponse updatedBrand = updateResponse.getBody();
        assertThat(updatedBrand).isNotNull();
        assertThat(updatedBrand.getName()).isEqualTo(updatedName);
        assertThat(updatedBrand.getDescription()).isEqualTo(updatedDescription);
        assertThat(updatedBrand.getActive()).isTrue();

        BrandResponse fetchedAfterUpdate = getBrandByName(accessToken, updatedName);
        assertThat(fetchedAfterUpdate.getName()).isEqualTo(updatedName);
        assertThat(fetchedAfterUpdate.getDescription()).isEqualTo(updatedDescription);

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/api/v1/brands/" + brandId),
                HttpMethod.DELETE,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        BrandResponse inactiveBrand = getBrandById(accessToken, brandId);
        assertThat(inactiveBrand.getActive()).isFalse();

        ResponseEntity<BrandResponse> activateResponse = restTemplate.exchange(
                url("/api/v1/brands/" + brandId + "/activate"),
                HttpMethod.PATCH,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                BrandResponse.class
        );

        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        BrandResponse reactivatedBrand = activateResponse.getBody();
        assertThat(reactivatedBrand).isNotNull();
        assertThat(reactivatedBrand.getActive()).isTrue();
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

    private BrandResponse getBrandById(String accessToken, UUID brandId) {
        ResponseEntity<BrandResponse> response = restTemplate.exchange(
                url("/api/v1/brands/" + brandId),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                BrandResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        BrandResponse body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    private BrandResponse getBrandByName(String accessToken, String name) {
        ResponseEntity<BrandResponse> response = restTemplate.exchange(
                url("/api/v1/brands/name/" + name),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                BrandResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        BrandResponse body = response.getBody();
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
