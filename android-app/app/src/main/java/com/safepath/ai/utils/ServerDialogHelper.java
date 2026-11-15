package com.safepath.ai.utils;

import android.content.Context;
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

    public static void show(Context context, OnServerChangedListener listener) {
        String[] options = new String[]{
                "USB Debugging (127.0.0.1:8000 via adb reverse)",
                "Android Emulator (10.0.2.2:8000)",
                "Wi-Fi LAN / Custom Computer IP",
                "☁️ Oracle Cloud / Remote Server URL",
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
                        showCloudUrlDialog(context, listener);
                    } else if (which == 4) {
                        testConnection(context, ApiConfig.getBaseUrl());
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private static void showCloudUrlDialog(Context context, OnServerChangedListener listener) {
        final EditText input = new EditText(context);
        input.setHint("e.g. https://safepath.yourdomain.com");
        input.setText(ApiConfig.getBaseUrl());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Enter Oracle Cloud / Server URL")
                .setMessage("Enter your OCI domain or public HTTPS endpoint:")
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
                .setTitle("Enter Laptop IP or Host")
                .setMessage("Enter your computer's local network IP:")
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

    public static void testConnection(Context context, String baseUrl) {
        String testUrl = baseUrl.endsWith("/") ? baseUrl + "health" : baseUrl + "/health";
        Toast.makeText(context, "Testing connection to " + testUrl + "...", Toast.LENGTH_SHORT).show();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url(testUrl).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, "❌ Cannot reach server: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                boolean success = response.isSuccessful();
                int code = response.code();
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(context, "✅ Connected to SafePath Backend! (HTTP " + code + ")", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, "⚠️ Server responded with HTTP " + code, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }
}
