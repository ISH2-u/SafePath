package com.safepath.ai.repositories;

import androidx.annotation.NonNull;
import com.safepath.ai.network.AssistantApiService;
import com.safepath.ai.network.RetrofitClient;
import com.safepath.ai.network.dto.AssistantDto;
import com.safepath.ai.utils.NetworkErrorParser;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * AssistantRepository — Manages queries to the backend RAG Safety Assistant API.
 * Strictly decoupled from emergency SOS and contact modifications.
 */
public class AssistantRepository {

    public interface AssistantCallback {
        void onSuccess(AssistantDto.ChatMessageResponse response);
        void onError(String errorMessage);
    }

    private final AssistantApiService apiService;

    public AssistantRepository() {
        this.apiService = RetrofitClient.getInstance().create(AssistantApiService.class);
    }

    public void askAssistant(String message, String sessionId, @NonNull AssistantCallback callback) {
        AssistantDto.ChatMessageRequest req = new AssistantDto.ChatMessageRequest(message, sessionId);
        apiService.queryAssistant(req).enqueue(new Callback<AssistantDto.ChatMessageResponse>() {
            @Override
            public void onResponse(@NonNull Call<AssistantDto.ChatMessageResponse> call,
                                   @NonNull Response<AssistantDto.ChatMessageResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(NetworkErrorParser.parseHttpError(response, "Safety Assistant query failed"));
                }
            }

            @Override
            public void onFailure(@NonNull Call<AssistantDto.ChatMessageResponse> call, @NonNull Throwable t) {
                callback.onError(NetworkErrorParser.parseFailure(t));
            }
        });
    }
}

