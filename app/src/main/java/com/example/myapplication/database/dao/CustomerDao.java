package com.example.myapplication.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.myapplication.database.entities.CustomerEntity;

import java.util.List;

@Dao
public interface CustomerDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCustomer(CustomerEntity customer);
    
    @Update
    void updateCustomer(CustomerEntity customer);
    
    @Query("SELECT * FROM customers WHERE id = :customerId")
    CustomerEntity getCustomerById(String customerId);
    
    @Query("SELECT * FROM customers WHERE nationalIdNumber = :nationalId")
    CustomerEntity getCustomerByNationalId(String nationalId);
    
    /**
     * Find customer by National ID for a specific agent (createdBy)
     * Used to prevent duplicate National IDs per agent/dealer
     */
    @Query("SELECT * FROM customers WHERE nationalIdNumber = :nationalId AND createdBy = :userId AND isActive = 1")
    CustomerEntity getCustomerByNationalIdAndUser(String nationalId, String userId);
    
    @Query("SELECT * FROM customers WHERE isActive = 1 AND createdBy = :userId ORDER BY createdAt DESC")
    List<CustomerEntity> getCustomersByUser(String userId);
    
    
    @Query("SELECT * FROM customers WHERE isActive = 1 AND (fullName LIKE '%' || :searchQuery || '%' OR nationalIdNumber LIKE '%' || :searchQuery || '%' OR phoneNumber LIKE '%' || :searchQuery || '%') ORDER BY createdAt DESC")
    List<CustomerEntity> searchCustomers(String searchQuery);
    
    @Query("SELECT * FROM customers WHERE isActive = 1 AND createdBy = :userId AND (fullName LIKE '%' || :searchQuery || '%' OR nationalIdNumber LIKE '%' || :searchQuery || '%' OR phoneNumber LIKE '%' || :searchQuery || '%') ORDER BY createdAt DESC")
    List<CustomerEntity> searchCustomersByUser(String userId, String searchQuery);
    
    
    @Query("SELECT * FROM customers WHERE isActive = 1 ORDER BY createdAt DESC")
    List<CustomerEntity> getAllActiveCustomers();
    
    @Query("SELECT * FROM customers WHERE needsSync = 1")
    List<CustomerEntity> getCustomersNeedingSync();
    
    @Query("SELECT * FROM customers WHERE nationalIdNumber = :nationalId AND id != :excludeId")
    CustomerEntity getCustomerByNationalIdExcluding(String nationalId, String excludeId);
    
    /**
     * Find customer by National ID for a specific agent, excluding a specific customer ID
     * Used when editing an existing customer to allow keeping the same National ID
     */
    @Query("SELECT * FROM customers WHERE nationalIdNumber = :nationalId AND createdBy = :userId AND id != :excludeId AND isActive = 1")
    CustomerEntity getCustomerByNationalIdAndUserExcluding(String nationalId, String userId, String excludeId);
    
    @Query("UPDATE customers SET isActive = 0, needsSync = 1, updatedAt = :updatedAt WHERE id = :customerId")
    void softDeleteCustomer(String customerId, long updatedAt);
    
    @Query("DELETE FROM customers")
    void deleteAllCustomers();
    
    @Query("SELECT COUNT(*) FROM customers WHERE isActive = 1 AND createdBy = :userId")
    int getCustomerCountByUser(String userId);
    
    
    @Query("UPDATE customers SET needsSync = 0, lastSyncAt = :syncTime WHERE id = :customerId")
    void markAsSynced(String customerId, long syncTime);
    
    /**
     * Find customer by phone number for a specific agent (createdBy)
     * Used to prevent duplicate phone numbers per agent
     */
    @Query("SELECT * FROM customers WHERE phoneNumber = :phoneNumber AND createdBy = :userId AND isActive = 1")
    CustomerEntity getCustomerByPhoneNumberAndUser(String phoneNumber, String userId);
    
    /**
     * Find customer by phone number for a specific agent, excluding a specific customer ID
     * Used when editing an existing customer to allow keeping the same phone number
     */
    @Query("SELECT * FROM customers WHERE phoneNumber = :phoneNumber AND createdBy = :userId AND id != :excludeId AND isActive = 1")
    CustomerEntity getCustomerByPhoneNumberAndUserExcluding(String phoneNumber, String userId, String excludeId);
    
    /**
     * Find customer by National ID AND Phone Number for a specific agent
     * Used to prevent exact duplicates (same National ID + same phone number) per agent/dealer
     * This allows same National ID with different phone numbers
     */
    @Query("SELECT * FROM customers WHERE nationalIdNumber = :nationalId AND phoneNumber = :phoneNumber AND createdBy = :userId AND isActive = 1")
    CustomerEntity getCustomerByNationalIdAndPhoneAndUser(String nationalId, String phoneNumber, String userId);
    
    /**
     * Find customer by National ID AND Phone Number for a specific agent, excluding a specific customer ID
     * Used when editing an existing customer to allow keeping the same National ID + phone combination
     */
    @Query("SELECT * FROM customers WHERE nationalIdNumber = :nationalId AND phoneNumber = :phoneNumber AND createdBy = :userId AND id != :excludeId AND isActive = 1")
    CustomerEntity getCustomerByNationalIdAndPhoneAndUserExcluding(String nationalId, String phoneNumber, String userId, String excludeId);
}


