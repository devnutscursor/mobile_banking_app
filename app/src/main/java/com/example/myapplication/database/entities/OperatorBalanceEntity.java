package com.example.myapplication.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

/**
 * Operator balance entity - stores balance per user-operator combination.
 * Each user can have separate balances for each operator they work with.
 */
@Entity(
    tableName = "operator_balances",
    indices = {@Index(value = {"userId", "operatorId"}, unique = true)}
)
public class OperatorBalanceEntity {
    @PrimaryKey
    @NonNull
    private String id; // Composite key: userId_operatorId
    
    @NonNull
    private String userId; // User who owns this balance
    @NonNull
    private String operatorId; // Operator for this balance
    
    private double balance; // Current balance for this operator
    private double totalCreditUsed; // Total credit used for this operator
    private double totalCreditEarned; // Total credit earned for this operator
    
    // Timestamps
    private long createdAt;
    private long updatedAt;
    private long lastSyncAt;
    private boolean needsSync;

    // Default constructor
    public OperatorBalanceEntity() {
        this.needsSync = true;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSyncAt = 0L;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    @NonNull
    public String getUserId() { return userId; }
    public void setUserId(@NonNull String userId) { this.userId = userId; }

    @NonNull
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(@NonNull String operatorId) { this.operatorId = operatorId; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public double getTotalCreditUsed() { return totalCreditUsed; }
    public void setTotalCreditUsed(double totalCreditUsed) { this.totalCreditUsed = totalCreditUsed; }

    public double getTotalCreditEarned() { return totalCreditEarned; }
    public void setTotalCreditEarned(double totalCreditEarned) { this.totalCreditEarned = totalCreditEarned; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public long getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(long lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public boolean isNeedsSync() { return needsSync; }
    public void setNeedsSync(boolean needsSync) { this.needsSync = needsSync; }
}

