package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;

public class UserDeleteRequest {
    @SerializedName("confirm")
    public boolean confirm;

    @SerializedName("password")
    public String password;

    public UserDeleteRequest() {}

    public UserDeleteRequest(boolean confirm, String password) {
        this.confirm = confirm;
        this.password = password;
    }
}

