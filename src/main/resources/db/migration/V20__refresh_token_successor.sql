-- Tracked successor for refresh-token rotation.
-- `replaced_by_id` points to the single token that replaced this one when it was
-- rotated. A rotated token presented again within its grace window returns this
-- tracked successor instead of minting a brand-new long-lived token, so a
-- replayed/stolen pre-rotation cookie can't bootstrap a fresh session. NULL means
-- the token has not been rotated yet. No FK constraint on purpose: rotated tokens
-- are pruned independently of their successors.
ALTER TABLE refresh_tokens ADD COLUMN replaced_by_id UUID;
