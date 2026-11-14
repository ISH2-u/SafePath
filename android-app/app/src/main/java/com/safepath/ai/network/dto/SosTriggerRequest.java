package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;

public class SosTriggerRequest {

    @SerializedName("latitude")
    public Double latitude;

    @SerializedName("longitude")
    public Double longitude;

    @SerializedName("accuracy")
    public Float accuracy;

    @SerializedName("is_last_known")
    public boolean isLastKnown;

    @SerializedName("trigger_type")
    public String triggerType;

    @SerializedName("idempotency_key")
    public String idempotencyKey;

    @SerializedName("battery_level")
    public Integer batteryLevel;

    @SerializedName("notes")
    public String notes;

    public SosTriggerRequest() {}

    public SosTriggerRequest(Double latitude, Double longitude, Float accuracy,
                             boolean isLastKnown, String triggerType,
                             String idempotencyKey, Integer batteryLevel, String notes) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.isLastKnown = isLastKnown;
        this.triggerType = triggerType;
        this.idempotencyKey = idempotencyKey;
        this.batteryLevel = batteryLevel;
        this.notes = notes;
    }
}

