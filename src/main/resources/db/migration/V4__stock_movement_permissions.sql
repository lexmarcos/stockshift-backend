-- Seed stock_movements permissions

INSERT INTO permissions (id, code, description)
SELECT (
        substr(md5('permission:' || rp.code), 1, 8) || '-' ||
        substr(md5('permission:' || rp.code), 9, 4) || '-' ||
        substr(md5('permission:' || rp.code), 13, 4) || '-' ||
        substr(md5('permission:' || rp.code), 17, 4) || '-' ||
        substr(md5('permission:' || rp.code), 21, 12)
       )::uuid,
       rp.code,
       rp.description
FROM (VALUES
    ('stock_movements:read', 'Read stock movements'),
    ('stock_movements:create', 'Create stock movements')
) AS rp(code, description)
WHERE NOT EXISTS (
    SELECT 1 FROM permissions p WHERE p.code = rp.code
);
