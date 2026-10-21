-- Portal (channel tier) schema. Authentication data lives here; every authorization
-- decision of record is made by the payment-api decision tier from its own database.

CREATE TABLE portal_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100),
    full_name VARCHAR(200) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    company_id UUID,
    company_name VARCHAR(200),
    approval_limit NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ
);

CREATE TABLE portal_user_account (
    user_id UUID NOT NULL REFERENCES portal_user(id),
    account_id UUID NOT NULL,
    PRIMARY KEY (user_id, account_id)
);

CREATE TABLE invite_token (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES portal_user(id),
    email VARCHAR(254) NOT NULL,
    company_id UUID,
    company_name VARCHAR(200),
    role VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE password_reset_token (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES portal_user(id),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE document (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    uploader_id UUID,
    category VARCHAR(40) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(80) NOT NULL UNIQUE,
    mime_type VARCHAR(120),
    size_bytes BIGINT NOT NULL,
    payment_id UUID,
    bulk_file_id UUID,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_document_company ON document(company_id);

CREATE TABLE api_key (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    key_prefix VARCHAR(24) NOT NULL,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ
);
CREATE INDEX ix_api_key_company ON api_key(company_id);

CREATE TABLE email_outbox (
    id UUID PRIMARY KEY,
    to_email VARCHAR(254) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE portal_audit_event (
    id UUID PRIMARY KEY,
    event_at TIMESTAMPTZ NOT NULL,
    actor_id UUID,
    actor_email VARCHAR(254),
    actor_role VARCHAR(20),
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_ref VARCHAR(120),
    company_id UUID,
    summary VARCHAR(500),
    ip_address VARCHAR(64)
);
CREATE INDEX ix_portal_audit_time ON portal_audit_event(event_at);
CREATE INDEX ix_portal_audit_company ON portal_audit_event(company_id);
