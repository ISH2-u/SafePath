package com.safepath.ai.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safepath.ai.network.ApiConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * ServerDialogHelper — Allows physical device and emulator testers to switch between
 * USB reverse debugging (127.0.0.1:8000), Android Emulator (10.0.2.2:8000), and Wi-Fi LAN
 * without needing a recompile.
 */
public final class ServerDialogHelper {

    private ServerDialogHelper() {}

    public interface OnServerChangedListener {
        void onServerChanged(String newBaseUrl);
    }

    public interface HealthCheckCallback {
        void onResult(boolean healthy, int statusCode, String message);
    }

    public static void show(Context context, OnServerChangedListener listener) {
        String[] options = new String[]{
                "USB Debugging (127.0.0.1:8000 via adb reverse)",
                "Android Emulator (10.0.2.2:8000)",
                "Wi-Fi LAN / Custom Computer IP",
                "Custom Server Base URL",
                "⚡ Test Current Server Connection"
        };

        new MaterialAlertDialogBuilder(context)
                .setTitle("SafePath Backend Server")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        ApiConfig.setServer(context, ApiConfig.DEFAULT_USB_HOST, ApiConfig.DEFAULT_PORT);
                        Toast.makeText(context, "Switched to USB mode (127.0.0.1:8000)", Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onServerChanged(ApiConfig.getBaseUrl());
                    } else if (which == 1) {
                        ApiConfig.setServer(context, ApiConfig.DEFAULT_EMULATOR_HOST, ApiConfig.DEFAULT_PORT);
                        Toast.makeText(context, "Switched to Emulator mode (10.0.2.2:8000)", Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onServerChanged(ApiConfig.getBaseUrl());
                    } else if (which == 2) {
                        showCustomHostDialog(context, listener);
                    } else if (which == 3) {
                        showCustomUrlDialog(context, listener);
                    } else if (which == 4) {
                        testConnection(context, ApiConfig.getBaseUrl());
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private static void showCustomUrlDialog(Context context, OnServerChangedListener listener) {
        final EditText input = new EditText(context);
        input.setHint("e.g. http://192.168.1.50:8000/api/v1/");
        input.setText(ApiConfig.getBaseUrl());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Enter Custom Server URL")
                .setMessage("Enter the base API URL for SafePath backend:")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        ApiConfig.setServer(context, url, "");
                        Toast.makeText(context, "Server updated: " + ApiConfig.getBaseUrl(), Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onServerChanged(ApiConfig.getBaseUrl());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showCustomHostDialog(Context context, OnServerChangedListener listener) {
        final EditText input = new EditText(context);
        input.setHint("e.g. 192.168.1.100 or 10.0.0.5");
        input.setText(ApiConfig.getHost());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Enter Computer IP or Host")
                .setMessage("Enter your computer's local Wi-Fi IP address:")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String host = input.getText().toString().trim();
                    if (!host.isEmpty()) {
                        ApiConfig.setServer(context, host, ApiConfig.getPort());
                        Toast.makeText(context, "Server updated: " + ApiConfig.getBaseUrl(), Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onServerChanged(ApiConfig.getBaseUrl());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Checks backend health asynchronously and delivers callback on the main thread.
     */
    public static void checkHealth(String baseUrl, HealthCheckCallback callback) {
        String trimmed = baseUrl != null ? baseUrl.trim() : "";
        String testUrl = trimmed.endsWith("/") ? trimmed + "health" : trimmed + "/health";
        Handler mainHandler = new Handler(Looper.getMainLooper());

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url(testUrl).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String diagnostic = NetworkErrorParser.parseFailure(e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onResult(false, -1, diagnostic);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                int code = response.code();
                boolean success = false;
                String message = "";
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        // Strict validation: Must contain {"status": "healthy"}
                        try {
                            JSONObject json = new JSONObject(body);
                            if ("healthy".equalsIgnoreCase(json.optString("status"))) {
                                success = true;
                                message = "Connected to SafePath Backend (HTTP " + code + ")";
                            } else {
                                message = "Server returned unexpected health response: " + body;
                            }
                        } catch (Exception ex) {
                            message = "Server response was not valid JSON: " + body;
                        }
                    } else {
                        message = "Server responded with HTTP " + code;
                    }
                } catch (Exception e) {
                    message = "Error reading server response: " + e.getMessage();
                }

                final boolean finalSuccess = success;
                final String finalMessage = message;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onResult(finalSuccess, code, finalMessage);
                    }
                });
            }
        });
    }

    public static void testConnection(Context context, String baseUrl) {
        Toast.makeText(context, "Testing connection to " + baseUrl + "...", Toast.LENGTH_SHORT).show();
        checkHealth(baseUrl, (healthy, statusCode, message) -> {
            if (healthy) {
                Toast.makeText(context, "✅ " + message, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "❌ Connection check failed:\n" + message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
