-- Transfer table (business process aggregate)
CREATE TABLE transfers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    transfer_code VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',

    source_warehouse_id UUID NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    destination_warehouse_id UUID NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,

    notes TEXT,

    -- Audit fields
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    dispatched_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    dispatched_at TIMESTAMPTZ,
    validation_started_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    validation_started_at TIMESTAMPTZ,
    completed_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    completed_at TIMESTAMPTZ,
    cancelled_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,

    -- Concurrency control
    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transfer_status CHECK (status IN (
        'DRAFT', 'IN_TRANSIT', 'VALIDATION_IN_PROGRESS',
        'COMPLETED', 'COMPLETED_WITH_DISCREPANCY', 'CANCELLED'
    )),
    CONSTRAINT chk_different_warehouses CHECK (source_warehouse_id != destination_warehouse_id),
    CONSTRAINT uq_transfer_code_tenant UNIQUE (tenant_id, transfer_code)
);

-- Transfer items table
CREATE TABLE transfer_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,

    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    source_batch_id UUID NOT NULL REFERENCES batches(id) ON DELETE RESTRICT,
    destination_batch_id UUID REFERENCES batches(id) ON DELETE RESTRICT,

    expected_quantity INTEGER NOT NULL,
    received_quantity INTEGER,

    item_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_expected_positive CHECK (expected_quantity > 0),
    CONSTRAINT chk_received_non_negative CHECK (received_quantity IS NULL OR received_quantity >= 0),
    CONSTRAINT chk_item_status CHECK (item_status IN (
        'PENDING', 'RECEIVED', 'PARTIAL', 'EXCESS', 'MISSING'
    ))
);

-- Indexes for transfers
CREATE INDEX idx_transfers_tenant ON transfers(tenant_id);
CREATE INDEX idx_transfers_status ON transfers(status);
CREATE INDEX idx_transfers_source ON transfers(source_warehouse_id);
CREATE INDEX idx_transfers_destination ON transfers(destination_warehouse_id);
CREATE INDEX idx_transfers_created_at ON transfers(created_at DESC);

-- Indexes for transfer_items
CREATE INDEX idx_transfer_items_transfer ON transfer_items(transfer_id);
CREATE INDEX idx_transfer_items_product ON transfer_items(product_id);
CREATE INDEX idx_transfer_items_source_batch ON transfer_items(source_batch_id);

-- Update triggers
CREATE TRIGGER update_transfers_updated_at BEFORE UPDATE ON transfers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transfer_items_updated_at BEFORE UPDATE ON transfer_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Sequence for transfer_code generation
CREATE SEQUENCE transfer_code_seq START 1;
