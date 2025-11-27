package com.example.myapplication.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "commission_rates")
public class CommissionRateEntity {
    @PrimaryKey
    @NonNull
    private String id; // Unique ID: userId_operatorId or dealerId_operatorId
    
    // Who this rate applies to
    private String userId; // Agent or Dealer ID
    private String userRole; // "agent" or "dealer"
    
    // Which operator this rate is for
    private String operatorId;
    private String operatorName;
    
    // Commission rates (as percentages, e.g., 0.4 for 0.4%)
    private double commissionRate; // Base commission rate (exclusive of tax)
    private double taxRate; // Tax rate (e.g., 15 for 15%)
    
    // Calculated fields (for display)
    private double commissionRateWithTax; // Commission rate including tax
    
    // Transaction types this rate applies to
    private String transactionTypes; // Comma-separated: "deposit,withdrawal"
    
    // Metadata
    private long createdAt;
    private long updatedAt;
    private boolean needsSync;
    private long lastSyncAt;
    
    // Default constructor
    public CommissionRateEntity() {
        this.id = "rate_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        this.commissionRate = 0.0;
        this.taxRate = 0.0;
        this.commissionRateWithTax = 0.0;
        this.transactionTypes = "deposit,withdrawal";
        this.needsSync = true;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSyncAt = 0;
    }
    
    // Helper method to calculate commission rate with tax
    public void calculateRateWithTax() {
        // Formula: rate_with_tax = rate * (1 + tax_rate/100)
        this.commissionRateWithTax = this.commissionRate * (1 + (this.taxRate / 100.0));
    }
    
    // Getters and Setters
    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }
    
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    
    public double getCommissionRate() { return commissionRate; }
    public void setCommissionRate(double commissionRate) { 
        this.commissionRate = commissionRate;
        calculateRateWithTax();
    }
    
    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double taxRate) { 
        this.taxRate = taxRate;
        calculateRateWithTax();
    }
    
    public double getCommissionRateWithTax() { return commissionRateWithTax; }
    public void setCommissionRateWithTax(double commissionRateWithTax) { 
        this.commissionRateWithTax = commissionRateWithTax; 
    }
    
    public String getTransactionTypes() { return transactionTypes; }
    public void setTransactionTypes(String transactionTypes) { this.transactionTypes = transactionTypes; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    
    public boolean isNeedsSync() { return needsSync; }
    public void setNeedsSync(boolean needsSync) { this.needsSync = needsSync; }
    
    public long getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(long lastSyncAt) { this.lastSyncAt = lastSyncAt; }
}






