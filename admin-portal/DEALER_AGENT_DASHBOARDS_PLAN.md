# Dealer and Agent Dashboards Implementation Plan

## Overview
This document outlines the implementation plan for dealer and agent dashboards in the admin portal.

## Authentication Updates
✅ **COMPLETED:**
- Updated `AuthContext` to support dealer and agent roles (not just admin)
- Updated login page to redirect based on role:
  - Admin → `/dashboard`
  - Dealer → `/dealer/dashboard`
  - Agent → `/agent/dashboard`

## Routes Structure
- `/dashboard` - Admin dashboard (existing, unchanged)
- `/dealer/dashboard` - Dealer dashboard (NEW)
- `/agent/dashboard` - Agent dashboard (NEW)

## Dealer Dashboard Features

### 1. View Transactions
- View own transactions
- View transactions from all affiliated agents (agents with `dealerId` matching dealer's `uid`)
- Filter by date range, status, operator, action
- Export to Excel

### 2. View Customers
- View own customers
- View customers from all affiliated agents
- Filter by name, phone, date added
- Export to Excel

### 3. View Commissions
- View own commissions per period (month/year)
- View commissions for all affiliated agents per period
- Filter by period (month/year), agent
- Export to Excel

### 4. Add Agents
- Add new agents (limited by license `maxAgentCount`)
- Form: name, email, password
- Check current agent count vs license limit
- Assign to dealer (set `dealerId` to dealer's `uid`)

### 5. Export Data
- Export transactions to Excel
- Export customers to Excel
- Export commissions to Excel

## Agent Dashboard Features

### 1. View Transactions
- View own transactions only
- Filter by date range, status, operator, action
- Export to Excel

### 2. View Customers
- View own customers only
- Filter by name, phone, date added
- Export to Excel

### 3. View Commissions
- View own commissions per period (month/year)
- Filter by period (month/year)
- Export to Excel

### 4. Export Data
- Export transactions to Excel
- Export customers to Excel
- Export commissions to Excel

## Admin Dashboard Updates
- Add export functionality to all tabs (transactions, customers, etc.)

## Implementation Steps

1. ✅ Update AuthContext for dealer/agent support
2. ✅ Update login redirect logic
3. Create dealer dashboard route and layout
4. Create agent dashboard route and layout
5. Create reusable filtered components:
   - `FilteredTransactionsTab` - with role-based filtering
   - `FilteredCustomersTab` - with role-based filtering
   - `FilteredCommissionsTab` - with role-based filtering
6. Create export utility (`lib/exportUtils.ts`)
7. Create "Add Agents" component for dealer dashboard
8. Add export buttons to all dashboards

## Database Queries Needed

### For Dealer:
```typescript
// Get affiliated agents
const agentsQuery = query(
  collection(db, 'users'),
  where('role', '==', 'agent'),
  where('dealerId', '==', dealerUid)
);

// Get dealer + agent transactions
const agentIds = [dealerUid, ...affiliatedAgentIds];
const transactionsQuery = query(
  collection(db, 'transactions'),
  where('userId', 'in', agentIds) // Note: 'in' supports max 10 items
);
// If > 10 agents, need multiple queries and merge

// Get dealer + agent customers
const customersQuery = query(
  collection(db, 'customers'),
  where('addedBy', 'in', agentIds)
);
```

### For Agent:
```typescript
// Get own transactions only
const transactionsQuery = query(
  collection(db, 'transactions'),
  where('userId', '==', agentUid)
);

// Get own customers only
const customersQuery = query(
  collection(db, 'customers'),
  where('addedBy', '==', agentUid)
);
```

## Export Format

Excel files should include:
- Transactions: Date, Customer, Operator, Action, Amount, Status, Agent
- Customers: Name, Phone, Address, Date Added, Added By
- Commissions: Period, Agent Name, Transaction Count, Total Commission


