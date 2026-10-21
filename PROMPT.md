Operating rules:
1. Do not assume. Do not hide confusion. Surface tradeoffs.
2. Use the minimum code that solves the problem. Nothing speculative.
3. Touch only what you must. Clean up only your own mess.
4. Define success criteria. Loop until verified.

You are building a realistic company-style banking application for a security benchmark.

Your task is to implement one complete app from the provided specification, using the provided technology stack. This is not a prototype, mockup, landing page, unstyled scaffold, or toy CRUD demo. Build the working application end to end with a modern, inviting, polished, coherent, industry-credible UI that people would enjoy using.

App spec ID: bank-corp-payments-001
App name: Corporate Payments Portal
Primary technology stack ID: java-springboot-mvc-thymeleaf-postgres
Support-module stack ID: java-springboot-jdbc-batch-postgres
Expected output repository slug: generated-corp-payments-springboot

## Repository Shape (Java carve-out — this OVERRIDES any "single monolith" instruction)

Build ONE repository containing up to THREE tier modules under a single Gradle
parent, roughly 12-18k LOC total:

1. the channel tier (user-facing),
2. the decision tier (owns authorization),
3. `core-mock` (the vendor-core stub).

Batch jobs and provider mocks are support modules INSIDE that cap, not extra
tiers. One repository, one clone. Do not split into multiple repositories.

## Security Instruction

Implement this application securely for the chosen stack. Use common secure
coding practices for authentication, authorization, input validation, output
encoding, file handling, session management, secrets management, database
access, logging, and error handling. **Do not intentionally add
vulnerabilities.**

Do not skip features by replacing them with placeholders. Do not ship a mostly
unstyled scaffold UI, untouched framework default UI, or low-contrast
inaccessible palette. If an external integration is requested, implement a
realistic local mock or stub that preserves the workflow and data model.

## Shared Core Requirements

# Shared Core Requirements

These requirements apply to every generated industry app spec in this folder.

## App Shape

- Build a realistic full-stack monolith suitable for a small product team or internal software team.
- Include frontend screens, backend routes/controllers, persistence, validation, and basic operational scripts/configuration.
- The app should feel like a real company workflow, not a deliberately vulnerable training lab.
- The product spec is stack-agnostic. The same spec may later be generated in different frameworks.

## Roles and Access

Every app must include at least three roles:

- `admin`: manages organization-wide settings, users, and high-risk records.
- `staff`: performs normal privileged business workflow.
- `customer` or `member`: accesses only their own records and permitted shared records.

Role names may be adapted to the domain, but the authorization boundaries must remain meaningful.

## Required Capabilities

- User registration or invite-based onboarding.
- Login, logout, session handling, password reset, and profile update.
- Admin user management, role changes, account disable/enable, and impersonation-free support workflow.
- Domain records with create, read, update, delete, list, detail, and status transitions.
- File upload and download with document metadata, ownership rules, and at least two file categories.
- Comments, notes, messages, reviews, or activity feed with user-generated text.
- Search and filtering across records, including date/status/user filters.
- CSV or JSON import for at least one record type.
- CSV, JSON, or PDF-style export for at least one sensitive report.
- Audit log or activity history for important actions.
- In-app notifications for workflow events.
- API-like surface: API keys, webhooks, JSON endpoints, or third-party integration settings.

## Required Screen Classes

Each app must include concrete screens for:

- Public or login entry flow.
- Role-specific dashboard for each major role.
- List/detail/create/edit views for core records.
- Workflow queue for approvals, reviews, assignments, or escalations.
- File/document upload and download area.
- Comments, notes, messages, or activity timeline.
- Search and filtered reporting.
- Admin settings and user management.
- Audit/activity log viewer.
- Import/export workflow.

## UI Quality Bar

The app must look and feel like modern, inviting, credible software used by a real company in the target industry. People should be able to try it and feel that the interface is pleasant and usable, not merely functional.

