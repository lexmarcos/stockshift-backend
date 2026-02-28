# RBAC + Warehouse Scope Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implementar autorização por permissões (`resource:action`) escopadas ao warehouse ativo no token.

**Architecture:** O acesso é resolvido por `UserRoleWarehouse` (`user x role x warehouse`), o JWT carrega `warehouseId` e `authorities`, e os controllers usam method security com guard de warehouse. O service layer reforça o escopo para consultas e mutações sensíveis.

**Tech Stack:** Spring Boot, Spring Security (`@EnableMethodSecurity`), JPA/Hibernate, Flyway, JWT (jjwt), Testcontainers.

### Task 1: Schema + source of truth

**Files:**
- Create: `src/main/resources/db/migration/V2__rbac_warehouse_scope.sql`
- Create: `src/main/java/br/com/stockshift/security/PermissionCodes.java`
- Modify: `src/main/java/br/com/stockshift/model/entity/Permission.java`
- Modify: `src/main/java/br/com/stockshift/repository/PermissionRepository.java`

**Step 1:** adicionar `code` em `permissions`, deduplicar e semear códigos obrigatórios.
**Step 2:** criar `user_role_warehouses` e backfill de `user_roles` + `user_warehouses`.

### Task 2: Resolver e acesso por warehouse

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/UserRoleWarehouseId.java`
- Create: `src/main/java/br/com/stockshift/model/entity/UserRoleWarehouse.java`
- Create: `src/main/java/br/com/stockshift/repository/UserRoleWarehouseRepository.java`
- Create: `src/main/java/br/com/stockshift/service/PermissionResolverService.java`
- Modify: `src/main/java/br/com/stockshift/service/WarehouseAccessService.java`
- Modify: `src/main/java/br/com/stockshift/service/UserService.java`

**Step 1:** resolver permissões efetivas por `(userId, warehouseId)`.
**Step 2:** validar acesso ao warehouse ativo e sincronizar vínculos no create/update de usuário.

### Task 3: JWT/Auth flow

**Files:**
- Modify: `src/main/java/br/com/stockshift/security/JwtTokenProvider.java`
- Modify: `src/main/java/br/com/stockshift/security/JwtAuthenticationFilter.java`
- Modify: `src/main/java/br/com/stockshift/service/AuthService.java`
- Modify: `src/main/java/br/com/stockshift/controller/AuthController.java`

**Step 1:** incluir claims `warehouseId` e `authorities` no access token.
**Step 2:** usar permissões resolvidas por warehouse em login/refresh.
**Step 3:** implementar switch warehouse com validação de vínculo e rotação de refresh token.

### Task 4: Method security e escopo de domínio

**Files:**
- Modify controllers em `src/main/java/br/com/stockshift/controller/*.java`
- Create: `src/main/java/br/com/stockshift/security/WarehouseGuard.java`
- Modify serviços: `BatchService`, `TransferService`, `TransferValidationService`, `WarehouseService`, `ReportService`

**Step 1:** migrar permissões para `resource:action` nas anotações.
**Step 2:** aplicar guard de warehouse nas rotas com `warehouseId`.
**Step 3:** reforçar escopo no service layer para evitar acesso cross-warehouse.

### Task 5: Verification

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/AuthorizationScopeIntegrationTest.java`
- Modify: `src/test/java/br/com/stockshift/controller/AuthenticationControllerIntegrationTest.java`
- Modify: `src/test/java/br/com/stockshift/controller/PermissionControllerIntegrationTest.java`
- Modify: `src/test/java/br/com/stockshift/security/UserPrincipalTest.java`
- Modify: `src/test/java/br/com/stockshift/service/BatchServiceTest.java`

**Step 1:** garantir 403 sem permissão e 403 com warehouse fora do escopo.
**Step 2:** validar claims do token e fluxo de `switch-warehouse`.
**Step 3:** rodar suíte completa (`./gradlew test`) e corrigir regressões.
