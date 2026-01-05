-- Add image_url column to products table
ALTER TABLE products
ADD COLUMN image_url VARCHAR(500);

-- Add index for faster lookups
CREATE INDEX idx_products_image_url ON products(image_url);

-- Add comment
COMMENT ON COLUMN products.image_url IS 'Public URL of the product image stored in Supabase Storage';
