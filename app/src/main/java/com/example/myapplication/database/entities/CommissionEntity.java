package com.example.myapplication.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "commissions")
public class CommissionEntity {
    @PrimaryKey
    @NonNull
    private String id; // Unique commission ID
    
    // Related transaction
    private String transactionId;
    private String transactionType; // "deposit" or "withdrawal"
    private double transactionAmount;
    
    // Who earned the commission
    private String userId; // Agent or Dealer ID
    private String userName;
    private String userRole; // "agent" or "dealer"
    
    // Operator details
    private String operatorId;
    private String operatorName;
    
    // Commission calculation
    private double commissionRate; // Base rate used (exclusive of tax)
    private double taxRate; // Tax rate used
    private double commissionAmount; // Gross commission (before tax deduction)
    private double taxAmount; // Tax on commission (to be deducted)
    private double totalCommission; // Net commission after tax deduction (total earned)
    
    // Period tracking
    private long commissionDate; // Date of commission (timestamp for day)
    private int year; // For yearly reports
    private int month; // For monthly reports (1-12)
    private int day; // For daily reports (1-31)
    
    // Metadata
    private long createdAt;
    private long updatedAt;
    private boolean needsSync;
    private long lastSyncAt;
    
    // Default constructor
    public CommissionEntity() {
        this.id = "comm_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        this.commissionAmount = 0.0;
        this.taxAmount = 0.0;
        this.totalCommission = 0.0;
        this.needsSync = true;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSyncAt = 0;
        
        // Set date fields
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(now);
        this.year = cal.get(java.util.Calendar.YEAR);
        this.month = cal.get(java.util.Calendar.MONTH) + 1; // Calendar.MONTH is 0-based
        this.day = cal.get(java.util.Calendar.DAY_OF_MONTH);
        this.commissionDate = cal.getTimeInMillis();
    }
    
    // Helper method to calculate commission
    public void calculateCommission(double transactionAmount, double commissionRate, double taxRate) {
        this.transactionAmount = transactionAmount;
        this.commissionRate = commissionRate;
        this.taxRate = taxRate;
        
        // Calculate gross commission (before tax deduction)
        this.commissionAmount = transactionAmount * (commissionRate / 100.0);
        
        // Calculate tax on commission (to be deducted)
        this.taxAmount = this.commissionAmount * (taxRate / 100.0);
        
        // Calculate net commission after tax deduction
        // Example: 25,000F × 0.2% = 50F gross, tax = 50F × 15% = 7.5F, net = 50F - 7.5F = 42.5F
        this.totalCommission = this.commissionAmount - this.taxAmount;
    }
    
    // Getters and Setters
    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }
    
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    
    public double getTransactionAmount() { return transactionAmount; }
    public void setTransactionAmount(double transactionAmount) { this.transactionAmount = transactionAmount; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    
    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }
    
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    
    public double getCommissionRate() { return commissionRate; }
    public void setCommissionRate(double commissionRate) { this.commissionRate = commissionRate; }
    
    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double taxRate) { this.taxRate = taxRate; }
    
    public double getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(double commissionAmount) { this.commissionAmount = commissionAmount; }
    
    public double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(double taxAmount) { this.taxAmount = taxAmount; }
    
    public double getTotalCommission() { return totalCommission; }
    public void setTotalCommission(double totalCommission) { this.totalCommission = totalCommission; }
    
    public long getCommissionDate() { return commissionDate; }
    public void setCommissionDate(long commissionDate) { 
        this.commissionDate = commissionDate;
        // Update year, month, day fields
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(commissionDate);
        this.year = cal.get(java.util.Calendar.YEAR);
        this.month = cal.get(java.util.Calendar.MONTH) + 1;
        this.day = cal.get(java.util.Calendar.DAY_OF_MONTH);
    }
    
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    
    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    
    public boolean isNeedsSync() { return needsSync; }
    public void setNeedsSync(boolean needsSync) { this.needsSync = needsSync; }
    
    public long getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(long lastSyncAt) { this.lastSyncAt = lastSyncAt; }
}






