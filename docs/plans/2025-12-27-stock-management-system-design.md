# Design do Sistema de Gerenciamento de Estoque - StockShift

**Data:** 2025-12-27
**Versão:** 1.0
**Status:** Aprovado

## Visão Geral

Sistema de gerenciamento de estoque para pequenos empresários com múltiplas lojas em diferentes cidades. O sistema gerencia produtos diversos (perfumes, bolsas, roupas, cosméticos, kits), controla entrada/saída, transferências entre estoques, validade de produtos e possui sistema de permissões granular.

## Decisões Arquiteturais

### Multi-tenancy
- **Modelo:** Cada empresário é um tenant isolado
- **Implementação:** Coluna `tenant_id` em todas as entidades principais
- **Isolamento:** Hibernate Filter automático garante que queries só retornem dados do tenant do usuário logado

### Autenticação
- **Modelo:** JWT + Refresh Token
- **Access Token:** JWT stateless, 15 minutos de validade
- **Refresh Token:** UUID armazenado no banco, 7 dias de validade
- **Segurança:** Refresh tokens podem ser revogados, permitindo controle total de logout

### Produtos Flexíveis
- **Modelo:** JSONB para atributos dinâmicos
- **Implementação:** PostgreSQL com coluna `attributes` (JSONB)
- **Vantagem:** Suporta produtos diversos sem necessidade de múltiplas tabelas ou colunas nulas
- **Categorias:** Definem schema opcional de atributos via `attributes_schema` (JSONB)

### Kits de Produtos
- **Modelo:** Kit como produto composto
- **Implementação:** Tabela `product_kits` relaciona kit com componentes
- **Estoque:** Kit não tem estoque próprio, ao vender diminui estoque dos componentes

### Controle de Validade
- **Modelo:** Lotes/Batches
- **Implementação:** Cada entrada de produto cria um lote com data de validade
- **FEFO:** Sistema sugere First Expired, First Out, mas usuário pode escolher lote

### Movimentações de Estoque
- **Tipos:** PURCHASE, SALE, TRANSFER, ADJUSTMENT, RETURN
- **Estados:** PENDING, IN_TRANSIT, COMPLETED, CANCELLED
- **Transferências:** Passam por estados para rastrear produto em trânsito entre cidades

### Permissões
- **Modelo:** RBAC granular (Role-Based Access Control)
- **Implementação:** Roles customizáveis por tenant + permissões específicas
- **Flexibilidade:** Permite controle por recurso, ação e escopo (ex: gerente só aprova transferências do próprio warehouse)

### Códigos de Barras
- **Modelo:** Híbrido
- **Implementação:** Produtos podem ter código de barras existente (EAN, UPC) ou gerado pelo sistema
- **ID Único:** Sempre possui UUID interno independente do código de barras

---

## Arquitetura do Sistema

### Camadas (Clean Architecture)

```
├── controller/     # REST endpoints, DTOs, validação de entrada
├── service/        # Lógica de negócio, orquestração
├── repository/     # Acesso a dados (Spring Data JPA)
├── model/entity/   # Entidades JPA
├── model/enums/    # Enumerações
├── security/       # Autenticação, autorização, JWT
├── config/         # Configurações Spring
└── exception/      # Exception handlers, erros customizados
```

### Stack Tecnológica

- **Framework:** Spring Boot 4.0.1
- **Java:** 17
- **Banco de Dados:** PostgreSQL
- **Migrations:** Flyway
- **Segurança:** Spring Security + JWT
- **Validação:** Bean Validation
- **ORM:** Spring Data JPA (Hibernate)
- **Build:** Gradle
- **Documentação API:** Springdoc OpenAPI (Swagger)
- **Testes:** JUnit 5, Mockito, Testcontainers

---

## Modelo de Dados

### Entidades Principais

#### tenants
```sql
id (UUID) PK
business_name (VARCHAR)
document (VARCHAR) - CPF/CNPJ
email (VARCHAR)
phone (VARCHAR)
is_active (BOOLEAN)
created_at (TIMESTAMP)
updated_at (TIMESTAMP)
```

#### users
```sql
id (UUID) PK
tenant_id (UUID) FK → tenants
email (VARCHAR) UNIQUE per tenant
password (VARCHAR) - BCrypt hash
full_name (VARCHAR)
is_active (BOOLEAN)
created_at (TIMESTAMP)
updated_at (TIMESTAMP)
last_login (TIMESTAMP)
```

