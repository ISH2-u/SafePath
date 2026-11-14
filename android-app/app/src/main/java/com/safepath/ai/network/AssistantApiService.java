package com.safepath.ai.network;

import com.safepath.ai.network.dto.AssistantDto;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * AssistantApiService — Retrofit interface for SafePath AI safety knowledge assistant.
 */
public interface AssistantApiService {

    @POST("assistant/chat")
    Call<AssistantDto.ChatMessageResponse> queryAssistant(@Body AssistantDto.ChatMessageRequest request);
}