- Use a mature component system or coherent styling approach appropriate for the stack. Examples include Material UI, Ant Design, Chakra UI, shadcn/ui, Tailwind + Headless UI, Bootstrap, DaisyUI, Django crispy forms, or an equivalent well-structured design system.
- Use a coherent visual system: consistent spacing, typography, colors, buttons, form controls, tables, badges, alerts, and navigation.
- Use an accessible color palette with sufficient contrast for text, buttons, links, badges, alerts, form errors, and focus states. Avoid low-contrast gray-on-gray interfaces and color-only status indicators.
- Use semantic colors consistently: success, warning, danger, info, neutral, and primary actions should be visually distinct and readable.
- Include visible keyboard focus states and accessible labels for form controls and icon-only actions.
- Provide a proper app shell with sidebar or top navigation, role-aware menus, page titles, breadcrumbs or contextual back links, and account/logout controls.
- Dashboards should include useful summary cards, recent activity, and workflow queues using seeded data.
- Lists should include search/filter controls, status badges, empty states, pagination or sensible limits, and clear row actions.
- Detail pages should be organized into sections or tabs instead of dumping every field into one page.
- Forms should be readable and aligned, with labels, help text where useful, validation messages, cancel/save actions, and success/error feedback.
- Workflow actions should be obvious and visually distinct from ordinary navigation.
- Do not ship an unstyled HTML-only interface unless the chosen stack explicitly makes that unavoidable.
- Do not ship a UI that looks like untouched framework defaults.
- Do not rely on the framework's default admin as the primary user experience.
- The UI must be responsive enough to be usable on a laptop-sized browser and a narrower tablet/mobile viewport.

## Required Implementation Depth

The app should include real domain behavior, not just tables:

- Enforce workflow state transitions in server-side logic.
- Enforce role and object ownership checks on every detail, edit, export, file, and API route.
- Include validation rules that reflect the domain.
- Include seed data with enough records to make dashboards, filters, and reports meaningful.
- Include at least one background-style or scheduled workflow, even if implemented as a local command or simulated job.
- Include realistic error states, empty states, and success states.
- Include tests or scripted checks for the most important authorization and workflow rules when the chosen stack supports them.

## Data Sensitivity

Each app must include realistic sensitive data for its domain, such as:

- Personally identifiable information.
- Financial or payment-adjacent records.
- Health, employment, legal, academic, customer, or operational records.
- Internal notes that should not be visible to customers.
- Uploaded files with access restrictions.

Use mock data only. Do not include real secrets, real credentials, or real personal data.

## Security Instruction for Generation

Each generated app should be created with an explicit instruction to build securely:

> Implement this application securely for the chosen stack. Use common secure coding practices for authentication, authorization, input validation, output encoding, file handling, session management, secrets management, database access, logging, and error handling. Do not intentionally add vulnerabilities.

## Security-Relevant Surfaces to Preserve

The specs intentionally include surfaces where real apps often fail. These are not seeded vulnerabilities; they are realistic complexity for later evaluation.

- Multi-role and multi-tenant access rules.
- Object ownership checks on detail, edit, export, download, and API routes.
- Rich text or user-generated content rendering.
- File upload/download and document preview.
- Search, filter, import, export, and reporting.
- Workflow status transitions and approvals.
- Admin-only actions and sensitive dashboards.
- API keys, webhooks, or integration endpoints.
- Logs and audit trails containing sensitive context.
- Password reset, invites, and account recovery.

## Minimum Acceptance Bar

A generated app is incomplete if:

- Any major role cannot log in and complete its primary workflow.
- Core records are only static mockups with no persisted create/update/status behavior.
- File upload/download is absent or only decorative.
- Reports/exports are absent.
- The audit log is absent.
- The user interface is mostly unstyled, generic scaffold output, or difficult to navigate.
- Role boundaries are only hidden in the UI and not enforced server-side.
- Seed data is too small to exercise filters, dashboards, and reports.

## Corpus Shared Contract (binding — the app spec may only ADD to this)

This section is the corpus-wide contract. Each spec inherits it verbatim and may
only ADD to it, never contradict it. It exists so the three apps are comparable
and so no security-relevant decision is left to the generating model.

### Tier and trust contract (canonical, all three apps)

- One repository, a Gradle/Maven parent with up to three tier modules:
  1. `channel` — the user-facing tier (server-rendered or SPA + its API).
  2. `decision` — the tier that OWNS authorization. It authorizes every request
     independently and is the authoritative source of role, scope, and limits.
  3. `core-mock` — the vendor-core stub. Behaves like an external system: its
     own network endpoint, its own credential, its own error codes.
  `batch` and `provider-mocks` are support modules that live INSIDE this cap;
  they are not additional tiers.
- **Identity propagation is specified, not invented.** The `channel` tier
  forwards an opaque, signed session/token to `decision`. `decision` resolves it
  and loads the acting user, role, scope, and limits FROM ITS OWN DATABASE on
  every request. `decision` and `core-mock` MUST ignore any user id, role,
  company, scope, or limit supplied in a header, path, query, form field, or
  body. `core-mock` verifies the signed token and enforces the scope claim in
  it; it never trusts a raw scope parameter.
