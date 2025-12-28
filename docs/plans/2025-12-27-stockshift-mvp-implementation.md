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

## Note on Plan Execution

This implementation plan covers the foundational phases of the StockShift MVP:

**Completed Sections:**
- Phase 1: Project Setup and Configuration (Tasks 1.1-1.3) ✅
- Phase 2: Database Schema (Tasks 2.1-2.8) ✅
- Phase 3: Core Entities and Enums (Tasks 3.1-3.6) ✅
- Phase 4: Authentication and Security (Tasks 4.1-4.6) ✅ COMPLETE

**Remaining Work (to be added incrementally):**
- Phase 5: Exception Handling and DTOs
- Phase 6: Authentication Service and Controller
- Phase 7: Product Management
- Phase 8: Warehouse and Batch Management
- Phase 9: Stock Movements
- Phase 10: Basic Reports and Dashboard
- Phase 11: Integration Tests

**Execution Strategy:**
This plan should be executed incrementally. Start with the completed sections above, test each phase thoroughly, and then extend the plan with additional phases as needed. The bite-sized tasks ensure each step is testable and committable independently.

---
