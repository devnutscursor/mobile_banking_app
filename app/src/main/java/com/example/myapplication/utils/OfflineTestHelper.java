package com.example.myapplication.utils;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.database.entities.LicenseEntity;
import com.example.myapplication.database.entities.SessionEntity;

/**
 * Helper class to test offline-first functionality
 */
public class OfflineTestHelper {
    private static final String TAG = "OfflineTestHelper";
    
    public static void testOfflineCapabilities(Context context) {
        Log.d(TAG, "Testing offline capabilities...");
        
        AppDatabase db = AppDatabase.getDatabase(context);
        
        // Test user creation
        UserEntity testUser = new UserEntity();
        testUser.setUid("test-user-123");
        testUser.setEmail("test@offline.com");
        testUser.setName("Offline Test User");
        testUser.setPhone("+1234567890");
        testUser.setRole("agent");
        testUser.setActive(true);
        testUser.setCreatedAt(System.currentTimeMillis());
        testUser.setUpdatedAt(System.currentTimeMillis());
        testUser.setLastSyncAt(System.currentTimeMillis());
        
        db.userDao().insertUser(testUser);
        Log.d(TAG, "Test user inserted");
        
        // Test license creation
        LicenseEntity testLicense = new LicenseEntity();
        testLicense.setLicenseKey("TEST-LICENSE-123");
        testLicense.setAssignedToUserId("test-user-123");
        testLicense.setIssueDate(System.currentTimeMillis());
        testLicense.setExpiryDate(System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000)); // 1 year
        testLicense.setActive(true);
        testLicense.setLastSyncAt(System.currentTimeMillis());
        
        db.licenseDao().insertLicense(testLicense);
        Log.d(TAG, "Test license inserted");
        
        // Test session creation
        SessionEntity testSession = new SessionEntity("test_session");
        testSession.setUserId("test-user-123");
        testSession.setEmail("test@offline.com");
        testSession.setRole("agent");
        testSession.setLicenseKey("TEST-LICENSE-123");
        testSession.setLoggedIn(true);
        testSession.setFirstLoginComplete(true);
        testSession.setLoginTime(System.currentTimeMillis());
        testSession.setLastActivityTime(System.currentTimeMillis());
        testSession.setLastOnlineSync(System.currentTimeMillis());
        
        db.sessionDao().insertSession(testSession);
        Log.d(TAG, "Test session inserted");
        
        // Test data retrieval
        UserEntity retrievedUser = db.userDao().getUserById("test-user-123");
        LicenseEntity retrievedLicense = db.licenseDao().getLicenseByKeyForUser("TEST-LICENSE-123", "test-user-123");
        SessionEntity retrievedSession = db.sessionDao().getSessionById("test_session");
        
        Log.d(TAG, "Retrieved user: " + (retrievedUser != null ? retrievedUser.getEmail() : "null"));
        Log.d(TAG, "Retrieved license: " + (retrievedLicense != null ? retrievedLicense.getLicenseKey() : "null"));
        Log.d(TAG, "Retrieved session: " + (retrievedSession != null ? retrievedSession.getEmail() : "null"));
        
        // Test SessionManager
        SessionManager sessionManager = new SessionManager(context);
        boolean canWorkOffline = sessionManager.canWorkOffline();
        Log.d(TAG, "Can work offline: " + canWorkOffline);
        
        Log.d(TAG, "Offline capabilities test completed");
    }
}



