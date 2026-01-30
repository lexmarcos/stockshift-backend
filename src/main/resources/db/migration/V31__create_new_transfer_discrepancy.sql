-- V31__create_new_transfer_discrepancy.sql
CREATE TABLE transfer_discrepancy (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE RESTRICT,
    transfer_item_id UUID NOT NULL REFERENCES transfer_items(id) ON DELETE RESTRICT,

    discrepancy_type VARCHAR(20) NOT NULL,
    expected_quantity DECIMAL(15,3) NOT NULL,
    received_quantity DECIMAL(15,3) NOT NULL,
    difference DECIMAL(15,3) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_RESOLUTION',
    resolution VARCHAR(30),
    resolution_notes TEXT,
    resolved_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    resolved_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_discrepancy_type CHECK (discrepancy_type IN ('SHORTAGE', 'EXCESS')),
    CONSTRAINT chk_discrepancy_status CHECK (status IN (
        'PENDING_RESOLUTION', 'RESOLVED', 'WRITTEN_OFF'
    )),
    CONSTRAINT chk_resolution CHECK (resolution IS NULL OR resolution IN (
        'WRITE_OFF', 'FOUND', 'RETURN_TRANSIT', 'ACCEPTED'
    ))
);

CREATE INDEX idx_discrepancy_tenant ON transfer_discrepancy(tenant_id);
CREATE INDEX idx_discrepancy_transfer ON transfer_discrepancy(transfer_id);
CREATE INDEX idx_discrepancy_item ON transfer_discrepancy(transfer_item_id);
CREATE INDEX idx_discrepancy_pending ON transfer_discrepancy(status) WHERE status = 'PENDING_RESOLUTION';
