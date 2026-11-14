package com.safepath.ai.network;

import com.safepath.ai.network.dto.AuthResponse;
import com.safepath.ai.network.dto.LoginRequest;
import com.safepath.ai.network.dto.RegisterRequest;
import com.safepath.ai.network.dto.UserDeleteRequest;
import com.safepath.ai.network.dto.UserDto;
import com.safepath.ai.network.dto.UserUpdateRequest;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.POST;
import retrofit2.http.PUT;

public interface AuthApiService {

    @POST("auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("auth/logout")
    Call<Map<String, String>> logout();

    @GET("auth/me")
    Call<UserDto> getProfile();

    @PUT("auth/me")
    Call<UserDto> updateProfile(@Body UserUpdateRequest request);

    @HTTP(method = "DELETE", path = "auth/me", hasBody = true)
    Call<Map<String, String>> deleteAccount(@Body UserDeleteRequest request);
}

