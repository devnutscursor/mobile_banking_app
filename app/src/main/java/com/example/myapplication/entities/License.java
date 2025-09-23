package com.example.myapplication.entities;

import com.google.firebase.firestore.PropertyName;

public class License {
    private String licenseKey;
    private String assignedToUserId;
    private java.util.Date issueDate;
    private java.util.Date expiryDate;
    // Map exactly to Firestore field name "isActive"
    private boolean isActive;

    // Default constructor
    public License() {}

   // Constructor for new licenses
    public License(String licenseKey, String assignedToUserId, java.util.Date issueDate, java.util.Date expiryDate, boolean isActive) {
        this.licenseKey = licenseKey;
        this.assignedToUserId = assignedToUserId;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.isActive = isActive;
    }

    // Getters and Setters
    public String getLicenseKey() { return licenseKey; }
    public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }

    public String getAssignedToUserId() { return assignedToUserId; }
    public void setAssignedToUserId(String assignedToUserId) { this.assignedToUserId = assignedToUserId; }

    public java.util.Date getIssueDate() { return issueDate; }
    public void setIssueDate(java.util.Date issueDate) { this.issueDate = issueDate; }

    public java.util.Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(java.util.Date expiryDate) { this.expiryDate = expiryDate; }

    @PropertyName("isActive")
    public boolean isActive() { return isActive; }

    @PropertyName("isActive")
    public void setActive(boolean active) { this.isActive = active; }

    // Helper methods
    public boolean isValid() {
        if (!isActive()) return false;
        if (expiryDate == null) return true; // Perpetual license if no expiry
        return expiryDate.after(new java.util.Date());
    }

    public boolean isExpired() {
        if (expiryDate == null) return false; // Perpetual license never expires
        return expiryDate.before(new java.util.Date());
    }
}

