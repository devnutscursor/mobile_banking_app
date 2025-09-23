# Data Model & Offline Sync Design (English)

This document describes entities, relationships, and the offline-first synchronization strategy with Firebase.

---

## 1) Core Entities (Logical Schema)
- `User` { id, role: [admin|dealer|agent], name, phone, dealerId?, active }
- `License` { id, key, assignedToUserId, deviceId, validUntil, active }
- `Operator` { id, name, type: [USSD|NON_USSD], active }
- `OperatorAction` { id, operatorId, name, kind: [deposit|withdraw|transfer|custom], requiredFields[], ussdTemplate? }
- `Customer` { id, fullName, dateOfBirth, nationalId, issueDate, expiryDate, barcodeRaw?, createdByUserId }
- `Account` { id, ownerType: [dealer|agent], ownerId, operatorId }
- `CashRegister` { id, ownerType: [dealer|agent], ownerId, openedAt, closedAt?, openingBalance }
- `CashMovement` { id, cashRegisterId, type: [in|out], amount, refType, refId, createdAt }
- `VirtualLedgerEntry` { id, accountId, delta, reason, refType, refId, createdAt }
- `Transaction` { id, operatorId, actionId, fromParty, toParty, amount, fee, status, channel: [USSD|FORM], meta }
- `UssdTemplate` { id, operatorId, actionId, templateString, fields: [number, amount, ...] }
- `SyncState` { id, entity, lastSyncedAt }

See relationships below for foreign keys and ownership.

---

## 2) Relationships
- `User (dealer)` 1—N `User (agent)` via `dealerId` (optional if independent)
- `Operator` 1—N `OperatorAction`
- `Owner (dealer/agent)` 1—N `Account` (per operator)
- `Owner` 1—N `CashRegister`; `CashRegister` 1—N `CashMovement`
- `Account` 1—N `VirtualLedgerEntry`
- `Transaction` links accounts and creates `CashMovement`/`VirtualLedgerEntry`
- `UssdTemplate` linked to `OperatorAction` (USSD only)

---

## 3) Accounting Rules (Examples)
- Agent buys virtual credit from Dealer (amount M, operator X):
  - Agent: `VirtualLedgerEntry(+M)` on `Account(agent,X)`; `CashMovement(out,M)` on Agent cash register
  - Dealer: `VirtualLedgerEntry(-M)` on `Account(dealer,X)`; `CashMovement(in,M)` on Dealer cash register
- Customer deposit (at Agent):
  - Agent: `VirtualLedgerEntry(-M)`; `CashMovement(in,M)`
  - Customer: tracked as off-system or memo; focus is Agent/Dealer stock

---

## 4) Local vs Firestore Storage
- Local (Room): all entities required to operate offline, plus `SyncQueue`
- Firestore: mirrored collections (users, licenses, operators, actions, customers, accounts, registers, transactions, movements, ledgerEntries)
- Some global data (default operators) is seeded from Firestore on first sync

---

## 5) Synchronization Strategy
- Writes: any local create/update is added to `SyncQueue` with timestamp and UUID
- Push: WorkManager flushes the queue to Firestore (batch writes where possible)
- Pull: fetch changes since `lastSyncedAt` by collection
- Conflicts: compare `updatedAt`; `last-write-wins` except domain exceptions:
  - Licenses: server is source of truth
  - Balances: prevent direct balance edits; derive from entries
- Idempotency: use `clientId` to deduplicate in Cloud Functions

---

## 6) Security & Firestore Rules (Overview)
- Access filtered by role and ownership (dealer/agent)
- Transaction writes: only if license valid and user active
- Cloud Functions: validate accounting consistency before commit

---

## 7) Indexing & Search
- `Customer`: indexes on `nationalId`, `fullName`
- `Transaction`: indexes on `operatorId`, `createdAt`, `createdByUserId`

---

## 8) Performance
- Pagination by date/time
- Compaction: periodic derived balance calculation in Cloud Functions to speed up UI

---

## 9) Backups & Restore
- Firestore scheduled exports
- Local: optional encrypted export of Room database

---

## 10) Audit Logs
- Events: sign-in, sync, USSD action edits, reversals, adjustments
- Storage: Firestore `audits` + Storage for attachments
