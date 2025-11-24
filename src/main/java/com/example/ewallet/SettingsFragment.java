package com.example.ewallet;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Fragment for managing user settings: updating name (Firestore) and updating password (Firebase Auth).
 */
public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    // UI elements
    private RadioGroup rgSettingsSelector;
    private LinearLayout inputContainerName, inputContainerPassword;
    private Button btnUpdateChanges;

    // Name Fields
    private EditText inputNewName, inputConfirmName;

    // Password Fields
    private EditText inputNewPassword, inputConfirmPassword;

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Link UI components
        rgSettingsSelector = view.findViewById(R.id.rg_settings_selector);
        inputContainerName = view.findViewById(R.id.input_container_name);
        inputContainerPassword = view.findViewById(R.id.input_container_password);
        btnUpdateChanges = view.findViewById(R.id.btn_update_changes);

        // Link Input Fields
        inputNewName = view.findViewById(R.id.input_new_name);
        inputConfirmName = view.findViewById(R.id.input_confirm_name);
        inputNewPassword = view.findViewById(R.id.input_new_password);
        inputConfirmPassword = view.findViewById(R.id.input_confirm_password);

        // Setup Listeners
        setupSegmentedControl();
        btnUpdateChanges.setOnClickListener(v -> handleUpdate());

        // Back Button Logic
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        return view;
    }

    private void setupSegmentedControl() {
        // This listener toggles the visibility of the input fields based on the radio button selection
        rgSettingsSelector.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_update_name) {
                inputContainerName.setVisibility(View.VISIBLE);
                inputContainerPassword.setVisibility(View.GONE);
            } else if (checkedId == R.id.rb_update_password) {
                inputContainerName.setVisibility(View.GONE);
                inputContainerPassword.setVisibility(View.VISIBLE);
            }
        });
    }

    private void handleUpdate() {
        int selectedId = rgSettingsSelector.getCheckedRadioButtonId();

        if (selectedId == R.id.rb_update_name) {
            updateName();
        } else if (selectedId == R.id.rb_update_password) {
            updatePassword();
        }
    }

    // --- Name Update Logic ---
    private void updateName() {
        final String newName = inputNewName.getText().toString().trim();
        String confirmName = inputConfirmName.getText().toString().trim();

        // 1. Validation Checks
        if (TextUtils.isEmpty(newName) || TextUtils.isEmpty(confirmName)) {
            Toast.makeText(getContext(), "Name fields cannot be empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newName.equals(confirmName)) {
            Toast.makeText(getContext(), "New Name and Confirm Name do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUser == null) return;

        // 2. Update Name in Firestore
        // Note: The user's name is only stored in Firestore, not Firebase Auth.
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", newName);

        db.collection("users").document(currentUser.getUid()).set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Name updated successfully!", Toast.LENGTH_LONG).show();
                    // Optional: Automatically go back to Home to see the change
                    if (getActivity() != null) {
                        getActivity().getSupportFragmentManager().popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating name: ", e);
                    Toast.makeText(getContext(), "Failed to update name.", Toast.LENGTH_SHORT).show();
                });
    }

    // --- Password Update Logic ---
    private void updatePassword() {
        final String newPassword = inputNewPassword.getText().toString().trim();
        String confirmPassword = inputConfirmPassword.getText().toString().trim();

        // 1. Validation Checks
        if (TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(getContext(), "Password fields cannot be empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            Toast.makeText(getContext(), "New Password and Confirm Password do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPassword.length() < 6) {
            Toast.makeText(getContext(), "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUser == null) return;

        // 2. Update Password in Firebase Authentication
        currentUser.updatePassword(newPassword)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Password updated successfully!", Toast.LENGTH_LONG).show();
                    // Optional: Automatically go back to Home
                    if (getActivity() != null) {
                        getActivity().getSupportFragmentManager().popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating password: ", e);
                    // Note: Password updates often fail if the user hasn't recently logged in (security constraint)
                    Toast.makeText(getContext(), "Failed: Please sign out and sign in again before changing password.", Toast.LENGTH_LONG).show();
                });
    }
}