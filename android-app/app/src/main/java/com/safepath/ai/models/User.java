package com.safepath.ai.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Local cached representation of the authenticated user. */
@Entity(tableName = "users")
public class User {
    @PrimaryKey
    @NonNull
    public String id = "";
    public String email;
    public String displayName;
    public String phone;
    public String bloodGroup;
    public String medicalNotes;
    public long createdAt;
}
