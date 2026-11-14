package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;

public class VerifyContactRequest {
    @SerializedName("code")
    public String code;

    public VerifyContactRequest() {}

    public VerifyContactRequest(String code) {
        this.code = code;
    }
}

