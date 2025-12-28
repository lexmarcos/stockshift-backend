package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.product.CategoryRequest;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class CategoryControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant testTenant;
    private User testUser;

    @BeforeEach
    void setUpTestData() {
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Category Test Tenant", "22222222000102");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "category@test.com");

        TenantContext.setTenantId(testTenant.getId());
    }

    @Test
    @WithMockUser(username = "category@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCreateCategory() throws Exception {
        CategoryRequest request = CategoryRequest.builder()
                .name("Electronics")
                .build();

        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Electronics"));
    }

    @Test
    @WithMockUser(username = "category@test.com", authorities = {"ROLE_ADMIN"})
    void shouldGetCategoryById() throws Exception {
        Category category = TestDataFactory.createCategory(categoryRepository,
                testTenant.getId(), "Books");

        mockMvc.perform(get("/api/categories/{id}", category.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(category.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Books"));
    }

    @Test
    @WithMockUser(username = "category@test.com", authorities = {"ROLE_ADMIN"})
    void shouldListAllCategories() throws Exception {
        TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Category 1");
        TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Category 2");

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
