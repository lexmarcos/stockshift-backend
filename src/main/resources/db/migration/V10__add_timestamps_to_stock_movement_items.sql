-- Add timestamp columns to stock_movement_items table
ALTER TABLE stock_movement_items ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE stock_movement_items ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Add update trigger for stock_movement_items
CREATE TRIGGER update_stock_movement_items_updated_at BEFORE UPDATE ON stock_movement_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