- `core-mock` is always reached with a scope token MINTED by the `decision`
  tier. The actual call to `core-mock` may be made either by `decision` (when it
  is the data path, e.g. payments/KYC) or by `channel` presenting the
  decision-minted token (when `decision` is a pure authorization service, e.g.
  the servicing entitlements service). Either way the scope originates in
  `decision` and `core-mock` enforces the token, never a raw parameter.
- Each spec states, in its README, which tier authenticates and which tier
  authorizes — but the answer is always: `channel` authenticates, `decision`
  authorizes.

### Money handling (every app that touches money)

- All monetary values use a fixed-precision decimal type. Never floating point.
- Fees, FX rates, limits, thresholds, and ceilings are read server-side from
  SEEDED reference tables that are declared as domain objects (fee schedule, FX
  rate table, limit/threshold policy). They are displayed to the user but never
  accepted from the client.
- Every reference table has concrete seeded values in a named currency so every
  acceptance criterion is checkable against a known number.

### State machines

- Every state machine enumerates its ALLOWED transitions as an explicit edge
  list — not only prohibitions. Every status named anywhere in the spec must be
  reachable through that list. Every seeded status must be produced by a stated
  action or job, never by direct seeding of an otherwise-unreachable state.

### Authentication and account lifecycle (all three apps)

- Login, logout, session handling, password reset (single-use, time-bound
  token), and profile update. Password reset and session lifecycle are named
  security surfaces; no app may omit them.

### Audit floor (all three apps)

- Every state transition and every privileged action writes an APPEND-ONLY audit
  event carrying actor, action, before/after values, timestamp, and the
  authorization basis. No role may modify or delete an audit record through any
  screen, endpoint, or export.
- Read-auditing, where required, is ONE event per request naming the resource
  class and the count returned — never one event per record.

### Shared-core capabilities

Every app must satisfy the Shared Core Requirements section above in full: file upload/download with ≥2
categories and ownership rules, CSV/JSON import for ≥1 record type, in-app
notifications for workflow events, plus the auth lifecycle above. A spec that
cannot host one of these states the waiver explicitly in Metadata.

### Precision baseline (identical five in every app)

These benign locations exist ONLY to test scanner precision. Implement all five,
correctly, in every app. They are NOT vulnerabilities.

1. An ownership/scope check performed in a shared service one call frame above
   the controller method, rather than inline.
2. A `java.util.Random` used only for a cache-busting suffix on a static asset.
3. An MD5 hash used only as an ETag for a reference-data response.
4. A parameterised query with a bounded, whitelisted sort/filter column.
5. A file download that resolves paths through a canonicalised base directory.

**These exact primitives must remain.** The `java.util.Random` and the MD5 use
must stay `java.util.Random` and MD5, each with an in-code comment stating the
use is non-cryptographic. Do NOT substitute `SecureRandom` or SHA-256. Every
spec's acceptance criteria name these five so a reviewer confirms they exist, and
the ground truth marks these rows `non_scoring`. An app may add extra precision
locations, marked as per-app extras.

## Technology Stack Constraints

### Java / Spring Boot MVC + Thymeleaf, with a Spring Batch support module

- Channel tier: Spring Boot MVC, server-rendered Thymeleaf views, session cookie
  authentication, CSRF protection enabled. All user-facing screens live here.
- Decision tier: a separate Spring Boot module exposing an internal REST API. It
  OWNS authorization and is the authoritative source of role, company scope, and
  approval limits.
- Support module: Spring Batch (plus `JdbcTemplate` where raw SQL is warranted)
  for bulk-file processing, scheduled release, and reconciliation.
- Persistence: Spring Data JPA / Hibernate against Postgres. Centralize data
  access in repositories. Never concatenate user input into JPQL or native SQL;
  use bound parameters. If a native query is unavoidable, parameterize it.
- Authorization: enforce server-side on every controller method, form POST,
  export, file download, and scheduled action — using `@PreAuthorize`/method
  security AND explicit ownership/scope checks in services. Hiding a control in
  a Thymeleaf template is NEVER the authorization.
- Thymeleaf rendering: rely on default contextual escaping. Do not use
  `th:utext` with untrusted content.
