# Integration Tests Design - Phase 11 MVP

**Data:** 2025-12-28
**Objetivo:** Completar testes de integração para todos os controllers com foco em happy paths

---

## Estratégia Geral

### Princípios
1. **Um teste por endpoint principal** - Validar o happy path de cada operação CRUD
2. **Reutilizar setup** - Criar métodos helper para tenant/user/category/warehouse/product
3. **Focar em integrações** - Validar que controllers → services → repositories → database funcionam juntos
4. **Tenant context sempre** - Garantir que TenantContext está configurado em cada teste
5. **Autenticação simplificada** - Usar `@WithMockUser` para rapidez

### Controllers a Testar
1. **AuthenticationController** - Login, refresh, logout
2. **CategoryController** - Create, get by ID, list all
3. **WarehouseController** - Create, get by ID, list all
4. **BatchController** - Create, get by ID, find by warehouse, find expiring
5. **StockMovementController** - Create, execute, get by ID
6. **ReportController** - Dashboard, stock report

---

## Estrutura de Classes

```
src/test/java/br/com/stockshift/
├── BaseIntegrationTest.java (✅ já existe)
├── util/
│   └── TestDataFactory.java (NOVO - helpers para criar entidades)
└── controller/
    ├── ProductControllerIntegrationTest.java (✅ já existe)
    ├── AuthenticationControllerIntegrationTest.java (NOVO)
    ├── CategoryControllerIntegrationTest.java (NOVO)
    ├── WarehouseControllerIntegrationTest.java (NOVO)
    ├── BatchControllerIntegrationTest.java (NOVO)
    ├── StockMovementControllerIntegrationTest.java (NOVO)
    └── ReportControllerIntegrationTest.java (NOVO)
```

---

## TestDataFactory - Métodos Helper

Criar `src/test/java/br/com/stockshift/util/TestDataFactory.java` com:

```java
public class TestDataFactory {
    // Criar tenant de teste
    public static Tenant createTenant(TenantRepository repo, String name, String document)

    // Criar usuário de teste
    public static User createUser(UserRepository repo, PasswordEncoder encoder,
                                   UUID tenantId, String email)

    // Criar categoria de teste
    public static Category createCategory(CategoryRepository repo, UUID tenantId, String name)

    // Criar produto de teste
    public static Product createProduct(ProductRepository repo, UUID tenantId,
                                        Category category, String name)

    // Criar warehouse de teste
    public static Warehouse createWarehouse(WarehouseRepository repo, UUID tenantId, String name)

    // Criar batch de teste
    public static Batch createBatch(BatchRepository repo, UUID tenantId,
                                    Product product, Warehouse warehouse, Integer quantity)
}
```

---

## Autenticação nos Testes

**Abordagem Escolhida: @WithMockUser**

```java
@Test
@WithMockUser(username = "test@example.com", authorities = {"ROLE_ADMIN"})
void shouldCreateWarehouse() throws Exception {
    // TenantContext configurado no @BeforeEach
    // Security context configurado automaticamente
    mockMvc.perform(post("/api/warehouses")...
}
```

**Exceção:** `AuthenticationControllerIntegrationTest` NÃO usará `@WithMockUser` pois precisa testar a geração real de JWT tokens.

---

## ✅ O Que SERÁ Testado (Happy Paths)

### AuthenticationController
- ✅ POST /api/auth/login - Login com credenciais válidas
- ✅ POST /api/auth/refresh - Renovar token válido
- ✅ POST /api/auth/logout - Logout com token válido

### CategoryController
- ✅ POST /api/categories - Criar categoria
- ✅ GET /api/categories/{id} - Buscar por ID
- ✅ GET /api/categories - Listar todas

### WarehouseController
- ✅ POST /api/warehouses - Criar warehouse
- ✅ GET /api/warehouses/{id} - Buscar por ID
- ✅ GET /api/warehouses - Listar todos

### BatchController
- ✅ POST /api/batches - Criar lote
- ✅ GET /api/batches/{id} - Buscar por ID
- ✅ GET /api/batches/warehouse/{warehouseId} - Listar por warehouse
- ✅ GET /api/batches/expiring/{daysAhead} - Lotes expirando

### StockMovementController
- ✅ POST /api/stock-movements - Criar movimento PURCHASE
- ✅ POST /api/stock-movements/{id}/execute - Executar movimento (valida atualização de estoque)
- ✅ GET /api/stock-movements/{id} - Buscar por ID

### ReportController
- ✅ GET /api/reports/dashboard - Dashboard summary
- ✅ GET /api/reports/stock - Relatório completo de estoque

### ProductController
- ✅ Já testado no ProductControllerIntegrationTest existente

---

## ❌ O Que NÃO Será Testado (Fase 2 - Documentar)

### Casos de Erro
- ❌ 401 Unauthorized (token inválido/expirado)
- ❌ 403 Forbidden (permissões insuficientes)
- ❌ 404 Not Found (recurso inexistente)
- ❌ 400 Bad Request (validações de campos obrigatórios, formatos inválidos)
- ❌ 409 Conflict (SKU/barcode duplicado, violação de unique constraints)

