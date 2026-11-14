package com.safepath.ai.models;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * SosEvent — Represents an emergency SOS event.
 * Stored locally in Room BEFORE attempting network delivery to ensure zero data loss.
 */
@Entity(
    tableName = "sos_events",
    indices = {@Index(value = {"idempotencyKey"}, unique = true)}
)
public class SosEvent {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String remoteId;
    public String userId;

    public double latitude;
    public double longitude;
    public float accuracy;
    public boolean isLastKnown;

    public String idempotencyKey;
    public String triggerType; // MANUAL | VOICE | FALL_DETECT
    public String status;      // LOCAL_QUEUED | PENDING | SENT | DELIVERED | FAILED

    public int deliveryAttempts;
    public int recipientsCount;
    public int deliveredCount;

    public String messageBody;
    public String errorMessage;
    public long triggeredAt;

    public SosEvent() {
        this.status = "LOCAL_QUEUED";
        this.deliveryAttempts = 0;
        this.recipientsCount = 0;
        this.deliveredCount = 0;
        this.triggeredAt = System.currentTimeMillis();
    }
}
