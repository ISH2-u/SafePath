package com.safepath.ai.viewmodels;

import android.app.Application;
import android.location.Location;
import android.os.CountDownTimer;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.safepath.ai.models.SosEvent;
import com.safepath.ai.network.dto.SosResponse;
import com.safepath.ai.repositories.SosRepository;

/**
 * SosViewModel — Manages emergency SOS countdown, trigger execution,
 * offline queuing status, and verified contact delivery responses.
 */
public class SosViewModel extends AndroidViewModel {

    private final SosRepository repository;

    private final MutableLiveData<Boolean> isCountingDown   = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> secondsRemaining = new MutableLiveData<>(5);
    private final MutableLiveData<Boolean> isDispatching    = new MutableLiveData<>(false);
    private final MutableLiveData<SosResponse> sosResponse   = new MutableLiveData<>(null);
    private final MutableLiveData<SosEvent> localEvent       = new MutableLiveData<>(null);
    private final MutableLiveData<String> statusMessage     = new MutableLiveData<>(null);
    private final MutableLiveData<String> errorMessage       = new MutableLiveData<>(null);

    private CountDownTimer countDownTimer;

    public SosViewModel(@NonNull Application application) {
        super(application);
        this.repository = new SosRepository(application.getApplicationContext());
    }

    public LiveData<Boolean> getIsCountingDown()   { return isCountingDown; }
    public LiveData<Integer> getSecondsRemaining() { return secondsRemaining; }
    public LiveData<Boolean> getIsDispatching()    { return isDispatching; }
    public LiveData<SosResponse> getSosResponse()   { return sosResponse; }
    public LiveData<SosEvent> getLocalEvent()       { return localEvent; }
    public LiveData<String> getStatusMessage()      { return statusMessage; }
    public LiveData<String> getErrorMessage()        { return errorMessage; }

    public void startCountdown(Location location, String notes) {
        cancelCountdown();

        isCountingDown.setValue(true);
        secondsRemaining.setValue(5);

        countDownTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) (millisUntilFinished / 1000) + 1;
                secondsRemaining.postValue(sec);
            }

            @Override
            public void onFinish() {
                isCountingDown.postValue(false);
                dispatchSos(location, notes);
            }
        }.start();
    }

    public void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        if (Boolean.TRUE.equals(isCountingDown.getValue())) {
            isCountingDown.setValue(false);
            statusMessage.setValue("SOS dispatch aborted.");
        }
    }

    public void triggerImmediateSos(Location location, String notes) {
        cancelCountdown();
        dispatchSos(location, notes);
    }

    private void dispatchSos(Location location, String notes) {
        isDispatching.setValue(true);
        errorMessage.setValue(null);

        Double lat = null;
        Double lng = null;
        Float acc = null;
        boolean isLastKnown = true;

        if (location != null) {
            lat = location.getLatitude();
            lng = location.getLongitude();
            acc = location.getAccuracy();
            // If location was generated within the last 30 seconds, it's live
            long ageMs = System.currentTimeMillis() - location.getTime();
            isLastKnown = (ageMs > 30_000);
        }

        repository.triggerSos(
            "current_user", lat, lng, acc, isLastKnown,
            "MANUAL", null, notes,
            new SosRepository.SosCallback<SosResponse>() {
                @Override
                public void onSuccess(SosResponse result) {
                    isDispatching.postValue(false);
                    sosResponse.postValue(result);

                    int count = result.recipientsCount;
                    if (count > 0) {
                        statusMessage.postValue(
                            String.format("EMERGENCY ALERT DELIVERED TO %d VERIFIED CONTACTS.", count)
                        );
                    } else {
                        statusMessage.postValue(
                            "Alert recorded. Note: You have not verified any emergency contacts yet."
                        );
                    }
                }

                @Override
                public void onError(String error, SosEvent event) {
                    isDispatching.postValue(false);
                    localEvent.postValue(event);
                    errorMessage.postValue(error);
                }
            }
        );
    }

    public void retryPending() {
        repository.retryPendingAlerts();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelCountdown();
    }
}

