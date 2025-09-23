package com.example.myapplication.utils;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.database.entities.LicenseEntity;
import com.example.myapplication.database.entities.SessionEntity;
import com.example.myapplication.entities.User;
import com.example.myapplication.entities.License;

import java.util.Date;

public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String CURRENT_SESSION_ID = "current_session";
    
    private final AppDatabase database;
    private SessionEntity currentSession;
    
    public SessionManager(Context context) {
        this.database = AppDatabase.getDatabase(context);
        loadCurrentSession();
    }
    
    private void loadCurrentSession() {
        currentSession = database.sessionDao().getCurrentSession();
        if (currentSession == null) {
            currentSession = new SessionEntity(CURRENT_SESSION_ID);
            database.sessionDao().insertSession(currentSession);
        }
    }
    
    /**
     * Check if user is logged in (either online or offline)
     */
    public boolean isLoggedIn() {
        return currentSession != null && currentSession.isLoggedIn();
    }
    
    /**
     * Check if user has completed first online login + license validation
     */
    public boolean isFirstLoginComplete() {
        return currentSession != null && currentSession.isFirstLoginComplete();
    }
    
    /**
     * Check if a specific user has completed first online login + license validation
     * by verifying presence of an active, valid license assigned to that user in the local DB.
     */
    public boolean isFirstLoginCompleteForUser(String email) {
        if (email == null) {
            return false;
        }
        UserEntity user = getUserByEmail(email);
        if (user == null) {
            Log.d(TAG, "isFirstLoginCompleteForUser: user not found for email=" + email);
            return false;
        }
        LicenseEntity license = getLicenseByUser(user.getUid());
        if (license == null) {
            Log.d(TAG, "isFirstLoginCompleteForUser: no license for user=" + email);
            return false;
        }
        boolean result = user.isActive() && license.isActive() && license.isValid();
        Log.d(TAG, "isFirstLoginCompleteForUser: " + email + " => " + result);
        return result;
    }
    
    // Removed duplicate isFirstLoginCompleteForUser
    
    /**
     * Get current logged in user from local database
     */
    public UserEntity getCurrentUser() {
        if (!isLoggedIn() || currentSession.getUserId() == null) {
            return null;
        }
        return database.userDao().getUserById(currentSession.getUserId());
    }
    
    /**
     * Get user data from local database (ignore login status - for offline access)
     */
    public UserEntity getUserFromSession() {
        if (currentSession == null || currentSession.getUserId() == null) {
            return null;
        }
        return database.userDao().getUserById(currentSession.getUserId());
    }
    
    /**
     * Get current user's license from local database
     */
    public LicenseEntity getCurrentLicense() {
        if (!isLoggedIn() || currentSession.getLicenseKey() == null) {
            return null;
        }
        return database.licenseDao().getLicenseByKey(currentSession.getLicenseKey());
    }
    
    /**
     * Get license data from local database (ignore login status - for offline access)
     */
    public LicenseEntity getLicenseFromSession() {
        if (currentSession == null || currentSession.getLicenseKey() == null) {
            return null;
        }
        return database.licenseDao().getLicenseByKey(currentSession.getLicenseKey());
    }
    
    /**
     * Validate offline login - check if user can work offline
     * This checks if offline data exists, regardless of current login status
     */
    public boolean canWorkOffline() {
        if (currentSession == null || !isFirstLoginComplete()) {
            Log.d(TAG, "First login not complete - need online validation");
            return false;
        }
        
        String userId = currentSession.getUserId();
        String licenseKey = currentSession.getLicenseKey();
        
        if (userId == null || licenseKey == null) {
            Log.d(TAG, "No user or license in session");
            return false;
        }
        
        // Get user data directly from database (ignore current session login status)
        UserEntity user = database.userDao().getUserById(userId);
        LicenseEntity license = database.licenseDao().getLicenseByKey(licenseKey);
        
        if (user == null || license == null) {
            Log.d(TAG, "User or license not found in local database");
            return false;
        }
        
        // Check both user and license active status
        if (!user.isActive() || !license.isActive()) {
            Log.d(TAG, "User or license is inactive");
            return false;
        }
        
        if (!license.isValid()) {
            Log.d(TAG, "License is expired or invalid");
            return false;
        }
        
        Log.d(TAG, "User can work offline: " + user.getEmail());
        return true;
    }
    
    /**
     * Start online session after successful Firebase login + license validation
     */
    public void startOnlineSession(User user, License license) {
        Log.d(TAG, "Starting online session for: " + user.getEmail());
        
        // Save/update user in local database
        UserEntity userEntity = convertUserToEntity(user);
        database.userDao().insertUser(userEntity);
        
        // Save/update license in local database
        LicenseEntity licenseEntity = convertLicenseToEntity(license);
        database.licenseDao().insertLicense(licenseEntity);
        
        // Update session
        long now = System.currentTimeMillis();
        currentSession.setUserId(user.getUid());
        currentSession.setEmail(user.getEmail());
        currentSession.setRole(user.getRole());
        currentSession.setLicenseKey(license.getLicenseKey());
        currentSession.setLoggedIn(true);
        currentSession.setFirstLoginComplete(true);
        currentSession.setLoginTime(now);
        currentSession.setLastActivityTime(now);
        currentSession.setLastOnlineSync(now);
        
        database.sessionDao().updateSession(currentSession);
        
        Log.d(TAG, "Online session started successfully");
    }
    
    /**
     * Start offline session - user continues working without internet
     */
    public boolean startOfflineSession() {
        if (!canWorkOffline()) {
            Log.d(TAG, "Cannot start offline session - validation failed");
            return false;
        }
        
        // Get user data from session (ignore login status)
        UserEntity user = getUserFromSession();
        if (user == null) {
            Log.d(TAG, "User not found in database");
            return false;
        }
        
        Log.d(TAG, "Starting offline session for: " + user.getEmail());
        
        // Update session status
        long now = System.currentTimeMillis();
        currentSession.setLoggedIn(true);
        currentSession.setLoginTime(now);
        currentSession.setLastActivityTime(now);
        
        database.sessionDao().updateSession(currentSession);
        
        Log.d(TAG, "Offline session started successfully");
        return true;
    }
    
    /**
     * Start offline session for a specific user
     */
    public boolean startOfflineSessionForUser(UserEntity userEntity) {
        if (userEntity == null) {
            Log.d(TAG, "User entity is null");
            return false;
        }
        
        // Get license for this user
        LicenseEntity licenseEntity = getLicenseByUser(userEntity.getUid());
        if (licenseEntity == null || !licenseEntity.isActive() || !licenseEntity.isValid()) {
            Log.d(TAG, "No valid license found for user: " + userEntity.getEmail());
            return false;
        }
        
        // Update session with this user's data
        currentSession.setUserId(userEntity.getUid());
        currentSession.setEmail(userEntity.getEmail());
        currentSession.setRole(userEntity.getRole());
        currentSession.setLicenseKey(licenseEntity.getLicenseKey());
        currentSession.setLoggedIn(true);
        currentSession.setLoginTime(System.currentTimeMillis());
        currentSession.setLastActivityTime(System.currentTimeMillis());
        database.sessionDao().updateSession(currentSession);
        
        Log.d(TAG, "Offline session started for: " + userEntity.getEmail());
        return true;
    }
    
    /**
     * Update user activity (keep session alive)
     */
    public void updateActivity() {
        if (currentSession != null && currentSession.isLoggedIn()) {
            currentSession.updateActivity();
            database.sessionDao().updateActivity(CURRENT_SESSION_ID, currentSession.getLastActivityTime());
        }
    }
    
    /**
     * Lock session (user can unlock with offline login later)
     */
    public void lockSession() {
        Log.d(TAG, "Locking session");
        if (currentSession != null) {
            currentSession.setLoggedIn(false);
            currentSession.setLastActivityTime(System.currentTimeMillis());
            database.sessionDao().updateSession(currentSession);
        }
    }
    
    /**
     * Full logout - clear all local data (user needs internet next time)
     */
    public void fullLogout() {
        Log.d(TAG, "Full logout - clearing all local data");
        
        // Clear all local data
        database.sessionDao().deleteAllSessions();
        database.userDao().deleteAllUsers();
        database.licenseDao().deleteAllLicenses();
        
        // Create fresh session
        currentSession = new SessionEntity(CURRENT_SESSION_ID);
        database.sessionDao().insertSession(currentSession);
    }
    
    /**
     * Check if session needs online sync
     */
    public boolean needsOnlineSync() {
        return currentSession != null && currentSession.needsOnlineSync();
    }
    
    /**
     * Update last online sync time
     */
    public void updateLastOnlineSync() {
        if (currentSession != null) {
            currentSession.setLastOnlineSync(System.currentTimeMillis());
            database.sessionDao().updateLastOnlineSync(CURRENT_SESSION_ID, currentSession.getLastOnlineSync());
        }
    }
    
    // Helper methods to convert between entities
    private UserEntity convertUserToEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setUid(user.getUid());
        entity.setEmail(user.getEmail());
        entity.setName(user.getName());
        entity.setPhone(user.getPhone());
        entity.setRole(user.getRole());
        entity.setDealerId(user.getDealerId());
        // For offline-first, set user as active when saving to local database
        // This ensures offline access works after successful online login
        entity.setActive(true);
        entity.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().getTime() : System.currentTimeMillis());
        entity.setUpdatedAt(user.getUpdatedAt() != null ? user.getUpdatedAt().getTime() : System.currentTimeMillis());
        entity.setLastSyncAt(System.currentTimeMillis());
        return entity;
    }
    
    private LicenseEntity convertLicenseToEntity(License license) {
        LicenseEntity entity = new LicenseEntity();
        entity.setLicenseKey(license.getLicenseKey());
        entity.setAssignedToUserId(license.getAssignedToUserId());
        entity.setIssueDate(license.getIssueDate() != null ? license.getIssueDate().getTime() : System.currentTimeMillis());
        entity.setExpiryDate(license.getExpiryDate() != null ? license.getExpiryDate().getTime() : 0);
        entity.setActive(license.isActive());
        entity.setLastSyncAt(System.currentTimeMillis());
        return entity;
    }
    
    public User convertEntityToUser(UserEntity entity) {
        if (entity == null) return null;
        
        User user = new User();
        user.setUid(entity.getUid());
        user.setEmail(entity.getEmail());
        user.setName(entity.getName());
        user.setPhone(entity.getPhone());
        user.setRole(entity.getRole());
        user.setDealerId(entity.getDealerId());
        user.setActive(entity.isActive());
        user.setCreatedAt(new Date(entity.getCreatedAt()));
        user.setUpdatedAt(new Date(entity.getUpdatedAt()));
        return user;
    }
    
    public License convertEntityToLicense(LicenseEntity entity) {
        if (entity == null) return null;
        
        License license = new License();
        license.setLicenseKey(entity.getLicenseKey());
        license.setAssignedToUserId(entity.getAssignedToUserId());
        license.setIssueDate(new Date(entity.getIssueDate()));
        license.setExpiryDate(entity.getExpiryDate() > 0 ? new Date(entity.getExpiryDate()) : null);
        license.setActive(entity.isActive());
        return license;
    }
    
    /**
     * Get user by email from local database
     */
    public UserEntity getUserByEmail(String email) {
        return database.userDao().getUserByEmail(email);
    }
    
    /**
     * Get license by user ID from local database
     */
    public LicenseEntity getLicenseByUser(String userId) {
        return database.licenseDao().getLicenseByUserId(userId);
    }
}
