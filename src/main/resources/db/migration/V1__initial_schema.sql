-- Flyway Migration V1: Initial Schema
-- StockShift Database Schema

-- =============================================
-- TABLES
-- =============================================

-- Tenants
CREATE TABLE tenants (
    id uuid NOT NULL PRIMARY KEY,
    business_name character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    document character varying(20) UNIQUE,
    phone character varying(20),
    is_active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL
);

-- Warehouses
CREATE TABLE warehouses (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(255) NOT NULL,
    city character varying(100) NOT NULL,
    state character varying(2) NOT NULL,
    address text,
    is_active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT warehouses_tenant_id_code_key UNIQUE (tenant_id, code),
    CONSTRAINT warehouses_tenant_id_name_key UNIQUE (tenant_id, name)
);

-- Brands
CREATE TABLE brands (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    logo_url character varying(500),
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    CONSTRAINT brands_tenant_id_name_key UNIQUE (tenant_id, name)
);

-- Categories
CREATE TABLE categories (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    parent_category_id uuid,
    name character varying(255) NOT NULL,
    description character varying(255),
    attributes_schema jsonb,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    CONSTRAINT categories_tenant_id_name_key UNIQUE (tenant_id, name)
);

-- Products
CREATE TABLE products (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    brand_id uuid,
    category_id uuid,
    name character varying(255) NOT NULL,
    description character varying(255),
    sku character varying(100),
    barcode character varying(100),
    barcode_type character varying(20),
    image_url character varying(500),
    attributes jsonb,
    active boolean NOT NULL,
    has_expiration boolean NOT NULL,
    is_kit boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    CONSTRAINT products_tenant_id_sku_key UNIQUE (tenant_id, sku),
    CONSTRAINT products_tenant_id_barcode_key UNIQUE (tenant_id, barcode),
    CONSTRAINT products_barcode_type_check CHECK (
        barcode_type IN ('EXTERNAL', 'GENERATED')
    )
);

-- Product Kits
CREATE TABLE product_kits (
    id uuid NOT NULL PRIMARY KEY,
    kit_product_id uuid NOT NULL,
    component_product_id uuid NOT NULL,
    quantity integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT product_kits_kit_product_id_component_product_id_key UNIQUE (
        kit_product_id,
        component_product_id
    )
);

-- Batches
CREATE TABLE batches (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    product_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    origin_batch_id uuid,
    origin_transfer_id uuid,
    batch_code character varying(100) NOT NULL,
    quantity numeric(15, 3) NOT NULL,
    cost_price bigint,
    selling_price bigint,
    manufactured_date date,
    expiration_date date,
    version bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    CONSTRAINT batches_tenant_id_batch_code_key UNIQUE (tenant_id, batch_code)
);

-- Permissions
CREATE TABLE permissions (
    id uuid NOT NULL PRIMARY KEY,
    resource character varying(50) NOT NULL,
    action character varying(50) NOT NULL,
    scope character varying(50) NOT NULL,
    description character varying(255),
    CONSTRAINT permissions_resource_action_scope_key UNIQUE (resource, action, scope),
    CONSTRAINT permissions_resource_check CHECK (
        resource IN (
            'PRODUCT',
            'STOCK',
            'USER',
            'REPORT',
            'WAREHOUSE',
            'TRANSFER'
        )
    ),
    CONSTRAINT permissions_action_check CHECK (
        action IN (
            'CREATE',
            'READ',
            'UPDATE',
            'DELETE',
            'APPROVE',
            'EXECUTE',
            'VALIDATE',
            'RESOLVE',
            'CANCEL'
        )
    ),
    CONSTRAINT permissions_scope_check CHECK (
        scope IN (
            'ALL',
            'OWN_WAREHOUSE',
            'OWN',
            'TENANT',
            'OWNED'
        )
    )
);

-- Roles
CREATE TABLE roles (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(255),
    is_system_role boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT roles_tenant_id_name_key UNIQUE (tenant_id, name)
);

