# Client Feedback Implementation Plan

Tracking checklist for UAT feedback. Mark items `[x]` when complete.

Recommended order: **P0 → P1 → P2 → P3**

## Balance data model (1.3)

- `users.cashBalance` — physical cash in register (global per user)
- `users.virtualCredit` — legacy/global credit field
- `operator_balances` — per-operator virtual credit (`userId_operatorId` doc id), synced via `DataSyncService` and pushed immediately via `OperatorBalanceSyncHelper`

---

## Part 1 — TRANSACTIONS

### 1.1 Customer search (P0) — [x]

- [x] Fix `updateCustomerSpinner()` to preserve selection when still in filtered list
- [x] Debounce search (~300ms) + ignore stale async results
- [x] Hint updated: name / phone / ID search
- [x] FR strings aligned

### 1.2 Receipt printing (P0) — [x]

- [x] Restore header/footer via `R.string.receipt_header`, `receipt_footer_line1/2`
- [x] Notes, action name, payment status on receipt
- [x] Bluetooth decision: see [`docs/BLUETOOTH_RECEIPT_DECISION.md`](docs/BLUETOOTH_RECEIPT_DECISION.md)
- [x] `ReceiptPrinterHelper` (ESC/POS) + share-sheet PNG fallback
- [x] Bluetooth permissions in manifest

### 1.3 Balance sync (P1) — [x]

- [x] Document data model (above)
- [x] Push `operator_balances` after balance changes + on cash register resume
- [x] First-login pull: keep local balances if Firestore empty

### 1.4 Transaction notes (P1) — [x]

- [x] Notes in Android details dialog + list snippet
- [x] Notes column in admin `TransactionsTab` / `FilteredTransactionsTab`
- [x] `userNotes` field separate from system `notes`
- [x] Notes on receipt

### 1.5 Payment status paid/unpaid (P1) — [x]

- [x] `paymentStatus` on Room + Firestore + admin types
- [x] Default `paid` on create
- [x] Dealer toggles unpaid in cash register dialog
- [x] Filter on app + admin portal
- [x] Sync + Excel export

### 1.6 Optional signature (P2) — deferred

- [ ] Confirm UX with client
- [ ] Implementation pending

---

## Part 2 — LICENSE

### 2.1 Generate button (P2) — [x]

- [x] Visible `message.success` feedback on Generate

### 2.2 Issue date automatic (P2) — [x]

- [x] Default `issueDate` to today on create

### 2.3 Max users mandatory (P2) — [x]

- [x] Required, min 1, default 1

### 2.4 Sort lists newest first (P2) — [x]

- [x] Licenses, agents, dealers sorted desc

### 2.5 License security (P0) — [x] partial

- [ ] Review client security video (pending client asset)
- [x] Fix `assignedToUserId` array queries in admin portal
- [x] Atomic Firestore transaction for `maxAgentCount` on activation
- [x] Crypto-random license key generation
- [ ] Production Firestore rules (documented in `SECURITY.md`)

---

## Part 3 — COMMISSION

### 3.1 Per-type rates (P2) — [x]

- [x] `depositRate`, `withdrawalRate`, `transferRate` on entity + sync
- [x] Mobile config UI (three rate fields)
- [x] `CommissionCalculator` picks rate by transaction type
- [x] Migrate: existing single rate copied to all three on pull

---

## Part 4 — OTHERS

### 4.1 Phone login default (P2) — [x]

- [x] Login opens with phone + PIN by default

### 4.2 Security document (P3) — [x]

- [x] [`SECURITY.md`](SECURITY.md) created
- [x] Offline password hashing fixed (`PasswordHasher`)

### 4.3 Captcha / Turnstile (P3) — [x]

- [x] Cloudflare Turnstile on admin login (env-configured, localhost bypass)
- [x] Server verification API route
- [x] `.env.example` updated

---

## Captcha / Bluetooth decisions

| Decision | Choice | Doc |
|----------|--------|-----|
| Web captcha | Cloudflare Turnstile (default) | `SECURITY.md`, `.env.example` |
| Receipt print | Hybrid: Bluetooth ESC/POS if MAC saved, else share PNG | `docs/BLUETOOTH_RECEIPT_DECISION.md` |
