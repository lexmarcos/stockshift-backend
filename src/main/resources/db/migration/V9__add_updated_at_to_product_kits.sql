-- Add updated_at column to product_kits table
ALTER TABLE product_kits ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Add update trigger for product_kits
CREATE TRIGGER update_product_kits_updated_at BEFORE UPDATE ON product_kits
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
