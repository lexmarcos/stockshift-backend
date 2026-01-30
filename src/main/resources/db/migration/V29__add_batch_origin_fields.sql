-- Add origin tracking fields to batches for transfer traceability
ALTER TABLE batches ADD COLUMN origin_transfer_id UUID REFERENCES transfers(id) ON DELETE RESTRICT;
ALTER TABLE batches ADD COLUMN origin_batch_id UUID REFERENCES batches(id) ON DELETE RESTRICT;

-- Index for finding batches created from transfers
CREATE INDEX idx_batches_origin_transfer ON batches(origin_transfer_id) WHERE origin_transfer_id IS NOT NULL;
CREATE INDEX idx_batches_origin_batch ON batches(origin_batch_id) WHERE origin_batch_id IS NOT NULL;