### Regras de Negócio Complexas
- ❌ **Isolamento multi-tenant:** Criar recurso em tenant A e tentar acessar de tenant B
- ❌ **Soft delete behavior:** Recursos deletados não devem aparecer em queries
- ❌ **Optimistic locking:** Tentativa de atualização concorrente de batches (version conflict)
- ❌ **FEFO logic:** First Expired First Out - ordem correta de consumo de lotes por validade
- ❌ **Stock movement validations:**
  - TRANSFER requer source + destination warehouse diferentes
  - SALE/ADJUSTMENT requer apenas destination
  - Quantidade disponível em estoque para SALE
- ❌ **Kit products:** Produtos compostos por outros produtos com quantidades específicas
- ❌ **Hierarchy validations:** Categoria pai deve existir, sem ciclos

### Endpoints Secundários
- ❌ PUT /api/warehouses/{id} - Update warehouse
- ❌ DELETE /api/warehouses/{id} - Delete warehouse
- ❌ PUT /api/batches/{id} - Update batch
- ❌ DELETE /api/batches/{id} - Delete batch
- ❌ PUT /api/products/{id} - Update product
- ❌ DELETE /api/products/{id} - Soft delete product
- ❌ PUT /api/categories/{id} - Update category
- ❌ DELETE /api/categories/{id} - Soft delete category
- ❌ POST /api/stock-movements/{id}/cancel - Cancelar movimento
- ❌ GET /api/stock-movements/type/{type} - Filtrar por tipo
- ❌ GET /api/stock-movements/status/{status} - Filtrar por status
- ❌ GET /api/products/search?q={query} - Search products (já testado)
- ❌ GET /api/products/barcode/{barcode} - Find by barcode (já testado)
- ❌ GET /api/products/sku/{sku} - Find by SKU
- ❌ GET /api/products/active/{isActive} - Filter by active status
- ❌ GET /api/categories/parent/{parentId} - Filter by parent category
- ❌ GET /api/warehouses/active/{isActive} - Filter by active status
- ❌ GET /api/batches/product/{productId} - Batches by product
- ❌ GET /api/batches/low-stock/{threshold} - Low stock alert
- ❌ GET /api/reports/stock/low-stock - Low stock report
- ❌ GET /api/reports/stock/expiring - Expiring products report

### Edge Cases
- ❌ Movimento de estoque com quantidade maior que disponível
- ❌ Batches com data de validade já expirada
- ❌ Refresh token revogado ou usado múltiplas vezes
- ❌ Criação de produto/categoria com tenant_id diferente do contexto
- ❌ Operações simultâneas causando race conditions
- ❌ Warehouse inativo sendo usado em movimento
- ❌ Transferência entre warehouses de tenants diferentes

### Cenários Avançados
- ❌ **Rollback de movimentos:** Cancelar movimento já executado e reverter estoque
- ❌ **Auditoria completa:** Verificar createdAt/updatedAt em todas operações
- ❌ **Paginação:** Endpoints com muitos resultados
- ❌ **Performance:** Queries com grandes volumes de dados
- ❌ **Concorrência:** Múltiplos usuários editando mesmo recurso

---

## Padrão de Implementação

### Setup Padrão (@BeforeEach)
```java
@BeforeEach
void setUpTestData() {
    // 1. Limpar dados
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    // ... outros repositórios

    // 2. Criar tenant e user usando TestDataFactory
    testTenant = TestDataFactory.createTenant(tenantRepository, "Test Tenant", "12345678000190");
    testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                                          testTenant.getId(), "test@example.com");

    // 3. Configurar TenantContext
    TenantContext.setTenantId(testTenant.getId());

    // 4. Criar dependências específicas do controller (category, warehouse, etc.)
}
```

### Estrutura de Teste
```java
@Test
@WithMockUser(username = "test@example.com", authorities = {"ROLE_ADMIN"})
void shouldCreateResource() throws Exception {
    // 1. Preparar request DTO
    RequestDTO request = RequestDTO.builder()
        .field("value")
        .build();

    // 2. Executar chamada
    mockMvc.perform(post("/api/endpoint")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        // 3. Validar resposta
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.field").value("value"));
}
```

---

## Métricas de Sucesso

- ✅ Todos os controllers têm pelo menos 3 testes (create, get, list)
- ✅ StockMovement tem teste de execução validando atualização de estoque
- ✅ AuthenticationController testa fluxo completo de login/refresh/logout
- ✅ Todos os testes passam com Testcontainers PostgreSQL
- ✅ Coverage document criado listando endpoints não testados

---

## Próximos Passos (Após Implementação)

1. Executar `./gradlew test` e validar que todos passam
2. Revisar cobertura de código (opcional: `./gradlew jacocoTestReport`)
3. Documentar no README como rodar os testes
4. Criar issue/card para Fase 2 (testes de erro e edge cases)
