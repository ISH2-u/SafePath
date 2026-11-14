package com.safepath.ai.repositories;

import android.content.Context;
import com.safepath.ai.database.SafePathDatabase;
import com.safepath.ai.database.dao.UserDao;
import com.safepath.ai.models.User;
import com.safepath.ai.network.AuthApiService;
import com.safepath.ai.network.RetrofitClient;
import com.safepath.ai.network.dto.AuthResponse;
import com.safepath.ai.network.dto.LoginRequest;
import com.safepath.ai.network.dto.RegisterRequest;
import com.safepath.ai.network.dto.UserDeleteRequest;
import com.safepath.ai.network.dto.UserDto;
import com.safepath.ai.network.dto.UserUpdateRequest;
import com.safepath.ai.utils.NetworkErrorParser;
import com.safepath.ai.utils.TokenManager;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {

    private final AuthApiService apiService;
    private final UserDao userDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface AuthCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public AuthRepository(Context context) {
        this.apiService = RetrofitClient.getInstance().create(AuthApiService.class);
        this.userDao = SafePathDatabase.getInstance(context).userDao();
    }

    public void register(String email, String password, String name, String phone, AuthCallback<UserDto> callback) {
        RegisterRequest request = new RegisterRequest(email, password, name, phone);
        apiService.register(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse auth = response.body();
                    TokenManager.getInstance().saveToken(auth.accessToken);
                    saveUserLocally(auth.user);
                    callback.onSuccess(auth.user);
                } else {
                    callback.onError(NetworkErrorParser.parseHttpError(response, "Registration failed"));
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                callback.onError(NetworkErrorParser.parseFailure(t));
            }
        });
    }

    public void login(String email, String password, AuthCallback<UserDto> callback) {
        LoginRequest request = new LoginRequest(email, password);
        apiService.login(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse auth = response.body();
                    TokenManager.getInstance().saveToken(auth.accessToken);
                    saveUserLocally(auth.user);
                    callback.onSuccess(auth.user);
                } else {
                    callback.onError(NetworkErrorParser.parseHttpError(response, "Login failed"));
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                callback.onError(NetworkErrorParser.parseFailure(t));
            }
        });
    }

    public void getProfile(AuthCallback<UserDto> callback) {
        apiService.getProfile().enqueue(new Callback<UserDto>() {
            @Override
            public void onResponse(Call<UserDto> call, Response<UserDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveUserLocally(response.body());
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(NetworkErrorParser.parseHttpError(response, "Failed to load profile"));
                }
            }

            @Override
            public void onFailure(Call<UserDto> call, Throwable t) {
                callback.onError(NetworkErrorParser.parseFailure(t));
            }
        });
    }

    public void updateProfile(UserUpdateRequest request, AuthCallback<UserDto> callback) {
        apiService.updateProfile(request).enqueue(new Callback<UserDto>() {
            @Override
            public void onResponse(Call<UserDto> call, Response<UserDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveUserLocally(response.body());
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(NetworkErrorParser.parseHttpError(response, "Failed to update profile"));
                }
            }

            @Override
            public void onFailure(Call<UserDto> call, Throwable t) {
                callback.onError(NetworkErrorParser.parseFailure(t));
            }
        });
    }

    public void deleteAccount(String password, AuthCallback<String> callback) {
        UserDeleteRequest request = new UserDeleteRequest(true, password);
        apiService.deleteAccount(request).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (response.isSuccessful()) {
                    logout();
                    callback.onSuccess("Account permanently deleted.");
                } else {
                    callback.onError(NetworkErrorParser.parseHttpError(response, "Account deletion failed"));
                }
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                callback.onError(NetworkErrorParser.parseFailure(t));
            }
        });
    }

    public void logout() {
        TokenManager.getInstance().clearToken();
        executor.execute(userDao::deleteAll);
    }

    public boolean isLoggedIn() {
        String token = TokenManager.getInstance().getToken();
        return token != null && !token.isEmpty();
    }

    private void saveUserLocally(UserDto dto) {
        if (dto == null) return;
        executor.execute(() -> {
            User user = new User();
            user.id = dto.id;
            user.email = dto.email;
            user.displayName = dto.displayName;
            user.phone = dto.phone;
            user.bloodGroup = dto.bloodGroup;
            user.medicalNotes = dto.medicalNotes;
            user.createdAt = System.currentTimeMillis();
            userDao.insert(user);
        });
    }

    private String parseError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception ignored) {}
        return "HTTP " + response.code();
    }
}

