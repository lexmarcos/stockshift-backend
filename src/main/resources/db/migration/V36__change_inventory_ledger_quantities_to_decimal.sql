-- V36: Change inventory_ledger quantity and balance_after from INTEGER to NUMERIC(15,3)

-- Step 1: Drop CHECK constraint on quantity
ALTER TABLE inventory_ledger DROP CONSTRAINT IF EXISTS chk_quantity_positive;

-- Step 2: Change column types
ALTER TABLE inventory_ledger ALTER COLUMN quantity TYPE NUMERIC(15,3) USING quantity::NUMERIC(15,3);
ALTER TABLE inventory_ledger ALTER COLUMN balance_after TYPE NUMERIC(15,3) USING balance_after::NUMERIC(15,3);

-- Step 3: Recreate constraint
ALTER TABLE inventory_ledger ADD CONSTRAINT chk_quantity_positive CHECK (quantity > 0);
