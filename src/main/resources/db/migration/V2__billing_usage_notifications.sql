-- V2__billing_usage_notifications.sql

-- ============================================================
-- SUBSCRIPTION PLANS (configurable by admin)
-- ============================================================
CREATE TABLE plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(50) NOT NULL UNIQUE,  -- FREE, BASIC, PRO, ENTERPRISE
    display_name    VARCHAR(100) NOT NULL,
    price_ghs       DECIMAL(10,2) NOT NULL DEFAULT 0,
    max_apps        INT NOT NULL DEFAULT 1,
    max_sessions    INT NOT NULL DEFAULT 500,      -- per month; -1 = unlimited
    extra_session_fee DECIMAL(10,4) DEFAULT 0,     -- GHS per extra session
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO plans (name, display_name, price_ghs, max_apps, max_sessions, extra_session_fee) VALUES
  ('FREE',       'Free',       0,      1,  500,    0),
  ('BASIC',      'Basic',      150,    3,  5000,   0.05),
  ('PRO',        'Pro',        400,    10, 50000,  0.03),
  ('ENTERPRISE', 'Enterprise', 1500,  -1, -1,      0);

-- ============================================================
-- SUBSCRIPTIONS (one active subscription per tenant)
-- ============================================================
CREATE TABLE subscriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan_id             UUID NOT NULL REFERENCES plans(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, PAST_DUE, CANCELLED, TRIAL
    paystack_customer_id VARCHAR(100),
    paystack_sub_code   VARCHAR(100),           -- Paystack subscription code
    current_period_start TIMESTAMP NOT NULL DEFAULT NOW(),
    current_period_end  TIMESTAMP NOT NULL DEFAULT NOW() + INTERVAL '30 days',
    cancelled_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- USAGE RECORDS (monthly session counts per tenant)
-- ============================================================
CREATE TABLE usage_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    app_id          UUID REFERENCES ussd_apps(id) ON DELETE SET NULL,
    period_year     INT NOT NULL,
    period_month    INT NOT NULL,               -- 1-12
    session_count   INT NOT NULL DEFAULT 0,
    extra_sessions  INT NOT NULL DEFAULT 0,     -- sessions above plan limit
    extra_charges   DECIMAL(10,2) DEFAULT 0,    -- GHS charged for extras
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, app_id, period_year, period_month)
);

-- ============================================================
-- INVOICES
-- ============================================================
CREATE TABLE invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES subscriptions(id),
    invoice_number  VARCHAR(50) NOT NULL UNIQUE,
    amount_ghs      DECIMAL(10,2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, PAID, FAILED, VOID
    paystack_ref    VARCHAR(100),
    paystack_txn_id VARCHAR(100),
    due_date        TIMESTAMP,
    paid_at         TIMESTAMP,
    period_start    TIMESTAMP,
    period_end      TIMESTAMP,
    line_items      JSONB DEFAULT '[]',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- EMAIL NOTIFICATIONS LOG
-- ============================================================
CREATE TABLE notification_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID REFERENCES tenants(id) ON DELETE SET NULL,
    recipient_email VARCHAR(255) NOT NULL,
    type            VARCHAR(50) NOT NULL, -- WELCOME, USAGE_WARNING, INVOICE, PAYMENT_FAILED, SUSPENSION
    subject         VARCHAR(255),
    status          VARCHAR(20) DEFAULT 'SENT', -- SENT, FAILED
    error_message   TEXT,
    sent_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ADMIN USERS (platform superadmins)
-- ============================================================
CREATE TABLE admin_users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255) NOT NULL,
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- INDEXES
-- ============================================================
CREATE INDEX idx_subscriptions_tenant ON subscriptions(tenant_id);
CREATE INDEX idx_usage_tenant_period ON usage_records(tenant_id, period_year, period_month);
CREATE INDEX idx_invoices_tenant ON invoices(tenant_id);
CREATE INDEX idx_notification_logs_tenant ON notification_logs(tenant_id);
