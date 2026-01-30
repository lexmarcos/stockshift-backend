-- Create scan_logs table for scan idempotency
CREATE TABLE scan_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    idempotency_key UUID NOT NULL,
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    transfer_item_id UUID NOT NULL,
    barcode VARCHAR(255) NOT NULL,
    quantity DECIMAL(15, 3) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_scan_logs_idempotency_key UNIQUE (idempotency_key)
);

-- Indexes for performance and uniqueness
CREATE INDEX idx_scan_logs_idempotency_key ON scan_logs(idempotency_key);
CREATE INDEX idx_scan_logs_expires_at ON scan_logs(expires_at);
CREATE INDEX idx_scan_logs_tenant ON scan_logs(tenant_id);
CREATE INDEX idx_scan_logs_transfer ON scan_logs(transfer_id);

-- Update trigger for updated_at column
CREATE TRIGGER update_scan_logs_updated_at BEFORE UPDATE ON scan_logs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
