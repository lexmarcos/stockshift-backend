-- Unify SALES -> SALE (remove duplicate resource)
-- First, delete any SALES permissions (they were already cleaned up by V24 removing invalid actions)
DELETE FROM role_permissions WHERE permission_id IN (
    SELECT id FROM permissions WHERE resource = 'SALES'
);
DELETE FROM permissions WHERE resource = 'SALES';
