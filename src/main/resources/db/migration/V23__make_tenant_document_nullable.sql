-- Make document column nullable so it can be filled later
ALTER TABLE tenants ALTER COLUMN document DROP NOT NULL;
