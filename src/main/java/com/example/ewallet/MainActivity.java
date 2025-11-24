package com.example.ewallet;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.content.Intent;
import android.graphics.Color; // Import Color class to handle hex codes
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView; // Required for TextView access
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;

/**
 * MainActivity serves as the host for all application Fragments (Home, Stats, Settings).
 * It handles checking the user's login status and managing the bottom navigation bar clicks.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private FirebaseAuth mAuth;

    // Color definitions using Hex codes to avoid R.color errors
    private final int COLOR_PURPLE_ACCENT = Color.parseColor("#8A63D2");
    private final int COLOR_GRAY_INACTIVE = Color.parseColor("#C0C0C0");

    // UI Containers for the navigation bar icons/text
    private LinearLayout navHomeContainer, navSettingsContainer, navStatsContainer, navLogoutContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Links to the minimal layout that contains the fragment host (activity_main.xml)
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth instance
        mAuth = FirebaseAuth.getInstance();

        // 1. Check User Authentication Status (Security Check)
        checkAuthentication();

        // 2. Initialize UI components if the user is logged in
        if (mAuth.getCurrentUser() != null) {

            // Link the bottom navigation containers
            navHomeContainer = findViewById(R.id.nav_home_container);
            navSettingsContainer = findViewById(R.id.nav_settings_container);
            navStatsContainer = findViewById(R.id.nav_stats_container);
            navLogoutContainer = findViewById(R.id.nav_logout_container);

            // 3. Load the initial Home Fragment
            if (savedInstanceState == null) {
                // We load HomeFragment first (HomeFragment must exist in com.example.ewallet package)
                loadFragment(new HomeFragment(), false);
            }

            // 4. Set up the bottom navigation listeners
            setupBottomNavigation();
        }
    }

    // Ensures the user is logged in; redirects to WelcomeActivity otherwise.
    private void checkAuthentication() {
        if (mAuth.getCurrentUser() == null) {
            Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
            startActivity(intent);
            finish(); // Close MainActivity
        }
    }

    // Central function to load or switch fragments
    public void loadFragment(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        // R.id.fragment_container is the FrameLayout in activity_main.xml
        ft.replace(R.id.fragment_container, fragment);

        // Add to back stack allows the user to press the phone's back button
        if (addToBackStack) {
            ft.addToBackStack(null);
        }

        ft.commit();

        // After loading a fragment, update the navigation bar's visual state
        if (fragment instanceof HomeFragment) {
            updateNavBarTint(R.id.nav_home_container);
        }
        // FUTURE: Add checks for other fragments here
    }

    private void setupBottomNavigation() {
        // --- Navigation Logic (Switching Fragments) ---

        // Home Button Click: Loads HomeFragment
        navHomeContainer.setOnClickListener(v -> {
            loadFragment(new HomeFragment(), false);
        });

        // Stats Button Click (Future Fragment)
        navStatsContainer.setOnClickListener(v -> {
            // FUTURE: When StatsFragment is ready: loadFragment(new StatsFragment(), false);
            Toast.makeText(this, "Stats Fragment coming soon!", Toast.LENGTH_SHORT).show();
            updateNavBarTint(R.id.nav_stats_container);
        });

        // Settings Button Click (Future Fragment)
// Settings Button Click (Bottom Nav Bar Link)
        navSettingsContainer.setOnClickListener(v -> {
            // We now link the bottom navigation button to load the Settings Fragment
            loadFragment(new SettingsFragment(), false);
            updateNavBarTint(R.id.nav_settings_container);
        });


        // Stats Button Click (Bottom Nav Bar Link)
        navStatsContainer.setOnClickListener(v -> {
            // Load the Stats Fragment
            loadFragment(new StatsFragment(), false);
            updateNavBarTint(R.id.nav_stats_container);
        });

        // Logout Button Click
        navLogoutContainer.setOnClickListener(v -> {
            mAuth.signOut(); // Sign the user out of Firebase
            Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show();

            // Redirect back to the Welcome/Login screen
            Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
            startActivity(intent);
            finish();
        });
    }

    // Helper function to handle the visual state of the bottom navigation bar
    private void updateNavBarTint(int activeContainerId) {
        // Array containing all navigation containers
        LinearLayout[] containers = {navHomeContainer, navSettingsContainer, navStatsContainer, navLogoutContainer};

        for (LinearLayout container : containers) {
            // Get the ImageView (icon) and TextView (label) inside the container
            ImageView icon = (ImageView) container.getChildAt(0);
            TextView text = (TextView) container.getChildAt(1);

            if (container.getId() == activeContainerId) {
                // Set the active icon and text to Purple
                icon.setColorFilter(COLOR_PURPLE_ACCENT);
                text.setTextColor(COLOR_PURPLE_ACCENT);
            } else {
                // Set the inactive icons and text to Gray
                icon.setColorFilter(COLOR_GRAY_INACTIVE);
                text.setTextColor(COLOR_GRAY_INACTIVE);
            }
        }
    }
}