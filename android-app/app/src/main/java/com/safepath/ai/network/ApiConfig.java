package com.safepath.ai.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import com.safepath.ai.BuildConfig;

/**
 * ApiConfig — Centralized development API configuration.
 *
 * Supports:
 * 1. USB Debugging mode (via adb reverse tcp:8000 tcp:8000 -> 127.0.0.1:8000)
 * 2. Android Emulator (10.0.2.2:8000)
 * 3. LAN Wi-Fi / Hotspot mode (via custom computer IP configured in dialog or gradle.properties)
 * 4. Automatic fallback between USB and LAN via DevFallbackInterceptor
 * 5. Production release URLs (https://api.safepath.ai/api/v1/)
 */
public final class ApiConfig {

    private static final String PREF_NAME = "safepath_net_config";
    private static final String KEY_CUSTOM_HOST = "custom_host";
    private static final String KEY_CUSTOM_PORT = "custom_port";

    public static final String DEFAULT_USB_HOST      = "127.0.0.1";
    public static final String DEFAULT_EMULATOR_HOST = "10.0.2.2";
    public static final String DEFAULT_PORT          = "8000";

    private static volatile String runtimeHost = null;
    private static volatile String runtimePort = null;

    private ApiConfig() {
        // Utility class
    }

    public static void init(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedHost = prefs.getString(KEY_CUSTOM_HOST, null);
        String savedPort = prefs.getString(KEY_CUSTOM_PORT, null);
        if (savedHost != null && !savedHost.trim().isEmpty()) {
            runtimeHost = savedHost.trim();
        }
        if (savedPort != null && !savedPort.trim().isEmpty()) {
            runtimePort = savedPort.trim();
        }
    }

    public static String getBaseUrl() {
        String host = getHost();
        String port = getPort();

        // If a full custom URL or protocol scheme was entered at runtime:
        if (host != null && (host.startsWith("http://") || host.startsWith("https://"))) {
            String url = host.trim();
            if (!url.endsWith("/")) url += "/";
            if (!url.contains("/api/v1/")) url += "api/v1/";
            return url;
        }

        if (!BuildConfig.DEBUG) {
            String prodUrl = BuildConfig.API_BASE_URL;
            if (prodUrl != null && !prodUrl.isEmpty()) {
                return prodUrl.endsWith("/") ? prodUrl : prodUrl + "/";
            }
        }

        // Standard HTTP/HTTPS ports without explicit port numbers
        if ("80".equals(port)) {
            return "http://" + host + "/api/v1/";
        }
        if ("443".equals(port)) {
            return "https://" + host + "/api/v1/";
        }

        return "http://" + host + ":" + port + "/api/v1/";
    }

    public static String getHost() {
        if (runtimeHost != null && !runtimeHost.trim().isEmpty()) {
            return runtimeHost;
        }
        if (BuildConfig.SERVER_HOST != null && !BuildConfig.SERVER_HOST.isEmpty()) {
            return BuildConfig.SERVER_HOST;
        }
        return isEmulator() ? DEFAULT_EMULATOR_HOST : DEFAULT_USB_HOST;
    }

    public static String getPort() {
        if (runtimePort != null && !runtimePort.trim().isEmpty()) {
            return runtimePort;
        }
        if (BuildConfig.SERVER_PORT != null && !BuildConfig.SERVER_PORT.isEmpty()) {
            return BuildConfig.SERVER_PORT;
        }
        return DEFAULT_PORT;
    }

    public static String getCustomHost() {
        return runtimeHost;
    }

    public static String getLanHost() {
        return isEmulator() ? DEFAULT_EMULATOR_HOST : DEFAULT_USB_HOST;
    }

    public static boolean isEmulator() {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT));
    }

    public static synchronized void setRuntimeHost(String host) {
        runtimeHost = host;
        RetrofitClient.resetInstance();
    }

    public static synchronized void setServer(Context context, String host, String port) {
        runtimeHost = host;
        runtimePort = port;
        RetrofitClient.resetInstance();

        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_CUSTOM_HOST, host)
                    .putString(KEY_CUSTOM_PORT, port)
                    .apply();
        }
    }

    public static boolean isConfigured() {
        String host = getHost();
        return host != null && !host.isEmpty() && !host.contains("YOUR_LAPTOP_IP");
    }
}
