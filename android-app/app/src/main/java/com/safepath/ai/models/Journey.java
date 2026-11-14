package com.safepath.ai.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Journey — represents a GPS-tracked journey session.
 * Stores start/end coordinates, timestamps, journey status,
 * cumulative distance, and recorded breadcrumb waypoints.
 */
@Entity(tableName = "journeys")
public class Journey {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String userId;
    public String destinationLabel;

    public double startLat;
    public double startLng;
    public double endLat;
    public double endLng;

    public long startedAt;
    public long endedAt;

    public String status; // ACTIVE | COMPLETED | CANCELLED

    /**
     * Serialized JSON array of breadcrumb waypoints:
     * [{"lat": 28.6139, "lng": 77.2090, "acc": 5.2, "t": 1690000000000}, ...]
     */
    public String waypointsJson;

    /** Cumulative distance covered in meters */
    public float distanceMeters;

    public Journey() {
        this.status = "ACTIVE";
        this.startedAt = System.currentTimeMillis();
        this.waypointsJson = "[]";
        this.distanceMeters = 0.0f;
    }
}
