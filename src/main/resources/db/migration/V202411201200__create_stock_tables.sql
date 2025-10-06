CREATE TABLE IF NOT EXISTS stock_events (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    warehouse_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    reason_code VARCHAR(40),
    idempotency_key VARCHAR(100) UNIQUE,
    notes VARCHAR(500),
    created_by_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_stock_events_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_stock_events_created_by FOREIGN KEY (created_by_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS stock_event_lines (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_event_lines_event FOREIGN KEY (event_id) REFERENCES stock_events(id),
    CONSTRAINT fk_stock_event_lines_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id)
);

CREATE INDEX IF NOT EXISTS idx_stock_event_lines_event ON stock_event_lines(event_id);
CREATE INDEX IF NOT EXISTS idx_stock_event_lines_variant ON stock_event_lines(variant_id);

CREATE TABLE IF NOT EXISTS stock_items (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    quantity BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_stock_items_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_stock_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    CONSTRAINT uk_stock_items_warehouse_variant UNIQUE (warehouse_id, variant_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_stock_events_idempotency_key ON stock_events(idempotency_key);
