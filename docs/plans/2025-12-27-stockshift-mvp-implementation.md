# StockShift MVP Implementation Plan - Phases 1-5

## Phase 1: Project Setup & Configuration
- Configure application.yml (dev/test profiles)
- Add JWT, OpenAPI, Testcontainers dependencies
- Create package structure

## Phase 2: Database Schema (Flyway Migrations)
- V1: Tenants & Users tables with audit timestamps
- V2: Roles & Permissions with many-to-many relationships
- V3: Refresh tokens table
- V4: Categories & Products with JSONB attributes
- V5: Warehouses & Batches with optimistic locking (version column)
- V6: Stock Movements & Movement Items
- V7: Stock summary view
- V8: Default permissions seed data

## Phase 3: Core Entities & Enums
**Enums:** BarcodeType, MovementType, MovementStatus, PermissionResource/Action/Scope

**Base Entities:**
- `BaseEntity`: UUID id, createdAt, updatedAt
- `TenantAwareEntity`: Extends BaseEntity + tenantId

**Domain Entities:**
- Tenant → TenantRepository
- User (with roles relationship) → UserRepository  
- Permission → PermissionRepository
- Role (with permissions) → RoleRepository
- RefreshToken → RefreshTokenRepository

## Phase 4: Authentication & Security
- **JwtProperties**: Configuration for access/refresh expiration
- **JwtTokenProvider**: Generate/validate tokens, extract userId/tenantId
- **TenantContext**: ThreadLocal for multi-tenancy isolation
- **UserPrincipal**: Custom UserDetails implementation
- **CustomUserDetailsService**: Load user by email/id with roles
- **JwtAuthenticationFilter**: Extract JWT from header, validate, set SecurityContext + TenantContext
- **SecurityConfig**: Enable method security, configure filter chain, permit auth endpoints

## Phase 5: Exception Handling & DTOs
**Exceptions:**
- BusinessException (BAD_REQUEST)
- ResourceNotFoundException (NOT_FOUND)
- UnauthorizedException (UNAUTHORIZED)

**GlobalExceptionHandler:** Maps exceptions → HTTP responses with validation error details

**DTOs:**
- `ApiResponse<T>`: Wrapper with success flag, message, generic data
- `ErrorResponse`: timestamp, status, error, message, path, validationErrors

---

## Phases 6-11 (Ready for Implementation)
**Phase 6:** Authentication Service & Controller (Login/Refresh/Logout)  
**Phase 7:** Product Management (Categories, Products, Product Kits)  
**Phase 8:** Warehouse & Batch Management  
**Phase 9:** Stock Movements with type-specific logic (PURCHASE, SALE, TRANSFER, ADJUSTMENT, RETURN)  
**Phase 10:** Reports & Dashboard  
**Phase 11:** Integration Tests with Testcontainers

---

**Key Principles:**
- Multi-tenancy: All queries filter by `tenantId`
- Soft deletes: Category/Product have `deletedAt` column
- Optimistic locking: Batch uses `@Version` for concurrent updates
- JSONB: Product attributes stored as flexible JSON
- JWT + Refresh tokens: Stateless auth with token rotation
- Clean Architecture: Controller → Service → Repository layers

---

# StockShift MVP - Phases 6-7 Summary

## Phase 6: Authentication Service & Controller

### Authentication DTOs (Task 6.1)
- **LoginRequest**: Email + Password validation
- **LoginResponse**: Access token, refresh token, user info, expiry time
- **RefreshTokenRequest/Response**: Token rotation support

### RefreshToken Service (Task 6.2)
- Create refresh tokens (revokes old ones)
- Validate token expiration
- Revoke single or all user tokens

### AuthService (Task 6.3)
- **Login**: Authenticates credentials, generates JWT + refresh token, updates last login
- **Refresh**: Validates refresh token, generates new access token
- **Logout**: Revokes refresh token

### AuthController (Task 6.4)
- `POST /api/auth/login` - Login endpoint
- `POST /api/auth/refresh` - Token refresh endpoint
- `POST /api/auth/logout` - Logout endpoint
- All endpoints return `ApiResponse<T>` wrapper

### Repository Methods (Task 6.5)
- **RefreshTokenRepository**: `findByToken()`, `deleteByUserId()`
- **UserRepository**: `findByEmail()`

---

## Phase 7: Product Management

### Entities (Task 7.1)
- **Category**: Hierarchical, soft-delete, JSONB attributes schema
- **Product**: SKU/Barcode unique per tenant, soft-delete, JSONB attributes, kit support
- **ProductKit**: Links kit product to component products with quantities