-- Role Permissions (Join Table)
CREATE TABLE role_permissions (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

-- Users
CREATE TABLE users (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    full_name character varying(255) NOT NULL,
    is_active boolean NOT NULL,
    must_change_password boolean NOT NULL,
    last_login timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT users_tenant_id_email_key UNIQUE (tenant_id, email)
);

-- User Roles (Join Table)
CREATE TABLE user_roles (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- User Warehouses (Join Table)
CREATE TABLE user_warehouses (
    user_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    PRIMARY KEY (user_id, warehouse_id)
);

-- Refresh Tokens
CREATE TABLE refresh_tokens (
    id uuid NOT NULL PRIMARY KEY,
    user_id uuid NOT NULL,
    token character varying(255) NOT NULL UNIQUE,
    created_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL
);

-- Transfers
CREATE TABLE transfers (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    source_warehouse_id uuid NOT NULL,
    destination_warehouse_id uuid NOT NULL,
    transfer_code character varying(50) NOT NULL,
    status character varying(50) NOT NULL,
    notes text,
    cancellation_reason text,
    created_by uuid NOT NULL,
    dispatched_by uuid,
    validation_started_by uuid,
    completed_by uuid,
    cancelled_by uuid,
    dispatched_at timestamp(6) without time zone,
    validation_started_at timestamp(6) without time zone,
    completed_at timestamp(6) without time zone,
    cancelled_at timestamp(6) without time zone,
    version bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT transfers_tenant_id_transfer_code_key UNIQUE (tenant_id, transfer_code),
    CONSTRAINT transfers_status_check CHECK (
        status IN (
            'DRAFT',
            'IN_TRANSIT',
            'VALIDATION_IN_PROGRESS',
            'COMPLETED',
            'COMPLETED_WITH_DISCREPANCY',
            'CANCELLED'
        )
    )
);

-- Transfer Items
CREATE TABLE transfer_items (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    transfer_id uuid NOT NULL,
    product_id uuid NOT NULL,
    source_batch_id uuid NOT NULL,
    destination_batch_id uuid,
    expected_quantity numeric(15, 3) NOT NULL,
    received_quantity numeric(15, 3),
    item_status character varying(30) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT transfer_items_item_status_check CHECK (
        item_status IN (
            'PENDING',
            'RECEIVED',
            'PARTIAL',
            'EXCESS',
            'MISSING'
        )
    )
);

-- Transfer In Transit
CREATE TABLE transfer_in_transit (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    transfer_id uuid NOT NULL,
    transfer_item_id uuid NOT NULL,
    product_id uuid NOT NULL,
    source_batch_id uuid NOT NULL,
    quantity numeric(15, 3) NOT NULL,
    consumed_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL
);

-- Transfer Events
CREATE TABLE transfer_events (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    transfer_id uuid NOT NULL,
    performed_by uuid NOT NULL,
    event_type character varying(255) NOT NULL,
    from_status character varying(255),
    to_status character varying(255) NOT NULL,
    metadata jsonb,
    performed_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT transfer_events_event_type_check CHECK (
        event_type IN (
            'CREATED',
            'UPDATED',
            'DISPATCHED',
            'VALIDATION_STARTED',
            'ITEM_SCANNED',
            'COMPLETED',
            'COMPLETED_WITH_DISCREPANCY',
            'CANCELLED',
            'DISCREPANCY_RESOLVED'
        )
    ),
    CONSTRAINT transfer_events_from_status_check CHECK (
        from_status IS NULL
        OR from_status IN (
            'DRAFT',
            'IN_TRANSIT',
            'VALIDATION_IN_PROGRESS',
            'COMPLETED',
            'COMPLETED_WITH_DISCREPANCY',
            'CANCELLED'
        )
    ),
    CONSTRAINT transfer_events_to_status_check CHECK (
        to_status IN (
            'DRAFT',
            'IN_TRANSIT',
            'VALIDATION_IN_PROGRESS',
            'COMPLETED',
            'COMPLETED_WITH_DISCREPANCY',
            'CANCELLED'
        )
    )
);

-- Transfer Discrepancy
CREATE TABLE transfer_discrepancy (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    transfer_id uuid NOT NULL,
    transfer_item_id uuid NOT NULL,
    resolved_by uuid,
    discrepancy_type character varying(20) NOT NULL,
    status character varying(30) NOT NULL,
    resolution character varying(30),
    expected_quantity numeric(15, 3) NOT NULL,
    received_quantity numeric(15, 3) NOT NULL,
    difference numeric(15, 3) NOT NULL,
    resolution_notes text,
    resolved_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT transfer_discrepancy_discrepancy_type_check CHECK (
        discrepancy_type IN ('SHORTAGE', 'EXCESS')
    ),
    CONSTRAINT transfer_discrepancy_status_check CHECK (
        status IN (
            'PENDING_RESOLUTION',
            'RESOLVED',
            'WRITTEN_OFF'
        )
    ),
    CONSTRAINT transfer_discrepancy_resolution_check CHECK (
        resolution IS NULL
        OR resolution IN (
            'WRITE_OFF',
            'FOUND',
            'RETURN_TRANSIT',
            'ACCEPTED'
        )
    )
);

-- Scan Logs
CREATE TABLE scan_logs (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    transfer_id uuid NOT NULL,
    transfer_item_id uuid NOT NULL,
    idempotency_key uuid NOT NULL UNIQUE,
    barcode character varying(255) NOT NULL,
    quantity numeric(15, 3) NOT NULL,
    processed_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL
);

-- Inventory Ledger
CREATE TABLE inventory_ledger (
    id uuid NOT NULL PRIMARY KEY,
    tenant_id uuid NOT NULL,
    product_id uuid NOT NULL,
    warehouse_id uuid,
    batch_id uuid,
    transfer_item_id uuid,
    reference_id uuid NOT NULL,
    reference_type character varying(50) NOT NULL,
    entry_type character varying(50) NOT NULL,
    quantity numeric(15, 3) NOT NULL,
    balance_after numeric(15, 3),
    notes text,
    created_by uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT inventory_ledger_entry_type_check CHECK (
        entry_type IN (
            'PURCHASE_IN',
            'ADJUSTMENT_IN',
            'ADJUSTMENT_OUT',
            'TRANSFER_OUT',
            'TRANSFER_IN_TRANSIT',
            'TRANSFER_IN',
            'TRANSFER_TRANSIT_CONSUMED',
            'TRANSFER_LOSS'
        )
    )
);

-- =============================================
-- FOREIGN KEYS
-- =============================================

-- Categories self-reference
ALTER TABLE categories
ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_category_id) REFERENCES categories (id);

-- Products references
ALTER TABLE products
ADD CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id),
ADD CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id);

