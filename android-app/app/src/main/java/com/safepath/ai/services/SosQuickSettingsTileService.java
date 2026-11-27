package com.safepath.ai.services;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.safepath.ai.R;
import com.safepath.ai.activities.MainActivity;
import com.safepath.ai.controllers.SosCoordinator;

/**
 * SosQuickSettingsTileService — Dedicated Android Quick Settings Tile for Emergency SOS.
 *
 * SPECIFICATION:
 * - Registered with BIND_QUICK_SETTINGS_TILE permission.
 * - Icon and label: "SafePath SOS".
 * - Clicking initiates the unified 5-second SOS countdown on SosCoordinator.
 * - Reflects live active/inactive state of SOS countdown.
 * - Debounces rapid clicks through SosCoordinator.
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class SosQuickSettingsTileService extends TileService {

    private static final String TAG = "SosTileService";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        Log.i(TAG, "[AUDIT] SOS_QUICK_SETTINGS_TILE_CLICKED");

        SosCoordinator coordinator = SosCoordinator.getInstance(getApplicationContext());

        // Debounce: if already counting down or dispatching, do not duplicate
        if (Boolean.TRUE.equals(coordinator.getIsCountingDown().getValue()) ||
                Boolean.TRUE.equals(coordinator.getIsDispatching().getValue())) {
            Log.i(TAG, "[AUDIT] SOS_TILE_CLICK_IGNORED | Countdown already active");
            updateTileState();
            return;
        }

        // Trigger shared SOS countdown
        coordinator.startCountdown("QUICK_SETTINGS", null, "Quick Settings Tile SOS Trigger");

        // Update tile UI to active state
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(getString(R.string.tile_sos_label));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle("5s Countdown Active");
            }
            tile.updateTile();
        }

        // Collapse notification shade and bring app to foreground if possible
        try {
            Intent appIntent = new Intent(this, MainActivity.class);
            appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivityAndCollapse(appIntent);
        } catch (Exception e) {
            Log.w(TAG, "startActivityAndCollapse failed: " + e.getMessage());
        }
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        SosCoordinator coordinator = SosCoordinator.getInstance(getApplicationContext());
        boolean isCounting = Boolean.TRUE.equals(coordinator.getIsCountingDown().getValue());
        boolean isDispatching = Boolean.TRUE.equals(coordinator.getIsDispatching().getValue());

        if (isCounting || isDispatching) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(getString(R.string.tile_sos_label));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle(isCounting ? "Counting Down..." : "Broadcasting...");
            }
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_sos_label));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle("Emergency SOS");
            }
        }

        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_sos));
        tile.updateTile();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }
}
