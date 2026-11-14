package com.safepath.ai.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.safepath.ai.R;
import com.safepath.ai.viewmodels.JourneyViewModel;
import com.safepath.ai.viewmodels.RiskViewModel;
import java.util.Locale;

/**
 * HomeFragment — Main dashboard displaying real-time safety status,
 * current GPS telemetry, active journey progress, area-risk estimation,
 * emergency contacts status, and prominent manual SOS trigger.
 */
public class HomeFragment extends Fragment {

    private TextView tvLocationPermissionStatus;
    private TextView tvLocationCoordinates;
    private TextView tvJourneyBadge;
    private TextView tvJourneyDetail;
    private TextView tvRiskExplanation;
    private View btnGrantLocationPermission;

    private JourneyViewModel journeyViewModel;
    private RiskViewModel riskViewModel;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineLocation = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseLocation = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (Boolean.TRUE.equals(fineLocation) || Boolean.TRUE.equals(coarseLocation)) {
                    updateLocationPermissionState(true);
                } else {
                    updateLocationPermissionState(false);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvLocationPermissionStatus = view.findViewById(R.id.tvLocationPermissionStatus);
        tvLocationCoordinates      = view.findViewById(R.id.tvLocationCoordinates);
        tvJourneyBadge             = view.findViewById(R.id.tvJourneyBadge);
        tvJourneyDetail            = view.findViewById(R.id.tvJourneyDetail);
        tvRiskExplanation          = view.findViewById(R.id.tvRiskExplanation);
        btnGrantLocationPermission = view.findViewById(R.id.btnGrantLocationPermission);

        journeyViewModel = new ViewModelProvider(requireActivity()).get(JourneyViewModel.class);
        riskViewModel = new ViewModelProvider(this).get(RiskViewModel.class);

        setupObservers();
        setupNavigation(view);
        checkPermissions();
    }

    private void setupObservers() {
        // Observe live GPS telemetry
        journeyViewModel.getCurrentLocation().observe(getViewLifecycleOwner(), location -> {
            if (location != null && tvLocationCoordinates != null) {
                float acc = location.getAccuracy();
                tvLocationCoordinates.setText(String.format(
                    Locale.getDefault(),
                    "GPS acquired: Lat %.5f°, Lng %.5f° (±%.1fm). Telemetry ready for SOS.",
                    location.getLatitude(), location.getLongitude(), acc
                ));

                // Fetch Area Risk for current location
                riskViewModel.fetchAreaRisk(location.getLatitude(), location.getLongitude(), 1.0);
            }
        });

        // Observe Journey status
        journeyViewModel.getIsTracking().observe(getViewLifecycleOwner(), isTracking -> {
            boolean tracking = Boolean.TRUE.equals(isTracking);
            if (tvJourneyBadge != null && getContext() != null) {
                tvJourneyBadge.setText(tracking ? "STREAMING" : "INACTIVE");
                tvJourneyBadge.setTextColor(ContextCompat.getColor(requireContext(),
                        tracking ? R.color.color_safe_green : R.color.color_on_surface_variant));
            }
        });

        journeyViewModel.getActiveJourney().observe(getViewLifecycleOwner(), journey -> {
            if (journey != null && tvJourneyDetail != null) {
                String dest = journey.destinationLabel != null ? journey.destinationLabel : "Safe Destination";
                tvJourneyDetail.setText("Active Journey to: " + dest + ". Location and route recording active.");
            } else if (tvJourneyDetail != null) {
                tvJourneyDetail.setText("No active journey currently monitored. Tap to set destination and begin safe travel.");
            }
        });

        // Observe Area Risk (PROMPT 8)
        riskViewModel.getAreaRisk().observe(getViewLifecycleOwner(), risk -> {
            if (risk != null && tvRiskExplanation != null) {
                tvRiskExplanation.setText(String.format(
                    Locale.getDefault(),
                    "%s (%s) · %s. Tap to view safety map.",
                    risk.riskCategory, risk.dataPeriod, risk.safetyGuidance
                ));
            }
        });
    }

    private void setupNavigation(View view) {
        // PROMINENT MANUAL SOS BUTTON
        View btnQuickSos = view.findViewById(R.id.btnQuickSos);
        if (btnQuickSos != null) {
            btnQuickSos.setOnClickListener(v ->
                    Navigation.findNavController(v).navigate(R.id.nav_sos));
        }

        // Location permission request button
        if (btnGrantLocationPermission != null) {
            btnGrantLocationPermission.setOnClickListener(v ->
                    locationPermissionLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }));
        }

        // Navigation cards
        setNavClick(view, R.id.cardJourneyStatus, R.id.nav_journey);
        setNavClick(view, R.id.cardAreaRisk, R.id.nav_map);
        setNavClick(view, R.id.cardContactsStatus, R.id.nav_contacts);
        setNavClick(view, R.id.cardMicStatus, R.id.nav_voice_codeword);
        setNavClick(view, R.id.tileVoiceCodeword, R.id.nav_voice_codeword);
        setNavClick(view, R.id.tileSafetyAssistant, R.id.nav_assistant);
        setNavClick(view, R.id.tileProfile, R.id.nav_profile);
        setNavClick(view, R.id.tileSettings, R.id.nav_settings);
    }

    private void setNavClick(View parent, int viewId, int destinationId) {
        View target = parent.findViewById(viewId);
        if (target != null) {
            target.setOnClickListener(v -> Navigation.findNavController(v).navigate(destinationId));
        }
    }

    private void checkPermissions() {
        if (getContext() == null) return;
        boolean hasLocation = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        updateLocationPermissionState(hasLocation);
    }

    private void updateLocationPermissionState(boolean granted) {
        if (tvLocationPermissionStatus == null || getContext() == null) return;
        if (granted) {
            tvLocationPermissionStatus.setText("ACTIVE");
            tvLocationPermissionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_safe_green));
            if (btnGrantLocationPermission != null) {
                btnGrantLocationPermission.setVisibility(View.GONE);
            }
        } else {
            tvLocationPermissionStatus.setText("PERMISSION NEEDED");
            tvLocationPermissionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_warning_yellow));
            if (tvLocationCoordinates != null) {
                tvLocationCoordinates.setText("Precise location is needed to broadcast coordinates during an SOS and monitor your safe route.");
            }
            if (btnGrantLocationPermission != null) {
                btnGrantLocationPermission.setVisibility(View.VISIBLE);
            }
        }
    }
}
