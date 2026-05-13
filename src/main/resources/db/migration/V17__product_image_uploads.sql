CREATE TABLE product_image_uploads (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    uploaded_by_user_id UUID NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    public_url VARCHAR(500) NOT NULL,
    original_name VARCHAR(255),
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_product_image_uploads_tenant_user_status
    ON product_image_uploads (tenant_id, uploaded_by_user_id, status);

CREATE INDEX idx_product_image_uploads_status_expires_at
    ON product_image_uploads (status, expires_at);
