package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.myapplication.database.entities.CredentialEntity;

@Dao
public interface CredentialDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCredential(CredentialEntity credential);
    
    @Update
    void updateCredential(CredentialEntity credential);
    
    @Query("SELECT * FROM credentials WHERE email = :email")
    CredentialEntity getCredentialByEmail(String email);
    
    @Query("DELETE FROM credentials WHERE email = :email")
    void deleteCredential(String email);
    
    @Query("DELETE FROM credentials")
    void deleteAllCredentials();
}