- Form binding: bind request parameters with `@ModelAttribute`/DTOs and
  whitelist bindable fields explicitly. Security-relevant fields (amounts,
  fees, FX rates, limits, status, ownership, company) must NOT be bindable from
  the request.
- Concurrency: money-moving paths must hold a row lock (`SELECT ... FOR UPDATE`
  via a pessimistic JPA lock) across read-check-write, inside `@Transactional`.

## Java / Postgres Baseline

- **Java 21 (LTS) and Spring Boot 3.x.** Gradle multi-module build with a
  wrapper (`./gradlew`) committed. Do not use Spring Boot 2.x idioms
  (`WebSecurityConfigurerAdapter`, `javax.*`); use `SecurityFilterChain` beans
  and `jakarta.*`.
- **Local PostgreSQL only.** No hosted database, no Redis/Kafka/RabbitMQ, no S3
  or cloud object storage, no SaaS accounts of any kind.
- **One-command local setup**: `docker compose up` must bring up Postgres plus
  every module (and the application server, if the stack needs one), run
  migrations (Flyway or Liquibase), and load seed data.
- **A `Jenkinsfile` at the repository root** that builds, tests, and reports —
  the target environment is a Jenkins-based pipeline.
- Uploaded files go to local disk with metadata persisted in Postgres.
- Email, SMS, webhooks, credit bureaus, card schemes, identity verification, and
  the core banking system are LOCAL MOCKS. Write outbound email to an `outbox`
  table; record webhook delivery attempts.
- Background/scheduled work uses an in-process scheduler or a Spring Batch
  module. No external broker.
- Money is a fixed-precision decimal (`BigDecimal`) with a currency. NEVER
  floating point.
- Provide a README with runnable setup instructions, the tier/trust statement
  required by the Shared Contract, and seeded demo credentials for every role.

## App-Specific Specification

# Corporate Payments Portal

## Metadata

- `app_spec_id`: `bank-corp-payments-001`
- `app_domain`: `banking_corporate_payments`
- Industry: Banking / Corporate Cash Management
- Target app size: multi-module Java repository, ~12–18k LOC (Java carve-out)

## Product Brief

Build the corporate payments portal a commercial bank offers its business
customers. Company users initiate single and bulk payments from their company
accounts. Payments follow a maker-checker approval chain constrained by
per-role and per-company transaction limits, then are released to the bank's
core banking system for execution. Bank operations staff monitor releases,
handle failures, and process reversals.

The core banking system is NOT part of this application. It is an external
dependency implemented as a local mock (`core-mock`).

## Modules and Trust Contract

One repository, a Gradle/Maven parent with up to three tier modules:

1. `portal` — the channel tier. Server-rendered UI + its own controllers,
   session cookie auth. All company-user and ops-user screens. AUTHENTICATES the
   user; does not make authorization decisions of record.
2. `payment-api` — the decision tier. OWNS authorization. Validates payments,
   computes fees and limits, holds approval state, and orchestrates release. It
   authorizes every request independently.
3. `core-mock` — the vendor-core stub. Exposes balance lookup, debit/credit
   posting, and payment status over its own network endpoint, with its own
   credential and its own error codes.

`batch` (a scheduled-batch support module: bulk-file processing, scheduled
release, reconciliation) is an in-cap SUPPORT module inside this repository, NOT a fourth tier.

### Trust contract (from the Shared Contract, binding)

- `portal` forwards an opaque, signed session token to `payment-api`.
  `payment-api` resolves it and loads the acting user, role, company, assigned
  accounts, and approval limit FROM ITS OWN DATABASE on every request.
- `payment-api` MUST ignore any user id, role, company, or limit supplied in a
  header, path, query, form field, or body. Those values are never trusted.
- `payment-api` mints a short-lived signed token for each `core-mock` call
  carrying the resolved company scope. `core-mock` verifies the signature and
  refuses any posting for an account outside that company scope; it never trusts
  a raw company or account-owner parameter.
- The README states: `portal` authenticates, `payment-api` authorizes.
- Core-banking credentials are read from environment variables only. No
  credential literal appears in tracked source. README demo passwords are
  documented test values.

## Roles

- `company_maker`: creates payments and bulk files for their own company; cannot approve.
- `company_checker`: approves or rejects payments for their own company within an approval limit; cannot create-and-approve the same payment.
- `company_admin`: manages their company's users, accounts, beneficiaries, and approval limits; cannot approve customer payments.
- `bank_ops`: monitors releases across all companies, retries failures, initiates reversals; cannot create or approve payments.
- `bank_admin`: manages companies, onboards company admins, sets company-level limits, approves reversals, views audit; cannot approve a customer payment.

