-- Transfer validations table
CREATE TABLE transfer_validations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stock_movement_id UUID NOT NULL REFERENCES stock_movements(id) ON DELETE RESTRICT,
    validated_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    notes TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Transfer validation items table
CREATE TABLE transfer_validation_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_validation_id UUID NOT NULL REFERENCES transfer_validations(id) ON DELETE CASCADE,
    stock_movement_item_id UUID NOT NULL REFERENCES stock_movement_items(id) ON DELETE RESTRICT,
    expected_quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    scanned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Transfer discrepancies table
CREATE TABLE transfer_discrepancies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_validation_id UUID NOT NULL REFERENCES transfer_validations(id) ON DELETE CASCADE,
    stock_movement_item_id UUID NOT NULL REFERENCES stock_movement_items(id) ON DELETE RESTRICT,
    expected_quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL,
    missing_quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_transfer_validations_movement ON transfer_validations(stock_movement_id);
CREATE INDEX idx_transfer_validations_user ON transfer_validations(validated_by);
CREATE INDEX idx_transfer_validations_status ON transfer_validations(status);

CREATE INDEX idx_transfer_validation_items_validation ON transfer_validation_items(transfer_validation_id);
CREATE INDEX idx_transfer_validation_items_movement_item ON transfer_validation_items(stock_movement_item_id);

CREATE INDEX idx_transfer_discrepancies_validation ON transfer_discrepancies(transfer_validation_id);

-- Update triggers
CREATE TRIGGER update_transfer_validations_updated_at BEFORE UPDATE ON transfer_validations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transfer_validation_items_updated_at BEFORE UPDATE ON transfer_validation_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
