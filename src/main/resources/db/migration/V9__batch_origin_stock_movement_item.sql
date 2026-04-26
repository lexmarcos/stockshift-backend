ALTER TABLE batches
    ADD COLUMN origin_stock_movement_item_id UUID;

ALTER TABLE batches
    ADD CONSTRAINT fk_batches_origin_stock_movement_item
    FOREIGN KEY (origin_stock_movement_item_id) REFERENCES stock_movement_items(id)
    ON DELETE SET NULL;

CREATE INDEX idx_batches_origin_stock_movement_item
    ON batches(origin_stock_movement_item_id);
