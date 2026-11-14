package com.safepath.ai.repositories;

import android.content.Context;
import androidx.lifecycle.LiveData;
import com.safepath.ai.database.SafePathDatabase;
import com.safepath.ai.database.dao.JourneyDao;
import com.safepath.ai.models.Journey;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JourneyRepository — abstracts Room local database operations for journeys.
 * Guarantees local offline persistence even during complete network loss.
 */
public class JourneyRepository {

    public interface JourneyCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private final JourneyDao journeyDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public JourneyRepository(Context context) {
        this.journeyDao = SafePathDatabase.getInstance(context).journeyDao();
    }

    public LiveData<Journey> getActiveJourney(String userId) {
        return journeyDao.getActiveJourney(userId);
    }

    public LiveData<List<Journey>> getAllJourneys(String userId) {
        return journeyDao.getAllForUser(userId);
    }

    public void startJourney(String userId, String destinationLabel,
                             double startLat, double startLng,
                             JourneyCallback<Journey> callback) {
        executor.execute(() -> {
            try {
                // If there is an existing ACTIVE journey, complete it first
                Journey existing = journeyDao.getActiveJourneySync();
                if (existing != null) {
                    existing.status = "COMPLETED";
                    existing.endedAt = System.currentTimeMillis();
                    journeyDao.update(existing);
                }

                Journey journey = new Journey();
                journey.userId = (userId != null && !userId.isEmpty()) ? userId : "local_user";
                journey.destinationLabel = (destinationLabel != null && !destinationLabel.trim().isEmpty())
                        ? destinationLabel.trim() : "Custom Journey";
                journey.startLat = startLat;
                journey.startLng = startLng;
                journey.endLat = startLat;
                journey.endLng = startLng;
                journey.startedAt = System.currentTimeMillis();
                journey.status = "ACTIVE";

                // Seed initial waypoint
                JSONArray waypoints = new JSONArray();
                if (startLat != 0.0 || startLng != 0.0) {
                    JSONObject pt = new JSONObject();
                    pt.put("lat", startLat);
                    pt.put("lng", startLng);
                    pt.put("acc", 10.0f);
                    pt.put("t", journey.startedAt);
                    waypoints.put(pt);
                }
                journey.waypointsJson = waypoints.toString();

                long id = journeyDao.insert(journey);
                journey.id = id;

                if (callback != null) {
                    callback.onSuccess(journey);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("Failed to start journey: " + e.getMessage());
                }
            }
        });
    }

    public void stopJourney(long journeyId, double endLat, double endLng,
                            JourneyCallback<Journey> callback) {
        executor.execute(() -> {
            try {
                Journey journey = journeyDao.getJourneyById(journeyId);
                if (journey != null) {
                    journey.endedAt = System.currentTimeMillis();
                    journey.endLat = endLat;
                    journey.endLng = endLng;
                    journey.status = "COMPLETED";
                    journeyDao.update(journey);
                    if (callback != null) callback.onSuccess(journey);
                } else {
                    if (callback != null) callback.onError("Journey not found: " + journeyId);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("Failed to end journey: " + e.getMessage());
                }
            }
        });
    }

    public void getActiveJourneySync(JourneyCallback<Journey> callback) {
        executor.execute(() -> {
            try {
                Journey journey = journeyDao.getActiveJourneySync();
                if (callback != null) callback.onSuccess(journey);
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }
}

