-- Add PENDING status to sales
ALTER TABLE sales DROP CONSTRAINT chk_sale_status;
ALTER TABLE sales ADD CONSTRAINT chk_sale_status
    CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'));

-- Add InfinitePay columns to tenants
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS infinitepay_handle VARCHAR(100);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS infinitepay_doc_number VARCHAR(20);

-- Add InfinitePay transaction data to sales
ALTER TABLE sales ADD COLUMN IF NOT EXISTS infinitepay_nsu VARCHAR(100);
ALTER TABLE sales ADD COLUMN IF NOT EXISTS infinitepay_aut VARCHAR(50);
ALTER TABLE sales ADD COLUMN IF NOT EXISTS infinitepay_card_brand VARCHAR(50);
