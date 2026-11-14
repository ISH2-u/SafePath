package com.safepath.ai.viewmodels;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.safepath.ai.models.Journey;
import com.safepath.ai.repositories.JourneyRepository;
import com.safepath.ai.services.LocationService;
import java.util.Locale;

/**
 * JourneyViewModel — Manages active GPS journey tracking state,
 * timer updates, location accuracy verification, and service communication.
 */
public class JourneyViewModel extends AndroidViewModel {

    private final JourneyRepository repository;

    private final MutableLiveData<Location> currentLocation = new MutableLiveData<>(null);
    private final MutableLiveData<Journey> activeJourney     = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isTracking        = new MutableLiveData<>(false);
    private final MutableLiveData<String> accuracyText       = new MutableLiveData<>("GPS Offline / Waiting");
    private final MutableLiveData<String> durationText       = new MutableLiveData<>("00:00:00");
    private final MutableLiveData<String> distanceText       = new MutableLiveData<>("0.00 km");
    private final MutableLiveData<Integer> waypointCount     = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> gpsAvailable      = new MutableLiveData<>(true);
    private final MutableLiveData<String> statusMessage      = new MutableLiveData<>(null);
    private final MutableLiveData<String> errorMessage        = new MutableLiveData<>(null);

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private long journeyStartTimestamp = 0;

    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;

            if (LocationService.BROADCAST_LOCATION_UPDATE.equals(intent.getAction())) {
                double lat = intent.getDoubleExtra(LocationService.EXTRA_LATITUDE, 0.0);
                double lng = intent.getDoubleExtra(LocationService.EXTRA_LONGITUDE, 0.0);
                float acc  = intent.getFloatExtra(LocationService.EXTRA_ACCURACY, 0.0f);
                float speed = intent.getFloatExtra(LocationService.EXTRA_SPEED, 0.0f);
                float bearing = intent.getFloatExtra(LocationService.EXTRA_BEARING, 0.0f);
                long time  = intent.getLongExtra(LocationService.EXTRA_TIME, System.currentTimeMillis());

                Location loc = new Location("fused");
                loc.setLatitude(lat);
                loc.setLongitude(lng);
                loc.setAccuracy(acc);
                loc.setSpeed(speed);
                loc.setBearing(bearing);
                loc.setTime(time);

                currentLocation.postValue(loc);

                // STRICT: Explicitly disclose accuracy. Never claim exact when uncertain.
                if (acc <= 8.0f) {
                    accuracyText.postValue(String.format(Locale.getDefault(), "High Accuracy (± %.1fm)", acc));
                } else if (acc <= 20.0f) {
                    accuracyText.postValue(String.format(Locale.getDefault(), "Moderate (± %.1fm)", acc));
                } else {
                    accuracyText.postValue(String.format(Locale.getDefault(), "Approximate (± %.1fm - Satellite Obstruction)", acc));
                }

                // Increment waypoint count
                Integer current = waypointCount.getValue();
                waypointCount.postValue((current != null ? current : 0) + 1);

            } else if (LocationService.BROADCAST_GPS_STATUS.equals(intent.getAction())) {
                boolean available = intent.getBooleanExtra(LocationService.EXTRA_IS_AVAILABLE, true);
                gpsAvailable.postValue(available);
                if (!available) {
                    accuracyText.postValue("GPS Signal Unavailable");
                }
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public JourneyViewModel(@NonNull Application application) {
        super(application);
        this.repository = new JourneyRepository(application.getApplicationContext());

        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationService.BROADCAST_LOCATION_UPDATE);
        filter.addAction(LocationService.BROADCAST_GPS_STATUS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(locationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            application.registerReceiver(locationReceiver, filter);
        }

        checkExistingActiveJourney();
    }

    public LiveData<Location> getCurrentLocation() { return currentLocation; }
    public LiveData<Journey> getActiveJourney()     { return activeJourney; }
    public LiveData<Boolean> getIsTracking()        { return isTracking; }
    public LiveData<String> getAccuracyText()       { return accuracyText; }
    public LiveData<String> getDurationText()       { return durationText; }
    public LiveData<String> getDistanceText()       { return distanceText; }
    public LiveData<Integer> getWaypointCount()     { return waypointCount; }
    public LiveData<Boolean> getGpsAvailable()      { return gpsAvailable; }
    public LiveData<String> getStatusMessage()      { return statusMessage; }
    public LiveData<String> getErrorMessage()        { return errorMessage; }

    public void checkExistingActiveJourney() {
        repository.getActiveJourneySync(new JourneyRepository.JourneyCallback<Journey>() {
            @Override
            public void onSuccess(Journey journey) {
                if (journey != null) {
                    activeJourney.postValue(journey);
                    isTracking.postValue(true);
                    journeyStartTimestamp = journey.startedAt;
                    startTimer();
                } else {
                    isTracking.postValue(false);
                }
            }

            @Override
            public void onError(String error) {
                // No active journey
            }
        });
    }

    public void startJourney(String destination) {
        Location loc = currentLocation.getValue();
        double startLat = (loc != null) ? loc.getLatitude() : 0.0;
        double startLng = (loc != null) ? loc.getLongitude() : 0.0;

        repository.startJourney("current_user", destination, startLat, startLng,
                new JourneyRepository.JourneyCallback<Journey>() {
            @Override
            public void onSuccess(Journey journey) {
                activeJourney.postValue(journey);
                isTracking.postValue(true);
                waypointCount.postValue(1);
                journeyStartTimestamp = System.currentTimeMillis();
                startTimer();

                // Start Foreground Service
                Context ctx = getApplication().getApplicationContext();
                Intent serviceIntent = new Intent(ctx, LocationService.class);
                serviceIntent.setAction(LocationService.ACTION_START_JOURNEY);
                serviceIntent.putExtra("journey_id", journey.id);
                ContextCompat.startForegroundService(ctx, serviceIntent);

                statusMessage.postValue("Journey started. Live safety protection active.");
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue("Could not start journey: " + error);
            }
        });
    }

    public void stopJourney() {
        Journey current = activeJourney.getValue();
        if (current == null) return;

        Location loc = currentLocation.getValue();
        double endLat = (loc != null) ? loc.getLatitude() : current.endLat;
        double endLng = (loc != null) ? loc.getLongitude() : current.endLng;

        repository.stopJourney(current.id, endLat, endLng,
                new JourneyRepository.JourneyCallback<Journey>() {
            @Override
            public void onSuccess(Journey journey) {
                isTracking.postValue(false);
                activeJourney.postValue(null);
                stopTimer();

                // Stop Foreground Service
                Context ctx = getApplication().getApplicationContext();
                Intent serviceIntent = new Intent(ctx, LocationService.class);
                serviceIntent.setAction(LocationService.ACTION_STOP_JOURNEY);
                ctx.startService(serviceIntent);

                statusMessage.postValue("Journey completed safely. Tracking stopped.");
            }

            @Override
            public void onError(String error) {
                errorMessage.postValue("Error stopping journey: " + error);
            }
        });
    }

    private void startTimer() {
        stopTimer();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (Boolean.TRUE.equals(isTracking.getValue()) && journeyStartTimestamp > 0) {
                    long elapsedSeconds = (System.currentTimeMillis() - journeyStartTimestamp) / 1000;
                    long hours = elapsedSeconds / 3600;
                    long minutes = (elapsedSeconds % 3600) / 60;
                    long seconds = elapsedSeconds % 60;
                    durationText.postValue(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopTimer();
        try {
            getApplication().unregisterReceiver(locationReceiver);
        } catch (Exception ignored) {}
    }
}

