-- payment-api schema. Money is NUMERIC(19,4) — fixed precision, never floating point.

CREATE TABLE company (
    id UUID PRIMARY KEY,
    legal_name VARCHAR(200) NOT NULL,
    registration_number VARCHAR(40) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    base_currency CHAR(3) NOT NULL,
    daily_limit NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE company_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    approval_limit NUMERIC(19,4) NOT NULL DEFAULT 0,
    company_id UUID REFERENCES company(id), -- null for bank-wide roles
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_company_user_email UNIQUE (email)
);
CREATE INDEX ix_company_user_company ON company_user(company_id);

CREATE TABLE account (
    id UUID PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL,
    company_id UUID NOT NULL REFERENCES company(id),
    currency CHAR(3) NOT NULL,
    product VARCHAR(32) NOT NULL,
    name VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_account_number UNIQUE (account_number)
);
CREATE INDEX ix_account_company ON account(company_id);

CREATE TABLE company_user_account (
    user_id UUID NOT NULL REFERENCES company_user(id),
    account_id UUID NOT NULL REFERENCES account(id),
    PRIMARY KEY (user_id, account_id)
);

CREATE TABLE beneficiary (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    name VARCHAR(200) NOT NULL,
    account_number VARCHAR(34) NOT NULL,
    bank_code VARCHAR(34) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    cooling_until TIMESTAMPTZ,
    created_by UUID REFERENCES company_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    last_amended_at TIMESTAMPTZ,
    last_amended_by UUID REFERENCES company_user(id),
    CONSTRAINT uq_beneficiary_account UNIQUE (company_id, account_number, bank_code)
);
CREATE INDEX ix_beneficiary_company ON beneficiary(company_id);

CREATE TABLE fee_schedule (
    id UUID PRIMARY KEY,
    product_type VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    flat_fee NUMERIC(19,4) NOT NULL,
    pct_fee NUMERIC(19,4) NOT NULL,
    min_fee NUMERIC(19,4) NOT NULL,
    max_fee NUMERIC(19,4) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL
);

CREATE TABLE fx_rate (
    id UUID PRIMARY KEY,
    currency_pair VARCHAR(7) NOT NULL,
    rate NUMERIC(19,8) NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_fx_rate_pair ON fx_rate(currency_pair, effective_at);

CREATE TABLE payment (
    id UUID PRIMARY KEY,
    ref VARCHAR(40) NOT NULL,
    company_id UUID NOT NULL REFERENCES company(id),
    debit_account_id UUID NOT NULL REFERENCES account(id),
    beneficiary_id UUID NOT NULL REFERENCES beneficiary(id),
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    fee NUMERIC(19,4) NOT NULL,
    fx_rate NUMERIC(19,8) NOT NULL,
    settlement_amount NUMERIC(19,4) NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    value_date DATE NOT NULL,
    reference VARCHAR(140) NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(64),
    created_by UUID REFERENCES company_user(id),
    amended_by UUID REFERENCES company_user(id),
    bulk_file_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_ref UNIQUE (ref)
);
CREATE INDEX ix_payment_company_status ON payment(company_id, status);
CREATE INDEX ix_payment_status_value_date ON payment(status, value_date);
CREATE INDEX ix_payment_company_created ON payment(company_id, created_at);

CREATE TABLE payment_amendment (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    actor_id UUID NOT NULL REFERENCES company_user(id),
    changed_fields JSONB NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_amendment_payment ON payment_amendment(payment_id);

CREATE TABLE approval_action (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    actor_id UUID NOT NULL REFERENCES company_user(id),
    decision VARCHAR(16) NOT NULL,
    comment VARCHAR(500),
    limit_applied NUMERIC(19,4) NOT NULL,
    half_required BOOLEAN NOT NULL,
    acted_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_approval_payment ON approval_action(payment_id);

CREATE TABLE payment_comment (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    author_id UUID NOT NULL REFERENCES company_user(id),
    text VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_comment_payment ON payment_comment(payment_id);

CREATE TABLE bulk_file (
    id UUID PRIMARY KEY,
    ref VARCHAR(40) NOT NULL,
    company_id UUID NOT NULL REFERENCES company(id),
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    declared_total NUMERIC(19,4) NOT NULL,
    declared_count INT NOT NULL,
    parsed_count INT NOT NULL DEFAULT 0,
    valid_count INT NOT NULL DEFAULT 0,
    invalid_count INT NOT NULL DEFAULT 0,
    uploaded_by UUID REFERENCES company_user(id),
    uploaded_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_bulk_file_ref UNIQUE (ref)
);
CREATE INDEX ix_bulk_file_company ON bulk_file(company_id);

CREATE TABLE bulk_file_line (
    id UUID PRIMARY KEY,
    bulk_file_id UUID NOT NULL REFERENCES bulk_file(id),
    line_number INT NOT NULL,
    raw_content VARCHAR(1000) NOT NULL,
    debit_account_number VARCHAR(20),
    beneficiary_account_number VARCHAR(34),
    bank_code VARCHAR(34),
    amount NUMERIC(19,4),
    value_date DATE,
    reference VARCHAR(140),
    status VARCHAR(16),
    error_message VARCHAR(300),
    payment_id UUID,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_bulk_line_file ON bulk_file_line(bulk_file_id, line_number);

CREATE TABLE release_record (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    core_ref VARCHAR(40),
    posted_amount NUMERIC(19,4) NOT NULL,
    response_code VARCHAR(4),
    reason VARCHAR(64),
    retry_count INT NOT NULL DEFAULT 0,
    investigation_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    investigation_notes VARCHAR(1000),
    attempted_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    CONSTRAINT uq_release_payment UNIQUE (payment_id)
);

CREATE TABLE reversal (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_by UUID NOT NULL REFERENCES company_user(id),
    requested_at TIMESTAMPTZ NOT NULL,
    approved_by UUID REFERENCES company_user(id),
    decided_at TIMESTAMPTZ,
    core_ref VARCHAR(40),
    posted_at TIMESTAMPTZ
);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    event_at TIMESTAMPTZ NOT NULL,
    actor_type VARCHAR(8) NOT NULL,
    actor_id UUID,
    actor_name VARCHAR(200),
    actor_role VARCHAR(20),
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id UUID,
    resource_ref VARCHAR(40),
    company_id UUID,
    summary VARCHAR(500),
    before_state JSONB,
    after_state JSONB,
    auth_basis VARCHAR(500),
    request_id VARCHAR(64)
);
CREATE INDEX ix_audit_resource ON audit_event(resource_type, resource_id);
CREATE INDEX ix_audit_event_at ON audit_event(event_at);
CREATE INDEX ix_audit_company ON audit_event(company_id);

CREATE TABLE notification (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES company_user(id),
    type VARCHAR(32) NOT NULL,
    message VARCHAR(500) NOT NULL,
    link VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ
);
CREATE INDEX ix_notification_user ON notification(user_id, read_at);

CREATE TABLE webhook_config (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company(id),
    url VARCHAR(500) NOT NULL,
    secret VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_webhook_company UNIQUE (company_id)
);

CREATE TABLE webhook_delivery (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    company_id UUID NOT NULL REFERENCES company(id),
    url VARCHAR(500) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    response_code VARCHAR(8),
    created_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ
);
