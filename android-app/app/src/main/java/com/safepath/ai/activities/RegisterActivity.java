package com.safepath.ai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.safepath.ai.R;
import com.safepath.ai.network.ApiConfig;
import com.safepath.ai.network.dto.RegisterRequest;
import com.safepath.ai.viewmodels.AuthViewModel;

/**
 * RegisterActivity — Handles user account registration.
 * Wired with AuthViewModel; navigates to MainActivity on success.
 */
public class RegisterActivity extends AppCompatActivity {

    // ── UI ────────────────────────────────────────────────────────────────────
    private TextInputLayout   tilFullName;
    private TextInputLayout   tilEmail;
    private TextInputLayout   tilPhone;
    private TextInputLayout   tilPassword;
    private TextInputLayout   tilConfirmPassword;
    private TextInputEditText etFullName;
    private TextInputEditText etEmail;
    private TextInputEditText etPhone;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private MaterialButton    btnRegister;
    private TextView          tvGoToLogin;
    private ProgressBar       progressRegister;
    private TextView          tvRegisterError;

    // ── ViewModel ─────────────────────────────────────────────────────────────
    private AuthViewModel viewModel;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Wire UI
        tilFullName       = findViewById(R.id.tilFullName);
        tilEmail          = findViewById(R.id.tilEmail);
        tilPhone          = findViewById(R.id.tilPhone);
        tilPassword       = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        etFullName        = findViewById(R.id.etFullName);
        etEmail           = findViewById(R.id.etEmail);
        etPhone           = findViewById(R.id.etPhone);
        etPassword        = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister       = findViewById(R.id.btnRegister);
        tvGoToLogin       = findViewById(R.id.tvGoToLogin);
        progressRegister  = findViewById(R.id.progressRegister);
        tvRegisterError   = findViewById(R.id.tvRegisterError);

        TextView tvServerEndpoint = findViewById(R.id.tvServerEndpoint);
        if (tvServerEndpoint != null) {
            tvServerEndpoint.setText("Server: " + ApiConfig.getBaseUrl() + " (tap to configure)");
            tvServerEndpoint.setOnClickListener(v ->
                    com.safepath.ai.utils.ServerDialogHelper.show(this, newUrl ->
                            tvServerEndpoint.setText("Server: " + newUrl + " (tap to configure)")));
        }

        // ViewModel
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Observe loading state
        viewModel.isLoading().observe(this, loading -> {
            if (progressRegister != null) {
                progressRegister.setVisibility(
                        Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
            }
            if (btnRegister != null) {
                btnRegister.setEnabled(!Boolean.TRUE.equals(loading));
                btnRegister.setText(Boolean.TRUE.equals(loading) ? "Registering..." : getString(R.string.btn_register));
            }
        });

        // Observe errors
        viewModel.getErrorMessage().observe(this, error -> {
            if (tvRegisterError != null) {
                if (error != null && !error.isEmpty()) {
                    tvRegisterError.setText(error);
                    tvRegisterError.setVisibility(View.VISIBLE);
                } else {
                    tvRegisterError.setVisibility(View.GONE);
                }
            }
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });

        // Observe successful registration (token returned)
        viewModel.getAuthenticatedUser().observe(this, user -> {
            if (user != null) {
                // Registration and auto-login successful
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });

        // Register button
        if (btnRegister != null) {
            btnRegister.setOnClickListener(v -> handleRegister());
        }

        // Go to login
        if (tvGoToLogin != null) {
            tvGoToLogin.setOnClickListener(v -> finish());
        }
    }

    // ── Registration handler ──────────────────────────────────────────────────

    private void handleRegister() {
        String fullName       = getText(etFullName);
        String email          = getText(etEmail);
        String phone          = getText(etPhone);
        String password       = getText(etPassword);
        String confirmPassword = getText(etConfirmPassword);

        // Clear previous errors
        clearErrors();

        // Validate
        boolean valid = true;

        if (TextUtils.isEmpty(fullName)) {
            setError(tilFullName, "Full name is required");
            valid = false;
        }

        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setError(tilEmail, "Enter a valid email address");
            valid = false;
        }

        if (TextUtils.isEmpty(phone) || !phone.matches("^\\+?[0-9]{7,15}$")) {
            setError(tilPhone, "Enter a valid phone number (e.g. +91XXXXXXXXXX)");
            valid = false;
        }

        if (TextUtils.isEmpty(password) || password.length() < 8) {
            setError(tilPassword, "Password must be at least 8 characters");
            valid = false;
        }

        if (!password.equals(confirmPassword)) {
            setError(tilConfirmPassword, "Passwords do not match");
            valid = false;
        }

        if (!valid) return;

        // Build request and delegate to ViewModel
        RegisterRequest req = new RegisterRequest();
        req.fullName = fullName;
        req.email    = email;
        req.phone    = phone;
        req.password = password;

        viewModel.register(req);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getText(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private void setError(TextInputLayout til, String msg) {
        if (til != null) til.setError(msg);
    }

    private void clearErrors() {
        setError(tilFullName, null);
        setError(tilEmail, null);
        setError(tilPhone, null);
        setError(tilPassword, null);
        setError(tilConfirmPassword, null);
    }
}
