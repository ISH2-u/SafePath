package com.safepath.ai.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.safepath.ai.network.dto.AreaRiskDto;
import com.safepath.ai.repositories.RiskRepository;

/**
 * RiskViewModel — Exposes historical area risk estimates,
 * loading status, and data disclaimers to Map and Home fragments.
 */
public class RiskViewModel extends AndroidViewModel {

    private final RiskRepository repository;

    private final MutableLiveData<AreaRiskDto> areaRisk = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    public RiskViewModel(@NonNull Application application) {
        super(application);
        this.repository = new RiskRepository(application.getApplicationContext());
    }

    public LiveData<AreaRiskDto> getAreaRisk() { return areaRisk; }
    public LiveData<Boolean> getIsLoading()     { return isLoading; }
    public LiveData<Boolean> isLoading()        { return isLoading; }
    public LiveData<String> getErrorMessage()   { return errorMessage; }

    public void fetchAreaRisk(double lat, double lng, double radiusKm) {
        isLoading.setValue(true);
        errorMessage.setValue(null);

        repository.getAreaRisk(lat, lng, radiusKm, new RiskRepository.RiskCallback<AreaRiskDto>() {
            @Override
            public void onSuccess(AreaRiskDto result) {
                isLoading.postValue(false);
                areaRisk.postValue(result);
            }

            @Override
            public void onError(String error) {
                isLoading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }
}
