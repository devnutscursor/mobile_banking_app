package com.example.myapplication.services;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.CustomerEntity;
import com.example.myapplication.database.entities.OperatorActionEntity;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.TransactionEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.database.entities.BalanceAdjustmentEntity;
import com.example.myapplication.database.entities.CommissionRateEntity;
import com.example.myapplication.database.entities.CommissionEntity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive data synchronization service
 * Handles bidirectional sync between local database and Firestore
 */
public class DataSyncService {
    private static final String TAG = "DataSyncService";
    
    private final Context context;
    private final AppDatabase database;
    private final FirebaseFirestore firestore;
    
    public interface SyncCallback {
        void onSyncStarted();
        void onSyncProgress(String message, int current, int total);
        void onSyncComplete(boolean success, String message);
    }
    
    public DataSyncService(Context context) {
        this.context = context;
        this.database = AppDatabase.getDatabase(context);
        this.firestore = FirebaseFirestore.getInstance();
    }
    
    /**
     * Comprehensive sync of all data for a user
     */
    public void syncAllData(String userId, SyncCallback callback) {
        new Thread(() -> {
            try {
                if (callback != null) {
                    callback.onSyncStarted();
                }
                
                Log.d(TAG, "Starting comprehensive sync for user: " + userId);
                
                AtomicInteger progress = new AtomicInteger(0);
                final int totalSteps = 11; // All data types + all users credits + balance adjustments + operator balances + commission rates + commissions + license expiry check
                
                // Step 1: Sync user data
                if (callback != null) {
                    callback.onSyncProgress("Syncing user data...", progress.incrementAndGet(), totalSteps);
                }
                syncUserData(userId);
                
                // Step 2: Sync customers
                if (callback != null) {
                    callback.onSyncProgress("Syncing customers...", progress.incrementAndGet(), totalSteps);
                }
                syncCustomers(userId);
                
                // Step 3: Sync operators
                if (callback != null) {
                    callback.onSyncProgress("Syncing operators...", progress.incrementAndGet(), totalSteps);
                }
                syncOperators(userId);
                
                // Step 4: Sync operator actions
                if (callback != null) {
                    callback.onSyncProgress("Syncing operator actions...", progress.incrementAndGet(), totalSteps);
                }
                syncOperatorActions(userId);
                
                // Step 5: Sync transactions (bidirectional)
                if (callback != null) {
                    callback.onSyncProgress("Syncing transactions...", progress.incrementAndGet(), totalSteps);
                }
                syncTransactionsBidirectional(userId);
                
                // Step 6: Sync credits for all users (dealer + agents) if current user is dealer
                if (callback != null) {
                    callback.onSyncProgress("Syncing credits for all users...", progress.incrementAndGet(), totalSteps);
                }
                syncAllUsersCredits();
                
                // Step 7: Sync balance adjustments
                if (callback != null) {
                    callback.onSyncProgress("Syncing balance adjustments...", progress.incrementAndGet(), totalSteps);
                }
                syncBalanceAdjustments(userId);

                // Step 8: Sync operator balances (push local operator_balances to Firestore)
                if (callback != null) {
                    callback.onSyncProgress("Syncing operator balances...", progress.incrementAndGet(), totalSteps);
                }
                syncOperatorBalances(userId);
                
                // Step 9: Sync commission rates
                if (callback != null) {
                    callback.onSyncProgress("Syncing commission rates...", progress.incrementAndGet(), totalSteps);
                }
                syncCommissionRates(userId);
                
                // Step 10: Sync commissions
                if (callback != null) {
                    callback.onSyncProgress("Syncing commissions...", progress.incrementAndGet(), totalSteps);
                }
                syncCommissions(userId);
                
                // Step 11: Validate license expiry after sync
                if (callback != null) {
                    callback.onSyncProgress("Validating license...", progress.incrementAndGet(), totalSteps);
                }
                checkLicenseExpiry(userId);
                
                Log.d(TAG, "Comprehensive sync completed successfully for user: " + userId);
                if (callback != null) {
                    callback.onSyncComplete(true, "Sync completed successfully");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error during comprehensive sync for user: " + userId, e);
                if (callback != null) {
                    callback.onSyncComplete(false, "Sync failed: " + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * Check if license has expired after sync and logout user if expired
     */
    private void checkLicenseExpiry(String userId) {
        try {
            com.example.myapplication.database.AppDatabase database = 
                com.example.myapplication.database.AppDatabase.getDatabase(context);
            com.example.myapplication.database.entities.LicenseEntity license = 
                database.licenseDao().getLicenseByUserId(userId);
            
            if (license != null && license.isExpired()) {
                Log.w(TAG, "License expired for user: " + userId + " - logging out");
                // Logout user on main thread
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        // Clear session and license
                        com.example.myapplication.utils.SessionManager sessionManager = 
                            new com.example.myapplication.utils.SessionManager(context);
                        sessionManager.fullLogout();
                        
                        com.example.myapplication.utils.LicenseManager.getInstance(context).clearLicense();
                        
                        // Show message and navigate to login
                        android.widget.Toast.makeText(context, 
                            "Your license has expired. Please contact support.", 
                            android.widget.Toast.LENGTH_LONG).show();
                        
                        android.content.Intent intent = new android.content.Intent(context, 
                            com.example.myapplication.LoginActivity.class);
                        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | 
                                      android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(intent);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error during license expiry logout", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking license expiry", e);
        }
    }
    
    /**
     * Bidirectional sync user data (credit, profile info)
     * First pushes local changes to Firestore, then pulls latest from Firestore
     */
    private void syncUserData(String userId) throws Exception {
        Log.d(TAG, "=== SYNCING USER DATA (BIDIRECTIONAL) for user: " + userId + " ===");
        
        // Step 1: Push local user data to Firestore if modified
        UserEntity localUser = database.userDao().getUserById(userId);
        if (localUser != null) {
            Log.d(TAG, "Local user found: " + userId + 
                " (credit: " + localUser.getVirtualCredit() + 
                ", updatedAt: " + new java.util.Date(localUser.getUpdatedAt()) + 
                ", lastSyncAt: " + new java.util.Date(localUser.getLastSyncAt()) + ")");
            
            // Check if local user has changes that need to be pushed
            if (localUser.getUpdatedAt() > localUser.getLastSyncAt()) {
                Log.d(TAG, "Pushing local user changes to Firestore for: " + userId);
                CountDownLatch pushLatch = new CountDownLatch(1);
                final Exception[] pushError = {null};
                
                Map<String, Object> userData = new HashMap<>();
                userData.put("virtualCredit", localUser.getVirtualCredit());
                userData.put("cashBalance", localUser.getCashBalance());
                userData.put("name", localUser.getName());
                userData.put("email", localUser.getEmail());
                userData.put("phone", localUser.getPhone());
                userData.put("role", localUser.getRole());
                userData.put("disabled", localUser.isDisabled());
                userData.put("creditUpdatedAt", localUser.getCreditUpdatedAt());
                userData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(localUser.getUpdatedAt())));
                
                firestore.collection("users").document(userId)
                        .set(userData, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Successfully pushed user data to Firestore: " + userId + 
                                " (credit: " + localUser.getVirtualCredit() + ")");
                            pushLatch.countDown();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to push user data to Firestore: " + userId, e);
                            pushError[0] = e;
                            pushLatch.countDown();
                        });
                
                pushLatch.await();
                if (pushError[0] != null) {
                    throw pushError[0];
                }
            } else {
                Log.d(TAG, "No local changes to push for user: " + userId);
            }
        }
        
        // Step 2: Pull latest data from Firestore
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = {null};
        
        firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    try {
                        if (documentSnapshot.exists()) {
                            UserEntity localUserEntity = database.userDao().getUserById(userId);
                            
                            if (localUserEntity != null) {
                                // Handle updatedAt as Firestore Timestamp or Long
                                Long firestoreUpdatedAt = null;
                                try {
                                    Object updatedAtObj = documentSnapshot.get("updatedAt");
                                    if (updatedAtObj instanceof com.google.firebase.Timestamp) {
                                        firestoreUpdatedAt = ((com.google.firebase.Timestamp) updatedAtObj).toDate().getTime();
                                    } else if (updatedAtObj instanceof Long) {
                                        firestoreUpdatedAt = (Long) updatedAtObj;
                                    } else if (updatedAtObj instanceof Number) {
                                        firestoreUpdatedAt = ((Number) updatedAtObj).longValue();
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Could not parse updatedAt timestamp", e);
                                }
                                
                                if (firestoreUpdatedAt == null) {
                                    firestoreUpdatedAt = System.currentTimeMillis();
                                }
                                
                                Log.d(TAG, "Comparing timestamps - Local: " + 
                                    new java.util.Date(localUserEntity.getUpdatedAt()) + 
                                    ", Firestore: " + new java.util.Date(firestoreUpdatedAt));
                                
                                // ALWAYS sync disabled status (critical for security)
                                Boolean disabled = documentSnapshot.getBoolean("disabled");
                                if (disabled != null) {
                                    localUserEntity.setDisabled(disabled);
                                    Log.d(TAG, "Synced disabled status from Firestore: " + disabled);
                                }
                                
                                // Only update if Firestore version is newer
                                if (firestoreUpdatedAt > localUserEntity.getUpdatedAt()) {
                                    Log.d(TAG, "Firestore version is newer, updating local user");
                                    
                                    // Check credit-specific timestamp before updating credit
                                    Double credit = documentSnapshot.getDouble("virtualCredit");
                                    if (credit != null) {
                                        // Check if Firestore credit is more recent than local credit
                                        long firestoreCreditUpdatedAt = 0;
                                        if (documentSnapshot.contains("creditUpdatedAt")) {
                                            Object creditUpdatedAt = documentSnapshot.get("creditUpdatedAt");
                                            if (creditUpdatedAt instanceof com.google.firebase.Timestamp) {
                                                firestoreCreditUpdatedAt = ((com.google.firebase.Timestamp) creditUpdatedAt).toDate().getTime();
                                            } else if (creditUpdatedAt instanceof Long) {
                                                firestoreCreditUpdatedAt = (Long) creditUpdatedAt;
                                            }
                                        }
                                        
                                        // Only update credit if Firestore credit is more recent
                                        if (firestoreCreditUpdatedAt > localUserEntity.getCreditUpdatedAt()) {
                                            Log.d(TAG, "Updating credit: " + localUserEntity.getVirtualCredit() + " -> " + credit);
                                            localUserEntity.setVirtualCredit(credit);
                                            localUserEntity.setCreditUpdatedAt(firestoreCreditUpdatedAt);
                                        } else {
                                            Log.d(TAG, "Local credit is more recent, keeping local value: " + localUserEntity.getVirtualCredit());
                                        }
                                    }
                                    
                                    String name = documentSnapshot.getString("name");
                                    if (name != null) {
                                        localUserEntity.setName(name);
                                    }
                                    
                                    String email = documentSnapshot.getString("email");
                                    if (email != null) {
                                        localUserEntity.setEmail(email);
                                    }
                                    
                                    // Sync cash balance (no timestamp check needed)
                                    Double cashBalance = documentSnapshot.getDouble("cashBalance");
                                    if (cashBalance != null) {
                                        localUserEntity.setCashBalance(cashBalance);
                                    }
                                    
                                    localUserEntity.setUpdatedAt(firestoreUpdatedAt);
                                    localUserEntity.setLastSyncAt(System.currentTimeMillis());
                                    database.userDao().updateUser(localUserEntity);
                                    
                                    Log.d(TAG, "User data pulled from Firestore: " + userId +
                                            " (credit: " + localUserEntity.getVirtualCredit() + ")");
                                } else {
                                    Log.d(TAG, "Local version is up-to-date or newer, skipping pull");
                                    // Still update lastSyncAt and disabled status
                                    localUserEntity.setLastSyncAt(System.currentTimeMillis());
                                    database.userDao().updateUser(localUserEntity);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Firestore user data", e);
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to pull user data from Firestore: " + userId, e);
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "=== USER DATA SYNC COMPLETED for: " + userId + " ===");
    }
    
    /**
     * Sync credits for ALL users in local database (dealer + all agents)
     * This ensures when dealer adds credit to agent, both get synced
     */
    private void syncAllUsersCredits() throws Exception {
        Log.d(TAG, "=== SYNCING ALL USERS CREDITS ===");
        
        List<UserEntity> allUsers = database.userDao().getAllUsers();
        Log.d(TAG, "Found " + allUsers.size() + " users to sync credits");
        
        int syncedCount = 0;
        for (UserEntity user : allUsers) {
            try {
                // Check if this user has credit changes that need syncing
                if (user.getUpdatedAt() > user.getLastSyncAt()) {
                    Log.d(TAG, "Syncing credits for user: " + user.getUid() + 
                        " (role: " + user.getRole() + 
                        ", credit: " + user.getVirtualCredit() + ")");
                    syncUserData(user.getUid());
                    syncedCount++;
                } else {
                    Log.d(TAG, "User " + user.getUid() + " credits are already synced");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync credits for user: " + user.getUid(), e);
                // Continue with other users
            }
        }
        
        Log.d(TAG, "=== ALL USERS CREDITS SYNC COMPLETED (" + syncedCount + " users synced) ===");
    }
    
    /**
     * Bidirectional sync of customers (DISABLED - schema mismatch)
     */
    private void syncCustomers(String userId) throws Exception {
        Log.d(TAG, "=== SYNCING CUSTOMERS for user: " + userId + " ===");
        
        // Push local customers to Firestore
        List<CustomerEntity> localCustomers = database.customerDao().getCustomersNeedingSync();
        Log.d(TAG, "Found " + localCustomers.size() + " local customers needing sync");
        int syncedCount = 0;
        
        for (CustomerEntity customer : localCustomers) {
            try {
                Log.d(TAG, "Pushing customer to Firestore: " + customer.getId() + 
                    " (name: " + customer.getFullName() + 
                    ", phone: " + customer.getPhoneNumber() + 
                    ", createdAt: " + new java.util.Date(customer.getCreatedAt()) + 
                    ", updatedAt: " + new java.util.Date(customer.getUpdatedAt()) + ")");
                
                Map<String, Object> customerData = new HashMap<>();
                customerData.put("userId", customer.getCreatedBy());
                customerData.put("fullName", customer.getFullName());
                customerData.put("phoneNumber", customer.getPhoneNumber());
                customerData.put("nationalIdNumber", customer.getNationalIdNumber());
                customerData.put("address", customer.getAddress());
                customerData.put("dateOfBirth", customer.getDateOfBirth());
                customerData.put("isActive", customer.isActive());
                customerData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(customer.getCreatedAt())));
                customerData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(customer.getUpdatedAt())));
                
                firestore.collection("customers").document(customer.getId())
                        .set(customerData)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                customer.setNeedsSync(false);
                                customer.setLastSyncAt(System.currentTimeMillis());
                                database.customerDao().updateCustomer(customer);
                                Log.d(TAG, "Customer synced to Firestore: " + customer.getId());
                            }).start();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to sync customer to Firestore: " + customer.getId(), e);
                        });
                syncedCount++;
            } catch (Exception e) {
                Log.e(TAG, "Error preparing customer data for sync: " + customer.getId(), e);
            }
        }
        
        Log.d(TAG, "Pushed " + syncedCount + " local customers to Firestore");
        
        // Pull customers from Firestore
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = {null};
        
        firestore.collection("customers")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " customers in Firestore for user: " + userId);
                        int pulledCount = 0;
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String customerId = doc.getId();
                            CustomerEntity localCustomer = database.customerDao().getCustomerById(customerId);
                            
