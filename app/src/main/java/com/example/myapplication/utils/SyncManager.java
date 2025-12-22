package com.example.myapplication.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.CustomerEntity;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.OperatorActionEntity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncManager {
    private static final String TAG = "SyncManager";
    
    private Context context;
    private AppDatabase database;
    private FirebaseFirestore firestore;
    private ConnectivityManager connectivityManager;
    private SessionManager sessionManager;
    
    public SyncManager(Context context) {
        this.context = context;
        this.database = AppDatabase.getDatabase(context);
        this.firestore = FirebaseFirestore.getInstance();
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.sessionManager = new SessionManager(context);
    }
    
    /**
     * Check if device has internet connection
     */
    public boolean isOnline() {
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
    
    /**
     * Sync all customers that need sync
     */
    public void syncCustomers() {
        if (!isOnline()) {
            Log.d(TAG, "No internet connection, skipping sync");
            return;
        }
        
        new Thread(() -> {
            try {
                List<CustomerEntity> customersToSync = database.customerDao().getCustomersNeedingSync();
                
                if (customersToSync.isEmpty()) {
                    Log.d(TAG, "No customers need sync");
                    return;
                }
                
                Log.d(TAG, "Syncing " + customersToSync.size() + " customers");
                
                WriteBatch batch = firestore.batch();
                
                for (CustomerEntity customer : customersToSync) {
                    // Create Firestore document reference
                    String documentId = customer.getId();
                    if (documentId == null || documentId.isEmpty()) {
                        documentId = "customer_" + System.currentTimeMillis();
                        customer.setId(documentId);
                    }
                    
                    // Convert to Firestore document
                    Map<String, Object> customerData = new HashMap<>();
                    customerData.put("id", customer.getId());
                    customerData.put("fullName", customer.getFullName());
                    customerData.put("dateOfBirth", customer.getDateOfBirth());
                    customerData.put("nationalIdNumber", customer.getNationalIdNumber());
                    customerData.put("issueDate", customer.getIssueDate());
                    customerData.put("expiryDate", customer.getExpiryDate());
                    customerData.put("phoneNumber", customer.getPhoneNumber());
                    customerData.put("address", customer.getAddress());
                    customerData.put("email", customer.getEmail());
                    customerData.put("createdBy", customer.getCreatedBy());
                    customerData.put("createdAt", customer.getCreatedAt());
                    customerData.put("updatedAt", customer.getUpdatedAt());
                    customerData.put("isActive", customer.isActive());
                    customerData.put("lastSyncAt", System.currentTimeMillis());
                    
                    // If record is soft-deleted locally (isActive=false), propagate deletion as a tombstone
                    if (!customer.isActive()) {
                        customerData.put("isActive", false);
                        batch.set(firestore.collection("customers").document(documentId), customerData);
                    } else {
                        // Normal upsert
                        batch.set(firestore.collection("customers").document(documentId), customerData);
                    }
                }
                
                // Commit batch
                batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Successfully synced " + customersToSync.size() + " customers");
                        android.os.Handler handler = new android.os.Handler(context.getMainLooper());
                        handler.post(() -> android.widget.Toast.makeText(context, "Customers synced", android.widget.Toast.LENGTH_SHORT).show());
                        
                        // Mark customers as synced
                        new Thread(() -> {
                            long syncTime = System.currentTimeMillis();
                            for (CustomerEntity customer : customersToSync) {
                                database.customerDao().markAsSynced(customer.getId(), syncTime);
                            }
                        }).start();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error syncing customers", e);
                        android.os.Handler handler = new android.os.Handler(context.getMainLooper());
                        handler.post(() -> android.widget.Toast.makeText(context, "Sync failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
                    });
                
            } catch (Exception e) {
                Log.e(TAG, "Error in sync process", e);
            }
        }).start();
    }

    /**
     * Sync operators (upload local changes)
     */
    public void syncOperators() {
        if (!isOnline()) return;
        new Thread(() -> {
            try {
                java.util.List<OperatorEntity> ops = database.operatorDao().getNeedingSync();
                if (ops.isEmpty()) return;
                WriteBatch batch = firestore.batch();
                for (OperatorEntity op : ops) {
                    String id = op.getId();
                    if (id == null || id.isEmpty()) id = java.util.UUID.randomUUID().toString();
                    op.setId(id);
                    java.util.Map<String,Object> data = new java.util.HashMap<>();
                    data.put("id", op.getId());
                    data.put("name", op.getName());
                    data.put("type", op.getType());
                    data.put("enabled", op.isEnabled());
                    data.put("code", op.getCode());
                    data.put("color", op.getColor());
                    data.put("addedBy", op.getAddedBy());
                    data.put("createdAt", op.getCreatedAt());
                    data.put("updatedAt", op.getUpdatedAt());
                    data.put("isActive", op.isActive());
                    data.put("lastSyncAt", System.currentTimeMillis());
                    batch.set(firestore.collection("operators").document(id), data);
                }
                batch.commit().addOnSuccessListener(v -> new Thread(() -> {
                    long t = System.currentTimeMillis();
                    for (OperatorEntity op : ops) database.operatorDao().markSynced(op.getId(), t);
                }).start());
            } catch (Exception e) { Log.e(TAG, "syncOperators", e); }
        }).start();
    }

    /**
     * Sync operator actions (upload local changes)
     */
    public void syncOperatorActions() {
        if (!isOnline()) return;
        new Thread(() -> {
            try {
                java.util.List<OperatorActionEntity> acts = database.operatorActionDao().getNeedingSync();
                if (acts.isEmpty()) return;
                WriteBatch batch = firestore.batch();
                for (OperatorActionEntity a : acts) {
                    String id = a.getId();
                    if (id == null || id.isEmpty()) id = java.util.UUID.randomUUID().toString();
                    a.setId(id);
                    java.util.Map<String,Object> data = new java.util.HashMap<>();
                    data.put("id", a.getId());
                    data.put("operatorId", a.getOperatorId());
                    data.put("name", a.getName());
                    data.put("type", a.getType());
                    data.put("actionCode", a.getActionCode());
                    data.put("ussdTemplate", a.getUssdTemplate());
                    data.put("requiredFieldsJson", a.getRequiredFieldsJson());
                    data.put("addedBy", a.getAddedBy());
                    data.put("createdAt", a.getCreatedAt());
                    data.put("updatedAt", a.getUpdatedAt());
                    data.put("isActive", a.isActive());
                    data.put("disableUssd", a.isDisableUssd());
                    data.put("lastSyncAt", System.currentTimeMillis());
                    batch.set(firestore.collection("operator_actions").document(id), data);
                }
                batch.commit().addOnSuccessListener(v -> new Thread(() -> {
                    long t = System.currentTimeMillis();
                    for (OperatorActionEntity a : acts) database.operatorActionDao().markSynced(a.getId(), t);
                }).start());
            } catch (Exception e) { Log.e(TAG, "syncOperatorActions", e); }
        }).start();
    }

    /** Download operators for current user */
    public void downloadOperators() {
        if (!isOnline()) return;
        String uid = null; var u = sessionManager.getUserFromSession(); if (u!=null) uid = u.getUid();
        if (uid == null || uid.isEmpty()) return;
        firestore.collection("operators")
                .whereEqualTo("isActive", true)
                .whereEqualTo("addedBy", uid)
                .get()
                .addOnSuccessListener(snap -> new Thread(() -> {
                    for (var d : snap) {
                        try {
                            OperatorEntity op = new OperatorEntity();
                            java.util.Map<String,Object> m = d.getData();
                            op.setId((String)m.get("id"));
                            op.setName((String)m.get("name"));
                            op.setType((String)m.get("type"));
                            Object en=m.get("enabled"); op.setEnabled(en instanceof Boolean ? (Boolean)en : true);
                            op.setAddedBy((String)m.get("addedBy"));
                            op.setCode((String)m.get("code"));
                            op.setColor((String)m.get("color"));
                            Object tr=m.get("transferRate"); op.setTransferRate(tr instanceof Number ? ((Number)tr).doubleValue() : 0.0);
                            Object ia=m.get("isActive"); op.setActive(!(ia instanceof Boolean) || (Boolean)ia);
                            op.setNeedsSync(false);
                            // Skip overwrite if local pending change
                            OperatorEntity local = database.operatorDao().getById(op.getId());
                            if (local != null && local.isNeedsSync()) continue;
                            database.operatorDao().insertOperator(op);
                        } catch (Exception ex) { Log.e(TAG, "downloadOperators item", ex); }
                    }
                }).start())
                .addOnFailureListener(e -> Log.e(TAG, "downloadOperators", e));
    }

    /** Download operator actions for current user */
    public void downloadOperatorActions() {
        if (!isOnline()) return;
        String uid = null; var u = sessionManager.getUserFromSession(); if (u!=null) uid = u.getUid();
        if (uid == null || uid.isEmpty()) return;
        firestore.collection("operator_actions")
                .whereEqualTo("isActive", true)
                .whereEqualTo("addedBy", uid)
                .get()
                .addOnSuccessListener(snap -> new Thread(() -> {
                    for (var d : snap) {
                        try {
                            OperatorActionEntity a = new OperatorActionEntity();
                            java.util.Map<String,Object> m = d.getData();
                            a.setId((String)m.get("id"));
                            a.setOperatorId((String)m.get("operatorId"));
                            a.setName((String)m.get("name"));
                            a.setType((String)m.get("type"));
                            a.setActionCode((String)m.get("actionCode"));
                            a.setUssdTemplate((String)m.get("ussdTemplate"));
                            a.setRequiredFieldsJson((String)m.get("requiredFieldsJson"));
                            a.setAddedBy((String)m.get("addedBy"));
                            Object ia=m.get("isActive"); a.setActive(!(ia instanceof Boolean) || (Boolean)ia);
                            a.setNeedsSync(false);
                            OperatorActionEntity local = database.operatorActionDao().getById(a.getId());
                            if (local != null && local.isNeedsSync()) continue;
                            database.operatorActionDao().insertAction(a);
                        } catch (Exception ex) { Log.e(TAG, "downloadOperatorActions item", ex); }
                    }
                }).start())
                .addOnFailureListener(e -> Log.e(TAG, "downloadOperatorActions", e));
    }
    
    /**
     * Download customers from Firestore to local database
     */
    public void downloadCustomers() {
        if (!isOnline()) {
            Log.d(TAG, "No internet connection, skipping download");
            return;
        }
        
        new Thread(() -> {
            try {
                String uid = null;
                com.example.myapplication.database.entities.UserEntity u = sessionManager.getUserFromSession();
                if (u != null) uid = u.getUid();

                com.google.firebase.firestore.Query query = firestore.collection("customers")
                    .whereEqualTo("isActive", true);
                if (uid != null && !uid.isEmpty()) {
                    query = query.whereEqualTo("createdBy", uid);
                }

                query
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        Log.d(TAG, "Downloaded " + queryDocumentSnapshots.size() + " customers from Firestore");
                        
                        new Thread(() -> {
                            for (var document : queryDocumentSnapshots) {
                                try {
                                    Map<String, Object> data = document.getData();
                                    
                                    CustomerEntity customer = new CustomerEntity();
                                    String id = (String) data.get("id");
                                    if (id == null || id.isEmpty()) {
                                        id = document.getId();
                                    }
                                    customer.setId(id);
                                    String fullName = (String) data.get("fullName");
                                    if (fullName == null || fullName.isEmpty()) {
                                        fullName = (String) data.get("name");
                                    }
                                    customer.setFullName(fullName);
                                    customer.setDateOfBirth((String) data.get("dateOfBirth"));
                                    customer.setNationalIdNumber((String) data.get("nationalIdNumber"));
                                    customer.setIssueDate((String) data.get("issueDate"));
                                    customer.setExpiryDate((String) data.get("expiryDate"));
                                    customer.setPhoneNumber((String) data.get("phoneNumber"));
                                    customer.setAddress((String) data.get("address"));
                                    customer.setEmail((String) data.get("email"));
                                    customer.setCreatedBy((String) data.get("createdBy"));
                                    
                                    // Handle timestamps
                                    Object createdAt = data.get("createdAt");
                                    if (createdAt instanceof Long) {
                                        customer.setCreatedAt((Long) createdAt);
                                    } else if (createdAt instanceof com.google.firebase.Timestamp) {
                                        customer.setCreatedAt(((com.google.firebase.Timestamp) createdAt).toDate().getTime());
                                    }
                                    
                                    Object updatedAt = data.get("updatedAt");
                                    if (updatedAt instanceof Long) {
                                        customer.setUpdatedAt((Long) updatedAt);
                                    } else if (updatedAt instanceof com.google.firebase.Timestamp) {
                                        customer.setUpdatedAt(((com.google.firebase.Timestamp) updatedAt).toDate().getTime());
                                    }
                                    
                                    Object lastSyncAt = data.get("lastSyncAt");
                                    if (lastSyncAt instanceof Long) {
                                        customer.setLastSyncAt((Long) lastSyncAt);
                                    } else if (lastSyncAt instanceof com.google.firebase.Timestamp) {
                                        customer.setLastSyncAt(((com.google.firebase.Timestamp) lastSyncAt).toDate().getTime());
                                    }
                                    
                                    Object isActive = data.get("isActive");
                                    if (isActive instanceof Boolean) {
                                        customer.setActive((Boolean) isActive);
                                    } else {
                                        customer.setActive(true);
                                    }
                                    
                                    // If we have a pending local change for this ID, prefer local truth
                                    CustomerEntity local = database.customerDao().getCustomerById(customer.getId());
                                    if (local != null && local.isNeedsSync()) {
                                        // Skip overwrite to avoid flicker; keep local pending change
                                        return;
                                    }
                                    customer.setNeedsSync(false); // Downloaded from server, no need to sync
                                    // Insert or update in local database
                                    database.customerDao().insertCustomer(customer);
                                    
                                } catch (Exception e) {
                                    Log.e(TAG, "Error processing customer document: " + document.getId(), e);
                                }
                            }
                            // Notify on main thread after local write
                            android.os.Handler handler = new android.os.Handler(context.getMainLooper());
                            handler.post(() -> Log.d(TAG, "Customers downloaded and saved locally"));
                        }).start();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error downloading customers", e);
                    });
                
            } catch (Exception e) {
                Log.e(TAG, "Error in download process", e);
            }
        }).start();
    }

    /**
     * Download customers then invoke a UI callback
     */
    public void downloadCustomers(Runnable onComplete) {
        if (!isOnline()) {
            Log.d(TAG, "No internet connection, skipping download callback");
            return;
        }
        firestore.collection("__ping__").get() // trigger async chain; we don't use result
            .addOnCompleteListener(t -> {
                downloadCustomers();
                // Give a tiny delay to allow local inserts to complete
                new android.os.Handler(context.getMainLooper()).postDelayed(() -> {
                    if (onComplete != null) onComplete.run();
                }, 400);
            });
    }
    
    /**
     * Full sync - upload local changes and download server changes
     */
    public void fullSync() {
        Log.d(TAG, "Starting full sync");
        
        // First upload local changes
        syncCustomers();
        
        // Then download server changes
        downloadCustomers();
    }
    
    /**
     * Auto-sync when internet becomes available
     */
    public void startAutoSync() {
        // This would typically be implemented with a BroadcastReceiver
        // to listen for connectivity changes, but for simplicity,
        // we'll just sync when the app starts or when explicitly called
        
        if (isOnline()) {
            fullSync();
        }
    }
}
