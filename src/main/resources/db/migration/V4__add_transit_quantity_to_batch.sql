ALTER TABLE batches ADD COLUMN transit_quantity NUMERIC(19, 4) NOT NULL DEFAULT 0;

-- Add constraint to ensure transit_quantity is not negative
ALTER TABLE batches ADD CONSTRAINT chk_batches_transit_quantity_non_negative CHECK (transit_quantity >= 0);
