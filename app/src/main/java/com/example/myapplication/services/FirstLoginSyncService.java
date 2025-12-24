package com.example.myapplication.services;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.util.Log;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.example.myapplication.R;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.CustomerEntity;
import com.example.myapplication.database.entities.OperatorActionEntity;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.TransactionEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class FirstLoginSyncService {
    private static final String TAG = "FirstLoginSyncService";
    private final Context context;
    private final AppDatabase database;
    private final FirebaseFirestore firestore;

    public interface FirstLoginSyncCallback {
        void onSyncStarted();
        void onSyncProgress(String message, int current, int total);
        void onSyncComplete(boolean success, String message);
    }

    public FirstLoginSyncService(Context context) {
        this.context = context;
        this.database = AppDatabase.getDatabase(context);
        this.firestore = FirebaseFirestore.getInstance();
    }

    /**
     * Check if user has data in Firestore and needs first login sync
     */
    public void checkAndSyncFirstLogin(String userId, FirstLoginSyncCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Checking if user needs first login sync: " + userId);
                
                // 0) If we've already completed the first sync for this user, skip
                if (isFirstSyncDone(userId)) {
                    callback.onSyncComplete(true, "First sync already completed");
                    return;
                }
                
                // 1) If local DB already has any data for this user, skip (treat as already synced)
                if (hasLocalDataForUser(userId)) {
                    markFirstSyncDone(userId);
                    callback.onSyncComplete(true, "Local data present; skipping first sync");
                    return;
                }
                
                // 2) If offline, skip prompting (cannot sync on first login)
                if (!isOnline()) {
                    callback.onSyncComplete(true, "Offline; skipping first sync");
                    return;
                }
                
                // Check if user has any data in Firestore
                boolean hasData = checkUserHasDataInFirestore(userId);
                
                if (!hasData) {
                    Log.d(TAG, "No data found in Firestore for user: " + userId);
                    // Mark as done so we do not ask again on next logins
                    markFirstSyncDone(userId);
                    callback.onSyncComplete(true, "No data to sync");
                    return;
                }
                
                Log.d(TAG, "User has data in Firestore, starting compulsory sync");
                
                // Show compulsory sync dialog
                showCompulsorySyncDialog(userId, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "Error checking first login sync", e);
                callback.onSyncComplete(false, "Error checking sync: " + e.getMessage());
            }
        }).start();
    }

    private boolean checkUserHasDataInFirestore(String userId) {
        try {
            // Check both legacy and current field names for ownership
            CountDownLatch latch = new CountDownLatch(7);
            final boolean[] hasData = {false};
            
            // Customers: createdBy (legacy)
            firestore.collection("customers")
                    .whereEqualTo("createdBy", userId)
                    .limit(1)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            hasData[0] = true;
                        }
                        latch.countDown();
                    });
            // Customers: userId (current)
            firestore.collection("customers")
                    .whereEqualTo("userId", userId)
                    .limit(1)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            hasData[0] = true;
                        }
                        latch.countDown();
                    });
            
            // Operators: addedBy (legacy)
            firestore.collection("operators")
                    .whereEqualTo("addedBy", userId)
                    .limit(1)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            hasData[0] = true;
                        }
                        latch.countDown();
                    });
            // Operators: userId (current)
            firestore.collection("operators")
                    .whereEqualTo("userId", userId)
                    .limit(1)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            hasData[0] = true;
                        }
                        latch.countDown();
                    });
            
            // Operator actions: addedBy (legacy)
            firestore.collection("operator_actions")
                    .whereEqualTo("addedBy", userId)
                    .limit(1)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            hasData[0] = true;
                        }
                        latch.countDown();
                    });
            // Operator actions: userId (current)
            firestore.collection("operator_actions")
                    .whereEqualTo("userId", userId)
                    .limit(1)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            hasData[0] = true;
                        }
                        latch.countDown();
                    });
            
            // Transactions (unchanged)
            firestore.collection("transactions")
                    .whereEqualTo("userId", userId)
                    .limit(1)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            hasData[0] = true;
                        }
                        latch.countDown();
                    });
            
            latch.await();
            return hasData[0];
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking Firestore data", e);
            return false;
        }
    }

    private void showCompulsorySyncDialog(String userId, FirstLoginSyncCallback callback) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(context.getString(R.string.data_sync_required))
                    .setMessage(context.getString(R.string.data_found_in_cloud))
                    .setCancelable(false)
                    .setPositiveButton(context.getString(R.string.sync_now), (dialog, which) -> {
                        dialog.dismiss();
                        performCompulsorySync(userId, callback);
                    });
            
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    private void performCompulsorySync(String userId, FirstLoginSyncCallback callback) {
        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(context.getString(R.string.syncing_data));
        progressDialog.setMessage(context.getString(R.string.syncing_data_please_wait));
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                callback.onSyncStarted();
                
                int totalSteps = 5;
                int currentStep = 0;
                
                // Step 1: Sync users (to get disabled status)
                callback.onSyncProgress("Syncing users...", ++currentStep, totalSteps);
                syncUsersFromFirestore(userId);
                
                // Step 2: Sync customers
                callback.onSyncProgress(context.getString(R.string.syncing_customers), ++currentStep, totalSteps);
                syncCustomersFromFirestore(userId);
                
                // Step 3: Sync operators
                callback.onSyncProgress(context.getString(R.string.syncing_operators), ++currentStep, totalSteps);
                syncOperatorsFromFirestore(userId);
                
                // Step 4: Sync operator actions
                callback.onSyncProgress(context.getString(R.string.syncing_operator_actions), ++currentStep, totalSteps);
                syncOperatorActionsFromFirestore(userId);
                
                // Step 5: Sync transactions
                callback.onSyncProgress(context.getString(R.string.syncing_transactions), ++currentStep, totalSteps);
                syncTransactionsFromFirestore(userId);
                
                ((android.app.Activity) context).runOnUiThread(() -> {
                    progressDialog.dismiss();
                    markFirstSyncDone(userId);
                    callback.onSyncComplete(true, context.getString(R.string.data_synced_successfully));
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error during compulsory sync", e);
                ((android.app.Activity) context).runOnUiThread(() -> {
                    progressDialog.dismiss();
                    callback.onSyncComplete(false, context.getString(R.string.sync_failed_error, e.getMessage()));
                });
            }
        }).start();
    }
    
    // ---- Helpers ----
    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } catch (Exception e) {
            Log.w(TAG, "Connectivity check failed", e);
            return false;
        }
    }

    private boolean hasLocalDataForUser(String userId) {
        try {
            int customerCount = 0;
            try { customerCount = database.customerDao().getCustomerCountByUser(userId); } catch (Exception ignored) {}
            int operatorCount = 0;
            try { List<com.example.myapplication.database.entities.OperatorEntity> ops = database.operatorDao().getByUser(userId); operatorCount = (ops != null ? ops.size() : 0); } catch (Exception ignored) {}
            int txCount = 0;
            try { List<com.example.myapplication.database.entities.TransactionEntity> txs = database.transactionDao().getTransactionsByUser(userId); txCount = (txs != null ? txs.size() : 0); } catch (Exception ignored) {}
            return (customerCount + operatorCount + txCount) > 0;
        } catch (Exception e) {
            Log.w(TAG, "Local data check failed", e);
            return false;
        }
    }

    private boolean isFirstSyncDone(String userId) {
        SharedPreferences prefs = context.getSharedPreferences("first_login_sync", Context.MODE_PRIVATE);
        return prefs.getBoolean("first_sync_done_" + userId, false);
    }

    private void markFirstSyncDone(String userId) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("first_login_sync", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("first_sync_done_" + userId, true).apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to mark first sync done", e);
        }
    }

    private void syncCustomersFromFirestore(String userId) throws Exception {
        // Fetch using both legacy and current owner fields
        CountDownLatch latch = new CountDownLatch(2);
        final Exception[] error = {null};

        // Legacy: createdBy
        firestore.collection("customers")
                .whereEqualTo("createdBy", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String customerId = doc.getId();
                            CustomerEntity existingCustomer = database.customerDao().getCustomerById(customerId);

                            if (existingCustomer == null) {
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
                                newCustomer.setCreatedAt(getTimestampAsMillis(doc, "createdAt"));
                                newCustomer.setUpdatedAt(getTimestampAsMillis(doc, "updatedAt"));
                                newCustomer.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                newCustomer.setNeedsSync(false);
                                newCustomer.setLastSyncAt(System.currentTimeMillis());

                                database.customerDao().insertCustomer(newCustomer);
                                Log.d(TAG, "Synced customer from Firestore: " + customerId);
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

        // Current: userId
        firestore.collection("customers")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String customerId = doc.getId();
                            CustomerEntity existingCustomer = database.customerDao().getCustomerById(customerId);

                            if (existingCustomer == null) {
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
                                // Map current schema owner field to createdBy locally
                                String owner = doc.getString("createdBy");
                                if (owner == null || owner.isEmpty()) owner = doc.getString("userId");
                                newCustomer.setCreatedBy(owner);
                                newCustomer.setCreatedAt(getTimestampAsMillis(doc, "createdAt"));
                                newCustomer.setUpdatedAt(getTimestampAsMillis(doc, "updatedAt"));
                                newCustomer.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                newCustomer.setNeedsSync(false);
                                newCustomer.setLastSyncAt(System.currentTimeMillis());

                                database.customerDao().insertCustomer(newCustomer);
                                Log.d(TAG, "Synced customer from Firestore (userId): " + customerId);
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
    }

    private void syncOperatorsFromFirestore(String userId) throws Exception {
        // Fetch using both legacy and current owner fields
        CountDownLatch latch = new CountDownLatch(2);
        final Exception[] error = {null};

        // Legacy: addedBy
        firestore.collection("operators")
                .whereEqualTo("addedBy", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String operatorId = doc.getId();
                            OperatorEntity existingOperator = database.operatorDao().getById(operatorId);

                            if (existingOperator == null) {
                                OperatorEntity newOperator = new OperatorEntity();
                                newOperator.setId(operatorId);
                                newOperator.setName(doc.getString("name"));
                                newOperator.setCode(doc.getString("code"));
                                newOperator.setType(doc.getString("type"));
                                newOperator.setColor(doc.getString("color"));
                                Double transferRate = doc.getDouble("transferRate");
                                newOperator.setTransferRate(transferRate != null ? transferRate : 0.0);
                                newOperator.setEnabled(doc.getBoolean("enabled") != null ? doc.getBoolean("enabled") : true);
                                newOperator.setAddedBy(doc.getString("addedBy"));  // Fixed: use "addedBy"
                                newOperator.setCreatedAt(getTimestampAsMillis(doc, "createdAt"));
                                newOperator.setUpdatedAt(getTimestampAsMillis(doc, "updatedAt"));
                                newOperator.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                newOperator.setNeedsSync(false);
                                newOperator.setLastSyncAt(System.currentTimeMillis());

                                database.operatorDao().insertOperator(newOperator);
                                Log.d(TAG, "Synced operator from Firestore: " + operatorId);
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

        // Current: userId
        firestore.collection("operators")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String operatorId = doc.getId();
                            OperatorEntity existingOperator = database.operatorDao().getById(operatorId);

                            if (existingOperator == null) {
                                OperatorEntity newOperator = new OperatorEntity();
                                newOperator.setId(operatorId);
                                newOperator.setName(doc.getString("name"));
                                newOperator.setCode(doc.getString("code"));
                                newOperator.setType(doc.getString("type"));
                                newOperator.setColor(doc.getString("color"));
                                Double transferRate = doc.getDouble("transferRate");
                                newOperator.setTransferRate(transferRate != null ? transferRate : 0.0);
                                newOperator.setEnabled(doc.getBoolean("enabled") != null ? doc.getBoolean("enabled") : true);
                                String owner = doc.getString("addedBy");
                                if (owner == null || owner.isEmpty()) owner = doc.getString("userId");
                                newOperator.setAddedBy(owner);
                                newOperator.setCreatedAt(getTimestampAsMillis(doc, "createdAt"));
                                newOperator.setUpdatedAt(getTimestampAsMillis(doc, "updatedAt"));
                                newOperator.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                newOperator.setNeedsSync(false);
                                newOperator.setLastSyncAt(System.currentTimeMillis());

                                database.operatorDao().insertOperator(newOperator);
                                Log.d(TAG, "Synced operator from Firestore (userId): " + operatorId);
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
    }

    private void syncOperatorActionsFromFirestore(String userId) throws Exception {
        // Fetch using both legacy and current owner fields
        CountDownLatch latch = new CountDownLatch(2);
        final Exception[] error = {null};

        // Legacy: addedBy
        firestore.collection("operator_actions")
                .whereEqualTo("addedBy", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String actionId = doc.getId();
                            OperatorActionEntity existingAction = database.operatorActionDao().getById(actionId);

                            if (existingAction == null) {
                                OperatorActionEntity newAction = new OperatorActionEntity();
                                newAction.setId(actionId);
                                newAction.setName(doc.getString("name"));
                                newAction.setActionCode(doc.getString("actionCode"));
                                newAction.setType(doc.getString("type"));
                                newAction.setOperatorId(doc.getString("operatorId"));
                                newAction.setUssdTemplate(doc.getString("ussdTemplate"));
                                newAction.setRequiredFieldsJson(doc.getString("requiredFieldsJson"));
                                newAction.setAddedBy(doc.getString("addedBy"));  // Fixed: use "addedBy"
                                newAction.setCreatedAt(getTimestampAsMillis(doc, "createdAt"));
                                newAction.setUpdatedAt(getTimestampAsMillis(doc, "updatedAt"));
                                newAction.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                newAction.setDisableUssd(doc.getBoolean("disableUssd") != null ? doc.getBoolean("disableUssd") : false);
                                newAction.setNeedsSync(false);
                                newAction.setLastSyncAt(System.currentTimeMillis());

                                database.operatorActionDao().insertAction(newAction);
                                Log.d(TAG, "Synced operator action from Firestore: " + actionId);
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

        // Current: userId
        firestore.collection("operator_actions")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String actionId = doc.getId();
                            OperatorActionEntity existingAction = database.operatorActionDao().getById(actionId);

                            if (existingAction == null) {
                                OperatorActionEntity newAction = new OperatorActionEntity();
                                newAction.setId(actionId);
                                newAction.setName(doc.getString("name"));
                                newAction.setActionCode(doc.getString("actionCode"));
                                newAction.setType(doc.getString("type"));
                                newAction.setOperatorId(doc.getString("operatorId"));
                                newAction.setUssdTemplate(doc.getString("ussdTemplate"));
                                newAction.setRequiredFieldsJson(doc.getString("requiredFieldsJson"));
                                String owner = doc.getString("addedBy");
                                if (owner == null || owner.isEmpty()) owner = doc.getString("userId");
                                newAction.setAddedBy(owner);
                                newAction.setCreatedAt(getTimestampAsMillis(doc, "createdAt"));
                                newAction.setUpdatedAt(getTimestampAsMillis(doc, "updatedAt"));
                                newAction.setActive(doc.getBoolean("isActive") != null ? doc.getBoolean("isActive") : true);
                                newAction.setDisableUssd(doc.getBoolean("disableUssd") != null ? doc.getBoolean("disableUssd") : false);
                                newAction.setNeedsSync(false);
                                newAction.setLastSyncAt(System.currentTimeMillis());

                                database.operatorActionDao().insertAction(newAction);
                                Log.d(TAG, "Synced operator action from Firestore (userId): " + actionId);
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
    }

    private void syncTransactionsFromFirestore(String userId) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] error = {null};

        firestore.collection("transactions")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String transactionId = doc.getId();
                            TransactionEntity existingTransaction = database.transactionDao().getTransactionById(transactionId);

                            if (existingTransaction == null) {
                                TransactionEntity newTransaction = new TransactionEntity();
                                newTransaction.setId(transactionId);
                                newTransaction.setUserId(doc.getString("userId"));
                                newTransaction.setUserName(doc.getString("userName"));
                                newTransaction.setUserRole(doc.getString("userRole"));
                                newTransaction.setCustomerId(doc.getString("customerId"));
                                newTransaction.setCustomerName(doc.getString("customerName"));
                                newTransaction.setCustomerPhone(doc.getString("customerPhone"));
                                newTransaction.setOperatorId(doc.getString("operatorId"));
                                newTransaction.setOperatorName(doc.getString("operatorName"));
                                newTransaction.setActionId(doc.getString("actionId"));
                                newTransaction.setActionName(doc.getString("actionName"));
                                newTransaction.setTransactionType(doc.getString("transactionType"));
                                newTransaction.setAmount(doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0);
                                newTransaction.setStatus(doc.getString("status"));
                                newTransaction.setNotes(doc.getString("notes"));
                                newTransaction.setChannel(doc.getString("channel"));
                                newTransaction.setCreditBefore(doc.getDouble("creditBefore") != null ? doc.getDouble("creditBefore") : 0.0);
                                newTransaction.setCreditAfter(doc.getDouble("creditAfter") != null ? doc.getDouble("creditAfter") : 0.0);

                                // Handle timestamps safely
                                newTransaction.setCreatedAt(getTimestampAsMillis(doc, "createdAt"));
                                newTransaction.setUpdatedAt(getTimestampAsMillis(doc, "updatedAt"));

                                newTransaction.setNeedsSync(false);
                                newTransaction.setLastSyncAt(System.currentTimeMillis());

                                database.transactionDao().insertTransaction(newTransaction);
                                Log.d(TAG, "Synced transaction from Firestore: " + transactionId);
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
    }
    /**
     * Safely extract timestamp values from Firestore documents.
     * Supports Long, com.google.firebase.Timestamp, java.util.Date, and generic Number.
     */
    private long getTimestampAsMillis(QueryDocumentSnapshot doc, String fieldName) {
        try {
            // First try to get as Firestore Timestamp (most common case)
            Object raw = doc.get(fieldName);
            if (raw instanceof com.google.firebase.Timestamp) {
                long timestamp = ((com.google.firebase.Timestamp) raw).toDate().getTime();
                Log.d(TAG, "Parsed " + fieldName + " as Firestore Timestamp: " + new java.util.Date(timestamp).toString());
                return timestamp;
            }
            
            // Then try as Long
            if (raw instanceof Long) {
                Log.d(TAG, "Parsed " + fieldName + " as Long: " + new java.util.Date((Long) raw).toString());
                return (Long) raw;
            }
            
            // Then try as Date
            if (raw instanceof java.util.Date) {
                long timestamp = ((java.util.Date) raw).getTime();
                Log.d(TAG, "Parsed " + fieldName + " as Date: " + new java.util.Date(timestamp).toString());
                return timestamp;
            }
            
            // Then try as Number
            if (raw instanceof Number) {
                long timestamp = ((Number) raw).longValue();
                Log.d(TAG, "Parsed " + fieldName + " as Number: " + new java.util.Date(timestamp).toString());
                return timestamp;
            }
            
            Log.w(TAG, "Unknown timestamp type for " + fieldName + ": " + (raw != null ? raw.getClass().getSimpleName() : "null"));
        } catch (Exception e) {
            Log.w(TAG, "Could not parse timestamp for field: " + fieldName, e);
        }
        Log.w(TAG, "Using current time as fallback for " + fieldName);
        return System.currentTimeMillis();
    }
    
    /**
     * Sync users from Firestore to get disabled status
     */
    private void syncUsersFromFirestore(String userId) throws Exception {
        Log.d(TAG, "Starting user sync for: " + userId);
        
        // Get current user from Firestore
        firestore.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        try {
                            // Get user data from Firestore
                            String email = documentSnapshot.getString("email");
                            String name = documentSnapshot.getString("name");
                            String phone = documentSnapshot.getString("phone");
                            String role = documentSnapshot.getString("role");
                            String dealerId = documentSnapshot.getString("dealerId");
                            Boolean active = documentSnapshot.getBoolean("active");
                            Boolean disabled = documentSnapshot.getBoolean("disabled");
                            
                            // Get credit data
                            Double virtualCredit = documentSnapshot.getDouble("virtualCredit");
                            Double totalCreditUsed = documentSnapshot.getDouble("totalCreditUsed");
                            Double totalCreditEarned = documentSnapshot.getDouble("totalCreditEarned");
                            Double cashBalance = documentSnapshot.getDouble("cashBalance");
                            
                            // Create or update user entity
                            com.example.myapplication.database.entities.UserEntity userEntity = 
                                database.userDao().getUserById(userId);
                            
                            if (userEntity == null) {
                                // Create new user entity
                                userEntity = new com.example.myapplication.database.entities.UserEntity();
                                userEntity.setUid(userId);
                            }
                            
                            // Update user data
                            userEntity.setEmail(email);
                            userEntity.setName(name);
                            userEntity.setPhone(phone);
                            userEntity.setRole(role);
                            userEntity.setDealerId(dealerId);
                            userEntity.setActive(active != null ? active : true);
                            userEntity.setDisabled(disabled != null ? disabled : false); // Default to false
                            
                            // Update credit data
                            if (virtualCredit != null) {
                                userEntity.setVirtualCredit(virtualCredit);
                            }
                            if (totalCreditUsed != null) {
                                userEntity.setTotalCreditUsed(totalCreditUsed);
                            }
                            if (totalCreditEarned != null) {
                                userEntity.setTotalCreditEarned(totalCreditEarned);
                            }
                            if (cashBalance != null) {
                                userEntity.setCashBalance(cashBalance);
                            }
                            
                            userEntity.setUpdatedAt(System.currentTimeMillis());
                            userEntity.setLastSyncAt(System.currentTimeMillis());
                            
                            // Save to local database
                            database.userDao().insertUser(userEntity);
                            Log.d(TAG, "Synced user from Firestore: " + userId + " (disabled: " + userEntity.isDisabled() + ")");
                            
                            // If this is a dealer, also sync all agents under this dealer
                            if ("dealer".equals(role)) {
                                Log.d(TAG, "Dealer detected, syncing all agents under dealer: " + userId);
                                syncAllAgentsForDealer(userId);
                            }
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error syncing user: " + userId, e);
                        }
                    } else {
                        Log.w(TAG, "User document not found in Firestore: " + userId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to sync user: " + userId, e);
                });
    }
    
    /**
     * Sync all agents under a dealer for dashboard stats
     */
    private void syncAllAgentsForDealer(String dealerId) {
        Log.d(TAG, "Syncing all agents for dealer: " + dealerId);
        
        firestore.collection("users")
                .whereEqualTo("dealerId", dealerId)
                .whereEqualTo("role", "agent")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " agents for dealer: " + dealerId);
                    
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        try {
                            String agentId = doc.getId();
                            String email = doc.getString("email");
                            String name = doc.getString("name");
                            String phone = doc.getString("phone");
                            String role = doc.getString("role");
                            String agentDealerId = doc.getString("dealerId");
                            Boolean active = doc.getBoolean("active");
                            Boolean disabled = doc.getBoolean("disabled");
                            
                            // Get credit data
                            Double virtualCredit = doc.getDouble("virtualCredit");
                            Double totalCreditUsed = doc.getDouble("totalCreditUsed");
                            Double totalCreditEarned = doc.getDouble("totalCreditEarned");
                            Double cashBalance = doc.getDouble("cashBalance");
                            
                            // Create or update agent entity
                            com.example.myapplication.database.entities.UserEntity agentEntity = 
                                database.userDao().getUserById(agentId);
                            
                            if (agentEntity == null) {
                                agentEntity = new com.example.myapplication.database.entities.UserEntity();
                                agentEntity.setUid(agentId);
                            }
                            
                            // Update agent data
                            agentEntity.setEmail(email);
                            agentEntity.setName(name);
                            agentEntity.setPhone(phone);
                            agentEntity.setRole(role);
                            agentEntity.setDealerId(agentDealerId);
                            agentEntity.setActive(active != null ? active : true);
                            agentEntity.setDisabled(disabled != null ? disabled : false);
                            
                            // Update credit data
                            if (virtualCredit != null) {
                                agentEntity.setVirtualCredit(virtualCredit);
                            }
                            if (totalCreditUsed != null) {
                                agentEntity.setTotalCreditUsed(totalCreditUsed);
                            }
                            if (totalCreditEarned != null) {
                                agentEntity.setTotalCreditEarned(totalCreditEarned);
                            }
                            if (cashBalance != null) {
                                agentEntity.setCashBalance(cashBalance);
                            }
                            
                            agentEntity.setUpdatedAt(System.currentTimeMillis());
                            agentEntity.setLastSyncAt(System.currentTimeMillis());
                            
                            // Save to local database
                            database.userDao().insertUser(agentEntity);
                            Log.d(TAG, "Synced agent: " + agentId + " (credit: " + agentEntity.getVirtualCredit() + ")");
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error syncing agent: " + doc.getId(), e);
                        }
                    }
                    
                    Log.d(TAG, "Completed syncing all agents for dealer: " + dealerId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to sync agents for dealer: " + dealerId, e);
                });
    }
}
