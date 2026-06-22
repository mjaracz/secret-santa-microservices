ALTER TABLE users
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER';

ALTER TABLE users
    ADD CONSTRAINT ck_users_role
        CHECK (role IN ('USER', 'ADMIN'));

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_email_verification_tokens_user_id
    ON email_verification_tokens (user_id);

CREATE INDEX ix_email_verification_tokens_expires_at
    ON email_verification_tokens (expires_at);

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    family_expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    replaced_by_token_hash VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_refresh_sessions_user_id
    ON refresh_sessions (user_id);

CREATE INDEX ix_refresh_sessions_family_id
    ON refresh_sessions (family_id);

CREATE INDEX ix_refresh_sessions_family_expires_at
    ON refresh_sessions (family_expires_at);
