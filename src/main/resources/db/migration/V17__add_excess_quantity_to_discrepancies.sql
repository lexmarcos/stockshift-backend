-- Add excess_quantity column to transfer_discrepancies table
ALTER TABLE transfer_discrepancies ADD COLUMN excess_quantity INTEGER NOT NULL DEFAULT 0;

-- Update existing records to have explicit 0 for excess_quantity (already handled by DEFAULT)
-- Remove the default after migration since we want explicit values
ALTER TABLE transfer_discrepancies ALTER COLUMN excess_quantity DROP DEFAULT;
