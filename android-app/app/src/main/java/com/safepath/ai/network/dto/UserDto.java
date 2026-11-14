package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;

public class UserDto {
    @SerializedName("id")
    public String id;

    @SerializedName("email")
    public String email;

    @SerializedName("display_name")
    public String displayName;

    @SerializedName("phone")
    public String phone;

    @SerializedName("blood_group")
    public String bloodGroup;

    @SerializedName("medical_notes")
    public String medicalNotes;

    @SerializedName("is_active")
    public boolean isActive;

    @SerializedName("created_at")
    public String createdAt;
}

