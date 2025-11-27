# Commission Features - Dry Run Guide

## 🎯 Where to Access Commission Features

### **1. Commission Configuration**
**Location:** Dealer Dashboard & Agent Dashboard

**Steps:**
1. Login as **Dealer** or **Agent**
2. On the Dashboard, scroll down to **Operations** section
3. You'll see a new row with two buttons:
   - **"Commission Configuration"** (with edit note icon)
   - **"Commission Reports"** (with dashboard icon)
4. Tap on **"Commission Configuration"**

**What you can do:**
- ✅ Set commission rates for each operator
- ✅ Set tax rates (e.g., 15%)
- ✅ Select transaction types (Deposit, Withdrawal, or both)
- ✅ See real-time calculation of commission with tax
- ✅ Edit or delete existing commission rates

**Example:**
- Operator: "Orange Money"
- Commission Rate: 0.4%
- Tax Rate: 15%
- Transaction Types: Deposit & Withdrawal
- **Result:** Commission with tax = 0.46% (0.4% × 1.15)

---

### **2. Commission Reports**
**Location:** Dealer Dashboard & Agent Dashboard

**Steps:**
1. Login as **Dealer** or **Agent**
2. On the Dashboard, scroll to **Operations** section
3. Tap on **"Commission Reports"**

**What you can do:**
- ✅ View **Daily Commission** reports
- ✅ View **Monthly Commission** reports
- ✅ View **Yearly Commission** reports
- ✅ See breakdown:
  - Commission without tax
  - Tax amount
  - Total commission (with tax)
- ✅ Select specific month and year

**Report Types:**
- **Daily:** Shows commission for a specific day
- **Monthly:** Shows commission for a specific month
- **Yearly:** Shows commission for a specific year

---

### **3. Automatic Commission Calculation**
**Location:** Process Transaction Activity

**Steps:**
1. Go to **"Process Transactions"** from Dashboard
2. Complete a transaction (Deposit or Withdrawal)
3. Commission is **automatically calculated** and saved
4. Commission is based on:
   - Operator selected
   - Commission rate configured for that operator
   - Tax rate configured
   - Transaction amount

**Note:** Commission is only calculated if:
- Commission rate is configured for the operator
- Transaction is successful
- Transaction type matches (Deposit/Withdrawal)

---

## 📱 Navigation Path

### **For Dealer:**
```
Login → Dealer Dashboard → Operations Section → 
  ├─ Commission Configuration
  └─ Commission Reports
```

### **For Agent:**
```
Login → Agent Dashboard → Scroll Down → 
  ├─ Commission Configuration (Card)
  └─ Commission Reports (Card)
```

---

## 🔄 Complete Flow Example

### **Step 1: Configure Commission Rate**
1. Open **Commission Configuration**
2. Tap **"Set Commission Rate"**
3. Select Operator: "Orange Money"
4. Enter Commission Rate: `0.4`
5. Enter Tax Rate: `15`
6. Check: Deposit & Withdrawal
7. Tap **Save**

### **Step 2: Process Transaction**
1. Go to **Process Transactions**
2. Select Customer
3. Select Operator: "Orange Money"
4. Select Type: Deposit
5. Enter Amount: `10000`
6. Complete transaction
7. Commission is automatically calculated:
   - Commission (0.4%): 40
   - Tax (15%): 6
   - Total Commission: 46

### **Step 3: View Commission Report**
1. Open **Commission Reports**
2. Select Period: **Daily**
3. Select Month: Current month
4. Select Year: Current year
5. Tap **Generate Report**
6. See your commission breakdown

---

## 🎨 UI Locations

### **Dealer Dashboard:**
- **Row 1:** Customers | Cash Register | Buy Credit
- **Row 2:** Process Transactions | Manage Operators
- **Row 3:** ⭐ **Commission Configuration** | ⭐ **Commission Reports** (NEW!)

### **Agent Dashboard:**
- Stats Cards (Top)
- Operations Grid (Middle)
- ⭐ **Commission Configuration** (Card) (NEW!)
- ⭐ **Commission Reports** (Card) (NEW!)
- Logout Button (Bottom)

---

## ✅ Testing Checklist

- [ ] Can access Commission Configuration from Dashboard
- [ ] Can add new commission rate for an operator
- [ ] Can see commission with tax calculation
- [ ] Can edit existing commission rate
- [ ] Can delete commission rate
- [ ] Can access Commission Reports from Dashboard
- [ ] Can generate daily report
- [ ] Can generate monthly report
- [ ] Can generate yearly report
- [ ] Commission is calculated automatically on transaction
- [ ] Commission appears in reports after transaction

---

## 🚀 Quick Start

1. **Login** to the app
2. **Navigate** to Dashboard
3. **Scroll** to Operations section
4. **Tap** "Commission Configuration" to set rates
5. **Tap** "Commission Reports" to view earnings
6. **Process** a transaction to see automatic commission calculation

---

**Note:** Make sure you have at least one operator configured before setting commission rates!





