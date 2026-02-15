-- Flyway Migration V1: Initial Schema
-- StockShift Database Schema
-- Generated from production database dump

-- =============================================
-- TABLES
-- =============================================

-- Tenants
CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    business_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    document VARCHAR(20) UNIQUE,
    phone VARCHAR(20),
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

-- Warehouses
CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(2) NOT NULL,
    address TEXT,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT warehouses_tenant_id_code_key UNIQUE (tenant_id, code),
    CONSTRAINT warehouses_tenant_id_name_key UNIQUE (tenant_id, name)
);

-- Brands
CREATE TABLE brands (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6),
    CONSTRAINT brands_tenant_id_name_key UNIQUE (tenant_id, name)
);

-- Categories
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    parent_category_id UUID,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    attributes_schema JSONB,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6),
    CONSTRAINT categories_tenant_id_name_key UNIQUE (tenant_id, name),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_category_id) REFERENCES categories(id)
);

-- Products
CREATE TABLE products (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    brand_id UUID,
    category_id UUID,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    sku VARCHAR(100),
    barcode VARCHAR(100),
    barcode_type VARCHAR(20),
    image_url VARCHAR(500),
    attributes JSONB,
    active BOOLEAN NOT NULL,
    has_expiration BOOLEAN NOT NULL,
    is_kit BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6),
    CONSTRAINT products_tenant_id_sku_key UNIQUE (tenant_id, sku),
    CONSTRAINT products_tenant_id_barcode_key UNIQUE (tenant_id, barcode),
    CONSTRAINT products_barcode_type_check CHECK (barcode_type IN ('EXTERNAL', 'GENERATED')),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands(id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- Product Kits
CREATE TABLE product_kits (
    id UUID PRIMARY KEY,
    kit_product_id UUID NOT NULL,
    component_product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT product_kits_kit_product_id_component_product_id_key UNIQUE (kit_product_id, component_product_id),
    CONSTRAINT fk_product_kits_kit FOREIGN KEY (kit_product_id) REFERENCES products(id),
    CONSTRAINT fk_product_kits_component FOREIGN KEY (component_product_id) REFERENCES products(id)
);

-- Permissions
CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    scope VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT permissions_resource_action_scope_key UNIQUE (resource, action, scope),
    CONSTRAINT permissions_action_check CHECK (action IN ('CREATE', 'READ', 'UPDATE', 'DELETE', 'APPROVE', 'EXECUTE', 'VALIDATE', 'RESOLVE', 'CANCEL')),
    CONSTRAINT permissions_resource_check CHECK (resource IN ('PRODUCT', 'STOCK', 'USER', 'REPORT', 'WAREHOUSE', 'TRANSFER')),
    CONSTRAINT permissions_scope_check CHECK (scope IN ('ALL', 'OWN_WAREHOUSE', 'OWN', 'TENANT', 'OWNED'))
);

-- Roles
CREATE TABLE roles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_system_role BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT roles_tenant_id_name_key UNIQUE (tenant_id, name)
);

-- Role Permissions (Join Table)
CREATE TABLE role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (permission_id, role_id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- Users
CREATE TABLE users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL,
    must_change_password BOOLEAN NOT NULL,
    last_login TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT users_tenant_id_email_key UNIQUE (tenant_id, email)
);

-- User Roles (Join Table)
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (role_id, user_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- User Warehouses (Join Table)
CREATE TABLE user_warehouses (
    user_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    PRIMARY KEY (user_id, warehouse_id),
    CONSTRAINT fk_user_warehouses_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_warehouses_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);

-- Refresh Tokens
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    warehouse_id UUID,
    token VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Batches
CREATE TABLE batches (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    origin_batch_id UUID,
    batch_code VARCHAR(100) NOT NULL,
    quantity NUMERIC(15, 3) NOT NULL,
    transit_quantity NUMERIC(19, 4) NOT NULL DEFAULT 0,
    cost_price BIGINT,
    selling_price BIGINT,
    manufactured_date DATE,
    expiration_date DATE,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6),
    CONSTRAINT batches_tenant_id_batch_code_key UNIQUE (tenant_id, batch_code),
    CONSTRAINT fk_batches_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_batches_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_batches_origin_batch FOREIGN KEY (origin_batch_id) REFERENCES batches(id)
);

-- Transfers
CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    source_warehouse_id UUID NOT NULL,
    destination_warehouse_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    notes TEXT,
    cancellation_reason TEXT,
    created_by_user_id UUID NOT NULL,
    executed_by_user_id UUID,
    executed_at TIMESTAMP(6) WITH TIME ZONE,
    validated_by_user_id UUID,
    validated_at TIMESTAMP(6) WITH TIME ZONE,
    cancelled_by_user_id UUID,
    cancelled_at TIMESTAMP(6) WITH TIME ZONE,
    version BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT transfers_tenant_id_code_key UNIQUE (tenant_id, code),
    CONSTRAINT transfers_source_destination_check CHECK (source_warehouse_id <> destination_warehouse_id),
    CONSTRAINT transfers_status_check CHECK (status IN ('DRAFT', 'IN_TRANSIT', 'PENDING_VALIDATION', 'COMPLETED', 'COMPLETED_WITH_DISCREPANCY', 'CANCELLED'))
);

CREATE INDEX idx_transfers_tenant ON transfers(tenant_id);
CREATE INDEX idx_transfers_source_warehouse ON transfers(source_warehouse_id);
CREATE INDEX idx_transfers_destination_warehouse ON transfers(destination_warehouse_id);
CREATE INDEX idx_transfers_status ON transfers(status);

-- Transfer Items
CREATE TABLE transfer_items (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    source_batch_id UUID NOT NULL,
    destination_batch_id UUID,
    product_id UUID NOT NULL,
    product_barcode VARCHAR(255),
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(255),
    quantity_sent NUMERIC(19, 4) NOT NULL,
    quantity_received NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_transfer_items_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id)
);

CREATE INDEX idx_transfer_items_transfer ON transfer_items(transfer_id);
CREATE INDEX idx_transfer_items_source_batch ON transfer_items(source_batch_id);
CREATE INDEX idx_transfer_items_product_barcode ON transfer_items(product_barcode);

-- Transfer Validation Logs
CREATE TABLE transfer_validation_logs (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    transfer_item_id UUID,
    barcode VARCHAR(255) NOT NULL,
    validated_by_user_id UUID NOT NULL,
    validated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    valid BOOLEAN NOT NULL,
    CONSTRAINT fk_validation_logs_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id)
);

CREATE INDEX idx_transfer_validation_logs_transfer ON transfer_validation_logs(transfer_id);
CREATE INDEX idx_transfer_validation_logs_item ON transfer_validation_logs(transfer_item_id);

-- Inventory Ledger
CREATE TABLE inventory_ledger (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    warehouse_id UUID,
    batch_id UUID,
    transfer_item_id UUID,
    reference_id UUID NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    quantity NUMERIC(15, 3) NOT NULL,
    balance_after NUMERIC(15, 3),
    notes TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT inventory_ledger_entry_type_check CHECK (entry_type IN ('PURCHASE_IN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER_OUT', 'TRANSFER_CANCELLED', 'TRANSFER_IN', 'TRANSFER_IN_DISCREPANCY'))
);

CREATE INDEX idx_inventory_ledger_tenant ON inventory_ledger(tenant_id);
CREATE INDEX idx_inventory_ledger_product ON inventory_ledger(product_id);
CREATE INDEX idx_inventory_ledger_reference ON inventory_ledger(reference_type, reference_id);
