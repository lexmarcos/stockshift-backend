-- Create sales table
CREATE TABLE sales (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    customer_id BIGINT,
    customer_name VARCHAR(200),
    payment_method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    subtotal DECIMAL(15, 2) NOT NULL,
    discount DECIMAL(15, 2) DEFAULT 0,
    total DECIMAL(15, 2) NOT NULL,
    notes TEXT,
    stock_movement_id BIGINT,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancelled_by BIGINT,
    cancellation_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sales_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_sales_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_sales_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_sales_stock_movement FOREIGN KEY (stock_movement_id) REFERENCES stock_movements(id),
    CONSTRAINT fk_sales_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users(id)
);

-- Create sale_items table
CREATE TABLE sale_items (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_id BIGINT,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(15, 2) NOT NULL,
    subtotal DECIMAL(15, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales(id),
    CONSTRAINT fk_sale_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_sale_items_batch FOREIGN KEY (batch_id) REFERENCES batches(id)
);

-- Create indexes for performance
CREATE INDEX idx_sales_tenant_created ON sales(tenant_id, created_at DESC);
CREATE INDEX idx_sales_tenant_status ON sales(tenant_id, status);
CREATE INDEX idx_sales_warehouse ON sales(warehouse_id);
CREATE INDEX idx_sales_user ON sales(user_id);
CREATE INDEX idx_sale_items_sale ON sale_items(sale_id);
CREATE INDEX idx_sale_items_product ON sale_items(product_id);

-- Add comments
COMMENT ON TABLE sales IS 'Vendas realizadas';
COMMENT ON TABLE sale_items IS 'Itens das vendas';
COMMENT ON COLUMN sales.payment_method IS 'Método de pagamento: CASH, DEBIT_CARD, CREDIT_CARD, INSTALLMENT, PIX, BANK_TRANSFER, OTHER';
COMMENT ON COLUMN sales.status IS 'Status da venda: COMPLETED, CANCELLED';
