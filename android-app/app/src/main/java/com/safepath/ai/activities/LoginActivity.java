package com.safepath.ai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.safepath.ai.R;
import com.safepath.ai.network.ApiConfig;
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

        TextView tvServerEndpoint = findViewById(R.id.tvLoginServerEndpoint);
        if (tvServerEndpoint != null) {
            tvServerEndpoint.setText("Server: " + ApiConfig.getBaseUrl());
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

    private void observeViewModel() {
        authViewModel.getIsLoading().observe(this, loading -> {
            if (btnLogin != null) {
                btnLogin.setEnabled(!loading);
                btnLogin.setText(loading ? "Authenticating..." : getString(R.string.btn_login));
            }
        });

        authViewModel.getErrorMessage().observe(this, error -> {
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
        String password = etPassword != null && etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (tilEmail != null) tilEmail.setError("Please enter a valid email address");
            return;
        }
        if (tilEmail != null) tilEmail.setError(null);

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            if (tilPassword != null) tilPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (tilPassword != null) tilPassword.setError(null);

        authViewModel.login(email, password);
    }
}