-- Product Kits references
ALTER TABLE product_kits
ADD CONSTRAINT fk_product_kits_kit FOREIGN KEY (kit_product_id) REFERENCES products (id),
ADD CONSTRAINT fk_product_kits_component FOREIGN KEY (component_product_id) REFERENCES products (id);

-- Batches references
ALTER TABLE batches
ADD CONSTRAINT fk_batches_product FOREIGN KEY (product_id) REFERENCES products (id),
ADD CONSTRAINT fk_batches_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
ADD CONSTRAINT fk_batches_origin_batch FOREIGN KEY (origin_batch_id) REFERENCES batches (id),
ADD CONSTRAINT fk_batches_origin_transfer FOREIGN KEY (origin_transfer_id) REFERENCES transfers (id);

-- Role Permissions references
ALTER TABLE role_permissions
ADD CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
ADD CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id);

-- User Roles references
ALTER TABLE user_roles
ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
ADD CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id);

-- User Warehouses references
ALTER TABLE user_warehouses
ADD CONSTRAINT fk_user_warehouses_user FOREIGN KEY (user_id) REFERENCES users (id),
ADD CONSTRAINT fk_user_warehouses_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

-- Refresh Tokens references
ALTER TABLE refresh_tokens
ADD CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id);

-- Transfers references
ALTER TABLE transfers
ADD CONSTRAINT fk_transfers_source_warehouse FOREIGN KEY (source_warehouse_id) REFERENCES warehouses (id),
ADD CONSTRAINT fk_transfers_destination_warehouse FOREIGN KEY (destination_warehouse_id) REFERENCES warehouses (id),
ADD CONSTRAINT fk_transfers_created_by FOREIGN KEY (created_by) REFERENCES users (id),
ADD CONSTRAINT fk_transfers_dispatched_by FOREIGN KEY (dispatched_by) REFERENCES users (id),
ADD CONSTRAINT fk_transfers_validation_started_by FOREIGN KEY (validation_started_by) REFERENCES users (id),
ADD CONSTRAINT fk_transfers_completed_by FOREIGN KEY (completed_by) REFERENCES users (id),
ADD CONSTRAINT fk_transfers_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users (id);

-- Transfer Items references
ALTER TABLE transfer_items
ADD CONSTRAINT fk_transfer_items_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id),
ADD CONSTRAINT fk_transfer_items_product FOREIGN KEY (product_id) REFERENCES products (id),
ADD CONSTRAINT fk_transfer_items_source_batch FOREIGN KEY (source_batch_id) REFERENCES batches (id),
ADD CONSTRAINT fk_transfer_items_destination_batch FOREIGN KEY (destination_batch_id) REFERENCES batches (id);

-- Transfer In Transit references
ALTER TABLE transfer_in_transit
ADD CONSTRAINT fk_transfer_in_transit_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id),
ADD CONSTRAINT fk_transfer_in_transit_transfer_item FOREIGN KEY (transfer_item_id) REFERENCES transfer_items (id),
ADD CONSTRAINT fk_transfer_in_transit_product FOREIGN KEY (product_id) REFERENCES products (id),
ADD CONSTRAINT fk_transfer_in_transit_source_batch FOREIGN KEY (source_batch_id) REFERENCES batches (id);

-- Transfer Events references
ALTER TABLE transfer_events
ADD CONSTRAINT fk_transfer_events_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id),
ADD CONSTRAINT fk_transfer_events_performed_by FOREIGN KEY (performed_by) REFERENCES users (id);

-- Transfer Discrepancy references
ALTER TABLE transfer_discrepancy
ADD CONSTRAINT fk_transfer_discrepancy_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id),
ADD CONSTRAINT fk_transfer_discrepancy_transfer_item FOREIGN KEY (transfer_item_id) REFERENCES transfer_items (id),
ADD CONSTRAINT fk_transfer_discrepancy_resolved_by FOREIGN KEY (resolved_by) REFERENCES users (id);