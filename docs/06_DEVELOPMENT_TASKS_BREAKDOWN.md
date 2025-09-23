# Development Tasks Breakdown - Mobile Banking Agent App

This document breaks down all features into specific development tasks with clear descriptions, requirements, and implementation details.

## **ROLE-BASED FEATURE ACCESS**

### **🏢 DEALER FEATURES (Full Access)**
- **License Management**: Validate license only (Admin creates licenses)
- **Agent Management**: Manage assigned agents (virtual accounts, balances)
- **Customer KYC**: Full CRUD operations for customer data
- **Cash Register**: Manage physical cash register (open/close daily)
- **Virtual Accounts**: Multiple operators, buy/sell virtual credit
- **Sell Operations**: Sell virtual credit to agents (virtual->cash)
- **Transaction Processing**: All USSD and non-USSD operations
- **Operator Management**: Add/manage operators and their USSD actions
- **Reporting**: Full access to their network's reports

### **👤 AGENT FEATURES (Limited Access)**
- **License Management**: Validate license only (Admin creates licenses)
- **Customer KYC**: Register customers they serve
- **Cash Register**: Basic cash register management (open/close daily)
- **Virtual Accounts**: Limited to assigned operators
- **Buy Operations**: Buy virtual credit from dealer (cash->virtual)
- **Transaction Processing**: USSD & non-USSD for their customers
- **Operator Management**: Add operators and customize USSD actions
- **Reporting**: Limited to their own transaction reports

### **🌐 ADMIN FEATURES (Web Portal Only)**
- **License Creation**: Create, assign, revoke licenses
- **User Management**: Create dealers and agents, assign relationships
- **System Monitoring**: View system health and analytics
- **Global Operator Management**: Manage system-wide operators
- **Balance Adjustments**: Correct cash/virtual balances
- **Data Export**: Export all system data
- **Audit Trail**: View all system activities

### **KEY BUSINESS RULES:**
1. **User Creation**: Only Admin creates users (no signup in mobile app)
2. **Dealer-Agent Relationship**: Admin assigns agents to dealers
3. **Virtual Credit Flow**: Agent buys from Dealer (cash→virtual), Customer transactions use virtual credit
4. **Operators**: Each has specific USSD actions (deposit, withdrawal, transfer, bills, etc.)
5. **Cash Registers**: Both dealers and agents maintain separate cash registers
6. **USSD Actions**: Based on operator templates, customizable per user

---

## Phase 1: Foundation & Authentication

### Task 1.1: Project Setup & Dependencies
**Description:** Set up Android project with all required dependencies and configurations.

**Requirements:**
- Android Studio project with Java
- Firebase integration (Auth, Firestore, Storage)
- Room database for offline storage
- ML Kit for barcode scanning
- WorkManager for background sync

**Implementation Details:**
- Configure `build.gradle.kts` with all dependencies
- Add `google-services.json` to app folder
- Set up Firebase project with Authentication, Firestore, Storage
- Configure Firestore and Storage security rules
- Set up Room database with entities and DAOs

**Acceptance Criteria:**
- Project builds without errors
- Firebase connection established
- Basic authentication working
- Room database accessible

---

### Task 1.2: User Authentication & Roles
**Description:** Implement user authentication with role-based access (Dealer, Agent only).
**👥 Access:** All mobile users (Dealer, Agent) - Login only, no signup

**Requirements:**
- Email/Password authentication (login only)
- Role validation (dealer, agent)
- Dealer-Agent relationship validation
- Login/logout functionality
- Session management

**Implementation Details:**
- Create `User` entity with role and dealerId fields
- Implement Firebase Auth with email/password (login only)
- Validate user role and dealer-agent relationships
- Create login screen (NO SIGNUP)
- Implement role-based navigation

**Acceptance Criteria:**
- Only existing users can login (created by Admin)
- Roles are properly validated
- Agent-Dealer relationships are enforced
- Navigation changes based on user role
- Session persists across app restarts

**Note:** User creation is Admin-only via web portal. Mobile app only handles login.

---

### Task 1.3: License Validation System (Mobile App Only)
**Description:** Implement license validation system for mobile app access control.
**👥 Access:** All mobile users (Dealer, Agent) - validation only

**Requirements:**
- License validation on app startup
- License expiration handling
- Offline license caching
- License status display
- Graceful handling of invalid/expired licenses

