# Requirements Explained with Real-Life Examples and App Flow (English)

This document explains each requirement with a real-world example and maps it to the application flow.

---

## 1) Offline-First Mobile App (Android)
- Real life: An agent works in an area with poor internet. They must register a customer and perform transactions.
- App Flow: The agent fills KYC forms locally (Room/SQLite). Transactions go into a local queue. When connectivity returns, background sync pushes data to Firebase. Conflicts are handled safely.

## 2) Identity Forms (KYC) + PDF417 Scanner
- Real life: The agent scans the national ID card’s PDF417 barcode; full name, date of birth, card number, issue/expiry dates are read automatically.
- App Flow: ML Kit extracts fields; the agent reviews/edits, then saves. The customer profile is reusable for future transactions.

## 3) Pre-filled USSD Transactions (deposit, withdraw, transfer)
- Real life: To deposit 100 at 0999123456, the agent dials `*144*2*1*0999123456*100#` and then enters a 4-digit password.
- App Flow: The agent selects "Deposit", enters number and amount; the app generates the USSD from a template with placeholders `{number}`, `{amount}` and launches the dialer. The agent enters the password in the device’s USSD UI.

## 4) Dynamic Actions per Operator
- Real life: Operators change USSD sequences; sometimes new services are added (electricity, top-up).
- App Flow: Settings → Operators → Actions. The agent adds/edits `UssdTemplate` (name, required fields, pattern). Non-USSD actions are simple forms.

## 5) Multiple Operators
- Real life: An agent works with multiple providers (Mobile Money A, B, C) and also Western Union.
- App Flow: The agent creates `Operator` records; for USSD, configures templates; for non-USSD, configures form actions only.

## 6) Dealers and Agents — Business Model
- Real life: Agents buy virtual credit from dealers. Physical cash moves from agent to dealer; virtual credit moves from dealer to agent.
- App Flow: Record a buy/sell operation of virtual credit: mirrored entries on both parties’ ledgers and cash registers. Optional receipt proof (photo) can be attached.

## 7) Cash Registers and Virtual Credit per Operator
- Real life: Each party maintains a cash register and per-operator virtual accounts.
- App Flow: Open/close cash register; record entries/exits; balances are computed. `VirtualLedgerEntry` derives virtual balances.

## 8) Transaction Flows (Examples)
- Deposit at Agent: Virtual moves Agent → Customer; Cash enters Agent’s register.
- Withdrawal at Agent: Virtual moves Customer → Agent; Cash exits Agent’s register.
- Transfer: similar to deposit but with fees; who pays fees is configurable.

## 9) Refunds and Reversals
- Real life: Wrong amount; the agent must cancel or refund.
- App Flow: Inversion action creates opposite entries; may require admin permissions and justification.

## 10) Licensing System
- Real life: Only licensed users may use the app.
- App Flow: On login, the app checks `License` in Firebase (assigned to user/device). Offline mode uses a cached license with expiration; if expired → restricted access until renewal/sync.

## 11) Admin Web Panel
- Real life: Central supervision of users, balances, transactions, and licenses.
- App Flow: CRUD for agents/dealers/operators, balance adjustments (with audit), reporting and export.

## 12) Multilingual Support
- Real life: Agents may prefer French; developers/admins use English.
- App Flow: strings resources for EN and FR; default EN for development. Web panel can be bilingual.

---

## Real Process vs App Flow (Summary)
- Manual USSD dialing → Dynamic templates with guided fields and preview.
- Paper KYC → PDF417 scan with instant search.
- Separate ledgers → Mirrored entries with audit trails.
- Network dependency → Offline-first with robust sync.
- Weak control → Licensing, roles, and audit logs.
