package com.safepath.ai.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safepath.ai.R;
import com.safepath.ai.viewmodels.AuthViewModel;

/**
 * MainActivity — hosts NavHostFragment and BottomNavigationView.
 * Includes overflow menu with Logout option.
 */
public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        // ViewModel for logout
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Navigation
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            AppBarConfiguration appBarConfig = new AppBarConfiguration.Builder(
                    R.id.nav_home,
                    R.id.nav_map,
                    R.id.nav_journey,
                    R.id.nav_contacts,
                    R.id.nav_sos,
                    R.id.nav_assistant,
                    R.id.nav_profile,
                    R.id.nav_settings,
                    R.id.nav_voice_codeword
            ).build();

            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig);

            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            if (bottomNav != null) {
                NavigationUI.setupWithNavController(bottomNav, navController);
            }
        }
    }

    // ── Options menu (Logout) ─────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            showLogoutConfirmation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showLogoutConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out of SafePath AI?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign Out", (d, w) -> performLogout())
                .show();
    }

    private void performLogout() {
        authViewModel.logout();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return (navController != null && navController.navigateUp())
                || super.onSupportNavigateUp();
    }
}
