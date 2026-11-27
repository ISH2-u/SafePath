package com.safepath.ai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.google.android.material.button.MaterialButton;
import com.safepath.ai.R;
import com.safepath.ai.services.SosAccessibilityService;

/**
 * SettingsFragment — Manages system, privacy, and Emergency SOS hardware/tile triggers.
 */
public class SettingsFragment extends Fragment {

    private TextView tvAccessibilityStatus;
    private MaterialButton btnConfigureAccessibility;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvAccessibilityStatus = view.findViewById(R.id.tvAccessibilityStatus);
        btnConfigureAccessibility = view.findViewById(R.id.btnConfigureAccessibility);

        if (btnConfigureAccessibility != null) {
            btnConfigureAccessibility.setOnClickListener(v ->
                    SosAccessibilityService.openAccessibilitySettings(requireContext()));
        }

        View shortcut = view.findViewById(R.id.cardVoiceCodewordShortcut);
        if (shortcut != null) {
            shortcut.setOnClickListener(v ->
                    Navigation.findNavController(v).navigate(R.id.nav_voice_codeword));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAccessibilityStatus();
    }

    private void updateAccessibilityStatus() {
        if (tvAccessibilityStatus == null || getContext() == null) return;
        boolean enabled = SosAccessibilityService.isAccessibilityServiceEnabled(requireContext());
        if (enabled) {
            tvAccessibilityStatus.setText("Status: Active & Enabled (Key Filtering Granted)");
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_safe_green));
        } else {
            tvAccessibilityStatus.setText("Status: Setup Required (Accessibility Disabled)");
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_moderate_risk));
        }
    }
}
