-- V34: Change batches quantity from INTEGER to NUMERIC(15,3)

-- Step 1: Drop inline CHECK constraint from V5 (unnamed, referenced by column)
-- PostgreSQL names inline constraints automatically, we need to find and drop it
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    -- Find the unnamed inline constraint from V5
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_attribute att ON att.attnum = ANY(con.conkey) AND att.attrelid = con.conrelid
    WHERE con.conrelid = 'batches'::regclass
      AND con.contype = 'c'
      AND att.attname = 'quantity'
      AND con.conname != 'chk_batch_quantity_non_negative';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE batches DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- Step 2: Drop named constraint from V18
ALTER TABLE batches DROP CONSTRAINT IF EXISTS chk_batch_quantity_non_negative;

-- Step 3: Change column type
ALTER TABLE batches ALTER COLUMN quantity TYPE NUMERIC(15,3) USING quantity::NUMERIC(15,3);

-- Step 4: Recreate constraint
ALTER TABLE batches ADD CONSTRAINT chk_batch_quantity_non_negative CHECK (quantity >= 0);
