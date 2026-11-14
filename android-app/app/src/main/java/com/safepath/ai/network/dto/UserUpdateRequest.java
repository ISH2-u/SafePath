package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * UserUpdateRequest — payload sent to PUT /auth/me.
 */
public class UserUpdateRequest {

    @SerializedName("display_name")
    public String fullName;         // serialized as display_name to match backend

    @SerializedName("email")
    public String email;

    @SerializedName("phone")
    public String phone;

    @SerializedName("blood_group")
    public String bloodGroup;

    @SerializedName("medical_notes")
    public String medicalNotes;

    /** No-arg constructor — set fields individually. */
    public UserUpdateRequest() {}

    /** Convenience constructor (old API kept for compatibility). */
    public UserUpdateRequest(String fullName, String phone, String bloodGroup, String medicalNotes) {
        this.fullName     = fullName;
        this.phone        = phone;
        this.bloodGroup   = bloodGroup;
        this.medicalNotes = medicalNotes;
    }
}
