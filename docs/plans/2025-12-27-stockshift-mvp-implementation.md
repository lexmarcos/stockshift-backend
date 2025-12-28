# StockShift MVP Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a functional stock management API with multi-tenancy, JWT authentication, flexible products, batch control, and stock movements.

**Architecture:** Clean Architecture with layers (Controller → Service → Repository). Multi-tenancy via tenant_id filtering. JWT + Refresh Token for authentication. PostgreSQL with JSONB for flexible product attributes.

**Tech Stack:** Spring Boot 4.0.1, Java 17, PostgreSQL, Flyway, Spring Security, JWT, Lombok, JUnit 5, Testcontainers

---

## Phase 1: Project Setup and Configuration

### Task 1.1: Configure Application Properties

**Files:**
- Modify: `src/main/resources/application.properties`
- Create: `src/main/resources/application-dev.yml`
- Create: `src/main/resources/application-test.yml`

**Step 1: Delete application.properties and create application.yml**

```bash
rm src/main/resources/application.properties
```

**Step 2: Create main application.yml**

Create `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: stockshift
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

jwt:
  secret: ${JWT_SECRET:dev-secret-key-change-in-production-must-be-at-least-256-bits-long}
  access-expiration: ${JWT_ACCESS_EXPIRATION:900000}
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}

cors:
  allowed-origins: ${ALLOWED_ORIGINS:http://localhost:3000}

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    root: INFO
    br.com.stockshift: DEBUG
```

**Step 3: Create application-dev.yml**

Create `src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:stockshift}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
  jpa:
    show-sql: true
```

**Step 4: Create application-test.yml**

Create `src/main/resources/application-test.yml`:

```yaml
spring:
  datasource:
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
    url: jdbc:tc:postgresql:16:///testdb
  jpa:
    hibernate:
      ddl-auto: validate
```

**Step 5: Commit**

```bash
git add src/main/resources/
git commit -m "config: setup application configuration files"
```

---

### Task 1.2: Add Required Dependencies

**Files:**
- Modify: `build.gradle`

**Step 1: Add JWT and additional dependencies**

Update `build.gradle` dependencies section:

```gradle
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-flyway'
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.flywaydb:flyway-database-postgresql'

	// JWT
	implementation 'io.jsonwebtoken:jjwt-api:0.12.3'
	runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.3'
	runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.3'

	// OpenAPI/Swagger
	implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'

	compileOnly 'org.projectlombok:lombok'
	developmentOnly 'org.springframework.boot:spring-boot-devtools'
	runtimeOnly 'org.postgresql:postgresql'
	annotationProcessor 'org.projectlombok:lombok'

	// Test dependencies
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testImplementation 'org.springframework.security:spring-security-test'
	testImplementation 'org.testcontainers:testcontainers:1.19.3'
	testImplementation 'org.testcontainers:postgresql:1.19.3'
	testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

**Step 2: Test dependency resolution**

```bash
./gradlew build --refresh-dependencies
```

Expected: Build successful

**Step 3: Commit**

```bash
git add build.gradle
git commit -m "build: add JWT, OpenAPI, and Testcontainers dependencies"
```

---

### Task 1.3: Create Package Structure

**Files:**
- Create directory structure

**Step 1: Create package directories**

```bash
mkdir -p src/main/java/br/com/stockshift/{config,controller,dto/request,dto/response,exception,model/entity,model/enums,repository,security,service,util}
mkdir -p src/test/java/br/com/stockshift/{controller,service,integration}
mkdir -p src/main/resources/db/migration
```

**Step 2: Commit**

```bash
git add src/
git commit -m "chore: create package structure"
```

---

## Phase 2: Database Schema (Flyway Migrations)

### Task 2.1: Create Tenants and Users Tables

**Files:**
- Create: `src/main/resources/db/migration/V1__create_tenants_and_users.sql`

**Step 1: Write migration for tenants and users**

```sql
-- Create extension for UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Tenants table
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_name VARCHAR(255) NOT NULL,
    document VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    UNIQUE(tenant_id, email)
);

-- Indexes
CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);

-- Update timestamps trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply trigger to tenants
CREATE TRIGGER update_tenants_updated_at BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Apply trigger to users
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

**Step 2: Test migration (requires PostgreSQL running)**

```bash
./gradlew flywayMigrate
```

Expected: Migration V1 applied successfully

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V1__create_tenants_and_users.sql
git commit -m "feat: add tenants and users database schema"
```

---

### Task 2.2: Create Roles and Permissions Tables

**Files:**
- Create: `src/main/resources/db/migration/V2__create_roles_and_permissions.sql`

**Step 1: Write migration**

```sql
-- Permissions table
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    scope VARCHAR(50) NOT NULL,
    description TEXT,
    UNIQUE(resource, action, scope)
);

-- Roles table
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_system_role BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, name)
);

-- Role permissions (many-to-many)
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);

-- User roles (many-to-many)
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

-- Indexes
CREATE INDEX idx_roles_tenant ON roles(tenant_id);
CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX idx_user_roles_user ON user_roles(user_id);

-- Update trigger for roles
CREATE TRIGGER update_roles_updated_at BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

**Step 2: Test migration**

```bash
./gradlew flywayMigrate
```

Expected: Migration V2 applied successfully

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V2__create_roles_and_permissions.sql
git commit -m "feat: add roles and permissions schema"
```

---

### Task 2.3: Create Refresh Tokens Table

**Files:**
- Create: `src/main/resources/db/migration/V3__create_refresh_tokens.sql`

**Step 1: Write migration**

```sql
-- Refresh tokens table
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);
```

**Step 2: Test migration**

```bash
./gradlew flywayMigrate
```

Expected: Migration V3 applied successfully

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V3__create_refresh_tokens.sql
git commit -m "feat: add refresh tokens schema"
```

---

### Task 2.4: Create Categories and Products Tables

**Files:**
- Create: `src/main/resources/db/migration/V4__create_categories_and_products.sql`

**Step 1: Write migration**

```sql
-- Categories table
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    parent_category_id UUID REFERENCES categories(id),
    attributes_schema JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, name)
);

-- Products table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    category_id UUID REFERENCES categories(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    barcode VARCHAR(100),
    barcode_type VARCHAR(20),
    sku VARCHAR(100),
    is_kit BOOLEAN NOT NULL DEFAULT false,
    attributes JSONB,
    has_expiration BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, barcode),
    UNIQUE(tenant_id, sku)
);

-- Product kits table
CREATE TABLE product_kits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    kit_product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    component_product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(kit_product_id, component_product_id)
);

-- Indexes
CREATE INDEX idx_categories_tenant ON categories(tenant_id);
CREATE INDEX idx_categories_parent ON categories(parent_category_id);
CREATE INDEX idx_categories_schema ON categories USING GIN(attributes_schema);

CREATE INDEX idx_products_tenant_category ON products(tenant_id, category_id);
CREATE INDEX idx_products_barcode ON products(barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_products_sku ON products(sku) WHERE sku IS NOT NULL;
CREATE INDEX idx_products_attributes ON products USING GIN(attributes);

CREATE INDEX idx_product_kits_kit ON product_kits(kit_product_id);
CREATE INDEX idx_product_kits_component ON product_kits(component_product_id);

-- Update triggers
CREATE TRIGGER update_categories_updated_at BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

**Step 2: Test migration**

```bash
./gradlew flywayMigrate
```

Expected: Migration V4 applied successfully

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V4__create_categories_and_products.sql
git commit -m "feat: add categories and products schema"
```

---

### Task 2.5: Create Warehouses and Batches Tables

**Files:**
- Create: `src/main/resources/db/migration/V5__create_warehouses_and_batches.sql`

**Step 1: Write migration**

```sql
-- Warehouses table
CREATE TABLE warehouses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(2) NOT NULL,
    address TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, name)
);

-- Batches table
CREATE TABLE batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    product_id UUID NOT NULL REFERENCES products(id),
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    batch_code VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    manufactured_date DATE,
    expiration_date DATE,
    cost_price DECIMAL(15, 2),
    selling_price DECIMAL(15, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, batch_code)
);

-- Indexes
CREATE INDEX idx_warehouses_tenant ON warehouses(tenant_id);

CREATE INDEX idx_batches_product_warehouse ON batches(product_id, warehouse_id);
CREATE INDEX idx_batches_expiration ON batches(expiration_date) WHERE expiration_date IS NOT NULL;
CREATE INDEX idx_batches_tenant ON batches(tenant_id);

-- Update triggers
CREATE TRIGGER update_warehouses_updated_at BEFORE UPDATE ON warehouses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_batches_updated_at BEFORE UPDATE ON batches
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

**Step 2: Test migration**

```bash
./gradlew flywayMigrate
```

Expected: Migration V5 applied successfully

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V5__create_warehouses_and_batches.sql
git commit -m "feat: add warehouses and batches schema"
```

---

### Task 2.6: Create Stock Movements Tables

**Files:**
- Create: `src/main/resources/db/migration/V6__create_stock_movements.sql`

**Step 1: Write migration**

```sql
-- Stock movements table
CREATE TABLE stock_movements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    movement_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_warehouse_id UUID REFERENCES warehouses(id),
    destination_warehouse_id UUID REFERENCES warehouses(id),
    user_id UUID NOT NULL REFERENCES users(id),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Stock movement items table
CREATE TABLE stock_movement_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    movement_id UUID NOT NULL REFERENCES stock_movements(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    batch_id UUID REFERENCES batches(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(15, 2),
    total_price DECIMAL(15, 2)
);

-- Indexes
CREATE INDEX idx_movements_tenant_type ON stock_movements(tenant_id, movement_type, status);
CREATE INDEX idx_movements_warehouse ON stock_movements(source_warehouse_id, destination_warehouse_id);
CREATE INDEX idx_movements_user ON stock_movements(user_id);
CREATE INDEX idx_movements_created ON stock_movements(created_at);

CREATE INDEX idx_movement_items_movement ON stock_movement_items(movement_id);
CREATE INDEX idx_movement_items_product ON stock_movement_items(product_id);
CREATE INDEX idx_movement_items_batch ON stock_movement_items(batch_id);

-- Update trigger
CREATE TRIGGER update_stock_movements_updated_at BEFORE UPDATE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

**Step 2: Test migration**

```bash
./gradlew flywayMigrate
```

Expected: Migration V6 applied successfully

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V6__create_stock_movements.sql
git commit -m "feat: add stock movements schema"
```

---

### Task 2.7: Create Views

**Files:**
- Create: `src/main/resources/db/migration/V7__create_views.sql`

**Step 1: Write migration**

```sql
-- Stock summary view
CREATE OR REPLACE VIEW v_stock_summary AS
SELECT
    p.id as product_id,
    p.name as product_name,
    p.tenant_id,
    w.id as warehouse_id,
    w.name as warehouse_name,
    SUM(b.quantity) as total_quantity,
    MIN(b.expiration_date) as nearest_expiration,
    AVG(b.cost_price) as avg_cost_price,
    AVG(b.selling_price) as avg_selling_price
FROM products p
JOIN batches b ON b.product_id = p.id
JOIN warehouses w ON w.id = b.warehouse_id
WHERE b.quantity > 0
GROUP BY p.id, p.name, p.tenant_id, w.id, w.name;
```

**Step 2: Test migration**

```bash
./gradlew flywayMigrate
```

Expected: Migration V7 applied successfully

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V7__create_views.sql
git commit -m "feat: add database views"
```

---

### Task 2.8: Insert Default Permissions

**Files:**
- Create: `src/main/resources/db/migration/V8__insert_default_permissions.sql`

**Step 1: Write migration**

```sql
-- Insert default permissions
INSERT INTO permissions (resource, action, scope, description) VALUES
-- Product permissions
('PRODUCT', 'CREATE', 'ALL', 'Create products'),
('PRODUCT', 'READ', 'ALL', 'View all products'),
('PRODUCT', 'UPDATE', 'ALL', 'Update products'),
('PRODUCT', 'DELETE', 'ALL', 'Delete products'),

-- Stock permissions
('STOCK', 'CREATE', 'ALL', 'Create stock movements'),
('STOCK', 'READ', 'ALL', 'View stock'),
('STOCK', 'UPDATE', 'ALL', 'Update stock'),
('STOCK', 'APPROVE', 'ALL', 'Approve stock transfers'),
('STOCK', 'APPROVE', 'OWN_WAREHOUSE', 'Approve transfers for own warehouse'),

