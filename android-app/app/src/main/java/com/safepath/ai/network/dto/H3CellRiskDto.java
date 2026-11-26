package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * H3CellRiskDto — Individual H3 hexagonal cell risk classification.
 */
public class H3CellRiskDto {

    @SerializedName("cell_id")
    public String cellId;

    @SerializedName("risk_class")
    public int riskClass; // -1: No Data, 0: Low, 1: Moderate, 2: High, 3: Very High

    @SerializedName("risk_code")
    public String riskCode; // NO_DATA, LOW, MODERATE, HIGH, VERY_HIGH

    @SerializedName("risk_category")
    public String riskCategory;

    @SerializedName("color_indicator")
    public String colorIndicator;

    @SerializedName("confidence_score")
    public double confidenceScore;

    @SerializedName("incident_count")
    public int incidentCount;

    @SerializedName("primary_incident_type")
    public String primaryIncidentType;

    @SerializedName("observation_period")
    public String observationPeriod;

    @SerializedName("center_lat")
    public double centerLat;

    @SerializedName("center_lng")
    public double centerLng;

    @SerializedName("is_current_location")
    public boolean isCurrentLocation;

    @SerializedName("polygon_coordinates")
    public List<List<Double>> polygonCoordinates;
}
