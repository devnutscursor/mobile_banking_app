package com.example.myapplication.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import androidx.annotation.NonNull;

@Entity(tableName = "licenses")
public class LicenseEntity {
    @PrimaryKey
    @NonNull
    private String licenseKey;
    
    private String assignedToUserId;
    private long issueDate;
    private long expiryDate; // 0 means no expiry (perpetual)
    private boolean isActive;
    private long lastSyncAt; // When this record was last synced with Firebase

    public LicenseEntity() {
        // Required for Room
    }

    @Ignore
    public LicenseEntity(@NonNull String licenseKey, String assignedToUserId, long issueDate, long expiryDate, boolean isActive) {
        this.licenseKey = licenseKey;
        this.assignedToUserId = assignedToUserId;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.isActive = isActive;
        this.lastSyncAt = System.currentTimeMillis();
    }

    // Getters
    @NonNull
    public String getLicenseKey() { return licenseKey; }
    public String getAssignedToUserId() { return assignedToUserId; }
    public long getIssueDate() { return issueDate; }
    public long getExpiryDate() { return expiryDate; }
    public boolean isActive() { return isActive; }
    public long getLastSyncAt() { return lastSyncAt; }

    // Setters
    public void setLicenseKey(@NonNull String licenseKey) { this.licenseKey = licenseKey; }
    public void setAssignedToUserId(String assignedToUserId) { this.assignedToUserId = assignedToUserId; }
    public void setIssueDate(long issueDate) { this.issueDate = issueDate; }
    public void setExpiryDate(long expiryDate) { this.expiryDate = expiryDate; }
    public void setActive(boolean active) { isActive = active; }
    public void setLastSyncAt(long lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    // Helper methods
    public boolean isValid() {
        if (!isActive()) return false;
        if (expiryDate == 0) return true; // Perpetual license if no expiry
        return expiryDate > System.currentTimeMillis();
    }

    public boolean isExpired() {
        if (expiryDate == 0) return false; // Perpetual license never expires
        return expiryDate < System.currentTimeMillis();
    }
}

