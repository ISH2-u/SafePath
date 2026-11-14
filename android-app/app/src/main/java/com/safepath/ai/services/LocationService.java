package com.safepath.ai.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.safepath.ai.R;
import com.safepath.ai.activities.MainActivity;
import com.safepath.ai.database.SafePathDatabase;
import com.safepath.ai.models.Journey;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LocationService — Foreground service for active journey GPS tracking.
 * Uses Google Play Services FusedLocationProviderClient.
 *
 * SAFETY RULES ENFORCED:
 * 1. Only tracks when an explicit journey has been initiated.
 * 2. Broadcasts exact accuracy radius so UI never claims false precision.
 * 3. Immediately stops foreground tracking when journey is terminated.
 */
public class LocationService extends Service {

    private static final String TAG = "LocationService";

    public static final String ACTION_START_JOURNEY = "com.safepath.ai.action.START_JOURNEY";
    public static final String ACTION_STOP_JOURNEY  = "com.safepath.ai.action.STOP_JOURNEY";

    public static final String BROADCAST_LOCATION_UPDATE = "com.safepath.ai.LOCATION_UPDATE";
    public static final String BROADCAST_GPS_STATUS      = "com.safepath.ai.GPS_STATUS";

    public static final String EXTRA_LATITUDE  = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";
    public static final String EXTRA_ACCURACY  = "accuracy";
    public static final String EXTRA_SPEED     = "speed";
    public static final String EXTRA_BEARING   = "bearing";
    public static final String EXTRA_TIME      = "time";
    public static final String EXTRA_IS_AVAILABLE = "is_available";

    private static final String CHANNEL_ID = "safepath_journey_tracking";
    private static final int NOTIFICATION_ID = 2001;

    private static final long UPDATE_INTERVAL_MS = 4000;
    private static final long FASTEST_INTERVAL_MS = 2000;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation = null;
    private float totalDistanceMeters = 0.0f;
    private long activeJourneyId = -1;

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        handleNewLocation(location);
                    }
                }
            }

            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                boolean isAvailable = locationAvailability.isLocationAvailable();
                Intent intent = new Intent(BROADCAST_GPS_STATUS);
                intent.putExtra(EXTRA_IS_AVAILABLE, isAvailable);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_JOURNEY.equals(intent.getAction())) {
            stopJourneyTracking();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_START_JOURNEY.equals(intent.getAction())) {
            activeJourneyId = intent.getLongExtra("journey_id", -1);
            startForegroundServiceWithNotification();
            startLocationUpdates();
        }

        return START_STICKY;
    }

    private void startForegroundServiceWithNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, LocationService.class);
        stopIntent.setAction(ACTION_STOP_JOURNEY);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafePath Journey Active")
                .setContentText("Continuous GPS monitoring and safety tracking enabled.")
                .setSmallIcon(R.drawable.ic_location)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_stop, "End Journey", stopPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        try {
            LocationRequest locationRequest = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
                    .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                    .setMinUpdateDistanceMeters(2.0f)
                    .build();

            fusedLocationClient.requestLocationUpdates(
                    locationRequest, locationCallback, Looper.getMainLooper()
            );
            Log.d(TAG, "Location updates requested successfully.");
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing: " + e.getMessage());
        }
    }

    private void handleNewLocation(Location location) {
        if (lastLocation != null) {
            float distance = lastLocation.distanceTo(location);
            // Ignore tiny GPS jitter (< 1.5m)
            if (distance >= 1.5f) {
                totalDistanceMeters += distance;
            }
        }
        lastLocation = location;

        // 1. Broadcast to ViewModels and UI
        Intent broadcast = new Intent(BROADCAST_LOCATION_UPDATE);
        broadcast.putExtra(EXTRA_LATITUDE, location.getLatitude());
        broadcast.putExtra(EXTRA_LONGITUDE, location.getLongitude());
        broadcast.putExtra(EXTRA_ACCURACY, location.getAccuracy());
        broadcast.putExtra(EXTRA_SPEED, location.getSpeed());
        broadcast.putExtra(EXTRA_BEARING, location.getBearing());
        broadcast.putExtra(EXTRA_TIME, location.getTime());
        broadcast.setPackage(getPackageName());
        sendBroadcast(broadcast);

        // 2. Persist waypoint to active journey in Room DB
        if (activeJourneyId > 0) {
            final double lat = location.getLatitude();
            final double lng = location.getLongitude();
            final float acc = location.getAccuracy();
            final long time = location.getTime();
            final float dist = totalDistanceMeters;

            dbExecutor.execute(() -> {
                try {
                    Journey journey = SafePathDatabase.getInstance(getApplicationContext())
                            .journeyDao()
                            .getJourneyById(activeJourneyId);

                    if (journey != null && "ACTIVE".equals(journey.status)) {
                        JSONArray waypoints;
                        if (journey.waypointsJson != null && !journey.waypointsJson.isEmpty()) {
                            waypoints = new JSONArray(journey.waypointsJson);
                        } else {
                            waypoints = new JSONArray();
                        }

                        JSONObject pt = new JSONObject();
                        pt.put("lat", lat);
                        pt.put("lng", lng);
                        pt.put("acc", acc);
                        pt.put("t", time);
                        waypoints.put(pt);

                        journey.waypointsJson = waypoints.toString();
                        journey.distanceMeters = dist;
                        SafePathDatabase.getInstance(getApplicationContext())
                                .journeyDao()
                                .update(journey);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error saving waypoint: " + e.getMessage());
                }
            });
        }
    }

    private void stopJourneyTracking() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        stopForeground(true);
        Log.d(TAG, "Journey tracking stopped.");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SafePath Journey Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active GPS safety tracking status.");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopJourneyTracking();
        dbExecutor.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

