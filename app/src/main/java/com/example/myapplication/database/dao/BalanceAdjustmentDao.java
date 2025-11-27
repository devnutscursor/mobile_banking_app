package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.myapplication.database.entities.BalanceAdjustmentEntity;

import java.util.List;

@Dao
public interface BalanceAdjustmentDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAdjustment(BalanceAdjustmentEntity adjustment);
    
    @Update
    void updateAdjustment(BalanceAdjustmentEntity adjustment);
    
    @Query("SELECT * FROM balance_adjustments WHERE id = :adjustmentId LIMIT 1")
    BalanceAdjustmentEntity getAdjustmentById(String adjustmentId);
    
    @Query("SELECT * FROM balance_adjustments WHERE userId = :userId ORDER BY createdAt DESC")
    List<BalanceAdjustmentEntity> getAdjustmentsByUser(String userId);
    
    @Query("SELECT * FROM balance_adjustments WHERE userId = :userId AND adjustmentType = :type ORDER BY createdAt DESC")
    List<BalanceAdjustmentEntity> getAdjustmentsByUserAndType(String userId, String type);
    
    @Query("SELECT * FROM balance_adjustments WHERE needsSync = 1")
    List<BalanceAdjustmentEntity> getNeedingSync();
    
    @Query("UPDATE balance_adjustments SET needsSync = 0, lastSyncAt = :syncTime WHERE id = :id")
    void markSynced(String id, long syncTime);
}



