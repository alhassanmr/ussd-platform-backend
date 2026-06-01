-- V12: soft delete for USSD apps
ALTER TABLE ussd_apps ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE ussd_apps ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_ussd_apps_deleted ON ussd_apps(deleted_at);