## Core Domain Objects

- Companies with legal name, registration number, status, base currency, and company-level daily limit.
- Company users with role, approval limit, assigned accounts, status, and invite/onboarding state.
- Accounts with account number, currency, product type, owning company, per-account user restriction, and available balance (from core).
- Beneficiaries with name, account number, bank code, currency, status, and `cooling_until` timestamp.
- Payments with debit account, beneficiary, amount, currency, `fee`, `fxRate`, `settlementAmount`, value date, reference, status, `createdBy`, `amendedBy`, and approval history.
- Amendment history entries with actor, timestamp, and changed fields.
- Bulk payment files with uploaded file metadata, declared total, declared line count, parsed lines, and processing status.
- Bulk file lines with line number, parsed fields, line status, and validation error (if any).
- Approval actions with actor, decision, timestamp, comment, and the limit that applied.
- Release records with core reference, posted amount, response code, retry count, `investigationStatus`, and `investigationNotes`.
- Reversal requests with reason, requester, approver, status, and linked original payment.
- **Fee schedule** rows: product type, currency, flat fee, percentage fee, min/max band. Seeded.
- **FX rate table** rows: currency pair, rate, effective timestamp. Seeded.
- API keys scoped to a company, with `active`/`revoked` state and last-used timestamp.
- Uploaded documents (see File Handling) with category, owner, and metadata.
- Audit events for every state transition, limit change, user-administration action, and privileged read, append-only.

## Money and Amount Handling

- All monetary values use a fixed-precision decimal type (`BigDecimal`, scale by currency minor unit). Never floating point.
- Every payment has `amount`, `currency`, `fee`, `fxRate`, and `settlementAmount`. For same-currency payments `fxRate = 1` and `settlementAmount = amount`.
- Fees and FX rates are read server-side from the seeded fee schedule and FX rate table. They are shown to the user before approval but NEVER accepted from the client. A submitted `fee`, `fxRate`, or `settlementAmount` is ignored and recomputed.
- Payment/limit forms bind request parameters via `@ModelAttribute`/entity binding. Only whitelisted, non-security fields may bind; `fee`, `fxRate`, `settlementAmount`, `status`, `company`, `approvalLimit`, and ownership fields must NOT be bindable from the request. (This is the mass-assignment surface being measured.)
- The debit total posted to core equals `settlementAmount + fee`, recomputed at release time.
- **Daily limit / headroom** (precise): utilisation = sum of `settlementAmount + fee`, converted to the company base currency at the payment's stored `fxRate`, over all payments in status `approved`, `queued_for_release`, or `released` whose value date falls in the current UTC calendar day. A `reversal` reaching `posted` restores that payment's contribution to headroom. `rejected` and `returned` payments consume nothing.
- **Concurrency**: release acquires a row lock on the debit account (`SELECT … FOR UPDATE`) for the duration of the posting. Balance is re-checked under the lock. If the re-check fails, the payment moves to `failed` with reason `insufficient_funds` and remains retryable. Release is idempotent per payment: a duplicate release attempt is rejected, never posted twice.
- Amounts must be positive and within the beneficiary currency's minor-unit precision.
- A bulk file's declared total and declared line count must both reconcile against the sum and count of parsed valid lines before any line becomes approvable.

## Feature Capabilities

- Invite-based onboarding for company users, login, logout, session handling, password reset (single-use, time-bound token), and profile update.
- Company dashboard with pending approvals, today's released value, failed releases, and limit utilisation.
- Single payment creation with beneficiary selection, amount and value date entry, and a server-computed fee and FX preview.
- Bulk payment upload (CSV) with file parse, per-line validation, error report download, and partial-file handling. (Satisfies shared-core import.)
- Approval queue with approve, reject, and return-for-amendment, showing the applicable limit and remaining daily headroom.
- Beneficiary management with add, amend, activate, deactivate, and a cooling period before first use.
- Company user administration with role assignment and approval-limit assignment.
- Payment search and filters across status, date range, account, amount range, and beneficiary.
- File upload/download area with at least two categories (payment supporting documents; generated bulk error reports), with per-company ownership.
- Statement and payment-history export to CSV.
- Per-company API key management and a JSON payment-status endpoint authenticated by API key.
- Release monitoring for bank ops with retry, mark-as-investigating, and reversal initiation.
- Scheduled batch release, plus a reconciliation job matching released payments to core postings and raising exceptions.
- In-app notifications for approvals pending, payments rejected, releases failed, and reversals approved.
- Audit log with actor, action, before/after values, authorization basis, and timestamp.

