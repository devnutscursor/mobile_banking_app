package com.example.myapplication.database.entities;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.annotation.NonNull;

@Entity(
    tableName = "licenses",
    primaryKeys = {"licenseKey", "assignedToUserId"}
)
public class LicenseEntity {
    @NonNull
    private String licenseKey;
    
    @NonNull
    private String assignedToUserId;
    private long issueDate;
    private long expiryDate; // 0 means no expiry (perpetual)
    private boolean isActive;
    private long lastSyncAt; // When this record was last synced with Firebase
    private Integer maxAgentCount; // Maximum number of agents allowed (null means unlimited)
    private String licenseType; // "monthly" or "annual"

    public LicenseEntity() {
        // Required for Room
        this.licenseKey = "";
        this.assignedToUserId = "";
    }

    @Ignore
    public LicenseEntity(@NonNull String licenseKey, @NonNull String assignedToUserId, long issueDate, long expiryDate, boolean isActive) {
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
    @NonNull
    public String getAssignedToUserId() { return assignedToUserId; }
    public long getIssueDate() { return issueDate; }
    public long getExpiryDate() { return expiryDate; }
    public boolean isActive() { return isActive; }
    public long getLastSyncAt() { return lastSyncAt; }
    public Integer getMaxAgentCount() { return maxAgentCount; }
    public String getLicenseType() { return licenseType; }

    // Setters
    public void setLicenseKey(@NonNull String licenseKey) { this.licenseKey = licenseKey; }
    public void setAssignedToUserId(@NonNull String assignedToUserId) { this.assignedToUserId = assignedToUserId; }
    public void setIssueDate(long issueDate) { this.issueDate = issueDate; }
    public void setExpiryDate(long expiryDate) { this.expiryDate = expiryDate; }
    public void setActive(boolean active) { isActive = active; }
    public void setLastSyncAt(long lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public void setMaxAgentCount(Integer maxAgentCount) { this.maxAgentCount = maxAgentCount; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }

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

