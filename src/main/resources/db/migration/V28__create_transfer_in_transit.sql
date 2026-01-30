-- Transfer in transit table (tracks goods between dispatch and receipt)
CREATE TABLE transfer_in_transit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE RESTRICT,
    transfer_item_id UUID NOT NULL REFERENCES transfer_items(id) ON DELETE RESTRICT,

    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    source_batch_id UUID NOT NULL REFERENCES batches(id) ON DELETE RESTRICT,

    quantity INTEGER NOT NULL,
    consumed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transit_quantity_non_negative CHECK (quantity >= 0)
);

-- Indexes
CREATE INDEX idx_transit_transfer ON transfer_in_transit(transfer_id);
CREATE INDEX idx_transit_pending ON transfer_in_transit(consumed_at) WHERE consumed_at IS NULL;
CREATE INDEX idx_transit_tenant ON transfer_in_transit(tenant_id);

-- Update trigger
CREATE TRIGGER update_transfer_in_transit_updated_at BEFORE UPDATE ON transfer_in_transit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
