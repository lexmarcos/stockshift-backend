-- Remove existing unique constraints that don't consider soft deletes
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_tenant_id_barcode_key;
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_tenant_id_sku_key;

-- Create partial unique indexes that only apply to non-deleted products
-- This allows the same barcode/sku to be reused after soft deletion
CREATE UNIQUE INDEX products_tenant_id_barcode_active_key
    ON products(tenant_id, barcode)
    WHERE deleted_at IS NULL AND barcode IS NOT NULL;

CREATE UNIQUE INDEX products_tenant_id_sku_active_key
    ON products(tenant_id, sku)
    WHERE deleted_at IS NULL AND sku IS NOT NULL;
