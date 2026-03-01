-- Stock Movement module tables

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    warehouse_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    notes TEXT,
    created_by_user_id UUID NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_stock_movement_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_stock_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_stock_movement_user FOREIGN KEY (created_by_user_id) REFERENCES users(id)
);

CREATE TABLE stock_movement_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_movement_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(100),
    batch_id UUID NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_smi_stock_movement FOREIGN KEY (stock_movement_id) REFERENCES stock_movements(id) ON DELETE CASCADE,
    CONSTRAINT fk_smi_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_smi_batch FOREIGN KEY (batch_id) REFERENCES batches(id)
);

-- Indexes for common queries
CREATE INDEX idx_stock_movements_tenant_warehouse ON stock_movements(tenant_id, warehouse_id);
CREATE INDEX idx_stock_movements_tenant_type ON stock_movements(tenant_id, type);
CREATE INDEX idx_stock_movements_tenant_created ON stock_movements(tenant_id, created_at DESC);
CREATE INDEX idx_stock_movements_reference ON stock_movements(reference_type, reference_id);
CREATE INDEX idx_stock_movement_items_movement ON stock_movement_items(stock_movement_id);
CREATE INDEX idx_stock_movement_items_product ON stock_movement_items(product_id);
