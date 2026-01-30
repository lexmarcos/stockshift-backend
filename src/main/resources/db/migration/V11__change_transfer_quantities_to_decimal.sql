-- V11: Change transfer_items quantities from INTEGER to NUMERIC(15,3)

-- Change expected_quantity to NUMERIC
ALTER TABLE transfer_items ALTER COLUMN expected_quantity TYPE NUMERIC(15,3);

-- Change received_quantity to NUMERIC
ALTER TABLE transfer_items ALTER COLUMN received_quantity TYPE NUMERIC(15,3);
