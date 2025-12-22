package com.example.myapplication.utils;

import android.util.Log;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.OperatorBalanceEntity;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.TransactionEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        // Safety: balance should never be negative – clamp and persist fix if needed
        double rawBalance = balance.getBalance();
        if (rawBalance < 0) {
            rawBalance = 0.0;
            balance.setBalance(0.0);
            balance.setUpdatedAt(System.currentTimeMillis());
            balance.setNeedsSync(true);
            database.operatorBalanceDao().updateBalance(balance);
            Log.w("OperatorBalanceHelper", "Clamped negative operator balance to 0 for user "
                    + userId + " and operator " + operatorId);
        }
        return rawBalance;
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
        // Safety: never persist a negative balance
        if (newBalance < 0) {
            Log.w("OperatorBalanceHelper", "Attempted to set negative balance (" + newBalance +
                    ") for user " + userId + " and operator " + operatorId + ". Clamping to 0.");
            newBalance = 0.0;
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
    
    /**
     * Recalculate operator balances from existing successful transactions.
     * This method should be called when operator balance system is first introduced
     * or when balances need to be recalculated from transaction history.
     * 
     * @param userId The user ID to recalculate balances for
     */
    public void recalculateBalancesFromTransactions(String userId) {
        Log.d("OperatorBalanceHelper", "Starting balance recalculation for user: " + userId);
        
        try {
            // Get all transactions for this user
            List<TransactionEntity> allTransactions = database.transactionDao().getTransactionsByUser(userId);
            Log.d("OperatorBalanceHelper", "Found " + allTransactions.size() + " total transactions");
            
            // Map to store calculated balances per operator
            Map<String, Double> operatorBalances = new HashMap<>();
            Map<String, Double> operatorCreditUsed = new HashMap<>();
            Map<String, Double> operatorCreditEarned = new HashMap<>();
            
            // Get all operators for this user to handle transactions without operator IDs
            List<OperatorEntity> userOperators = database.operatorDao().getActiveForUser(userId);
            Log.d("OperatorBalanceHelper", "User has " + userOperators.size() + " active operators");
            
            // Process each transaction
            for (TransactionEntity transaction : allTransactions) {
                // Only count successful/completed transactions
                String status = transaction.getStatus();
                if (status == null) {
                    continue;
                }
                status = status.toLowerCase();
                // Accept both "successful" and "completed" as valid statuses
                if (!status.equals("successful") && !status.equals("completed")) {
                    continue;
                }
                
                String operatorId = transaction.getOperatorId();
                
                // Handle transactions without operator ID (old transactions)
                if (operatorId == null || operatorId.isEmpty()) {
                    Log.w("OperatorBalanceHelper", "Transaction " + transaction.getId() + " has no operator ID, attempting to infer from operator name");
                    
                    // Try to match by operator name
                    String operatorName = transaction.getOperatorName();
                    if (operatorName != null && !operatorName.isEmpty() && !userOperators.isEmpty()) {
                        for (OperatorEntity operator : userOperators) {
                            if (operatorName.equalsIgnoreCase(operator.getName())) {
                                operatorId = operator.getId();
                                Log.d("OperatorBalanceHelper", "Matched transaction to operator by name: " + operatorName + " -> " + operatorId);
                                break;
                            }
                        }
                    }
                    
                    // If still no operator ID, assign to first operator (or distribute if multiple)
                    if ((operatorId == null || operatorId.isEmpty()) && !userOperators.isEmpty()) {
                        // Use first operator as default for old transactions
                        operatorId = userOperators.get(0).getId();
                        Log.d("OperatorBalanceHelper", "Assigned transaction to default operator: " + operatorId);
                    } else if (userOperators.isEmpty()) {
                        Log.w("OperatorBalanceHelper", "No operators found for user, skipping transaction " + transaction.getId());
                        continue;
                    }
                }
                
                double amount = transaction.getAmount();
                String transactionType = transaction.getTransactionType();
                if (transactionType == null) {
                    transactionType = "";
                }
                transactionType = transactionType.toLowerCase();
                
                // Initialize operator balance if not exists
                // Always start from 0 - we'll calculate from transactions, then account for purchases/adjustments separately
                if (!operatorBalances.containsKey(operatorId)) {
                    operatorBalances.put(operatorId, 0.0);
                    operatorCreditUsed.put(operatorId, 0.0);
                    operatorCreditEarned.put(operatorId, 0.0);
                }
                
                // Calculate balance change based on transaction type
                double creditChange = 0.0;
                double trackedUsed = 0.0;
                double trackedEarned = 0.0;
                
                if (transactionType.contains("deposit")) {
                    // Deposit: decreases credit (agent gives credit to customer)
                    creditChange = -amount;
                    trackedUsed = amount;
                } else if (transactionType.contains("withdrawal")) {
                    // Withdrawal: increases credit (customer gives cash, agent gets credit)
                    creditChange = amount;
                    trackedEarned = amount;
                } else if (transactionType.contains("transfer")) {
                    // Transfer: decreases credit by amount + fees
                    OperatorEntity operator = database.operatorDao().getById(operatorId);
                    if (operator != null) {
                        double transferRate = operator.getTransferRate();
                        double transferFee = amount * (transferRate / 100.0);
                        double totalAmount = amount + transferFee;
                        creditChange = -totalAmount;
                        trackedUsed = totalAmount;
                    } else {
                        // Fallback: treat as deposit
                        creditChange = -amount;
                        trackedUsed = amount;
                    }
                }
                
                // Update balances
                double currentBalance = operatorBalances.get(operatorId);
                operatorBalances.put(operatorId, currentBalance + creditChange);
                operatorCreditUsed.put(operatorId, operatorCreditUsed.get(operatorId) + trackedUsed);
                operatorCreditEarned.put(operatorId, operatorCreditEarned.get(operatorId) + trackedEarned);
                
                Log.d("OperatorBalanceHelper", "Transaction " + transaction.getId() + 
                      " (Type: " + transactionType + ", Amount: " + amount + 
                      ") -> Operator " + operatorId + " balance change: " + creditChange);
            }
            
            // Account for operator balance adjustments
            // Note: Adjustments don't store operatorId, so we sum all operator adjustments
            // and add them proportionally to operators that have transactions
            List<com.example.myapplication.database.entities.BalanceAdjustmentEntity> operatorAdjustments = 
                database.balanceAdjustmentDao().getAdjustmentsByUserAndType(userId, "operator");
            double totalOperatorAdjustments = 0.0;
            for (com.example.myapplication.database.entities.BalanceAdjustmentEntity adj : operatorAdjustments) {
                totalOperatorAdjustments += adj.getAmount();
            }
            Log.d("OperatorBalanceHelper", "Found operator balance adjustments totaling: " + totalOperatorAdjustments);
            
            // Distribute adjustments proportionally to operators (or add to first operator if only one)
            if (totalOperatorAdjustments != 0 && !operatorBalances.isEmpty()) {
                if (operatorBalances.size() == 1) {
                    // Single operator - add all adjustments to it
                    String operatorId = operatorBalances.keySet().iterator().next();
                    double current = operatorBalances.get(operatorId);
                    operatorBalances.put(operatorId, current + totalOperatorAdjustments);
                    Log.d("OperatorBalanceHelper", "Added " + totalOperatorAdjustments + " from adjustments to operator " + operatorId);
                } else {
                    // Multiple operators - distribute equally
                    double perOperator = totalOperatorAdjustments / operatorBalances.size();
                    for (String operatorId : operatorBalances.keySet()) {
                        double current = operatorBalances.get(operatorId);
                        operatorBalances.put(operatorId, current + perOperator);
                    }
                    Log.d("OperatorBalanceHelper", "Distributed " + totalOperatorAdjustments + " adjustments equally across " + operatorBalances.size() + " operators");
                }
            }
            
            // Update operator balance entities
            for (Map.Entry<String, Double> entry : operatorBalances.entrySet()) {
                String operatorId = entry.getKey();
                double calculatedBalanceFromTransactions = entry.getValue();
                double creditUsed = operatorCreditUsed.getOrDefault(operatorId, 0.0);
                double creditEarned = operatorCreditEarned.getOrDefault(operatorId, 0.0);
                
                OperatorBalanceEntity balance = getBalanceEntity(userId, operatorId);
                double existingBalance = balance.getBalance();
                
                // Calculate the difference - this represents purchases/adjustments that aren't in transactions
                // Note: Credit purchases directly update the balance but aren't tracked as transactions
                double purchasesAndAdjustments = existingBalance - calculatedBalanceFromTransactions;
                
                // Determine final balance:
                // - If existing balance is significantly higher than calculated, it likely includes purchases
                //   In this case, preserve the existing balance (it has purchases we can't recalculate)
                // - If existing balance is close to or lower than calculated, use calculated (transactions are source of truth)
                double finalBalance;
                if (purchasesAndAdjustments > 100 && existingBalance > 0) {
                    // Significant positive difference - likely includes credit purchases
                    // Preserve existing balance to keep purchases intact
                    finalBalance = existingBalance;
                    Log.d("OperatorBalanceHelper", "Operator " + operatorId + 
                          ": Preserving existing balance " + existingBalance + 
                          " (calculated from transactions: " + calculatedBalanceFromTransactions + 
                          ", difference: " + purchasesAndAdjustments + " likely from purchases/adjustments)");
                } else {
                    // Use calculated balance from transactions (source of truth)
                    // This ensures accuracy when balances are wrong
                    finalBalance = calculatedBalanceFromTransactions;
                    Log.d("OperatorBalanceHelper", "Operator " + operatorId + 
                          ": Using calculated balance from transactions: " + finalBalance + 
                          " (existing was: " + existingBalance + ")");
                }

                // Final safety: operator balance must never be negative
                if (finalBalance < 0) {
                    Log.w("OperatorBalanceHelper", "Recalculation produced negative balance (" +
                            finalBalance + ") for operator " + operatorId +
                            ". Clamping to 0 to keep credits non-negative.");
                    finalBalance = 0.0;
                }
                
                // Always update with the final balance
                balance.setBalance(finalBalance);
                // Usage/earned stats should also never be negative
                balance.setTotalCreditUsed(Math.max(0.0, creditUsed));
                balance.setTotalCreditEarned(Math.max(0.0, creditEarned));
                balance.setUpdatedAt(System.currentTimeMillis());
                balance.setNeedsSync(true);
                database.operatorBalanceDao().updateBalance(balance);
                
                Log.d("OperatorBalanceHelper", "Updated balance for operator " + operatorId + 
                      ": Balance=" + finalBalance + ", Used=" + creditUsed + ", Earned=" + creditEarned);
            }
            
            Log.d("OperatorBalanceHelper", "Balance recalculation completed for " + operatorBalances.size() + " operators");
            
        } catch (Exception e) {
            Log.e("OperatorBalanceHelper", "Error recalculating balances from transactions", e);
            e.printStackTrace();
        }
    }
}

