package com.example.myapplication.utils;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.OperatorBalanceEntity;

/**
 * Helper utility class for managing operator-specific balances.
 * Provides convenience methods to get, update, and manage balances per operator.
 */
public class OperatorBalanceHelper {
    
    private AppDatabase database;
    
    public OperatorBalanceHelper(AppDatabase database) {
        this.database = database;
    }
    
    /**
     * Get the balance for a specific user-operator combination.
     * If no balance exists, creates one with balance 0.0.
     */
    public double getBalance(String userId, String operatorId) {
        OperatorBalanceEntity balance = database.operatorBalanceDao().getBalance(userId, operatorId);
        if (balance == null) {
            // Create a new balance record with 0 balance
            balance = createNewBalance(userId, operatorId);
            database.operatorBalanceDao().insertBalance(balance);
            return 0.0;
        }
        return balance.getBalance();
    }
    
    /**
     * Update the balance for a specific user-operator combination.
     * Creates a new balance record if one doesn't exist.
     */
    public void updateBalance(String userId, String operatorId, double newBalance) {
        OperatorBalanceEntity balance = database.operatorBalanceDao().getBalance(userId, operatorId);
        if (balance == null) {
            balance = createNewBalance(userId, operatorId);
        }
        balance.setBalance(newBalance);
        balance.setUpdatedAt(System.currentTimeMillis());
        balance.setNeedsSync(true);
        database.operatorBalanceDao().insertBalance(balance);
    }
    
    /**
     * Add amount to the balance (can be negative to subtract).
     */
    public void adjustBalance(String userId, String operatorId, double amount) {
        double currentBalance = getBalance(userId, operatorId);
        updateBalance(userId, operatorId, currentBalance + amount);
    }
    
    /**
     * Create a new OperatorBalanceEntity with default values.
     */
    private OperatorBalanceEntity createNewBalance(String userId, String operatorId) {
        OperatorBalanceEntity balance = new OperatorBalanceEntity();
        balance.setId(userId + "_" + operatorId);
        balance.setUserId(userId);
        balance.setOperatorId(operatorId);
        balance.setBalance(0.0);
        balance.setTotalCreditUsed(0.0);
        balance.setTotalCreditEarned(0.0);
        long now = System.currentTimeMillis();
        balance.setCreatedAt(now);
        balance.setUpdatedAt(now);
        balance.setLastSyncAt(0L);
        balance.setNeedsSync(true);
        return balance;
    }
    
    /**
     * Get the OperatorBalanceEntity object (creates if doesn't exist).
     */
    public OperatorBalanceEntity getBalanceEntity(String userId, String operatorId) {
        OperatorBalanceEntity balance = database.operatorBalanceDao().getBalance(userId, operatorId);
        if (balance == null) {
            balance = createNewBalance(userId, operatorId);
            database.operatorBalanceDao().insertBalance(balance);
        }
        return balance;
    }
    
    /**
     * Increment total credit used for an operator.
     */
    public void incrementCreditUsed(String userId, String operatorId, double amount) {
        OperatorBalanceEntity balance = getBalanceEntity(userId, operatorId);
        balance.setTotalCreditUsed(balance.getTotalCreditUsed() + amount);
        balance.setUpdatedAt(System.currentTimeMillis());
        balance.setNeedsSync(true);
        database.operatorBalanceDao().updateBalance(balance);
    }
    
    /**
     * Increment total credit earned for an operator.
     */
    public void incrementCreditEarned(String userId, String operatorId, double amount) {
        OperatorBalanceEntity balance = getBalanceEntity(userId, operatorId);
        balance.setTotalCreditEarned(balance.getTotalCreditEarned() + amount);
        balance.setUpdatedAt(System.currentTimeMillis());
        balance.setNeedsSync(true);
        database.operatorBalanceDao().updateBalance(balance);
    }
}

