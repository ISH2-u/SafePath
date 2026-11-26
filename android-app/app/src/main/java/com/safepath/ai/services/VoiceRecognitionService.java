package com.safepath.ai.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.safepath.ai.R;
import com.safepath.ai.activities.MainActivity;
import com.safepath.ai.ai.CodewordDetector;
import com.safepath.ai.network.dto.SosResponse;
import com.safepath.ai.repositories.SosRepository;
import com.safepath.ai.utils.VoicePreferenceManager;

/**
 * VoiceRecognitionService — Compliant Android Foreground Service for on-device voice codeword listening.
 *
 * CRITICAL REQUIREMENTS ENFORCED:
 * 1. Uses FOREGROUND_SERVICE_MICROPHONE with clear, persistent notification and quick-stop action.
 * 2. 100% on-device keyword recognition (EXTRA_PREFER_OFFLINE).
 * 3. Zero raw microphone audio streamed to any external servers or stored in audio files.
 * 4. Immediate SOS dispatch upon codeword detection with duplicate-trigger protection.
 * 5. Uses PARTIAL_WAKE_LOCK to preserve listening during screen-off / screen-locked conditions.
 * 6. Uses the same secure SosRepository and verified-contacts pipeline as the manual SOS button.
 */
public class VoiceRecognitionService extends Service implements CodewordDetector.DetectionListener {

    private static final String TAG = "VoiceRecognitionService";
    public static final String ACTION_START = "com.safepath.ai.action.START_VOICE_SERVICE";
    public static final String ACTION_STOP = "com.safepath.ai.action.STOP_VOICE_SERVICE";
    public static final String ACTION_VOICE_SOS_TRIGGERED = "com.safepath.ai.action.VOICE_SOS_TRIGGERED";

    public static final String CHANNEL_ID = "safepath_voice_codeword_channel";
    public static final int NOTIFICATION_ID = 2002;

    // Cooldown window between consecutive voice-triggered SOS dispatches (45 seconds)
    private static final long SOS_TRIGGER_COOLDOWN_MS = 45_000L;
    private static long lastSosTriggerTimeMs = 0L;

    private CodewordDetector detector;
    private SosRepository sosRepository;
    private VoicePreferenceManager prefManager;
    private FusedLocationProviderClient fusedLocationClient;
    private PowerManager.WakeLock wakeLock;

    public static void start(Context context) {
        Intent intent = new Intent(context, VoiceRecognitionService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, VoiceRecognitionService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefManager = VoicePreferenceManager.getInstance(this);
        sosRepository = new SosRepository(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        detector = new CodewordDetector(this, this);

        createNotificationChannel();
        acquireWakeLock();
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && (wakeLock == null || !wakeLock.isHeld())) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafePath:VoiceMonitoringWakeLock");
                wakeLock.acquire(10 * 60 * 1000L); // 10 minutes rolling lock, re-acquired on loop
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not acquire WakeLock: " + e.getMessage());
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {}
        wakeLock = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopListeningAndSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification("Active: Monitoring for your emergency codeword"));
        acquireWakeLock();

        if (detector != null) {
            detector.startListening();
        }
        prefManager.setMonitoringActive(true);

        return START_STICKY;
    }

    @Override
    public void onCodewordDetected(String matchedText, float confidence) {
        long now = System.currentTimeMillis();
        if (now - lastSosTriggerTimeMs < SOS_TRIGGER_COOLDOWN_MS) {
            Log.w(TAG, "Emergency codeword recognized, but within 45s cooldown window. Duplicate SOS suppressed.");
            return;
        }
        lastSosTriggerTimeMs = now;

        Log.w(TAG, "EMERGENCY: Codeword recognized with confidence=" + confidence + "! Immediately dispatching SOS.");

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            Notification alertNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("EMERGENCY ALERT TRIGGERED")
                    .setContentText("Voice codeword detected. Emergency alerts dispatched to verified contacts.")
                    .setSmallIcon(R.drawable.ic_sos)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setAutoCancel(true)
                    .build();
            nm.notify(NOTIFICATION_ID + 1, alertNotification);
        }

        // Broadcast locally to notify UI activities if running
        Intent triggerIntent = new Intent(ACTION_VOICE_SOS_TRIGGERED);
        triggerIntent.setPackage(getPackageName());
        sendBroadcast(triggerIntent);

        // Fetch location and dispatch via SosRepository
        fetchLocationAndTriggerSos();
    }

    private void fetchLocationAndTriggerSos() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            dispatchSosWithLocation(location, false);
                        } else {
                            fallbackToLastLocation();
                        }
                    })
                    .addOnFailureListener(e -> fallbackToLastLocation());
        } catch (SecurityException se) {
            fallbackToLastLocation();
        }
    }

    private void fallbackToLastLocation() {
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                if (lastLoc != null) {
                    dispatchSosWithLocation(lastLoc, true);
                } else {
                    dispatchSosWithoutLocation();
                }
            }).addOnFailureListener(e -> dispatchSosWithoutLocation());
        } catch (SecurityException se) {
            dispatchSosWithoutLocation();
        }
    }

    private void dispatchSosWithLocation(Location loc, boolean isLastKnown) {
        sosRepository.triggerSos(
                "current_user",
                loc.getLatitude(),
                loc.getLongitude(),
                loc.getAccuracy(),
                isLastKnown,
                "VOICE",
                100,
                "Voice Codeword Emergency Trigger (Hands-Free)",
                new SosRepository.SosCallback<SosResponse>() {
                    @Override
                    public void onSuccess(SosResponse result) {
                        Log.i(TAG, "Voice SOS successfully dispatched to backend. Event ID: " + result.id);
                    }

                    @Override
                    public void onError(String errorMessage, com.safepath.ai.models.SosEvent localEvent) {
                        Log.w(TAG, "Voice SOS queued locally. Offline retry active: " + errorMessage);
                    }
                }
        );
    }

    private void dispatchSosWithoutLocation() {
        sosRepository.triggerSos(
                "current_user",
                null,
                null,
                null,
                false,
                "VOICE",
                100,
                "Voice Codeword Emergency Trigger (Location Unavailable)",
                new SosRepository.SosCallback<SosResponse>() {
                    @Override
                    public void onSuccess(SosResponse result) {
                        Log.i(TAG, "Voice SOS successfully dispatched without location. Event ID: " + result.id);
                    }

                    @Override
                    public void onError(String errorMessage, com.safepath.ai.models.SosEvent localEvent) {
                        Log.w(TAG, "Voice SOS queued locally without location. Offline retry active.");
                    }
                }
        );
    }

    @Override
    public void onAcousticHypothesis(String partialHypothesis, float confidence) {
        // Hypotheses processed on-device; never uploaded or stored
    }

    @Override
    public void onError(int errorCode, String errorMessage) {
        Log.d(TAG, "Detector status: code=" + errorCode + " - " + errorMessage);
    }

    @Override
    public void onListeningStateChanged(boolean isListening) {
        prefManager.setMonitoringActive(isListening);
    }

    private void stopListeningAndSelf() {
        if (detector != null) {
            detector.stopListening();
        }
        prefManager.setMonitoringActive(false);
        releaseWakeLock();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (detector != null) {
            detector.stopListening();
            detector.destroy();
        }
        prefManager.setMonitoringActive(false);
        releaseWakeLock();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Codeword Monitoring",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Provides continuous on-device emergency codeword monitoring for personal safety.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildForegroundNotification(String statusText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent stopIntent = new Intent(this, VoiceRecognitionService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafePath Voice Codeword")
                .setContentText(statusText)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_stop, "Disable Monitoring", stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
