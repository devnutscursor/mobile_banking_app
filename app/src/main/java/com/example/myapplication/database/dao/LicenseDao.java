package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.myapplication.database.entities.LicenseEntity;

@Dao
public interface LicenseDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLicense(LicenseEntity license);
    
    @Update
    void updateLicense(LicenseEntity license);
    
    @Query("SELECT * FROM licenses WHERE licenseKey = :licenseKey LIMIT 1")
    LicenseEntity getLicenseByKey(String licenseKey);
    
    @Query("SELECT * FROM licenses WHERE assignedToUserId = :userId LIMIT 1")
    LicenseEntity getLicenseByUserId(String userId);
    
    @Query("UPDATE licenses SET assignedToUserId = :userId, isActive = :active, lastSyncAt = :syncTime WHERE licenseKey = :licenseKey")
    void assignLicenseToUser(String licenseKey, String userId, boolean active, long syncTime);
    
    @Query("UPDATE licenses SET lastSyncAt = :syncTime WHERE licenseKey = :licenseKey")
    void updateLastSync(String licenseKey, long syncTime);
    
    @Query("SELECT * FROM licenses WHERE lastSyncAt < :threshold")
    LicenseEntity[] getLicensesNeedingSync(long threshold);
    
    @Query("DELETE FROM licenses WHERE licenseKey = :licenseKey")
    void deleteLicense(String licenseKey);
    
    @Query("DELETE FROM licenses")
    void deleteAllLicenses();
}



