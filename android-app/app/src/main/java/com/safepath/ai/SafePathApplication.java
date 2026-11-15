package com.safepath.ai;

import android.app.Application;
import android.util.Log;
import com.safepath.ai.database.SafePathDatabase;

/**
 * Application class — single entry point for app-wide initialisation.
 * Initialises Room database eagerly so it is ready before any Activity starts.
 */
public class SafePathApplication extends Application {

    private static final String TAG = "SafePathApp";
    private static SafePathApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Enforce consistent light theme across all devices
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        // Initialize dynamic API configuration
        com.safepath.ai.network.ApiConfig.init(this);
        // Kick off DB initialisation on background thread
        SafePathDatabase.getInstance(this);
        try {
            org.maplibre.android.MapLibre.getInstance(this);
        } catch (Exception ignored) {}
        Log.i(TAG, "SafePath AI Application started.");
    }

    public static SafePathApplication getInstance() {
        return instance;
    }
}

