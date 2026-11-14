package com.safepath.ai.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.safepath.ai.network.dto.UserDto;
import com.safepath.ai.network.dto.UserUpdateRequest;
import com.safepath.ai.repositories.AuthRepository;

/**
 * ProfileViewModel — Manages loading, updating and deleting the user profile.
 * Method and LiveData names aligned with ProfileFragment.
 */
public class ProfileViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;

    private final MutableLiveData<Boolean> loading        = new MutableLiveData<>(false);
    private final MutableLiveData<String>  errorMessage   = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> updateSuccess  = new MutableLiveData<>(false);
    private final MutableLiveData<UserDto> profile        = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> accountDeleted = new MutableLiveData<>(false);

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        this.authRepository = new AuthRepository(application.getApplicationContext());
    }

    // ── Accessors (names match what ProfileFragment observes) ─────────────────
    public LiveData<Boolean> isLoading()       { return loading; }
    public LiveData<Boolean> getIsLoading()    { return loading; }
    public LiveData<String>  getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getUpdateSuccess(){ return updateSuccess; }
    public LiveData<UserDto> getProfile()      { return profile; }
    public LiveData<Boolean> getAccountDeleted(){ return accountDeleted; }

    // ── Actions ───────────────────────────────────────────────────────────────

    public void loadProfile() {
        loading.setValue(true);
        errorMessage.setValue(null);
        authRepository.getProfile(new AuthRepository.AuthCallback<UserDto>() {
            @Override public void onSuccess(UserDto result) {
                loading.postValue(false);
                profile.postValue(result);
            }
            @Override public void onError(String error) {
                loading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }

    public void updateProfile(UserUpdateRequest req) {
        loading.setValue(true);
        errorMessage.setValue(null);
        updateSuccess.setValue(false);
        authRepository.updateProfile(req, new AuthRepository.AuthCallback<UserDto>() {
            @Override public void onSuccess(UserDto result) {
                loading.postValue(false);
                profile.postValue(result);
                updateSuccess.postValue(true);
            }
            @Override public void onError(String error) {
                loading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }

    public void deleteAccount(String password) {
        loading.setValue(true);
        errorMessage.setValue(null);
        authRepository.deleteAccount(password, new AuthRepository.AuthCallback<String>() {
            @Override public void onSuccess(String result) {
                loading.postValue(false);
                accountDeleted.postValue(true);
            }
            @Override public void onError(String error) {
                loading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }
}
