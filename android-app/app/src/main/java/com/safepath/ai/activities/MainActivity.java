package com.safepath.ai.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safepath.ai.R;
import com.safepath.ai.controllers.SosCoordinator;
import com.safepath.ai.controllers.VolumeKeyDetector;
import com.safepath.ai.viewmodels.AuthViewModel;

/**
 * MainActivity — hosts NavHostFragment and BottomNavigationView.
 * Includes overflow menu with Logout option.
 * Configured with resilient two-way navigation for Safety Modules:
 * - Top app-bar back arrow returns to Home.
 * - Bottom navigation Home button returns to Home (on select and reselect).
 * - Android system back button returns to Home.
 */
public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private AuthViewModel authViewModel;
    private BottomNavigationView bottomNav;
    private VolumeKeyDetector volumeKeyDetector;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check & request notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        // ViewModel for logout
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Hardware Volume Key SOS trigger in foreground
        volumeKeyDetector = new VolumeKeyDetector(() -> {
            SosCoordinator.getInstance(getApplicationContext())
                    .startCountdown("VOLUME_KEY", null, "Hardware SOS: Volume Down x3 (Foreground)");
        });

        // Navigation
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            // Top-level destinations: Home, Map, Journey, and SOS.
            // Safety sub-modules (Contacts, Voice Codeword, Assistant, Profile, Settings)
            // are treated as hierarchical children and automatically display the Up/Back arrow.
            AppBarConfiguration appBarConfig = new AppBarConfiguration.Builder(
                    R.id.nav_home,
                    R.id.nav_map,
                    R.id.nav_journey,
                    R.id.nav_sos
            ).build();

            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig);

            if (toolbar != null) {
                toolbar.setNavigationOnClickListener(v -> onSupportNavigateUp());
            }

            bottomNav = findViewById(R.id.bottom_navigation);
            if (bottomNav != null) {
                // Synchronize bottom navigation selection with NavController
                navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                    int destId = destination.getId();
                    MenuItem item = bottomNav.getMenu().findItem(destId);
                    if (item != null) {
                        item.setChecked(true);
                    } else {
                        // Sub-screens launched from Home (Contacts, Voice Codeword, Assistant, Profile)
                        // visually map to the Home tab.
                        MenuItem homeItem = bottomNav.getMenu().findItem(R.id.nav_home);
                        if (homeItem != null) {
                            homeItem.setChecked(true);
                        }
                    }
                });

                // Reliable bottom navigation item selection
                bottomNav.setOnItemSelectedListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.nav_home) {
                        navigateToHome();
                        return true;
                    }

                    // For other tabs (Map, Journey, SOS, Settings), pop up to nav_home
                    // to prevent stacking orphaned sub-screens.
                    NavOptions navOptions = new NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setPopUpTo(R.id.nav_home, false)
                            .build();
                    try {
                        navController.navigate(itemId, null, navOptions);
                        return true;
                    } catch (Exception e) {
                        return NavigationUI.onNavDestinationSelected(item, navController);
                    }
                });

                // Reliable bottom navigation item reselection (when Home is already selected)
                bottomNav.setOnItemReselectedListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.nav_home) {
                        navigateToHome();
                    } else {
                        navController.popBackStack(itemId, false);
                    }
                });
            }
        }
    }

    /**
     * Reliably navigate back to the Home screen from any sub-screen or tab.
     */
    public void navigateToHome() {
        if (navController == null) return;
        int currentId = navController.getCurrentDestination() != null
                ? navController.getCurrentDestination().getId() : -1;
        if (currentId != R.id.nav_home) {
            boolean popped = navController.popBackStack(R.id.nav_home, false);
            if (!popped) {
                NavOptions options = new NavOptions.Builder()
                        .setPopUpTo(R.id.nav_home, true)
                        .setLaunchSingleTop(true)
                        .build();
                navController.navigate(R.id.nav_home, null, options);
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            return onSupportNavigateUp();
        }
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (volumeKeyDetector != null && volumeKeyDetector.onKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null) {
            if (navController.navigateUp()) {
                return true;
            }
            return navController.popBackStack(R.id.nav_home, false);
        }
        return super.onSupportNavigateUp();
    }
}
