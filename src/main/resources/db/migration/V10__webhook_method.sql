-- V10__webhook_method.sql
-- Add webhook request method to ussd_apps
ALTER TABLE ussd_apps ADD COLUMN IF NOT EXISTS webhook_method VARCHAR(10) NOT NULL DEFAULT 'POST';