-- Sale permissions
('SALE', 'CREATE', 'ALL', 'Create sales'),
('SALE', 'READ', 'ALL', 'View sales'),

-- User permissions
('USER', 'CREATE', 'ALL', 'Create users'),
('USER', 'READ', 'ALL', 'View users'),
('USER', 'UPDATE', 'ALL', 'Update users'),
('USER', 'DELETE', 'ALL', 'Delete users'),

-- Report permissions
('REPORT', 'READ', 'ALL', 'View all reports'),

-- Warehouse permissions
('WAREHOUSE', 'CREATE', 'ALL', 'Create warehouses'),
('WAREHOUSE', 'READ', 'ALL', 'View warehouses'),
('WAREHOUSE', 'UPDATE', 'ALL', 'Update warehouses'),
('WAREHOUSE', 'DELETE', 'ALL', 'Delete warehouses');
```

**Step 2: Test migration**

```bash
./gradlew flywayMigrate
```

Expected: Migration V8 applied successfully

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V8__insert_default_permissions.sql
git commit -m "feat: add default permissions data"
```

---

## Phase 3: Core Entities and Enums

### Task 3.1: Create Enums

**Files:**
- Create: `src/main/java/br/com/stockshift/model/enums/BarcodeType.java`
- Create: `src/main/java/br/com/stockshift/model/enums/MovementType.java`
- Create: `src/main/java/br/com/stockshift/model/enums/MovementStatus.java`
- Create: `src/main/java/br/com/stockshift/model/enums/PermissionResource.java`
- Create: `src/main/java/br/com/stockshift/model/enums/PermissionAction.java`
- Create: `src/main/java/br/com/stockshift/model/enums/PermissionScope.java`

**Step 1: Create BarcodeType enum**

```java
package br.com.stockshift.model.enums;

public enum BarcodeType {
    EXTERNAL,  // Existing barcode from supplier
    GENERATED  // Generated by the system
}
```

**Step 2: Create MovementType enum**

```java
package br.com.stockshift.model.enums;

public enum MovementType {
    PURCHASE,   // Buying from supplier
    SALE,       // Selling to customer
    TRANSFER,   // Moving between warehouses
    ADJUSTMENT, // Inventory adjustment (loss, damage, etc)
    RETURN      // Product return
}
```

**Step 3: Create MovementStatus enum**

```java
package br.com.stockshift.model.enums;

public enum MovementStatus {
    PENDING,    // Created but not executed
    IN_TRANSIT, // Product left origin, not arrived at destination
    COMPLETED,  // Movement completed
    CANCELLED   // Movement cancelled
}
```

**Step 4: Create PermissionResource enum**

```java
package br.com.stockshift.model.enums;

public enum PermissionResource {
    PRODUCT,
    STOCK,
    SALE,
    USER,
    REPORT,
    WAREHOUSE
}
```

**Step 5: Create PermissionAction enum**

```java
package br.com.stockshift.model.enums;

public enum PermissionAction {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    APPROVE
}
```

**Step 6: Create PermissionScope enum**

```java
package br.com.stockshift.model.enums;

public enum PermissionScope {
    ALL,           // All resources
    OWN_WAREHOUSE, // Only own warehouse
    OWN            // Only own resources
}
```

**Step 7: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/
git commit -m "feat: add domain enums"
```

---

### Task 3.2: Create Base Entity Classes

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/BaseEntity.java`
- Create: `src/main/java/br/com/stockshift/model/entity/TenantAwareEntity.java`

**Step 1: Create BaseEntity**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

**Step 2: Create TenantAwareEntity**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@MappedSuperclass
public abstract class TenantAwareEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
}
```

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/BaseEntity.java src/main/java/br/com/stockshift/model/entity/TenantAwareEntity.java
git commit -m "feat: add base entity classes"
```

---

### Task 3.3: Create Tenant Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/Tenant.java`
- Create: `src/main/java/br/com/stockshift/repository/TenantRepository.java`

**Step 1: Create Tenant entity**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenants")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Tenant extends BaseEntity {

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "document", nullable = false, unique = true, length = 20)
    private String document;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
```

**Step 2: Create TenantRepository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByDocument(String document);
    Optional<Tenant> findByEmail(String email);
}
```

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/Tenant.java src/main/java/br/com/stockshift/repository/TenantRepository.java
git commit -m "feat: add Tenant entity and repository"
```

---

### Task 3.4: Create User Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/User.java`
- Create: `src/main/java/br/com/stockshift/repository/UserRepository.java`

**Step 1: Create User entity**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "email"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class User extends TenantAwareEntity {

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}
```

**Step 2: Create UserRepository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);
    Optional<User> findByEmail(String email);
}
```

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/User.java src/main/java/br/com/stockshift/repository/UserRepository.java
git commit -m "feat: add User entity and repository"
```

---

### Task 3.5: Create Permission, Role Entities

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/Permission.java`
- Create: `src/main/java/br/com/stockshift/model/entity/Role.java`
- Create: `src/main/java/br/com/stockshift/repository/PermissionRepository.java`
- Create: `src/main/java/br/com/stockshift/repository/RoleRepository.java`

**Step 1: Create Permission entity**

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.PermissionAction;
import br.com.stockshift.model.enums.PermissionResource;
import br.com.stockshift.model.enums.PermissionScope;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "permissions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"resource", "action", "scope"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource", nullable = false, length = 50)
    private PermissionResource resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private PermissionAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 50)
    private PermissionScope scope;

    @Column(name = "description")
    private String description;
}
```

**Step 2: Create Role entity**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "name"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Role extends TenantAwareEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "is_system_role", nullable = false)
    private Boolean isSystemRole = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();
}
```

**Step 3: Create PermissionRepository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.enums.PermissionAction;
import br.com.stockshift.model.enums.PermissionResource;
import br.com.stockshift.model.enums.PermissionScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByResourceAndActionAndScope(
        PermissionResource resource,
        PermissionAction action,
        PermissionScope scope
    );
}
```

**Step 4: Create RoleRepository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findByTenantId(UUID tenantId);
    Optional<Role> findByTenantIdAndName(UUID tenantId, String name);
}
```

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/Permission.java src/main/java/br/com/stockshift/model/entity/Role.java src/main/java/br/com/stockshift/repository/PermissionRepository.java src/main/java/br/com/stockshift/repository/RoleRepository.java
git commit -m "feat: add Permission and Role entities with repositories"
```

---

### Task 3.6: Create RefreshToken Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/RefreshToken.java`
- Create: `src/main/java/br/com/stockshift/repository/RefreshTokenRepository.java`

**Step 1: Create RefreshToken entity**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
```

**Step 2: Create RefreshTokenRepository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
    void deleteByUser(User user);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteExpiredTokens(LocalDateTime now);
}
```

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/RefreshToken.java src/main/java/br/com/stockshift/repository/RefreshTokenRepository.java
git commit -m "feat: add RefreshToken entity and repository"
```

---

## Phase 4: Authentication and Security

### Task 4.1: Create Security Configuration

**Files:**
- Create: `src/main/java/br/com/stockshift/config/JwtProperties.java`

**Step 1: Create JwtProperties**

```java
package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {
    private String secret;
    private Long accessExpiration; // in milliseconds
    private Long refreshExpiration; // in milliseconds
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/config/JwtProperties.java
git commit -m "feat: add JWT configuration properties"
```

---

### Task 4.2: Create JWT Token Provider

**Files:**
- Create: `src/main/java/br/com/stockshift/security/JwtTokenProvider.java`

**Step 1: Create JwtTokenProvider**

```java
package br.com.stockshift.security;

import br.com.stockshift.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID userId, UUID tenantId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessExpiration());

        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("tenantId", tenantId.toString())
                .claim("email", email)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return UUID.fromString(claims.getSubject());
    }

    public UUID getTenantIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return UUID.fromString(claims.get("tenantId", String.class));
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature");
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty");
        }
        return false;
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/security/JwtTokenProvider.java
git commit -m "feat: add JWT token provider"
```

---

### Task 4.3: Create Tenant Context

**Files:**
- Create: `src/main/java/br/com/stockshift/security/TenantContext.java`

**Step 1: Create TenantContext**

```java
package br.com.stockshift.security;

import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();

    public static void setTenantId(UUID tenantId) {
        currentTenant.set(tenantId);
    }

    public static UUID getTenantId() {
        return currentTenant.get();
    }

    public static void clear() {
        currentTenant.remove();
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/security/TenantContext.java
git commit -m "feat: add tenant context for multi-tenancy"
```

---

### Task 4.4: Create Custom UserDetails

**Files:**
- Create: `src/main/java/br/com/stockshift/security/UserPrincipal.java`
- Create: `src/main/java/br/com/stockshift/security/CustomUserDetailsService.java`

**Step 1: Create UserPrincipal**

```java
package br.com.stockshift.security;

import br.com.stockshift.model.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private UUID id;
    private UUID tenantId;
    private String email;
    private String password;
    private boolean active;
    private Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(User user) {
        Collection<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .collect(Collectors.toList());

        return new UserPrincipal(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getPassword(),
                user.getIsActive(),
                authorities
        );
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
```

**Step 2: Create CustomUserDetailsService**

```java
package br.com.stockshift.security;

import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return UserPrincipal.create(user);
    }

    @Transactional
    public UserDetails loadUserById(String id) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));

        return UserPrincipal.create(user);
    }
}
```

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/security/UserPrincipal.java src/main/java/br/com/stockshift/security/CustomUserDetailsService.java
git commit -m "feat: add custom UserDetails implementation"
```

---

### Task 4.5: Create JWT Authentication Filter

**Files:**
- Create: `src/main/java/br/com/stockshift/security/JwtAuthenticationFilter.java`

**Step 1: Create JwtAuthenticationFilter**

Create `src/main/java/br/com/stockshift/security/JwtAuthenticationFilter.java`:

```java
package br.com.stockshift.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                UUID userId = tokenProvider.getUserIdFromToken(jwt);
                UUID tenantId = tokenProvider.getTenantIdFromToken(jwt);

                // Set tenant context
                TenantContext.setTenantId(tenantId);

                UserDetails userDetails = userDetailsService.loadUserById(userId.toString());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/security/JwtAuthenticationFilter.java
git commit -m "feat: add JWT authentication filter"
```

---

### Task 4.6: Integrate JWT Filter into Security Configuration

**Files:**
- Modify: `src/main/java/br/com/stockshift/config/SecurityConfig.java`

**Step 1: Add JWT filter to security chain**

Update `SecurityConfig.java` to add the JWT filter:

```java
package br.com.stockshift.config;

import br.com.stockshift.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Swagger UI
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                // Health check
                .requestMatchers("/actuator/health/**").permitAll()
                // Auth endpoints
                .requestMatchers("/api/auth/**").permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/config/SecurityConfig.java
git commit -m "feat: integrate JWT filter into security chain"
```

---

## Phase 5: Exception Handling and DTOs

### Task 5.1: Create Custom Exception Classes

**Files:**
- Create: `src/main/java/br/com/stockshift/exception/BusinessException.java`
- Create: `src/main/java/br/com/stockshift/exception/ResourceNotFoundException.java`
- Create: `src/main/java/br/com/stockshift/exception/UnauthorizedException.java`

**Step 1: Create BusinessException**

Create `src/main/java/br/com/stockshift/exception/BusinessException.java`:

```java
package br.com.stockshift.exception;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Step 2: Create ResourceNotFoundException**

Create `src/main/java/br/com/stockshift/exception/ResourceNotFoundException.java`:

```java
package br.com.stockshift.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s not found with %s: %s", resource, field, value));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

**Step 3: Create UnauthorizedException**

Create `src/main/java/br/com/stockshift/exception/UnauthorizedException.java`:

```java
package br.com.stockshift.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
```

**Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/exception/
git commit -m "feat: add custom exception classes"
```

---

### Task 5.2: Create Global Exception Handler

**Files:**
- Create: `src/main/java/br/com/stockshift/exception/GlobalExceptionHandler.java`

**Step 1: Create GlobalExceptionHandler**

Create `src/main/java/br/com/stockshift/exception/GlobalExceptionHandler.java`:

```java
package br.com.stockshift.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            WebRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            UnauthorizedException ex,
            WebRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            WebRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Business Rule Violation")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            WebRequest request
    ) {
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input")
                .path(request.getDescription(false).replace("uri=", ""))
                .validationErrors(validationErrors)
                .build();

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("You don't have permission to access this resource")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            BadCredentialsException ex,
            WebRequest request
    ) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Invalid email or password")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            WebRequest request
    ) {
        log.error("Unexpected error occurred", ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

**Step 2: Verify compilation (will fail - need ErrorResponse DTO)**

Run: `./gradlew compileJava`
Expected: FAILED - ErrorResponse class not found

---

### Task 5.3: Create Error Response DTO

**Files:**
- Create: `src/main/java/br/com/stockshift/exception/ErrorResponse.java`

**Step 1: Create ErrorResponse DTO**

Create `src/main/java/br/com/stockshift/exception/ErrorResponse.java`:

```java
package br.com.stockshift.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private LocalDateTime timestamp;
    private Integer status;
    private String error;
    private String message;
    private String path;
    private Map<String, String> validationErrors;
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/exception/
git commit -m "feat: add global exception handler and error response DTO"
```

---

### Task 5.4: Create Base API Response DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/ApiResponse.java`

**Step 1: Create ApiResponse DTO**

Create `src/main/java/br/com/stockshift/dto/ApiResponse.java`:

```java
package br.com.stockshift.dto;

import com.fasterxml.jackson.annotation.jsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private Boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();
    }

    public static ApiResponse<Void> error(String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .build();
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/
git commit -m "feat: add generic API response DTO"
```

---

## Phase 6: Authentication Service and Controller

### Task 6.1: Create Authentication DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/auth/LoginRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/auth/LoginResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/auth/RefreshTokenRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/auth/RefreshTokenResponse.java`

**Step 1: Create LoginRequest DTO**

Create `src/main/java/br/com/stockshift/dto/auth/LoginRequest.java`:

```java
package br.com.stockshift.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
```

**Step 2: Create LoginResponse DTO**

Create `src/main/java/br/com/stockshift/dto/auth/LoginResponse.java`:

```java
package br.com.stockshift.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long expiresIn; // in milliseconds
    private UUID userId;
    private String email;
    private String fullName;
}
```

**Step 3: Create RefreshTokenRequest DTO**

Create `src/main/java/br/com/stockshift/dto/auth/RefreshTokenRequest.java`:

```java
package br.com.stockshift.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
```

**Step 4: Create RefreshTokenResponse DTO**

Create `src/main/java/br/com/stockshift/dto/auth/RefreshTokenResponse.java`:

```java
package br.com.stockshift.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private Long expiresIn; // in milliseconds
}
```

**Step 5: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/auth/
git commit -m "feat: add authentication DTOs"
```

---

### Task 6.2: Create RefreshToken Service

**Files:**
- Create: `src/main/java/br/com/stockshift/service/RefreshTokenService.java`

**Step 1: Create RefreshTokenService**

Create `src/main/java/br/com/stockshift/service/RefreshTokenService.java`:

```java
package br.com.stockshift.service;

import br.com.stockshift.config.JwtProperties;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Revoke existing refresh tokens for this user
        refreshTokenRepository.deleteByUserId(user.getId());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUserId(user.getId());
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpiration() / 1000));

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token expired");
        }

        return refreshToken;
    }

    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent(refreshTokenRepository::delete);
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/service/RefreshTokenService.java
git commit -m "feat: add refresh token service"
```

---

### Task 6.3: Create Authentication Service

**Files:**
- Create: `src/main/java/br/com/stockshift/service/AuthService.java`

**Step 1: Create AuthService**

Create `src/main/java/br/com/stockshift/service/AuthService.java`:

```java
package br.com.stockshift.service;

import br.com.stockshift.config.JwtProperties;
import br.com.stockshift.dto.auth.LoginRequest;
import br.com.stockshift.dto.auth.LoginResponse;
import br.com.stockshift.dto.auth.RefreshTokenRequest;
import br.com.stockshift.dto.auth.RefreshTokenResponse;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // Load user details
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UnauthorizedException("User not found"));

            if (!user.getIsActive()) {
                throw new UnauthorizedException("User account is disabled");
            }

            // Generate tokens
            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(),
                    user.getTenantId(),
                    user.getEmail()
            );

            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

            // Update last login
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken.getToken())
                    .tokenType("Bearer")
                    .expiresIn(jwtProperties.getAccessExpiration())
                    .userId(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .build();

        } catch (AuthenticationException e) {
            log.error("Authentication failed for user: {}", request.getEmail());
            throw new UnauthorizedException("Invalid email or password");
        }
    }

    @Transactional
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        // Validate refresh token
        RefreshToken refreshToken = refreshTokenService.validateRefreshToken(request.getRefreshToken());

        // Load user
        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.getIsActive()) {
            throw new UnauthorizedException("User account is disabled");
        }

        // Generate new access token
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getTenantId(),
                user.getEmail()
        );

        return RefreshTokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessExpiration())
                .build();
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenService.revokeRefreshToken(refreshTokenValue);
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/service/AuthService.java
git commit -m "feat: add authentication service with login/refresh/logout"
```

---

### Task 6.4: Create Authentication Controller

**Files:**
- Create: `src/main/java/br/com/stockshift/controller/AuthController.java`

**Step 1: Create AuthController**

Create `src/main/java/br/com/stockshift/controller/AuthController.java`:

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.auth.LoginRequest;
import br.com.stockshift.dto.auth.LoginResponse;
import br.com.stockshift.dto.auth.RefreshTokenRequest;
import br.com.stockshift.dto.auth.RefreshTokenResponse;
import br.com.stockshift.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and return access token + refresh token")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token", description = "Generate new access token using refresh token")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoke refresh token and logout user")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/AuthController.java
git commit -m "feat: add authentication REST controller"
```

---

### Task 6.5: Add Missing Repository Methods

**Files:**
- Modify: `src/main/java/br/com/stockshift/repository/RefreshTokenRepository.java`
- Modify: `src/main/java/br/com/stockshift/repository/UserRepository.java`

**Step 1: Add methods to RefreshTokenRepository**

Update `src/main/java/br/com/stockshift/repository/RefreshTokenRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUserId(UUID userId);
}
```

**Step 2: Add findByEmail to UserRepository**

Update `src/main/java/br/com/stockshift/repository/UserRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/
git commit -m "feat: add repository query methods for authentication"
```

---

## Phase 7: Product Management

### Task 7.1: Create Product and Category Entities

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/Category.java`
- Create: `src/main/java/br/com/stockshift/model/entity/Product.java`
- Create: `src/main/java/br/com/stockshift/model/entity/ProductKit.java`

**Step 1: Create Category Entity**

Create `src/main/java/br/com/stockshift/model/entity/Category.java`:

```java
package br.com.stockshift.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "categories", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "name"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Category extends TenantAwareEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private Category parentCategory;

    @Type(JsonType.class)
    @Column(name = "attributes_schema", columnDefinition = "jsonb")
    private JsonNode attributesSchema;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;
}
```

**Step 2: Create Product Entity**

Create `src/main/java/br/com/stockshift/model/entity/Product.java`:

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.BarcodeType;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "products", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "barcode"}),
    @UniqueConstraint(columnNames = {"tenant_id", "sku"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Product extends TenantAwareEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "barcode_type", length = 20)
    private BarcodeType barcodeType;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "is_kit", nullable = false)
    private Boolean isKit = false;

    @Type(JsonType.class)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private JsonNode attributes;

    @Column(name = "has_expiration", nullable = false)
    private Boolean hasExpiration = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;
}
```

**Step 3: Create ProductKit Entity**

Create `src/main/java/br/com/stockshift/model/entity/ProductKit.java`:

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_kits", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"kit_product_id", "component_product_id"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ProductKit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kit_product_id", nullable = false)
    private Product kitProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_product_id", nullable = false)
    private Product componentProduct;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
```

**Step 4: Add Hibernate Types dependency to build.gradle**

This step requires adding the `hibernate-types` library for JSONB support.

Edit `build.gradle` and add to dependencies:
```gradle
implementation 'io.hypersistence:hypersistence-utils-hibernate-63:3.7.0'
```

**Step 5: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/Category.java src/main/java/br/com/stockshift/model/entity/Product.java src/main/java/br/com/stockshift/model/entity/ProductKit.java build.gradle
git commit -m "feat: add Product, Category and ProductKit entities"
```

---

### Task 7.2: Create Product and Category Repositories

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/CategoryRepository.java`
- Create: `src/main/java/br/com/stockshift/repository/ProductRepository.java`
- Create: `src/main/java/br/com/stockshift/repository/ProductKitRepository.java`

**Step 1: Create CategoryRepository**

Create `src/main/java/br/com/stockshift/repository/CategoryRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL")
    List<Category> findAllByTenantId(UUID tenantId);

    @Query("SELECT c FROM Category c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<Category> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT c FROM Category c WHERE c.parentCategory.id = :parentId AND c.deletedAt IS NULL")
    List<Category> findByParentCategoryId(UUID parentId);
}
```

**Step 2: Create ProductRepository**

Create `src/main/java/br/com/stockshift/repository/ProductRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL")
    List<Product> findAllByTenantId(UUID tenantId);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.active = :active AND p.deletedAt IS NULL")
    List<Product> findByTenantIdAndActive(UUID tenantId, Boolean active);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.category.id = :categoryId AND p.deletedAt IS NULL")
    List<Product> findByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "p.deletedAt IS NULL")
    List<Product> searchByTenantId(@Param("tenantId") UUID tenantId, @Param("search") String search);

    @Query("SELECT p FROM Product p WHERE p.barcode = :barcode AND p.tenantId = :tenantId AND p.deletedAt IS NULL")
    Optional<Product> findByBarcodeAndTenantId(String barcode, UUID tenantId);

    @Query("SELECT p FROM Product p WHERE p.sku = :sku AND p.tenantId = :tenantId AND p.deletedAt IS NULL")
    Optional<Product> findBySkuAndTenantId(String sku, UUID tenantId);
}
```

**Step 3: Create ProductKitRepository**

Create `src/main/java/br/com/stockshift/repository/ProductKitRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.ProductKit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductKitRepository extends JpaRepository<ProductKit, UUID> {

    @Query("SELECT pk FROM ProductKit pk WHERE pk.kitProduct.id = :kitProductId")
    List<ProductKit> findByKitProductId(UUID kitProductId);

    @Query("SELECT pk FROM ProductKit pk WHERE pk.componentProduct.id = :componentProductId")
    List<ProductKit> findByComponentProductId(UUID componentProductId);
}
```

**Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/
git commit -m "feat: add Product, Category and ProductKit repositories"
```

---

### Task 7.3: Create Product DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/product/CategoryRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/product/CategoryResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/product/ProductRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/product/ProductResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/product/ProductKitRequest.java`

**Step 1: Create CategoryRequest DTO**

Create `src/main/java/br/com/stockshift/dto/product/CategoryRequest.java`:

```java
package br.com.stockshift.dto.product;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {
    @NotBlank(message = "Category name is required")
    private String name;

    private String description;
    private UUID parentCategoryId;
    private JsonNode attributesSchema;
}
```

**Step 2: Create CategoryResponse DTO**

Create `src/main/java/br/com/stockshift/dto/product/CategoryResponse.java`:

```java
package br.com.stockshift.dto.product;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID parentCategoryId;
    private String parentCategoryName;
    private JsonNode attributesSchema;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Step 3: Create ProductRequest DTO**

Create `src/main/java/br/com/stockshift/dto/product/ProductRequest.java`:

```java
package br.com.stockshift.dto.product;

import br.com.stockshift.model.enums.BarcodeType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    private String name;

    private String description;
    private UUID categoryId;
    private String barcode;
    private BarcodeType barcodeType;
    private String sku;
    private Boolean isKit = false;
    private JsonNode attributes;
    private Boolean hasExpiration = false;
    private Boolean active = true;
}
```

**Step 4: Create ProductResponse DTO**

Create `src/main/java/br/com/stockshift/dto/product/ProductResponse.java`:

```java
package br.com.stockshift.dto.product;

import br.com.stockshift.model.enums.BarcodeType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID categoryId;
    private String categoryName;
    private String barcode;
    private BarcodeType barcodeType;
    private String sku;
    private Boolean isKit;
    private JsonNode attributes;
    private Boolean hasExpiration;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Step 5: Create ProductKitRequest DTO**

Create `src/main/java/br/com/stockshift/dto/product/ProductKitRequest.java`:

