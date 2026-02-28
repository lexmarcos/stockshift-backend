-- Flyway Migration V2: RBAC by Warehouse Scope

ALTER TABLE permissions ADD COLUMN IF NOT EXISTS code VARCHAR(120);

ALTER TABLE permissions DROP CONSTRAINT IF EXISTS permissions_resource_check;
ALTER TABLE permissions DROP CONSTRAINT IF EXISTS permissions_action_check;
ALTER TABLE permissions DROP CONSTRAINT IF EXISTS permissions_scope_check;
ALTER TABLE permissions DROP CONSTRAINT IF EXISTS permissions_resource_action_scope_key;

ALTER TABLE permissions ALTER COLUMN resource DROP NOT NULL;
ALTER TABLE permissions ALTER COLUMN action DROP NOT NULL;
ALTER TABLE permissions ALTER COLUMN scope DROP NOT NULL;

UPDATE permissions
SET code = CASE
    WHEN resource = 'USER' AND action = 'READ' THEN 'users:read'
    WHEN resource = 'USER' AND action = 'CREATE' THEN 'users:create'
    WHEN resource = 'USER' AND action = 'UPDATE' THEN 'users:update'
    WHEN resource = 'USER' AND action = 'DELETE' THEN 'users:delete'

    WHEN resource = 'WAREHOUSE' AND action = 'READ' THEN 'warehouses:read'
    WHEN resource = 'WAREHOUSE' AND action = 'CREATE' THEN 'warehouses:create'
    WHEN resource = 'WAREHOUSE' AND action = 'UPDATE' THEN 'warehouses:update'
    WHEN resource = 'WAREHOUSE' AND action = 'DELETE' THEN 'warehouses:delete'

    WHEN resource = 'PRODUCT' AND action = 'READ' THEN 'products:read'
    WHEN resource = 'PRODUCT' AND action = 'CREATE' THEN 'products:create'
    WHEN resource = 'PRODUCT' AND action = 'UPDATE' THEN 'products:update'
    WHEN resource = 'PRODUCT' AND action = 'DELETE' THEN 'products:delete'

    WHEN resource = 'REPORT' AND action = 'READ' THEN 'reports:read'

    WHEN resource = 'STOCK' AND action = 'READ' THEN 'batches:read'
    WHEN resource = 'STOCK' AND action = 'CREATE' THEN 'batches:create'
    WHEN resource = 'STOCK' AND action = 'UPDATE' THEN 'batches:update'
    WHEN resource = 'STOCK' AND action = 'DELETE' THEN 'batches:delete'

    WHEN resource = 'TRANSFER' AND action = 'READ' THEN 'transfers:read'
    WHEN resource = 'TRANSFER' AND action = 'CREATE' THEN 'transfers:create'
    WHEN resource = 'TRANSFER' AND action = 'UPDATE' THEN 'transfers:update'
    WHEN resource = 'TRANSFER' AND action = 'DELETE' THEN 'transfers:delete'
    WHEN resource = 'TRANSFER' AND action = 'CANCEL' THEN 'transfers:delete'
    WHEN resource = 'TRANSFER' AND action = 'EXECUTE' THEN 'transfers:execute'
    WHEN resource = 'TRANSFER' AND action = 'VALIDATE' THEN 'transfers:validate'

    ELSE lower(resource) || ':' || lower(action)
END
WHERE code IS NULL;

WITH required_permissions(code, description) AS (
    VALUES
    ('users:read', 'users:read'),
    ('users:create', 'users:create'),
    ('users:update', 'users:update'),
    ('users:delete', 'users:delete'),
    ('roles:read', 'roles:read'),
    ('roles:create', 'roles:create'),
    ('roles:update', 'roles:update'),
    ('roles:delete', 'roles:delete'),
    ('brands:read', 'brands:read'),
    ('brands:create', 'brands:create'),
    ('brands:update', 'brands:update'),
    ('brands:delete', 'brands:delete'),
    ('categories:read', 'categories:read'),
    ('categories:create', 'categories:create'),
    ('categories:update', 'categories:update'),
    ('categories:delete', 'categories:delete'),
    ('products:read', 'products:read'),
    ('products:create', 'products:create'),
    ('products:update', 'products:update'),
    ('products:delete', 'products:delete'),
    ('products:analyze_image', 'products:analyze_image'),
    ('warehouses:read', 'warehouses:read'),
    ('warehouses:create', 'warehouses:create'),
    ('warehouses:update', 'warehouses:update'),
    ('warehouses:delete', 'warehouses:delete'),
    ('batches:read', 'batches:read'),
    ('batches:create', 'batches:create'),
    ('batches:update', 'batches:update'),
    ('batches:delete', 'batches:delete'),
    ('transfers:read', 'transfers:read'),
    ('transfers:create', 'transfers:create'),
    ('transfers:update', 'transfers:update'),
    ('transfers:delete', 'transfers:delete'),
    ('transfers:execute', 'transfers:execute'),
    ('transfers:validate', 'transfers:validate'),
    ('reports:read', 'reports:read'),
    ('permissions:read', 'permissions:read')
)
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
FROM required_permissions rp
WHERE NOT EXISTS (
    SELECT 1 FROM permissions p WHERE p.code = rp.code
);

WITH ranked AS (
    SELECT
        id,
        code,
        MIN(id::text) OVER (PARTITION BY code)::uuid AS canonical_id,
        ROW_NUMBER() OVER (PARTITION BY code ORDER BY id) AS rn
    FROM permissions
    WHERE code IS NOT NULL
),
mapped AS (
    SELECT DISTINCT
        rp.role_id,
        r.canonical_id AS permission_id
    FROM role_permissions rp
    JOIN ranked r ON r.id = rp.permission_id
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT role_id, permission_id
FROM mapped
ON CONFLICT (permission_id, role_id) DO NOTHING;

WITH ranked AS (
    SELECT
        id,
        ROW_NUMBER() OVER (PARTITION BY code ORDER BY id) AS rn
    FROM permissions
    WHERE code IS NOT NULL
)
DELETE FROM role_permissions rp
USING ranked r
WHERE rp.permission_id = r.id
  AND r.rn > 1;

WITH ranked AS (
    SELECT
        id,
        ROW_NUMBER() OVER (PARTITION BY code ORDER BY id) AS rn
    FROM permissions
    WHERE code IS NOT NULL
)
DELETE FROM permissions p
USING ranked r
WHERE p.id = r.id
  AND r.rn > 1;

ALTER TABLE permissions ALTER COLUMN code SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'permissions_code_key'
    ) THEN
        ALTER TABLE permissions
            ADD CONSTRAINT permissions_code_key UNIQUE (code);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS user_role_warehouses (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id, warehouse_id)
);

INSERT INTO user_role_warehouses (user_id, role_id, warehouse_id)
SELECT ur.user_id, ur.role_id, uw.warehouse_id
FROM user_roles ur
JOIN user_warehouses uw ON uw.user_id = ur.user_id
ON CONFLICT (user_id, role_id, warehouse_id) DO NOTHING;

INSERT INTO user_role_warehouses (user_id, role_id, warehouse_id)
SELECT ur.user_id, ur.role_id, w.id
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN users u ON u.id = ur.user_id
JOIN warehouses w ON w.tenant_id = u.tenant_id
WHERE upper(r.name) IN ('ADMIN', 'SUPER_ADMIN')
ON CONFLICT (user_id, role_id, warehouse_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_urw_user_wh ON user_role_warehouses(user_id, warehouse_id);
