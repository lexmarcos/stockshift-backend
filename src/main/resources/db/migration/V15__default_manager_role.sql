-- Backfill the default manager role for existing tenants.

WITH missing_manager_roles AS (
    SELECT
        gen_random_uuid() AS id,
        t.id AS tenant_id
    FROM tenants t
    WHERE NOT EXISTS (
        SELECT 1
        FROM roles r
        WHERE r.tenant_id = t.id
          AND r.name = 'GERENTE'
    )
)
INSERT INTO roles (id, tenant_id, name, description, is_system_role, created_at, updated_at)
SELECT
    id,
    tenant_id,
    'GERENTE',
    'Manager role with operational access',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM missing_manager_roles;

UPDATE roles
SET is_system_role = true,
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'GERENTE'
  AND is_system_role = false;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'sales:create',
    'sales:read',
    'sales:cancel',
    'products:read',
    'products:create',
    'products:update',
    'products:delete',
    'products:analyze_image',
    'brands:read',
    'brands:create',
    'brands:update',
    'brands:delete',
    'categories:read',
    'categories:create',
    'categories:update',
    'categories:delete',
    'batches:read',
    'batches:create',
    'batches:update',
    'batches:delete',
    'warehouses:read',
    'warehouses:create',
    'warehouses:update',
    'warehouses:delete',
    'stock_movements:read',
    'stock_movements:create',
    'transfers:read',
    'transfers:create',
    'transfers:update',
    'transfers:delete',
    'transfers:execute',
    'transfers:validate',
    'reports:read'
)
WHERE r.name = 'GERENTE'
ON CONFLICT (permission_id, role_id) DO NOTHING;
