-- Refresh token rotation grace window.
-- `rotated_at` marks when a token was replaced by a newer one. A rotated token
-- stays valid for a short grace period (jwt.refresh-rotation-grace-period) so
-- concurrent refresh requests sharing the same cookie don't invalidate each
-- other and force a logout. NULL means the token is the current/active one.
ALTER TABLE refresh_tokens ADD COLUMN rotated_at TIMESTAMP(6);

-- Speeds up the grace-window cleanup (deleteRotatedBefore).
CREATE INDEX idx_refresh_tokens_rotated_at ON refresh_tokens (rotated_at) WHERE rotated_at IS NOT NULL;
