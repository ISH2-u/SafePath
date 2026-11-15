package com.safepath.ai.utils;

import android.util.Log;
import com.safepath.ai.BuildConfig;
import com.safepath.ai.network.ApiConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import retrofit2.Response;

/**
 * NetworkErrorParser — Transforms low-level network exceptions and HTTP status codes
 * into clear, user-understandable diagnostic messages.
 *
 * Enforces:
 * 1. Zero raw exception stack traces presented to end users.
 * 2. Diagnostic logging active ONLY in debug builds (no passwords, tokens, or PII logged).
 */
public final class NetworkErrorParser {

    private static final String TAG = "SafePathNetwork";

    private NetworkErrorParser() {}

    /**
     * Parses Throwable failures from Retrofit enqueue callbacks.
     */
    public static String parseFailure(Throwable t) {
        String host = ApiConfig.getHost();
        String port = ApiConfig.getPort();

        if (BuildConfig.DEBUG) {
            Log.e(TAG, "Network failure [target " + host + ":" + port + "]: "
                    + (t != null ? t.getClass().getSimpleName() + " - " + t.getMessage() : "null"));
        }

        if (t == null) {
            return "An unknown network error occurred. Please check your connection.";
        }

        if (t instanceof ConnectException) {
            return "Cannot reach SafePath backend at http://" + host + ":" + port
                    + ".\n\nTroubleshooting:\n"
                    + "• For USB testing: run `adb reverse tcp:8000 tcp:8000`.\n"
                    + "• Ensure FastAPI backend is running on your computer (`python run.py`).\n"
                    + "• For Wi-Fi testing: verify phone and computer are on the same network.\n"
                    + "• Tap the server badge above to test or switch connection mode.";
        }

        if (t instanceof SocketTimeoutException) {
            return "Connection timed out while trying to reach http://" + host + ":" + port
                    + ".\n\nTroubleshooting:\n"
                    + "• Your laptop's IPv4 address may have changed. Run `ipconfig` and update gradle.properties.\n"
                    + "• Windows Firewall may be silently dropping port " + port + " traffic.\n"
                    + "• Verify phone hotspot does not have client isolation enabled.";
        }

        if (t instanceof UnknownHostException) {
            return "Could not resolve server address (" + host + "). "
                    + "Please check LAPTOP_IPV4_ADDRESS in gradle.properties and check phone network connection.";
        }

        String msg = t.getMessage() != null ? t.getMessage() : "";
        if (msg.contains("Cleartext HTTP traffic") || msg.contains("CLEARTEXT")) {
            return "Cleartext HTTP traffic to " + host + " was blocked by device security policy. "
                    + "Ensure you are running a debug build of the application.";
        }

        if (t instanceof IOException) {
            return "Network connection error (" + t.getClass().getSimpleName() + "). "
                    + "Please ensure your phone is connected to your hotspot and try again.";
        }

        return "Network communication error. Please verify server connectivity and try again.";
    }

    /**
     * Parses HTTP error responses (4xx, 5xx).
     */
    public static String parseHttpError(Response<?> response, String defaultContext) {
        if (response == null) {
            return "Server returned an empty or invalid response.";
        }

        int code = response.code();

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "HTTP Error " + code + " for request: " + response.raw().request().url());
        }

        // Try to parse detailed message from JSON response body
        String extractedDetail = extractDetailFromErrorBody(response);

        if (code == 400) {
            if (extractedDetail != null && !extractedDetail.isEmpty()) {
                return extractedDetail;
            }
            return (defaultContext != null ? defaultContext : "Validation failed")
                    + ": Invalid input data. Please check all fields.";
        }

        if (code == 401) {
            if (extractedDetail != null && !extractedDetail.isEmpty()) {
                return extractedDetail;
            }
            return "Authentication failed: Incorrect email or password.";
        }

        if (code == 403) {
            return "Access denied: Account may be deactivated or unauthorized.";
        }

        if (code == 404) {
            return "API endpoint not found (HTTP 404). Ensure the backend is running the latest SafePath AI code.";
        }

        if (code == 409) {
            return extractedDetail != null ? extractedDetail : "An account with this email address already exists.";
        }

        if (code == 422) {
            return extractedDetail != null ? extractedDetail : "Invalid form data format. Please verify your information.";
        }

        if (code >= 500) {
            return "Backend server error (HTTP " + code + "). "
                    + "The backend encountered an internal error. Please check your laptop's backend terminal logs.";
        }

        return "Unexpected server response (HTTP " + code + "). Please try again.";
    }

    private static String extractDetailFromErrorBody(Response<?> response) {
        try {
            if (response.errorBody() == null) return null;
            String body = response.errorBody().string();
            if (body.isEmpty()) return null;

            JSONObject json = new JSONObject(body);
            if (json.has("detail")) {
                Object detailObj = json.get("detail");
                if (detailObj instanceof String) {
                    return (String) detailObj;
                } else if (detailObj instanceof JSONArray) {
                    JSONArray arr = (JSONArray) detailObj;
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        String msg = item.optString("msg", "");
                        JSONArray loc = item.optJSONArray("loc");
                        String field = (loc != null && loc.length() > 0)
                                ? loc.optString(loc.length() - 1, "") : "";
                        if (sb.length() > 0) sb.append("\n");
                        if (!field.isEmpty() && !field.equals("body")) {
                            sb.append(field).append(": ");
                        }
                        sb.append(msg);
                    }
                    return sb.toString();
                }
            }
            if (json.has("message")) {
                return json.optString("message");
            }
        } catch (Exception ignored) {}
        return null;
    }
}

