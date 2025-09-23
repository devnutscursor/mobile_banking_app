# User Operations Guide — Dealers, Agents, Operators, USSD (English)

This guide explains step-by-step what each user can do, including USSD and non-USSD examples.

---

## A) Create and Manage Operators
1. Go to: Settings → Operators → New
2. Enter: Name (e.g., Mobile Money A, Western Union, MoneyGram, Ria)
3. Type: `USSD` or `Non-USSD`
4. Save

Operators are the providers you transact with.

---

## B) Actions per Operator
- USSD: create an Action (e.g., Deposit) with a template:
  - Action name
  - Required fields (e.g., number, amount)
  - USSD template: `*144*2*1*{number}*{amount}#`
- Non-USSD: create a form-only Action (e.g., Western Union — Payment) with required fields, no USSD dialing.

Each operator can have multiple actions. Templates are editable by the user.

---

## C) Create Users (Admin Web)
- Admin creates Dealer and Agent accounts (user profile + role)
- Assign licenses and allowed operators

Only admins can create users and assign licenses.

---

## D) Declare Virtual Accounts (per operator)
- Dealer/Agent → Accounts → Add → Select operator
- Virtual balance is derived from ledger entries (transactions, buy/sell credit)

---

## E) Open Cash Register
- Dealer/Agent → Cash → Open → Enter opening cash balance
- Any cash in/out updates the register

---

## F) Buy/Sell Virtual Credit (Dealer ↔ Agent)
- Agent buys from Dealer:
  - Agent hands cash to Dealer
  - App: record "Buy Credit" (operator, amount)
  - Effects: Agent (+virtual, -cash), Dealer (-virtual, +cash)
- Dealer sells to Agent: inverse operation

Always record both sides for accurate balances.

---

## G) KYC — Register Customer
- Customers → New → Scan PDF417
- Review and complete: Full name, Date of birth, National ID number, Issue/Expiry dates
- Save (works offline)

---

## H) USSD Transactions (Agent)
- Deposit example:
  - Choose operator (USSD) → Action "Deposit"
  - Enter: Customer number (e.g., 0999123456), Amount (e.g., 100)
  - Preview USSD: `*144*2*1*0999123456*100#`
  - Launch → device dialer opens → enter 4-digit password
- Withdrawal: similar, using the corresponding action/template
- Transfer: enter destination number and amount; apply fees per configuration

Notes:
- `#` is encoded automatically; confirmation happens in the phone UI.
- If USSD fails, log the incident and retry if needed.

---

## I) Non-USSD Operations (e.g., Western Union)
- Select non-USSD operator → Choose Action (e.g., Payout)
- Fill form: Customer name, Reference number, Amount, etc.
- Confirm → record saved; balances updated if applicable

---

## J) Refunds / Reversals
- Find the transaction → Options → Refund/Reverse
- Confirm and provide justification
- The system creates reversing entries and keeps an audit trail

---

## K) Synchronization
- The app works offline; actions are queued locally
- On connectivity, sync runs automatically
- Conflicts are resolved per rules; errors are shown when user attention is needed

---

## L) Licenses
- App access requires a valid license
- On expiration: access is restricted until renewal and synchronization
