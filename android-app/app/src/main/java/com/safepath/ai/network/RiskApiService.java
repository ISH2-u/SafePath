package com.safepath.ai.network;

import com.safepath.ai.network.dto.AreaRiskDto;
import com.safepath.ai.network.dto.H3GridRiskDto;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * RiskApiService — Retrofit interface for area risk and H3 grid endpoints.
 */
public interface RiskApiService {

    @GET("risk/area")
    Call<AreaRiskDto> getAreaRisk(
            @Query("lat") double latitude,
            @Query("lng") double longitude,
            @Query("radius_km") double radiusKm
    );

    @GET("risk/h3-grid")
    Call<H3GridRiskDto> getH3GridRisk(
            @Query("lat") double latitude,
            @Query("lng") double longitude,
            @Query("radius_km") double radiusKm,
            @Query("resolution") int resolution
    );
}