#### roles
```sql
id (UUID) PK
tenant_id (UUID) FK → tenants
name (VARCHAR) - ex: "ADMIN", "GERENTE_SP", "VENDEDOR"
description (TEXT)
is_system_role (BOOLEAN) - true para roles padrão
created_at (TIMESTAMP)
updated_at (TIMESTAMP)
```

#### permissions
```sql
id (UUID) PK
resource (VARCHAR) - "PRODUCT", "STOCK", "SALE", "USER", "REPORT"
action (VARCHAR) - "CREATE", "READ", "UPDATE", "DELETE", "APPROVE"
scope (VARCHAR) - "ALL", "OWN_WAREHOUSE", "OWN"
```

#### role_permissions (many-to-many)
```sql
role_id (UUID) FK → roles
permission_id (UUID) FK → permissions
created_at (TIMESTAMP)
```

#### user_roles (many-to-many)
```sql
user_id (UUID) FK → users
role_id (UUID) FK → roles
created_at (TIMESTAMP)
```

#### refresh_tokens
```sql
id (UUID) PK
token (VARCHAR) UNIQUE - UUID
user_id (UUID) FK → users
expires_at (TIMESTAMP)
created_at (TIMESTAMP)
```

#### categories
```sql
id (UUID) PK
tenant_id (UUID) FK → tenants
name (VARCHAR) - ex: "Perfumes", "Roupas"
description (TEXT)
parent_category_id (UUID) FK → categories (nullable, hierarquia)
attributes_schema (JSONB) - define atributos aceitos
  exemplo: {"tamanho": "string", "cor": "string", "ml": "number"}
created_at (TIMESTAMP)
updated_at (TIMESTAMP)
```

#### products
```sql
id (UUID) PK
tenant_id (UUID) FK → tenants
category_id (UUID) FK → categories
name (VARCHAR)
description (TEXT)
barcode (VARCHAR) UNIQUE per tenant (nullable)
barcode_type (ENUM) - EXTERNAL, GENERATED
sku (VARCHAR) UNIQUE per tenant (nullable)
is_kit (BOOLEAN)
attributes (JSONB) - atributos específicos
  exemplo perfume: {"fragrancia": "floral", "ml": 100, "genero": "feminino"}
  exemplo roupa: {"tamanho": "M", "cor": "azul", "material": "algodão"}
has_expiration (BOOLEAN)
active (BOOLEAN)
created_at (TIMESTAMP)
updated_at (TIMESTAMP)
```

#### product_kits
```sql
id (UUID) PK
kit_product_id (UUID) FK → products (o kit)
component_product_id (UUID) FK → products (componente)
quantity (INTEGER) - quantidade do componente no kit
created_at (TIMESTAMP)
```

#### warehouses
```sql
id (UUID) PK
tenant_id (UUID) FK → tenants
name (VARCHAR) - "Loja Centro SP"
city (VARCHAR)
state (VARCHAR)
address (TEXT)
is_active (BOOLEAN)
created_at (TIMESTAMP)
updated_at (TIMESTAMP)
```

#### batches
```sql
id (UUID) PK
tenant_id (UUID) FK → tenants
product_id (UUID) FK → products
warehouse_id (UUID) FK → warehouses
batch_code (VARCHAR)
quantity (INTEGER)
version (LONG) - Optimistic Locking
manufactured_date (DATE)
expiration_date (DATE) - nullable se produto não expira
cost_price (DECIMAL)
selling_price (DECIMAL)
created_at (TIMESTAMP)
updated_at (TIMESTAMP)
```

#### stock_movements
```sql
id (UUID) PK
tenant_id (UUID) FK → tenants
movement_type (ENUM) - PURCHASE, SALE, TRANSFER, ADJUSTMENT, RETURN
status (ENUM) - PENDING, IN_TRANSIT, COMPLETED, CANCELLED
source_warehouse_id (UUID) FK → warehouses (nullable)
destination_warehouse_id (UUID) FK → warehouses (nullable)
user_id (UUID) FK → users
notes (TEXT)
created_at (TIMESTAMP)
updated_at (TIMESTAMP)
completed_at (TIMESTAMP)
```

#### stock_movement_items
```sql
id (UUID) PK
movement_id (UUID) FK → stock_movements
product_id (UUID) FK → products
batch_id (UUID) FK → batches
quantity (INTEGER)
unit_price (DECIMAL)
total_price (DECIMAL)
```

### Índices Importantes

