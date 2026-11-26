package com.safepath.ai.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.safepath.ai.ai.CodewordDetector;
import com.safepath.ai.controllers.SosCoordinator;
import com.safepath.ai.utils.VoicePreferenceManager;

/**
 * VoiceRecognitionService — Foreground Service for on-device voice codeword listening.
 *
 * CRITICAL REQUIREMENTS ENFORCED:
 * 1. Uses FOREGROUND_SERVICE_MICROPHONE with persistent notification showing configured codeword.
 * 2. 100% on-device keyword recognition.
 * 3. Exact codeword "blue moon" detection initiates 5-second countdown on the unified SosCoordinator.
 * 4. Notification updates in place every second during countdown.
 * 5. Uses PARTIAL_WAKE_LOCK to preserve listening during screen-off / background.
 * 6. Quick "Stop Monitoring" and "Cancel SOS" actions directly in the notification.
 */
public class VoiceRecognitionService extends Service implements CodewordDetector.DetectionListener {

    private static final String TAG = "VoiceRecognitionService";
    public static final String ACTION_START = "com.safepath.ai.action.START_VOICE_SERVICE";
    public static final String ACTION_STOP = "com.safepath.ai.action.STOP_VOICE_SERVICE";

    public static final String CHANNEL_ID = "safepath_voice_codeword_channel";
    public static final int NOTIFICATION_ID = 2002;

    private CodewordDetector detector;
    private VoicePreferenceManager prefManager;
    private SosCoordinator sosCoordinator;
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
        sosCoordinator = SosCoordinator.getInstance(this);
        detector = new CodewordDetector(this, this);

        sosCoordinator.ensureNotificationChannel();
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

        Notification notification = sosCoordinator.buildMonitoringNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        acquireWakeLock();

        if (detector != null) {
            detector.startListening();
        }
        prefManager.setMonitoringActive(true);

        return START_STICKY;
    }

    @Override
    public void onCodewordDetected(String matchedText, float confidence) {
        Log.i(TAG, "Codeword recognized: '" + matchedText + "' with confidence=" + confidence);
        Log.i("SafePathAudit", "[AUDIT] VOICE_CODEWORD_DETECTED | Phrase: " + matchedText + " | Confidence: " + confidence);

        // Initiate unified countdown on SosCoordinator (Source: VOICE)
        sosCoordinator.startCountdown("VOICE", null, "Voice Codeword Triggered (" + matchedText + ")");
    }

    @Override
    public void onAcousticHypothesis(String partialHypothesis, float confidence) {
        // Processed entirely on-device; never uploaded or stored
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
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
}
