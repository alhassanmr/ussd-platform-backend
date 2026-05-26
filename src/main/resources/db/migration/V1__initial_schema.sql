-- V1__initial_schema.sql
-- USSD as a Service — Full Database Schema

-- ============================================================
-- TENANTS (Companies using the platform)
-- ============================================================
CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,   -- used in API keys & routing
    email       VARCHAR(255) NOT NULL UNIQUE,
    phone       VARCHAR(20),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, TRIAL
    plan        VARCHAR(20) NOT NULL DEFAULT 'FREE',   -- FREE, BASIC, PRO, ENTERPRISE
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- USERS (Belong to a tenant)
-- ============================================================
CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    full_name    VARCHAR(255) NOT NULL,
    role         VARCHAR(20) NOT NULL DEFAULT 'MEMBER', -- OWNER, ADMIN, MEMBER
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- USSD APPS (Each tenant can have multiple USSD apps)
-- ============================================================
CREATE TABLE ussd_apps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    short_code      VARCHAR(20),          -- e.g. *714#
    gateway_type    VARCHAR(50) NOT NULL, -- AFRICASTALKING, HUBTEL, CUSTOM
    gateway_config  JSONB,                -- encrypted gateway credentials per app
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT, ACTIVE, PAUSED
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- MENUS (Each app has a tree of menus)
-- ============================================================
CREATE TABLE menus (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_id          UUID NOT NULL REFERENCES ussd_apps(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    is_root         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- MENU ITEMS (Each menu has ordered items / options)
-- ============================================================
CREATE TABLE menu_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_id         UUID NOT NULL REFERENCES menus(id) ON DELETE CASCADE,
    parent_id       UUID REFERENCES menu_items(id) ON DELETE CASCADE,
    display_order   INT NOT NULL DEFAULT 0,
    item_type       VARCHAR(30) NOT NULL, -- DISPLAY, INPUT, ROUTER, WEBHOOK, END
    label           TEXT NOT NULL,        -- text shown to user
    input_prompt    TEXT,                 -- used for INPUT type
    variable_name   VARCHAR(100),         -- stores user input into this variable
    next_menu_id    UUID REFERENCES menus(id),
    webhook_url     TEXT,                 -- called on WEBHOOK type
    webhook_method  VARCHAR(10) DEFAULT 'POST',
    end_message     TEXT,                 -- shown on END type
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- SESSIONS (Active USSD sessions — also cached in Redis)
-- ============================================================
CREATE TABLE ussd_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      VARCHAR(255) NOT NULL UNIQUE,  -- gateway session ID
    app_id          UUID NOT NULL REFERENCES ussd_apps(id),
    msisdn          VARCHAR(20) NOT NULL,           -- caller phone number
    current_menu_id UUID REFERENCES menus(id),
    current_item_id UUID REFERENCES menu_items(id),
    session_data    JSONB DEFAULT '{}',             -- collected variables
    status          VARCHAR(20) DEFAULT 'ACTIVE',   -- ACTIVE, COMPLETED, TIMEOUT
    started_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    last_activity   TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMP
);

-- ============================================================
-- SESSION LOGS (Full interaction log per session)
-- ============================================================
CREATE TABLE session_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID NOT NULL REFERENCES ussd_sessions(id) ON DELETE CASCADE,
    direction       VARCHAR(10) NOT NULL, -- IN (from user), OUT (to user)
    message         TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- API KEYS (For gateway-to-platform callbacks)
-- ============================================================
CREATE TABLE api_keys (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    app_id      UUID REFERENCES ussd_apps(id) ON DELETE CASCADE,
    key_hash    VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(100),
    last_used   TIMESTAMP,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- INDEXES
-- ============================================================
CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_ussd_apps_tenant ON ussd_apps(tenant_id);
CREATE INDEX idx_menus_app ON menus(app_id);
CREATE INDEX idx_menu_items_menu ON menu_items(menu_id);
CREATE INDEX idx_sessions_session_id ON ussd_sessions(session_id);
CREATE INDEX idx_sessions_app ON ussd_sessions(app_id);
CREATE INDEX idx_session_logs_session ON session_logs(session_id);
