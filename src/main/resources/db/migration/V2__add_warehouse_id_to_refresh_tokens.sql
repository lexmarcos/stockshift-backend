-- Add warehouse_id column to refresh_tokens table
ALTER TABLE refresh_tokens ADD COLUMN warehouse_id UUID;

-- Add foreign key constraint
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL;
