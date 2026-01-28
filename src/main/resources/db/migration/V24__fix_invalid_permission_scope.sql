-- Fix invalid scope value OWNED -> OWN
UPDATE permissions SET scope = 'OWN' WHERE scope = 'OWNED';

-- Delete permissions with invalid action values
DELETE FROM role_permissions WHERE permission_id IN (
    SELECT id FROM permissions WHERE action NOT IN ('CREATE', 'READ', 'UPDATE', 'DELETE', 'APPROVE')
);
DELETE FROM permissions WHERE action NOT IN ('CREATE', 'READ', 'UPDATE', 'DELETE', 'APPROVE');

-- Delete permissions with invalid scope values
DELETE FROM role_permissions WHERE permission_id IN (
    SELECT id FROM permissions WHERE scope NOT IN ('ALL', 'OWN_WAREHOUSE', 'OWN')
);
DELETE FROM permissions WHERE scope NOT IN ('ALL', 'OWN_WAREHOUSE', 'OWN');
