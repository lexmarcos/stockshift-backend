-- Add sales permissions
INSERT INTO permissions (resource, action, scope, description)
VALUES
    ('SALES', 'CREATE', 'OWNED', 'Create sales'),
    ('SALES', 'READ', 'OWNED', 'View sales'),
    ('SALES', 'CANCEL', 'OWNED', 'Cancel sales');

-- Grant sales permissions to existing roles (adjust role IDs as needed)
-- This is an example - adjust based on your role structure