### Repositories (Task 7.2)
- **CategoryRepository**: Find by tenant, parent, with soft-delete filtering
- **ProductRepository**: Search by name/SKU/barcode, find active, by category
- **ProductKitRepository**: Find by kit/component product

### DTOs (Task 7.3)
- **Category**: Request/Response with parent category support
- **Product**: Request/Response with barcode/SKU, kit flag, expiration tracking
- **ProductKit**: Request with component product and quantity

### Services (Task 7.4)
- **CategoryService**: CRUD + hierarchy + soft delete
- **ProductService**: CRUD + search + barcode/SKU lookup + soft delete

### Controllers (Task 7.5)
- **CategoryController**: All CRUD + parent filtering
- **ProductController**: All CRUD + search + barcode/SKU endpoints + active filtering

All secured with `@PreAuthorize` annotations using custom permissions.


---
# Phase 8 & 9: Warehouse, Batch & Stock Movement Implementation

## Phase 8: Warehouse and Batch Management
Implements warehouse CRUD operations and batch tracking with optimistic locking.

**Key Components:**
- `Warehouse` entity with multi-tenancy and active status
- `Batch` entity with optimistic locking (`@Version`), expiration tracking, and pricing
- Repositories with queries for low-stock and expiring batches
- DTOs for request/response with validation
- Services with business logic and stock calculations
- Controllers with proper authorization using `@PreAuthorize`

**Endpoints:**
- Warehouse: POST/GET/PUT/DELETE `/api/warehouses`
- Batch: POST/GET/PUT/DELETE `/api/batches`, plus `/expiring/{daysAhead}` and `/low-stock/{threshold}`

## Phase 9: Stock Movements
Manages inventory transfers across warehouses with type-specific logic.

**Movement Types:** PURCHASE, SALE, TRANSFER, ADJUSTMENT, RETURN
**Statuses:** PENDING, COMPLETED, CANCELLED

**Key Features:**
- Validates warehouses based on movement type
- Applies automatic stock changes during execution
- Handles TRANSFER by decreasing source and increasing destination batches
- Optimistic locking prevents concurrent update conflicts

**Endpoints:**
- POST `/api/stock-movements` - Create movement
- POST `/api/stock-movements/{id}/execute` - Execute pending
- POST `/api/stock-movements/{id}/cancel` - Cancel movement
- GET `/api/stock-movements` - List all
- GET `/api/stock-movements/type/{type}` - Filter by type
- GET `/api/stock-movements/status/{status}` - Filter by status

**Architecture:** Controller → Service → Repository with automatic stock adjustment during execution.


## Phase 10: Reports & Dashboard

Create report DTOs and service for dashboard summaries and stock analysis. Implement four endpoints:
- `/api/reports/dashboard` - Key metrics overview
- `/api/reports/stock` - Complete inventory status
- `/api/reports/stock/low-stock` - Items below threshold
- `/api/reports/stock/expiring` - Products expiring soon

Services aggregate batch data by product/warehouse, calculate total values, and apply tenant isolation via TenantContext.

## Phase 11: Integration Tests

Set up Testcontainers-based testing with PostgreSQL. Create base test class with automatic database provisioning. Implement integration tests for Product and StockMovement controllers, validating CRUD operations, search functionality, and movement execution workflows.

**Key Focus:**
- Multi-tenant data isolation
- Soft delete verification
- Stock quantity updates on movement execution
- API response structure validation


---

## Note on Plan Execution

This implementation plan covers the foundational phases of the StockShift MVP:

**Completed Sections:**
- Phase 1: Project Setup and Configuration (Tasks 1.1-1.3) ✅
- Phase 2: Database Schema (Tasks 2.1-2.8) ✅
- Phase 3: Core Entities and Enums (Tasks 3.1-3.6) ✅
- Phase 4: Authentication and Security (Tasks 4.1-4.6) ✅
- Phase 5: Exception Handling and DTOs (Tasks 5.1-5.4) ✅
- Phase 6: Authentication Service and Controller (Tasks 6.1-6.4) ✅
- Phase 7: Product Management (Tasks 7.1-7.5) ✅

**Ready for Execution:**
- Phase 8: Warehouse and Batch Management (Tasks 8.1-8.5) 📋
- Phase 9: Stock Movements (Tasks 9.1-9.5) 📋
- Phase 10: Basic Reports and Dashboard (Tasks 10.1-10.2) 📋
- Phase 11: Integration Tests (Tasks 11.1-11.3) 📋

**Execution Strategy:**
This plan should be executed incrementally. Start with the completed sections above, test each phase thoroughly, and then extend the plan with additional phases as needed. The bite-sized tasks ensure each step is testable and committable independently.

---
