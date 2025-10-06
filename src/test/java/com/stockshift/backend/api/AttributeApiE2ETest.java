package com.stockshift.backend.api;

import com.stockshift.backend.api.dto.attribute.AttributeDefinitionResponse;
import com.stockshift.backend.api.dto.attribute.AttributeValueResponse;
import com.stockshift.backend.api.dto.attribute.CreateAttributeDefinitionRequest;
import com.stockshift.backend.api.dto.attribute.CreateAttributeValueRequest;
import com.stockshift.backend.api.dto.attribute.UpdateAttributeDefinitionRequest;
import com.stockshift.backend.api.dto.attribute.UpdateAttributeValueRequest;
import com.stockshift.backend.api.dto.auth.LoginRequest;
import com.stockshift.backend.api.dto.auth.LoginResponse;
import com.stockshift.backend.domain.attribute.AttributeStatus;
import com.stockshift.backend.domain.attribute.AttributeType;
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
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
class AttributeApiE2ETest {

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
    void shouldExecuteAttributeLifecycleSuccessfully() {
        String accessToken = authenticateTestUser();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String definitionName = "E2E Attribute " + uniqueSuffix;
        String definitionCode = "attr_" + uniqueSuffix;

        CreateAttributeDefinitionRequest createDefinitionRequest = new CreateAttributeDefinitionRequest();
        createDefinitionRequest.setName(definitionName);
        createDefinitionRequest.setCode(definitionCode);
        createDefinitionRequest.setType(AttributeType.ENUM);
        createDefinitionRequest.setDescription("Definition description for " + uniqueSuffix);
        createDefinitionRequest.setIsVariantDefining(true);
        createDefinitionRequest.setIsRequired(false);
        createDefinitionRequest.setSortOrder(2);

        HttpEntity<CreateAttributeDefinitionRequest> createDefinitionEntity = new HttpEntity<>(
                createDefinitionRequest,
                authorizedJsonHeaders(accessToken)
        );

        ResponseEntity<AttributeDefinitionResponse> createDefinitionResponse = restTemplate.postForEntity(
                url("/api/v1/attributes/definitions"),
                createDefinitionEntity,
                AttributeDefinitionResponse.class
        );

        assertThat(createDefinitionResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AttributeDefinitionResponse createdDefinition = createDefinitionResponse.getBody();
        assertThat(createdDefinition).isNotNull();
        assertThat(createdDefinition.getId()).isNotNull();
        assertThat(createdDefinition.getName()).isEqualTo(definitionName);
        assertThat(createdDefinition.getCode()).isEqualTo(definitionCode.toUpperCase());
        assertThat(createdDefinition.getType()).isEqualTo(AttributeType.ENUM);
        assertThat(createdDefinition.getStatus()).isEqualTo(AttributeStatus.ACTIVE);

        UUID definitionId = createdDefinition.getId();

        AttributeDefinitionResponse fetchedById = getDefinitionById(accessToken, definitionId);
        assertThat(fetchedById.getName()).isEqualTo(definitionName);
        assertThat(fetchedById.getCode()).isEqualTo(definitionCode.toUpperCase());
        assertThat(fetchedById.getStatus()).isEqualTo(AttributeStatus.ACTIVE);

        AttributeDefinitionResponse fetchedByCode = getDefinitionByCode(accessToken, definitionCode);
        assertThat(fetchedByCode.getId()).isEqualTo(definitionId);

        String valueCode = "val_" + uniqueSuffix;
        CreateAttributeValueRequest createValueRequest = new CreateAttributeValueRequest(
                "Value " + uniqueSuffix,
                valueCode,
                "Value description for " + uniqueSuffix,
                "#A1B2C3"
        );

        HttpEntity<CreateAttributeValueRequest> createValueEntity = new HttpEntity<>(
                createValueRequest,
                authorizedJsonHeaders(accessToken)
        );

        ResponseEntity<AttributeValueResponse> createValueResponse = restTemplate.postForEntity(
                url("/api/v1/attributes/definitions/" + definitionId + "/values"),
                createValueEntity,
                AttributeValueResponse.class
        );

        assertThat(createValueResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AttributeValueResponse createdValue = createValueResponse.getBody();
        assertThat(createdValue).isNotNull();
        assertThat(createdValue.getId()).isNotNull();
        assertThat(createdValue.getDefinitionId()).isEqualTo(definitionId);
        assertThat(createdValue.getCode()).isEqualTo(valueCode.toUpperCase());
        assertThat(createdValue.getStatus()).isEqualTo(AttributeStatus.ACTIVE);

        UUID valueId = createdValue.getId();

        AttributeValueResponse fetchedValue = getValueById(accessToken, valueId);
        assertThat(fetchedValue.getValue()).isEqualTo("Value " + uniqueSuffix);
        assertThat(fetchedValue.getStatus()).isEqualTo(AttributeStatus.ACTIVE);

        UpdateAttributeValueRequest updateValueRequest = new UpdateAttributeValueRequest(
                "Updated Value " + uniqueSuffix,
                "Updated value description for " + uniqueSuffix,
                "#D4E5F6"
        );

        HttpEntity<UpdateAttributeValueRequest> updateValueEntity = new HttpEntity<>(
                updateValueRequest,
                authorizedJsonHeaders(accessToken)
        );

        ResponseEntity<AttributeValueResponse> updateValueResponse = restTemplate.exchange(
                url("/api/v1/attributes/values/" + valueId),
                HttpMethod.PUT,
                updateValueEntity,
                AttributeValueResponse.class
        );

        assertThat(updateValueResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AttributeValueResponse updatedValue = updateValueResponse.getBody();
        assertThat(updatedValue).isNotNull();
        assertThat(updatedValue.getValue()).isEqualTo("Updated Value " + uniqueSuffix);
        assertThat(updatedValue.getDescription()).isEqualTo("Updated value description for " + uniqueSuffix);
        assertThat(updatedValue.getSwatchHex()).isEqualTo("#D4E5F6");
        assertThat(updatedValue.getStatus()).isEqualTo(AttributeStatus.ACTIVE);

        ResponseEntity<Void> deactivateValueResponse = restTemplate.exchange(
                url("/api/v1/attributes/values/" + valueId),
                HttpMethod.DELETE,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                Void.class
        );

        assertThat(deactivateValueResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        AttributeValueResponse inactiveValue = getValueById(accessToken, valueId);
        assertThat(inactiveValue.getStatus()).isEqualTo(AttributeStatus.INACTIVE);

        ResponseEntity<AttributeValueResponse> reactivateValueResponse = restTemplate.exchange(
                url("/api/v1/attributes/values/" + valueId + "/activate"),
                HttpMethod.PATCH,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                AttributeValueResponse.class
        );

        assertThat(reactivateValueResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AttributeValueResponse reactivatedValue = reactivateValueResponse.getBody();
        assertThat(reactivatedValue).isNotNull();
        assertThat(reactivatedValue.getStatus()).isEqualTo(AttributeStatus.ACTIVE);

        List<UUID> updatedCategories = List.of(UUID.randomUUID());
        UpdateAttributeDefinitionRequest updateDefinitionRequest = new UpdateAttributeDefinitionRequest(
                definitionName + " Updated",
                "Updated definition description for " + uniqueSuffix,
                false,
                true,
                updatedCategories,
                5
        );

        HttpEntity<UpdateAttributeDefinitionRequest> updateDefinitionEntity = new HttpEntity<>(
                updateDefinitionRequest,
                authorizedJsonHeaders(accessToken)
        );

        ResponseEntity<AttributeDefinitionResponse> updateDefinitionResponse = restTemplate.exchange(
                url("/api/v1/attributes/definitions/" + definitionId),
                HttpMethod.PUT,
                updateDefinitionEntity,
                AttributeDefinitionResponse.class
        );

        assertThat(updateDefinitionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AttributeDefinitionResponse updatedDefinition = updateDefinitionResponse.getBody();
        assertThat(updatedDefinition).isNotNull();
        assertThat(updatedDefinition.getName()).isEqualTo(definitionName + " Updated");
        assertThat(updatedDefinition.getDescription()).isEqualTo("Updated definition description for " + uniqueSuffix);
        assertThat(updatedDefinition.getIsVariantDefining()).isFalse();
        assertThat(updatedDefinition.getIsRequired()).isTrue();
        assertThat(updatedDefinition.getSortOrder()).isEqualTo(5);
        assertThat(updatedDefinition.getApplicableCategoryIds()).containsExactlyElementsOf(updatedCategories);

        ResponseEntity<Void> deactivateDefinitionResponse = restTemplate.exchange(
                url("/api/v1/attributes/definitions/" + definitionId),
                HttpMethod.DELETE,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                Void.class
        );

        assertThat(deactivateDefinitionResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        AttributeDefinitionResponse inactiveDefinition = getDefinitionById(accessToken, definitionId);
        assertThat(inactiveDefinition.getStatus()).isEqualTo(AttributeStatus.INACTIVE);

        AttributeValueResponse valueAfterDefinitionDeactivation = getValueById(accessToken, valueId);
        assertThat(valueAfterDefinitionDeactivation.getStatus()).isEqualTo(AttributeStatus.INACTIVE);

        ResponseEntity<AttributeDefinitionResponse> reactivateDefinitionResponse = restTemplate.exchange(
                url("/api/v1/attributes/definitions/" + definitionId + "/activate"),
                HttpMethod.PATCH,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                AttributeDefinitionResponse.class
        );

        assertThat(reactivateDefinitionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AttributeDefinitionResponse reactivatedDefinition = reactivateDefinitionResponse.getBody();
        assertThat(reactivatedDefinition).isNotNull();
        assertThat(reactivatedDefinition.getStatus()).isEqualTo(AttributeStatus.ACTIVE);

        AttributeValueResponse valueAfterDefinitionReactivation = getValueById(accessToken, valueId);
        assertThat(valueAfterDefinitionReactivation.getStatus()).isEqualTo(AttributeStatus.INACTIVE);

        ResponseEntity<AttributeValueResponse> reactivateValueAfterDefinitionResponse = restTemplate.exchange(
                url("/api/v1/attributes/values/" + valueId + "/activate"),
                HttpMethod.PATCH,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                AttributeValueResponse.class
        );

        assertThat(reactivateValueAfterDefinitionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AttributeValueResponse reactivatedValueAfterDefinition = reactivateValueAfterDefinitionResponse.getBody();
        assertThat(reactivatedValueAfterDefinition).isNotNull();
        assertThat(reactivatedValueAfterDefinition.getStatus()).isEqualTo(AttributeStatus.ACTIVE);
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

    private AttributeDefinitionResponse getDefinitionById(String accessToken, UUID definitionId) {
        ResponseEntity<AttributeDefinitionResponse> response = restTemplate.exchange(
                url("/api/v1/attributes/definitions/" + definitionId),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                AttributeDefinitionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AttributeDefinitionResponse body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    private AttributeDefinitionResponse getDefinitionByCode(String accessToken, String code) {
        ResponseEntity<AttributeDefinitionResponse> response = restTemplate.exchange(
                url("/api/v1/attributes/definitions/code/" + code),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                AttributeDefinitionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AttributeDefinitionResponse body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    private AttributeValueResponse getValueById(String accessToken, UUID valueId) {
        ResponseEntity<AttributeValueResponse> response = restTemplate.exchange(
                url("/api/v1/attributes/values/" + valueId),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                AttributeValueResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AttributeValueResponse body = response.getBody();
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
