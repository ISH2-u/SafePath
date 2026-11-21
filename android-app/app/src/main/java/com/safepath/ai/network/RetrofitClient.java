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
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            clientBuilder.addInterceptor(new DevFallbackInterceptor());
            clientBuilder.addInterceptor(logging);
        }

        clientBuilder.addInterceptor(new AuthInterceptor());
        OkHttpClient client = clientBuilder.build();

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

