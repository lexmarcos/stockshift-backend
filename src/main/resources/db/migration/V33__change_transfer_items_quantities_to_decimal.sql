-- V33: Change transfer_items quantities from INTEGER to NUMERIC(15,3)

-- Step 1: Drop inline CHECK constraints
ALTER TABLE transfer_items DROP CONSTRAINT IF EXISTS chk_expected_positive;
ALTER TABLE transfer_items DROP CONSTRAINT IF EXISTS chk_received_non_negative;

-- Step 2: Change column types
ALTER TABLE transfer_items ALTER COLUMN expected_quantity TYPE NUMERIC(15,3) USING expected_quantity::NUMERIC(15,3);
ALTER TABLE transfer_items ALTER COLUMN received_quantity TYPE NUMERIC(15,3) USING received_quantity::NUMERIC(15,3);

-- Step 3: Recreate constraints with new type
ALTER TABLE transfer_items ADD CONSTRAINT chk_expected_positive CHECK (expected_quantity > 0);
ALTER TABLE transfer_items ADD CONSTRAINT chk_received_non_negative CHECK (received_quantity IS NULL OR received_quantity >= 0);