```sql
-- Performance em queries frequentes
CREATE INDEX idx_products_tenant_category ON products(tenant_id, category_id);
CREATE INDEX idx_products_barcode ON products(barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_batches_product_warehouse ON batches(product_id, warehouse_id);
CREATE INDEX idx_batches_expiration ON batches(expiration_date) WHERE expiration_date IS NOT NULL;
CREATE INDEX idx_movements_tenant_type ON stock_movements(tenant_id, movement_type, status);
CREATE INDEX idx_movements_warehouse ON stock_movements(source_warehouse_id, destination_warehouse_id);

-- JSONB indexes (GIN para busca em atributos)
CREATE INDEX idx_products_attributes ON products USING GIN(attributes);
CREATE INDEX idx_categories_schema ON categories USING GIN(attributes_schema);
```

### Views

```sql
-- Resumo de estoque por produto e warehouse
CREATE VIEW v_stock_summary AS
SELECT
  p.id as product_id,
  p.name,
  w.id as warehouse_id,
  w.name as warehouse_name,
  SUM(b.quantity) as total_quantity,
  MIN(b.expiration_date) as nearest_expiration
FROM products p
JOIN batches b ON b.product_id = p.id
JOIN warehouses w ON w.id = b.warehouse_id
WHERE b.quantity > 0
GROUP BY p.id, p.name, w.id, w.name;
```

---

## API REST

### Autenticação (`/api/auth`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /login | Login com email/password → retorna accessToken + refreshToken |
| POST | /refresh | Renova accessToken usando refreshToken |
| POST | /logout | Revoga refreshToken |
| POST | /register | Cadastro inicial de tenant + primeiro admin |

### Produtos (`/api/products`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | / | Lista produtos (filtros: category, search, active) |
| POST | / | Cria produto |
| GET | /:id | Detalhes do produto |
| PUT | /:id | Atualiza produto |
| DELETE | /:id | Desativa produto |
| POST | /:id/kit | Adiciona componentes ao kit |
| GET | /:id/stock | Estoque atual por warehouse |
| GET | /:id/batches | Lotes disponíveis |

### Categorias (`/api/categories`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | / | Lista categorias (suporta hierarquia) |
| POST | / | Cria categoria |
| PUT | /:id | Atualiza categoria |
| DELETE | /:id | Remove categoria |

### Estoques (`/api/warehouses`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | / | Lista warehouses |
| POST | / | Cria warehouse |
| PUT | /:id | Atualiza warehouse |
| GET | /:id/stock | Estoque total do warehouse |
| GET | /:id/expiring | Produtos próximos ao vencimento |

### Lotes (`/api/batches`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | / | Lista lotes (filtros: product, warehouse, expiring) |
| POST | / | Cria lote manualmente |
| PUT | /:id | Ajusta quantidade/informações do lote |
| GET | /expiring | Lotes próximos ao vencimento (30 dias) |

### Movimentações (`/api/movements`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | /purchase | Registra compra |
| POST | /sale | Registra venda |
| POST | /transfer | Cria transferência |
| PUT | /transfer/:id/transit | Marca transferência como em trânsito |
| PUT | /transfer/:id/complete | Conclui transferência |
| PUT | /:id/cancel | Cancela movimentação |
| GET | / | Histórico de movimentações |
| GET | /:id | Detalhes da movimentação |

### Usuários (`/api/users`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | / | Lista usuários do tenant |
| POST | / | Cria usuário |
| PUT | /:id | Atualiza usuário |
| DELETE | /:id | Desativa usuário |
| PUT | /:id/roles | Atribui roles ao usuário |
| GET | /me | Dados do usuário logado |
| PUT | /me/password | Troca senha |

### Roles e Permissões (`/api/roles`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | / | Lista roles |
| POST | / | Cria role customizada |
| PUT | /:id | Atualiza role |
| DELETE | /:id | Remove role (apenas não-sistema) |
| GET | /permissions | Lista todas permissões disponíveis |
| PUT | /:id/permissions | Atribui permissões à role |

### Relatórios (`/api/reports`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /stock-summary | Resumo de estoque por warehouse |
| GET | /stock-value | Valor total do estoque |
| GET | /movements-history | Histórico de movimentações (filtros) |
| GET | /sales-report | Relatório de vendas (período) |
| GET | /expiring-products | Produtos próximos ao vencimento |
| GET | /low-stock | Produtos com estoque baixo |
| GET | /product-movement-history/:productId | Histórico de um produto |

### Dashboard (`/api/dashboard`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | /stats | Estatísticas gerais |
| GET | /alerts | Alertas (vencimento, estoque baixo, transferências) |

