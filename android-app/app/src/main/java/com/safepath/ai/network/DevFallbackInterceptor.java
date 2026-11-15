package com.safepath.ai.network;

import android.util.Log;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

/**
 * DevFallbackInterceptor — Automatic fallback between USB (127.0.0.1) and LAN (Wi-Fi) development servers.
 *
 * If connecting via 127.0.0.1 fails (e.g. phone disconnected from USB), automatically retries using the laptop's LAN IP.
 * If connecting via LAN IP fails (e.g. router isolation or IP change), automatically retries using 127.0.0.1 (ADB reverse).
 */
public class DevFallbackInterceptor implements Interceptor {

    private static final String TAG = "DevFallback";

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        try {
            return chain.proceed(request);
        } catch (IOException originalException) {
            HttpUrl url = request.url();
            String currentHost = url.host();
            String fallbackHost = null;

            if ("127.0.0.1".equals(currentHost) || "localhost".equals(currentHost)) {
                fallbackHost = ApiConfig.getLanHost();
            } else if (currentHost.startsWith("10.") || currentHost.startsWith("192.168.") || currentHost.startsWith("172.")) {
                fallbackHost = ApiConfig.DEFAULT_USB_HOST;
            }

            if (fallbackHost != null && !fallbackHost.equals(currentHost)) {
                Log.w(TAG, "Request to " + currentHost + " failed (" + originalException.getClass().getSimpleName()
                        + "). Automatically retrying on fallback: " + fallbackHost);

                HttpUrl fallbackUrl = url.newBuilder().host(fallbackHost).build();
                Request fallbackRequest = request.newBuilder().url(fallbackUrl).build();

                try {
                    Response fallbackResponse = chain.proceed(fallbackRequest);
                    // Successfully connected via fallback! Update active host
                    ApiConfig.setRuntimeHost(fallbackHost);
                    Log.i(TAG, "Fallback connection succeeded on " + fallbackHost + "!");
                    return fallbackResponse;
                } catch (IOException fallbackError) {
                    Log.e(TAG, "Fallback to " + fallbackHost + " also failed: " + fallbackError.getMessage());
                }
            }

            throw originalException;
        }
    }
}
