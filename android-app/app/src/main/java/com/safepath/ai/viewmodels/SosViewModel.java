package com.safepath.ai.viewmodels;

import android.app.Application;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.safepath.ai.controllers.SosCoordinator;
import com.safepath.ai.models.SosEvent;
import com.safepath.ai.network.dto.SosResponse;
import com.safepath.ai.repositories.SosRepository;

/**
 * SosViewModel — Bridges the unified SosCoordinator to the UI (SosFragment).
 *
 * Guarantees that manual SOS buttons and background Voice SOS notifications
 * use the exact same underlying coordinator, timer, debouncer, and dispatch pipeline.
 */
public class SosViewModel extends AndroidViewModel {

    private final SosCoordinator coordinator;
    private final SosRepository repository;

    public SosViewModel(@NonNull Application application) {
        super(application);
        this.coordinator = SosCoordinator.getInstance(application);
        this.repository = new SosRepository(application.getApplicationContext());
    }

    public LiveData<Boolean> getIsCountingDown()   { return coordinator.getIsCountingDown(); }
    public LiveData<Integer> getSecondsRemaining() { return coordinator.getSecondsRemaining(); }
    public LiveData<Boolean> getIsDispatching()    { return coordinator.getIsDispatching(); }
    public LiveData<SosResponse> getSosResponse()   { return coordinator.getSosResponse(); }
    public LiveData<SosEvent> getLocalEvent()       { return coordinator.getLocalEvent(); }
    public LiveData<String> getStatusMessage()      { return coordinator.getStatusMessage(); }
    public LiveData<String> getErrorMessage()        { return coordinator.getErrorMessage(); }

    public void startCountdown(Location location, String notes) {
        coordinator.startCountdown("MANUAL", location, notes);
    }

    public void cancelCountdown() {
        coordinator.cancelCountdown();
    }

    public void triggerImmediateSos(Location location, String notes) {
        // Immediate SOS simply starts a 1s countdown or delegates directly
        coordinator.startCountdown("MANUAL", location, notes);
    }

    public void retryPending() {
        repository.retryPendingAlerts();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Note: Do NOT cancel countdown on ViewModel clearing because
        // the countdown must continue running even if the user navigates away or backgrounds the app!
    }
}
