package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;

public class ContactRequest {
    @SerializedName("name")
    public String name;

    @SerializedName("phone")
    public String phone;

    @SerializedName("email")
    public String email;

    @SerializedName("relationship_type")
    public String relationshipType;

    @SerializedName("receive_sos_alerts")
    public boolean receiveSosAlerts;

    public ContactRequest() {}

    public ContactRequest(String name, String phone, String email, String relationshipType, boolean receiveSosAlerts) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.relationshipType = relationshipType;
        this.receiveSosAlerts = receiveSosAlerts;
    }
}