**Implementation Details:**
- Create `LicenseCache` entity for local storage
- Implement license validation in `MainActivity`
- Cache license locally with expiration check
- Display license status to user
- Handle license expiration gracefully

**Acceptance Criteria:**
- App blocks access without valid license
- License validation works offline
- Expired licenses are handled properly
- User sees clear license status
- App doesn't crash on license issues

**Note:** License creation/assignment is handled in Web Admin Portal, not in mobile app.

---

## Phase 2: Core Data Models

### Task 2.1: Customer Management (KYC)
**Description:** Implement customer registration with PDF417 barcode scanning.
**👥 Access:** 🏢 Dealers (full CRUD) | 👤 Agents (limited to their customers)

**Requirements:**
- PDF417 barcode scanning from national ID
- Customer form with fields: full name, date of birth, national ID number, issue date, expiry date
- Offline storage with sync capability
- Customer search functionality
- Data validation

**Implementation Details:**
- Create `Customer` entity with all required fields
- Implement ML Kit barcode scanning for PDF417
- Create customer registration form
- Implement customer search with filters
- Add data validation and error handling

**Acceptance Criteria:**
- PDF417 scanning extracts customer data automatically
- Manual data entry works when scanning fails
- Customer data is stored offline and synced
- Search functionality works efficiently
- Data validation prevents invalid entries

---

### Task 2.2: Operator Management
**Description:** Allow users to add and manage money transfer operators.
**👥 Access:** 🏢 Dealers & 👤 Agents (can add operators they work with)

**Requirements:**
- Add USSD operators (mobile money: MTN, Orange, Airtel, etc.)
- Add Non-USSD operators (traditional: Western Union, MoneyGram, Ria, etc.)
- Operator status management (active/inactive)
- Each operator has multiple actions (deposit, withdrawal, transfer, bills, etc.)

**Implementation Details:**
- Create `Operator` entity with fields: `name`, `type`, `active`, `addedBy`
- Create operator management screens
- Support both USSD and traditional operators
- Link operators to their actions
- Sync operators with Firestore

**Acceptance Criteria:**
- Users can add USSD operators (for mobile banking)
- Users can add Non-USSD operators (for traditional transfers)
- Operator types are properly categorized
- Operators can be activated/deactivated
- Only active operators appear in transactions

**Business Rule:** Both dealers and agents can add operators they work with

---

### Task 2.3: Operator Actions Management
**Description:** Allow users to create and manage actions for each operator.
**👥 Access:** 🏢 Dealers & 👤 Agents (customize actions for their operators)

**Requirements:**
- **USSD Actions**: deposit, withdrawal, transfer, bills (electricity, water, mobile top-up)
- **Non-USSD Actions**: form-based data collection only
- USSD template management with placeholders: `{number}`, `{amount}`
- Customizable action templates per operator
- Default actions: deposit, withdrawal, transfer

**Implementation Details:**
- Create `OperatorAction` entity: `operatorId`, `name`, `type`, `ussdTemplate`, `requiredFields[]`
- USSD template example: `*144*2*1*{number}*{amount}#`
- Implement action creation/editing screens
- Template validation and USSD preview
- Default actions for mobile money operators

**Acceptance Criteria:**
- Default actions created: deposit, withdrawal, transfer
- Users can add custom actions (bills, top-up, etc.)
- USSD templates support placeholders
- Template preview shows final USSD code
- Non-USSD actions only collect data (no USSD)

**Business Rule:** Actions are operator-specific and customizable per user

---

## Phase 3: Financial Management

### Task 3.1: Cash Register Management
**Description:** Implement cash register functionality for agents and dealers.
**👥 Access:** 🏢 Dealers (full management) | 👤 Agents (limited to their transactions)

**Requirements:**
- Open/close cash register daily
- Track cash movements (in/out)
- Calculate running balance
- Cash register reports
- Multiple cash registers per user

**Implementation Details:**
- Create `CashRegister` entity with fields: `ownerType`, `ownerId`, `openedAt`, `closedAt`, `openingBalance`
- Create `CashMovement` entity with fields: `cashRegisterId`, `type`, `amount`, `refType`, `refId`, `createdAt`
- Implement cash register opening/closing screens
- Create cash movement tracking
- Add balance calculation and reporting

**Acceptance Criteria:**
- Users can open/close cash registers
- All cash movements are tracked
- Running balance is calculated correctly
- Cash register reports are accurate
- Multiple registers are supported

---

