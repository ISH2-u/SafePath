package com.safepath.ai.controllers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.safepath.ai.R;
import com.safepath.ai.activities.MainActivity;
import com.safepath.ai.models.SosEvent;
import com.safepath.ai.network.dto.SosResponse;
import com.safepath.ai.repositories.SosRepository;
import com.safepath.ai.utils.VoicePreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * SosCoordinator — Unified Singleton SOS Controller.
 *
 * CRITICAL ARCHITECTURE RULE:
 * All SOS activation methods:
 * 1. Manual in-app SOS button (SosFragment)
 * 2. Hardware Volume Down triple press (SosAccessibilityService / MainActivity)
 * 3. Quick Settings SOS Tile (SosQuickSettingsTileService)
 * use this exact SAME coordinator instance.
 *
 * Responsibilities:
 * 1. Single shared countdown timer (5 -> 4 -> 3 -> 2 -> 1 -> dispatch).
 * 2. In-place Android Notification updates across all states (Countdown -> Dispatching -> Result).
 * 3. Prevention of duplicate/overlapping SOS dispatches with 45s debounce cooldown.
 * 4. High-accuracy GPS location acquisition with graceful last-known fallback.
 * 5. Full execution through SosRepository -> Room local store -> FastAPI backend.
 * 6. Structured audit logging across every state.
 */
public class SosCoordinator {

    private static final String TAG = "SosCoordinator";

    public static final String ACTION_CANCEL_SOS = "com.safepath.ai.action.CANCEL_SOS";
    public static final String CHANNEL_ID = "safepath_emergency_sos_channel";
    public static final int NOTIFICATION_ID = 2002;

    // 45-second debounce window between completed SOS events
    private static final long SOS_DEBOUNCE_COOLDOWN_MS = 45_000L;
    private static long lastCompletedDispatchTimeMs = 0L;

    private static volatile SosCoordinator sInstance;

    private final Context appContext;
    private final SosRepository sosRepository;
    private final VoicePreferenceManager prefManager;
    private final FusedLocationProviderClient fusedLocationClient;
    private final NotificationManager notificationManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Observable State (Shared with SosViewModel & UI) ───────────────────────
    private final MutableLiveData<Boolean> isCountingDown   = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> secondsRemaining = new MutableLiveData<>(5);
    private final MutableLiveData<Boolean> isDispatching    = new MutableLiveData<>(false);
    private final MutableLiveData<SosResponse> sosResponse   = new MutableLiveData<>(null);
    private final MutableLiveData<SosEvent> localEvent       = new MutableLiveData<>(null);
    private final MutableLiveData<String> statusMessage     = new MutableLiveData<>(null);
    private final MutableLiveData<String> errorMessage       = new MutableLiveData<>(null);

    private CountDownTimer countDownTimer;
    private String currentCorrelationId;
    private String activeTriggerSource = "MANUAL";
    private String activeNotes = "";
    private Location activeLocation;

    private SosCoordinator(Context context) {
        this.appContext = context.getApplicationContext();
        this.sosRepository = new SosRepository(this.appContext);
        this.prefManager = VoicePreferenceManager.getInstance(this.appContext);
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.appContext);
        this.notificationManager = (NotificationManager) this.appContext.getSystemService(Context.NOTIFICATION_SERVICE);

