CREATE TABLE product_image_thumbnails (
    product_id UUID NOT NULL,
    size VARCHAR(10) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    public_url VARCHAR(500) NOT NULL,
    width_px INTEGER NOT NULL,
    height_px INTEGER,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL DEFAULT 'image/jpeg',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, size),
    CONSTRAINT fk_thumbnail_product FOREIGN KEY (product_id) REFERENCES products(id)
);
