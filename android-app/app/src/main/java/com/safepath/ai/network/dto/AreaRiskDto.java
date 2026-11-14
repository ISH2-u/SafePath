package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * AreaRiskDto — Area risk payload received from GET /api/v1/risk/area.
 * Represents historical relative recorded incidence density, NOT individual victimization probability.
 */
public class AreaRiskDto {

    @SerializedName("latitude")
    public double latitude;

    @SerializedName("longitude")
    public double longitude;

    @SerializedName("radius_km")
    public double radiusKm;

    @SerializedName("spatial_cell_id")
    public String spatialCellId;

    @SerializedName("risk_category")
    public String riskCategory;

    @SerializedName("risk_class")
    public int riskClass; // 0=Low, 1=Moderate, 2=High, -1=Insufficient Data

    @SerializedName("risk_code")
    public String riskCode; // LOW | MODERATE | HIGH | INSUFFICIENT_DATA

    @SerializedName("color_indicator")
    public String colorIndicator;

    @SerializedName("confidence_score")
    public double confidenceScore;

    @SerializedName("data_period")
    public String dataPeriod;

    @SerializedName("last_updated")
    public String lastUpdated;

    @SerializedName("incident_summary")
    public String incidentSummary;

    @SerializedName("safety_guidance")
    public String safetyGuidance;

    @SerializedName("disclaimer")
    public String disclaimer;

    @SerializedName("guarantee_of_safety")
    public boolean guaranteeOfSafety;
}
