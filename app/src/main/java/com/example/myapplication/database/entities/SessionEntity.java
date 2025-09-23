package com.example.myapplication.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import androidx.annotation.NonNull;

@Entity(tableName = "sessions")
public class SessionEntity {
    @PrimaryKey
    @NonNull
    private String id; // Usually "current_session"
    
    private String userId;
    private String email;
    private String role;
    private String licenseKey;
    private boolean isLoggedIn;
    private boolean firstLoginComplete;
    private long loginTime;
    private long lastActivityTime;
    private long lastOnlineSync; // Last time we synced with Firebase

    public SessionEntity() {
        // Required for Room
    }

    @Ignore
    public SessionEntity(@NonNull String id) {
        this.id = id;
        this.isLoggedIn = false;
        this.firstLoginComplete = false;
        long now = System.currentTimeMillis();
        this.loginTime = now;
        this.lastActivityTime = now;
        this.lastOnlineSync = 0;
    }

    // Getters
    @NonNull
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getLicenseKey() { return licenseKey; }
    public boolean isLoggedIn() { return isLoggedIn; }
    public boolean isFirstLoginComplete() { return firstLoginComplete; }
    public long getLoginTime() { return loginTime; }
    public long getLastActivityTime() { return lastActivityTime; }
    public long getLastOnlineSync() { return lastOnlineSync; }

    // Setters
    public void setId(@NonNull String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setEmail(String email) { this.email = email; }
    public void setRole(String role) { this.role = role; }
    public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }
    public void setLoggedIn(boolean loggedIn) { isLoggedIn = loggedIn; }
    public void setFirstLoginComplete(boolean firstLoginComplete) { this.firstLoginComplete = firstLoginComplete; }
    public void setLoginTime(long loginTime) { this.loginTime = loginTime; }
    public void setLastActivityTime(long lastActivityTime) { this.lastActivityTime = lastActivityTime; }
    public void setLastOnlineSync(long lastOnlineSync) { this.lastOnlineSync = lastOnlineSync; }

    // Helper methods
    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public boolean needsOnlineSync() {
        // Sync every 24 hours or if never synced
        long dayInMs = 24 * 60 * 60 * 1000;
        return lastOnlineSync == 0 || (System.currentTimeMillis() - lastOnlineSync) > dayInMs;
    }
}

