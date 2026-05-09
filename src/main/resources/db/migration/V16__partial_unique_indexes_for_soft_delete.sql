ALTER TABLE products DROP CONSTRAINT IF EXISTS products_tenant_id_sku_key;
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_tenant_id_barcode_key;
ALTER TABLE batches DROP CONSTRAINT IF EXISTS batches_tenant_id_batch_code_key;

CREATE UNIQUE INDEX IF NOT EXISTS products_tenant_id_sku_key
    ON products (tenant_id, sku)
    WHERE deleted_at IS NULL AND sku IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS products_tenant_id_barcode_key
    ON products (tenant_id, barcode)
    WHERE deleted_at IS NULL AND barcode IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS batches_tenant_id_batch_code_key
    ON batches (tenant_id, batch_code)
    WHERE deleted_at IS NULL;
