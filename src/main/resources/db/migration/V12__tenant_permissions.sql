WITH required_permissions(code, description) AS (
    VALUES
    ('tenants:read', 'tenants:read'),
    ('tenants:update', 'tenants:update')
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

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE upper(r.name) IN ('ADMIN', 'SUPER_ADMIN')
  AND p.code IN ('tenants:read', 'tenants:update')
ON CONFLICT (permission_id, role_id) DO NOTHING;
