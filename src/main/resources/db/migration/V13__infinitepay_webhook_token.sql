ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS infinitepay_webhook_token_hash VARCHAR(64);
