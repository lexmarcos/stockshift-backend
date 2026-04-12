-- Add batch_code column to transfer_items for snapshotting the source batch code at transfer creation time
ALTER TABLE transfer_items ADD COLUMN batch_code VARCHAR(255);

-- Backfill existing rows from the source batch
UPDATE transfer_items ti
SET batch_code = b.batch_code
FROM batches b
WHERE ti.source_batch_id = b.id AND ti.batch_code IS NULL;
