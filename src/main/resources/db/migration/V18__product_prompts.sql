CREATE TABLE product_prompts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    prompt TEXT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    deleted_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_product_prompts_tenant_active
    ON product_prompts (tenant_id)
    WHERE deleted_at IS NULL;

WITH required_permissions(code, description) AS (
    VALUES
    ('product_prompts:read', 'Read product prompts'),
    ('product_prompts:create', 'Create product prompts'),
    ('product_prompts:update', 'Update product prompts'),
    ('product_prompts:delete', 'Delete product prompts')
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
JOIN permissions p ON p.code IN (
    'product_prompts:read',
    'product_prompts:create',
    'product_prompts:update',
    'product_prompts:delete'
)
WHERE upper(r.name) = 'GERENTE'
ON CONFLICT (permission_id, role_id) DO NOTHING;
