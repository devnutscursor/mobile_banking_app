package com.example.myapplication.utils;

import android.util.Log;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.CommissionEntity;
import com.example.myapplication.database.entities.CommissionRateEntity;
import com.example.myapplication.database.entities.TransactionEntity;
import com.example.myapplication.database.entities.UserEntity;

public class CommissionCalculator {
    private static final String TAG = "CommissionCalculator";
    
    private AppDatabase database;
    
    public CommissionCalculator(AppDatabase database) {
        this.database = database;
    }
    
    /**
     * Calculate and save commission for a completed transaction
     * @param transaction The completed transaction
     * @param user The user (agent/dealer) who performed the transaction
     * @return CommissionEntity if commission was calculated, null otherwise
     */
    public CommissionEntity calculateCommission(TransactionEntity transaction, UserEntity user) {
        try {
            // Only calculate commission for deposit, withdrawal, and transfer transactions
            if (transaction == null || user == null) {
                Log.w(TAG, "Transaction or user is null");
                return null;
            }
            
            String transactionType = transaction.getTransactionType();
            if (!"deposit".equals(transactionType) && !"withdrawal".equals(transactionType) && !"transfer".equals(transactionType)) {
                Log.d(TAG, "Commission only applies to deposit/withdrawal/transfer transactions");
                return null;
            }
            
            // Only calculate commission for successful transactions
            if (!"successful".equals(transaction.getStatus()) && !"completed".equals(transaction.getStatus())) {
                Log.d(TAG, "Commission only applies to successful transactions");
                return null;
            }
            
            // Get commission rate for this user and operator
            CommissionRateEntity rate = database.commissionRateDao()
                    .getCommissionRateByUserAndOperator(user.getUid(), transaction.getOperatorId());
            
            if (rate == null) {
                Log.d(TAG, "No commission rate found for user " + user.getUid() + " and operator " + transaction.getOperatorId());
                return null;
            }
            
            // Check if this transaction type is eligible for commission
            String transactionTypes = rate.getTransactionTypes();
            if (transactionTypes != null && !transactionTypes.contains(transactionType)) {
                Log.d(TAG, "Transaction type " + transactionType + " not eligible for commission");
                return null;
            }
            
            // Create or update commission entity
            CommissionEntity existing = database.commissionDao()
                    .getCommissionByTransaction(transaction.getId());
            CommissionEntity commission = existing != null ? existing : new CommissionEntity();
            commission.setTransactionId(transaction.getId());
            commission.setTransactionType(transactionType);
            // For transfers, commission is calculated on base amount (without fees)
            // For deposits and withdrawals, use transaction amount as is
            double commissionBaseAmount = transaction.getAmount();
            if ("transfer".equals(transactionType)) {
                // Commission is calculated on base amount only (without transfer fees)
                // transaction.getAmount() already contains the base amount
                commissionBaseAmount = transaction.getAmount();
                Log.d(TAG, "Transfer commission calculated on base amount (without fees): " + commissionBaseAmount);
            }
            
            commission.setTransactionAmount(commissionBaseAmount);
            commission.setUserId(user.getUid());
            commission.setUserName(user.getName());
            commission.setUserRole(user.getRole());
            commission.setOperatorId(transaction.getOperatorId());
            commission.setOperatorName(transaction.getOperatorName());
            
            // Calculate commission
            double commissionRate = rate.getCommissionRate();
            double taxRate = rate.getTaxRate();
            commission.calculateCommission(commissionBaseAmount, commissionRate, taxRate);
            
            // Set commission date
            commission.setCommissionDate(transaction.getCreatedAt());
            commission.setUpdatedAt(System.currentTimeMillis());
            commission.setNeedsSync(true);
            
            // Save commission
            if (existing == null) {
                database.commissionDao().insertCommission(commission);
            } else {
                database.commissionDao().updateCommission(commission);
            }
            
            Log.d(TAG, "Commission calculated: " + commission.getTotalCommission() + 
                  " (Base: " + commission.getCommissionAmount() + 
                  ", Tax: " + commission.getTaxAmount() + ")");
            
            return commission;
            
        } catch (Exception e) {
            Log.e(TAG, "Error calculating commission", e);
            return null;
        }
    }
    
    /**
     * Get commission rate for a user and operator
     */
    public CommissionRateEntity getCommissionRate(String userId, String operatorId) {
        try {
            return database.commissionRateDao().getCommissionRateByUserAndOperator(userId, operatorId);
        } catch (Exception e) {
            Log.e(TAG, "Error getting commission rate", e);
            return null;
        }
    }
    
    /**
     * Remove any commission tied to a transaction (e.g., when canceled)
     */
    public void removeCommissionForTransaction(String transactionId) {
        try {
            if (transactionId == null) return;
            database.commissionDao().deleteCommissionByTransaction(transactionId);
            Log.d(TAG, "Removed commission for transaction: " + transactionId);
        } catch (Exception e) {
            Log.e(TAG, "Error removing commission for transaction " + transactionId, e);
        }
    }
}


