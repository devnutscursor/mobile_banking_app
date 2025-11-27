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
    
    @Query("SELECT * FROM licenses WHERE licenseKey = :licenseKey AND assignedToUserId = :userId LIMIT 1")
    LicenseEntity getLicenseByKeyForUser(String licenseKey, String userId);
    
    @Query("SELECT * FROM licenses WHERE assignedToUserId = :userId LIMIT 1")
    LicenseEntity getLicenseByUserId(String userId);
    
    @Query("UPDATE licenses SET lastSyncAt = :syncTime WHERE licenseKey = :licenseKey AND assignedToUserId = :userId")
    void updateLastSync(String licenseKey, String userId, long syncTime);
    
    @Query("SELECT * FROM licenses WHERE lastSyncAt < :threshold")
    LicenseEntity[] getLicensesNeedingSync(long threshold);
    
    @Query("DELETE FROM licenses WHERE licenseKey = :licenseKey")
    void deleteLicense(String licenseKey);
    
    @Query("DELETE FROM licenses WHERE assignedToUserId = :userId")
    void deleteLicenseByUserId(String userId);
    
    @Query("DELETE FROM licenses")
    void deleteAllLicenses();
}



