-- V35: Change transfer_in_transit quantity from INTEGER to NUMERIC(15,3)

-- Step 1: Drop CHECK constraint
ALTER TABLE transfer_in_transit DROP CONSTRAINT IF EXISTS chk_transit_quantity_non_negative;

-- Step 2: Change column type
ALTER TABLE transfer_in_transit ALTER COLUMN quantity TYPE NUMERIC(15,3) USING quantity::NUMERIC(15,3);

-- Step 3: Recreate constraint
ALTER TABLE transfer_in_transit ADD CONSTRAINT chk_transit_quantity_non_negative CHECK (quantity >= 0);
