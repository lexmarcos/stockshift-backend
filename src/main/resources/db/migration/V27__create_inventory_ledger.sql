-- Inventory ledger table (append-only accounting records)
CREATE TABLE inventory_ledger (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,

    warehouse_id UUID REFERENCES warehouses(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    batch_id UUID REFERENCES batches(id) ON DELETE RESTRICT,

    entry_type VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    balance_after INTEGER,

    reference_type VARCHAR(50) NOT NULL,
    reference_id UUID NOT NULL,
    transfer_item_id UUID REFERENCES transfer_items(id) ON DELETE RESTRICT,

    notes TEXT,

    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_entry_type CHECK (entry_type IN (
        'PURCHASE_IN', 'SALE_OUT', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT',
        'TRANSFER_OUT', 'TRANSFER_IN_TRANSIT', 'TRANSFER_IN',
        'TRANSFER_TRANSIT_CONSUMED', 'TRANSFER_LOSS', 'RETURN_IN'
    ))
);

-- Indexes for inventory_ledger
CREATE INDEX idx_ledger_tenant ON inventory_ledger(tenant_id);
CREATE INDEX idx_ledger_warehouse ON inventory_ledger(warehouse_id);
CREATE INDEX idx_ledger_product ON inventory_ledger(product_id);
CREATE INDEX idx_ledger_batch ON inventory_ledger(batch_id);
CREATE INDEX idx_ledger_reference ON inventory_ledger(reference_type, reference_id);
CREATE INDEX idx_ledger_transfer_item ON inventory_ledger(transfer_item_id);
CREATE INDEX idx_ledger_created_at ON inventory_ledger(created_at DESC);
CREATE INDEX idx_ledger_entry_type ON inventory_ledger(entry_type);

-- Trigger to prevent modifications (append-only)
CREATE OR REPLACE FUNCTION prevent_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Inventory ledger entries cannot be modified or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_immutable
    BEFORE UPDATE OR DELETE ON inventory_ledger
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_modification();
