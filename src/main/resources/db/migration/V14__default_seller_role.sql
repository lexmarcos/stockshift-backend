-- Backfill the default seller role for existing tenants.

WITH missing_seller_roles AS (
    SELECT
        gen_random_uuid() AS id,
        t.id AS tenant_id
    FROM tenants t
    WHERE NOT EXISTS (
        SELECT 1
        FROM roles r
        WHERE r.tenant_id = t.id
          AND r.name = 'VENDEDOR'
    )
)
INSERT INTO roles (id, tenant_id, name, description, is_system_role, created_at, updated_at)
SELECT
    id,
    tenant_id,
    'VENDEDOR',
    'Seller role with sales and stock read access',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM missing_seller_roles;

UPDATE roles
SET is_system_role = true,
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'VENDEDOR'
  AND is_system_role = false;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'sales:create',
    'sales:read',
    'products:read',
    'batches:read',
    'brands:read',
    'categories:read'
)
WHERE r.name = 'VENDEDOR'
ON CONFLICT (permission_id, role_id) DO NOTHING;
