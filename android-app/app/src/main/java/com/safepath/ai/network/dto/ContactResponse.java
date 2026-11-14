package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;

public class ContactResponse {
    @SerializedName("id")
    public String id;

    @SerializedName("user_id")
    public String userId;

    @SerializedName("name")
    public String name;

    @SerializedName("phone")
    public String phone;

    @SerializedName("email")
    public String email;

    @SerializedName("relationship_type")
    public String relationshipType;

    @SerializedName("is_verified")
    public boolean isVerified;

    @SerializedName("receive_sos_alerts")
    public boolean receiveSosAlerts;

    @SerializedName("added_at")
    public String addedAt;
}

