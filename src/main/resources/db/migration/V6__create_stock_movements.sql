-- Stock movements table
CREATE TABLE stock_movements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    movement_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_warehouse_id UUID REFERENCES warehouses(id) ON DELETE RESTRICT,
    destination_warehouse_id UUID REFERENCES warehouses(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

-- Stock movement items table
CREATE TABLE stock_movement_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    movement_id UUID NOT NULL REFERENCES stock_movements(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    batch_id UUID REFERENCES batches(id) ON DELETE RESTRICT,
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
