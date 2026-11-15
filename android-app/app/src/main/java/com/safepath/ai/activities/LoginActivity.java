package com.safepath.ai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.safepath.ai.R;
import com.safepath.ai.network.ApiConfig;
import com.safepath.ai.utils.ServerDialogHelper;
import com.safepath.ai.viewmodels.AuthViewModel;

/**
 * LoginActivity — Handles user authentication into SafePath AI.
 */
public class LoginActivity extends AppCompatActivity {

    private AuthViewModel authViewModel;
    private EditText etEmail;
    private EditText etPassword;
    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private MaterialButton btnLogin;
    private TextView tvLoginError;
    private ProgressBar progressLogin;
    private TextView tvServerEndpoint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvLoginError = findViewById(R.id.tvLoginError);
        progressLogin = findViewById(R.id.progressLogin);
        tvServerEndpoint = findViewById(R.id.tvLoginServerEndpoint);

        updateServerBadge();
        if (tvServerEndpoint != null) {
            tvServerEndpoint.setOnClickListener(v -> ServerDialogHelper.show(this, newUrl -> updateServerBadge()));
        }

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> handleLogin());
        }

        View btnDemo = findViewById(R.id.btnDemoExplore);
        if (btnDemo != null) {
            btnDemo.setOnClickListener(v -> {
                Toast.makeText(this, "Entering SafePath AI in Demo Preview Mode", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        }

        View tvRegister = findViewById(R.id.tvGoToRegister);
        if (tvRegister != null) {
            tvRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        }

        observeViewModel();
    }

    private void updateServerBadge() {
        if (tvServerEndpoint != null) {
            tvServerEndpoint.setText("Server: " + ApiConfig.getBaseUrl() + " (tap to configure)");
        }
    }

    private void observeViewModel() {
        authViewModel.getIsLoading().observe(this, loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            if (btnLogin != null) {
                btnLogin.setEnabled(!isLoading);
                btnLogin.setText(isLoading ? "Authenticating..." : getString(R.string.btn_login));
            }
            if (progressLogin != null) {
                progressLogin.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
        });

        authViewModel.getErrorMessage().observe(this, error -> {
            if (tvLoginError != null) {
                if (error != null && !error.isEmpty()) {
                    tvLoginError.setText(error);
                    tvLoginError.setVisibility(View.VISIBLE);
                } else {
                    tvLoginError.setVisibility(View.GONE);
                }
            }
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });

        authViewModel.getAuthenticatedUser().observe(this, user -> {
            if (user != null) {
                Toast.makeText(this, "Welcome, " + user.displayName + "!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            }
        });
    }

    private void handleLogin() {
        String email = etEmail != null && etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword != null && etPassword.getText() != null ? etPassword.getText().toString().trim()
                : "";

        if (tvLoginError != null) {
            tvLoginError.setVisibility(View.GONE);
        }

        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (tilEmail != null)
                tilEmail.setError("Please enter a valid email address");
            return;
        }
        if (tilEmail != null)
            tilEmail.setError(null);

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            if (tilPassword != null)
                tilPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (tilPassword != null)
            tilPassword.setError(null);

        authViewModel.login(email, password);
    }
}
