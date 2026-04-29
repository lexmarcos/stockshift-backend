-- Flyway Migration V11: Audit trail

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    occurred_at TIMESTAMP(6) NOT NULL,
    actor_user_id UUID,
    actor_email VARCHAR(255),
    warehouse_id UUID,
    operation VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    outcome VARCHAR(50) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(120),
    reason TEXT,
    request_id VARCHAR(120),
    http_method VARCHAR(20),
    http_path TEXT,
    http_status INTEGER,
    ip_address VARCHAR(100),
    user_agent TEXT,
    before_state JSONB,
    after_state JSONB,
    changed_fields JSONB,
    metadata JSONB
);

CREATE INDEX idx_audit_events_tenant_date ON audit_events(tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_events_actor_date ON audit_events(actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_events_resource ON audit_events(resource_type, resource_id);
CREATE INDEX idx_audit_events_action ON audit_events(action);
CREATE INDEX idx_audit_events_outcome ON audit_events(outcome);
CREATE INDEX idx_audit_events_request_id ON audit_events(request_id);

DO $$
DECLARE
    audited_table TEXT;
BEGIN
    FOREACH audited_table IN ARRAY ARRAY[
        'tenants',
        'warehouses',
        'brands',
        'categories',
        'products',
        'product_kits',
        'roles',
        'users',
        'batches',
        'transfers',
        'stock_movements',
        'sales'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS created_by UUID', audited_table);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS updated_by UUID', audited_table);
    END LOOP;
END $$;

WITH audit_permission AS (
    INSERT INTO permissions (id, code, description)
    SELECT (
        substr(md5('permission:audit:read'), 1, 8) || '-' ||
        substr(md5('permission:audit:read'), 9, 4) || '-' ||
        substr(md5('permission:audit:read'), 13, 4) || '-' ||
        substr(md5('permission:audit:read'), 17, 4) || '-' ||
        substr(md5('permission:audit:read'), 21, 12)
    )::uuid,
    'audit:read',
    'audit:read'
    WHERE NOT EXISTS (
        SELECT 1 FROM permissions WHERE code = 'audit:read'
    )
    RETURNING id
),
permission_row AS (
    SELECT id FROM audit_permission
    UNION
    SELECT id FROM permissions WHERE code = 'audit:read'
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permission_row p
WHERE upper(r.name) IN ('ADMIN', 'SUPER_ADMIN')
ON CONFLICT (permission_id, role_id) DO NOTHING;
