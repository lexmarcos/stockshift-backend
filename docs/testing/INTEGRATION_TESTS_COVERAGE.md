# Integration Tests Coverage

Ultima atualizacao: 2026-02-15
Escopo: integration tests em `src/test/java/br/com/stockshift/controller`

## Resumo Executivo

- Endpoints REST mapeados em controllers: **74**
- Endpoints com cobertura de integration test: **37**
- Cobertura de endpoints por integracao: **50%**

Observacao: esta metrica considera cobertura por endpoint (path+metodo), nao por cenario de erro.

## Matriz Atual por Controller

| Controller | Endpoints Totais | Endpoints Cobertos | Status |
|---|---:|---:|---|
| AuthController | 8 | 3 | Parcial |
| BrandController | 5 | 5 | Bom |
| CategoryController | 6 | 3 | Parcial |
| ProductController | 11 | 7 | Parcial |
| WarehouseController | 7 | 4 | Parcial |
| BatchController | 11 | 8 | Parcial |
| ReportController | 4 | 2 | Parcial |
| TransferController | 11 | 5 | Parcial |
| UserController | 5 | 0 | Nao coberto |
| RoleController | 5 | 0 | Nao coberto |
| PermissionController | 1 | 0 | Nao coberto |

## Endpoints Cobertos Hoje

### Auth
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`

### Brands
- `POST /api/brands`
- `GET /api/brands`
- `GET /api/brands/{id}`
- `PUT /api/brands/{id}`
- `DELETE /api/brands/{id}`

### Categories
- `POST /api/categories`
- `GET /api/categories`
- `GET /api/categories/{id}`

### Products
- `POST /api/products`
- `GET /api/products/{id}`
- `GET /api/products/barcode/{barcode}`
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}`
- `GET /api/products/search`
- `POST /api/products/analyze-image`

### Warehouses
- `POST /api/warehouses`
- `GET /api/warehouses`
- `GET /api/warehouses/{id}`
- `GET /api/warehouses/{id}/products`

### Batches
- `POST /api/batches`
- `POST /api/batches/with-product`
- `GET /api/batches`
- `GET /api/batches/{id}`
- `GET /api/batches/warehouse/{warehouseId}`
- `GET /api/batches/expiring/{daysAhead}`
- `DELETE /api/batches/{id}`
- `DELETE /api/batches/warehouses/{warehouseId}/products/{productId}/batches`

### Reports
- `GET /api/reports/dashboard`
- `GET /api/reports/stock`

### Transfers
- `POST /api/transfers`
- `GET /api/transfers`
- `POST /api/transfers/{id}/execute`
- `POST /api/transfers/{id}/start-validation`
- `POST /api/transfers/{id}/scan`

### Transfer (novos cenarios cobertos no ponto 5)
- `400` ao criar transfer com source/destination iguais.
- `400` ao cancelar transfer `IN_TRANSIT` sem motivo.
- `404` ao executar transfer com `TenantContext` de outro tenant (isolamento multi-tenant).
- `409` ao tentar reexecutar transfer ja em `IN_TRANSIT` (conflito de estado/concurrency-like).

## Principais Gaps (Backlog Prioritario)

### 1. Cenarios de erro (prioridade alta)
- 401/403 em endpoints sensiveis (especialmente transfer e auth).
- 400 para payload invalido e validacoes de negocio.
- 404 para recursos inexistentes em todos os modulos.
- 409 para conflitos de integridade (ex.: codigo de transfer duplicado).

### 2. Isolamento multi-tenant (prioridade alta)
- Acesso cruzado entre tenants em transfer, warehouse e batch.
- Validacao de filtros por tenant em listagens/paginacao.
- Garantia de que usuario sem escopo nao acessa dados de outro tenant.

### 3. Concorrencia (prioridade alta)
- Colisao de codigo em criacao de transfer sob carga concorrente.
- Transicoes de estado concorrentes no fluxo de transfer.
- Atualizacoes de estoque sob lock/concorrencia (pessimistic/optimistic cases).

## Referencias de Testes Existentes

- `src/test/java/br/com/stockshift/controller/AuthenticationControllerIntegrationTest.java`
- `src/test/java/br/com/stockshift/controller/BrandControllerIntegrationTest.java`
- `src/test/java/br/com/stockshift/controller/CategoryControllerIntegrationTest.java`
- `src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java`
- `src/test/java/br/com/stockshift/controller/WarehouseControllerIntegrationTest.java`
- `src/test/java/br/com/stockshift/controller/BatchControllerIntegrationTest.java`
- `src/test/java/br/com/stockshift/controller/BatchSoftDeleteTest.java`
- `src/test/java/br/com/stockshift/controller/BatchDeletionIntegrationTest.java`
- `src/test/java/br/com/stockshift/controller/ReportControllerIntegrationTest.java`
- `src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java`
