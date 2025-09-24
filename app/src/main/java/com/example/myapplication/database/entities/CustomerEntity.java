package com.example.myapplication.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import androidx.annotation.NonNull;

@Entity(tableName = "customers")
public class CustomerEntity {
    @PrimaryKey
    @NonNull
    private String id; // Unique customer ID (can be national ID or generated UUID)
    
    private String fullName;
    private String dateOfBirth; // Format: YYYY-MM-DD
    private String nationalIdNumber;
    private String issueDate; // Format: YYYY-MM-DD
    private String expiryDate; // Format: YYYY-MM-DD
    private String phoneNumber;
    private String address;
    private String email;
    
    // Metadata
    private String createdBy; // User ID who created this customer
    private long createdAt;
    private long updatedAt;
    private long lastSyncAt;
    private boolean isActive;
    private boolean needsSync; // Flag for sync status

    // Default constructor
    public CustomerEntity() {}

    // Constructor
    @Ignore
    public CustomerEntity(@NonNull String id, String fullName, String dateOfBirth, 
                         String nationalIdNumber, String issueDate, String expiryDate) {
        this.id = id;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.nationalIdNumber = nationalIdNumber;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.isActive = true;
        this.needsSync = true;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSyncAt = now;
    }

    // Getters and Setters
    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getNationalIdNumber() { return nationalIdNumber; }
    public void setNationalIdNumber(String nationalIdNumber) { this.nationalIdNumber = nationalIdNumber; }

    public String getIssueDate() { return issueDate; }
    public void setIssueDate(String issueDate) { this.issueDate = issueDate; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public long getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(long lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isNeedsSync() { return needsSync; }
    public void setNeedsSync(boolean needsSync) { this.needsSync = needsSync; }

    // Helper methods
    public boolean isExpired() {
        if (expiryDate == null || expiryDate.isEmpty()) return false;
        try {
            java.time.LocalDate expiry = java.time.LocalDate.parse(expiryDate);
            return expiry.isBefore(java.time.LocalDate.now());
        } catch (Exception e) {
            return false;
        }
    }

    public int getAge() {
        if (dateOfBirth == null || dateOfBirth.isEmpty()) return 0;
        try {
            java.time.LocalDate birthDate = java.time.LocalDate.parse(dateOfBirth);
            return java.time.Period.between(birthDate, java.time.LocalDate.now()).getYears();
        } catch (Exception e) {
            return 0;
        }
    }
}


