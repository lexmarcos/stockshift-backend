-- Add CHECK constraint to ensure batch quantity is never negative
-- This provides database-level protection against negative inventory values

ALTER TABLE batches
ADD CONSTRAINT chk_batch_quantity_non_negative CHECK (quantity >= 0);
