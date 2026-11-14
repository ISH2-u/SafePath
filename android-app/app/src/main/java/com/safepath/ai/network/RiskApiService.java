package com.safepath.ai.network;

import com.safepath.ai.network.dto.AreaRiskDto;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * RiskApiService — Retrofit interface for area risk estimation endpoints.
 */
public interface RiskApiService {

    @GET("risk/area")
    Call<AreaRiskDto> getAreaRisk(
            @Query("lat") double latitude,
            @Query("lng") double longitude,
            @Query("radius_km") double radiusKm
    );
}
