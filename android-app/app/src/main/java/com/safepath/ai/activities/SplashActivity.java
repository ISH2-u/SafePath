package com.safepath.ai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.safepath.ai.R;
import com.safepath.ai.utils.TokenManager;

/**
 * SplashActivity — Displays branding, then routes to:
 *   - MainActivity  if the user already has a valid auth token stored
 *   - LoginActivity if no token or token is empty
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 1500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Check token via TokenManager directly — no AuthRepository getInstance needed
            String token = TokenManager.getInstance().getToken();
            boolean isLoggedIn = (token != null && !token.isEmpty());

            Class<?> destination = isLoggedIn ? MainActivity.class : LoginActivity.class;

            Intent intent = new Intent(SplashActivity.this, destination);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, SPLASH_DELAY_MS);
    }
}
