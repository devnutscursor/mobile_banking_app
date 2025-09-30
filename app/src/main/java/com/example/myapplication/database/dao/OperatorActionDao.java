package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.myapplication.database.entities.OperatorActionEntity;

import java.util.List;

@Dao
public interface OperatorActionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAction(OperatorActionEntity action);

    @Update
    void updateAction(OperatorActionEntity action);

    @Query("SELECT * FROM operator_actions WHERE id = :id")
    OperatorActionEntity getById(String id);

    @Query("SELECT * FROM operator_actions WHERE isActive = 1 AND operatorId = :operatorId AND addedBy = :userId ORDER BY createdAt DESC")
    List<OperatorActionEntity> getByOperatorForUser(String operatorId, String userId);

    @Query("SELECT * FROM operator_actions WHERE needsSync = 1")
    List<OperatorActionEntity> getNeedingSync();

    @Query("UPDATE operator_actions SET isActive = 0, needsSync = 1, updatedAt = :updatedAt WHERE id = :id")
    void softDelete(String id, long updatedAt);

    @Query("UPDATE operator_actions SET needsSync = 0, lastSyncAt = :syncTime WHERE id = :id")
    void markSynced(String id, long syncTime);
}





