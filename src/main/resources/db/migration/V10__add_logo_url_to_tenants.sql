ALTER TABLE tenants ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500);

COMMENT ON COLUMN tenants.logo_url IS 'Public URL of the company logo stored in R2';
