package com.safepath.ai.fragments;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.safepath.ai.R;
import com.safepath.ai.network.dto.SosResponse;
import com.safepath.ai.viewmodels.JourneyViewModel;
import com.safepath.ai.viewmodels.SosViewModel;
import java.util.Locale;

/**
 * SosFragment — Fullscreen SOS emergency trigger with cancel countdown,
 * local Room storage before network dispatch, and verified recipient tracking.
 *
 * STRICT SAFETY RULE:
 * Emergency alerts are dispatched exclusively to user-verified contacts.
 * Authorities and strangers are NEVER alerted.
 */
public class SosFragment extends Fragment {

    private SosViewModel sosViewModel;
    private JourneyViewModel journeyViewModel;

    private TextView tvCountdown;
    private MaterialButton btnCancelSos;
    private MaterialButton btnSos;
    private SwitchCompat switchSilentSos;
    private MaterialCardView cardSosDeliveryStatus;
    private TextView tvSosStatusTitle;
    private TextView tvSosStatusDetails;
    private TextView tvSosTelemetry;

    private Location latestLocation = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvCountdown           = view.findViewById(R.id.tvCountdown);
        btnCancelSos          = view.findViewById(R.id.btnCancelSos);
        btnSos                = view.findViewById(R.id.btnSos);
        switchSilentSos       = view.findViewById(R.id.switchSilentSos);
        cardSosDeliveryStatus = view.findViewById(R.id.cardSosDeliveryStatus);
        tvSosStatusTitle      = view.findViewById(R.id.tvSosStatusTitle);
        tvSosStatusDetails    = view.findViewById(R.id.tvSosStatusDetails);
        tvSosTelemetry        = view.findViewById(R.id.tvSosTelemetry);

        sosViewModel = new ViewModelProvider(this).get(SosViewModel.class);
        journeyViewModel = new ViewModelProvider(requireActivity()).get(JourneyViewModel.class);

        setupObservers();
        setupListeners();
    }

    private void setupObservers() {
        // Track latest GPS fix from JourneyViewModel
        journeyViewModel.getCurrentLocation().observe(getViewLifecycleOwner(), location -> {
            if (location != null) {
                latestLocation = location;
            }
        });

        // Observe countdown state
        sosViewModel.getIsCountingDown().observe(getViewLifecycleOwner(), countingDown -> {
            boolean active = Boolean.TRUE.equals(countingDown);
            btnCancelSos.setVisibility(active ? View.VISIBLE : View.GONE);
            if (!active && !Boolean.TRUE.equals(sosViewModel.getIsDispatching().getValue())) {
                if (sosViewModel.getSosResponse().getValue() == null) {
                    tvCountdown.setText("Status: Ready to trigger");
                    tvCountdown.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_safe_green));
                }
            }
        });

        // Observe countdown timer seconds
        sosViewModel.getSecondsRemaining().observe(getViewLifecycleOwner(), sec -> {
            if (Boolean.TRUE.equals(sosViewModel.getIsCountingDown().getValue())) {
                tvCountdown.setText(String.format(Locale.getDefault(), "ALERT DISPATCHING IN %ds (Tap Cancel to Abort)", sec));
                tvCountdown.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_sos_red));
            }
        });

        // Observe dispatching
        sosViewModel.getIsDispatching().observe(getViewLifecycleOwner(), dispatching -> {
            if (Boolean.TRUE.equals(dispatching)) {
                tvCountdown.setText("Saving locally & broadcasting to verified contacts...");
                tvCountdown.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_sos_red));
            }
        });

        // Observe successful SOS response
        sosViewModel.getSosResponse().observe(getViewLifecycleOwner(), response -> {
            if (response != null) {
                displaySosResponse(response);
            }
        });

        // Observe status message
        sosViewModel.getStatusMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                tvCountdown.setText(msg);
                tvCountdown.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_sos_red));
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            }
        });

        // Observe errors / offline queuing
        sosViewModel.getErrorMessage().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty()) {
                cardSosDeliveryStatus.setVisibility(View.VISIBLE);
                tvSosStatusTitle.setText("Alert Saved in Local Offline Queue");
                tvSosStatusTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_warning_yellow));
                tvSosStatusDetails.setText("Network unavailable. Alert stored safely in local database and will retry automatically once connected.");
                Toast.makeText(requireContext(), "Offline: SOS stored locally. Will auto-retry.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupListeners() {
        btnSos.setOnClickListener(v -> {
            boolean silent = switchSilentSos != null && switchSilentSos.isChecked();
            String notes = silent ? "Silent SOS trigger" : "Manual SOS trigger";
            sosViewModel.startCountdown(latestLocation, notes);
        });

        btnCancelSos.setOnClickListener(v -> {
            sosViewModel.cancelCountdown();
            Toast.makeText(requireContext(), "SOS Cancelled", Toast.LENGTH_SHORT).show();
        });
    }

    private void displaySosResponse(@NonNull SosResponse response) {
        cardSosDeliveryStatus.setVisibility(View.VISIBLE);
        tvSosStatusTitle.setText("🚨 EMERGENCY ALERT DISPATCHED");
        tvSosStatusTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_sos_red));

        String detailText = String.format(
            Locale.getDefault(),
            "Alert delivered to %d verified contacts.\nIdempotency Key: %s\n%s",
            response.recipientsCount,
            response.id,
            response.disclaimer != null ? response.disclaimer : ""
        );
        tvSosStatusDetails.setText(detailText);

        if (response.latitude != null && response.longitude != null) {
            String accStr = (response.accuracy != null) ? String.format(Locale.getDefault(), " (±%.1fm)", response.accuracy) : "";
            String lastKnownStr = response.isLastKnown ? " [LAST-KNOWN LOCATION]" : "";
            tvSosTelemetry.setText(String.format(
                Locale.getDefault(),
                "Coordinates: Lat %.5f, Lng %.5f%s%s",
                response.latitude, response.longitude, accStr, lastKnownStr
            ));
        } else {
            tvSosTelemetry.setText("Coordinates: Unavailable (Obstructed Signal)");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sosViewModel != null) {
            sosViewModel.cancelCountdown();
        }
    }
}
