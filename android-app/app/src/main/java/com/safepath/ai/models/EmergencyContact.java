package com.safepath.ai.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** An emergency contact added and verified by the user. */
@Entity(tableName = "emergency_contacts")
public class EmergencyContact {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String remoteId;
    public String userId;
    public String name;
    public String phone;
    public String email;
    public String relationshipType;
    public boolean isVerified;
    public boolean receiveSosAlerts;
    public long addedAt;
}