```java
package br.com.stockshift.dto.product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductKitRequest {
    @NotNull(message = "Component product ID is required")
    private UUID componentProductId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;
}
```

**Step 6: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/product/
git commit -m "feat: add Product and Category DTOs"
```

---

### Task 7.4: Create Category and Product Services

**Files:**
- Create: `src/main/java/br/com/stockshift/service/CategoryService.java`
- Create: `src/main/java/br/com/stockshift/service/ProductService.java`

**Step 1: Create CategoryService**

Create `src/main/java/br/com/stockshift/service/CategoryService.java`:

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.product.CategoryRequest;
import br.com.stockshift.dto.product.CategoryResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Validate parent category if provided
        Category parentCategory = null;
        if (request.getParentCategoryId() != null) {
            parentCategory = categoryRepository.findByTenantIdAndId(tenantId, request.getParentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category", "id", request.getParentCategoryId()));
        }

        Category category = new Category();
        category.setTenantId(tenantId);
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setParentCategory(parentCategory);
        category.setAttributesSchema(request.getAttributesSchema());

        Category saved = categoryRepository.save(category);
        log.info("Created category {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return categoryRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Category category = categoryRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        return mapToResponse(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findByParentId(UUID parentId) {
        return categoryRepository.findByParentCategoryId(parentId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        Category category = categoryRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Validate parent category if provided
        if (request.getParentCategoryId() != null) {
            if (request.getParentCategoryId().equals(id)) {
                throw new BusinessException("Category cannot be its own parent");
            }
            Category parentCategory = categoryRepository.findByTenantIdAndId(tenantId, request.getParentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category", "id", request.getParentCategoryId()));
            category.setParentCategory(parentCategory);
        } else {
            category.setParentCategory(null);
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setAttributesSchema(request.getAttributesSchema());

        Category updated = categoryRepository.save(category);
        log.info("Updated category {} for tenant {}", id, tenantId);

        return mapToResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Category category = categoryRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Soft delete
        category.setDeletedAt(LocalDateTime.now());
        categoryRepository.save(category);

        log.info("Soft deleted category {} for tenant {}", id, tenantId);
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentCategoryId(category.getParentCategory() != null ? category.getParentCategory().getId() : null)
                .parentCategoryName(category.getParentCategory() != null ? category.getParentCategory().getName() : null)
                .attributesSchema(category.getAttributesSchema())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
```

**Step 2: Create ProductService**

