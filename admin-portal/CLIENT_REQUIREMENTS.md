# Client Requirements Document

**Date:** Current
**Status:** New Requirements - Pending Implementation

---

## 1. Add Users

### Issues to Fix:

- **Scanner not working** - The scanner functionality needs to be fixed
- **Date of Birth Format** - Must be in DD-MM-YYYY format with automatic dashes
- **Field Order** - Date of birth field must be placed **after** the identity document fields
- **Identity Document Type** - Add a field for document type with default options:
  - CNI
  - PASSPORT
  - CNI AES
  - CNI CEDEAO
  - PASSPORT ETRANGER
  - CARTE CONSULAIRE

### New Feature:

- **Add Customers from Transactions Page** - Allow customers to be added directly from the transactions page

---

## 2. Operators

### New Feature:

- **Disable USSD for Operator Actions** - Allow USSD to be disabled for certain operator actions
  - **Reason:** Some actions (like withdrawals) are initiated by the customer and only require verification by the operator
  - **Requirement:** USSD should not be necessary for these verification-only actions

---

## 3. Purchase of Credit / Purchase of Cash

### Core Principle:

- **Independence:** Agents and dealers must be independent
  - Agents can add their own credits independently
  - Dealers can add their own credits independently
  - Even if a dealer has agents, they operate independently
  - Dealers can only **view** their agents' transactions in the dashboard
  - If an agent doesn't belong to a dealer, only that agent can access their transactions

### Balance Management:

#### Purchase of Credit:

- **Operator balance:** Credited (increased)
- **Cash balance:** Debited (decreased)

#### Purchase of Cash:

- **Operator balance:** Debited (decreased)
- **Cash balance:** Credited (increased)

### New Feature - Checkboxes:

- **Purchase Credit without Deducting Cash Balance**

  - Checkbox option to add new funds to the circuit
  - Allows credit purchase without affecting cash balance
- **Purchase Cash without Deducting Operator Balance**

  - Checkbox option to add new funds to the circuit
  - Allows cash purchase without affecting operator balance

---

## 4. Transactions

### New Features:

#### Transaction Cancellation:

- **Allow transactions to be canceled**
- **Balance Reversal:** When a transaction is canceled:
  - Funds must be returned to the operator's balance OR
  - Funds must be returned to the cash register
  - **Rule:** The consequences of the transaction must be completely reversed based on transaction type

#### Balance Adjustment:

- **Adjust Cash Register Balance** - Allow manual adjustment of cash register balance
- **Adjust Operator Balance** - Allow manual adjustment of operator balance
- **Reason:** Agents may forget to enter certain transactions, causing discrepancies between app balances and reality
- **Requirement:** Must maintain a **history of all adjustments** for audit purposes

#### Transaction Search:

- **Enable transaction search in the app** - Add search functionality to find transactions

---

## 5. General Updates

### Currency Symbol:

- **Current:** Unknown/Incorrect
- **Required:** `F.` (Franc symbol)
- **Action:** Correct this in both the mobile app and the dashboard

### License Expiration Date:

- **Display license expiration date on the application** - Show when the license expires

### Dashboard Access:

- **Question:** How can agents and dealers log in to the dashboard?
- **Action:** Provide documentation/instructions for dashboard login

---

## 6. License Management

### New Features:

#### Agent Count Control:

- **Add option to control the number of agents allowed for a license**
- Set maximum agent limit per license

#### Monthly Licenses:

- **Add support for monthly licenses** - Currently only annual licenses may be supported
- Implement monthly license type with appropriate billing/expiration logic

---

## 7. Commission System

### Requirements:

#### Commission Structure:

- Agents and dealers receive commission as remuneration for each transaction
- **Transaction Types:** Deposit, Withdrawal
- **Commission Rate Variables:**
  - Varies from dealer to dealer
  - Varies from agent to agent
  - Varies depending on the operators

#### Commission Management:

- **Agent Configuration:** Agents must be able to add commission percentage based on commission rates
- **System Calculation:** System should calculate commission per operator per:
  - Day
  - Month
  - Year

#### Implementation Scope:

- Commission rate configuration per agent/dealer
- Commission rate configuration per operator
- Automatic commission calculation on transactions
- Commission reporting (daily, monthly, yearly)
- Commission history tracking

---

## Questions for Clarification

1. **Dashboard Login:** What authentication method should agents/dealers use for dashboard access?
2. **Commission Rates:** Should commission rates be set per transaction type (deposit vs withdrawal) or per operator only?
3. **Balance Adjustment:** Who has permission to adjust balances? Only admins or also dealers/agents?
