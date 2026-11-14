package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SosResponse {

    @SerializedName("id")
    public String id;

    @SerializedName("user_id")
    public String userId;

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

    @SerializedName("status")
    public String status;

    @SerializedName("delivery_attempts")
    public int deliveryAttempts;

    @SerializedName("recipients_count")
    public int recipientsCount;

    @SerializedName("delivered_count")
    public int deliveredCount;

    @SerializedName("message_body")
    public String messageBody;

    @SerializedName("triggered_at")
    public String triggeredAt;

    @SerializedName("recipients")
    public List<RecipientStatusDto> recipients;

    @SerializedName("is_idempotent_replay")
    public boolean isIdempotentReplay;

    @SerializedName("disclaimer")
    public String disclaimer;

    public static class RecipientStatusDto {
        @SerializedName("contact_id")
        public String contactId;

        @SerializedName("contact_name")
        public String contactName;

        @SerializedName("phone")
        public String phone;

        @SerializedName("channel")
        public String channel;

        @SerializedName("status")
        public String status;

        @SerializedName("dispatched_at")
        public String dispatchedAt;
    }
}

