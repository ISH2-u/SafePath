package com.safepath.ai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.safepath.ai.R;
import com.safepath.ai.network.ApiConfig;
import com.safepath.ai.utils.NetworkErrorParser;
import com.safepath.ai.utils.ServerDialogHelper;
import com.safepath.ai.viewmodels.AuthViewModel;

/**
 * LoginActivity — Handles user authentication into SafePath AI.
 * Equipped with live backend connection diagnostics, retry capability,
 * and clear separation between network connectivity issues and auth failures.
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

    // Connection Diagnostics Card Views
    private ImageView ivConnectionStatusDot;
    private TextView tvConnectionStatusTitle;
    private ProgressBar progressConnectionCheck;
    private TextView tvServerEndpoint;
    private TextView tvConnectionDetails;
    private MaterialButton btnRetryConnection;
    private MaterialButton btnConfigureServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Core Auth Views
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvLoginError = findViewById(R.id.tvLoginError);
        progressLogin = findViewById(R.id.progressLogin);

        // Connection Diagnostics Views
        ivConnectionStatusDot = findViewById(R.id.ivConnectionStatusDot);
        tvConnectionStatusTitle = findViewById(R.id.tvConnectionStatusTitle);
        progressConnectionCheck = findViewById(R.id.progressConnectionCheck);
        tvServerEndpoint = findViewById(R.id.tvLoginServerEndpoint);
        tvConnectionDetails = findViewById(R.id.tvConnectionDetails);
        btnRetryConnection = findViewById(R.id.btnRetryConnection);
        btnConfigureServer = findViewById(R.id.btnConfigureServer);

        updateServerDisplay();

        if (btnRetryConnection != null) {
            btnRetryConnection.setOnClickListener(v -> checkConnectionStatus(true));
        }

        if (btnConfigureServer != null) {
            btnConfigureServer.setOnClickListener(v ->
                    ServerDialogHelper.show(this, newUrl -> {
                        updateServerDisplay();
                        checkConnectionStatus(true);
                    }));
        }

        if (tvServerEndpoint != null) {
            tvServerEndpoint.setOnClickListener(v ->
                    ServerDialogHelper.show(this, newUrl -> {
                        updateServerDisplay();
                        checkConnectionStatus(true);
                    }));
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

        // Perform initial backend health check on screen load
        checkConnectionStatus(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServerDisplay();
    }

    private void updateServerDisplay() {
        if (tvServerEndpoint != null) {
            tvServerEndpoint.setText("Target: " + ApiConfig.getBaseUrl() + " (tap to configure)");
        }
    }

    /**
     * Checks backend health asynchronously and updates the diagnostics card accordingly.
     * @param userInitiated true if triggered by the user tapping "Retry Connection" or configuring server.
     */
    private void checkConnectionStatus(boolean userInitiated) {
        if (tvConnectionStatusTitle != null) {
            tvConnectionStatusTitle.setText("Checking backend connection...");
        }
        if (ivConnectionStatusDot != null) {
            ivConnectionStatusDot.setColorFilter(ContextCompat.getColor(this, R.color.color_inactive_grey));
        }
        if (progressConnectionCheck != null) {
            progressConnectionCheck.setVisibility(View.VISIBLE);
        }
        if (btnRetryConnection != null) {
            btnRetryConnection.setEnabled(false);
        }

        String targetUrl = ApiConfig.getBaseUrl();
        ServerDialogHelper.checkHealth(targetUrl, (healthy, statusCode, message) -> {
            if (isFinishing() || isDestroyed()) return;

            if (progressConnectionCheck != null) {
                progressConnectionCheck.setVisibility(View.GONE);
            }
            if (btnRetryConnection != null) {
                btnRetryConnection.setEnabled(true);
            }

            if (healthy) {
                if (ivConnectionStatusDot != null) {
                    ivConnectionStatusDot.setColorFilter(ContextCompat.getColor(this, R.color.color_safe_green));
                }
                if (tvConnectionStatusTitle != null) {
                    tvConnectionStatusTitle.setText("Backend Connected (HTTP " + statusCode + ")");
                }
                if (tvConnectionDetails != null) {
                    tvConnectionDetails.setVisibility(View.GONE);
                }
                // If previous error was connection-related, clear it
                if (tvLoginError != null && NetworkErrorParser.isConnectivityErrorMessage(tvLoginError.getText().toString())) {
                    tvLoginError.setVisibility(View.GONE);
                }
                if (userInitiated) {
                    Toast.makeText(this, "✅ " + message, Toast.LENGTH_SHORT).show();
                }
            } else {
                if (ivConnectionStatusDot != null) {
                    ivConnectionStatusDot.setColorFilter(ContextCompat.getColor(this, R.color.color_danger_red));
                }
                if (tvConnectionStatusTitle != null) {
                    tvConnectionStatusTitle.setText("Backend Unreachable");
                }
                if (tvConnectionDetails != null) {
                    tvConnectionDetails.setText(message);
                    tvConnectionDetails.setVisibility(View.VISIBLE);
                }
                if (userInitiated) {
                    Toast.makeText(this, "❌ Backend unreachable. Check USB & ADB reverse.", Toast.LENGTH_SHORT).show();
                }
            }
        });
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
            if (error != null && !error.isEmpty()) {
                if (NetworkErrorParser.isConnectivityErrorMessage(error)) {
                    // Connectivity failure: backend could not be reached
                    if (ivConnectionStatusDot != null) {
                        ivConnectionStatusDot.setColorFilter(ContextCompat.getColor(this, R.color.color_danger_red));
                    }
                    if (tvConnectionStatusTitle != null) {
                        tvConnectionStatusTitle.setText("Backend Unreachable");
                    }
                    if (tvConnectionDetails != null) {
                        tvConnectionDetails.setText(error);
                        tvConnectionDetails.setVisibility(View.VISIBLE);
                    }
                    if (tvLoginError != null) {
                        tvLoginError.setVisibility(View.GONE);
                    }
                } else {
                    // Application / Authentication failure: backend IS reached, but credentials rejected
                    if (ivConnectionStatusDot != null) {
                        ivConnectionStatusDot.setColorFilter(ContextCompat.getColor(this, R.color.color_safe_green));
                    }
                    if (tvConnectionStatusTitle != null) {
                        tvConnectionStatusTitle.setText("Backend Connected");
                    }
                    if (tvConnectionDetails != null) {
                        tvConnectionDetails.setVisibility(View.GONE);
                    }
                    if (tvLoginError != null) {
                        tvLoginError.setText(error);
                        tvLoginError.setVisibility(View.VISIBLE);
                    }
                }
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            } else {
                if (tvLoginError != null) {
                    tvLoginError.setVisibility(View.GONE);
                }
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