---

## Regras de Negócio

### Validações de Produtos
- Código de barras único por tenant (se informado)
- SKU único por tenant (se informado)
- Kit não pode conter a si mesmo (validação recursiva)
- Produto inativo não pode ter movimentações
- Atributos JSONB podem seguir schema da categoria (validação opcional)

### Validações de Estoque
- Não permitir venda/transferência com quantidade maior que disponível
- Ao vender produto com validade, alertar se lote está vencido
- FEFO sugerido mas não obrigatório (usuário pode escolher lote)
- Venda de kit verifica disponibilidade de TODOS os componentes

### Validações de Movimentações
- **TRANSFER:** warehouses origem e destino diferentes
- **SALE:** apenas warehouse origem (destino null)
- **PURCHASE:** apenas warehouse destino (origem null)
- Cancelamento apenas de movimentações PENDING ou IN_TRANSIT
- Transição de status sequencial: PENDING → IN_TRANSIT → COMPLETED

### Fluxos de Movimentação

#### PURCHASE (Compra)
1. Status: PENDING
2. Admin registra entrada de produtos
3. Status: COMPLETED
4. Cria novo batch no warehouse destino

#### SALE (Venda)
1. Status: COMPLETED (imediato)
2. Diminui quantity dos batches selecionados
3. Se produto for kit, diminui estoque de todos os componentes

#### TRANSFER (Transferência)
1. Gerente cria transferência: PENDING
2. Produto sai da origem: IN_TRANSIT (quantity diminui no batch origem)
3. Produto chega ao destino: COMPLETED (cria/incrementa batch no destino)
4. Pode ser CANCELLED se ainda PENDING ou IN_TRANSIT (reverte estoque origem)

#### ADJUSTMENT (Ajuste)
1. Status: COMPLETED (imediato)
2. Ajusta quantity do batch (perdas, danos, inventário)

### Regras de Permissões

**ADMIN:**
- Acesso total ao sistema
- Gerencia usuários, roles, permissões
- Todas as operações em todos os recursos

**GERENTE:**
- Gerencia produtos, categorias, estoques
- Cria e aprova movimentações
- Acessa relatórios
- Não pode criar admins ou modificar configurações críticas
- Pode ter escopo limitado a warehouses específicos

**VENDEDOR:**
- Cria vendas (SALE)
- Consulta estoque (read-only)
- Consulta produtos (read-only)
- Não acessa configurações ou relatórios gerenciais

**CONVIDADO:**
- Acesso read-only a tudo
- Não pode criar ou modificar nada

### Auditoria
- Todas movimentações registram `user_id` (quem executou)
- Timestamps `created_at`/`updated_at` em todas tabelas
- Movimentações nunca são deletadas, apenas canceladas
- Histórico completo de alterações em estoque

---

## Segurança

### JWT Configuration
- **Secret:** Variável de ambiente `JWT_SECRET`
- **Access Token:** 15 minutos de validade
- **Refresh Token:** 7 dias de validade
- **Algoritmo:** HS256
- **Claims:** userId, tenantId, roles

### Security Filter Chain

1. **JwtAuthenticationFilter**
   - Valida JWT no header Authorization
   - Extrai user, tenant, roles
   - Popula SecurityContext

2. **TenantFilter**
   - Injeta tenantId em queries automáticas (Hibernate Filter)
   - Garante isolamento de dados

3. **PermissionEvaluator**
   - Valida permissões granulares por recurso/ação/escopo

### Multi-tenancy com Hibernate Filter

```java
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = "uuid"))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
```

Aplica automaticamente `WHERE tenant_id = ?` em todas queries.

### Password Security
- **Encoder:** BCryptPasswordEncoder (strength 12)
- **Validação:** Mínimo 8 caracteres, letra + número

### CORS Configuration
- Origens configuráveis: `ALLOWED_ORIGINS`
- Métodos: GET, POST, PUT, DELETE
- Headers: Authorization, Content-Type

---

## Tratamento de Erros

### Padrão de Resposta Success
```json
{
  "data": { ... },
  "message": "Operação realizada com sucesso",
  "timestamp": "2025-12-27T10:30:00Z"
}
```

### Padrão de Resposta Erro
```json
{
  "error": "INSUFFICIENT_STOCK",
  "message": "Estoque insuficiente para realizar a operação",
  "details": {
    "productId": "uuid",
    "requested": 10,
    "available": 5
  },
  "timestamp": "2025-12-27T10:30:00Z",
  "path": "/api/movements/sale"
}
```