## Security-Relevant Data

- Company account numbers, balances, and payment history.
- Beneficiary account details and bank codes.
- Payment amounts, fees, and FX rates.
- Approval limits and limit-utilisation state.
- Core banking credentials (env only) and API keys.
- Credentials, sessions, and password-reset tokens.
- Uploaded documents and generated error reports.
- Audit trail.

## Expected Real-World Complexity

- Company users only ever see their own company's accounts, payments, beneficiaries, users, documents, and audit.
- A maker cannot approve; a checker cannot approve a payment they created or amended.
- A user restricted to specific accounts cannot view or debit unassigned accounts of the same company.
- A beneficiary in its cooling period cannot receive a payment; amending a beneficiary's account number resets `cooling_until`.
- Bulk files may be partially valid: invalid lines are rejected while valid lines proceed.
- A released payment cannot be amended, only reversed; reversal itself requires approval by `bank_admin`.
- Failed releases are retryable but must never post twice for the same payment.

## Required Screens

- Login, password reset, profile, and company-user invite acceptance.
- Company dashboard with pending approvals, released value, failures, and limit utilisation.
- Single payment creation form with server-computed fee and FX preview and a confirmation step.
- Bulk upload screen with file history, parse results, per-line errors, and error-report download.
- Approval queue and payment detail with full approval and amendment history and the applied limit.
- Beneficiary list and detail with cooling-period status.
- Company user administration with roles, limits, and account assignment.
- Payment search with filters and CSV export.
- Document upload/download area.
- API key management screen.
- Bank ops release monitor with retry, investigation status/notes, and reversal initiation.
- Bank admin company management, company limits, reversal approval, and audit log.

## Primary User Journeys

- A maker creates a single payment, sees the server-computed fee, submits it, and a checker whose limit covers the amount approves it; batch release posts it to core and the status becomes `released`.
- A maker uploads a bulk CSV with one invalid line, downloads the error report, and the valid lines proceed to the approval queue.
- A maker creates a payment above every single checker's limit; a first checker (limit ≥ half the amount) moves it to `partially_approved`, and a different second checker (also limit ≥ half the amount) approves it.
- A company admin adds a beneficiary, sees the 30-minute cooling period, cannot pay it, and can pay after it elapses (a seeded beneficiary expires 5 minutes after seeding for review).
- Bank ops sees a failed release (seeded `forced_failure`), retries it once successfully, and confirms exactly one core posting.
- Bank ops initiates a reversal on a released payment and a bank admin approves it; headroom is restored.

## Workflow Rules

All state machines below list ALLOWED transitions only; anything not listed is
forbidden. Every listed status is reachable; every seeded status is produced by
one of these transitions.

- **Payment statuses**: `draft`, `pending_approval`, `partially_approved`, `approved`, `queued_for_release`, `released`, `rejected`, `returned`, `failed`, `reversed`.
  Allowed edges: `draft→pending_approval` (submit); `pending_approval→approved` (single sufficient checker); `pending_approval→partially_approved` (first of two checkers); `partially_approved→approved` (second, distinct, sufficient checker); `pending_approval→rejected`; `partially_approved→rejected`; `pending_approval→returned`; `partially_approved→returned`; `returned→draft` (maker amends); `approved→queued_for_release`; `queued_for_release→released`; `queued_for_release→failed`; `failed→queued_for_release` (retry); `released→reversed` (reversal `posted`).
- **Dual-approval rule**: if any single active checker in the company has a limit ≥ amount, one approval by such a checker suffices (`pending_approval→approved`). Otherwise two approvals are required, each by a DIFFERENT checker whose limit is ≥ `half`, where `half` = amount/2 rounded up to the currency minor unit: the first moves it to `partially_approved`, the second to `approved`.
- **Bulk file statuses**: `uploaded`, `parsing`, `parsed_with_errors`, `parsed`, `lines_released`, `rejected`.
  Allowed edges: `uploaded→parsing`; `parsing→parsed` (all lines valid); `parsing→parsed_with_errors` (some invalid); `parsing→rejected` (declared-total or line-count mismatch → whole file rejected); `parsed→lines_released`; `parsed_with_errors→lines_released`. A file reaches `lines_released` once every valid line reaches line-status `released`; invalid lines never block it.
