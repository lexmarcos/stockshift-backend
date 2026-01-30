-- Add Transfer-specific permissions
INSERT INTO permissions (id, description, resource, action, scope)
SELECT
    uuid_generate_v4(),
    'Create transfers from source warehouse',
    'TRANSFER',
    'CREATE',
    'TENANT'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE resource = 'TRANSFER' AND action = 'CREATE' AND scope = 'TENANT');

INSERT INTO permissions (id, description, resource, action, scope)
SELECT
    uuid_generate_v4(),
    'Update transfers in DRAFT status',
    'TRANSFER',
    'UPDATE',
    'TENANT'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE resource = 'TRANSFER' AND action = 'UPDATE' AND scope = 'TENANT');

INSERT INTO permissions (id, description, resource, action, scope)
SELECT
    uuid_generate_v4(),
    'Cancel transfers in DRAFT status',
    'TRANSFER',
    'DELETE',
    'TENANT'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE resource = 'TRANSFER' AND action = 'DELETE' AND scope = 'TENANT');

INSERT INTO permissions (id, description, resource, action, scope)
SELECT
    uuid_generate_v4(),
    'Dispatch transfers from source warehouse',
    'TRANSFER',
    'EXECUTE',
    'TENANT'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE resource = 'TRANSFER' AND action = 'EXECUTE' AND scope = 'TENANT');

INSERT INTO permissions (id, description, resource, action, scope)
SELECT
    uuid_generate_v4(),
    'Validate and receive transfers at destination warehouse',
    'TRANSFER',
    'VALIDATE',
    'TENANT'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE resource = 'TRANSFER' AND action = 'VALIDATE' AND scope = 'TENANT');

INSERT INTO permissions (id, description, resource, action, scope)
SELECT
    uuid_generate_v4(),
    'View transfers',
    'TRANSFER',
    'READ',
    'TENANT'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE resource = 'TRANSFER' AND action = 'READ' AND scope = 'TENANT');

INSERT INTO permissions (id, description, resource, action, scope)
SELECT
    uuid_generate_v4(),
    'Resolve transfer discrepancies',
    'TRANSFER',
    'RESOLVE',
    'TENANT'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE resource = 'TRANSFER' AND action = 'RESOLVE' AND scope = 'TENANT');
