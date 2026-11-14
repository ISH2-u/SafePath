package com.safepath.ai.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.safepath.ai.R;
import com.safepath.ai.models.Journey;
import com.safepath.ai.viewmodels.JourneyViewModel;
import java.util.Locale;

/**
 * JourneyFragment — Coordinates GPS journey tracking UI, permission workflows,
 * live telemetry updates, and background foreground tracking controls.
 */
public class JourneyFragment extends Fragment {

    private JourneyViewModel viewModel;

    private MaterialCardView bannerPermission;
    private MaterialCardView bannerGpsDisabled;
    private MaterialCardView cardJourneyStats;
    private TextInputLayout tilDestination;
    private EditText etDestination;
    private TextView tvActiveJourneyDestination;
    private TextView tvDuration;
    private TextView tvWaypoints;
    private TextView tvDistance;
    private TextView tvLatitude;
    private TextView tvLongitude;
    private TextView tvAccuracy;
    private MaterialButton btnStartJourney;
    private MaterialButton btnStopJourney;
    private MaterialButton btnRequestPermission;
    private MaterialButton btnOpenLocationSettings;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                if (Boolean.TRUE.equals(fineGranted) || Boolean.TRUE.equals(coarseGranted)) {
                    bannerPermission.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Location permission granted.", Toast.LENGTH_SHORT).show();
                } else {
                    bannerPermission.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(), "Location permission is required for journey tracking.", Toast.LENGTH_LONG).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_journey, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind views
        bannerPermission          = view.findViewById(R.id.bannerPermission);
        bannerGpsDisabled        = view.findViewById(R.id.bannerGpsDisabled);
        cardJourneyStats         = view.findViewById(R.id.cardJourneyStats);
        tilDestination           = view.findViewById(R.id.tilDestination);
        etDestination            = view.findViewById(R.id.etDestination);
        tvActiveJourneyDestination = view.findViewById(R.id.tvActiveJourneyDestination);
        tvDuration               = view.findViewById(R.id.tvDuration);
        tvWaypoints              = view.findViewById(R.id.tvWaypoints);
        tvDistance               = view.findViewById(R.id.tvDistance);
        tvLatitude               = view.findViewById(R.id.tvLatitude);
        tvLongitude              = view.findViewById(R.id.tvLongitude);
        tvAccuracy               = view.findViewById(R.id.tvAccuracy);
        btnStartJourney          = view.findViewById(R.id.btnStartJourney);
        btnStopJourney           = view.findViewById(R.id.btnStopJourney);
        btnRequestPermission     = view.findViewById(R.id.btnRequestPermission);
        btnOpenLocationSettings  = view.findViewById(R.id.btnOpenLocationSettings);

        viewModel = new ViewModelProvider(this).get(JourneyViewModel.class);

        setupObservers();
        setupListeners();
        checkPermissions();
    }

    private void setupObservers() {
        viewModel.getIsTracking().observe(getViewLifecycleOwner(), isTracking -> {
            boolean tracking = Boolean.TRUE.equals(isTracking);
            cardJourneyStats.setVisibility(tracking ? View.VISIBLE : View.GONE);
            btnStopJourney.setVisibility(tracking ? View.VISIBLE : View.GONE);
            btnStartJourney.setVisibility(tracking ? View.GONE : View.VISIBLE);
            tilDestination.setVisibility(tracking ? View.GONE : View.VISIBLE);
        });

        viewModel.getActiveJourney().observe(getViewLifecycleOwner(), journey -> {
            if (journey != null && tvActiveJourneyDestination != null) {
                String dest = !TextUtils.isEmpty(journey.destinationLabel)
                        ? journey.destinationLabel : "Active Safety Journey";
                tvActiveJourneyDestination.setText(dest);
            }
        });

        viewModel.getCurrentLocation().observe(getViewLifecycleOwner(), location -> {
            if (location != null) {
                tvLatitude.setText(String.format(Locale.getDefault(), "Lat: %.5f°", location.getLatitude()));
                tvLongitude.setText(String.format(Locale.getDefault(), "Lng: %.5f°", location.getLongitude()));
            }
        });

        viewModel.getAccuracyText().observe(getViewLifecycleOwner(), accuracy -> {
            if (tvAccuracy != null && accuracy != null) {
                tvAccuracy.setText(accuracy);
            }
        });

        viewModel.getDurationText().observe(getViewLifecycleOwner(), duration -> {
            if (tvDuration != null && duration != null) {
                tvDuration.setText(duration);
            }
        });

        viewModel.getDistanceText().observe(getViewLifecycleOwner(), distance -> {
            if (tvDistance != null && distance != null) {
                tvDistance.setText(distance);
            }
        });

        viewModel.getWaypointCount().observe(getViewLifecycleOwner(), count -> {
            if (tvWaypoints != null && count != null) {
                tvWaypoints.setText(String.format(Locale.getDefault(), "%d pts", count));
            }
        });

        viewModel.getGpsAvailable().observe(getViewLifecycleOwner(), available -> {
            bannerGpsDisabled.setVisibility(Boolean.FALSE.equals(available) ? View.VISIBLE : View.GONE);
        });

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty()) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupListeners() {
        btnStartJourney.setOnClickListener(v -> {
            if (!hasLocationPermission()) {
                showPermissionRationaleDialog();
                return;
            }
            String destination = etDestination.getText() != null
                    ? etDestination.getText().toString().trim() : "";
            viewModel.startJourney(destination);
        });

        btnStopJourney.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("End Journey")
                    .setMessage("Are you sure you want to end this journey? GPS tracking will stop.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("End Journey", (dialog, which) -> viewModel.stopJourney())
                    .show();
        });

        btnRequestPermission.setOnClickListener(v -> showPermissionRationaleDialog());

        btnOpenLocationSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkPermissions() {
        if (!hasLocationPermission()) {
            bannerPermission.setVisibility(View.VISIBLE);
        } else {
            bannerPermission.setVisibility(View.GONE);
        }
    }

    private void showPermissionRationaleDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_location_permission, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        View btnGrant = dialogView.findViewById(R.id.btnGrantLocationPermission);
        View btnDeny = dialogView.findViewById(R.id.btnDenyLocationPermission);

        if (btnGrant != null) {
            btnGrant.setOnClickListener(v -> {
                dialog.dismiss();
                locationPermissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            });
        }

        if (btnDeny != null) {
            btnDeny.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }
}
