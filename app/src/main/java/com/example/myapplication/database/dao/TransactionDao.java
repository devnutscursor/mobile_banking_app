package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.myapplication.database.entities.TransactionEntity;

import java.util.List;

@Dao
public interface TransactionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransaction(TransactionEntity transaction);
    
    @Update
    void updateTransaction(TransactionEntity transaction);
    
    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    TransactionEntity getTransactionById(String transactionId);
    
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY createdAt DESC")
    List<TransactionEntity> getTransactionsByUser(String userId);
    
    @Query("SELECT * FROM transactions WHERE userId = :userId AND status = :status ORDER BY createdAt DESC")
    List<TransactionEntity> getTransactionsByUserAndStatus(String userId, String status);
    
    @Query("SELECT * FROM transactions WHERE userId = :userId AND createdAt >= :startTime ORDER BY createdAt DESC")
    List<TransactionEntity> getTransactionsSinceDate(String userId, long startTime);
    
    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId")
    int getTransactionCountByUser(String userId);
    
    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId AND createdAt >= :startTime")
    int getTodayTransactionCount(String userId, long startTime);
    
    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId AND createdAt >= :startTime")
    int getLast7DaysTransactionCount(String userId, long startTime);
    
    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND transactionType = 'deposit' AND status = 'completed'")
    Double getTotalDeposits(String userId);
    
    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND transactionType = 'withdrawal' AND status = 'completed'")
    Double getTotalWithdrawals(String userId);
    
    @Query("SELECT * FROM transactions WHERE needsSync = 1 ORDER BY createdAt ASC")
    List<TransactionEntity> getUnsyncedTransactions();
    
    @Query("DELETE FROM transactions WHERE id = :transactionId")
    void deleteTransaction(String transactionId);
    
    @Query("DELETE FROM transactions")
    void deleteAllTransactions();
}


