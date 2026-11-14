package com.safepath.ai.network;

import androidx.annotation.NonNull;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import com.safepath.ai.utils.TokenManager;
import java.io.IOException;

/**
 * AuthInterceptor — attaches the Bearer JWT token to every outgoing request.
 * Token is retrieved from TokenManager (EncryptedSharedPreferences).
 */
public class AuthInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        String token = TokenManager.getInstance().getToken();
        Request request = chain.request();
        if (token != null && !token.isEmpty()) {
            request = request.newBuilder()
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
        }
        return chain.proceed(request);
    }
}

