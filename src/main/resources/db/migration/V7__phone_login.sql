-- V7__phone_login.sql
-- Index phone for fast lookup during phone-based login
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
