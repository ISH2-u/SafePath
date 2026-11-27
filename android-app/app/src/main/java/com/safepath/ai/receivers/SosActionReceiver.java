package com.safepath.ai.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.safepath.ai.controllers.SosCoordinator;

/**
 * SosActionReceiver — Handles notification action buttons (Cancel SOS) and testing broadcasts.
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
        } else if ("com.safepath.ai.action.TRIGGER_HARDWARE_SOS".equals(action)) {
            SosCoordinator.getInstance(context).startCountdown("VOLUME_KEY", null, "Hardware SOS Broadcast Test");
        } else if ("com.safepath.ai.action.TRIGGER_TILE_SOS".equals(action)) {
            SosCoordinator.getInstance(context).startCountdown("QUICK_SETTINGS", null, "Quick Settings Tile Broadcast Test");
        }
    }
}
