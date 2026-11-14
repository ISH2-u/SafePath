package com.safepath.ai.network;

import com.safepath.ai.BuildConfig;

/**
 * ApiConfig — Centralized development API configuration.
 *
 * Configured in gradle.properties:
 *   LAPTOP_IPV4_ADDRESS=10.43.20.214
 *   SERVER_PORT=8000
 *
 * For physical Android phone on mobile hotspot / Wi-Fi:
 *   Connects to: http://LAPTOP_IPV4_ADDRESS:8000/api/v1/
 *
 * For USB Debugging fallback (via adb reverse tcp:8000 tcp:8000):
 *   Connects to: http://127.0.0.1:8000/api/v1/
 *
 * In production releases:
 *   Connects to: https://api.safepath.ai/api/v1/
 */
public final class ApiConfig {

    private ApiConfig() {
        // Utility class
    }

    /**
     * Returns the centralized API Base URL.
     * Guaranteed to include the trailing slash required by Retrofit.
     */
    public static String getBaseUrl() {
        String url = BuildConfig.API_BASE_URL;
        if (url == null || url.trim().isEmpty()) {
            return "http://" + getHost() + ":" + getPort() + "/api/v1/";
        }
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }

    public static String getHost() {
        return BuildConfig.SERVER_HOST != null ? BuildConfig.SERVER_HOST : "10.43.20.214";
    }

    public static String getPort() {
        return BuildConfig.SERVER_PORT != null ? BuildConfig.SERVER_PORT : "8000";
    }

    public static boolean isConfigured() {
        String host = getHost();
        return host != null && !host.isEmpty() && !host.contains("YOUR_LAPTOP_IP");
    }
}

