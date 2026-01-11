-- ======================================
-- Migration: Decimal to Cents
-- Converts price fields from DECIMAL(15,2) to BIGINT (cents)
-- ======================================

-- BATCHES TABLE
-- Step 1: Add temporary columns
ALTER TABLE batches
    ADD COLUMN cost_price_cents BIGINT,
    ADD COLUMN selling_price_cents BIGINT;

-- Step 2: Convert existing data (multiply by 100)
UPDATE batches
SET
    cost_price_cents = CAST(COALESCE(cost_price, 0) * 100 AS BIGINT),
    selling_price_cents = CAST(COALESCE(selling_price, 0) * 100 AS BIGINT);

-- Step 2.5: Drop dependent view
DROP VIEW IF EXISTS v_stock_summary;

-- Step 3: Drop old columns
ALTER TABLE batches
    DROP COLUMN cost_price,
    DROP COLUMN selling_price;

-- Step 4: Rename new columns to original names
ALTER TABLE batches
    RENAME COLUMN cost_price_cents TO cost_price;

ALTER TABLE batches
    RENAME COLUMN selling_price_cents TO selling_price;

-- Step 5: Recreate view with BIGINT price columns
CREATE OR REPLACE VIEW v_stock_summary AS
SELECT
    p.id as product_id,
    p.name as product_name,
    p.tenant_id,
    w.id as warehouse_id,
    w.name as warehouse_name,
    SUM(b.quantity) as total_quantity,
    MIN(b.expiration_date) as nearest_expiration,
    AVG(b.cost_price) as avg_cost_price,
    AVG(b.selling_price) as avg_selling_price
FROM products p
JOIN batches b ON b.product_id = p.id
JOIN warehouses w ON w.id = b.warehouse_id
WHERE b.quantity > 0
    AND p.deleted_at IS NULL
    AND b.deleted_at IS NULL
    AND w.deleted_at IS NULL
GROUP BY p.id, p.name, p.tenant_id, w.id, w.name;
