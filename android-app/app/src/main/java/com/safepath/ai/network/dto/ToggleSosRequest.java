package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;

public class ToggleSosRequest {
    @SerializedName("receive_sos_alerts")
    public boolean receiveSosAlerts;

    public ToggleSosRequest() {}

    public ToggleSosRequest(boolean receiveSosAlerts) {
        this.receiveSosAlerts = receiveSosAlerts;
    }
}

