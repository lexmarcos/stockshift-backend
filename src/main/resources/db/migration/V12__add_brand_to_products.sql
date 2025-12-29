-- Add brand_id column to products
ALTER TABLE products
    ADD COLUMN brand_id UUID REFERENCES brands(id) ON DELETE RESTRICT;

-- Index for foreign key
CREATE INDEX idx_products_brand ON products(brand_id) WHERE brand_id IS NOT NULL;

-- Validate brand belongs to same tenant as product
CREATE OR REPLACE FUNCTION validate_product_brand_tenant()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.brand_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM brands
            WHERE id = NEW.brand_id
            AND tenant_id = NEW.tenant_id
        ) THEN
            RAISE EXCEPTION 'Brand must belong to the same tenant as the product';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_product_brand_tenant_trigger
BEFORE INSERT OR UPDATE OF brand_id ON products
FOR EACH ROW
EXECUTE FUNCTION validate_product_brand_tenant();

-- Comment
COMMENT ON COLUMN products.brand_id IS 'Optional brand association';
