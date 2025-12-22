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
                Log.w(TAG, "No commission rate found for user " + user.getUid() + " (role: " + user.getRole() + ") and operator " + transaction.getOperatorId() + ". Commission will not be calculated. Please configure commission rates in Commission Configuration.");
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
            
            // Set commission date (this will also update year, month, day fields)
            commission.setCommissionDate(transaction.getCreatedAt());
            // Ensure date fields are set correctly
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(transaction.getCreatedAt());
            commission.setYear(cal.get(java.util.Calendar.YEAR));
            commission.setMonth(cal.get(java.util.Calendar.MONTH) + 1); // Calendar.MONTH is 0-based
            commission.setDay(cal.get(java.util.Calendar.DAY_OF_MONTH));
            commission.setUpdatedAt(System.currentTimeMillis());
            commission.setNeedsSync(true);
            
            // Save commission
            if (existing == null) {
                database.commissionDao().insertCommission(commission);
            } else {
                database.commissionDao().updateCommission(commission);
            }
            
            Log.d(TAG, "Commission calculated for user " + user.getUid() + " (role: " + user.getRole() + "): " + commission.getTotalCommission() + 
                  " (Base: " + commission.getCommissionAmount() + 
                  ", Tax: " + commission.getTaxAmount() + 
                  ", Year: " + commission.getYear() + 
                  ", Month: " + commission.getMonth() + 
                  ", Day: " + commission.getDay() + ")");
            
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


