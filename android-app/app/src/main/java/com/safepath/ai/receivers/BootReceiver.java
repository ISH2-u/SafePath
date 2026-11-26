package com.safepath.ai.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.safepath.ai.services.VoiceRecognitionService;
import com.safepath.ai.utils.VoicePreferenceManager;

/**
 * BootReceiver — Recovers voice codeword background monitoring service on device reboot
 * ONLY if the user has explicitly enabled monitoring and configured a codeword.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "SafePathBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        VoicePreferenceManager prefManager = VoicePreferenceManager.getInstance(context);
        if (prefManager.isMonitoringEnabled() && prefManager.hasCodewordConfigured()) {
            Log.i(TAG, "Device rebooted. Resuming user-authorized background voice codeword monitoring service.");
            try {
                VoiceRecognitionService.start(context);
            } catch (Exception e) {
                Log.w(TAG, "Could not start voice service on boot: " + e.getMessage());
            }
        }
    }
}
