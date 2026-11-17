package com.safepath.ai.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.safepath.ai.R;
import com.safepath.ai.activities.LoginActivity;
import com.safepath.ai.network.dto.UserUpdateRequest;
import com.safepath.ai.utils.PhoneUtils;
import com.safepath.ai.viewmodels.ProfileViewModel;

/**
 * ProfileFragment — View and edit user profile.
 * Wires ProfileViewModel for loading, saving, and account deletion.
 */
public class ProfileFragment extends Fragment {

    // ── UI References ─────────────────────────────────────────────────────────
    private TextInputEditText etFullName;
    private TextInputEditText etEmail;
    private TextInputEditText etPhone;
    private TextInputEditText etBloodGroup;
    private TextInputEditText etMedicalNotes;
    private TextInputLayout   tilFullName;
    private TextInputLayout   tilEmail;
    private TextInputLayout   tilPhone;
    private MaterialButton    btnSaveProfile;
    private MaterialButton    btnDeleteAccount;
    private ProgressBar       progressProfile;
    private TextView          tvProfileError;
    private TextView          tvProfileName;
    private TextView          tvProfileEmail;

    // ── ViewModel ─────────────────────────────────────────────────────────────
    private ProfileViewModel viewModel;

    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Wire UI
        etFullName      = view.findViewById(R.id.etProfileFullName);
        etEmail         = view.findViewById(R.id.etProfileEmail);
        etPhone         = view.findViewById(R.id.etProfilePhone);
        etBloodGroup    = view.findViewById(R.id.etBloodGroup);
        etMedicalNotes  = view.findViewById(R.id.etMedicalNotes);
        tilFullName     = view.findViewById(R.id.tilProfileFullName);
        tilEmail        = view.findViewById(R.id.tilProfileEmail);
        tilPhone        = view.findViewById(R.id.tilProfilePhone);
        btnSaveProfile  = view.findViewById(R.id.btnSaveProfile);
        btnDeleteAccount= view.findViewById(R.id.btnDeleteAccount);
        progressProfile = view.findViewById(R.id.progressProfile);
        tvProfileError  = view.findViewById(R.id.tvProfileError);
        tvProfileName   = view.findViewById(R.id.tvProfileName);
        tvProfileEmail  = view.findViewById(R.id.tvProfileEmail);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        // Observe profile data
        viewModel.getProfile().observe(getViewLifecycleOwner(), user -> {
            if (user == null) return;
            if (etFullName != null)     etFullName.setText(user.displayName);
            if (etEmail    != null)     etEmail.setText(user.email);
            if (etPhone    != null)     etPhone.setText(user.phone);
            if (etBloodGroup  != null)  etBloodGroup.setText(user.bloodGroup);
            if (etMedicalNotes != null) etMedicalNotes.setText(user.medicalNotes);
            // Update header labels if present
            if (tvProfileName  != null) tvProfileName.setText(user.displayName);
            if (tvProfileEmail != null) tvProfileEmail.setText(user.email);
        });

        // Loading
        viewModel.isLoading().observe(getViewLifecycleOwner(), loading -> {
            if (progressProfile != null) {
                progressProfile.setVisibility(
                        Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
            }
            boolean enabled = !Boolean.TRUE.equals(loading);
            if (btnSaveProfile  != null) btnSaveProfile.setEnabled(enabled);
            if (btnDeleteAccount != null) btnDeleteAccount.setEnabled(enabled);
        });

        // Error messages
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (tvProfileError != null) {
                if (error != null && !error.isEmpty()) {
                    tvProfileError.setText(error);
                    tvProfileError.setVisibility(View.VISIBLE);
                } else {
                    tvProfileError.setVisibility(View.GONE);
                }
            }
        });

        // Profile update success
        viewModel.getUpdateSuccess().observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(requireContext(),
                        "Profile updated successfully.", Toast.LENGTH_SHORT).show();
            }
        });

        // Account deleted → go to Login
        viewModel.getAccountDeleted().observe(getViewLifecycleOwner(), deleted -> {
            if (Boolean.TRUE.equals(deleted)) {
                Toast.makeText(requireContext(),
                        "Account permanently deleted.", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(requireContext(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });

        // Save button
        if (btnSaveProfile != null) {
            btnSaveProfile.setOnClickListener(v -> handleSaveProfile());
        }

        // Delete account button
        if (btnDeleteAccount != null) {
            btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
        }

        // Load profile
        viewModel.loadProfile();
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleSaveProfile() {
        String name  = etFullName  != null && etFullName.getText()  != null
                ? etFullName.getText().toString().trim()  : "";
        String email = etEmail     != null && etEmail.getText()     != null
                ? etEmail.getText().toString().trim()     : "";
        String phone = etPhone     != null && etPhone.getText()     != null
                ? etPhone.getText().toString().trim()     : "";
        String blood = etBloodGroup != null && etBloodGroup.getText() != null
                ? etBloodGroup.getText().toString().trim() : "";
        String notes = etMedicalNotes != null && etMedicalNotes.getText() != null
                ? etMedicalNotes.getText().toString().trim() : "";

        boolean valid = true;
        if (name.isEmpty()) {
            if (tilFullName != null) tilFullName.setError("Name is required");
            valid = false;
        } else {
            if (tilFullName != null) tilFullName.setError(null);
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (tilEmail != null) tilEmail.setError("Valid email is required");
            valid = false;
        } else {
            if (tilEmail != null) tilEmail.setError(null);
        }

        String normPhone = PhoneUtils.normalize(phone);
        if (!phone.isEmpty() && !PhoneUtils.isValid(normPhone)) {
            if (tilPhone != null) tilPhone.setError("Enter a valid phone number (7-15 digits)");
            valid = false;
        } else {
            if (tilPhone != null) tilPhone.setError(null);
        }

        if (!valid) return;

        UserUpdateRequest req = new UserUpdateRequest();
        req.fullName     = name;
        req.email        = email;
        req.phone        = normPhone.isEmpty() ? null : normPhone;
        req.bloodGroup   = blood.isEmpty()  ? null : blood;
        req.medicalNotes = notes.isEmpty()  ? null : notes;
        viewModel.updateProfile(req);
    }

    private void showDeleteAccountDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_delete_account, null);

        TextInputLayout   tilPwd  = dialogView.findViewById(R.id.tilDeletePassword);
        TextInputEditText etPwd   = dialogView.findViewById(R.id.etDeletePassword);
        TextView          tvErr   = dialogView.findViewById(R.id.tvDeleteError);
        MaterialButton btnCancel  = dialogView.findViewById(R.id.btnCancelDelete);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmDelete);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                String pwd = etPwd != null && etPwd.getText() != null
                        ? etPwd.getText().toString() : "";
                if (pwd.length() < 8) {
                    if (tilPwd != null) tilPwd.setError("Enter your current password");
                    return;
                }
                if (tilPwd != null) tilPwd.setError(null);
                viewModel.deleteAccount(pwd);
                dialog.dismiss();
            });
        }

        dialog.show();
    }
}
