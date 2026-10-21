# core-mock — vendor core-banking stub

`core-mock` is the **vendor-core stub tier** of the Corporate Payments Portal.
It behaves like an external core banking system: its own network endpoint
(port `8082` by default), its own credential, and its own error codes.
It exposes balance lookups, debit/credit postings, payment status and a
bank-only internal surface for reconciliation and account creation.

The other tiers treat this module as an external dependency. It verifies —
never mints — the short-lived scope tokens signed by `payment-api`, and it
refuses any operation for an account outside the caller's signed scope.

## Trust model

- `portal` authenticates, `payment-api` authorizes, `core-mock` enforces the
  scope the decision tier minted.
- Every business endpoint requires **two** headers:
  - `X-Core-Api-Key` — core-mock's own credential (env only, constant-time
    comparison).
  - `X-Scope-Token` — JWT-like `base64url(header).base64url(payload).base64url(sig)`,
    HMAC-SHA256 over the first two segments with `CORE_SCOPE_SECRET`.
- Account numbers and company ids carried in bodies/paths/query params are
  **never trusted for authorization**. They are checked against the verified
  token scope only.

## Environment

| Variable | Required | Notes |
|---|---|---|
| `CORE_MOCK_API_KEY` | **yes** | Core-banking credential. No default: startup fails fast when blank. |
| `CORE_SCOPE_SECRET` | **yes** | Shared HMAC secret; must match the value used by `payment-api`. |
| `SERVER_PORT` | no | Default `8082`. |
| `SPRING_DATASOURCE_URL` | no | Default `jdbc:postgresql://localhost:5432/core_mock`. |
| `SPRING_DATASOURCE_USERNAME` | no | Default `core_mock`. |
| `SPRING_DATASOURCE_PASSWORD` | no | Default `coremock_dev_password` — a **documented local-development value only**; override in every real environment. |

Credential defaults are deliberately absent so a misconfigured deployment
fails closed instead of running with a guessable secret.

## API

All responses are JSON. Errors use one envelope:
`{"error":{"code":"...","message":"..."}}`.

| Endpoint | Scope | Description |
|---|---|---|
| `GET /v1/accounts/{accountNumber}/balance` | company scope incl. the account, or bank | `{accountNumber, currency, balance, companyId}`. `404 ACCOUNT_NOT_FOUND`, `403 CORE_SCOPE_DENIED`. |
| `POST /v1/postings` | company scope incl. the body's account, or bank | Body `{requestRef, accountNumber, postingType, amount, currency, counterparty}`. Idempotent per `requestRef`; forced failures decline with `91`; insufficient funds with `51`. Success: `{coreRef, status:"POSTED", responseCode:"00", balance}`. |
| `GET /v1/payment-status/{coreRef}` | any valid scope | `{coreRef, status, amount, currency, postedAt}` or `404 CORE_RECORD_NOT_FOUND`. |
| `GET /internal/postings?accountNumber=&since=` | bank only | Posting list for reconciliation; `since` is an optional ISO-8601 instant. |
| `POST /internal/accounts` | bank only | Create `{companyId, accountNumber, currency, product, openingBalance}`; `409 ACCOUNT_EXISTS` on duplicates. |

Error codes: `CORE_AUTH_FAILED` (401), `CORE_SCOPE_DENIED` (403),
`ACCOUNT_NOT_FOUND` / `CORE_RECORD_NOT_FOUND` (404), `VALIDATION` (400),
`ACCOUNT_EXISTS` (409), `INTERNAL_ERROR` (500).

`/actuator/health` is intentionally reachable without credentials for
orchestration probes; all other actuator endpoints are disabled.

## Running

```bash
# from the repository root
CORE_MOCK_API_KEY=<generated-key> \
CORE_SCOPE_SECRET=<shared-secret> \
./gradlew :core-mock:bootRun
```

The database schema is managed by Flyway (`classpath:db/migration`); the demo
seed (12 accounts across 4 companies, 16 released postings, 2 forced-failure
records) runs idempotently on first startup when `core_account` is empty.
`docker compose up` at the repository root starts Postgres and this service
with credentials from `.env`.

## Tests

```bash
./gradlew :core-mock:test
```

Tests run on embedded H2 (`MODE=PostgreSQL`, Flyway disabled,
Hibernate `create-drop`) and cover the token verifier (round-trip, tampering,
expiry, skew, malformed), the posting service (insufficient funds, forced
failure then success, idempotency, debit/credit) and the HTTP security layer
(401s, scope 403s, happy path).
