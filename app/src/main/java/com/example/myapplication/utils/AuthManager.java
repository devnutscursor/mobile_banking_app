package com.example.myapplication.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication.entities.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

public class AuthManager {
    private static final String PREFS_NAME = "auth_prefs";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_DEALER_ID = "dealer_id";
    
    private static AuthManager instance;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences prefs;
    private User currentUser;

    private AuthManager(Context context) {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }

    public interface AuthCallback {
        void onSuccess(User user);
        void onError(String error);
    }

    public void getCurrentUser(AuthCallback callback) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onError("No user logged in");
            return;
        }

        // Check if we have cached user data for THIS firebase user
        if (currentUser != null && firebaseUser.getUid().equals(currentUser.getUid())) {
            callback.onSuccess(currentUser);
            return;
        }
        // Cached user belongs to someone else (previous session). Invalidate it.
        currentUser = null;

        // Fetch user data from Firestore
        db.collection("users")
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        // Auto-provision minimal user doc (default agent)
                        createInitialUser(firebaseUser, new AuthCallback() {
                            @Override
                            public void onSuccess(User user) {
                                currentUser = user;
                                cacheUserData(user);
                                callback.onSuccess(user);
                            }

                            @Override
                            public void onError(String error) {
                                callback.onError(error);
                            }
                        });
                    } else {
                        try {
                            User user = parseUser(documentSnapshot);
                            currentUser = user;
                            cacheUserData(user);
                            callback.onSuccess(user);
                        } catch (Exception ex) {
                            callback.onError("Failed to parse user data: " + ex.getMessage());
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Offline-first fallback
                    if (e instanceof FirebaseFirestoreException) {
                        FirebaseFirestoreException ex = (FirebaseFirestoreException) e;
                        if (ex.getCode() == FirebaseFirestoreException.Code.UNAVAILABLE) {
                            loadCachedUserData();
                            if (currentUser != null) {
                                callback.onSuccess(currentUser);
                                return;
                            }
                        }
                    }
                    callback.onError("Failed to fetch user data: " + e.getMessage());
                });
    }

    public void refreshUserData(AuthCallback callback) {
        currentUser = null;
        getCurrentUser(callback);
    }

    public boolean isLoggedIn() {
        return mAuth.getCurrentUser() != null;
    }

    public boolean isDealer() {
        return currentUser != null && currentUser.isDealer();
    }

    public boolean isAgent() {
        return currentUser != null && currentUser.isAgent();
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }

    public String getUserRole() {
        return currentUser != null ? currentUser.getRole() : null;
    }

    public String getUserId() {
        return currentUser != null ? currentUser.getUid() : null;
    }

    public String getUserName() {
        return currentUser != null ? currentUser.getName() : null;
    }

    public String getDealerId() {
        return currentUser != null ? currentUser.getDealerId() : null;
    }

    public void logout() {
        mAuth.signOut();
        currentUser = null;
        clearCachedData();
    }

    private void cacheUserData(User user) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_USER_ROLE, user.getRole());
        editor.putString(KEY_USER_NAME, user.getName());
        editor.putString(KEY_DEALER_ID, user.getDealerId());
        editor.apply();
    }

    private void clearCachedData() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_USER_ROLE);
        editor.remove(KEY_USER_NAME);
        editor.remove(KEY_DEALER_ID);
        editor.apply();
    }

    public void loadCachedUserData() {
        String role = prefs.getString(KEY_USER_ROLE, null);
        String name = prefs.getString(KEY_USER_NAME, null);
        String dealerId = prefs.getString(KEY_DEALER_ID, null);
        
        if (role != null && name != null) {
            // Create a temporary user object with cached data
            currentUser = new User();
            currentUser.setRole(role);
            currentUser.setName(name);
            currentUser.setDealerId(dealerId);
        }
    }

    private User parseUser(DocumentSnapshot doc) {
        User user = new User();
        user.setUid(doc.getString("uid"));
        user.setEmail(doc.getString("email"));
        user.setName(doc.getString("name"));
        user.setPhone(doc.getString("phone"));
        user.setRole(doc.getString("role"));
        user.setDealerId(doc.getString("dealerId"));
        Boolean active = doc.getBoolean("active");
        user.setActive(active != null ? active : true);
        
        // CRITICAL: Read disabled status from Firestore
        Boolean disabled = doc.getBoolean("disabled");
        user.setDisabled(disabled != null ? disabled : false);

        java.util.Date created = toDateFlexible(doc.get("createdAt"));
        java.util.Date updated = toDateFlexible(doc.get("updatedAt"));
        user.setCreatedAt(created);
        user.setUpdatedAt(updated);
        return user;
    }

    private java.util.Date toDateFlexible(Object value) {
        if (value == null) return null;
        if (value instanceof java.util.Date) return (java.util.Date) value;
        if (value instanceof Timestamp) return ((Timestamp) value).toDate();
        if (value instanceof Long) return new java.util.Date((Long) value);
        if (value instanceof Double) return new java.util.Date(((Double) value).longValue());
        // Unsupported type
        return null;
    }

    private void createInitialUser(FirebaseUser firebaseUser, AuthCallback callback) {
        User user = new User();
        user.setUid(firebaseUser.getUid());
        user.setEmail(firebaseUser.getEmail());
        user.setName(firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User");
        user.setPhone(firebaseUser.getPhoneNumber());
        user.setRole("agent"); // default – can be adjusted later by Admin web
        user.setDealerId(null);
        user.setActive(false);
        user.setCreatedAt(new java.util.Date());
        user.setUpdatedAt(new java.util.Date());

        db.collection("users").document(firebaseUser.getUid())
                .set(new java.util.HashMap<String, Object>() {{
                    put("uid", user.getUid());
                    put("email", user.getEmail());
                    put("name", user.getName());
                    put("phone", user.getPhone());
                    put("role", user.getRole());
                    put("dealerId", user.getDealerId());
                    put("active", user.isActive());
                    put("createdAt", user.getCreatedAt());
                    put("updatedAt", user.getUpdatedAt());
                }})
                .addOnSuccessListener(aVoid -> callback.onSuccess(user))
                .addOnFailureListener(e -> callback.onError("Failed to create user profile: " + e.getMessage()));
    }
}

