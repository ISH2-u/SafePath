package com.safepath.ai.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.safepath.ai.activities.MainActivity;
import com.safepath.ai.controllers.SosCoordinator;
import com.safepath.ai.controllers.VolumeKeyDetector;

/**
 * SosAccessibilityService — Intercepts physical Volume Down hardware key events globally
 * using Android's AccessibilityService framework with key-event filtering.
 *
 * SPECIFICATION & SECURITY:
 * - Only filters key events (FLAG_REQUEST_FILTER_KEY_EVENTS).
 * - Does not read screen contents, text, or user input (eventTypes = 0).
 * - Completely disclosed in SafePath settings with direct shortcut to system settings.
 * - Detects 3 rapid Volume Down presses (within 2s) to activate Emergency SOS.
 */
public class SosAccessibilityService extends AccessibilityService {

    private static final String TAG = "SosAccessibility";
    private VolumeKeyDetector volumeKeyDetector;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        volumeKeyDetector = new VolumeKeyDetector(this::onHardwareSosDetected);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = 0; // We do NOT monitor UI accessibility events
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        Log.i(TAG, "[AUDIT] SOS_ACCESSIBILITY_SERVICE_CONNECTED | Hardware Volume Key filtering active.");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (volumeKeyDetector != null && volumeKeyDetector.onKeyEvent(event)) {
            return true; // Consume 3rd press
        }
        return super.onKeyEvent(event);
    }

    private void onHardwareSosDetected() {
        Log.i(TAG, "[AUDIT] HARDWARE_VOLUME_SOS_TRIGGERED | Triple press detected in AccessibilityService.");

        mainHandler.post(() -> {
            // 1. Initiate shared SOS countdown on SosCoordinator
            SosCoordinator coordinator = SosCoordinator.getInstance(getApplicationContext());
            coordinator.startCountdown("VOLUME_KEY", null, "Hardware SOS: Volume Down x3");

            // 2. Bring SafePath app to foreground if permitted
            try {
                Intent appIntent = new Intent(this, MainActivity.class);
                appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(appIntent);
            } catch (Exception e) {
                Log.w(TAG, "Could not bring SafePath to foreground: " + e.getMessage());
            }
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally no-op: privacy guaranteed, zero screen reading
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "[AUDIT] SOS_ACCESSIBILITY_SERVICE_INTERRUPTED");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (volumeKeyDetector != null) {
            volumeKeyDetector.reset();
        }
        Log.i(TAG, "[AUDIT] SOS_ACCESSIBILITY_SERVICE_DESTROYED");
    }

    // ── Static Helpers ─────────────────────────────────────────────────────────

    public static boolean isAccessibilityServiceEnabled(Context context) {
        if (context == null) return false;
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) return false;

        String expectedServiceName = context.getPackageName() + "/" + SosAccessibilityService.class.getName();
        return enabledServices.contains(expectedServiceName) ||
                enabledServices.contains(SosAccessibilityService.class.getSimpleName());
    }

    public static void openAccessibilitySettings(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open accessibility settings: " + e.getMessage());
        }
    }
}
