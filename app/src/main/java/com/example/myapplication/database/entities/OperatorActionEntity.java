package com.example.myapplication.database.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Operator Action entity (offline-first).
 * For USSD operators, ussdTemplate may contain placeholders: {number}, {amount}
 * For TRADITIONAL operators, ussdTemplate is null and actions are form-based only.
 */
@Entity(tableName = "operator_actions")
public class OperatorActionEntity {
    @PrimaryKey
    @NonNull
    private String id; // UUID

    private String operatorId; // FK to OperatorEntity.id (not enforced by Room FK)
    private String name; // deposit, withdrawal, transfer, bills, etc.
    private String type; // "USSD" or "FORM"
    private String actionCode; // optional short code appended after operator code for USSD
    private String ussdTemplate; // e.g., *144*2*1*{number}*{amount}#
    private String requiredFieldsJson; // JSON array of field keys for forms

    private String addedBy; // owner user id

    private long createdAt;
    private long updatedAt;
    private long lastSyncAt;
    private boolean isActive;
    private boolean needsSync;
    private boolean disableUssd; // If true, USSD dialer will not be launched (for verification-only actions)

    public OperatorActionEntity() {}

    @Ignore
    public OperatorActionEntity(@NonNull String id, String operatorId, String name, String type, String ussdTemplate,
                                String requiredFieldsJson, String addedBy) {
        this.id = id;
        this.operatorId = operatorId;
        this.name = name;
        this.type = type;
        this.actionCode = null;
        this.ussdTemplate = ussdTemplate;
        this.requiredFieldsJson = requiredFieldsJson;
        this.addedBy = addedBy;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSyncAt = 0L;
        this.isActive = true;
        this.needsSync = true;
        this.disableUssd = false; // Default to false (USSD enabled)
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }

    public String getUssdTemplate() { return ussdTemplate; }
    public void setUssdTemplate(String ussdTemplate) { this.ussdTemplate = ussdTemplate; }

    public String getRequiredFieldsJson() { return requiredFieldsJson; }
    public void setRequiredFieldsJson(String requiredFieldsJson) { this.requiredFieldsJson = requiredFieldsJson; }

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

    public boolean isDisableUssd() { return disableUssd; }
    public void setDisableUssd(boolean disableUssd) { this.disableUssd = disableUssd; }
}


