-- V5__team_invites.sql

-- ============================================================
-- TEAM INVITATIONS
-- ============================================================
CREATE TABLE team_invites (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invited_by  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invite_token ON team_invites(token);
CREATE INDEX idx_invite_tenant ON team_invites(tenant_id);
CREATE INDEX idx_invite_email ON team_invites(email);
