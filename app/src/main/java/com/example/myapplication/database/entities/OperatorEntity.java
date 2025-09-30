package com.example.myapplication.database.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Operator entity (offline-first).
 * Each user (dealer/agent) manages their own operators.
 * - enabled: business status (active/inactive in UI)
 * - isActive: record lifecycle (soft delete control)
 */
@Entity(tableName = "operators")
public class OperatorEntity {
    @PrimaryKey
    @NonNull
    private String id; // e.g., UUID or name-based ID

    private String name;
    private String type; // "USSD" or "TRADITIONAL"
    private boolean enabled; // business status toggle
    private String code; // base operator code/prefix, e.g., "*144*" (optional for non-USSD)
    private String color; // one of: orange,purple,blue,green,amber,red,teal,indigo

    // Ownership
    private String addedBy; // userId of creator

    // Timestamps & sync
    private long createdAt;
    private long updatedAt;
    private long lastSyncAt;
    private boolean isActive; // soft delete flag (true => record exists)
    private boolean needsSync; // pending sync flag

    public OperatorEntity() {}

    @Ignore
    public OperatorEntity(@NonNull String id, String name, String type, boolean enabled, String addedBy) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.enabled = enabled;
        this.addedBy = addedBy;
        this.code = null;
        this.color = "orange";
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSyncAt = 0L;
        this.isActive = true;
        this.needsSync = true;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getAddedBy() { return addedBy; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public long getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(long lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isNeedsSync() { return needsSync; }
    public void setNeedsSync(boolean needsSync) { this.needsSync = needsSync; }
}


