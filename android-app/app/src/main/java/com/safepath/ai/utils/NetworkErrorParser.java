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
     * Checks if a Throwable represents a network connectivity failure (as opposed to server HTTP response).
     */
    public static boolean isConnectivityError(Throwable t) {
        if (t == null) return false;
        return t instanceof ConnectException
                || t instanceof SocketTimeoutException
                || t instanceof UnknownHostException
                || (t instanceof IOException && !t.getClass().getSimpleName().contains("Http"));
    }

    /**
     * Checks if an error message string originates from a connectivity failure.
     */
    public static boolean isConnectivityErrorMessage(String msg) {
        if (msg == null) return false;
        return msg.contains("Cannot reach SafePath backend")
                || msg.contains("Connection timed out")
                || msg.contains("Could not resolve server")
                || msg.contains("Network connection error")
                || msg.contains("Failed to connect")
                || msg.contains("adb reverse");
    }

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
            boolean isUsb = "127.0.0.1".equals(host) || "localhost".equals(host);
            StringBuilder sb = new StringBuilder();
            sb.append("Cannot reach SafePath backend at http://").append(host).append(":").append(port).append("\n\n");
            sb.append("Troubleshooting:\n");
            if (isUsb) {
                sb.append("• Run in your computer terminal: adb reverse tcp:").append(port).append(" tcp:").append(port).append("\n");
                sb.append("• Ensure FastAPI backend is running: cd backend && .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port ").append(port).append("\n");
                sb.append("• Verify USB debugging is enabled and USB cable is connected.\n");
                sb.append("• Tap 'Retry Connection' below once ready.");
            } else {
                sb.append("• Ensure FastAPI backend is running on your computer.\n");
                sb.append("• Verify phone and computer are connected to the same Wi-Fi.\n");
                sb.append("• Check that your computer's firewall is not blocking port ").append(port).append(".\n");
                sb.append("• Tap 'Retry Connection' or Configure to switch to USB mode.");
            }
            return sb.toString();
        }

        if (t instanceof SocketTimeoutException) {
            return "Connection timed out while trying to reach http://" + host + ":" + port
                    + "\n\nTroubleshooting:\n"
                    + "• The backend server took too long to respond or host is unreachable.\n"
                    + "• If testing via USB, re-run `adb reverse tcp:" + port + " tcp:" + port + "`.\n"
                    + "• If testing via Wi-Fi, verify computer IP and disable hotspot client isolation.\n"
                    + "• Tap 'Retry Connection' below.";
        }

        if (t instanceof UnknownHostException) {
            return "Could not resolve server address (" + host + "). "
                    + "Please check server settings and verify your network connection.";
        }

        String msg = t.getMessage() != null ? t.getMessage() : "";
        if (msg.contains("Cleartext HTTP traffic") || msg.contains("CLEARTEXT")) {
            return "Cleartext HTTP traffic to " + host + " was blocked by device security policy. "
                    + "Ensure you are running a debug build of the application.";
        }

        if (t instanceof IOException) {
            return "Network connection error (" + t.getClass().getSimpleName() + "). "
                    + "Please ensure backend server is reachable and tap 'Retry Connection'.";
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
