package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.myapplication.database.entities.CommissionEntity;

import java.util.List;

@Dao
public interface CommissionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCommission(CommissionEntity commission);
    
    @Update
    void updateCommission(CommissionEntity commission);
    
    @Query("SELECT * FROM commissions WHERE id = :commissionId LIMIT 1")
    CommissionEntity getCommissionById(String commissionId);
    
    @Query("SELECT * FROM commissions WHERE transactionId = :transactionId LIMIT 1")
    CommissionEntity getCommissionByTransaction(String transactionId);
    
    @Query("DELETE FROM commissions WHERE transactionId = :transactionId")
    void deleteCommissionByTransaction(String transactionId);
    
    @Query("SELECT * FROM commissions WHERE userId = :userId ORDER BY createdAt DESC")
    List<CommissionEntity> getCommissionsByUser(String userId);
    
    // Daily reports
    @Query("SELECT * FROM commissions WHERE userId = :userId AND year = :year AND month = :month AND day = :day ORDER BY createdAt DESC")
    List<CommissionEntity> getCommissionsByDay(String userId, int year, int month, int day);
    
    @Query("SELECT SUM(totalCommission) FROM commissions WHERE userId = :userId AND year = :year AND month = :month AND day = :day")
    Double getTotalCommissionByDay(String userId, int year, int month, int day);
    
    // Monthly reports
    @Query("SELECT * FROM commissions WHERE userId = :userId AND year = :year AND month = :month ORDER BY createdAt DESC")
    List<CommissionEntity> getCommissionsByMonth(String userId, int year, int month);
    
    @Query("SELECT SUM(totalCommission) FROM commissions WHERE userId = :userId AND year = :year AND month = :month")
    Double getTotalCommissionByMonth(String userId, int year, int month);
    
    @Query("SELECT SUM(commissionAmount) FROM commissions WHERE userId = :userId AND year = :year AND month = :month")
    Double getTotalCommissionWithoutTaxByMonth(String userId, int year, int month);
    
    @Query("SELECT SUM(taxAmount) FROM commissions WHERE userId = :userId AND year = :year AND month = :month")
    Double getTotalTaxByMonth(String userId, int year, int month);
    
    // Yearly reports
    @Query("SELECT * FROM commissions WHERE userId = :userId AND year = :year ORDER BY createdAt DESC")
    List<CommissionEntity> getCommissionsByYear(String userId, int year);
    
    @Query("SELECT SUM(totalCommission) FROM commissions WHERE userId = :userId AND year = :year")
    Double getTotalCommissionByYear(String userId, int year);
    
    @Query("SELECT SUM(commissionAmount) FROM commissions WHERE userId = :userId AND year = :year")
    Double getTotalCommissionWithoutTaxByYear(String userId, int year);
    
    @Query("SELECT SUM(taxAmount) FROM commissions WHERE userId = :userId AND year = :year")
    Double getTotalTaxByYear(String userId, int year);
    
    // By operator
    @Query("SELECT * FROM commissions WHERE userId = :userId AND operatorId = :operatorId ORDER BY createdAt DESC")
    List<CommissionEntity> getCommissionsByUserAndOperator(String userId, String operatorId);
    
    @Query("SELECT SUM(totalCommission) FROM commissions WHERE userId = :userId AND operatorId = :operatorId AND year = :year AND month = :month")
    Double getTotalCommissionByUserOperatorAndMonth(String userId, String operatorId, int year, int month);
    
    // By transaction type
    @Query("SELECT * FROM commissions WHERE userId = :userId AND transactionType = :transactionType ORDER BY createdAt DESC")
    List<CommissionEntity> getCommissionsByUserAndType(String userId, String transactionType);
    
    // Date range queries
    @Query("SELECT * FROM commissions WHERE userId = :userId AND commissionDate >= :startDate AND commissionDate <= :endDate ORDER BY createdAt DESC")
    List<CommissionEntity> getCommissionsByDateRange(String userId, long startDate, long endDate);
    
    @Query("SELECT SUM(totalCommission) FROM commissions WHERE userId = :userId AND commissionDate >= :startDate AND commissionDate <= :endDate")
    Double getTotalCommissionByDateRange(String userId, long startDate, long endDate);
    
    // Sync queries
    @Query("SELECT * FROM commissions WHERE needsSync = 1")
    List<CommissionEntity> getCommissionsNeedingSync();
    
    @Query("UPDATE commissions SET needsSync = 0, lastSyncAt = :syncTime WHERE id = :commissionId")
    void markCommissionSynced(String commissionId, long syncTime);
}


