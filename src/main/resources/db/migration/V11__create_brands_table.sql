-- Brands table
CREATE TABLE brands (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    UNIQUE(tenant_id, name)
);

-- Indexes
CREATE INDEX idx_brands_tenant ON brands(tenant_id);
CREATE INDEX idx_brands_deleted_at ON brands(deleted_at) WHERE deleted_at IS NOT NULL;

-- Update trigger
CREATE TRIGGER update_brands_updated_at BEFORE UPDATE ON brands
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON TABLE brands IS 'Product brands with multi-tenancy support';
COMMENT ON COLUMN brands.name IS 'Brand name (unique per tenant)';
COMMENT ON COLUMN brands.logo_url IS 'Optional URL for brand logo';
COMMENT ON COLUMN brands.deleted_at IS 'Soft delete timestamp';
