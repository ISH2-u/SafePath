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
 * 1. Uses FOREGROUND_SERVICE_MICROPHONE with clear, persistent notification.
 * 2. 100% on-device keyword recognition (EXTRA_PREFER_OFFLINE).
 * 3. Zero raw microphone audio streamed to any external servers or logs.
 * 4. Immediate SOS dispatch upon codeword detection WITHOUT confirmation dialog or extra button press.
 * 5. Uses the same secure SosRepository and verified-contacts pipeline as the manual SOS button.
 */
public class VoiceRecognitionService extends Service implements CodewordDetector.DetectionListener {

    private static final String TAG = "VoiceRecognitionService";
    public static final String ACTION_START = "com.safepath.ai.action.START_VOICE_SERVICE";
    public static final String ACTION_STOP = "com.safepath.ai.action.STOP_VOICE_SERVICE";
    public static final String ACTION_VOICE_SOS_TRIGGERED = "com.safepath.ai.action.VOICE_SOS_TRIGGERED";

    public static final String CHANNEL_ID = "safepath_voice_codeword_channel";
    public static final int NOTIFICATION_ID = 2002;

    private CodewordDetector detector;
    private SosRepository sosRepository;
    private VoicePreferenceManager prefManager;
    private FusedLocationProviderClient fusedLocationClient;

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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopListeningAndSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification("Active: Listening for your personal codeword"));
        detector.startListening();
        prefManager.setMonitoringActive(true);

        return START_STICKY;
    }

    @Override
    public void onCodewordDetected(String matchedText, float confidence) {
        // CRITICAL: Trigger emergency SOS immediately!
        // No confirmation screen, no prompt to repeat phrase, no extra button press.
        Log.w(TAG, "EMERGENCY: Codeword recognized with confidence=" + confidence + "! Immediately dispatching SOS.");

        // Update notification immediately
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            Notification alertNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("EMERGENCY ALERT TRIGGERED")
                    .setContentText("Emergency phrase recognized! Dispatching alerts to verified contacts.")
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
                    // Even without coordinates, MUST dispatch SOS!
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
                "Voice Codeword Emergency Trigger",
                new SosRepository.SosCallback<SosResponse>() {
                    @Override
                    public void onSuccess(SosResponse result) {
                        Log.i(TAG, "Voice SOS successfully dispatched to backend. Event ID: " + result.id);
                    }

                    @Override
                    public void onError(String errorMessage, com.safepath.ai.models.SosEvent localEvent) {
                        Log.w(TAG, "Voice SOS queued locally. Offline retry active. " + errorMessage);
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
        // Hypotheses processed locally; never log sensitive text
    }

    @Override
    public void onError(int errorCode, String errorMessage) {
        Log.d(TAG, "Detector error code: " + errorCode + " - " + errorMessage);
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
                    "Voice Codeword Listener",
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

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafePath Voice Codeword")
                .setContentText(statusText)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
}

