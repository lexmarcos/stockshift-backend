-- refresh_tokens is filtered by user on every login, logout, refresh-rotation
-- cleanup, and grace cleanup, but the FK to users has no backing index (Postgres
-- does not auto-index foreign keys). Add one so those user-scoped reads/deletes
-- don't sequentially scan the table as it grows.
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
