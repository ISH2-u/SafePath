package com.safepath.ai.repositories;

import android.content.Context;
import androidx.annotation.NonNull;
import com.safepath.ai.network.RetrofitClient;
import com.safepath.ai.network.RiskApiService;
import com.safepath.ai.network.dto.AreaRiskDto;
import com.safepath.ai.network.dto.H3GridRiskDto;
import com.safepath.ai.utils.NetworkErrorParser;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * RiskRepository — Manages retrieval and caching of historical area-risk telemetry
 * and H3 hexagonal risk grids.
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

    private static class CachedEntry<T> {
        final T data;
        final long cachedAt;

        CachedEntry(T data) {
            this.data = data;
            this.cachedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - cachedAt) > CACHE_TTL_MS;
        }
    }

    private final RiskApiService apiService;
    private final Map<String, CachedEntry<AreaRiskDto>> areaCache = new ConcurrentHashMap<>();
    private final Map<String, CachedEntry<H3GridRiskDto>> gridCache = new ConcurrentHashMap<>();

    public RiskRepository(Context context) {
        this.apiService = RetrofitClient.getInstance().create(RiskApiService.class);
    }

    public void getAreaRisk(double lat, double lng, double radiusKm, @NonNull RiskCallback<AreaRiskDto> callback) {
        String cacheKey = String.format(Locale.US, "%.2f_%.2f", lat, lng);

        CachedEntry<AreaRiskDto> entry = areaCache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            callback.onSuccess(entry.data);
            return;
        }

        apiService.getAreaRisk(lat, lng, radiusKm).enqueue(new Callback<AreaRiskDto>() {
            @Override
            public void onResponse(@NonNull Call<AreaRiskDto> call, @NonNull Response<AreaRiskDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AreaRiskDto result = response.body();
                    areaCache.put(cacheKey, new CachedEntry<>(result));
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

    public void getH3GridRisk(double lat, double lng, double radiusKm, int resolution, @NonNull RiskCallback<H3GridRiskDto> callback) {
        String cacheKey = String.format(Locale.US, "grid_%.2f_%.2f_%.1f_%d", lat, lng, radiusKm, resolution);

        CachedEntry<H3GridRiskDto> entry = gridCache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            callback.onSuccess(entry.data);
            return;
        }

        apiService.getH3GridRisk(lat, lng, radiusKm, resolution).enqueue(new Callback<H3GridRiskDto>() {
            @Override
            public void onResponse(@NonNull Call<H3GridRiskDto> call, @NonNull Response<H3GridRiskDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    H3GridRiskDto result = response.body();
                    gridCache.put(cacheKey, new CachedEntry<>(result));
                    callback.onSuccess(result);
                } else {
                    callback.onError(NetworkErrorParser.parseHttpError(response, "H3 grid risk query failed"));
                }
            }

            @Override
            public void onFailure(@NonNull Call<H3GridRiskDto> call, @NonNull Throwable t) {
                callback.onError(NetworkErrorParser.parseFailure(t));
            }
        });
    }

    public void clearCache() {
        areaCache.clear();
        gridCache.clear();
    }
}
