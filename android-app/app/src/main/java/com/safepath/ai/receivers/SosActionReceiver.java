package com.safepath.ai.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.safepath.ai.controllers.SosCoordinator;
import com.safepath.ai.services.VoiceRecognitionService;

/**
 * SosActionReceiver — Handles notification action buttons (Cancel SOS, Stop Voice Monitoring).
 */
public class SosActionReceiver extends BroadcastReceiver {

    private static final String TAG = "SosActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        Log.i(TAG, "Notification action received: " + action);

        if (SosCoordinator.ACTION_CANCEL_SOS.equals(action)) {
            SosCoordinator.getInstance(context).cancelCountdown();
        } else if (SosCoordinator.ACTION_STOP_MONITORING.equals(action) ||
                VoiceRecognitionService.ACTION_STOP.equals(action)) {
            VoiceRecognitionService.stop(context);
        } else if ("com.safepath.ai.action.TRIGGER_VOICE_SOS".equals(action)) {
            SosCoordinator.getInstance(context).startCountdown("VOICE", null, "Voice Codeword: blue moon");
        }
    }
}
