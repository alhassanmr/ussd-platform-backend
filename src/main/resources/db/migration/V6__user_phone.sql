-- V6__user_phone.sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
