package com.example.myapplication.database.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import androidx.annotation.NonNull;

@Entity(tableName = "credentials")
public class CredentialEntity {
    @PrimaryKey
    @NonNull
    private String email;
    
    private String passwordHash; // Store hashed password, not plain text
    private String userId; // Link to user
    private long createdAt;
    private long updatedAt;

    // Default constructor
    public CredentialEntity() {}

    // Constructor
    @Ignore
    public CredentialEntity(@NonNull String email, String passwordHash, String userId) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.userId = userId;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Getters and Setters
    @NonNull
    public String getEmail() { return email; }
    public void setEmail(@NonNull String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
