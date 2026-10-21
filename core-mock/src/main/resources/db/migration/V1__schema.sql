-- core-mock schema. Money is always NUMERIC(19,4) — never floating point.
-- All timestamps are timestamptz (UTC instants).

CREATE TABLE core_account (
    id             uuid PRIMARY KEY,
    account_number varchar(20) NOT NULL UNIQUE,
    company_id     uuid NOT NULL,
    currency       char(3) NOT NULL,
    product        varchar(32),
    balance        numeric(19, 4) NOT NULL CHECK (balance >= 0),
    created_at     timestamptz NOT NULL
);

CREATE TABLE core_posting (
    id             uuid PRIMARY KEY,
    core_ref       varchar(40) NOT NULL UNIQUE,
    request_ref    varchar(40) NOT NULL,
    account_number varchar(20) NOT NULL,
    posting_type   varchar(20) NOT NULL
                   CHECK (posting_type IN ('DEBIT', 'CREDIT', 'REVERSAL_CREDIT', 'REVERSAL_DEBIT')),
    amount         numeric(19, 4) NOT NULL,
    currency       char(3),
    counterparty   varchar(120),
    response_code  varchar(4),
    status         varchar(20),
    posted_at      timestamptz
);

CREATE INDEX idx_core_posting_request_ref ON core_posting (request_ref);

CREATE TABLE payment_status (
    core_ref  varchar(40) PRIMARY KEY,
    status    varchar(20),
    amount    numeric(19, 4),
    currency  char(3),
    posted_at timestamptz
);

CREATE TABLE forced_failure (
    request_ref        varchar(40) PRIMARY KEY,
    attempts_remaining int NOT NULL,
    note               varchar(200)
);
