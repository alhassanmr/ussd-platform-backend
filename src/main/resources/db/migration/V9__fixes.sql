-- V9__fixes.sql

-- Fix ussd_sessions.app_id missing ON DELETE CASCADE
-- Drop and recreate the FK with CASCADE
ALTER TABLE ussd_sessions DROP CONSTRAINT IF EXISTS ussd_sessions_app_id_fkey;
ALTER TABLE ussd_sessions ADD CONSTRAINT ussd_sessions_app_id_fkey
    FOREIGN KEY (app_id) REFERENCES ussd_apps(id) ON DELETE CASCADE;

-- Fix ussd_sessions current_menu_id and current_item_id
-- When menu/item is deleted, set to NULL (not cascade)
ALTER TABLE ussd_sessions DROP CONSTRAINT IF EXISTS ussd_sessions_current_menu_id_fkey;
ALTER TABLE ussd_sessions ADD CONSTRAINT ussd_sessions_current_menu_id_fkey
    FOREIGN KEY (current_menu_id) REFERENCES menus(id) ON DELETE SET NULL;

ALTER TABLE ussd_sessions DROP CONSTRAINT IF EXISTS ussd_sessions_current_item_id_fkey;
ALTER TABLE ussd_sessions ADD CONSTRAINT ussd_sessions_current_item_id_fkey
    FOREIGN KEY (current_item_id) REFERENCES menu_items(id) ON DELETE SET NULL;

-- Add missing indexes for performance
CREATE INDEX IF NOT EXISTS idx_ussd_sessions_tenant
    ON ussd_sessions(app_id);

CREATE INDEX IF NOT EXISTS idx_users_status
    ON users(status);

CREATE INDEX IF NOT EXISTS idx_tenants_status
    ON tenants(status);

CREATE INDEX IF NOT EXISTS idx_otp_codes_user
    ON otp_codes(user_id);

-- Ensure password_reset_tokens has proper index
CREATE INDEX IF NOT EXISTS idx_pwd_reset_user
    ON password_reset_tokens(user_id);
