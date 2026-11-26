package com.safepath.ai.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.safepath.ai.network.dto.AreaRiskDto;
import com.safepath.ai.network.dto.H3CellRiskDto;
import com.safepath.ai.network.dto.H3GridRiskDto;
import com.safepath.ai.repositories.RiskRepository;

/**
 * RiskViewModel — Exposes historical area risk estimates, H3 hexagonal grid data,
 * selected cell inspection, loading status, and data disclaimers.
 */
public class RiskViewModel extends AndroidViewModel {

    private final RiskRepository repository;

    private final MutableLiveData<AreaRiskDto> areaRisk = new MutableLiveData<>(null);
    private final MutableLiveData<H3GridRiskDto> h3GridRisk = new MutableLiveData<>(null);
    private final MutableLiveData<H3CellRiskDto> selectedCell = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    public RiskViewModel(@NonNull Application application) {
        super(application);
        this.repository = new RiskRepository(application.getApplicationContext());
    }

    public LiveData<AreaRiskDto> getAreaRisk()       { return areaRisk; }
    public LiveData<H3GridRiskDto> getH3GridRisk()   { return h3GridRisk; }
    public LiveData<H3CellRiskDto> getSelectedCell() { return selectedCell; }
    public LiveData<Boolean> getIsLoading()           { return isLoading; }
    public LiveData<Boolean> isLoading()              { return isLoading; }
    public LiveData<String> getErrorMessage()         { return errorMessage; }

    public void selectCell(H3CellRiskDto cell) {
        selectedCell.setValue(cell);
    }

    public void clearSelectedCell() {
        selectedCell.setValue(null);
    }

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

    public void fetchH3GridRisk(double lat, double lng, double radiusKm, int resolution) {
        isLoading.setValue(true);
        errorMessage.setValue(null);

        repository.getH3GridRisk(lat, lng, radiusKm, resolution, new RiskRepository.RiskCallback<H3GridRiskDto>() {
            @Override
            public void onSuccess(H3GridRiskDto result) {
                isLoading.postValue(false);
                h3GridRisk.postValue(result);
            }

            @Override
            public void onError(String error) {
                isLoading.postValue(false);
                errorMessage.postValue(error);
            }
        });
    }
}
