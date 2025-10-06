package com.stockshift.backend.api;

import com.stockshift.backend.api.dto.auth.LoginRequest;
import com.stockshift.backend.api.dto.auth.LoginResponse;
import com.stockshift.backend.api.dto.category.CategoryResponse;
import com.stockshift.backend.api.dto.category.CreateCategoryRequest;
import com.stockshift.backend.api.dto.category.UpdateCategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
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
class CategoryApiE2ETest {

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
    @SuppressWarnings("null")
    void shouldExecuteCategoryLifecycleSuccessfully() {
        String accessToken = authenticateTestUser();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String categoryName = "E2E Category " + uniqueSuffix;
        String categoryDescription = "Initial description for " + uniqueSuffix;

        CreateCategoryRequest createRequest = new CreateCategoryRequest(
                categoryName,
                categoryDescription,
                null
        );

        HttpEntity<CreateCategoryRequest> createEntity = new HttpEntity<>(createRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<CategoryResponse> createResponse = restTemplate.postForEntity(
                url("/api/v1/categories"),
                createEntity,
                CategoryResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CategoryResponse createdCategory = createResponse.getBody();
        assertThat(createdCategory).isNotNull();
        assertThat(createdCategory.getId()).isNotNull();
        assertThat(createdCategory.getName()).isEqualTo(categoryName);
        assertThat(createdCategory.getDescription()).isEqualTo(categoryDescription);
        assertThat(createdCategory.getActive()).isTrue();
        assertThat(createdCategory.getParentId()).isNull();
        assertThat(createdCategory.getLevel()).isEqualTo(0);

        UUID categoryId = createdCategory.getId();

        CategoryResponse fetchedById = getCategoryById(accessToken, categoryId);
        assertThat(fetchedById.getName()).isEqualTo(categoryName);
        assertThat(fetchedById.getDescription()).isEqualTo(categoryDescription);
        assertThat(fetchedById.getActive()).isTrue();

        CategoryResponse fetchedByName = getCategoryByName(accessToken, categoryName);
        assertThat(fetchedByName.getId()).isEqualTo(categoryId);

        String updatedName = categoryName + " Updated";
        String updatedDescription = "Updated description for " + uniqueSuffix;

        UpdateCategoryRequest updateRequest = new UpdateCategoryRequest(
                updatedName,
                updatedDescription,
                null
        );

        HttpEntity<UpdateCategoryRequest> updateEntity = new HttpEntity<>(updateRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<CategoryResponse> updateResponse = restTemplate.exchange(
                url("/api/v1/categories/" + categoryId),
                HttpMethod.PUT,
                updateEntity,
                CategoryResponse.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        CategoryResponse updatedCategory = updateResponse.getBody();
        assertThat(updatedCategory).isNotNull();
        assertThat(updatedCategory.getName()).isEqualTo(updatedName);
        assertThat(updatedCategory.getDescription()).isEqualTo(updatedDescription);
        assertThat(updatedCategory.getActive()).isTrue();

        CategoryResponse fetchedAfterUpdate = getCategoryByName(accessToken, updatedName);
        assertThat(fetchedAfterUpdate.getName()).isEqualTo(updatedName);
        assertThat(fetchedAfterUpdate.getDescription()).isEqualTo(updatedDescription);

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/api/v1/categories/" + categoryId),
                HttpMethod.DELETE,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        CategoryResponse inactiveCategory = getCategoryById(accessToken, categoryId);
        assertThat(inactiveCategory.getActive()).isFalse();

        ResponseEntity<CategoryResponse> activateResponse = restTemplate.exchange(
                url("/api/v1/categories/" + categoryId + "/activate"),
                HttpMethod.PATCH,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                CategoryResponse.class
        );

        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        CategoryResponse reactivatedCategory = activateResponse.getBody();
        assertThat(reactivatedCategory).isNotNull();
        assertThat(reactivatedCategory.getActive()).isTrue();
    }

    @Test
    @SuppressWarnings("null")
    void shouldManageSubcategoriesSuccessfully() {
        String accessToken = authenticateTestUser();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        CreateCategoryRequest parentRequest = new CreateCategoryRequest(
                "Parent Category " + uniqueSuffix,
                "Parent description",
                null
        );

        HttpEntity<CreateCategoryRequest> parentEntity = new HttpEntity<>(parentRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<CategoryResponse> parentResponse = restTemplate.postForEntity(
                url("/api/v1/categories"),
                parentEntity,
                CategoryResponse.class
        );

        assertThat(parentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CategoryResponse parentCategory = parentResponse.getBody();
        assertThat(parentCategory).isNotNull();
        UUID parentId = parentCategory.getId();

        CreateCategoryRequest childRequest = new CreateCategoryRequest(
                "Child Category " + uniqueSuffix,
                "Child description",
                parentId
        );

        HttpEntity<CreateCategoryRequest> childEntity = new HttpEntity<>(childRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<CategoryResponse> childResponse = restTemplate.postForEntity(
                url("/api/v1/categories"),
                childEntity,
                CategoryResponse.class
        );

        assertThat(childResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CategoryResponse childCategory = childResponse.getBody();
        assertThat(childCategory).isNotNull();
        assertThat(childCategory.getParentId()).isEqualTo(parentId);
        assertThat(childCategory.getParentName()).isEqualTo(parentCategory.getName());
        assertThat(childCategory.getLevel()).isEqualTo(1);

        ResponseEntity<Map<String, Object>> subcategoriesResponse = restTemplate.exchange(
                url("/api/v1/categories/" + parentId + "/subcategories"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(subcategoriesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> subcategoriesPage = subcategoriesResponse.getBody();
        assertThat(subcategoriesPage).isNotNull();
        assertThat(subcategoriesPage.get("totalElements")).isEqualTo(1);

        ResponseEntity<List<CategoryResponse>> descendantsResponse = restTemplate.exchange(
                url("/api/v1/categories/" + parentId + "/descendants"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<List<CategoryResponse>>() {}
        );

        assertThat(descendantsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<CategoryResponse> descendants = descendantsResponse.getBody();
        assertThat(descendants).isNotNull();
        assertThat(descendants).isNotEmpty();
        assertThat(descendants).anyMatch(d -> d.getId().equals(childCategory.getId()));
        assertThat(descendants.stream()
                .filter(d -> d.getId().equals(childCategory.getId()))
                .findFirst()
                .orElseThrow()
                .getParentId()).isEqualTo(parentId);
    }

    @Test
    void shouldGetRootCategoriesSuccessfully() {
        String accessToken = authenticateTestUser();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        CreateCategoryRequest rootRequest = new CreateCategoryRequest(
                "Root Category " + uniqueSuffix,
                "Root description",
                null
        );

        HttpEntity<CreateCategoryRequest> rootEntity = new HttpEntity<>(rootRequest, authorizedJsonHeaders(accessToken));
        ResponseEntity<CategoryResponse> rootResponse = restTemplate.postForEntity(
                url("/api/v1/categories"),
                rootEntity,
                CategoryResponse.class
        );

        assertThat(rootResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> rootCategoriesResponse = restTemplate.exchange(
                url("/api/v1/categories/root"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(rootCategoriesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> rootCategoriesPage = rootCategoriesResponse.getBody();
        assertThat(rootCategoriesPage).isNotNull();
        assertThat(rootCategoriesPage.get("totalElements")).isNotNull();
    }

    @Test
    void shouldGetAllCategoriesWithPagination() {
        String accessToken = authenticateTestUser();

        ResponseEntity<Map<String, Object>> allCategoriesResponse = restTemplate.exchange(
                url("/api/v1/categories?page=0&size=10"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(allCategoriesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> categoriesPage = allCategoriesResponse.getBody();
        assertThat(categoriesPage).isNotNull();
        assertThat(categoriesPage).containsKeys("content", "totalElements", "totalPages", "size", "number");
    }

    @Test
    void shouldGetOnlyActiveCategoriesWhenRequested() {
        String accessToken = authenticateTestUser();

        ResponseEntity<Map<String, Object>> activeCategoriesResponse = restTemplate.exchange(
                url("/api/v1/categories?onlyActive=true"),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(activeCategoriesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> activeCategoriesPage = activeCategoriesResponse.getBody();
        assertThat(activeCategoriesPage).isNotNull();
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

    private CategoryResponse getCategoryById(String accessToken, UUID categoryId) {
        ResponseEntity<CategoryResponse> response = restTemplate.exchange(
                url("/api/v1/categories/" + categoryId),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                CategoryResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CategoryResponse body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    private CategoryResponse getCategoryByName(String accessToken, String name) {
        ResponseEntity<CategoryResponse> response = restTemplate.exchange(
                url("/api/v1/categories/name/" + name),
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                CategoryResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CategoryResponse body = response.getBody();
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
