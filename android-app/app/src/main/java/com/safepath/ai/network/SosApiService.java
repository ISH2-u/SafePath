package com.safepath.ai.network;

import com.safepath.ai.network.dto.SosResponse;
import com.safepath.ai.network.dto.SosTriggerRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * SosApiService — Retrofit interface for triggering manual SOS and polling status.
 */
public interface SosApiService {

    @POST("sos/trigger")
    Call<SosResponse> triggerSos(@Body SosTriggerRequest request);

    @GET("sos/{id}/status")
    Call<SosResponse> getSosStatus(@Path("id") String eventId);
}

