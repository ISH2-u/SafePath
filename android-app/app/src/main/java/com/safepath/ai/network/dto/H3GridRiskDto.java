package com.safepath.ai.network.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * H3GridRiskDto — Contiguous H3 hexagonal grid response from GET /api/v1/risk/h3-grid.
 */
public class H3GridRiskDto {

    @SerializedName("center_lat")
    public double centerLat;

    @SerializedName("center_lng")
    public double centerLng;

    @SerializedName("radius_km")
    public double radiusKm;

    @SerializedName("resolution")
    public int resolution;

    @SerializedName("current_cell_id")
    public String currentCellId;

    @SerializedName("current_risk_category")
    public String currentRiskCategory;

    @SerializedName("current_color_indicator")
    public String currentColorIndicator;

    @SerializedName("total_cells")
    public int totalCells;

    @SerializedName("cells_with_data")
    public int cellsWithData;

    @SerializedName("cells_no_data")
    public int cellsNoData;

    @SerializedName("last_updated")
    public String lastUpdated;

    @SerializedName("cells")
    public List<H3CellRiskDto> cells;

    @SerializedName("legend")
    public Map<String, String> legend;

    @SerializedName("disclaimer")
    public String disclaimer;

    @SerializedName("guarantee_of_safety")
    public boolean guaranteeOfSafety;
}
