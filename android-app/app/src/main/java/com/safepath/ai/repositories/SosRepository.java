package com.safepath.ai.repositories;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.safepath.ai.database.SafePathDatabase;
import com.safepath.ai.database.dao.SosEventDao;
import com.safepath.ai.models.SosEvent;
import com.safepath.ai.network.RetrofitClient;
import com.safepath.ai.network.SosApiService;
import com.safepath.ai.network.dto.SosResponse;
import com.safepath.ai.network.dto.SosTriggerRequest;
import com.safepath.ai.utils.NetworkErrorParser;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * SosRepository — Coordinates local Room persistence, offline queuing,
 * idempotency duplicate prevention, and secure backend dispatch of SOS events.
 */
public class SosRepository {

    public interface SosCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage, SosEvent localEvent);
    }

    private final SosEventDao sosDao;
    private final SosApiService apiService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SosRepository(Context context) {
        this.sosDao = SafePathDatabase.getInstance(context).sosEventDao();
        this.apiService = RetrofitClient.getInstance().create(SosApiService.class);
    }

    public LiveData<SosEvent> getLatestEvent(String userId) {
        return sosDao.getLatestEvent(userId);
    }

    public LiveData<List<SosEvent>> getAllEvents(String userId) {
        return sosDao.getAllForUser(userId);
    }

    /**
     * Triggers a manual SOS:
     * 1. Saves locally in Room DB first (ensures offline safety).
     * 2. Generates an idempotency key to prevent accidental duplicate alerts.
     * 3. Dispatches to FastAPI backend.
     */
    public void triggerSos(
        String userId,
        Double latitude,
        Double longitude,
        Float accuracy,
        boolean isLastKnown,
        String triggerType,
        Integer batteryLevel,
        String notes,
        @NonNull SosCallback<SosResponse> callback
    ) {
        final String idempotencyKey = UUID.randomUUID().toString();

        executor.execute(() -> {
            // 1. Save locally in Room BEFORE network attempt
            SosEvent localEvent = new SosEvent();
            localEvent.userId = (userId != null && !userId.isEmpty()) ? userId : "current_user";
            localEvent.latitude = (latitude != null) ? latitude : 0.0;
            localEvent.longitude = (longitude != null) ? longitude : 0.0;
            localEvent.accuracy = (accuracy != null) ? accuracy : 0.0f;
            localEvent.isLastKnown = isLastKnown;
            localEvent.idempotencyKey = idempotencyKey;
            localEvent.triggerType = (triggerType != null) ? triggerType : "MANUAL";
            localEvent.status = "LOCAL_QUEUED";
            localEvent.triggeredAt = System.currentTimeMillis();

            long localId = sosDao.insert(localEvent);
            localEvent.id = localId;

            // 2. Prepare network request
            SosTriggerRequest req = new SosTriggerRequest(
                latitude, longitude, accuracy, isLastKnown,
                triggerType, idempotencyKey, batteryLevel, notes
            );

            // 3. Dispatch to FastAPI backend
            apiService.triggerSos(req).enqueue(new Callback<SosResponse>() {
                @Override
                public void onResponse(@NonNull Call<SosResponse> call, @NonNull Response<SosResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        SosResponse body = response.body();

                        executor.execute(() -> {
                            localEvent.remoteId = body.id;
                            localEvent.status = body.status;
                            localEvent.recipientsCount = body.recipientsCount;
                            localEvent.deliveredCount = body.deliveredCount;
                            localEvent.deliveryAttempts = body.deliveryAttempts;
                            localEvent.messageBody = body.messageBody;
                            sosDao.update(localEvent);
                        });

                        callback.onSuccess(body);
                    } else {
                        String errMsg = NetworkErrorParser.parseHttpError(response, "SOS dispatch failed");
                        executor.execute(() -> {
                            localEvent.status = "FAILED";
                            localEvent.errorMessage = errMsg;
                            localEvent.deliveryAttempts += 1;
                            sosDao.update(localEvent);
                        });
                        callback.onError(errMsg, localEvent);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<SosResponse> call, @NonNull Throwable t) {
                    String netErr = NetworkErrorParser.parseFailure(t) + "\n(Alert safely queued locally for retry)";
                    executor.execute(() -> {
                        localEvent.status = "LOCAL_QUEUED";
                        localEvent.errorMessage = netErr;
                        sosDao.update(localEvent);
                    });
                    callback.onError(netErr, localEvent);
                }
            });
        });
    }

    /**
     * Retries any pending or offline-queued alerts.
     */
    public void retryPendingAlerts() {
        executor.execute(() -> {
            List<SosEvent> pending = sosDao.getPendingRetries();
            for (SosEvent event : pending) {
                if (event.deliveryAttempts >= 5) continue; // cap retries

                SosTriggerRequest req = new SosTriggerRequest(
                    event.latitude, event.longitude, event.accuracy,
                    event.isLastKnown, event.triggerType, event.idempotencyKey,
                    null, "Automated background retry"
                );

                try {
                    Response<SosResponse> res = apiService.triggerSos(req).execute();
                    if (res.isSuccessful() && res.body() != null) {
                        SosResponse body = res.body();
                        event.remoteId = body.id;
                        event.status = body.status;
                        event.recipientsCount = body.recipientsCount;
                        event.deliveredCount = body.deliveredCount;
                        event.messageBody = body.messageBody;
                        sosDao.update(event);
                    } else {
                        event.deliveryAttempts += 1;
                        sosDao.update(event);
                    }
                } catch (Exception e) {
                    event.deliveryAttempts += 1;
                    sosDao.update(event);
                }
            }
        });
    }
}