### Task 3.2: Virtual Account Management
**Description:** Implement virtual credit accounts for each operator.
**👥 Access:** 🏢 Dealers (all operators) | 👤 Agents (assigned operators only)

**Requirements:**
- Create virtual accounts per operator per user
- Track virtual credit movements
- Calculate virtual balances
- Account statements
- Transfer virtual credit between users

**Implementation Details:**
- Create `Account` entity with fields: `ownerType`, `ownerId`, `operatorId`
- Create `VirtualLedgerEntry` entity with fields: `accountId`, `delta`, `reason`, `refType`, `refId`, `createdAt`
- Implement account creation and management
- Create ledger entry tracking
- Add balance calculation and statements

**Acceptance Criteria:**
- Virtual accounts are created per operator
- All virtual movements are tracked
- Balances are calculated correctly
- Account statements are available
- Transfers between users work properly

---

### Task 3.3: Buy/Sell Virtual Credit
**Description:** Implement virtual credit trading between dealers and agents.
**👥 Access:** 🏢 Dealers (sell to agents) | 👤 Agents (buy from dealer only)

**Requirements:**
- Agent buys virtual credit from dealer
- Dealer sells virtual credit to agent
- Record both cash and virtual movements
- Generate receipts
- Track transaction history

**Implementation Details:**
- Create transaction types for buy/sell operations
- Implement dual-entry accounting (cash + virtual)
- Create buy/sell transaction screens
- Add receipt generation
- Implement transaction history tracking

**Acceptance Criteria:**
- Buy/sell operations update both cash and virtual accounts
- Receipts are generated and stored
- Transaction history is complete
- Balances are updated correctly
- Error handling prevents invalid operations

---

## Phase 4: Transaction Processing

### Task 4.1: USSD Transaction Execution
**Description:** Implement USSD transaction execution for mobile money operations.
**👥 Access:** 🏢 Dealers (all operations) | 👤 Agents (limited to their customer transactions)

**Requirements:**
- Generate USSD codes from templates
- Launch USSD dialer with pre-filled codes
- Handle USSD execution results
- Record transaction details
- Support multiple USSD formats

**Implementation Details:**
- Create USSD code generation from templates
- Implement `Intent.ACTION_DIAL` for USSD launching
- Create transaction recording system
- Add USSD result handling
- Implement template validation

**Acceptance Criteria:**
- USSD codes are generated correctly from templates
- Dialer launches with pre-filled codes
- Transactions are recorded after USSD execution
- Multiple USSD formats are supported
- Error handling for failed USSD calls

---

### Task 4.2: Transaction Management
**Description:** Implement comprehensive transaction management system.
**👥 Access:** 🏢 Dealers (full access) | 👤 Agents (their transactions only)

**Requirements:**
- Record deposits, withdrawals, transfers
- Track transaction status
- Handle transaction fees
- Generate transaction receipts
- Transaction history and reporting

**Implementation Details:**
- Create `Transaction` entity with all required fields
- Implement transaction creation and recording
- Add status tracking (pending, completed, failed)
- Create receipt generation system
- Implement transaction history and reporting

**Acceptance Criteria:**
- All transaction types are supported
- Transaction status is tracked properly
- Fees are calculated and recorded
- Receipts are generated and stored
- History and reporting are accurate

---

### Task 4.3: Non-USSD Operations
**Description:** Implement form-based operations for traditional money transfer services.
**👥 Access:** 🏢 Dealers (all operations) | 👤 Agents (limited to their customers)

**Requirements:**
- Create forms for non-USSD operators
- Validate form data
- Record operations without USSD
- Generate receipts
- Track operation status

**Implementation Details:**
- Create dynamic form generation for non-USSD actions
- Implement form validation
- Create operation recording system
- Add receipt generation
- Implement status tracking

**Acceptance Criteria:**
- Forms are generated dynamically
- Data validation works properly
- Operations are recorded correctly
- Receipts are generated
- Status tracking is accurate

---

## Phase 5: Offline-First & Sync

### Task 5.1: Offline Data Storage
**Description:** Implement comprehensive offline data storage using Room database.

**Requirements:**
- Store all data locally first
- Implement data relationships
- Handle data integrity
- Optimize database performance
- Implement data encryption

**Implementation Details:**
- Create all Room entities and DAOs
- Implement database relationships
- Add data validation and constraints
- Implement database encryption
- Optimize queries and indexes