        ensureNotificationChannel();
    }

    public static synchronized SosCoordinator getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SosCoordinator(context);
        }
        return sInstance;
    }

    // ── Public Observable State Getters ────────────────────────────────────────
    public LiveData<Boolean> getIsCountingDown()   { return isCountingDown; }
    public LiveData<Integer> getSecondsRemaining() { return secondsRemaining; }
    public LiveData<Boolean> getIsDispatching()    { return isDispatching; }
    public LiveData<SosResponse> getSosResponse()   { return sosResponse; }
    public LiveData<SosEvent> getLocalEvent()       { return localEvent; }
    public LiveData<String> getStatusMessage()      { return statusMessage; }
    public LiveData<String> getErrorMessage()        { return errorMessage; }

    // ── Channel Management ───────────────────────────────────────────────────
    public void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Emergency SOS Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Shows real-time emergency SOS countdowns, cancellation controls, and delivery confirmations.");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(true);
            channel.enableVibration(true);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    // ── Start Shared SOS Countdown ─────────────────────────────────────────────
    public synchronized boolean startCountdown(@NonNull String triggerSource,
                                               @Nullable Location location,
                                               @Nullable String notes) {
        long now = System.currentTimeMillis();

        // 1. Debounce check
        if (now - lastCompletedDispatchTimeMs < SOS_DEBOUNCE_COOLDOWN_MS) {
            logAudit("SOS_DUPLICATE_SUPPRESSED", triggerSource, "Trigger ignored: within 45s cooldown window.");
            return false;
        }

        // 2. Concurrency check
        if (Boolean.TRUE.equals(isCountingDown.getValue()) || Boolean.TRUE.equals(isDispatching.getValue())) {
            logAudit("SOS_ALREADY_ACTIVE", triggerSource, "Trigger ignored: countdown or dispatch already in progress.");
            return false;
        }

        this.currentCorrelationId = UUID.randomUUID().toString();
        this.activeTriggerSource = triggerSource;
        this.activeNotes = (notes != null) ? notes : ("Trigger: " + triggerSource);
        this.activeLocation = location;

        logAudit("SOS_COUNTDOWN_STARTED", triggerSource, "CorrelationId=" + currentCorrelationId + " | Duration=5s");

        // Update UI state
        mainHandler.post(() -> {
            isCountingDown.setValue(true);
            secondsRemaining.setValue(5);
            isDispatching.setValue(false);
            errorMessage.setValue(null);
            statusMessage.setValue("ALERT DISPATCHING IN 5s (Tap Cancel to Abort)");
        });

        // Update notification immediately to 5 seconds
        updateCountdownNotification(5);

        // Start 5-second ticking timer (5000ms total, 1000ms tick)
        countDownTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) (millisUntilFinished / 1000) + 1;
                logAudit("SOS_COUNTDOWN_TICK", activeTriggerSource, "Remaining=" + sec + "s | CorrelationId=" + currentCorrelationId);

                mainHandler.post(() -> {
                    secondsRemaining.setValue(sec);
                    statusMessage.setValue(String.format(Locale.getDefault(), "ALERT DISPATCHING IN %ds (Tap Cancel to Abort)", sec));
                });

                updateCountdownNotification(sec);
            }

            @Override
            public void onFinish() {
                logAudit("SOS_COUNTDOWN_COMPLETED", activeTriggerSource, "Countdown reached 0. Initiating dispatch.");

                mainHandler.post(() -> {
                    isCountingDown.setValue(false);
                    isDispatching.setValue(true);
                    statusMessage.setValue("Broadcasting emergency alert to verified contacts...");
                });

                updateDispatchingNotification();
                executeDispatchPipeline();
            }
        }.start();

        return true;
    }

    // ── Cancel Countdown ──────────────────────────────────────────────────────
    public synchronized void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        boolean wasCounting = Boolean.TRUE.equals(isCountingDown.getValue());
        logAudit("SOS_COUNTDOWN_CANCELLED", activeTriggerSource, "Cancelled by user. CorrelationId=" + currentCorrelationId);

        mainHandler.post(() -> {
            isCountingDown.setValue(false);
            isDispatching.setValue(false);
            statusMessage.setValue("Emergency SOS dispatch cancelled.");
        });

        // Dismiss the countdown notification
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    // ── Dispatch Execution Pipeline ───────────────────────────────────────────
    private void executeDispatchPipeline() {
        if (activeLocation != null) {
            performBackendDispatch(activeLocation, false);
        } else {
            // Attempt to acquire fresh high-accuracy GPS fix before dispatching
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener(loc -> {
                            if (loc != null) {
                                performBackendDispatch(loc, false);
                            } else {
                                fallbackToLastKnownLocation();
                            }
                        })
                        .addOnFailureListener(e -> fallbackToLastKnownLocation());
            } catch (SecurityException se) {
                fallbackToLastKnownLocation();
            }
        }
    }

    private void fallbackToLastKnownLocation() {
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(lastLoc -> {
                        if (lastLoc != null) {
                            performBackendDispatch(lastLoc, true);
                        } else {
                            performBackendDispatch(null, false);
                        }
                    })
                    .addOnFailureListener(e -> performBackendDispatch(null, false));
        } catch (SecurityException se) {
            performBackendDispatch(null, false);
        }
    }

    private void performBackendDispatch(@Nullable Location location, boolean isLastKnown) {
        logAudit("SOS_DISPATCH_REQUEST_PREPARED", activeTriggerSource,
                "Lat=" + (location != null ? location.getLatitude() : "null") +
                " | Lng=" + (location != null ? location.getLongitude() : "null") +
                " | IsLastKnown=" + isLastKnown +
                " | CorrelationId=" + currentCorrelationId);

        Double lat = location != null ? location.getLatitude() : null;
        Double lng = location != null ? location.getLongitude() : null;
        Float acc = location != null ? location.getAccuracy() : null;

        sosRepository.triggerSos(
                "current_user",
                lat, lng, acc, isLastKnown,
                activeTriggerSource,
                100,
                activeNotes,
                new SosRepository.SosCallback<SosResponse>() {
                    @Override
                    public void onSuccess(SosResponse result) {
                        lastCompletedDispatchTimeMs = System.currentTimeMillis();
                        logAudit("SOS_DISPATCH_COMPLETED", activeTriggerSource,
                                "EventId=" + result.id +
                                " | Status=" + result.status +
                                " | DeliveredCount=" + result.deliveredCount +
                                " | RecipientsCount=" + result.recipientsCount);

                        mainHandler.post(() -> {
                            isDispatching.setValue(false);
                            sosResponse.setValue(result);
                            statusMessage.setValue(String.format(Locale.getDefault(),
                                    "EMERGENCY ALERT DELIVERED TO %d VERIFIED CONTACTS.", result.recipientsCount));
                        });

                        showDispatchedNotification(result);
                    }

                    @Override
                    public void onError(String errorMessageStr, SosEvent localEventObj) {
                        logAudit("SOS_DISPATCH_FAILED", activeTriggerSource,
                                "Error=" + errorMessageStr + " | CorrelationId=" + currentCorrelationId);

                        mainHandler.post(() -> {
                            isDispatching.setValue(false);
                            localEvent.setValue(localEventObj);
                            errorMessage.setValue(errorMessageStr);
                            statusMessage.setValue("SOS dispatch failed: " + errorMessageStr);
                        });

                        showFailedNotification(errorMessageStr);
                    }
                }
        );
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private void updateCountdownNotification(int secondsLeft) {
        Intent cancelIntent = new Intent(ACTION_CANCEL_SOS);
        cancelIntent.setPackage(appContext.getPackageName());
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(
                appContext, 2, cancelIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent openAppIntent = new Intent(appContext, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                appContext, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        String text = String.format(Locale.getDefault(),
                "Emergency SOS sending in %d %s",
                secondsLeft, secondsLeft == 1 ? "second" : "seconds");

        String title = "SafePath — Emergency SOS";
        if ("VOLUME_KEY".equals(activeTriggerSource)) {
            title = "SafePath — Hardware SOS Triggered";
        } else if ("QUICK_SETTINGS".equals(activeTriggerSource)) {
            title = "SafePath — Quick Settings SOS Triggered";
        }

        Notification notification = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentIntent(contentPendingIntent)
                .addAction(R.drawable.ic_close, "Cancel SOS", cancelPendingIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .build();

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void updateDispatchingNotification() {
        Intent openAppIntent = new Intent(appContext, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                appContext, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle("SafePath — Emergency SOS")
                .setContentText("Broadcasting emergency alert to verified contacts...")
                .setSmallIcon(R.drawable.ic_sos)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void showDispatchedNotification(SosResponse result) {
        Intent openAppIntent = new Intent(appContext, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                appContext, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Check if simulated DEV_MOCK mode
        boolean isMock = true; // default safe in dev environment
        if (result != null && result.recipients != null && !result.recipients.isEmpty()) {
            isMock = false;
            for (SosResponse.RecipientStatusDto r : result.recipients) {
                if (r.channel != null && (r.channel.contains("MOCK") || r.status.contains("MOCK"))) {
                    isMock = true;
                    break;
                }
            }
        }

        String title;
        String text;

        if (isMock) {
            title = "TEST SOS — Dispatch simulated";
            text = "Your emergency alert has been accepted for dispatch (Simulated).";
        } else {
            title = "Emergency Alert Dispatched";
            text = "Your emergency alert has been accepted for dispatch.";
        }

        Notification notification = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void showFailedNotification(String error) {
        Intent openAppIntent = new Intent(appContext, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                appContext, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle("SafePath — Emergency SOS")
                .setContentText("SOS dispatch failed — Tap to retry or open SafePath.")
                .setSmallIcon(R.drawable.ic_sos)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void logAudit(String eventType, String triggerSource, String details) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        Log.i("SafePathAudit", String.format("[AUDIT] %s | Time: %s | Source: %s | %s",
                eventType, timestamp, triggerSource, details));
    }
}
