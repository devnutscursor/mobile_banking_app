package com.example.myapplication.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import androidx.annotation.NonNull;

@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey
    @NonNull
    private String uid;
    
    private String email;
    private String name;
    private String phone;
    private String role; // "dealer" or "agent"
    private String dealerId; // null for dealers, dealer's uid for agents
    private boolean active;
    private boolean disabled; // User disabled status - defaults to false
    private long createdAt;
    private long updatedAt;
    private long lastSyncAt; // When this record was last synced with Firebase
    
    // Credit tracking fields
    private double virtualCredit; // Current available credit
    private double totalCreditUsed; // Total credit used in deposits
    private double totalCreditEarned; // Total credit earned from withdrawals
    private long creditUpdatedAt; // Last credit update timestamp

    // Default constructor
    public UserEntity() {}

    // Constructor
    @Ignore
    public UserEntity(@NonNull String uid, String email, String name, String phone, String role) {
        this.uid = uid;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.active = true;
        this.disabled = false; // Default to not disabled
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSyncAt = now;
    }

    // Getters and Setters
    @NonNull
    public String getUid() { return uid; }
    public void setUid(@NonNull String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDealerId() { return dealerId; }
    public void setDealerId(String dealerId) { this.dealerId = dealerId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public long getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(long lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public double getVirtualCredit() { return virtualCredit; }
    public void setVirtualCredit(double virtualCredit) { this.virtualCredit = virtualCredit; }

    public double getTotalCreditUsed() { return totalCreditUsed; }
    public void setTotalCreditUsed(double totalCreditUsed) { this.totalCreditUsed = totalCreditUsed; }

    public double getTotalCreditEarned() { return totalCreditEarned; }
    public void setTotalCreditEarned(double totalCreditEarned) { this.totalCreditEarned = totalCreditEarned; }

    public long getCreditUpdatedAt() { return creditUpdatedAt; }
    public void setCreditUpdatedAt(long creditUpdatedAt) { this.creditUpdatedAt = creditUpdatedAt; }

    // Helper methods
    public boolean isDealer() {
        return "dealer".equals(role);
    }

    public boolean isAgent() {
        return "agent".equals(role);
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}

