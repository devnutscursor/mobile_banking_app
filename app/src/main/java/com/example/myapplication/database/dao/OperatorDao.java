package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.myapplication.database.entities.OperatorEntity;

import java.util.List;

@Dao
public interface OperatorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOperator(OperatorEntity operator);

    @Update
    void updateOperator(OperatorEntity operator);

    @Query("SELECT * FROM operators WHERE id = :id")
    OperatorEntity getById(String id);

    @Query("SELECT * FROM operators WHERE isActive = 1 AND addedBy = :userId ORDER BY createdAt DESC")
    List<OperatorEntity> getByUser(String userId);

    @Query("SELECT * FROM operators WHERE isActive = 1 AND addedBy = :userId AND enabled = 1 ORDER BY name ASC")
    List<OperatorEntity> getActiveForUser(String userId);

    @Query("SELECT * FROM operators WHERE needsSync = 1")
    List<OperatorEntity> getNeedingSync();

    @Query("UPDATE operators SET isActive = 0, needsSync = 1, updatedAt = :updatedAt WHERE id = :id")
    void softDelete(String id, long updatedAt);

    @Query("UPDATE operators SET needsSync = 0, lastSyncAt = :syncTime WHERE id = :id")
    void markSynced(String id, long syncTime);

    // Search by name or code
    @Query("SELECT * FROM operators WHERE isActive = 1 AND addedBy = :userId AND (name LIKE :q OR code LIKE :q) ORDER BY name ASC")
    List<OperatorEntity> search(String userId, String q);
}