                            if (localCustomer == null) {
                                // New customer from Firestore - add to local database
                                CustomerEntity newCustomer = new CustomerEntity();
                                newCustomer.setId(customerId);
                                newCustomer.setCreatedBy(doc.getString("userId"));
                                newCustomer.setFullName(doc.getString("fullName"));
                                newCustomer.setPhoneNumber(doc.getString("phoneNumber"));
                                newCustomer.setNationalIdNumber(doc.getString("nationalIdNumber"));
                                newCustomer.setAddress(doc.getString("address"));
                                newCustomer.setDateOfBirth(doc.getString("dateOfBirth"));
                                newCustomer.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                // Robust timestamp parsing for createdAt/updatedAt
                                Long cCreatedAt = null;
                                Long cUpdatedAt = null;
                                try {
                                    Object ts = doc.get("createdAt");
                                    if (ts instanceof com.google.firebase.Timestamp) {
                                        cCreatedAt = ((com.google.firebase.Timestamp) ts).toDate().getTime();
                                    } else if (ts instanceof Long) {
                                        cCreatedAt = (Long) ts;
                                    } else if (ts instanceof java.util.Date) {
                                        cCreatedAt = ((java.util.Date) ts).getTime();
                                    } else if (ts instanceof Number) {
                                        cCreatedAt = ((Number) ts).longValue();
                                    }
                                } catch (Exception ignore) {}
                                try {
                                    Object ts = doc.get("updatedAt");
                                    if (ts instanceof com.google.firebase.Timestamp) {
                                        cUpdatedAt = ((com.google.firebase.Timestamp) ts).toDate().getTime();
                                    } else if (ts instanceof Long) {
                                        cUpdatedAt = (Long) ts;
                                    } else if (ts instanceof java.util.Date) {
                                        cUpdatedAt = ((java.util.Date) ts).getTime();
                                    } else if (ts instanceof Number) {
                                        cUpdatedAt = ((Number) ts).longValue();
                                    }
                                } catch (Exception ignore) {}
                                newCustomer.setCreatedAt(cCreatedAt != null ? cCreatedAt : System.currentTimeMillis());
                                newCustomer.setUpdatedAt(cUpdatedAt != null ? cUpdatedAt : System.currentTimeMillis());
                                newCustomer.setNeedsSync(false);
                                newCustomer.setLastSyncAt(System.currentTimeMillis());
                                
                                Log.d(TAG, "Pulling new customer from Firestore: " + customerId + 
                                    " (name: " + newCustomer.getFullName() + 
                                    ", phone: " + newCustomer.getPhoneNumber() + 
                                    ", createdAt: " + new java.util.Date(newCustomer.getCreatedAt()) + 
                                    ", updatedAt: " + new java.util.Date(newCustomer.getUpdatedAt()) + ")");
                                
                                database.customerDao().insertCustomer(newCustomer);
                                Log.d(TAG, "New customer synced from Firestore: " + customerId);
                                pulledCount++;
                            } else {
                                Log.d(TAG, "Customer already exists locally: " + customerId);
                            }
                        }
                        Log.d(TAG, "Pulled " + pulledCount + " new customers from Firestore");
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Firestore customers", e);
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read customers from Firestore", e);
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "=== CUSTOMER SYNC COMPLETED for user: " + userId + " ===");
    }
    
    private void syncCustomers_DISABLED(String userId) throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        final Exception[] error = {null};
        
        // 1. Push local customers to Firestore
        List<CustomerEntity> localCustomers = database.customerDao().getCustomersByUser(userId);
        for (CustomerEntity customer : localCustomers) {
            if (customer.isNeedsSync()) {
                Map<String, Object> customerData = new HashMap<>();
                                customerData.put("name", customer.getFullName());
                                customerData.put("phoneNumber", customer.getPhoneNumber());
                                customerData.put("address", customer.getAddress());
                                customerData.put("dateOfBirth", customer.getDateOfBirth());
                                customerData.put("createdBy", customer.getCreatedBy());  // Fixed: use "createdBy"
                customerData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(customer.getCreatedAt())));
                customerData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(customer.getUpdatedAt())));
                customerData.put("isActive", customer.isActive());
                
                firestore.collection("customers").document(customer.getId())
                        .set(customerData)
                        .addOnSuccessListener(aVoid -> {
                            // Mark as synced in local DB
                            new Thread(() -> {
                                customer.setNeedsSync(false);
                                customer.setLastSyncAt(System.currentTimeMillis());
                                database.customerDao().updateCustomer(customer);
                            }).start();
                        });
            }
        }
        latch.countDown();
        
        // 2. Pull customers from Firestore
        firestore.collection("customers")
                .whereEqualTo("createdBy", userId)  // Fixed: use "createdBy" to match push
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String customerId = doc.getId();
                            CustomerEntity localCustomer = database.customerDao().getCustomerById(customerId);
                            
                            if (localCustomer == null) {
                                // New customer from Firestore
                                CustomerEntity newCustomer = new CustomerEntity();
                                newCustomer.setId(customerId);
                                String fullName = doc.getString("fullName");
                                if (fullName == null || fullName.isEmpty()) {
                                    fullName = doc.getString("name");
                                }
                                newCustomer.setFullName(fullName);
                                newCustomer.setPhoneNumber(doc.getString("phoneNumber"));
                                newCustomer.setAddress(doc.getString("address"));
                                newCustomer.setDateOfBirth(doc.getString("dateOfBirth"));
                                newCustomer.setCreatedBy(doc.getString("createdBy"));  // Fixed: use "createdBy"
                                newCustomer.setCreatedAt(doc.getLong("createdAt"));
                                newCustomer.setUpdatedAt(doc.getLong("updatedAt"));
                                newCustomer.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                newCustomer.setNeedsSync(false);
                                newCustomer.setLastSyncAt(System.currentTimeMillis());
                                
                                database.customerDao().insertCustomer(newCustomer);
                                Log.d(TAG, "New customer synced from Firestore: " + customerId);
                            }
                        }
                    } catch (Exception e) {
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "Customers synced: " + localCustomers.size());
    }
    
    /**
     * Bidirectional sync of operators (DISABLED - schema mismatch)
     */
    private void syncOperators(String userId) throws Exception {
        Log.d(TAG, "=== SYNCING OPERATORS for user: " + userId + " ===");
        
        // Push local operators to Firestore
        List<OperatorEntity> localOperators = database.operatorDao().getNeedingSync();
        Log.d(TAG, "Found " + localOperators.size() + " local operators needing sync");
        int syncedCount = 0;
        
        for (OperatorEntity operator : localOperators) {
            try {
                Log.d(TAG, "Pushing operator to Firestore: " + operator.getId() + 
                    " (name: " + operator.getName() + 
                    ", type: " + operator.getType() + 
                    ", createdAt: " + new java.util.Date(operator.getCreatedAt()) + 
                    ", updatedAt: " + new java.util.Date(operator.getUpdatedAt()) + ")");
                
                Map<String, Object> operatorData = new HashMap<>();
                operatorData.put("userId", operator.getAddedBy());
                operatorData.put("name", operator.getName());
                operatorData.put("type", operator.getType());
                operatorData.put("enabled", operator.isEnabled());
                operatorData.put("code", operator.getCode());
                operatorData.put("color", operator.getColor());
                operatorData.put("transferRate", operator.getTransferRate());
                operatorData.put("isActive", operator.isActive());
                operatorData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(operator.getCreatedAt())));
                operatorData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(operator.getUpdatedAt())));
                
                firestore.collection("operators").document(operator.getId())
                        .set(operatorData)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                operator.setNeedsSync(false);
                                operator.setLastSyncAt(System.currentTimeMillis());
                                database.operatorDao().updateOperator(operator);
                                Log.d(TAG, "Operator synced to Firestore: " + operator.getId());
                            }).start();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to sync operator to Firestore: " + operator.getId(), e);
                        });
                syncedCount++;
            } catch (Exception e) {
                Log.e(TAG, "Error preparing operator data for sync: " + operator.getId(), e);
            }
        }
        
        Log.d(TAG, "Pushed " + syncedCount + " local operators to Firestore");
        
        // Pull operators from Firestore
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = {null};
        
        firestore.collection("operators")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " operators in Firestore for user: " + userId);
                        int pulledCount = 0;
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String operatorId = doc.getId();
                            OperatorEntity localOperator = database.operatorDao().getById(operatorId);
                            
                            if (localOperator == null) {
                                // New operator from Firestore - add to local database
                                OperatorEntity newOperator = new OperatorEntity();
                                newOperator.setId(operatorId);
                                newOperator.setAddedBy(doc.getString("userId"));
                                newOperator.setName(doc.getString("name"));
                                newOperator.setType(doc.getString("type"));
                                newOperator.setEnabled(doc.getBoolean("enabled") != null ? doc.getBoolean("enabled") : true);
                                newOperator.setCode(doc.getString("code"));
                                newOperator.setColor(doc.getString("color"));
                                Double transferRate = doc.getDouble("transferRate");
                                newOperator.setTransferRate(transferRate != null ? transferRate : 0.0);
                                newOperator.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                // Robust timestamp parsing for createdAt/updatedAt
                                Long oCreatedAt = null;
                                Long oUpdatedAt = null;
                                try {
                                    Object ts = doc.get("createdAt");
                                    if (ts instanceof com.google.firebase.Timestamp) {
                                        oCreatedAt = ((com.google.firebase.Timestamp) ts).toDate().getTime();
                                    } else if (ts instanceof Long) {
                                        oCreatedAt = (Long) ts;
                                    } else if (ts instanceof java.util.Date) {
                                        oCreatedAt = ((java.util.Date) ts).getTime();
                                    } else if (ts instanceof Number) {
                                        oCreatedAt = ((Number) ts).longValue();
                                    }
                                } catch (Exception ignore) {}
                                try {
                                    Object ts = doc.get("updatedAt");
                                    if (ts instanceof com.google.firebase.Timestamp) {
                                        oUpdatedAt = ((com.google.firebase.Timestamp) ts).toDate().getTime();
                                    } else if (ts instanceof Long) {
                                        oUpdatedAt = (Long) ts;
                                    } else if (ts instanceof java.util.Date) {
                                        oUpdatedAt = ((java.util.Date) ts).getTime();
                                    } else if (ts instanceof Number) {
                                        oUpdatedAt = ((Number) ts).longValue();
                                    }
                                } catch (Exception ignore) {}
                                newOperator.setCreatedAt(oCreatedAt != null ? oCreatedAt : System.currentTimeMillis());
                                newOperator.setUpdatedAt(oUpdatedAt != null ? oUpdatedAt : System.currentTimeMillis());
                                newOperator.setNeedsSync(false);
                                newOperator.setLastSyncAt(System.currentTimeMillis());
                                
                                Log.d(TAG, "Pulling new operator from Firestore: " + operatorId + 
                                    " (name: " + newOperator.getName() + 
                                    ", type: " + newOperator.getType() + 
                                    ", createdAt: " + new java.util.Date(newOperator.getCreatedAt()) + 
                                    ", updatedAt: " + new java.util.Date(newOperator.getUpdatedAt()) + ")");
                                
                                database.operatorDao().insertOperator(newOperator);
                                Log.d(TAG, "New operator synced from Firestore: " + operatorId);
                                pulledCount++;
                            } else {
                                Log.d(TAG, "Operator already exists locally: " + operatorId);
                            }
                        }
                        Log.d(TAG, "Pulled " + pulledCount + " new operators from Firestore");
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Firestore operators", e);
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read operators from Firestore", e);
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "=== OPERATOR SYNC COMPLETED for user: " + userId + " ===");
    }
    
    private void syncOperators_DISABLED(String userId) throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        final Exception[] error = {null};
        
        // 1. Push local operators to Firestore
        List<OperatorEntity> localOperators = database.operatorDao().getActiveForUser(userId);
        for (OperatorEntity operator : localOperators) {
            if (operator.isNeedsSync()) {
                Map<String, Object> operatorData = new HashMap<>();
                operatorData.put("name", operator.getName());
                operatorData.put("code", operator.getCode());
                operatorData.put("type", operator.getType());
                operatorData.put("color", operator.getColor());
                operatorData.put("enabled", operator.isEnabled());
                operatorData.put("addedBy", operator.getAddedBy());
                operatorData.put("isActive", operator.isActive());
                operatorData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(operator.getCreatedAt())));
                operatorData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(operator.getUpdatedAt())));
                
                firestore.collection("operators").document(operator.getId())
                        .set(operatorData)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                operator.setNeedsSync(false);
                                operator.setLastSyncAt(System.currentTimeMillis());
                                database.operatorDao().updateOperator(operator);
                            }).start();
                        });
            }
        }
        latch.countDown();
        
        // 2. Pull operators from Firestore
        firestore.collection("operators")
                .whereEqualTo("addedBy", userId)  // Fixed: use "addedBy" to match push
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String operatorId = doc.getId();
                            OperatorEntity localOperator = database.operatorDao().getById(operatorId);
                            
                            if (localOperator == null) {
                                OperatorEntity newOperator = new OperatorEntity();
                                newOperator.setId(operatorId);
                                newOperator.setName(doc.getString("name"));
                                newOperator.setCode(doc.getString("code"));
                                newOperator.setType(doc.getString("type"));
                                newOperator.setColor(doc.getString("color"));
                                Double transferRate = doc.getDouble("transferRate");
                                newOperator.setTransferRate(transferRate != null ? transferRate : 0.0);
                                newOperator.setEnabled(doc.getBoolean("enabled") != null ? doc.getBoolean("enabled") : true);
                                newOperator.setAddedBy(doc.getString("addedBy"));
                                newOperator.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                newOperator.setCreatedAt(doc.getLong("createdAt"));
                                newOperator.setUpdatedAt(doc.getLong("updatedAt"));
                                newOperator.setNeedsSync(false);
                                newOperator.setLastSyncAt(System.currentTimeMillis());
                                
                                database.operatorDao().insertOperator(newOperator);
                                Log.d(TAG, "New operator synced from Firestore: " + operatorId);
                            }
                        }
                    } catch (Exception e) {
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "Operators synced: " + localOperators.size());
    }
    
    /**
     * Bidirectional sync of operator actions (DISABLED - schema mismatch)
     */
    private void syncOperatorActions(String userId) throws Exception {
        Log.d(TAG, "=== SYNCING OPERATOR ACTIONS for user: " + userId + " ===");
        
        // Push local operator actions to Firestore
        List<OperatorActionEntity> localActions = database.operatorActionDao().getNeedingSync();
        Log.d(TAG, "Found " + localActions.size() + " local operator actions needing sync");
        int syncedCount = 0;
        
        for (OperatorActionEntity action : localActions) {
            try {
                Log.d(TAG, "Pushing operator action to Firestore: " + action.getId() + 
                    " (name: " + action.getName() + 
                    ", type: " + action.getType() + 
                    ", operatorId: " + action.getOperatorId() + 
                    ", createdAt: " + new java.util.Date(action.getCreatedAt()) + 
                    ", updatedAt: " + new java.util.Date(action.getUpdatedAt()) + ")");
                
                Map<String, Object> actionData = new HashMap<>();
                actionData.put("userId", action.getAddedBy());
                actionData.put("operatorId", action.getOperatorId());
                actionData.put("name", action.getName());
                actionData.put("type", action.getType());
                actionData.put("actionCode", action.getActionCode());
                actionData.put("isActive", action.isActive());
                actionData.put("disableUssd", action.isDisableUssd());
                actionData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(action.getCreatedAt())));
                actionData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(action.getUpdatedAt())));
                
                firestore.collection("operator_actions").document(action.getId())
                        .set(actionData)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                action.setNeedsSync(false);
                                action.setLastSyncAt(System.currentTimeMillis());
                                database.operatorActionDao().updateAction(action);
                                Log.d(TAG, "Operator action synced to Firestore: " + action.getId());
                            }).start();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to sync operator action to Firestore: " + action.getId(), e);
                        });
                syncedCount++;
            } catch (Exception e) {
                Log.e(TAG, "Error preparing operator action data for sync: " + action.getId(), e);
            }
        }
        
        Log.d(TAG, "Pushed " + syncedCount + " local operator actions to Firestore");
        
        // Pull operator actions from Firestore
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = {null};
        
        firestore.collection("operator_actions")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " operator actions in Firestore for user: " + userId);
                        int pulledCount = 0;
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String actionId = doc.getId();
                            OperatorActionEntity localAction = database.operatorActionDao().getById(actionId);
                            
                            if (localAction == null) {
                                // New operator action from Firestore - add to local database
                                OperatorActionEntity newAction = new OperatorActionEntity();
                                newAction.setId(actionId);
                                newAction.setAddedBy(doc.getString("userId"));
                                newAction.setOperatorId(doc.getString("operatorId"));
                                newAction.setName(doc.getString("name"));
                                newAction.setType(doc.getString("type"));
                                newAction.setActionCode(doc.getString("actionCode"));
                                newAction.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                newAction.setDisableUssd(doc.getBoolean("disableUssd") != null ? doc.getBoolean("disableUssd") : false);
                                // Robust timestamp parsing for createdAt/updatedAt
                                Long aCreatedAt = null;
                                Long aUpdatedAt = null;
                                try {
                                    Object ts = doc.get("createdAt");
                                    if (ts instanceof com.google.firebase.Timestamp) {
                                        aCreatedAt = ((com.google.firebase.Timestamp) ts).toDate().getTime();
                                    } else if (ts instanceof Long) {
                                        aCreatedAt = (Long) ts;
                                    } else if (ts instanceof java.util.Date) {
                                        aCreatedAt = ((java.util.Date) ts).getTime();
                                    } else if (ts instanceof Number) {
                                        aCreatedAt = ((Number) ts).longValue();
                                    }
                                } catch (Exception ignore) {}
                                try {
                                    Object ts = doc.get("updatedAt");
                                    if (ts instanceof com.google.firebase.Timestamp) {
                                        aUpdatedAt = ((com.google.firebase.Timestamp) ts).toDate().getTime();
                                    } else if (ts instanceof Long) {
                                        aUpdatedAt = (Long) ts;
                                    } else if (ts instanceof java.util.Date) {
                                        aUpdatedAt = ((java.util.Date) ts).getTime();
                                    } else if (ts instanceof Number) {
                                        aUpdatedAt = ((Number) ts).longValue();
                                    }
                                } catch (Exception ignore) {}
                                newAction.setCreatedAt(aCreatedAt != null ? aCreatedAt : System.currentTimeMillis());
                                newAction.setUpdatedAt(aUpdatedAt != null ? aUpdatedAt : System.currentTimeMillis());
                                newAction.setNeedsSync(false);
                                newAction.setLastSyncAt(System.currentTimeMillis());
                                
                                Log.d(TAG, "Pulling new operator action from Firestore: " + actionId + 
                                    " (name: " + newAction.getName() + 
                                    ", type: " + newAction.getType() + 
                                    ", operatorId: " + newAction.getOperatorId() + 
                                    ", createdAt: " + new java.util.Date(newAction.getCreatedAt()) + 
                                    ", updatedAt: " + new java.util.Date(newAction.getUpdatedAt()) + ")");
                                
                                database.operatorActionDao().insertAction(newAction);
                                Log.d(TAG, "New operator action synced from Firestore: " + actionId);
                                pulledCount++;
                            } else {
                                Log.d(TAG, "Operator action already exists locally: " + actionId);
                            }
                        }
                        Log.d(TAG, "Pulled " + pulledCount + " new operator actions from Firestore");
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Firestore operator actions", e);
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read operator actions from Firestore", e);
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "=== OPERATOR ACTION SYNC COMPLETED for user: " + userId + " ===");
    }
    
    private void syncOperatorActions_DISABLED(String userId) throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        final Exception[] error = {null};
        
        // 1. Push local actions to Firestore (NOTE: No simple query for all user actions)
        List<OperatorActionEntity> localActions = database.operatorActionDao().getNeedingSync();
        for (OperatorActionEntity action : localActions) {
            if (action.isNeedsSync()) {
                Map<String, Object> actionData = new HashMap<>();
                actionData.put("name", action.getName());
                actionData.put("actionCode", action.getActionCode());
                actionData.put("type", action.getType());
                actionData.put("operatorId", action.getOperatorId());
                actionData.put("ussdTemplate", action.getUssdTemplate());
                actionData.put("requiredFieldsJson", action.getRequiredFieldsJson());
                actionData.put("addedBy", action.getAddedBy());
                actionData.put("isActive", action.isActive());
                actionData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(action.getCreatedAt())));
                actionData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(action.getUpdatedAt())));
                
                firestore.collection("operator_actions").document(action.getId())
                        .set(actionData)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                action.setNeedsSync(false);
                                action.setLastSyncAt(System.currentTimeMillis());
                                database.operatorActionDao().updateAction(action);
                            }).start();
                        });
            }
        }
        latch.countDown();
        
        // 2. Pull actions from Firestore
        firestore.collection("operator_actions")
                .whereEqualTo("addedBy", userId)  // Fixed: use "addedBy" to match push
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String actionId = doc.getId();
                            OperatorActionEntity localAction = database.operatorActionDao().getById(actionId);
                            
                            if (localAction == null) {
                                OperatorActionEntity newAction = new OperatorActionEntity();
                                newAction.setId(actionId);
                                newAction.setName(doc.getString("name"));
                                newAction.setActionCode(doc.getString("actionCode"));
                                newAction.setType(doc.getString("type"));
                                newAction.setOperatorId(doc.getString("operatorId"));
                                newAction.setUssdTemplate(doc.getString("ussdTemplate"));
                                newAction.setRequiredFieldsJson(doc.getString("requiredFieldsJson"));
                                newAction.setAddedBy(doc.getString("addedBy"));
                                newAction.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                newAction.setCreatedAt(doc.getLong("createdAt"));
                                newAction.setUpdatedAt(doc.getLong("updatedAt"));
                                newAction.setNeedsSync(false);
                                newAction.setLastSyncAt(System.currentTimeMillis());
                                
                                database.operatorActionDao().insertAction(newAction);
                                Log.d(TAG, "New action synced from Firestore: " + actionId);
                            }
                        }
                    } catch (Exception e) {
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "Operator actions synced: " + localActions.size());
    }
    
    /**
     * Bidirectional sync of transactions
     */
    private void syncTransactions(String userId) throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        final Exception[] error = {null};
        
        // 1. Push local transactions to Firestore
        List<TransactionEntity> localTransactions = database.transactionDao().getTransactionsByUser(userId);
        for (TransactionEntity transaction : localTransactions) {
            if (transaction.isNeedsSync()) {
                Map<String, Object> transactionData = new HashMap<>();
                transactionData.put("userId", transaction.getUserId());
                transactionData.put("userName", transaction.getUserName());
                transactionData.put("userRole", transaction.getUserRole());
                transactionData.put("customerId", transaction.getCustomerId());
                transactionData.put("customerName", transaction.getCustomerName());
                transactionData.put("operatorId", transaction.getOperatorId());
                transactionData.put("operatorName", transaction.getOperatorName());
                transactionData.put("actionId", transaction.getActionId());
                transactionData.put("actionName", transaction.getActionName());
                transactionData.put("transactionType", transaction.getTransactionType());
                transactionData.put("amount", transaction.getAmount());
                transactionData.put("status", transaction.getStatus());
                transactionData.put("notes", transaction.getNotes());
                transactionData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(transaction.getCreatedAt())));
                transactionData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(transaction.getUpdatedAt())));
                
                firestore.collection("transactions").document(transaction.getId())
                        .set(transactionData)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                transaction.setNeedsSync(false);
                                transaction.setLastSyncAt(System.currentTimeMillis());
                                database.transactionDao().updateTransaction(transaction);
                            }).start();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to sync transaction to Firestore: " + transaction.getId(), e);
                        });
            }
        }
        latch.countDown();
        
        // 2. Pull transactions from Firestore
        firestore.collection("transactions")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String transactionId = doc.getId();
                            TransactionEntity localTransaction = database.transactionDao().getTransactionById(transactionId);
                            
                            if (localTransaction == null) {
                                TransactionEntity newTransaction = new TransactionEntity();
                                newTransaction.setId(transactionId);
                                newTransaction.setUserId(doc.getString("userId"));
                                newTransaction.setUserName(doc.getString("userName"));
                                newTransaction.setUserRole(doc.getString("userRole"));
                                newTransaction.setCustomerId(doc.getString("customerId"));
                                newTransaction.setCustomerName(doc.getString("customerName"));
                                newTransaction.setOperatorId(doc.getString("operatorId"));
                                newTransaction.setOperatorName(doc.getString("operatorName"));
                                newTransaction.setActionId(doc.getString("actionId"));
                                newTransaction.setActionName(doc.getString("actionName"));
                                newTransaction.setTransactionType(doc.getString("transactionType"));
                                newTransaction.setAmount(doc.getDouble("amount"));
                                newTransaction.setStatus(doc.getString("status"));
                                newTransaction.setNotes(doc.getString("notes"));
                                // Handle timestamp conversion from Firestore
                                Long createdAt = null;
                                try {
                                    Object timestampObj = doc.get("createdAt");
                                    if (timestampObj instanceof com.google.firebase.Timestamp) {
                                        createdAt = ((com.google.firebase.Timestamp) timestampObj).toDate().getTime();
                                    } else if (timestampObj instanceof Long) {
                                        createdAt = (Long) timestampObj;
                                    } else if (timestampObj instanceof java.util.Date) {
                                        createdAt = ((java.util.Date) timestampObj).getTime();
                                    } else if (timestampObj instanceof Number) {
                                        createdAt = ((Number) timestampObj).longValue();
                                    } else {
                                        Log.w(TAG, "Unknown timestamp type for createdAt: " + (timestampObj != null ? timestampObj.getClass().getSimpleName() : "null"));
                                        createdAt = System.currentTimeMillis();
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Could not parse createdAt timestamp", e);
                                    createdAt = System.currentTimeMillis();
                                }
                                newTransaction.setCreatedAt(createdAt != null ? createdAt : System.currentTimeMillis());
                                
                                // Log timestamp details for debugging
                                Log.d(TAG, "Transaction " + transactionId + " createdAt parsed as: " + 
                                    (createdAt != null ? new java.util.Date(createdAt).toString() : "null"));
                                
                                Long updatedAt = null;
                                try {
                                    Object timestampObj = doc.get("updatedAt");
                                    if (timestampObj instanceof com.google.firebase.Timestamp) {
                                        updatedAt = ((com.google.firebase.Timestamp) timestampObj).toDate().getTime();
                                    } else if (timestampObj instanceof Long) {
                                        updatedAt = (Long) timestampObj;
                                    } else if (timestampObj instanceof java.util.Date) {
                                        updatedAt = ((java.util.Date) timestampObj).getTime();
                                    } else if (timestampObj instanceof Number) {
                                        updatedAt = ((Number) timestampObj).longValue();
                                    } else {
                                        Log.w(TAG, "Unknown timestamp type for updatedAt: " + (timestampObj != null ? timestampObj.getClass().getSimpleName() : "null"));
                                        updatedAt = System.currentTimeMillis();
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Could not parse updatedAt timestamp", e);
                                    updatedAt = System.currentTimeMillis();
                                }
                                newTransaction.setUpdatedAt(updatedAt != null ? updatedAt : System.currentTimeMillis());
                                
                                // Log timestamp details for debugging
                                Log.d(TAG, "Transaction " + transactionId + " updatedAt parsed as: " + 
                                    (updatedAt != null ? new java.util.Date(updatedAt).toString() : "null"));
                                
                                newTransaction.setNeedsSync(false);
                                newTransaction.setLastSyncAt(System.currentTimeMillis());
                                
                                database.transactionDao().insertTransaction(newTransaction);
                                Log.d(TAG, "New transaction synced from Firestore: " + transactionId);
                            }
                        }
                    } catch (Exception e) {
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read transactions from Firestore", e);
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "Transactions synced: " + localTransactions.size());
    }
    
    /**
     * Simplified transaction sync - only push local to Firestore
     */
    private void syncTransactionsSimple(String userId) throws Exception {
        Log.d(TAG, "Starting simple transaction sync for user: " + userId);
        
        // Get all transactions for this user that need sync
        List<TransactionEntity> localTransactions = database.transactionDao().getTransactionsByUser(userId);
        
        for (TransactionEntity transaction : localTransactions) {
            if (transaction.isNeedsSync()) {
                try {
                    Map<String, Object> transactionData = new HashMap<>();
                    transactionData.put("userId", transaction.getUserId());
                    transactionData.put("userName", transaction.getUserName());
                    transactionData.put("userRole", transaction.getUserRole());
                    transactionData.put("customerId", transaction.getCustomerId());
                    transactionData.put("customerName", transaction.getCustomerName());
                    transactionData.put("operatorId", transaction.getOperatorId());
                    transactionData.put("operatorName", transaction.getOperatorName());
                    transactionData.put("actionId", transaction.getActionId());
                    transactionData.put("actionName", transaction.getActionName());
                    transactionData.put("transactionType", transaction.getTransactionType());
                    transactionData.put("amount", transaction.getAmount());
                    transactionData.put("status", transaction.getStatus());
                    transactionData.put("notes", transaction.getNotes());
                    transactionData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(transaction.getCreatedAt())));
                    transactionData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(transaction.getUpdatedAt())));
                    
                    firestore.collection("transactions").document(transaction.getId())
                            .set(transactionData)
                            .addOnSuccessListener(aVoid -> {
                                new Thread(() -> {
                                    transaction.setNeedsSync(false);
                                    transaction.setLastSyncAt(System.currentTimeMillis());
                                    database.transactionDao().updateTransaction(transaction);
                                    Log.d(TAG, "Transaction synced to Firestore: " + transaction.getId());
                                }).start();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to sync transaction to Firestore: " + transaction.getId(), e);
                            });
                } catch (Exception e) {
                    Log.e(TAG, "Error preparing transaction data for sync: " + transaction.getId(), e);
                }
            }
        }
        
        Log.d(TAG, "Simple transaction sync completed for user: " + userId);
    }
    
    /**
     * Bidirectional transaction sync - push local to Firestore and pull from Firestore
     */
    private void syncTransactionsBidirectional(String userId) throws Exception {
        Log.d(TAG, "Starting bidirectional transaction sync for user: " + userId);
        
        // Step 1: Push local transactions to Firestore
        List<TransactionEntity> localTransactions = database.transactionDao().getTransactionsByUser(userId);
        int syncedCount = 0;
        
        for (TransactionEntity transaction : localTransactions) {
            if (transaction.isNeedsSync()) {
                try {
                    Map<String, Object> transactionData = new HashMap<>();
                    transactionData.put("userId", transaction.getUserId());
                    transactionData.put("userName", transaction.getUserName());
                    transactionData.put("userRole", transaction.getUserRole());
                    transactionData.put("customerId", transaction.getCustomerId());
                    transactionData.put("customerName", transaction.getCustomerName());
                    transactionData.put("operatorId", transaction.getOperatorId());
                    transactionData.put("operatorName", transaction.getOperatorName());
                    transactionData.put("actionId", transaction.getActionId());
                    transactionData.put("actionName", transaction.getActionName());
                    transactionData.put("transactionType", transaction.getTransactionType());
                    transactionData.put("amount", transaction.getAmount());
                    transactionData.put("status", transaction.getStatus());
                    transactionData.put("notes", transaction.getNotes());
                    transactionData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(transaction.getCreatedAt())));
                    transactionData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(transaction.getUpdatedAt())));
                    
                    firestore.collection("transactions").document(transaction.getId())
                            .set(transactionData)
                            .addOnSuccessListener(aVoid -> {
                                new Thread(() -> {
                                    transaction.setNeedsSync(false);
                                    transaction.setLastSyncAt(System.currentTimeMillis());
                                    database.transactionDao().updateTransaction(transaction);
                                    Log.d(TAG, "Transaction synced to Firestore: " + transaction.getId());
                                }).start();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to sync transaction to Firestore: " + transaction.getId(), e);
                            });
                    syncedCount++;
                } catch (Exception e) {
                    Log.e(TAG, "Error preparing transaction data for sync: " + transaction.getId(), e);
                }
            }
        }
        
        Log.d(TAG, "Pushed " + syncedCount + " local transactions to Firestore");
        
        // Step 2: Pull transactions from Firestore
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = {null};
        
        firestore.collection("transactions")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        int pulledCount = 0;
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String transactionId = doc.getId();
                            TransactionEntity localTransaction = database.transactionDao().getTransactionById(transactionId);
                            
                            if (localTransaction == null) {
                                // New transaction from Firestore - add to local database
                                TransactionEntity newTransaction = new TransactionEntity();
                                newTransaction.setId(transactionId);
                                newTransaction.setUserId(doc.getString("userId"));
                                newTransaction.setUserName(doc.getString("userName"));
                                newTransaction.setUserRole(doc.getString("userRole"));
                                newTransaction.setCustomerId(doc.getString("customerId"));
                                newTransaction.setCustomerName(doc.getString("customerName"));
                                newTransaction.setOperatorId(doc.getString("operatorId"));
                                newTransaction.setOperatorName(doc.getString("operatorName"));
                                newTransaction.setActionId(doc.getString("actionId"));
                                newTransaction.setActionName(doc.getString("actionName"));
                                newTransaction.setTransactionType(doc.getString("transactionType"));
                                newTransaction.setAmount(doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0);
                                newTransaction.setStatus(doc.getString("status"));
                                newTransaction.setNotes(doc.getString("notes"));
                                
                                // Additional fields from Firestore schema
                                newTransaction.setChannel(doc.getString("channel"));
                                newTransaction.setCustomerPhone(doc.getString("customerPhone"));
                                newTransaction.setCreditBefore(doc.getDouble("creditBefore") != null ? doc.getDouble("creditBefore") : 0.0);
                                newTransaction.setCreditAfter(doc.getDouble("creditAfter") != null ? doc.getDouble("creditAfter") : 0.0);
                                
                                // Handle timestamps safely - Firestore stores as Timestamp
                                Long createdAt = null;
                                Long updatedAt = null;
                                
                                try {
                                    // Try to get as Long first
                                    createdAt = doc.getLong("createdAt");
                                    if (createdAt == null) {
                                        // If not Long, try to get as Timestamp and convert
                                        Object timestampObj = doc.get("createdAt");
                                        if (timestampObj != null) {
                                            // Firestore Timestamp - convert to milliseconds
                                            createdAt = ((com.google.firebase.Timestamp) timestampObj).toDate().getTime();
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Could not parse createdAt timestamp", e);
                                    createdAt = System.currentTimeMillis();
                                }
                                
                                try {
                                    // Try to get as Long first
                                    updatedAt = doc.getLong("updatedAt");
                                    if (updatedAt == null) {
                                        // If not Long, try to get as Timestamp and convert
                                        Object timestampObj = doc.get("updatedAt");
                                        if (timestampObj != null) {
                                            // Firestore Timestamp - convert to milliseconds
                                            updatedAt = ((com.google.firebase.Timestamp) timestampObj).toDate().getTime();
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Could not parse updatedAt timestamp", e);
                                    updatedAt = System.currentTimeMillis();
                                }
                                
                                newTransaction.setCreatedAt(createdAt != null ? createdAt : System.currentTimeMillis());
                                newTransaction.setUpdatedAt(updatedAt != null ? updatedAt : System.currentTimeMillis());
                                
                                // Log timestamp details for debugging
                                Log.d(TAG, "Transaction " + transactionId + " timestamps - createdAt: " + 
                                    (createdAt != null ? new java.util.Date(createdAt).toString() : "null") + 
                                    ", updatedAt: " + (updatedAt != null ? new java.util.Date(updatedAt).toString() : "null"));
                                
                                newTransaction.setNeedsSync(false);
                                newTransaction.setLastSyncAt(System.currentTimeMillis());
                                
                                database.transactionDao().insertTransaction(newTransaction);
                                pulledCount++;
                                Log.d(TAG, "New transaction pulled from Firestore: " + transactionId);
                            } else {
                                // Existing transaction - update if Firestore has newer data
                                try {
                                    // Parse timestamps robustly
                                    Long createdAt = null;
                                    Long updatedAt = null;
                                    Object createdObj = doc.get("createdAt");
                                    if (createdObj instanceof com.google.firebase.Timestamp) {
                                        createdAt = ((com.google.firebase.Timestamp) createdObj).toDate().getTime();
                                    } else if (createdObj instanceof Long) {
                                        createdAt = (Long) createdObj;
                                    } else if (createdObj instanceof java.util.Date) {
                                        createdAt = ((java.util.Date) createdObj).getTime();
                                    } else if (createdObj instanceof Number) {
                                        createdAt = ((Number) createdObj).longValue();
                                    }

                                    Object updatedObj = doc.get("updatedAt");
                                    if (updatedObj instanceof com.google.firebase.Timestamp) {
                                        updatedAt = ((com.google.firebase.Timestamp) updatedObj).toDate().getTime();
                                    } else if (updatedObj instanceof Long) {
                                        updatedAt = (Long) updatedObj;
                                    } else if (updatedObj instanceof java.util.Date) {
                                        updatedAt = ((java.util.Date) updatedObj).getTime();
                                    } else if (updatedObj instanceof Number) {
                                        updatedAt = ((Number) updatedObj).longValue();
                                    }

                                    long localUpdatedAt = localTransaction.getUpdatedAt();
                                    long remoteUpdatedAt = updatedAt != null ? updatedAt : localUpdatedAt;

                                    Log.d(TAG, "Transaction " + transactionId + " compare timestamps: local=" +
                                            new java.util.Date(localUpdatedAt) + ", remote=" + new java.util.Date(remoteUpdatedAt) +
                                            ", needsSync=" + localTransaction.isNeedsSync());

                                    boolean shouldUpdateFromRemote = false;
                                    if (!localTransaction.isNeedsSync()) {
                                        // If local doesn't have pending changes, update whenever timestamps differ
                                        shouldUpdateFromRemote = (remoteUpdatedAt != localUpdatedAt);
                                    }

                                    if (shouldUpdateFromRemote) {
                                        // Firestore is newer → update local
                                        localTransaction.setUserName(doc.getString("userName"));
                                        localTransaction.setUserRole(doc.getString("userRole"));
                                        localTransaction.setCustomerId(doc.getString("customerId"));
                                        localTransaction.setCustomerName(doc.getString("customerName"));
                                        localTransaction.setOperatorId(doc.getString("operatorId"));
                                        localTransaction.setOperatorName(doc.getString("operatorName"));
                                        localTransaction.setActionId(doc.getString("actionId"));
                                        localTransaction.setActionName(doc.getString("actionName"));
                                        localTransaction.setTransactionType(doc.getString("transactionType"));
                                        localTransaction.setAmount(doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0);
                                        localTransaction.setStatus(doc.getString("status"));
                                        localTransaction.setNotes(doc.getString("notes"));
                                        localTransaction.setChannel(doc.getString("channel"));
                                        localTransaction.setCustomerPhone(doc.getString("customerPhone"));
                                        localTransaction.setCreditBefore(doc.getDouble("creditBefore") != null ? doc.getDouble("creditBefore") : 0.0);
                                        localTransaction.setCreditAfter(doc.getDouble("creditAfter") != null ? doc.getDouble("creditAfter") : 0.0);
                                        if (createdAt != null) localTransaction.setCreatedAt(createdAt);
                                        localTransaction.setUpdatedAt(remoteUpdatedAt);
                                        localTransaction.setNeedsSync(false);
                                        localTransaction.setLastSyncAt(System.currentTimeMillis());
                                        database.transactionDao().updateTransaction(localTransaction);
                                        Log.d(TAG, "Updated local transaction from Firestore: " + transactionId);
                                    } else {
                                        // Local is newer/same or has pending changes → mark synced/time
                                        localTransaction.setLastSyncAt(System.currentTimeMillis());
                                        database.transactionDao().updateTransaction(localTransaction);
                                        Log.d(TAG, "Skipped updating local transaction (kept local): " + transactionId);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error updating local transaction from Firestore: " + transactionId, e);
                                }
                            }
                        }
                        Log.d(TAG, "Pulled " + pulledCount + " new transactions from Firestore");
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Firestore transactions", e);
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read transactions from Firestore", e);
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "Bidirectional transaction sync completed for user: " + userId);
    }
    
    /**
     * Sync balance adjustments (bidirectional)
     */
    private void syncBalanceAdjustments(String userId) throws Exception {
        Log.d(TAG, "=== STARTING BALANCE ADJUSTMENT SYNC for user: " + userId + " ===");
        
        // Push local adjustments to Firestore
        List<BalanceAdjustmentEntity> localAdjustments = database.balanceAdjustmentDao().getNeedingSync();
        Log.d(TAG, "Found " + localAdjustments.size() + " local adjustments needing sync");
        
        for (BalanceAdjustmentEntity adjustment : localAdjustments) {
            try {
                Map<String, Object> adjustmentData = new HashMap<>();
                adjustmentData.put("id", adjustment.getId());
                adjustmentData.put("userId", adjustment.getUserId());
                adjustmentData.put("adjustmentType", adjustment.getAdjustmentType());
                adjustmentData.put("amount", adjustment.getAmount());
                adjustmentData.put("balanceBefore", adjustment.getBalanceBefore());
                adjustmentData.put("balanceAfter", adjustment.getBalanceAfter());
                adjustmentData.put("reason", adjustment.getReason());
                adjustmentData.put("adjustedBy", adjustment.getAdjustedBy());
                adjustmentData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(adjustment.getCreatedAt())));
                adjustmentData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(adjustment.getUpdatedAt())));
                
                firestore.collection("balance_adjustments").document(adjustment.getId())
                        .set(adjustmentData)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                adjustment.setNeedsSync(false);
                                adjustment.setLastSyncAt(System.currentTimeMillis());
                                database.balanceAdjustmentDao().updateAdjustment(adjustment);
                                Log.d(TAG, "Balance adjustment synced to Firestore: " + adjustment.getId());
                            }).start();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to sync balance adjustment to Firestore: " + adjustment.getId(), e);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error preparing balance adjustment data for sync: " + adjustment.getId(), e);
            }
        }
        
        // Pull balance adjustments from Firestore
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = {null};
        
        firestore.collection("balance_adjustments")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " balance adjustments in Firestore for user: " + userId);
                        int pulledCount = 0;
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String adjustmentId = doc.getId();
                            BalanceAdjustmentEntity localAdjustment = database.balanceAdjustmentDao().getAdjustmentById(adjustmentId);
                            
                            if (localAdjustment == null) {
                                // New adjustment from Firestore - add to local database
                                BalanceAdjustmentEntity newAdjustment = new BalanceAdjustmentEntity();
                                newAdjustment.setId(adjustmentId);
                                newAdjustment.setUserId(doc.getString("userId"));
                                newAdjustment.setAdjustmentType(doc.getString("adjustmentType"));
                                
                                Double amount = doc.getDouble("amount");
                                if (amount != null) {
                                    newAdjustment.setAmount(amount);
                                }
                                
                                Double balanceBefore = doc.getDouble("balanceBefore");
                                if (balanceBefore != null) {
                                    newAdjustment.setBalanceBefore(balanceBefore);
                                }
                                
                                Double balanceAfter = doc.getDouble("balanceAfter");
                                if (balanceAfter != null) {
                                    newAdjustment.setBalanceAfter(balanceAfter);
                                }
                                
                                newAdjustment.setReason(doc.getString("reason"));
                                newAdjustment.setAdjustedBy(doc.getString("adjustedBy"));
                                
                                // Robust timestamp parsing
                                Long aCreatedAt = null;
                                Long aUpdatedAt = null;
                                try {
                                    Object ts = doc.get("createdAt");
                                    if (ts instanceof com.google.firebase.Timestamp) {
                                        aCreatedAt = ((com.google.firebase.Timestamp) ts).toDate().getTime();
                                    } else if (ts instanceof Long) {
                                        aCreatedAt = (Long) ts;
                                    } else if (ts instanceof java.util.Date) {
                                        aCreatedAt = ((java.util.Date) ts).getTime();
                                    } else if (ts instanceof Number) {
                                        aCreatedAt = ((Number) ts).longValue();
                                    }
                                } catch (Exception ignore) {}
                                try {
                                    Object ts = doc.get("updatedAt");
                                    if (ts instanceof com.google.firebase.Timestamp) {
                                        aUpdatedAt = ((com.google.firebase.Timestamp) ts).toDate().getTime();
                                    } else if (ts instanceof Long) {
                                        aUpdatedAt = (Long) ts;
                                    } else if (ts instanceof java.util.Date) {
                                        aUpdatedAt = ((java.util.Date) ts).getTime();
                                    } else if (ts instanceof Number) {
                                        aUpdatedAt = ((Number) ts).longValue();
                                    }
                                } catch (Exception ignore) {}
                                
                                newAdjustment.setCreatedAt(aCreatedAt != null ? aCreatedAt : System.currentTimeMillis());
                                newAdjustment.setUpdatedAt(aUpdatedAt != null ? aUpdatedAt : System.currentTimeMillis());
                                newAdjustment.setNeedsSync(false);
                                newAdjustment.setLastSyncAt(System.currentTimeMillis());
                                
                                database.balanceAdjustmentDao().insertAdjustment(newAdjustment);
                                Log.d(TAG, "New balance adjustment synced from Firestore: " + adjustmentId);
                                pulledCount++;
                            } else {
                                Log.d(TAG, "Balance adjustment already exists locally: " + adjustmentId);
                            }
                        }
                        Log.d(TAG, "Pulled " + pulledCount + " new balance adjustments from Firestore");
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Firestore balance adjustments", e);
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read balance adjustments from Firestore", e);
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "=== BALANCE ADJUSTMENT SYNC COMPLETED for user: " + userId + " ===");
    }
    
    /**
     * Sync operator balances (bidirectional).
     * - Push local changes (needsSync = 1) to Firestore.
     * - Pull Firestore documents for this user and update/create local rows when Firestore is newer.
     */
    private void syncOperatorBalances(String userId) throws Exception {
        Log.d(TAG, "=== STARTING OPERATOR BALANCE SYNC for user: " + userId + " ===");
        
        // 1) Push local balances needing sync -> Firestore
        List<com.example.myapplication.database.entities.OperatorBalanceEntity> localBalances =
                database.operatorBalanceDao().getNeedingSyncForUser(userId);
        Log.d(TAG, "Found " + localBalances.size() + " local operator balances needing sync for user: " + userId);
        
        for (com.example.myapplication.database.entities.OperatorBalanceEntity balance : localBalances) {
            try {
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("id", balance.getId());
                data.put("userId", balance.getUserId());
                data.put("operatorId", balance.getOperatorId());
                data.put("balance", balance.getBalance());
                data.put("totalCreditUsed", balance.getTotalCreditUsed());
                data.put("totalCreditEarned", balance.getTotalCreditEarned());
                data.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(balance.getCreatedAt())));
                data.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(balance.getUpdatedAt())));
                
                firestore.collection("operator_balances").document(balance.getId())
                        .set(data)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                balance.setNeedsSync(false);
                                balance.setLastSyncAt(System.currentTimeMillis());
                                database.operatorBalanceDao().updateBalance(balance);
                                Log.d(TAG, "Operator balance pushed to Firestore: " + balance.getId());
                            }).start();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to push operator balance to Firestore: " + balance.getId(), e);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error preparing operator balance data for push: " + balance.getId(), e);
            }
        }

        // 2) Pull Firestore balances -> local when Firestore is newer / missing locally
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final Exception[] error = {null};

        firestore.collection("operator_balances")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        Log.d(TAG, "Reading operator_balances from Firestore for user " + userId
                                + " count=" + queryDocumentSnapshots.size());

                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String balanceId = doc.getId();
                            String opUserId = doc.getString("userId");
                            String operatorId = doc.getString("operatorId");
                            if (opUserId == null || operatorId == null) {
                                Log.w(TAG, "Skipping Firestore operator_balance " + balanceId
                                        + " due to missing userId/operatorId");
                                continue;
                            }

                            com.example.myapplication.database.entities.OperatorBalanceEntity local =
                                    database.operatorBalanceDao().getById(balanceId);

                            Long firestoreUpdatedAt = parseTimestamp(doc.get("updatedAt"));
                            if (firestoreUpdatedAt == null) {
                                firestoreUpdatedAt = System.currentTimeMillis();
                            }

                            if (local == null || firestoreUpdatedAt > local.getUpdatedAt()) {
                                com.example.myapplication.database.entities.OperatorBalanceEntity balance =
                                        (local != null) ? local
                                                : new com.example.myapplication.database.entities.OperatorBalanceEntity();
                                balance.setId(balanceId);
                                balance.setUserId(opUserId);
                                balance.setOperatorId(operatorId);
                                Double bal = doc.getDouble("balance");
                                Double used = doc.getDouble("totalCreditUsed");
                                Double earned = doc.getDouble("totalCreditEarned");
                                balance.setBalance(bal != null ? bal : 0.0);
                                balance.setTotalCreditUsed(used != null ? used : 0.0);
                                balance.setTotalCreditEarned(earned != null ? earned : 0.0);

                                Long createdAt = parseTimestamp(doc.get("createdAt"));
                                balance.setCreatedAt(createdAt != null ? createdAt : firestoreUpdatedAt);
                                balance.setUpdatedAt(firestoreUpdatedAt);
                                balance.setLastSyncAt(System.currentTimeMillis());
                                balance.setNeedsSync(false);

                                database.operatorBalanceDao().insertBalance(balance);
                                Log.d(TAG, "Updated local operator balance from Firestore: " + balanceId
                                        + " (operatorId=" + operatorId + ", balance=" + balance.getBalance() + ")");
                            } else {
                                Log.d(TAG, "Local operator balance is newer; keeping local for id=" + balanceId);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing operator_balances from Firestore", e);
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read operator_balances from Firestore", e);
                    error[0] = e;
                    latch.countDown();
                });

        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "Operator balance sync completed for user: " + userId);
    }
    
    /**
     * Sync commission rates (bidirectional)
     */
    private void syncCommissionRates(String userId) throws Exception {
        Log.d(TAG, "=== STARTING COMMISSION RATE SYNC for user: " + userId + " ===");
        
        // Push local commission rates to Firestore
        List<CommissionRateEntity> localRates = database.commissionRateDao().getCommissionRatesNeedingSync();
        Log.d(TAG, "Found " + localRates.size() + " local commission rates needing sync");
        
        for (CommissionRateEntity rate : localRates) {
            try {
                Map<String, Object> rateData = new HashMap<>();
                rateData.put("id", rate.getId());
                rateData.put("userId", rate.getUserId());
                rateData.put("userRole", rate.getUserRole());
                rateData.put("operatorId", rate.getOperatorId());
                rateData.put("operatorName", rate.getOperatorName());
                rateData.put("commissionRate", rate.getCommissionRate());
                rateData.put("taxRate", rate.getTaxRate());
                rateData.put("commissionRateWithTax", rate.getCommissionRateWithTax());
                rateData.put("transactionTypes", rate.getTransactionTypes());
                rateData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(rate.getCreatedAt())));
                rateData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(rate.getUpdatedAt())));
                
                firestore.collection("commission_rates").document(rate.getId())
                        .set(rateData)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                rate.setNeedsSync(false);
                                rate.setLastSyncAt(System.currentTimeMillis());
                                database.commissionRateDao().updateCommissionRate(rate);
                                Log.d(TAG, "Commission rate synced to Firestore: " + rate.getId());
                            }).start();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to sync commission rate to Firestore: " + rate.getId(), e);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error preparing commission rate data for sync: " + rate.getId(), e);
            }
        }
        
        // Pull commission rates from Firestore
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = {null};
        
        firestore.collection("commission_rates")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " commission rates in Firestore for user: " + userId);
                        int pulledCount = 0;
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String rateId = doc.getId();
                            CommissionRateEntity localRate = database.commissionRateDao().getCommissionRateById(rateId);
                            
                            if (localRate == null) {
                                CommissionRateEntity newRate = new CommissionRateEntity();
                                newRate.setId(rateId);
                                newRate.setUserId(doc.getString("userId"));
                                newRate.setUserRole(doc.getString("userRole"));
                                newRate.setOperatorId(doc.getString("operatorId"));
                                newRate.setOperatorName(doc.getString("operatorName"));
                                
                                Double commissionRate = doc.getDouble("commissionRate");
                                if (commissionRate != null) newRate.setCommissionRate(commissionRate);
                                
                                Double taxRate = doc.getDouble("taxRate");
                                if (taxRate != null) newRate.setTaxRate(taxRate);
                                
                                newRate.setTransactionTypes(doc.getString("transactionTypes"));
                                
                                // Timestamp parsing
                                Long createdAt = parseTimestamp(doc.get("createdAt"));
                                Long updatedAt = parseTimestamp(doc.get("updatedAt"));
                                newRate.setCreatedAt(createdAt != null ? createdAt : System.currentTimeMillis());
                                newRate.setUpdatedAt(updatedAt != null ? updatedAt : System.currentTimeMillis());
                                newRate.setNeedsSync(false);
                                newRate.setLastSyncAt(System.currentTimeMillis());
                                
                                database.commissionRateDao().insertCommissionRate(newRate);
                                Log.d(TAG, "New commission rate synced from Firestore: " + rateId);
                                pulledCount++;
                            }
                        }
                        Log.d(TAG, "Pulled " + pulledCount + " new commission rates from Firestore");
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Firestore commission rates", e);
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read commission rates from Firestore", e);
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "=== COMMISSION RATE SYNC COMPLETED for user: " + userId + " ===");
    }
    
    /**
     * Sync commissions (bidirectional)
     */
    private void syncCommissions(String userId) throws Exception {
        Log.d(TAG, "=== STARTING COMMISSION SYNC for user: " + userId + " ===");
        
        // Push local commissions to Firestore
        List<CommissionEntity> localCommissions = database.commissionDao().getCommissionsNeedingSync();
        Log.d(TAG, "Found " + localCommissions.size() + " local commissions needing sync");
        
        for (CommissionEntity commission : localCommissions) {
            try {
                Map<String, Object> commData = new HashMap<>();
                commData.put("id", commission.getId());
                commData.put("transactionId", commission.getTransactionId());
                commData.put("transactionType", commission.getTransactionType());
                commData.put("transactionAmount", commission.getTransactionAmount());
                commData.put("userId", commission.getUserId());
                commData.put("userName", commission.getUserName());
                commData.put("userRole", commission.getUserRole());
                commData.put("operatorId", commission.getOperatorId());
                commData.put("operatorName", commission.getOperatorName());
                commData.put("commissionRate", commission.getCommissionRate());
                commData.put("taxRate", commission.getTaxRate());
                commData.put("commissionAmount", commission.getCommissionAmount());
                commData.put("taxAmount", commission.getTaxAmount());
                commData.put("totalCommission", commission.getTotalCommission());
                commData.put("year", commission.getYear());
                commData.put("month", commission.getMonth());
                commData.put("day", commission.getDay());
                commData.put("commissionDate", new com.google.firebase.Timestamp(new java.util.Date(commission.getCommissionDate())));
                commData.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(commission.getCreatedAt())));
                commData.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(commission.getUpdatedAt())));
                
                firestore.collection("commissions").document(commission.getId())
                        .set(commData)
                        .addOnSuccessListener(aVoid -> {
                            new Thread(() -> {
                                commission.setNeedsSync(false);
                                commission.setLastSyncAt(System.currentTimeMillis());
                                database.commissionDao().updateCommission(commission);
                                Log.d(TAG, "Commission synced to Firestore: " + commission.getId());
                            }).start();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to sync commission to Firestore: " + commission.getId(), e);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error preparing commission data for sync: " + commission.getId(), e);
            }
        }
        
        // Pull commissions from Firestore
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = {null};
        
        firestore.collection("commissions")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " commissions in Firestore for user: " + userId);
                        int pulledCount = 0;
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String commId = doc.getId();
                            CommissionEntity localComm = database.commissionDao().getCommissionById(commId);
                            
                            if (localComm == null) {
                                CommissionEntity newComm = new CommissionEntity();
                                newComm.setId(commId);
                                newComm.setTransactionId(doc.getString("transactionId"));
                                newComm.setTransactionType(doc.getString("transactionType"));
                                
                                Double transactionAmount = doc.getDouble("transactionAmount");
                                if (transactionAmount != null) newComm.setTransactionAmount(transactionAmount);
                                
                                newComm.setUserId(doc.getString("userId"));
                                newComm.setUserName(doc.getString("userName"));
                                newComm.setUserRole(doc.getString("userRole"));
                                newComm.setOperatorId(doc.getString("operatorId"));
                                newComm.setOperatorName(doc.getString("operatorName"));
                                
                                Double commissionRate = doc.getDouble("commissionRate");
                                if (commissionRate != null) newComm.setCommissionRate(commissionRate);
                                
                                Double taxRate = doc.getDouble("taxRate");
                                if (taxRate != null) newComm.setTaxRate(taxRate);
                                
                                Double commissionAmount = doc.getDouble("commissionAmount");
                                if (commissionAmount != null) newComm.setCommissionAmount(commissionAmount);
                                
                                Double taxAmount = doc.getDouble("taxAmount");
                                if (taxAmount != null) newComm.setTaxAmount(taxAmount);
                                
                                Double totalCommission = doc.getDouble("totalCommission");
                                if (totalCommission != null) newComm.setTotalCommission(totalCommission);
                                
                                Long commissionDate = parseTimestamp(doc.get("commissionDate"));
                                if (commissionDate != null) newComm.setCommissionDate(commissionDate);
                                
                                Long createdAt = parseTimestamp(doc.get("createdAt"));
                                Long updatedAt = parseTimestamp(doc.get("updatedAt"));
                                newComm.setCreatedAt(createdAt != null ? createdAt : System.currentTimeMillis());
                                newComm.setUpdatedAt(updatedAt != null ? updatedAt : System.currentTimeMillis());
                                newComm.setNeedsSync(false);
                                newComm.setLastSyncAt(System.currentTimeMillis());
                                
                                database.commissionDao().insertCommission(newComm);
                                Log.d(TAG, "New commission synced from Firestore: " + commId);
                                pulledCount++;
                            }
                        }
                        Log.d(TAG, "Pulled " + pulledCount + " new commissions from Firestore");
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Firestore commissions", e);
                        error[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read commissions from Firestore", e);
                    error[0] = e;
                    latch.countDown();
                });
        
        latch.await();
        if (error[0] != null) {
            throw error[0];
        }
        
        Log.d(TAG, "=== COMMISSION SYNC COMPLETED for user: " + userId + " ===");
    }
    
    /**
     * Helper method to parse Firestore timestamps
     */
    private Long parseTimestamp(Object ts) {
        try {
            if (ts instanceof com.google.firebase.Timestamp) {
                return ((com.google.firebase.Timestamp) ts).toDate().getTime();
            } else if (ts instanceof Long) {
                return (Long) ts;
            } else if (ts instanceof java.util.Date) {
                return ((java.util.Date) ts).getTime();
            } else if (ts instanceof Number) {
                return ((Number) ts).longValue();
            }
        } catch (Exception ignore) {}
        return null;
    }
    
    /**
     * Check if first login and internet is available
     */
    public static boolean isFirstLogin(Context context, String userId) {
        AppDatabase database = AppDatabase.getDatabase(context);
        UserEntity user = database.userDao().getUserById(userId);
        return user == null || user.getLastSyncAt() == 0;
    }
}