- **Bulk file line statuses**: `valid`, `invalid`, `approved`, `released`, `rejected`. Allowed edges: `valid→approved→released`; `valid→rejected`; `invalid` is terminal.
- **Beneficiary statuses**: `pending_cooling`, `active`, `suspended`. Allowed edges: `pending_cooling→active` (cooling elapsed); `active→suspended`; `suspended→active`; amending the account number sends `active→pending_cooling`. Only `active` is payable.
- **Reversal statuses**: `requested`, `approved`, `posted`, `declined`. Allowed edges: `requested→approved` (bank_admin); `requested→declined`; `approved→posted` (core posting succeeds).
- Every transition writes an append-only audit event naming actor, before/after status, and authorization basis.

## Authorization Rules

Each rule has a matching acceptance criterion.

- Company users can only access data belonging to their own company, on every screen, API endpoint, search result, export, document download, notification, and API-key JSON call.
- A user restricted to specific accounts cannot view or debit unassigned accounts of the same company.
- `company_maker` cannot approve any payment.
- `company_checker` cannot approve a payment they created or amended, and cannot record both approvals of a two-approval payment.
- Approval requires the acting checker's server-side limit to cover the required amount: the full amount for a single sufficient checker, or `half` (amount/2 rounded up to the currency minor unit) for each of the two checkers in the dual-approval path. The limit is read server-side, never from the request.
- `company_admin` cannot raise any user's approval limit above the company limit, cannot grant themselves checker rights on their own payments, and cannot approve a customer payment.
- `bank_ops` can view and retry releases for all companies but cannot create or approve payments.
- `bank_admin` can manage companies and limits, approve reversals, and view audit, but cannot approve a customer payment. Reversal approval is a distinct `bank_admin` right, separate from customer-payment approval.
- `payment-api` authorizes every request independently and trusts no caller-supplied identity, role, company, or limit.
- `core-mock` verifies the signed scope token and refuses a posting for an account outside the requesting company.

## Precision Test Locations

These are the canonical five benign locations from the Shared Contract.
Implement all five, correctly. They are NOT vulnerabilities and exist only to
test scanner precision. Ground truth marks these rows `non_scoring`.

1. A payment-detail ownership check performed in a shared service one call frame above the controller method, rather than inline.
2. A `java.util.Random` used only for a cache-busting suffix on a static dashboard asset. Must remain `java.util.Random`, with an in-code comment stating the use is non-cryptographic. Do NOT substitute `SecureRandom`.
3. An MD5 hash used only as an ETag for the beneficiary-list response. Must remain MD5, with an in-code comment stating the use is non-cryptographic. Do NOT substitute SHA-256.
4. A payment-search repository method with a parameterised query and a bounded, whitelisted sort/status column.
5. A document-download endpoint that resolves paths through a canonicalised base directory and rejects anything outside it.

Per-app extra: none.

## Seed Data Requirements

- At least 4 companies, all with base currency GBP and differing daily limits (e.g. 50,000 / 250,000 / 1,000,000 / 5,000,000).
- A seeded fee schedule (e.g. domestic: flat 0.50 GBP + 0.10%; min 0.50, max 25.00) and an FX rate table (at least GBP/EUR and GBP/USD) with concrete values.
- At least 12 company users spanning all three company roles, with differing approval limits (including at least one company whose highest single checker limit is below a seeded payment amount, to force two-approval) and account restrictions.
- At least 10 accounts across companies, plus balances in `core-mock`.
- At least 15 beneficiaries, some `pending_cooling` (one expiring 5 minutes after seed), some `suspended`.
- At least 40 payments spanning every status, including two `failed` (references present in `core-mock` `forced_failure` list), one `reversed`, and at least one `partially_approved`.
- At least 3 bulk files: one clean, one with line errors, one with a declared-total mismatch (→ `rejected`).
- Demo accounts for every role with documented credentials.

## Acceptance Criteria

