-- Insert default permissions
INSERT INTO permissions (resource, action, scope, description) VALUES
-- Product permissions
('PRODUCT', 'CREATE', 'ALL', 'Create products'),
('PRODUCT', 'READ', 'ALL', 'View all products'),
('PRODUCT', 'UPDATE', 'ALL', 'Update products'),
('PRODUCT', 'DELETE', 'ALL', 'Delete products'),

-- Stock permissions
('STOCK', 'CREATE', 'ALL', 'Create stock movements'),
('STOCK', 'READ', 'ALL', 'View stock'),
('STOCK', 'UPDATE', 'ALL', 'Update stock'),
('STOCK', 'APPROVE', 'ALL', 'Approve stock transfers'),
('STOCK', 'APPROVE', 'OWN_WAREHOUSE', 'Approve transfers for own warehouse'),

-- Sale permissions
('SALE', 'CREATE', 'ALL', 'Create sales'),
('SALE', 'READ', 'ALL', 'View sales'),

-- User permissions
('USER', 'CREATE', 'ALL', 'Create users'),
('USER', 'READ', 'ALL', 'View users'),
('USER', 'UPDATE', 'ALL', 'Update users'),
('USER', 'DELETE', 'ALL', 'Delete users'),

-- Report permissions
('REPORT', 'READ', 'ALL', 'View all reports'),

-- Warehouse permissions
('WAREHOUSE', 'CREATE', 'ALL', 'Create warehouses'),
('WAREHOUSE', 'READ', 'ALL', 'View warehouses'),
('WAREHOUSE', 'UPDATE', 'ALL', 'Update warehouses'),
('WAREHOUSE', 'DELETE', 'ALL', 'Delete warehouses');