**Acceptance Criteria:**
- All data is stored locally
- Data relationships are maintained
- Database performance is optimized
- Data integrity is ensured
- Encryption is implemented

---

### Task 5.2: Background Sync System
**Description:** Implement background synchronization with Firebase.

**Requirements:**
- Sync data when connectivity is available
- Handle sync conflicts
- Implement retry mechanisms
- Track sync status
- Handle large data transfers

**Implementation Details:**
- Create `SyncQueue` entity for pending operations
- Implement WorkManager for background sync
- Add conflict resolution strategies
- Implement retry with exponential backoff
- Create sync status tracking

**Acceptance Criteria:**
- Data syncs automatically when online
- Conflicts are resolved properly
- Retry mechanisms work reliably
- Sync status is tracked
- Large data transfers are handled

---

### Task 5.3: Conflict Resolution
**Description:** Implement conflict resolution strategies for data synchronization.

**Requirements:**
- Handle concurrent modifications
- Implement last-write-wins strategy
- Handle domain-specific conflicts
- Maintain data consistency
- Audit conflict resolutions

**Implementation Details:**
- Implement timestamp-based conflict detection
- Create domain-specific conflict resolution rules
- Add conflict resolution logging
- Implement data consistency checks
- Create conflict resolution audit trail

**Acceptance Criteria:**
- Conflicts are detected and resolved
- Data consistency is maintained
- Resolution strategies are appropriate
- Audit trail is complete
- Performance is not impacted

---

## Phase 6: Advanced Features

### Task 6.1: Barcode Scanning Enhancement
**Description:** Enhance PDF417 scanning with better accuracy and error handling.

**Requirements:**
- Improve scanning accuracy
- Handle different ID card formats
- Add manual data entry fallback
- Implement scanning validation
- Add scanning history

**Implementation Details:**
- Optimize ML Kit scanning parameters
- Implement multiple scanning attempts
- Add manual data entry screens
- Create data validation for scanned data
- Implement scanning history tracking

**Acceptance Criteria:**
- Scanning accuracy is improved
- Different ID formats are supported
- Manual entry works as fallback
- Data validation prevents errors
- Scanning history is maintained

---

### Task 6.2: Reporting & Analytics
**Description:** Implement comprehensive reporting and analytics features.

**Requirements:**
- Generate transaction reports
- Create cash flow reports
- Implement user activity analytics
- Add export functionality
- Create dashboard views

**Implementation Details:**
- Create report generation system
- Implement data aggregation
- Add export to PDF/Excel
- Create dashboard with charts
- Implement report scheduling

**Acceptance Criteria:**
- Reports are generated accurately
- Data aggregation is correct
- Export functionality works
- Dashboard displays relevant data
- Reports can be scheduled

---

### Task 6.3: Admin Web Portal (Separate Project)
**Description:** Create web-based admin portal for system management (separate from mobile app).

**Requirements:**
- Admin authentication and authorization
- User management (create dealers/agents)
- License management (create, assign, revoke)
- System monitoring and analytics
- Data export and audit trail
- Balance adjustments and corrections

**Implementation Details:**
- Create web application (React/Next.js or Java Spring Boot)
- Implement admin authentication with role-based access
- Create user management screens (CRUD for dealers/agents)
- Add license management interface (create, assign, revoke licenses)
- Create system monitoring dashboard with real-time data
- Implement audit trail viewer and data export
- Add balance adjustment tools for admins

**Acceptance Criteria:**
- Admin portal is fully functional and secure
- User management works properly (create/edit/delete dealers/agents)
- License management is complete (create/assign/revoke)
- System monitoring shows accurate real-time data
- Audit trail is accessible and exportable
- Balance adjustments work correctly

**Note:** This is a completely separate project from the mobile app. Admins use this web portal to manage the system, while dealers/agents use the mobile app for daily operations.

---

## Phase 7: Testing & Deployment

### Task 7.1: Unit Testing
**Description:** Implement comprehensive unit testing for all components.

**Requirements:**
- Test all business logic
- Test data models
- Test utility functions
- Achieve high test coverage
- Test error scenarios

**Implementation Details:**
- Create unit tests for all classes
- Test Room database operations
- Test Firebase operations
- Test business logic functions
- Test error handling

**Acceptance Criteria:**
- Test coverage is above 80%
- All critical paths are tested
- Error scenarios are covered
- Tests are maintainable
- CI/CD integration works