Create `src/main/java/br/com/stockshift/service/ProductService.java`:

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.dto.product.ProductResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Validate unique barcode if provided
        if (request.getBarcode() != null) {
            productRepository.findByBarcodeAndTenantId(request.getBarcode(), tenantId)
                    .ifPresent(p -> {
                        throw new BusinessException("Product with barcode " + request.getBarcode() + " already exists");
                    });
        }

        // Validate unique SKU if provided
        if (request.getSku() != null) {
            productRepository.findBySkuAndTenantId(request.getSku(), tenantId)
                    .ifPresent(p -> {
                        throw new BusinessException("Product with SKU " + request.getSku() + " already exists");
                    });
        }

        // Validate category if provided
        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findByTenantIdAndId(tenantId, request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
        }

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(category);
        product.setBarcode(request.getBarcode());
        product.setBarcodeType(request.getBarcodeType());
        product.setSku(request.getSku());
        product.setIsKit(request.getIsKit() != null ? request.getIsKit() : false);
        product.setAttributes(request.getAttributes());
        product.setHasExpiration(request.getHasExpiration() != null ? request.getHasExpiration() : false);
        product.setActive(request.getActive() != null ? request.getActive() : true);

        Product saved = productRepository.save(product);
        log.info("Created product {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return productRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Product product = productRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findByCategory(UUID categoryId) {
        UUID tenantId = TenantContext.getTenantId();
        return productRepository.findByTenantIdAndCategoryId(tenantId, categoryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findActive(Boolean active) {
        UUID tenantId = TenantContext.getTenantId();
        return productRepository.findByTenantIdAndActive(tenantId, active).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> search(String searchTerm) {
        UUID tenantId = TenantContext.getTenantId();
        return productRepository.searchByTenantId(tenantId, searchTerm).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse findByBarcode(String barcode) {
        UUID tenantId = TenantContext.getTenantId();
        Product product = productRepository.findByBarcodeAndTenantId(barcode, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "barcode", barcode));
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse findBySku(String sku) {
        UUID tenantId = TenantContext.getTenantId();
        Product product = productRepository.findBySkuAndTenantId(sku, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        Product product = productRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Validate unique barcode if changed
        if (request.getBarcode() != null && !request.getBarcode().equals(product.getBarcode())) {
            productRepository.findByBarcodeAndTenantId(request.getBarcode(), tenantId)
                    .ifPresent(p -> {
                        throw new BusinessException("Product with barcode " + request.getBarcode() + " already exists");
                    });
        }

        // Validate unique SKU if changed
        if (request.getSku() != null && !request.getSku().equals(product.getSku())) {
            productRepository.findBySkuAndTenantId(request.getSku(), tenantId)
                    .ifPresent(p -> {
                        throw new BusinessException("Product with SKU " + request.getSku() + " already exists");
                    });
        }

        // Validate category if provided
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findByTenantIdAndId(tenantId, request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBarcode(request.getBarcode());
        product.setBarcodeType(request.getBarcodeType());
        product.setSku(request.getSku());
        product.setIsKit(request.getIsKit() != null ? request.getIsKit() : false);
        product.setAttributes(request.getAttributes());
        product.setHasExpiration(request.getHasExpiration() != null ? request.getHasExpiration() : false);
        product.setActive(request.getActive() != null ? request.getActive() : true);

        Product updated = productRepository.save(product);
        log.info("Updated product {} for tenant {}", id, tenantId);

        return mapToResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Product product = productRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Soft delete
        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("Soft deleted product {} for tenant {}", id, tenantId);
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .barcode(product.getBarcode())
                .barcodeType(product.getBarcodeType())
                .sku(product.getSku())
                .isKit(product.getIsKit())
                .attributes(product.getAttributes())
                .hasExpiration(product.getHasExpiration())
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/service/CategoryService.java src/main/java/br/com/stockshift/service/ProductService.java
git commit -m "feat: add Category and Product services with CRUD operations"
```

---

### Task 7.5: Create Category and Product Controllers

**Files:**
- Create: `src/main/java/br/com/stockshift/controller/CategoryController.java`
- Create: `src/main/java/br/com/stockshift/controller/ProductController.java`

**Step 1: Create CategoryController**

Create `src/main/java/br/com/stockshift/controller/CategoryController.java`:

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.product.CategoryRequest;
import br.com.stockshift.dto.product.CategoryResponse;
import br.com.stockshift.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Category management endpoints")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CATEGORY_CREATE', 'ADMIN')")
    @Operation(summary = "Create a new category")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CATEGORY_READ', 'ADMIN')")
    @Operation(summary = "Get all categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> findAll() {
        List<CategoryResponse> categories = categoryService.findAll();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATEGORY_READ', 'ADMIN')")
    @Operation(summary = "Get category by ID")
    public ResponseEntity<ApiResponse<CategoryResponse>> findById(@PathVariable UUID id) {
        CategoryResponse response = categoryService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/parent/{parentId}")
    @PreAuthorize("hasAnyAuthority('CATEGORY_READ', 'ADMIN')")
    @Operation(summary = "Get categories by parent ID")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> findByParentId(@PathVariable UUID parentId) {
        List<CategoryResponse> categories = categoryService.findByParentId(parentId);
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATEGORY_UPDATE', 'ADMIN')")
    @Operation(summary = "Update category")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CATEGORY_DELETE', 'ADMIN')")
    @Operation(summary = "Delete category (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }
}
```

**Step 2: Create ProductController**

Create `src/main/java/br/com/stockshift/controller/ProductController.java`:

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.dto.product.ProductResponse;
import br.com.stockshift.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management endpoints")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'ADMIN')")
    @Operation(summary = "Create a new product")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PRODUCT_READ', 'ADMIN')")
    @Operation(summary = "Get all products")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> findAll() {
        List<ProductResponse> products = productService.findAll();
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_READ', 'ADMIN')")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ApiResponse<ProductResponse>> findById(@PathVariable UUID id) {
        ProductResponse response = productService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_READ', 'ADMIN')")
    @Operation(summary = "Get products by category")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> findByCategory(@PathVariable UUID categoryId) {
        List<ProductResponse> products = productService.findByCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/active/{active}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_READ', 'ADMIN')")
    @Operation(summary = "Get products by active status")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> findActive(@PathVariable Boolean active) {
        List<ProductResponse> products = productService.findActive(active);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('PRODUCT_READ', 'ADMIN')")
    @Operation(summary = "Search products by name, SKU or barcode")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> search(@RequestParam String q) {
        List<ProductResponse> products = productService.search(q);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/barcode/{barcode}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_READ', 'ADMIN')")
    @Operation(summary = "Get product by barcode")
    public ResponseEntity<ApiResponse<ProductResponse>> findByBarcode(@PathVariable String barcode) {
        ProductResponse response = productService.findByBarcode(barcode);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/sku/{sku}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_READ', 'ADMIN')")
    @Operation(summary = "Get product by SKU")
    public ResponseEntity<ApiResponse<ProductResponse>> findBySku(@PathVariable String sku) {
        ProductResponse response = productService.findBySku(sku);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_UPDATE', 'ADMIN')")
    @Operation(summary = "Update product")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_DELETE', 'ADMIN')")
    @Operation(summary = "Delete product (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/CategoryController.java src/main/java/br/com/stockshift/controller/ProductController.java
git commit -m "feat: add Category and Product REST controllers"
```

---

## Phase 8: Warehouse and Batch Management

### Task 8.1: Create Warehouse and Batch Entities

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/Warehouse.java`
- Create: `src/main/java/br/com/stockshift/model/entity/Batch.java`

**Step 1: Create Warehouse Entity**

Create `src/main/java/br/com/stockshift/model/entity/Warehouse.java`:

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "warehouses", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "name"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Warehouse extends TenantAwareEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 2)
    private String state;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
```

**Step 2: Create Batch Entity**

Create `src/main/java/br/com/stockshift/model/entity/Batch.java`:

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "batches", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "batch_code"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Batch extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "batch_code", nullable = false, length = 100)
    private String batchCode;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "manufactured_date")
    private LocalDate manufacturedDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "cost_price", precision = 15, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", precision = 15, scale = 2)
    private BigDecimal sellingPrice;
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/Warehouse.java src/main/java/br/com/stockshift/model/entity/Batch.java
git commit -m "feat: add Warehouse and Batch entities with optimistic locking"
```

---

### Task 8.2: Create Warehouse and Batch Repositories

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/WarehouseRepository.java`
- Create: `src/main/java/br/com/stockshift/repository/BatchRepository.java`

**Step 1: Create WarehouseRepository**

Create `src/main/java/br/com/stockshift/repository/WarehouseRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    @Query("SELECT w FROM Warehouse w WHERE w.tenantId = :tenantId")
    List<Warehouse> findAllByTenantId(UUID tenantId);

    @Query("SELECT w FROM Warehouse w WHERE w.tenantId = :tenantId AND w.id = :id")
    Optional<Warehouse> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT w FROM Warehouse w WHERE w.tenantId = :tenantId AND w.isActive = :isActive")
    List<Warehouse> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive);

    @Query("SELECT w FROM Warehouse w WHERE w.tenantId = :tenantId AND w.name = :name")
    Optional<Warehouse> findByTenantIdAndName(UUID tenantId, String name);
}
```

**Step 2: Create BatchRepository**

Create `src/main/java/br/com/stockshift/repository/BatchRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchRepository extends JpaRepository<Batch, UUID> {

    @Query("SELECT b FROM Batch b WHERE b.tenantId = :tenantId")
    List<Batch> findAllByTenantId(UUID tenantId);

    @Query("SELECT b FROM Batch b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<Batch> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT b FROM Batch b WHERE b.tenantId = :tenantId AND b.batchCode = :batchCode")
    Optional<Batch> findByTenantIdAndBatchCode(UUID tenantId, String batchCode);

    @Query("SELECT b FROM Batch b WHERE b.warehouse.id = :warehouseId AND b.tenantId = :tenantId")
    List<Batch> findByWarehouseIdAndTenantId(UUID warehouseId, UUID tenantId);

    @Query("SELECT b FROM Batch b WHERE b.product.id = :productId AND b.tenantId = :tenantId")
    List<Batch> findByProductIdAndTenantId(UUID productId, UUID tenantId);

    @Query("SELECT b FROM Batch b WHERE b.product.id = :productId AND b.warehouse.id = :warehouseId AND b.tenantId = :tenantId")
    List<Batch> findByProductIdAndWarehouseIdAndTenantId(UUID productId, UUID warehouseId, UUID tenantId);

    @Query("SELECT b FROM Batch b WHERE b.expirationDate IS NOT NULL AND b.expirationDate BETWEEN :startDate AND :endDate AND b.tenantId = :tenantId ORDER BY b.expirationDate ASC")
    List<Batch> findExpiringBatches(LocalDate startDate, LocalDate endDate, UUID tenantId);

    @Query("SELECT b FROM Batch b WHERE b.quantity <= :threshold AND b.tenantId = :tenantId")
    List<Batch> findLowStock(Integer threshold, UUID tenantId);
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/
git commit -m "feat: add Warehouse and Batch repositories with stock queries"
```

---

### Task 8.3: Create Warehouse and Batch DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/warehouse/WarehouseRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/warehouse/WarehouseResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/warehouse/BatchRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/warehouse/BatchResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/warehouse/StockSummaryResponse.java`

**Step 1: Create warehouse DTO directory**

```bash
mkdir -p src/main/java/br/com/stockshift/dto/warehouse
```

**Step 2: Create WarehouseRequest**

Create `src/main/java/br/com/stockshift/dto/warehouse/WarehouseRequest.java`:

```java
package br.com.stockshift.dto.warehouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseRequest {
    @NotBlank(message = "Warehouse name is required")
    private String name;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @NotBlank(message = "State is required")
    @Pattern(regexp = "[A-Z]{2}", message = "State must be 2 uppercase letters")
    private String state;

    private String address;

    @Builder.Default
    private Boolean isActive = true;
}
```

**Step 3: Create WarehouseResponse**

Create `src/main/java/br/com/stockshift/dto/warehouse/WarehouseResponse.java`:

```java
package br.com.stockshift.dto.warehouse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseResponse {
    private UUID id;
    private String name;
    private String city;
    private String state;
    private String address;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Step 4: Create BatchRequest**

Create `src/main/java/br/com/stockshift/dto/warehouse/BatchRequest.java`:

```java
package br.com.stockshift.dto.warehouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRequest {
    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;

    @NotBlank(message = "Batch code is required")
    private String batchCode;

    @NotNull(message = "Quantity is required")
    @PositiveOrZero(message = "Quantity must be zero or positive")
    private Integer quantity;

    private LocalDate manufacturedDate;
    private LocalDate expirationDate;

    @PositiveOrZero(message = "Cost price must be zero or positive")
    private BigDecimal costPrice;

    @PositiveOrZero(message = "Selling price must be zero or positive")
    private BigDecimal sellingPrice;
}
```

**Step 5: Create BatchResponse**

Create `src/main/java/br/com/stockshift/dto/warehouse/BatchResponse.java`:

```java
package br.com.stockshift.dto.warehouse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID warehouseId;
    private String warehouseName;
    private String batchCode;
    private Integer quantity;
    private LocalDate manufacturedDate;
    private LocalDate expirationDate;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Step 6: Create StockSummaryResponse**

Create `src/main/java/br/com/stockshift/dto/warehouse/StockSummaryResponse.java`:

```java
package br.com.stockshift.dto.warehouse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSummaryResponse {
    private UUID productId;
    private String productName;
    private UUID warehouseId;
    private String warehouseName;
    private Integer totalQuantity;
    private LocalDate nearestExpiration;
    private BigDecimal avgCostPrice;
    private BigDecimal avgSellingPrice;
}
```

**Step 7: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/warehouse/
git commit -m "feat: add Warehouse and Batch DTOs with validation"
```

---

### Task 8.4: Create Warehouse and Batch Services

**Files:**
- Create: `src/main/java/br/com/stockshift/service/WarehouseService.java`
- Create: `src/main/java/br/com/stockshift/service/BatchService.java`

**Step 1: Create WarehouseService**

Create `src/main/java/br/com/stockshift/service/WarehouseService.java`:

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.warehouse.WarehouseRequest;
import br.com.stockshift.dto.warehouse.WarehouseResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Validate unique name
        warehouseRepository.findByTenantIdAndName(tenantId, request.getName())
                .ifPresent(w -> {
                    throw new BusinessException("Warehouse with name " + request.getName() + " already exists");
                });

        Warehouse warehouse = new Warehouse();
        warehouse.setTenantId(tenantId);
        warehouse.setName(request.getName());
        warehouse.setCity(request.getCity());
        warehouse.setState(request.getState());
        warehouse.setAddress(request.getAddress());
        warehouse.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Created warehouse {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return warehouseRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));
        return mapToResponse(warehouse);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findActive(Boolean isActive) {
        UUID tenantId = TenantContext.getTenantId();
        return warehouseRepository.findByTenantIdAndIsActive(tenantId, isActive).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public WarehouseResponse update(UUID id, WarehouseRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));

        // Validate unique name if changed
        if (!warehouse.getName().equals(request.getName())) {
            warehouseRepository.findByTenantIdAndName(tenantId, request.getName())
                    .ifPresent(w -> {
                        throw new BusinessException("Warehouse with name " + request.getName() + " already exists");
                    });
        }

        warehouse.setName(request.getName());
        warehouse.setCity(request.getCity());
        warehouse.setState(request.getState());
        warehouse.setAddress(request.getAddress());
        warehouse.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        Warehouse updated = warehouseRepository.save(warehouse);
        log.info("Updated warehouse {} for tenant {}", id, tenantId);

        return mapToResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));

        warehouseRepository.delete(warehouse);
        log.info("Deleted warehouse {} for tenant {}", id, tenantId);
    }

    private WarehouseResponse mapToResponse(Warehouse warehouse) {
        return WarehouseResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .city(warehouse.getCity())
                .state(warehouse.getState())
                .address(warehouse.getAddress())
                .isActive(warehouse.getIsActive())
                .createdAt(warehouse.getCreatedAt())
                .updatedAt(warehouse.getUpdatedAt())
                .build();
    }
}
```

**Step 2: Create BatchService**

Create `src/main/java/br/com/stockshift/service/BatchService.java`:

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.warehouse.BatchRequest;
import br.com.stockshift.dto.warehouse.BatchResponse;
import br.com.stockshift.dto.warehouse.StockSummaryResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchService {

    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional
    public BatchResponse create(BatchRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Validate unique batch code
        batchRepository.findByTenantIdAndBatchCode(tenantId, request.getBatchCode())
                .ifPresent(b -> {
                    throw new BusinessException("Batch with code " + request.getBatchCode() + " already exists");
                });

        // Validate product
        Product product = productRepository.findByTenantIdAndId(tenantId, request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        // Validate warehouse
        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", request.getWarehouseId()));

        // Validate expiration date if product has expiration
        if (product.getHasExpiration() && request.getExpirationDate() == null) {
            throw new BusinessException("Expiration date is required for products with expiration tracking");
        }

        Batch batch = new Batch();
        batch.setTenantId(tenantId);
        batch.setProduct(product);
        batch.setWarehouse(warehouse);
        batch.setBatchCode(request.getBatchCode());
        batch.setQuantity(request.getQuantity());
        batch.setManufacturedDate(request.getManufacturedDate());
        batch.setExpirationDate(request.getExpirationDate());
        batch.setCostPrice(request.getCostPrice());
        batch.setSellingPrice(request.getSellingPrice());

        Batch saved = batchRepository.save(batch);
        log.info("Created batch {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return batchRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BatchResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Batch batch = batchRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", id));
        return mapToResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findByWarehouse(UUID warehouseId) {
        UUID tenantId = TenantContext.getTenantId();
        return batchRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findByProduct(UUID productId) {
        UUID tenantId = TenantContext.getTenantId();
        return batchRepository.findByProductIdAndTenantId(productId, tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findExpiringBatches(Integer daysAhead) {
        UUID tenantId = TenantContext.getTenantId();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);

        return batchRepository.findExpiringBatches(startDate, endDate, tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findLowStock(Integer threshold) {
        UUID tenantId = TenantContext.getTenantId();
        return batchRepository.findLowStock(threshold, tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BatchResponse update(UUID id, BatchRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        Batch batch = batchRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", id));

        // Validate unique batch code if changed
        if (!batch.getBatchCode().equals(request.getBatchCode())) {
            batchRepository.findByTenantIdAndBatchCode(tenantId, request.getBatchCode())
                    .ifPresent(b -> {
                        throw new BusinessException("Batch with code " + request.getBatchCode() + " already exists");
                    });
        }

        // Validate product if changed
        if (!batch.getProduct().getId().equals(request.getProductId())) {
            Product product = productRepository.findByTenantIdAndId(tenantId, request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));
            batch.setProduct(product);
        }

        // Validate warehouse if changed
        if (!batch.getWarehouse().getId().equals(request.getWarehouseId())) {
            Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, request.getWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", request.getWarehouseId()));
            batch.setWarehouse(warehouse);
        }

        batch.setBatchCode(request.getBatchCode());
        batch.setQuantity(request.getQuantity());
        batch.setManufacturedDate(request.getManufacturedDate());
        batch.setExpirationDate(request.getExpirationDate());
        batch.setCostPrice(request.getCostPrice());
        batch.setSellingPrice(request.getSellingPrice());

        try {
            Batch updated = batchRepository.save(batch);
            log.info("Updated batch {} for tenant {}", id, tenantId);
            return mapToResponse(updated);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("Batch was modified by another transaction. Please retry.");
        }
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Batch batch = batchRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", id));

        batchRepository.delete(batch);
        log.info("Deleted batch {} for tenant {}", id, tenantId);
    }

    private BatchResponse mapToResponse(Batch batch) {
        return BatchResponse.builder()
                .id(batch.getId())
                .productId(batch.getProduct().getId())
                .productName(batch.getProduct().getName())
                .warehouseId(batch.getWarehouse().getId())
                .warehouseName(batch.getWarehouse().getName())
                .batchCode(batch.getBatchCode())
                .quantity(batch.getQuantity())
                .manufacturedDate(batch.getManufacturedDate())
                .expirationDate(batch.getExpirationDate())
                .costPrice(batch.getCostPrice())
                .sellingPrice(batch.getSellingPrice())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/service/WarehouseService.java src/main/java/br/com/stockshift/service/BatchService.java
git commit -m "feat: add Warehouse and Batch services with optimistic locking"
```

---

### Task 8.5: Create Warehouse and Batch Controllers

**Files:**
- Create: `src/main/java/br/com/stockshift/controller/WarehouseController.java`
- Create: `src/main/java/br/com/stockshift/controller/BatchController.java`

**Step 1: Create WarehouseController**

Create `src/main/java/br/com/stockshift/controller/WarehouseController.java`:

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.warehouse.WarehouseRequest;
import br.com.stockshift.dto.warehouse.WarehouseResponse;
import br.com.stockshift.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouses", description = "Warehouse management endpoints")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_CREATE', 'ADMIN')")
    @Operation(summary = "Create a new warehouse")
    public ResponseEntity<ApiResponse<WarehouseResponse>> create(@Valid @RequestBody WarehouseRequest request) {
        WarehouseResponse response = warehouseService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Warehouse created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_READ', 'ADMIN')")
    @Operation(summary = "Get all warehouses")
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> findAll() {
        List<WarehouseResponse> warehouses = warehouseService.findAll();
        return ResponseEntity.ok(ApiResponse.success(warehouses));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_READ', 'ADMIN')")
    @Operation(summary = "Get warehouse by ID")
    public ResponseEntity<ApiResponse<WarehouseResponse>> findById(@PathVariable UUID id) {
        WarehouseResponse response = warehouseService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/active/{isActive}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_READ', 'ADMIN')")
    @Operation(summary = "Get warehouses by active status")
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> findActive(@PathVariable Boolean isActive) {
        List<WarehouseResponse> warehouses = warehouseService.findActive(isActive);
        return ResponseEntity.ok(ApiResponse.success(warehouses));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_UPDATE', 'ADMIN')")
    @Operation(summary = "Update warehouse")
    public ResponseEntity<ApiResponse<WarehouseResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody WarehouseRequest request) {
        WarehouseResponse response = warehouseService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Warehouse updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_DELETE', 'ADMIN')")
    @Operation(summary = "Delete warehouse")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        warehouseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Warehouse deleted successfully", null));
    }
}
```

**Step 2: Create BatchController**

Create `src/main/java/br/com/stockshift/controller/BatchController.java`:

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.warehouse.BatchRequest;
import br.com.stockshift.dto.warehouse.BatchResponse;
import br.com.stockshift.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
@Tag(name = "Batches", description = "Batch and stock management endpoints")
public class BatchController {

    private final BatchService batchService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('BATCH_CREATE', 'ADMIN')")
    @Operation(summary = "Create a new batch")
    public ResponseEntity<ApiResponse<BatchResponse>> create(@Valid @RequestBody BatchRequest request) {
        BatchResponse response = batchService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Batch created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ADMIN')")
    @Operation(summary = "Get all batches")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findAll() {
        List<BatchResponse> batches = batchService.findAll();
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ADMIN')")
    @Operation(summary = "Get batch by ID")
    public ResponseEntity<ApiResponse<BatchResponse>> findById(@PathVariable UUID id) {
        BatchResponse response = batchService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ADMIN')")
    @Operation(summary = "Get batches by warehouse")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findByWarehouse(@PathVariable UUID warehouseId) {
        List<BatchResponse> batches = batchService.findByWarehouse(warehouseId);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ADMIN')")
    @Operation(summary = "Get batches by product")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findByProduct(@PathVariable UUID productId) {
        List<BatchResponse> batches = batchService.findByProduct(productId);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/expiring/{daysAhead}")
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ADMIN')")
    @Operation(summary = "Get batches expiring in next N days")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findExpiringBatches(@PathVariable Integer daysAhead) {
        List<BatchResponse> batches = batchService.findExpiringBatches(daysAhead);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/low-stock/{threshold}")
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ADMIN')")
    @Operation(summary = "Get batches with quantity below threshold")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findLowStock(@PathVariable Integer threshold) {
        List<BatchResponse> batches = batchService.findLowStock(threshold);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BATCH_UPDATE', 'ADMIN')")
    @Operation(summary = "Update batch")
    public ResponseEntity<ApiResponse<BatchResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody BatchRequest request) {
        BatchResponse response = batchService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Batch updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BATCH_DELETE', 'ADMIN')")
    @Operation(summary = "Delete batch")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        batchService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Batch deleted successfully", null));
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/WarehouseController.java src/main/java/br/com/stockshift/controller/BatchController.java
git commit -m "feat: add Warehouse and Batch REST controllers"
```

---

## Phase 9: Stock Movements

### Task 9.1: Create Stock Movement Entities

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/StockMovement.java`
- Create: `src/main/java/br/com/stockshift/model/entity/StockMovementItem.java`

**Step 1: Create StockMovement Entity**

Create `src/main/java/br/com/stockshift/model/entity/StockMovement.java`:

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_movements")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement extends TenantAwareEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MovementStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_warehouse_id")
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_warehouse_id")
    private Warehouse destinationWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "movement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockMovementItem> items = new ArrayList<>();
}
```

**Step 2: Create StockMovementItem Entity**

Create `src/main/java/br/com/stockshift/model/entity/StockMovementItem.java`:

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "stock_movement_items")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movement_id", nullable = false)
    private StockMovement movement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", precision = 15, scale = 2)
    private BigDecimal totalPrice;
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/StockMovement.java src/main/java/br/com/stockshift/model/entity/StockMovementItem.java
git commit -m "feat: add StockMovement and StockMovementItem entities"
```

---

### Task 9.2: Create Stock Movement Repositories

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/StockMovementRepository.java`
- Create: `src/main/java/br/com/stockshift/repository/StockMovementItemRepository.java`

**Step 1: Create StockMovementRepository**

Create `src/main/java/br/com/stockshift/repository/StockMovementRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId ORDER BY m.createdAt DESC")
    List<StockMovement> findAllByTenantId(UUID tenantId);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND m.id = :id")
    Optional<StockMovement> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND m.movementType = :movementType ORDER BY m.createdAt DESC")
    List<StockMovement> findByTenantIdAndMovementType(UUID tenantId, MovementType movementType);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND m.status = :status ORDER BY m.createdAt DESC")
    List<StockMovement> findByTenantIdAndStatus(UUID tenantId, MovementStatus status);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND (m.sourceWarehouse.id = :warehouseId OR m.destinationWarehouse.id = :warehouseId) ORDER BY m.createdAt DESC")
    List<StockMovement> findByTenantIdAndWarehouse(UUID tenantId, UUID warehouseId);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND m.user.id = :userId ORDER BY m.createdAt DESC")
    List<StockMovement> findByTenantIdAndUser(UUID tenantId, UUID userId);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND m.createdAt BETWEEN :startDate AND :endDate ORDER BY m.createdAt DESC")
    List<StockMovement> findByTenantIdAndDateRange(UUID tenantId, LocalDateTime startDate, LocalDateTime endDate);
}
```

**Step 2: Create StockMovementItemRepository**

Create `src/main/java/br/com/stockshift/repository/StockMovementItemRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.StockMovementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementItemRepository extends JpaRepository<StockMovementItem, UUID> {

    @Query("SELECT i FROM StockMovementItem i WHERE i.movement.id = :movementId")
    List<StockMovementItem> findByMovementId(UUID movementId);

    @Query("SELECT i FROM StockMovementItem i WHERE i.product.id = :productId")
    List<StockMovementItem> findByProductId(UUID productId);

    @Query("SELECT i FROM StockMovementItem i WHERE i.batch.id = :batchId")
    List<StockMovementItem> findByBatchId(UUID batchId);
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/
git commit -m "feat: add StockMovement and StockMovementItem repositories"
```

---

### Task 9.3: Create Stock Movement DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/movement/StockMovementItemRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/movement/StockMovementRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/movement/StockMovementItemResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/movement/StockMovementResponse.java`

**Step 1: Create movement DTO directory**

```bash
mkdir -p src/main/java/br/com/stockshift/dto/movement
```

**Step 2: Create StockMovementItemRequest**

Create `src/main/java/br/com/stockshift/dto/movement/StockMovementItemRequest.java`:

```java
package br.com.stockshift.dto.movement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementItemRequest {
    @NotNull(message = "Product ID is required")
    private UUID productId;

    private UUID batchId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    private BigDecimal unitPrice;
}
```

**Step 3: Create StockMovementRequest**

Create `src/main/java/br/com/stockshift/dto/movement/StockMovementRequest.java`:

```java
package br.com.stockshift.dto.movement;

import br.com.stockshift.model.enums.MovementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementRequest {
    @NotNull(message = "Movement type is required")
    private MovementType movementType;

    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;

    private String notes;

    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<StockMovementItemRequest> items;
}
```

**Step 4: Create StockMovementItemResponse**

Create `src/main/java/br/com/stockshift/dto/movement/StockMovementItemResponse.java`:

```java
package br.com.stockshift.dto.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID batchId;
    private String batchCode;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
```

**Step 5: Create StockMovementResponse**

Create `src/main/java/br/com/stockshift/dto/movement/StockMovementResponse.java`:

```java
package br.com.stockshift.dto.movement;

import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {
    private UUID id;
    private MovementType movementType;
    private MovementStatus status;
    private UUID sourceWarehouseId;
    private String sourceWarehouseName;
    private UUID destinationWarehouseId;
    private String destinationWarehouseName;
    private UUID userId;
    private String userName;
    private String notes;
    private List<StockMovementItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
```

**Step 6: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/movement/
git commit -m "feat: add StockMovement DTOs with validation"
```

---

### Task 9.4: Create Stock Movement Service

**Files:**
- Create: `src/main/java/br/com/stockshift/service/StockMovementService.java`

**Step 1: Create StockMovementService**

Create `src/main/java/br/com/stockshift/service/StockMovementService.java`:

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.movement.StockMovementItemRequest;
import br.com.stockshift.dto.movement.StockMovementItemResponse;
import br.com.stockshift.dto.movement.StockMovementRequest;
import br.com.stockshift.dto.movement.StockMovementResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final StockMovementItemRepository stockMovementItemRepository;
    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    @Transactional
    public StockMovementResponse create(StockMovementRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Get current user
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        // Validate warehouses based on movement type
        validateWarehouses(request, tenantId);

        StockMovement movement = new StockMovement();
        movement.setTenantId(tenantId);
        movement.setMovementType(request.getMovementType());
        movement.setStatus(MovementStatus.PENDING);
        movement.setUser(user);
        movement.setNotes(request.getNotes());

        if (request.getSourceWarehouseId() != null) {
            Warehouse source = warehouseRepository.findByTenantIdAndId(tenantId, request.getSourceWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source warehouse", "id", request.getSourceWarehouseId()));
            movement.setSourceWarehouse(source);
        }

        if (request.getDestinationWarehouseId() != null) {
            Warehouse destination = warehouseRepository.findByTenantIdAndId(tenantId, request.getDestinationWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination warehouse", "id", request.getDestinationWarehouseId()));
            movement.setDestinationWarehouse(destination);
        }

        // Create movement items
        List<StockMovementItem> items = new ArrayList<>();
        for (StockMovementItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findByTenantIdAndId(tenantId, itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemRequest.getProductId()));

            Batch batch = null;
            if (itemRequest.getBatchId() != null) {
                batch = batchRepository.findByTenantIdAndId(tenantId, itemRequest.getBatchId())
                        .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", itemRequest.getBatchId()));
            }

            StockMovementItem item = new StockMovementItem();
            item.setMovement(movement);
            item.setProduct(product);
            item.setBatch(batch);
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(itemRequest.getUnitPrice());

            if (itemRequest.getUnitPrice() != null) {
                item.setTotalPrice(itemRequest.getUnitPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
            }

            items.add(item);
        }

        movement.setItems(items);
        StockMovement saved = stockMovementRepository.save(movement);

        log.info("Created stock movement {} of type {} for tenant {}", saved.getId(), saved.getMovementType(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional
    public StockMovementResponse executeMovement(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        StockMovement movement = stockMovementRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", id));

        if (movement.getStatus() != MovementStatus.PENDING) {
            throw new BusinessException("Only pending movements can be executed");
        }

        try {
            // Apply stock changes based on movement type
            for (StockMovementItem item : movement.getItems()) {
                applyStockChanges(movement, item);
            }

            movement.setStatus(MovementStatus.COMPLETED);
            movement.setCompletedAt(LocalDateTime.now());

            StockMovement updated = stockMovementRepository.save(movement);
            log.info("Executed stock movement {} for tenant {}", id, tenantId);

            return mapToResponse(updated);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("Batch was modified by another transaction. Please retry.");
        }
    }

    @Transactional
    public StockMovementResponse cancelMovement(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        StockMovement movement = stockMovementRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", id));

        if (movement.getStatus() == MovementStatus.COMPLETED) {
            throw new BusinessException("Cannot cancel completed movements");
        }

        movement.setStatus(MovementStatus.CANCELLED);
        StockMovement updated = stockMovementRepository.save(movement);

        log.info("Cancelled stock movement {} for tenant {}", id, tenantId);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return stockMovementRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StockMovementResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        StockMovement movement = stockMovementRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", id));
        return mapToResponse(movement);
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> findByType(MovementType movementType) {
        UUID tenantId = TenantContext.getTenantId();
        return stockMovementRepository.findByTenantIdAndMovementType(tenantId, movementType).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> findByStatus(MovementStatus status) {
        UUID tenantId = TenantContext.getTenantId();
        return stockMovementRepository.findByTenantIdAndStatus(tenantId, status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void validateWarehouses(StockMovementRequest request, UUID tenantId) {
        MovementType type = request.getMovementType();

        if (type == MovementType.TRANSFER) {
            if (request.getSourceWarehouseId() == null || request.getDestinationWarehouseId() == null) {
                throw new BusinessException("TRANSFER movements require both source and destination warehouses");
            }
            if (request.getSourceWarehouseId().equals(request.getDestinationWarehouseId())) {
                throw new BusinessException("Source and destination warehouses must be different");
            }
        } else if (type == MovementType.PURCHASE) {
            if (request.getDestinationWarehouseId() == null) {
                throw new BusinessException("PURCHASE movements require a destination warehouse");
            }
        } else if (type == MovementType.SALE) {
            if (request.getSourceWarehouseId() == null) {
                throw new BusinessException("SALE movements require a source warehouse");
            }
        }
    }

    private void applyStockChanges(StockMovement movement, StockMovementItem item) {
        MovementType type = movement.getMovementType();

        if (item.getBatch() == null) {
            throw new BusinessException("Batch is required to execute stock movements");
        }

        Batch batch = item.getBatch();

        switch (type) {
            case PURCHASE:
                // Increase stock in destination warehouse
                batch.setQuantity(batch.getQuantity() + item.getQuantity());
                batchRepository.save(batch);
                break;

            case SALE:
            case ADJUSTMENT:
                // Decrease stock from source warehouse
                if (batch.getQuantity() < item.getQuantity()) {
                    throw new BusinessException("Insufficient stock for product " + item.getProduct().getName());
                }
                batch.setQuantity(batch.getQuantity() - item.getQuantity());
                batchRepository.save(batch);
                break;

            case TRANSFER:
                // Decrease from source, create/increase in destination
                if (batch.getQuantity() < item.getQuantity()) {
                    throw new BusinessException("Insufficient stock for product " + item.getProduct().getName());
                }
                batch.setQuantity(batch.getQuantity() - item.getQuantity());
                batchRepository.save(batch);

                // Find or create batch in destination warehouse
                List<Batch> destBatches = batchRepository.findByProductIdAndWarehouseIdAndTenantId(
                        item.getProduct().getId(),
                        movement.getDestinationWarehouse().getId(),
                        movement.getTenantId()
                );

                Batch destBatch;
                if (!destBatches.isEmpty()) {
                    destBatch = destBatches.get(0);
                    destBatch.setQuantity(destBatch.getQuantity() + item.getQuantity());
                } else {
                    destBatch = new Batch();
                    destBatch.setTenantId(movement.getTenantId());
                    destBatch.setProduct(item.getProduct());
                    destBatch.setWarehouse(movement.getDestinationWarehouse());
                    destBatch.setBatchCode(batch.getBatchCode() + "-TRANSFER");
                    destBatch.setQuantity(item.getQuantity());
                    destBatch.setManufacturedDate(batch.getManufacturedDate());
                    destBatch.setExpirationDate(batch.getExpirationDate());
                    destBatch.setCostPrice(batch.getCostPrice());
                    destBatch.setSellingPrice(batch.getSellingPrice());
                }
                batchRepository.save(destBatch);
                break;

            case RETURN:
                // Increase stock (return to warehouse)
                batch.setQuantity(batch.getQuantity() + item.getQuantity());
                batchRepository.save(batch);
                break;
        }
    }

    private StockMovementResponse mapToResponse(StockMovement movement) {
        List<StockMovementItemResponse> itemResponses = movement.getItems().stream()
                .map(this::mapItemToResponse)
                .collect(Collectors.toList());

        return StockMovementResponse.builder()
                .id(movement.getId())
                .movementType(movement.getMovementType())
                .status(movement.getStatus())
                .sourceWarehouseId(movement.getSourceWarehouse() != null ? movement.getSourceWarehouse().getId() : null)
                .sourceWarehouseName(movement.getSourceWarehouse() != null ? movement.getSourceWarehouse().getName() : null)
                .destinationWarehouseId(movement.getDestinationWarehouse() != null ? movement.getDestinationWarehouse().getId() : null)
                .destinationWarehouseName(movement.getDestinationWarehouse() != null ? movement.getDestinationWarehouse().getName() : null)
                .userId(movement.getUser().getId())
                .userName(movement.getUser().getFullName())
                .notes(movement.getNotes())
                .items(itemResponses)
                .createdAt(movement.getCreatedAt())
                .updatedAt(movement.getUpdatedAt())
                .completedAt(movement.getCompletedAt())
                .build();
    }

    private StockMovementItemResponse mapItemToResponse(StockMovementItem item) {
        return StockMovementItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .batchId(item.getBatch() != null ? item.getBatch().getId() : null)
                .batchCode(item.getBatch() != null ? item.getBatch().getBatchCode() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .build();
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/service/StockMovementService.java
git commit -m "feat: add StockMovement service with stock update logic"
```

---

### Task 9.5: Create Stock Movement Controller

**Files:**
- Create: `src/main/java/br/com/stockshift/controller/StockMovementController.java`

**Step 1: Create StockMovementController**

Create `src/main/java/br/com/stockshift/controller/StockMovementController.java`:

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.movement.StockMovementRequest;
import br.com.stockshift.dto.movement.StockMovementResponse;
import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import br.com.stockshift.service.StockMovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movements")
@RequiredArgsConstructor
@Tag(name = "Stock Movements", description = "Stock movement management endpoints")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_CREATE', 'ADMIN')")
    @Operation(summary = "Create a new stock movement")
    public ResponseEntity<ApiResponse<StockMovementResponse>> create(@Valid @RequestBody StockMovementRequest request) {
        StockMovementResponse response = stockMovementService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock movement created successfully", response));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_EXECUTE', 'ADMIN')")
    @Operation(summary = "Execute a pending stock movement")
    public ResponseEntity<ApiResponse<StockMovementResponse>> execute(@PathVariable UUID id) {
        StockMovementResponse response = stockMovementService.executeMovement(id);
        return ResponseEntity.ok(ApiResponse.success("Stock movement executed successfully", response));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_UPDATE', 'ADMIN')")
    @Operation(summary = "Cancel a stock movement")
    public ResponseEntity<ApiResponse<StockMovementResponse>> cancel(@PathVariable UUID id) {
        StockMovementResponse response = stockMovementService.cancelMovement(id);
        return ResponseEntity.ok(ApiResponse.success("Stock movement cancelled successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ', 'ADMIN')")
    @Operation(summary = "Get all stock movements")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> findAll() {
        List<StockMovementResponse> movements = stockMovementService.findAll();
        return ResponseEntity.ok(ApiResponse.success(movements));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ', 'ADMIN')")
    @Operation(summary = "Get stock movement by ID")
    public ResponseEntity<ApiResponse<StockMovementResponse>> findById(@PathVariable UUID id) {
        StockMovementResponse response = stockMovementService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/type/{movementType}")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ', 'ADMIN')")
    @Operation(summary = "Get stock movements by type")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> findByType(@PathVariable MovementType movementType) {
        List<StockMovementResponse> movements = stockMovementService.findByType(movementType);
        return ResponseEntity.ok(ApiResponse.success(movements));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ', 'ADMIN')")
    @Operation(summary = "Get stock movements by status")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> findByStatus(@PathVariable MovementStatus status) {
        List<StockMovementResponse> movements = stockMovementService.findByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(movements));
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/StockMovementController.java
git commit -m "feat: add StockMovement REST controller"
```

---

## Phase 10: Basic Reports and Dashboard

### Task 10.1: Create Report DTOs and Service

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/report/StockReportResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/report/DashboardResponse.java`
- Create: `src/main/java/br/com/stockshift/service/ReportService.java`

**Step 1: Create report DTO directory**

```bash
mkdir -p src/main/java/br/com/stockshift/dto/report
```

**Step 2: Create StockReportResponse**

Create `src/main/java/br/com/stockshift/dto/report/StockReportResponse.java`:

```java
package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReportResponse {
    private UUID productId;
    private String productName;
    private UUID warehouseId;
    private String warehouseName;
    private Integer totalQuantity;
    private BigDecimal totalValue;
    private LocalDate nearestExpiration;
    private Integer batchCount;
}
```

**Step 3: Create DashboardResponse**

Create `src/main/java/br/com/stockshift/dto/report/DashboardResponse.java`:

```java
package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private Long totalProducts;
    private Long totalWarehouses;
    private Integer totalStockQuantity;
    private BigDecimal totalStockValue;
    private Long pendingMovements;
    private Long completedMovementsToday;
    private List<StockReportResponse> lowStockProducts;
    private List<StockReportResponse> expiringProducts;
}
```

**Step 4: Create ReportService**

Create `src/main/java/br/com/stockshift/service/ReportService.java`:

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.report.DashboardResponse;
import br.com.stockshift.dto.report.StockReportResponse;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        UUID tenantId = TenantContext.getTenantId();

        long totalProducts = productRepository.findAllByTenantId(tenantId).size();
        long totalWarehouses = warehouseRepository.findAllByTenantId(tenantId).size();

        List<Batch> allBatches = batchRepository.findAllByTenantId(tenantId);

        int totalStockQuantity = allBatches.stream()
                .mapToInt(Batch::getQuantity)
                .sum();

        BigDecimal totalStockValue = allBatches.stream()
                .filter(b -> b.getCostPrice() != null)
                .map(b -> b.getCostPrice().multiply(BigDecimal.valueOf(b.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingMovements = stockMovementRepository.findByTenantIdAndStatus(tenantId, MovementStatus.PENDING).size();

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        long completedMovementsToday = stockMovementRepository.findByTenantIdAndDateRange(tenantId, todayStart, todayEnd)
                .stream()
                .filter(m -> m.getStatus() == MovementStatus.COMPLETED)
                .count();

        List<StockReportResponse> lowStockProducts = getLowStockReport(10, 10);
        List<StockReportResponse> expiringProducts = getExpiringProductsReport(30, 10);

        return DashboardResponse.builder()
                .totalProducts(totalProducts)
                .totalWarehouses(totalWarehouses)
                .totalStockQuantity(totalStockQuantity)
                .totalStockValue(totalStockValue)
                .pendingMovements(pendingMovements)
                .completedMovementsToday(completedMovementsToday)
                .lowStockProducts(lowStockProducts)
                .expiringProducts(expiringProducts)
                .build();
    }

    @Transactional(readOnly = true)
    public List<StockReportResponse> getStockReport() {
        UUID tenantId = TenantContext.getTenantId();
        List<Batch> batches = batchRepository.findAllByTenantId(tenantId);

        Map<String, List<Batch>> groupedBatches = batches.stream()
                .collect(Collectors.groupingBy(b ->
                    b.getProduct().getId().toString() + "_" + b.getWarehouse().getId().toString()
                ));

        return groupedBatches.values().stream()
                .map(this::aggregateBatches)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockReportResponse> getLowStockReport(Integer threshold, Integer limit) {
        UUID tenantId = TenantContext.getTenantId();
        List<Batch> lowStockBatches = batchRepository.findLowStock(threshold, tenantId);

        return lowStockBatches.stream()
                .limit(limit != null ? limit : Long.MAX_VALUE)
                .map(this::batchToReport)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockReportResponse> getExpiringProductsReport(Integer daysAhead, Integer limit) {
        UUID tenantId = TenantContext.getTenantId();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);

        List<Batch> expiringBatches = batchRepository.findExpiringBatches(startDate, endDate, tenantId);

        return expiringBatches.stream()
                .limit(limit != null ? limit : Long.MAX_VALUE)
                .map(this::batchToReport)
                .collect(Collectors.toList());
    }

    private StockReportResponse aggregateBatches(List<Batch> batches) {
        Batch first = batches.get(0);

        int totalQuantity = batches.stream()
                .mapToInt(Batch::getQuantity)
                .sum();

        BigDecimal totalValue = batches.stream()
                .filter(b -> b.getCostPrice() != null)
                .map(b -> b.getCostPrice().multiply(BigDecimal.valueOf(b.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate nearestExpiration = batches.stream()
                .map(Batch::getExpirationDate)
                .filter(date -> date != null)
                .min(LocalDate::compareTo)
                .orElse(null);

        return StockReportResponse.builder()
                .productId(first.getProduct().getId())
                .productName(first.getProduct().getName())
                .warehouseId(first.getWarehouse().getId())
                .warehouseName(first.getWarehouse().getName())
                .totalQuantity(totalQuantity)
                .totalValue(totalValue)
                .nearestExpiration(nearestExpiration)
                .batchCount(batches.size())
                .build();
    }

    private StockReportResponse batchToReport(Batch batch) {
        BigDecimal totalValue = batch.getCostPrice() != null ?
                batch.getCostPrice().multiply(BigDecimal.valueOf(batch.getQuantity())) :
                BigDecimal.ZERO;

        return StockReportResponse.builder()
                .productId(batch.getProduct().getId())
                .productName(batch.getProduct().getName())
                .warehouseId(batch.getWarehouse().getId())
                .warehouseName(batch.getWarehouse().getName())
                .totalQuantity(batch.getQuantity())
                .totalValue(totalValue)
                .nearestExpiration(batch.getExpirationDate())
                .batchCount(1)
                .build();
    }
}
```

**Step 5: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/report/ src/main/java/br/com/stockshift/service/ReportService.java
git commit -m "feat: add Report service with dashboard and stock reports"
```

---

### Task 10.2: Create Report Controller

**Files:**
- Create: `src/main/java/br/com/stockshift/controller/ReportController.java`

**Step 1: Create ReportController**

Create `src/main/java/br/com/stockshift/controller/ReportController.java`:

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.report.DashboardResponse;
import br.com.stockshift.dto.report.StockReportResponse;
import br.com.stockshift.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Reporting and dashboard endpoints")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('REPORT_READ', 'ADMIN')")
    @Operation(summary = "Get dashboard summary")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        DashboardResponse response = reportService.getDashboard();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAnyAuthority('REPORT_READ', 'ADMIN')")
    @Operation(summary = "Get complete stock report")
    public ResponseEntity<ApiResponse<List<StockReportResponse>>> getStockReport() {
        List<StockReportResponse> report = reportService.getStockReport();
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/stock/low-stock")
    @PreAuthorize("hasAnyAuthority('REPORT_READ', 'ADMIN')")
    @Operation(summary = "Get low stock report")
    public ResponseEntity<ApiResponse<List<StockReportResponse>>> getLowStockReport(
            @RequestParam(defaultValue = "10") Integer threshold,
            @RequestParam(required = false) Integer limit) {
        List<StockReportResponse> report = reportService.getLowStockReport(threshold, limit);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/stock/expiring")
    @PreAuthorize("hasAnyAuthority('REPORT_READ', 'ADMIN')")
    @Operation(summary = "Get expiring products report")
    public ResponseEntity<ApiResponse<List<StockReportResponse>>> getExpiringProductsReport(
            @RequestParam(defaultValue = "30") Integer daysAhead,
            @RequestParam(required = false) Integer limit) {
        List<StockReportResponse> report = reportService.getExpiringProductsReport(daysAhead, limit);
        return ResponseEntity.ok(ApiResponse.success(report));
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/ReportController.java
git commit -m "feat: add Report REST controller with dashboard endpoints"
```

---

## Phase 11: Integration Tests

### Task 11.1: Setup Test Infrastructure

**Files:**
- Create: `src/test/java/br/com/stockshift/BaseIntegrationTest.java`
- Create: `src/test/resources/application-test.yml`

**Step 1: Create BaseIntegrationTest**

Create `src/test/java/br/com/stockshift/BaseIntegrationTest.java`:

```java
package br.com.stockshift;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("stockshift_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @BeforeEach
    void setUp() {
        // Common setup for all integration tests
    }
}
```

**Step 2: Create test configuration**

Create `src/test/resources/application-test.yml`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    clean-disabled: false

jwt:
  secret: test-secret-key-for-integration-tests-minimum-256-bits
  access-expiration: 900000
  refresh-expiration: 604800000

logging:
  level:
    br.com.stockshift: DEBUG
    org.springframework.security: DEBUG
```

**Step 3: Verify tests run**

Run: `./gradlew test`
Expected: Tests run successfully with Testcontainers

**Step 4: Commit**

```bash
git add src/test/java/br/com/stockshift/BaseIntegrationTest.java src/test/resources/application-test.yml
git commit -m "test: add integration test infrastructure with Testcontainers"
```

---

### Task 11.2: Create Product Integration Tests

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java`

**Step 1: Create ProductControllerIntegrationTest**

Create `src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java`:

```java
package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.product.CategoryRequest;
import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.enums.BarcodeType;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

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
    private Category testCategory;

    @BeforeEach
    void setUpTestData() {
        // Create test tenant
        testTenant = new Tenant();
        testTenant.setName("Test Tenant");
        testTenant.setActive(true);
        testTenant = tenantRepository.save(testTenant);

        // Create test user
        testUser = new User();
        testUser.setTenantId(testTenant.getId());
        testUser.setEmail("test@example.com");
        testUser.setPassword(passwordEncoder.encode("password"));
        testUser.setFullName("Test User");
        testUser.setIsActive(true);
        testUser = userRepository.save(testUser);

        // Set security context
        TenantContext.setTenantId(testTenant.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser.getEmail(), null)
        );

        // Create test category
        testCategory = new Category();
        testCategory.setTenantId(testTenant.getId());
        testCategory.setName("Test Category");
        testCategory = categoryRepository.save(testCategory);
    }

    @Test
    void shouldCreateProduct() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Test Product")
                .description("Test Description")
                .categoryId(testCategory.getId())
                .barcode("1234567890")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("TEST-SKU-001")
                .isKit(false)
                .hasExpiration(false)
                .active(true)
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Test Product"))
                .andExpect(jsonPath("$.data.barcode").value("1234567890"))
                .andExpect(jsonPath("$.data.sku").value("TEST-SKU-001"));
    }

    @Test
    void shouldGetProductById() throws Exception {
        Product product = createTestProduct("Get Test Product", "BARCODE-001", "SKU-001");

        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Get Test Product"));
    }

    @Test
    void shouldFindProductByBarcode() throws Exception {
        Product product = createTestProduct("Barcode Test", "BARCODE-FIND", "SKU-002");

        mockMvc.perform(get("/api/products/barcode/{barcode}", "BARCODE-FIND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.barcode").value("BARCODE-FIND"));
    }

    @Test
    void shouldUpdateProduct() throws Exception {
        Product product = createTestProduct("Update Test", "BARCODE-UPDATE", "SKU-UPDATE");

        ProductRequest updateRequest = ProductRequest.builder()
                .name("Updated Product Name")
                .description("Updated Description")
                .categoryId(testCategory.getId())
                .barcode("BARCODE-UPDATE")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("SKU-UPDATE")
                .isKit(false)
                .hasExpiration(false)
                .active(false)
                .build();

        mockMvc.perform(put("/api/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Product Name"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        Product product = createTestProduct("Delete Test", "BARCODE-DELETE", "SKU-DELETE");

        mockMvc.perform(delete("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify soft delete
        Product deletedProduct = productRepository.findById(product.getId()).orElseThrow();
        assert deletedProduct.getDeletedAt() != null;
    }

    @Test
    void shouldSearchProducts() throws Exception {
        createTestProduct("Searchable Product 1", "SEARCH-001", "SEARCH-SKU-001");
        createTestProduct("Searchable Product 2", "SEARCH-002", "SEARCH-SKU-002");

        mockMvc.perform(get("/api/products/search")
                        .param("q", "Searchable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    private Product createTestProduct(String name, String barcode, String sku) {
        Product product = new Product();
        product.setTenantId(testTenant.getId());
        product.setName(name);
        product.setCategory(testCategory);
        product.setBarcode(barcode);
        product.setBarcodeType(BarcodeType.EXTERNAL);
        product.setSku(sku);
        product.setIsKit(false);
        product.setHasExpiration(false);
        product.setActive(true);
        return productRepository.save(product);
    }
}
```

**Step 2: Run integration tests**

Run: `./gradlew test --tests ProductControllerIntegrationTest`
Expected: All tests pass

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java
git commit -m "test: add Product controller integration tests"
```

---

### Task 11.3: Create Stock Movement Integration Tests

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/StockMovementControllerIntegrationTest.java`

**Step 1: Create StockMovementControllerIntegrationTest**

Create `src/test/java/br/com/stockshift/controller/StockMovementControllerIntegrationTest.java`:

```java
package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.movement.StockMovementItemRequest;
import br.com.stockshift.dto.movement.StockMovementRequest;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StockMovementControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant testTenant;
    private User testUser;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private Product testProduct;
    private Batch testBatch;

    @BeforeEach
    void setUpTestData() {
        // Create test tenant
        testTenant = new Tenant();
        testTenant.setName("Test Tenant");
        testTenant.setActive(true);
        testTenant = tenantRepository.save(testTenant);

        // Create test user
        testUser = new User();
        testUser.setTenantId(testTenant.getId());
        testUser.setEmail("test@example.com");
        testUser.setPassword(passwordEncoder.encode("password"));
        testUser.setFullName("Test User");
        testUser.setIsActive(true);
        testUser = userRepository.save(testUser);

        // Set security context
        TenantContext.setTenantId(testTenant.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser.getEmail(), null)
        );

        // Create warehouses
        sourceWarehouse = new Warehouse();
        sourceWarehouse.setTenantId(testTenant.getId());
        sourceWarehouse.setName("Source Warehouse");
        sourceWarehouse.setCity("São Paulo");
        sourceWarehouse.setState("SP");
        sourceWarehouse.setIsActive(true);
        sourceWarehouse = warehouseRepository.save(sourceWarehouse);

        destinationWarehouse = new Warehouse();
        destinationWarehouse.setTenantId(testTenant.getId());
        destinationWarehouse.setName("Destination Warehouse");
        destinationWarehouse.setCity("Rio de Janeiro");
        destinationWarehouse.setState("RJ");
        destinationWarehouse.setIsActive(true);
        destinationWarehouse = warehouseRepository.save(destinationWarehouse);

        // Create test product
        testProduct = new Product();
        testProduct.setTenantId(testTenant.getId());
        testProduct.setName("Test Product");
        testProduct.setSku("TEST-SKU");
        testProduct.setIsKit(false);
        testProduct.setActive(true);
        testProduct = productRepository.save(testProduct);

        // Create test batch
        testBatch = new Batch();
        testBatch.setTenantId(testTenant.getId());
        testBatch.setProduct(testProduct);
        testBatch.setWarehouse(sourceWarehouse);
        testBatch.setBatchCode("BATCH-001");
        testBatch.setQuantity(100);
        testBatch.setCostPrice(BigDecimal.valueOf(10.00));
        testBatch = batchRepository.save(testBatch);
    }

    @Test
    void shouldCreatePurchaseMovement() throws Exception {
        StockMovementItemRequest item = StockMovementItemRequest.builder()
                .productId(testProduct.getId())
                .batchId(testBatch.getId())
                .quantity(50)
                .unitPrice(BigDecimal.valueOf(10.00))
                .build();

        StockMovementRequest request = StockMovementRequest.builder()
                .movementType(MovementType.PURCHASE)
                .destinationWarehouseId(destinationWarehouse.getId())
                .notes("Test purchase")
                .items(Arrays.asList(item))
                .build();

        mockMvc.perform(post("/api/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.movementType").value("PURCHASE"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void shouldExecuteTransferMovement() throws Exception {
        // Create transfer movement
        StockMovementItemRequest item = StockMovementItemRequest.builder()
                .productId(testProduct.getId())
                .batchId(testBatch.getId())
                .quantity(30)
                .build();

        StockMovementRequest request = StockMovementRequest.builder()
                .movementType(MovementType.TRANSFER)
                .sourceWarehouseId(sourceWarehouse.getId())
                .destinationWarehouseId(destinationWarehouse.getId())
                .notes("Test transfer")
                .items(Arrays.asList(item))
                .build();

        String createResponse = mockMvc.perform(post("/api/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String movementId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        // Execute the movement
        mockMvc.perform(post("/api/stock-movements/{id}/execute", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").exists());

        // Verify stock was updated
        Batch updatedBatch = batchRepository.findById(testBatch.getId()).orElseThrow();
        assert updatedBatch.getQuantity() == 70; // 100 - 30
    }

    @Test
    void shouldCancelMovement() throws Exception {
        StockMovementItemRequest item = StockMovementItemRequest.builder()
                .productId(testProduct.getId())
                .batchId(testBatch.getId())
                .quantity(20)
                .build();

        StockMovementRequest request = StockMovementRequest.builder()
                .movementType(MovementType.SALE)
                .sourceWarehouseId(sourceWarehouse.getId())
                .items(Arrays.asList(item))
                .build();

        String createResponse = mockMvc.perform(post("/api/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String movementId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/stock-movements/{id}/cancel", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void shouldGetMovementsByStatus() throws Exception {
        mockMvc.perform(get("/api/stock-movements/status/{status}", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
```

**Step 2: Run integration tests**

Run: `./gradlew test --tests StockMovementControllerIntegrationTest`
Expected: All tests pass

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/StockMovementControllerIntegrationTest.java
git commit -m "test: add StockMovement controller integration tests"
```

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
