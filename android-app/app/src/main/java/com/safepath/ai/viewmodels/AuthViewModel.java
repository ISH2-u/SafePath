package com.safepath.ai.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.safepath.ai.network.dto.RegisterRequest;
import com.safepath.ai.network.dto.UserDto;
import com.safepath.ai.repositories.AuthRepository;

/**
 * AuthViewModel — login, register, logout.
 * register(RegisterRequest) overload added to match RegisterActivity.
 */
public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;

    private final MutableLiveData<Boolean> loading           = new MutableLiveData<>(false);
    private final MutableLiveData<String>  errorMessage      = new MutableLiveData<>(null);
    private final MutableLiveData<UserDto> authenticatedUser = new MutableLiveData<>(null);

    public AuthViewModel(@NonNull Application application) {
        super(application);
        this.authRepository = new AuthRepository(application.getApplicationContext());
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public LiveData<Boolean> isLoading()           { return loading; }
    public LiveData<Boolean> getIsLoading()        { return loading; }
    public LiveData<String>  getErrorMessage()     { return errorMessage; }
    public LiveData<UserDto> getAuthenticatedUser(){ return authenticatedUser; }

    // ── Register with DTO (called by RegisterActivity) ────────────────────────
    public void register(@NonNull RegisterRequest req) {
        loading.setValue(true);
        errorMessage.setValue(null);
        authRepository.register(
                req.email, req.password, req.fullName, req.phone,
                new AuthRepository.AuthCallback<UserDto>() {
            @Override public void onSuccess(UserDto result) {
                loading.postValue(false);
                authenticatedUser.postValue(result);
            }
            @Override public void onError(String error) {
                loading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }

    // ── Login ─────────────────────────────────────────────────────────────────
    public void login(String email, String password) {
        loading.setValue(true);
        errorMessage.setValue(null);
        authRepository.login(email, password, new AuthRepository.AuthCallback<UserDto>() {
            @Override public void onSuccess(UserDto result) {
                loading.postValue(false);
                authenticatedUser.postValue(result);
            }
            @Override public void onError(String error) {
                loading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }

    // ── Logout ────────────────────────────────────────────────────────────────
    public void logout() {
        authRepository.logout();
        authenticatedUser.setValue(null);
    }

    public boolean isLoggedIn() {
        return authRepository.isLoggedIn();
    }
}