---

### Task 7.2: Integration Testing
**Description:** Implement integration testing for system components.

**Requirements:**
- Test Firebase integration
- Test offline/online scenarios
- Test data synchronization
- Test user workflows
- Test error recovery

**Implementation Details:**
- Create integration test suite
- Test Firebase operations
- Test offline/online transitions
- Test data sync scenarios
- Test complete user workflows

**Acceptance Criteria:**
- Integration tests cover all scenarios
- Firebase operations are tested
- Offline/online transitions work
- Data sync is tested
- User workflows are validated

---

### Task 7.3: Performance Optimization
**Description:** Optimize app performance and resource usage.

**Requirements:**
- Optimize database queries
- Reduce memory usage
- Improve app startup time
- Optimize network usage
- Handle large datasets

**Implementation Details:**
- Profile app performance
- Optimize database queries
- Implement lazy loading
- Optimize image handling
- Implement data pagination

**Acceptance Criteria:**
- App startup time is under 3 seconds
- Memory usage is optimized
- Database queries are efficient
- Network usage is minimized
- Large datasets are handled properly

---

## Development Priority Order:

### **MOBILE APP (Android) - For Dealers & Agents:**
1. **Phase 1:** Foundation & Authentication (Tasks 1.1-1.2) - License validation only, no license creation
2. **Phase 2:** Core Data Models (Tasks 2.1-2.3) - Customer KYC, operators, actions
3. **Phase 3:** Financial Management (Tasks 3.1-3.3) - Cash registers, virtual accounts, buy/sell
4. **Phase 4:** Transaction Processing (Tasks 4.1-4.3) - USSD execution, transaction management
5. **Phase 5:** Offline-First & Sync (Tasks 5.1-5.3) - Local storage, background sync, conflicts
6. **Phase 6:** Advanced Features (Tasks 6.1-6.2) - Enhanced scanning, reporting
7. **Phase 7:** Testing & Deployment (Tasks 7.1-7.3) - Unit tests, integration tests, optimization

### **WEB ADMIN PORTAL - For Admins Only:**
1. **Phase 1:** Admin Authentication & User Management
2. **Phase 2:** License Management (Create, Assign, Revoke)
3. **Phase 3:** System Monitoring & Analytics
4. **Phase 4:** Data Export & Audit Trail
5. **Phase 5:** Balance Adjustments & Corrections

### **Key Differences:**
- **Mobile App:** Dealers/Agents use for daily operations, license validation only
- **Web Portal:** Admins use for system management, license creation, user management
- **License Flow:** Admin creates license → assigns to user → mobile app validates license
- **No License Creation:** Mobile app cannot create licenses, only validates them

Each phase should be completed and tested before moving to the next phase. This ensures a solid foundation and prevents integration issues later in development.

---

## **DEVELOPMENT IMPLEMENTATION SUMMARY BY ROLE**

### **🏢 DEALER IMPLEMENTATION PRIORITY:**
1. **Phase 1:** Authentication + License validation
2. **Phase 2:** Full customer KYC management
3. **Phase 3:** Complete cash register + virtual accounts + sell operations
4. **Phase 4:** All transaction processing capabilities
5. **Phase 5:** Full offline sync capabilities
6. **Phase 6:** Complete reporting and analytics
7. **Phase 7:** Agent management features

### **👤 AGENT IMPLEMENTATION PRIORITY:**
1. **Phase 1:** Authentication + License validation
2. **Phase 2:** Limited customer KYC (their customers only)
3. **Phase 3:** Basic cash register + limited virtual accounts + buy operations
4. **Phase 4:** Limited transaction processing (their customers only)
5. **Phase 5:** Basic offline sync
6. **Phase 6:** Limited reporting (their data only)
7. **Phase 7:** Basic profile management

### **🔒 ROLE-BASED RESTRICTIONS TO IMPLEMENT:**

#### **Database-Level Security:**
- Firestore security rules to restrict data access by role
- Agents can only access their assigned customers
- Agents can only see their own transactions
- Dealers can manage agents under their hierarchy

#### **UI-Level Restrictions:**
- Hide/show menu items based on user role
- Disable buttons for unauthorized operations
- Show different dashboards for dealers vs agents
- Display role-appropriate statistics

#### **Business Logic Restrictions:**
- Validate user permissions before operations
- Restrict operator access based on assignments
- Limit transaction amounts based on role
- Control virtual credit limits per role

