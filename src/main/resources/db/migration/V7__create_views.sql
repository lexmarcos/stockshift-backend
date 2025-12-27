-- Stock summary view
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
