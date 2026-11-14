package com.safepath.ai.repositories;

import android.content.Context;
import androidx.annotation.NonNull;
import com.safepath.ai.network.RetrofitClient;
import com.safepath.ai.network.RiskApiService;
import com.safepath.ai.network.dto.AreaRiskDto;
import com.safepath.ai.utils.NetworkErrorParser;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * RiskRepository — Manages retrieval and caching of historical area-risk telemetry.
 *
 * CACHING RULES:
 * 1. 15-minute TTL per spatial coordinate cluster (~1.1km grid cell).
 * 2. Never presents stale results as current live estimates.
 * 3. Never invents synthetic risk numbers when API is unavailable.
 */
public class RiskRepository {

    public interface RiskCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private static final long CACHE_TTL_MS = 15 * 60 * 1000; // 15 minutes

    private static class CachedEntry {
        final AreaRiskDto dto;
        final long cachedAt;

        CachedEntry(AreaRiskDto dto) {
            this.dto = dto;
            this.cachedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - cachedAt) > CACHE_TTL_MS;
        }
    }

    private final RiskApiService apiService;
    private final Map<String, CachedEntry> memoryCache = new ConcurrentHashMap<>();

    public RiskRepository(Context context) {
        this.apiService = RetrofitClient.getInstance().create(RiskApiService.class);
    }

    public void getAreaRisk(double lat, double lng, double radiusKm, @NonNull RiskCallback<AreaRiskDto> callback) {
        String cacheKey = String.format(Locale.US, "%.2f_%.2f", lat, lng);

        // Check memory cache
        CachedEntry entry = memoryCache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            callback.onSuccess(entry.dto);
            return;
        }

        // Fetch from API
        apiService.getAreaRisk(lat, lng, radiusKm).enqueue(new Callback<AreaRiskDto>() {
            @Override
            public void onResponse(@NonNull Call<AreaRiskDto> call, @NonNull Response<AreaRiskDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AreaRiskDto result = response.body();
                    memoryCache.put(cacheKey, new CachedEntry(result));
                    callback.onSuccess(result);
                } else {
                    callback.onError(NetworkErrorParser.parseHttpError(response, "Area risk query failed"));
                }
            }

            @Override
            public void onFailure(@NonNull Call<AreaRiskDto> call, @NonNull Throwable t) {
                callback.onError(NetworkErrorParser.parseFailure(t));
            }
        });
    }

    public void clearCache() {
        memoryCache.clear();
    }
}
