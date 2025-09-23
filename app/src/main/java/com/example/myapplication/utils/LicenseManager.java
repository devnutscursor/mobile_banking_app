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
                    String assignedTo = doc.getString("assignedToUserId");

                    if (active == null || !active) {
                        callback.onInvalid("License inactive");
                        return;
                    }
                    if (expiry != null && expiry.before(new java.util.Date())) {
                        callback.onInvalid("License expired");
                        return;
                    }
                    if (assignedTo != null && !assignedTo.equals(userId)) {
                        callback.onInvalid("License already assigned to another user");
                        return;
                    }

                    // Assign (or re-affirm) to this user
                    doc.getReference().update(
                            "assignedToUserId", userId,
                            "isActive", true
                    ).addOnSuccessListener(aVoid -> {
                        License license = doc.toObject(License.class);
                        if (license == null) {
                            license = new License();
                        }
                        license.setAssignedToUserId(userId);
                        if (license.getLicenseKey() == null) {
                            license.setLicenseKey(licenseKey);
                        }
                        cacheLicense(license);
                        currentLicense = license;
                        // Mark user as active after successful license activation
                        db.collection("users").document(userId)
                                .update("active", true, "updatedAt", FieldValue.serverTimestamp())
                                .addOnFailureListener(err -> { /* ignore non-critical */ });
                        callback.onValid(license);
                    }).addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
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
                    String assignedTo = doc.getString("assignedToUserId");

                    if (active == null || !active) {
                        callback.onInvalid("License inactive");
                        return;
                    }
                    if (expiry != null && expiry.before(new java.util.Date())) {
                        callback.onInvalid("License expired");
                        return;
                    }
                    if (assignedTo == null || !assignedTo.equals(userId)) {
                        callback.onInvalid("License not assigned to this user");
                        return;
                    }

                    License license = doc.toObject(License.class);
                    if (license == null) {
                        license = new License();
                        license.setLicenseKey(licenseKey);
                        license.setAssignedToUserId(userId);
                    }
                    cacheLicense(license);
                    currentLicense = license;
                    callback.onValid(license);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void validateLicense(String userId, LicenseCallback callback) {
        // First check cached in-memory license, but ensure it's for this user
        if (currentLicense != null && currentLicense.isValid()) {
            String assignedTo = currentLicense.getAssignedToUserId();
            if (assignedTo != null && assignedTo.equals(userId)) {
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
            currentLicense = license;
            callback.onValid(license);
            return;
        }

        // Fetch license from Firestore
        db.collection("licenses")
                .whereEqualTo("assignedToUserId", userId)
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        License license = doc.toObject(License.class);
                        if (license != null && license.isValid()) {
                            currentLicense = license;
                            cacheLicense(license);
                            
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

    private void cacheLicense(License license) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_LICENSE_KEY, license.getLicenseKey());
        long expiry = license.getExpiryDate() != null ? license.getExpiryDate().getTime() : -1;
        editor.putLong(KEY_LICENSE_VALID_UNTIL, expiry);
        editor.putBoolean(KEY_LICENSE_ACTIVE, license.isActive());
        editor.putString(KEY_LICENSE_ASSIGNED_TO, license.getAssignedToUserId());
        editor.apply();
    }

    private void clearCachedLicense() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_LICENSE_KEY);
        editor.remove(KEY_LICENSE_VALID_UNTIL);
        editor.remove(KEY_LICENSE_ACTIVE);
        editor.remove(KEY_LICENSE_ASSIGNED_TO);
        editor.apply();
    }

    public String getDeviceId(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }
}

