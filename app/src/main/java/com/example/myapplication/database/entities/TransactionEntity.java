package com.example.myapplication.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import java.util.UUID;

@Entity(tableName = "transactions")
public class TransactionEntity {
    @PrimaryKey
    @NonNull
    private String id; // Unique transaction ID
    
    private String operatorId;
    private String operatorName;
    private String actionId;
    private String actionName;
    private String customerId;
    private String customerName;
    private String customerPhone;
    
    private double amount;
    private String transactionType; // "deposit" or "withdrawal"
    private String channel; // "USSD" or "NON_USSD"
    
    // User who performed the transaction
    private String userId;
    private String userName;
    private String userRole; // "agent" or "dealer"
    
    // Credit tracking
    private double creditBefore;
    private double creditAfter;
    
    // Transaction status
    private String status; // "pending", "completed", "failed"
    private String notes;
    
    // Metadata
    private long createdAt;
    private long updatedAt;
    private boolean needsSync;
    private long lastSyncAt;

    // Default constructor
    public TransactionEntity() {
        // Use UUID for guaranteed uniqueness
        this.id = "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.status = "pending";
        this.needsSync = true;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSyncAt = 0;
    }

    // Getters and Setters
    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    public double getCreditBefore() { return creditBefore; }
    public void setCreditBefore(double creditBefore) { this.creditBefore = creditBefore; }

    public double getCreditAfter() { return creditAfter; }
    public void setCreditAfter(double creditAfter) { this.creditAfter = creditAfter; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public boolean isNeedsSync() { return needsSync; }
    public void setNeedsSync(boolean needsSync) { this.needsSync = needsSync; }

    public long getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(long lastSyncAt) { this.lastSyncAt = lastSyncAt; }
}








