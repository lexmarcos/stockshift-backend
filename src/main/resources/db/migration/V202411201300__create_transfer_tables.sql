-- Stock Transfers table
CREATE TABLE stock_transfers (
    id UUID PRIMARY KEY,
    origin_warehouse_id UUID NOT NULL,
    destination_warehouse_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    notes VARCHAR(500),
    idempotency_key VARCHAR(255) UNIQUE,
    created_by_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_by_id UUID,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    outbound_event_id UUID,
    inbound_event_id UUID,
    CONSTRAINT fk_transfer_origin_warehouse FOREIGN KEY (origin_warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_transfer_destination_warehouse FOREIGN KEY (destination_warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_transfer_created_by FOREIGN KEY (created_by_id) REFERENCES users(id),
    CONSTRAINT fk_transfer_confirmed_by FOREIGN KEY (confirmed_by_id) REFERENCES users(id),
    CONSTRAINT fk_transfer_outbound_event FOREIGN KEY (outbound_event_id) REFERENCES stock_events(id),
    CONSTRAINT fk_transfer_inbound_event FOREIGN KEY (inbound_event_id) REFERENCES stock_events(id),
    CONSTRAINT chk_transfer_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELED'))
);

-- Stock Transfer Lines table
CREATE TABLE stock_transfer_lines (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT fk_transfer_line_transfer FOREIGN KEY (transfer_id) REFERENCES stock_transfers(id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_line_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    CONSTRAINT chk_transfer_line_quantity_positive CHECK (quantity > 0)
);

-- Indexes for performance
CREATE INDEX idx_stock_transfers_origin_warehouse ON stock_transfers(origin_warehouse_id);
CREATE INDEX idx_stock_transfers_destination_warehouse ON stock_transfers(destination_warehouse_id);
CREATE INDEX idx_stock_transfers_status ON stock_transfers(status);
CREATE INDEX idx_stock_transfers_occurred_at ON stock_transfers(occurred_at);
CREATE INDEX idx_stock_transfers_created_by ON stock_transfers(created_by_id);
CREATE INDEX idx_stock_transfers_confirmed_by ON stock_transfers(confirmed_by_id);
CREATE INDEX idx_stock_transfer_lines_transfer ON stock_transfer_lines(transfer_id);
CREATE INDEX idx_stock_transfer_lines_variant ON stock_transfer_lines(variant_id);
