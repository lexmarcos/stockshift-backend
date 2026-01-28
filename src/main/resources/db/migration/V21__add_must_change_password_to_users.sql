-- Add must_change_password column to users table
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN users.must_change_password IS 'Indicates if user must change password on next login';
