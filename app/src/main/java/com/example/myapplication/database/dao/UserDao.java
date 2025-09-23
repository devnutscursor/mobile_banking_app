package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.myapplication.database.entities.UserEntity;

@Dao
public interface UserDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(UserEntity user);
    
    @Update
    void updateUser(UserEntity user);
    
    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    UserEntity getUserById(String uid);
    
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    UserEntity getUserByEmail(String email);
    
    @Query("UPDATE users SET active = :active, updatedAt = :updatedAt WHERE uid = :uid")
    void updateUserActiveStatus(String uid, boolean active, long updatedAt);
    
    @Query("UPDATE users SET lastSyncAt = :syncTime WHERE uid = :uid")
    void updateLastSync(String uid, long syncTime);
    
    @Query("SELECT * FROM users WHERE lastSyncAt < :threshold")
    UserEntity[] getUsersNeedingSync(long threshold);
    
    @Query("DELETE FROM users WHERE uid = :uid")
    void deleteUser(String uid);
    
    @Query("DELETE FROM users")
    void deleteAllUsers();
}



