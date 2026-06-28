package com.example.myapplication.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.example.myapplication.entities.License;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class LicenseManager {
    private static final String PREFS_NAME = "license_prefs";
    private static final String KEY_LICENSE_KEY = "license_key";
    private static final String KEY_LICENSE_VALID_UNTIL = "license_valid_until";
    private static final String KEY_LICENSE_ACTIVE = "license_active";
    private static final String KEY_LICENSE_ASSIGNED_TO = "license_assigned_to";
    private static final String KEY_LICENSE_MAX_AGENT_COUNT = "license_max_agent_count";
    private static final String KEY_LICENSE_TYPE = "license_type";
    
    private static LicenseManager instance;
    private FirebaseFirestore db;
    private SharedPreferences prefs;
    private License currentLicense;

    private LicenseManager(Context context) {
        db = FirebaseFirestore.getInstance();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized LicenseManager getInstance(Context context) {
        if (instance == null) {
            instance = new LicenseManager(context);
        }
        return instance;
    }

    public interface LicenseCallback {
        void onValid(License license);
        void onInvalid(String reason);
        void onError(String error);
    }

    public void activateLicense(String userId, String licenseKey, LicenseCallback callback) {
        // Lookup license by key and assign to user if available and active
        db.collection("licenses")
                .document(licenseKey)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onInvalid("License key not found");
                        return;
                    }

                    // Read fields explicitly to avoid mapping mismatches
                    Boolean active = doc.getBoolean("isActive");
                    java.util.Date expiry = doc.getDate("expiryDate");
                    Object assignedToObj = doc.get("assignedToUserId"); // Can be String (old) or List<String> (new array)
                    Long maxAgentCount = doc.getLong("maxAgentCount"); // Can be null for unlimited
                    Integer maxAgentCountInt = maxAgentCount != null ? maxAgentCount.intValue() : null;

                    if (active == null || !active) {
                        callback.onInvalid("License inactive");
                        return;
                    }
                    if (expiry != null && expiry.before(new java.util.Date())) {
                        callback.onInvalid("License expired");
                        return;
                    }
                    
                    // Handle assignedToUserId - can be String (legacy) or List<String> (array)
                    java.util.List<String> assignedUsers = new java.util.ArrayList<>();
                    boolean isAlreadyAssigned = false;
                    
                    if (assignedToObj != null) {
                        if (assignedToObj instanceof java.util.List) {
                            // Array format (new)
                            @SuppressWarnings("unchecked")
                            java.util.List<Object> assignedList = (java.util.List<Object>) assignedToObj;
                            for (Object userObj : assignedList) {
                                String userStr = userObj != null ? userObj.toString() : null;
                                if (userStr != null) {
                                    assignedUsers.add(userStr);
                                    if (userStr.equals(userId)) {
                                        isAlreadyAssigned = true;
                                    }
                                }
                            }
                        } else if (assignedToObj instanceof String) {
                            // String format (legacy - single user)
                            String assignedToStr = (String) assignedToObj;
                            if (!assignedToStr.isEmpty()) {
                                assignedUsers.add(assignedToStr);
                                if (assignedToStr.equals(userId)) {
                                    isAlreadyAssigned = true;
                                }
                            }
                        }
                    }
                    
                    // Check if already assigned
                    if (isAlreadyAssigned) {
                        // User already has this license assigned, allow reactivation
                        proceedWithActivation(doc, userId, licenseKey, callback);
                        return;
                    }
                    
                    // Check if license can accept more users based on maxAgentCount
                    if (maxAgentCountInt != null && maxAgentCountInt > 0) {
                        int currentUserCount = assignedUsers.size();
                        
                        // Check if we've reached the maximum user count
                        if (currentUserCount >= maxAgentCountInt) {
                            callback.onInvalid("License maximum user count (" + maxAgentCountInt + ") reached. " + currentUserCount + " user(s) are already using this license. Cannot assign to more users.");
                            return;
                        }
                    }
                    
                    // Within limit, proceed with activation
                    proceedWithActivation(doc, userId, licenseKey, callback);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
    
    private void proceedWithActivation(DocumentSnapshot doc, String userId, String licenseKey, LicenseCallback callback) {
        doc.getReference().getFirestore().runTransaction(transaction -> {
            DocumentSnapshot fresh = transaction.get(doc.getReference());
            Boolean active = fresh.getBoolean("isActive");
            java.util.Date expiry = fresh.getDate("expiryDate");
            if (active == null || !active) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException(
                        "License inactive", com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            if (expiry != null && expiry.before(new java.util.Date())) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException(
                        "License expired", com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            Object assignedToObj = fresh.get("assignedToUserId");
            java.util.List<String> assignedUsers = parseAssignedUsers(assignedToObj);
            boolean isAlreadyAssigned = assignedUsers.contains(userId);

            Long maxAgentCount = fresh.getLong("maxAgentCount");
            Integer maxAgentCountInt = maxAgentCount != null ? maxAgentCount.intValue() : null;
            if (!isAlreadyAssigned && maxAgentCountInt != null && maxAgentCountInt > 0
                    && assignedUsers.size() >= maxAgentCountInt) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException(
                        "License maximum user count reached",
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            transaction.update(doc.getReference(),
                    "assignedToUserId", FieldValue.arrayUnion(userId),
                    "isActive", true);
            return null;
        }).addOnSuccessListener(aVoid -> {
                        doc.getReference().get().addOnSuccessListener(updatedDoc -> {
                            // Manually construct License object to handle array field properly
                            License license = new License();
                            license.setLicenseKey(licenseKey);
                            
                            // Handle assignedToUserId as Object (can be String or List)
                            Object assignedToObj = updatedDoc.get("assignedToUserId");
                            license.setAssignedToUserId(assignedToObj);
                            
                            // Set other fields manually
                            java.util.Date issueDate = updatedDoc.getDate("issueDate");
                            if (issueDate != null) license.setIssueDate(issueDate);
                            
                            java.util.Date expiryDate = updatedDoc.getDate("expiryDate");
                            if (expiryDate != null) license.setExpiryDate(expiryDate);
                            
                            Boolean active = updatedDoc.getBoolean("isActive");
                            if (active != null) license.setActive(active);
                            
                            if (updatedDoc.contains("maxAgentCount")) {
                                Long maxCount = updatedDoc.getLong("maxAgentCount");
                                license.setMaxAgentCount(maxCount != null ? maxCount.intValue() : null);
                            }
                            if (updatedDoc.contains("licenseType")) {
                                license.setLicenseType(updatedDoc.getString("licenseType"));
                            }
                            
                            cacheLicense(license, userId);
                            currentLicense = license;
                            // Mark user as active after successful license activation
                            db.collection("users").document(userId)
                                    .update("active", true, "updatedAt", FieldValue.serverTimestamp())
                                    .addOnFailureListener(err -> { /* ignore non-critical */ });
                            callback.onValid(license);
                        }).addOnFailureListener(e -> callback.onError(e.getMessage()));
                }).addOnFailureListener(e -> {
                    String message = e.getMessage() != null ? e.getMessage() : "Activation failed";
                    if (message.contains("maximum user count")) {
                        callback.onInvalid(message);
                    } else if (message.contains("inactive") || message.contains("expired")) {
                        callback.onInvalid(message);
                    } else {
                        callback.onError(message);
                    }
                });
    }

    private java.util.List<String> parseAssignedUsers(Object assignedToObj) {
        java.util.List<String> assignedUsers = new java.util.ArrayList<>();
        if (assignedToObj instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<Object> assignedList = (java.util.List<Object>) assignedToObj;
            for (Object userObj : assignedList) {
                if (userObj != null) {
                    assignedUsers.add(userObj.toString());
                }
            }
        } else if (assignedToObj instanceof String) {
            String assignedToStr = (String) assignedToObj;
            if (!assignedToStr.isEmpty()) {
                assignedUsers.add(assignedToStr);
            }
        }
        return assignedUsers;
    }

    // New: Only verify a license by key; DO NOT mutate Firestore. The license must already
    // be assigned to this user in Firestore (assignedToUserId == userId).
    public void verifyLicenseWithKey(String userId, String licenseKey, LicenseCallback callback) {
        db.collection("licenses").document(licenseKey)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onInvalid("License key not found");
                        return;
                    }

                    Boolean active = doc.getBoolean("isActive");
                    java.util.Date expiry = doc.getDate("expiryDate");
                    Object assignedToObj = doc.get("assignedToUserId"); // Can be String (old) or List<String> (new array)

                    if (active == null || !active) {
                        callback.onInvalid("License inactive");
                        return;
                    }
                    if (expiry != null && expiry.before(new java.util.Date())) {
                        callback.onInvalid("License expired");
                        return;
                    }
                    
                    // Check if user is assigned - handle both array and string formats
                    boolean isAssigned = false;
                    if (assignedToObj != null) {
                        if (assignedToObj instanceof java.util.List) {
                            // Array format
                            @SuppressWarnings("unchecked")
                            java.util.List<Object> assignedList = (java.util.List<Object>) assignedToObj;
                            for (Object userObj : assignedList) {
                                if (userObj != null && userObj.toString().equals(userId)) {
                                    isAssigned = true;
                                    break;
                                }
                            }
                        } else if (assignedToObj instanceof String) {
                            // String format (legacy)
                            isAssigned = assignedToObj.toString().equals(userId);
                        }
                    }
                    
                    if (!isAssigned) {
                        callback.onInvalid("License not assigned to this user");
                        return;
                    }

                    // Manually construct License object to handle array field properly
                    License license = new License();
                    license.setLicenseKey(licenseKey);
                    
                    // Handle assignedToUserId as Object (can be String or List)
                    license.setAssignedToUserId(assignedToObj);
                    
                    // Set other fields manually
                    java.util.Date issueDate = doc.getDate("issueDate");
                    if (issueDate != null) license.setIssueDate(issueDate);
                    
                    java.util.Date expiryDate = doc.getDate("expiryDate");
                    if (expiryDate != null) license.setExpiryDate(expiryDate);
                    
                    // Use the already declared 'active' variable from above
                    license.setActive(active);
                    
                    Long maxAgentCount = doc.getLong("maxAgentCount");
                    if (maxAgentCount != null) {
                        license.setMaxAgentCount(maxAgentCount.intValue());
                    }
                    
                    String licenseType = doc.getString("licenseType");
                    if (licenseType != null) {
                        license.setLicenseType(licenseType);
                    }
                    
                    cacheLicense(license, userId);
                    currentLicense = license;
                    callback.onValid(license);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void validateLicense(String userId, LicenseCallback callback) {
        // First check cached in-memory license, but ensure it's for this user
        if (currentLicense != null && currentLicense.isValid()) {
            if (currentLicense.isAssignedToUser(userId)) {
                callback.onValid(currentLicense);
                return;
            }
            // Different user: ignore in-memory cache
        }

        // Check cached license from SharedPreferences
        String cachedKey = prefs.getString(KEY_LICENSE_KEY, null);
        long cachedValidUntil = prefs.getLong(KEY_LICENSE_VALID_UNTIL, -1);
        boolean cachedActive = prefs.getBoolean(KEY_LICENSE_ACTIVE, false);
        String cachedAssignedTo = prefs.getString(KEY_LICENSE_ASSIGNED_TO, null);

        if (cachedKey != null && cachedActive && (cachedValidUntil == -1 || System.currentTimeMillis() < cachedValidUntil)
                && cachedAssignedTo != null && cachedAssignedTo.equals(userId)) {
            // Use cached license
            License license = new License();
            license.setLicenseKey(cachedKey);
                    if (cachedValidUntil != -1) {
                        license.setExpiryDate(new java.util.Date(cachedValidUntil));
                    }
                    license.setActive(cachedActive);
                    license.setAssignedToUserId(cachedAssignedTo);
                    
                    // Check expiry date from cache - if expired, invalidate
                    if (cachedValidUntil != -1 && System.currentTimeMillis() >= cachedValidUntil) {
                        callback.onInvalid("License expired");
                        return;
                    }
                    
                    // Restore maxAgentCount and licenseType from cache if available
                    if (prefs.contains(KEY_LICENSE_MAX_AGENT_COUNT)) {
                        license.setMaxAgentCount(prefs.getInt(KEY_LICENSE_MAX_AGENT_COUNT, -1));
                    }
                    if (prefs.contains(KEY_LICENSE_TYPE)) {
                        license.setLicenseType(prefs.getString(KEY_LICENSE_TYPE, null));
                    }
                    
                    currentLicense = license;
                    callback.onValid(license);
                    return;
                }

        // Fetch license from Firestore
        // Use array-contains to find licenses where this user is in the assignedToUserId array
        db.collection("licenses")
                .whereArrayContains("assignedToUserId", userId)
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        // Manually construct License object to handle array field properly
                        License license = new License();
                        license.setLicenseKey(doc.getId());
                        
                        // Handle assignedToUserId as Object (can be String or List)
                        Object assignedToObj = doc.get("assignedToUserId");
                        license.setAssignedToUserId(assignedToObj);
                        
                        // Set other fields
                        java.util.Date issueDate = doc.getDate("issueDate");
                        if (issueDate != null) license.setIssueDate(issueDate);
                        
                        java.util.Date expiryDate = doc.getDate("expiryDate");
                        if (expiryDate != null) license.setExpiryDate(expiryDate);
                        
                        Boolean active = doc.getBoolean("isActive");
                        if (active != null) license.setActive(active);
                        
                        Long maxAgentCount = doc.getLong("maxAgentCount");
                        if (maxAgentCount != null) {
                            license.setMaxAgentCount(maxAgentCount.intValue());
                        }
                        
                        String licenseType = doc.getString("licenseType");
                        if (licenseType != null) {
                            license.setLicenseType(licenseType);
                        }
                        
                        if (license.isValid()) {
                            currentLicense = license;
                            cacheLicense(license, userId);
                            
                            // Mark user as active after successful license validation
                            db.collection("users").document(userId)
                                    .update("active", true, "updatedAt", FieldValue.serverTimestamp())
                                    .addOnFailureListener(err -> { /* ignore non-critical */ });
                            
                            callback.onValid(license);
                        } else {
                            callback.onInvalid("License expired or inactive");
                        }
                    } else {
                        callback.onInvalid("No valid license found for user");
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onError("Failed to validate license: " + e.getMessage());
                });
    }

    public boolean hasValidLicense() {
        return currentLicense != null && currentLicense.isValid();
    }

    public License getCurrentLicense() {
        return currentLicense;
    }

    public void clearLicense() {
        currentLicense = null;
        clearCachedLicense();
    }

    private void cacheLicense(License license, String userId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_LICENSE_KEY, license.getLicenseKey());
        long expiry = license.getExpiryDate() != null ? license.getExpiryDate().getTime() : -1;
        editor.putLong(KEY_LICENSE_VALID_UNTIL, expiry);
        editor.putBoolean(KEY_LICENSE_ACTIVE, license.isActive());
        // Cache as string - store the specific user for whom we validated this license
        editor.putString(KEY_LICENSE_ASSIGNED_TO, userId);
        if (license.getMaxAgentCount() != null) {
            editor.putInt(KEY_LICENSE_MAX_AGENT_COUNT, license.getMaxAgentCount());
        } else {
            editor.remove(KEY_LICENSE_MAX_AGENT_COUNT);
        }
        if (license.getLicenseType() != null) {
            editor.putString(KEY_LICENSE_TYPE, license.getLicenseType());
        } else {
            editor.remove(KEY_LICENSE_TYPE);
        }
        editor.apply();
    }

    private void clearCachedLicense() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_LICENSE_KEY);
        editor.remove(KEY_LICENSE_VALID_UNTIL);
        editor.remove(KEY_LICENSE_ACTIVE);
        editor.remove(KEY_LICENSE_ASSIGNED_TO);
        editor.remove(KEY_LICENSE_MAX_AGENT_COUNT);
        editor.remove(KEY_LICENSE_TYPE);
        editor.apply();
    }

    public String getDeviceId(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }
}

