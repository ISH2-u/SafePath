package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * RegisterRequest — payload sent to POST /auth/register.
 * fullName maps to display_name on the backend.
 */
public class RegisterRequest {

    @SerializedName("email")
    public String email;

    @SerializedName("password")
    public String password;

    @SerializedName("display_name")
    public String fullName;         // serialized as display_name to match backend schema

    @SerializedName("phone")
    public String phone;

    /** No-arg constructor for use by RegisterActivity (fields set individually). */
    public RegisterRequest() {}

    /** Convenience constructor. */
    public RegisterRequest(String email, String password, String fullName, String phone) {
        this.email    = email;
        this.password = password;
        this.fullName = fullName;
        this.phone    = phone;
    }
}