### Códigos HTTP
- **200 OK:** Sucesso em GET, PUT
- **201 Created:** Sucesso em POST
- **204 No Content:** Sucesso em DELETE
- **400 Bad Request:** Validação falhou
- **401 Unauthorized:** Não autenticado
- **403 Forbidden:** Não tem permissão
- **404 Not Found:** Recurso não existe
- **409 Conflict:** Conflito de negócio
- **500 Internal Server Error:** Erro inesperado

### Validações Bean Validation
- `@NotNull`, `@NotBlank` em campos obrigatórios
- `@Email` em emails
- `@Min`, `@Max` em quantidades
- `@Pattern` em códigos de barras
- Validações customizadas: `@ValidBarcode`, `@UniqueBarcode`

---

## Estratégia de Testes

### Testes Unitários (JUnit 5 + Mockito)

**Service Layer:**
- ProductServiceTest
- StockMovementServiceTest
- BatchServiceTest
- AuthServiceTest

**Validadores:**
- BarcodeValidatorTest
- KitCompositionValidatorTest
- StockAvailabilityValidatorTest

### Testes de Integração (Spring Boot Test + Testcontainers)

```java
@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = NONE)
```

**Containers:**
- PostgreSQLContainer para banco real
- Testa migrations Flyway
- Testa queries complexas, índices, JSONB

**Scenarios:**
- ProductIntegrationTest: CRUD completo, multi-tenancy isolation
- StockMovementIntegrationTest: fluxo completo de transferência
- AuthIntegrationTest: login, refresh, logout, expiração

### Testes de API (MockMvc + Spring Security Test)

```java
@WebMvcTest(ProductController.class)
```

**Testa:**
- Serialização/deserialização JSON
- Validações Bean Validation
- Respostas HTTP corretas
- Autenticação e autorização
- Multi-tenancy

**Com diferentes roles:**
```java
@WithMockUser(roles = "ADMIN")
@WithMockUser(roles = "VENDEDOR")
```

### Cobertura de Testes
- Mínimo 70% cobertura (JaCoCo)
- Foco em service layer e regras de negócio
- Controllers: validações e segurança
- Repositories: queries customizadas complexas

---

## Configurações e Variáveis de Ambiente

### application.yml

```yaml
# Database
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:stockshift}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:secret}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

# JWT
jwt:
  secret: ${JWT_SECRET}
  access-expiration: ${JWT_ACCESS_EXPIRATION:900000}
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}

# CORS
cors:
  allowed-origins: ${ALLOWED_ORIGINS:http://localhost:3000}

# Logging
logging:
  level:
    root: INFO
    br.com.stockshift: DEBUG

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized
```

### Variáveis de Ambiente (.env)

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=stockshift
DB_USER=postgres
DB_PASSWORD=secret

# JWT
JWT_SECRET=your-256-bit-secret-key-change-in-production
JWT_ACCESS_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000

# CORS
ALLOWED_ORIGINS=http://localhost:3000,https://app.stockshift.com

# Profile
SPRING_PROFILES_ACTIVE=dev
```

---

## Documentação da API (Springdoc OpenAPI)

### Dependência

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
```

### Configuração

```java
@Configuration
@OpenAPIDefinition(
  info = @Info(
    title = "StockShift API",
    version = "1.0",
    description = "API de Gerenciamento de Estoque para Pequenas Empresas"
  ),
  security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
  name = "bearerAuth",
  type = SecuritySchemeType.HTTP,
  scheme = "bearer",
  bearerFormat = "JWT"
)
public class OpenApiConfig {}
```

### Swagger UI
- URL: `http://localhost:8080/swagger-ui.html`
- Testa endpoints diretamente
- Documentação automática de DTOs, validações, responses

---

## Estrutura de Diretórios

