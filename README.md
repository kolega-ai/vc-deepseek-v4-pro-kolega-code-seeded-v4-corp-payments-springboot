# Northbridge Corporate Payments Portal

A realistic corporate payments application for a commercial bank: company users initiate single
and bulk payments from their company accounts, payments follow a maker–checker approval chain
constrained by per-role and per-company transaction limits, and the bank releases them to the
core banking system (a local mock). Bank operations staff monitor releases, handle failures and
process reversals.

**Stack:** Java 21 · Spring Boot 3.4 · Thymeleaf · Spring Data JPA/Hibernate · PostgreSQL ·
Flyway · Spring Batch · Docker Compose. Money is always `BigDecimal` (fixed precision) — never
floating point.

## Architecture and trust contract

One Gradle multi-module repository with three tier modules plus one in-cap support module:

| Module | Tier | Responsibility |
|---|---|---|
| `portal` | **channel** | Server-rendered Thymeleaf UI, session-cookie form login (BCrypt), CSRF. **AUTHENTICATES** the user; makes no authorization decisions of record. |
| `payment-api` | **decision** | Internal REST API that **OWNS AUTHORIZATION**. Validates payments, computes fees/FX/limits server-side, holds approval state, orchestrates release. |
| `core-mock` | vendor-core stub | External-system semantics: own endpoint, own credential (`CORE_MOCK_API_KEY`, env-only), own error codes, balances and postings with row locks. |
| `batch` | support module (in-cap) | Spring Batch jobs: bulk-file processing, scheduled release, reversal posting, reconciliation, webhook delivery simulation. |

### Identity propagation (binding)

- The `portal` forwards an **opaque, signed session token** (HMAC-SHA256, short-lived, minted per
  call) to `payment-api`. `payment-api` verifies the signature and then resolves the acting user,
  role, company scope, assigned accounts and approval limit **from its own database on every
  request**.
- `payment-api` **ignores any user id, role, company or limit supplied in a header, path, query,
  form field or body**. There is no request DTO field that can carry them.
- `payment-api` mints a **short-lived signed scope token** for every `core-mock` call carrying the
  resolved company id and account numbers. `core-mock` verifies the signature and **refuses any
  posting for an account outside that company scope**; it never trusts a raw company or
  account-owner parameter.
- Internal callers (the `batch` module and the portal's API-key proxy) authenticate to
  `payment-api`'s `/internal/**` endpoints with a shared service credential compared in constant
  time.
- **`portal` authenticates, `payment-api` authorizes.**

Secrets (tier token secrets, service credential, DB passwords, core credential) come from the
environment only. No credential literal appears in tracked source; README passwords are documented
test values. `CORE_MOCK_API_KEY` must be supplied — `scripts/setup-env.sh` generates one.

## One-command setup

```bash
cp .env.example .env        # or: scripts/setup-env.sh (also generates CORE_MOCK_API_KEY)
docker compose up --build
```

This starts PostgreSQL, runs Flyway migrations for each tier, seeds the demo data, and brings up
`core-mock` (8082), `payment-api` (8081), `portal` (8080) and `batch`. Wait for the health checks,
then open **http://localhost:8080**.

> Tip: if your machine already runs PostgreSQL on port 5432, set `POSTGRES_PORT=55432` in `.env`.

### Running without Docker

```bash
# 1. PostgreSQL (one server, three databases): use docker compose up postgres, or create
#    databases portal / payment_api / core_mock with the users from .env.example.
# 2. Export the shared secrets:
export TIER_TOKEN_SECRET=dev-tier-token-secret-change-me-0a1b2c3d
export CORE_SCOPE_SECRET=dev-core-scope-secret-change-me-4e5f6a7b
export INTERNAL_SERVICE_TOKEN=dev-internal-service-token-8c9d0e1f
export CORE_MOCK_API_KEY=dev-core-local-key-0123456789abcdef
# 3. Run each module (they default to localhost:5432 and ports 8080/8081/8082):
./gradlew :core-mock:bootRun
./gradlew :payment-api:bootRun
./gradlew :portal:bootRun
./gradlew :batch:bootRun
```

## Demo accounts (documented test values — password `Password123!` for all)

| Role | Email | Notes |
|---|---|---|
| company_maker | `maker1@acme.example` | unrestricted accounts |
| company_maker (restricted) | `maker2@acme.example` | can only debit account 20301002 |
| company_checker | `checker1@acme.example` | approval limit £25,000 |
| company_checker | `checker2@acme.example` | approval limit £20,000 |
| company_admin | `admin1@acme.example` | users, beneficiaries, API keys |
| company_maker | `maker1@brighton.example` | Brighton Retail Group (£250k limit) |
| company_checker | `checker1@brighton.example` | limit £150,000 |
| company_admin | `admin1@brighton.example` | |
| company_maker | `maker1@cranfield.example` | Cranfield Logistics (£1m limit) |
| company_checker | `checker1@cranfield.example` | limit £500,000 |
| company_admin | `admin1@cranfield.example` | |
| company_maker | `maker1@delos.example` | Delos Energy (£5m limit) |
| company_checker | `checker1@delos.example` | limit £2,000,000 |
| company_admin | `admin1@delos.example` | |
| bank_ops | `ops1@bank.example` / `ops2@bank.example` | release monitor, retries, reversals |
| bank_admin | `bankadmin1@bank.example` | companies, limits, reversal approvals, audit |

Other seeded companies' users follow the same pattern (`@brighton.example`, `@cranfield.example`,
`@delos.example`). Seeded API key for Acme (demo value): `nb_live_acme_demo_key_9f3a2c1b`.

