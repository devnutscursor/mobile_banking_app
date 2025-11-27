package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.myapplication.database.entities.CommissionRateEntity;

import java.util.List;

@Dao
public interface CommissionRateDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCommissionRate(CommissionRateEntity rate);
    
    @Update
    void updateCommissionRate(CommissionRateEntity rate);
    
    @Query("SELECT * FROM commission_rates WHERE id = :rateId LIMIT 1")
    CommissionRateEntity getCommissionRateById(String rateId);
    
    @Query("SELECT * FROM commission_rates WHERE userId = :userId")
    List<CommissionRateEntity> getCommissionRatesByUser(String userId);
    
    @Query("SELECT * FROM commission_rates WHERE userId = :userId AND operatorId = :operatorId LIMIT 1")
    CommissionRateEntity getCommissionRateByUserAndOperator(String userId, String operatorId);
    
    @Query("SELECT * FROM commission_rates WHERE userRole = :role")
    List<CommissionRateEntity> getCommissionRatesByRole(String role);
    
    @Query("SELECT * FROM commission_rates WHERE operatorId = :operatorId")
    List<CommissionRateEntity> getCommissionRatesByOperator(String operatorId);
    
    @Query("SELECT * FROM commission_rates WHERE needsSync = 1")
    List<CommissionRateEntity> getCommissionRatesNeedingSync();
    
    @Query("UPDATE commission_rates SET needsSync = 0, lastSyncAt = :syncTime WHERE id = :rateId")
    void markCommissionRateSynced(String rateId, long syncTime);
    
    @Query("DELETE FROM commission_rates WHERE id = :rateId")
    void deleteCommissionRate(String rateId);
}