```
src/main/java/br/com/stockshift/
├── config/
│   ├── SecurityConfig.java
│   ├── OpenApiConfig.java
│   ├── CorsConfig.java
│   └── HibernateConfig.java
├── controller/
│   ├── AuthController.java
│   ├── ProductController.java
│   ├── CategoryController.java
│   ├── WarehouseController.java
│   ├── BatchController.java
│   ├── StockMovementController.java
│   ├── UserController.java
│   ├── RoleController.java
│   ├── ReportController.java
│   └── DashboardController.java
├── dto/
│   ├── request/
│   └── response/
├── model/
│   ├── entity/
│   │   ├── Tenant.java
│   │   ├── User.java
│   │   ├── Role.java
│   │   ├── Permission.java
│   │   ├── RefreshToken.java
│   │   ├── Category.java
│   │   ├── Product.java
│   │   ├── ProductKit.java
│   │   ├── Warehouse.java
│   │   ├── Batch.java
│   │   ├── StockMovement.java
│   │   └── StockMovementItem.java
│   └── enums/
│       ├── MovementType.java
│       ├── MovementStatus.java
│       ├── BarcodeType.java
│       ├── PermissionResource.java
│       ├── PermissionAction.java
│       └── PermissionScope.java
├── repository/
│   ├── TenantRepository.java
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   ├── PermissionRepository.java
│   ├── RefreshTokenRepository.java
│   ├── CategoryRepository.java
│   ├── ProductRepository.java
│   ├── ProductKitRepository.java
│   ├── WarehouseRepository.java
│   ├── BatchRepository.java
│   ├── StockMovementRepository.java
│   └── StockMovementItemRepository.java
├── service/
│   ├── AuthService.java
│   ├── ProductService.java
│   ├── CategoryService.java
│   ├── WarehouseService.java
│   ├── BatchService.java
│   ├── StockMovementService.java
│   ├── UserService.java
│   ├── RoleService.java
│   ├── ReportService.java
│   └── DashboardService.java
├── security/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   ├── TenantFilter.java
│   ├── TenantContext.java
│   └── CustomPermissionEvaluator.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ResourceNotFoundException.java
│   ├── InsufficientStockException.java
│   ├── UnauthorizedException.java
│   └── BusinessException.java
└── util/
    ├── BarcodeValidator.java
    ├── BarcodeGenerator.java
    └── DateUtils.java

src/main/resources/
├── db/migration/
│   ├── V1__create_tenants_and_users.sql
│   ├── V2__create_roles_and_permissions.sql
│   ├── V3__create_products_and_categories.sql
│   ├── V4__create_warehouses_and_batches.sql
│   ├── V5__create_stock_movements.sql
│   ├── V6__create_refresh_tokens.sql
│   ├── V7__create_indexes.sql
│   ├── V8__create_views.sql
│   └── V9__insert_default_permissions.sql
└── application.yml

src/test/java/br/com/stockshift/
├── service/
├── controller/
└── integration/
```

---

## Pontos de Atenção

### Performance JSONB
- Índices GIN funcionam bem para a maioria das queries
- Queries complexas em atributos podem ser lentas
- Monitorar e criar índices específicos conforme necessidade

### Transações
- Movimentações de estoque devem ser `@Transactional`
- Especialmente transferências (diminui origem + aumenta destino)
- Garantir ACID em operações críticas

### Concorrência
- Usar `@Version` (Optimistic Locking) em `batches.quantity`
- Evita race conditions em vendas simultâneas do mesmo lote
- Tratamento adequado de `OptimisticLockException`

### Auditoria
- Considerar tabela `audit_log` para rastrear alterações críticas
- Pode ser implementado via Hibernate Envers (opcional para v1)

---

## Melhorias Futuras (pós-MVP)

### Funcionalidades
- Notificações (email/push) para produtos vencendo, estoque baixo
- Relatórios em PDF (JasperReports ou iText)
- Dashboard com gráficos (Chart.js no frontend)
- Importação em lote (CSV/Excel) de produtos
- Integração com APIs de nota fiscal
- App mobile (Android/iOS) para vendedores
- Código de barras/QR Code gerado automaticamente

### Performance
- Cache (Redis) para consultas frequentes
- Rate limiting (Bucket4j)
- Paginação otimizada para grandes volumes

### Segurança
- Two-factor authentication (2FA)
- Logs de auditoria detalhados
- Política de senha mais robusta

### DevOps
- CI/CD pipeline (GitHub Actions, GitLab CI)
- Containerização (Docker)
- Kubernetes para orquestração
- Monitoramento (Prometheus, Grafana)
- APM (Application Performance Monitoring)

---

## Conclusão

O design apresentado fornece uma base sólida para um sistema de gerenciamento de estoque robusto, escalável e seguro. A arquitetura em camadas, uso de JSONB para flexibilidade, controle granular de permissões e sistema de lotes garantem que o sistema atende aos requisitos de pequenos empresários com lojas em múltiplas cidades.

A implementação seguirá boas práticas do Spring Boot, com foco em segurança, validações adequadas, testes abrangentes e documentação clara da API.
