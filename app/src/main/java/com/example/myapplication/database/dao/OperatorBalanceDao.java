package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import com.example.myapplication.database.entities.OperatorBalanceEntity;
import java.util.List;

@Dao
public interface OperatorBalanceDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBalance(OperatorBalanceEntity balance);
    
    @Update
    void updateBalance(OperatorBalanceEntity balance);
    
    @Delete
    void deleteBalance(OperatorBalanceEntity balance);
    
    @Query("SELECT * FROM operator_balances WHERE userId = :userId AND operatorId = :operatorId")
    OperatorBalanceEntity getBalance(String userId, String operatorId);
    
    @Query("SELECT * FROM operator_balances WHERE userId = :userId")
    List<OperatorBalanceEntity> getBalancesByUser(String userId);
    
    @Query("SELECT * FROM operator_balances WHERE operatorId = :operatorId")
    List<OperatorBalanceEntity> getBalancesByOperator(String operatorId);
    
    @Query("SELECT COALESCE(SUM(balance), 0) FROM operator_balances WHERE userId = :userId")
    double getTotalBalanceForUser(String userId);

    @Query("SELECT * FROM operator_balances WHERE userId = :userId AND needsSync = 1")
    List<OperatorBalanceEntity> getNeedingSyncForUser(String userId);
    
    @Query("DELETE FROM operator_balances WHERE userId = :userId AND operatorId = :operatorId")
    void deleteBalance(String userId, String operatorId);
    
    @Query("DELETE FROM operator_balances WHERE userId = :userId")
    void deleteAllBalancesForUser(String userId);

    @Query("SELECT * FROM operator_balances WHERE id = :id")
    OperatorBalanceEntity getById(String id);
}

