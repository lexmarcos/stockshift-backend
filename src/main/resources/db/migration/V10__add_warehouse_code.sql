-- V10: Add code field to warehouses table

-- Add code column (nullable initially)
ALTER TABLE warehouses ADD COLUMN code VARCHAR(20);

-- Generate codes for existing warehouses (WH001, WH002, etc.)
DO $$
DECLARE
    warehouse_record RECORD;
    counter INTEGER := 1;
BEGIN
    FOR warehouse_record IN
        SELECT id FROM warehouses ORDER BY created_at
    LOOP
        UPDATE warehouses
        SET code = 'WH' || LPAD(counter::TEXT, 3, '0')
        WHERE id = warehouse_record.id;
        counter := counter + 1;
    END LOOP;
END $$;

-- Make code NOT NULL after populating
ALTER TABLE warehouses ALTER COLUMN code SET NOT NULL;

-- Add unique constraint on (tenant_id, code)
ALTER TABLE warehouses ADD CONSTRAINT uk_warehouses_tenant_code UNIQUE (tenant_id, code);

-- Add index for faster lookups
CREATE INDEX idx_warehouses_code ON warehouses(code);
