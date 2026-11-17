package com.safepath.ai.network.dto;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

public class VerificationResponse {

    @SerializedName("contact_id")
    public String contactId;

    @SerializedName("phone")
    public String phone;

    @SerializedName("status")
    public String status;

    @SerializedName("message")
    public String message;

    @Nullable
    @SerializedName("dev_verification_code")
    public String devVerificationCode;
}