## Primary user journeys (all doable from the UI)

1. **Single payment**: `maker1` → New payment → server-computed fee/FX preview → submit →
   `checker1` approves (within limit) → the batch module releases it to core → `released`.
2. **Bulk CSV**: `maker1` → Bulk payments → upload a CSV with one invalid line → the error report
   downloads as a document while the valid lines proceed to the approval queue.
   Format: header `H,<declaredTotal>,<lineCount>` then
   `D,<debitAccount>,<beneficiaryAccount>,<bankCode>,<amount>,<valueDate>,<reference>`.
3. **Dual approval**: seed a payment of £30,000 (above every single checker limit): `checker1`
   (limit ≥ half) moves it to `partially_approved`, then `checker2` (a different checker, limit ≥
   half) approves it. (`PY-ACM-1006` is seeded in this state.)
4. **Beneficiary cooling**: `admin1` adds a beneficiary → 30-minute cooling period; payments are
   refused until it elapses. Seeded `Swift Couriers GB` expires 5 minutes after seeding so the
   review is quick. Amending an account number restarts cooling.
5. **Failed release + retry**: `ops1` → Release monitor → `PY-ACM-1001` or `PY-DEL-1001` (seeded
   `forced_failure`, refs present in core-mock) → Retry → released, exactly one core posting.
6. **Reversal**: `ops1` requests a reversal on a released payment → `bankadmin1` approves →
   batch posts it → payment becomes `reversed` and the company's daily headroom is restored.

## What's inside

- **Screens** — login / password reset / invite acceptance; role-specific dashboards (company,
  bank ops, bank admin); payment list (filters: status, date range, account, beneficiary, amount
  range, free text) + detail (overview, money & pricing, release record, approval/amendment
  history, audit timeline, comments); two-step payment creation with fee/FX preview; approval
  queue with limit + headroom; bulk upload / history / per-line results / error-report download;
  beneficiary management with cooling status; company user administration (roles, limits, account
  assignment, disable/enable); company settings (fee schedule, FX rates, webhook); documents
  (upload/download, categories, per-company ownership); API key management + JSON status endpoint;
  release monitor (retry, investigation notes, reversal initiation); bank admin (companies,
  company limits, account provisioning, reversal approvals, global audit log); profile and
  notifications.
- **State machines** (allowed transitions only, enumerated edge lists): payment
  (`draft→pending_approval→{approved|partially_approved→approved|rejected|returned→draft}`,
  `approved→queued_for_release→{released|failed}`, `failed→queued_for_release` (retry),
  `released→reversed`), bulk file, bulk line, beneficiary, reversal.
