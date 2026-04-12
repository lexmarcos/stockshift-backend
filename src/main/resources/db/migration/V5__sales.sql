-- Sales module tables

-- Extend inventory_ledger entry types
ALTER TABLE inventory_ledger DROP CONSTRAINT inventory_ledger_entry_type_check;
ALTER TABLE inventory_ledger ADD CONSTRAINT inventory_ledger_entry_type_check
    CHECK (entry_type IN (
        'PURCHASE_IN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT',
        'TRANSFER_OUT', 'TRANSFER_CANCELLED', 'TRANSFER_IN',
        'TRANSFER_IN_DISCREPANCY', 'USAGE_OUT', 'GIFT_OUT',
        'LOSS_OUT', 'DAMAGE_OUT', 'STOCK_MOVEMENT_IN',
        'STOCK_MOVEMENT_OUT', 'SALE_OUT', 'SALE_CANCEL_IN'
    ));

CREATE TABLE sales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    warehouse_id UUID NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    installments INTEGER,
    discount_percentage NUMERIC(5,2),
    subtotal BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL DEFAULT 0,
    total BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    cancelled_by_user_id UUID,
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_sale_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_sale_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_sale_created_by FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_sale_cancelled_by FOREIGN KEY (cancelled_by_user_id) REFERENCES users(id),
    CONSTRAINT chk_sale_status CHECK (status IN ('COMPLETED', 'CANCELLED'))
);

CREATE TABLE sale_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(100),
    batch_id UUID NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL,
    unit_price BIGINT NOT NULL,
    total_price BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_sale_item_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_item_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_sale_item_batch FOREIGN KEY (batch_id) REFERENCES batches(id)
);

CREATE INDEX idx_sales_tenant_warehouse ON sales(tenant_id, warehouse_id);
CREATE INDEX idx_sales_tenant_status ON sales(tenant_id, status);
CREATE INDEX idx_sales_tenant_created ON sales(tenant_id, created_at DESC);
CREATE INDEX idx_sale_items_sale ON sale_items(sale_id);
CREATE INDEX idx_sale_items_product ON sale_items(product_id);

-- Seed SALES permissions
INSERT INTO permissions (id, resource, action, scope, code, description)
SELECT
    (substr(md5('SALES:CREATE:ALL'), 1, 8) || '-' || substr(md5('SALES:CREATE:ALL'), 9, 4) || '-' || substr(md5('SALES:CREATE:ALL'), 13, 4) || '-' || substr(md5('SALES:CREATE:ALL'), 17, 4) || '-' || substr(md5('SALES:CREATE:ALL'), 21, 12))::uuid,
    'SALES', 'CREATE', 'ALL', 'sales:create', 'SALES:CREATE:ALL'
UNION ALL SELECT
    (substr(md5('SALES:READ:ALL'), 1, 8) || '-' || substr(md5('SALES:READ:ALL'), 9, 4) || '-' || substr(md5('SALES:READ:ALL'), 13, 4) || '-' || substr(md5('SALES:READ:ALL'), 17, 4) || '-' || substr(md5('SALES:READ:ALL'), 21, 12))::uuid,
    'SALES', 'READ', 'ALL', 'sales:read', 'SALES:READ:ALL'
UNION ALL SELECT
    (substr(md5('SALES:READ:OWN_WAREHOUSE'), 1, 8) || '-' || substr(md5('SALES:READ:OWN_WAREHOUSE'), 9, 4) || '-' || substr(md5('SALES:READ:OWN_WAREHOUSE'), 13, 4) || '-' || substr(md5('SALES:READ:OWN_WAREHOUSE'), 17, 4) || '-' || substr(md5('SALES:READ:OWN_WAREHOUSE'), 21, 12))::uuid,
    'SALES', 'READ', 'OWN_WAREHOUSE', 'sales:read:own_warehouse', 'SALES:READ:OWN_WAREHOUSE'
UNION ALL SELECT
    (substr(md5('SALES:CANCEL:ALL'), 1, 8) || '-' || substr(md5('SALES:CANCEL:ALL'), 9, 4) || '-' || substr(md5('SALES:CANCEL:ALL'), 13, 4) || '-' || substr(md5('SALES:CANCEL:ALL'), 17, 4) || '-' || substr(md5('SALES:CANCEL:ALL'), 21, 12))::uuid,
    'SALES', 'CANCEL', 'ALL', 'sales:cancel', 'SALES:CANCEL:ALL'
UNION ALL SELECT
    (substr(md5('SALES:CANCEL:OWN_WAREHOUSE'), 1, 8) || '-' || substr(md5('SALES:CANCEL:OWN_WAREHOUSE'), 9, 4) || '-' || substr(md5('SALES:CANCEL:OWN_WAREHOUSE'), 13, 4) || '-' || substr(md5('SALES:CANCEL:OWN_WAREHOUSE'), 17, 4) || '-' || substr(md5('SALES:CANCEL:OWN_WAREHOUSE'), 21, 12))::uuid,
    'SALES', 'CANCEL', 'OWN_WAREHOUSE', 'sales:cancel:own_warehouse', 'SALES:CANCEL:OWN_WAREHOUSE'
ON CONFLICT (code) DO NOTHING;

-- Grant SALES permissions to ADMIN role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.resource = 'SALES'
ON CONFLICT (permission_id, role_id) DO NOTHING;