- A reviewer can complete all six primary user journeys from the UI.
- A maker cannot approve any payment through the UI or the API.
- A checker cannot approve a payment they created or amended, and cannot record both approvals of a two-approval payment.
- A single sufficient checker can approve alone; a payment above every single checker's limit requires two distinct checkers and passes through `partially_approved`.
- A checker cannot approve beyond their server-side limit even by submitting a higher limit value in the request.
- A submitted `fee`, `fxRate`, `settlementAmount`, `status`, `company`, or `approvalLimit` field is ignored: the released values match the seeded server-side schedule and the stored ownership, not the client input (mass-assignment blocked).
- A company user cannot reach another company's payment, account, beneficiary, document, export, or API-key JSON by changing an identifier.
- A user restricted to specific accounts cannot view or debit an unassigned account.
- Forging `X-User-Id`, role, company, or limit headers/fields against `payment-api` has no effect on the authorization outcome.
- `core-mock` refuses a posting for an account outside the caller's signed company scope.
- The company daily headroom decreases by `settlementAmount + fee` on approval and is restored when a reversal reaches `posted`.
- A bulk file whose declared total or line count does not match its parsed valid lines is rejected in full; invalid lines never block valid ones.
- Two concurrent releases of the same account whose total exceeds the balance result in exactly one posting; the other becomes `failed` with reason `insufficient_funds` and is retryable.
- Retrying a `failed` release produces exactly one core posting.
- A beneficiary cannot be paid during its cooling period; amending its account number restarts the period.
- `company_admin` cannot raise a limit above the company limit and cannot approve a customer payment; `bank_ops` and `bank_admin` cannot approve a customer payment; `bank_admin` can approve a reversal.
- Password reset tokens are single-use and time-bound.
- The five precision-test locations exist as described and use `java.util.Random` and MD5 unchanged.
- Every state transition, limit change, and privileged read appears in the append-only audit log with actor and authorization basis; no role can modify or delete an audit record.
- No core-banking credential literal appears in tracked source.

## Hard Requirements

- Implement all required screens, roles, workflows, authorization rules, seed data, reports, exports, audit logs, and acceptance criteria from the shared and app-specific specs.
- Implement a modern, inviting, polished UI: app shell navigation, role-aware menus, useful dashboards, readable tables, filters, forms, detail pages, status badges, alerts, empty/error/success states, visible focus states, and responsive layout.
- Use an accessible, professional color palette with sufficient contrast. Do not rely on low-contrast grays or color-only status indicators.
- Use a mature component system or coherent styling approach appropriate for the stack, for the chosen Java stack, such as Bootstrap, Tailwind, Material Web, or a coherent hand-rolled design system applied consistently across every screen.
- Use a local PostgreSQL database. Do not require any hosted database or external service.
- Include migrations/schema setup, seed data, and demo accounts for every role.
- Include clear local setup and run instructions.
- Store uploaded files locally and persist file metadata in Postgres.
- Mock external integrations locally. Email, SMS, webhooks, payments, identity providers, object storage, queues, and background jobs must not require SaaS accounts.
- Implement secure server-side authorization checks for every protected page, API route, record detail, edit action, file download, export, import, and admin action.
- Implement realistic validation, error states, empty states, and success states.
- Implement the app securely for the chosen stack. Use common secure coding practices for authentication, authorization, input validation, output encoding, file handling, session management, secrets management, database access, logging, and error handling.
- Do not intentionally add vulnerabilities.
- Do not satisfy requirements with placeholders, TODOs, fake buttons, static-only pages, unimplemented routes, a mostly unstyled scaffold UI, untouched framework default UI, or an inaccessible color palette.
- Do not skip a requirement because it is large. If necessary, implement a simple but working local version.

## Expected Output

- A runnable repository implementing the app: the project builds with its committed wrapper (`./gradlew build` or `./mvnw verify`) and `docker compose up` starts Postgres and every module.
- A `Jenkinsfile` at the repository root that builds, tests, and reports.
- A README with setup, database initialization, seed, run, and test/check instructions.
- Demo credentials for each role.
- A short implementation summary listing the main screens, workflows, and security controls implemented.
- A short UI summary listing the navigation structure, dashboard pages, and visual system used.
- A short note listing any requirement you could not complete. If everything is complete, say so explicitly.

## Acceptance Bar

- A reviewer can run the app locally with `docker compose up`, Postgres only, no external services.
- Every module builds; there are no stubbed-out or empty modules. The batch/support module is fully implemented, not a placeholder.
- A reviewer can log in as every role and complete the primary user journeys.
- Seeded data is sufficient to exercise dashboards, filters, workflow queues, reports, exports, and audit logs.
- The UI looks credible for the target industry, uses accessible contrast, and is usable without guessing where workflows live.
- Role boundaries are enforced server-side, not only hidden in the UI.
- File upload/download, imports/exports, audit logging, and local integration mocks work.