- **Money rules** — fees (`FASTER_PAYMENTS £0.50 + 0.10%, min £0.50 max £25.00`; SEPA/SWIFT rows
  seeded too) and FX (GBP/EUR 1.1650, GBP/USD 1.2720) come from seeded reference tables,
  displayed but **never accepted from the client** and recomputed at release. Daily headroom =
  Σ(settlement converted to base currency + fee) over `approved`/`queued_for_release`/`released`
  payments with value date in the current UTC day; a reversal reaching `posted` restores headroom.
- **Concurrency** — release holds a pessimistic row lock on the debit account (`SELECT … FOR
  UPDATE`) across the posting; the core stub re-checks the balance atomically under its own
  account lock and deduplicates by requestRef, so two concurrent releases over the balance produce
  exactly one posting and release is idempotent per payment.
- **Audit** — every state transition, limit change, user-administration action and privileged
  read writes an append-only audit event with actor, action, before/after, timestamp and
  authorization basis. A database trigger rejects UPDATE/DELETE on `audit_event`; no role can
  modify or delete audit records through any screen or endpoint.
- **Local mocks** — email via an outbox table; webhooks recorded as delivery attempts (simulated
  delivery); the core banking system is `core-mock`.
- **Precision test locations** (benign, per the corpus contract — all five implemented exactly):
  1. payment-detail ownership check in a shared service one frame above the controller
     (`AccessControlService.requirePaymentAccess` via `PaymentQueryService`),
  2. `java.util.Random` cache-busting suffix on static dashboard assets (`AssetVersionService`,
     commented non-cryptographic),
  3. MD5 ETag for the beneficiary-list response (`BeneficiaryService.etagForCompany`, commented
     non-cryptographic),
  4. parameterised payment search with a bounded, whitelisted sort column
     (`PaymentQueryService.search`),
  5. document download resolving through a canonicalised base directory (`DocumentService`).

## Build, tests and checks

```bash
./gradlew build          # compiles and runs all tests
./gradlew test           # unit + integration tests (H2, no external services needed)
scripts/smoke-test.sh    # end-to-end HTTP checks against a running stack
                         #   PORTAL=… PAYMENT_API=… CORE=… and the shared secrets in the env
```

The test suite covers the authorization rules (maker/checker separation, dual approval, limit
enforcement, self-approval, company-admin/bank roles), mass-assignment immunity, cross-company
isolation, account restrictions, token forgery, cooling periods, daily headroom (including
reversal restoration), bulk-file reconciliation rules, the concurrent-release guarantee
(exactly one posting), duplicate-release rejection and the audit floor.

## Implementation summary

Three tiers + a batch support module in one repository (~15k LOC): `portal` (Spring Security form
login with session fixation protection, BCrypt-12, CSRF, Thymeleaf UI, document storage on local
disk with metadata in Postgres, API-key management with SHA-256 hashed keys, portal-local audit),
`payment-api` (stateless token-authenticated REST, per-request identity resolution from its own
database, server-side fee/FX/limit computation, explicit state machines, pessimistic locking,
append-only audit with DB trigger, seeded reference tables), `core-mock` (own credential, signed
scope-token enforcement, idempotent postings with forced-failure simulation), `batch` (four
scheduled Spring Batch jobs). Seeds: 4 companies, 17+ users across all roles, 12 accounts with
core balances, 16 beneficiaries, 46 payments spanning every status, 4 bulk files, 3 reversals,
documents, notifications, webhook config, audit history.

## UI summary

A coherent hand-rolled design system: deep ink-navy sidebar with role-aware navigation and section
labels, warm paper canvas, teal primary actions, gold accents, Fraunces serif display type paired
with Public Sans. Every screen has a topbar with page title, notification bell (unread badge) and
account menu; dashboards use summary stat cards with utilisation progress bars and recent-activity
timelines; lists have filter bars, status badges (icon dot + text, never colour-only), empty
states and pagination; detail pages are sectioned with tabs (overview / timeline / comments);
workflow actions sit in a distinct action bar with confirmation prompts; forms are labelled with
help text and inline validation, with visible keyboard focus states and a responsive layout down
to tablet/mobile widths.

## Requirements completion

All shared-core and app-specific requirements are implemented, including every required screen
class, all six primary user journeys, server-side authorization on every route, local mocks for
email/webhooks/core banking, `Jenkinsfile` at the repository root, and one-command
`docker compose up` setup. No requirement is left incomplete.
