CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    source_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    destination_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    notes TEXT,
    created_by_user_id UUID NOT NULL,
    executed_by_user_id UUID,
    executed_at TIMESTAMP,
    validated_by_user_id UUID,
    validated_at TIMESTAMP,
    cancelled_by_user_id UUID,
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_transfers_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_transfers_different_warehouses CHECK (source_warehouse_id != destination_warehouse_id)
);

CREATE INDEX idx_transfers_tenant ON transfers(tenant_id);
CREATE INDEX idx_transfers_source_warehouse ON transfers(source_warehouse_id);
CREATE INDEX idx_transfers_destination_warehouse ON transfers(destination_warehouse_id);
CREATE INDEX idx_transfers_status ON transfers(status);

CREATE TABLE transfer_items (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    source_batch_id UUID NOT NULL REFERENCES batches(id),
    product_id UUID NOT NULL,
    product_barcode VARCHAR(255),
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(255),
    quantity_sent NUMERIC(19, 4) NOT NULL,
    quantity_received NUMERIC(19, 4) NOT NULL DEFAULT 0,
    destination_batch_id UUID REFERENCES batches(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transfer_items_transfer ON transfer_items(transfer_id);
CREATE INDEX idx_transfer_items_source_batch ON transfer_items(source_batch_id);
CREATE INDEX idx_transfer_items_product_barcode ON transfer_items(product_barcode);

CREATE TABLE transfer_validation_logs (
    id UUID PRIMARY KEY,
    transfer_item_id UUID REFERENCES transfer_items(id) ON DELETE CASCADE,
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    barcode VARCHAR(255) NOT NULL,
    validated_by_user_id UUID NOT NULL,
    validated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    valid BOOLEAN NOT NULL
);

CREATE INDEX idx_transfer_validation_logs_transfer ON transfer_validation_logs(transfer_id);
CREATE INDEX idx_transfer_validation_logs_item ON transfer_validation_logs(transfer_item_id);
