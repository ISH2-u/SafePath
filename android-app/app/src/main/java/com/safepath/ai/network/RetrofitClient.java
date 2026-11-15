package com.safepath.ai.network;

import com.safepath.ai.BuildConfig;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

/**
 * RetrofitClient — thread-safe singleton Retrofit instance.
 * Base URL reads from BuildConfig (set via build.gradle / local.properties).
 */
public class RetrofitClient {

    private static volatile RetrofitClient instance;
    private final Retrofit retrofit;

    private RetrofitClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(new DevFallbackInterceptor())
                .addInterceptor(logging)
                .addInterceptor(new AuthInterceptor())
                .build();

        retrofit = new Retrofit.Builder()
                .baseUrl(ApiConfig.getBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static RetrofitClient getInstance() {
        if (instance == null) {
            synchronized (RetrofitClient.class) {
                if (instance == null) instance = new RetrofitClient();
            }
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        instance = null;
    }

    public <T> T create(Class<T> serviceClass) {
        return retrofit.create(serviceClass);
    }
}

