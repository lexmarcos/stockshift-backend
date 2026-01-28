-- Create user_warehouses join table
CREATE TABLE user_warehouses (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, warehouse_id)
);

-- Create indexes for performance
CREATE INDEX idx_user_warehouses_user ON user_warehouses(user_id);
CREATE INDEX idx_user_warehouses_warehouse ON user_warehouses(warehouse_id);

COMMENT ON TABLE user_warehouses IS 'Association between users and warehouses they have access to';
