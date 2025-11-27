package com.example.myapplication.entities;

import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class License {
    private String licenseKey;
    // Store as Object to handle both String (legacy) and List<String> (array) formats
    private Object assignedToUserId;
    private java.util.Date issueDate;
    private java.util.Date expiryDate;
    // Map exactly to Firestore field name "isActive"
    private boolean isActive;
    private Integer maxAgentCount; // Maximum number of agents allowed (null means unlimited)
    private String licenseType; // "monthly" or "annual"

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

    // Handle both String and List<String> formats
    public Object getAssignedToUserId() { return assignedToUserId; }
    public void setAssignedToUserId(Object assignedToUserId) { this.assignedToUserId = assignedToUserId; }
    
    // Helper method to get assigned user ID as String (for backward compatibility)
    public String getAssignedToUserIdAsString() {
        if (assignedToUserId == null) return null;
        if (assignedToUserId instanceof String) {
            return (String) assignedToUserId;
        }
        if (assignedToUserId instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<Object> list = (java.util.List<Object>) assignedToUserId;
            if (!list.isEmpty()) {
                Object first = list.get(0);
                return first != null ? first.toString() : null;
            }
        }
        return assignedToUserId.toString();
    }
    
    // Helper method to check if a specific user is assigned
    public boolean isAssignedToUser(String userId) {
        if (assignedToUserId == null || userId == null) return false;
        if (assignedToUserId instanceof String) {
            return assignedToUserId.equals(userId);
        }
        if (assignedToUserId instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<Object> list = (java.util.List<Object>) assignedToUserId;
            for (Object userObj : list) {
                if (userObj != null && userObj.toString().equals(userId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public java.util.Date getIssueDate() { return issueDate; }
    public void setIssueDate(java.util.Date issueDate) { this.issueDate = issueDate; }

    public java.util.Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(java.util.Date expiryDate) { this.expiryDate = expiryDate; }

    @PropertyName("isActive")
    public boolean isActive() { return isActive; }

    @PropertyName("isActive")
    public void setActive(boolean active) { this.isActive = active; }

    public Integer getMaxAgentCount() { return maxAgentCount; }
    public void setMaxAgentCount(Integer maxAgentCount) { this.maxAgentCount = maxAgentCount; }

    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }

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

