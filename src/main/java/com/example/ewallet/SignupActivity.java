package com.example.ewallet;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date; // Needed to timestamp the transaction
import java.util.HashMap;
import java.util.Map;

/**
 * Activity for user registration (Sign Up).
 * This class handles:
 * 1. User authentication.
 * 2. Initializing the user's main profile and financial limits (balance, loanLimit, loanTaken).
 * 3. Creating the first entry in the 'transactions' sub-collection (Signup Bonus).
 */
public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity";

    // Firebase instances
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // UI elements
    private EditText inputUserId, inputName, inputPassword;
    private Button btnSignUp;
    private TextView linkToSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize Firebase services
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 1. Link UI elements to Java variables
        inputUserId = findViewById(R.id.input_user_id);
        inputName = findViewById(R.id.input_name);
        inputPassword = findViewById(R.id.input_password);
        btnSignUp = findViewById(R.id.btn_signup);
        linkToSignIn = findViewById(R.id.link_to_signin);

        // 2. Set up the click listeners
        btnSignUp.setOnClickListener(v -> registerUser());

        linkToSignIn.setOnClickListener(v -> {
            // Navigate the user to the Sign In screen
            Intent intent = new Intent(SignupActivity.this, SigninActivity.class);
            startActivity(intent);
        });
    }

    private void registerUser() {
        // --- 1. Get and Validate User Input ---
        final String userId = inputUserId.getText().toString().trim();
        final String name = inputName.getText().toString().trim();
        final String password = inputPassword.getText().toString().trim();

        // INTERNAL: Construct the hidden email format for Firebase Auth.
        final String emailId = userId + "@ewallet.com";

        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(name) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "All fields (User ID, Name, Password) are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        // --- 2. Create User Account in Firebase Authentication ---
        mAuth.createUserWithEmailAndPassword(emailId, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        String uid = mAuth.getCurrentUser().getUid();

                        // Proceed to save the complete user profile and financial limits
                        saveUserToFirestore(uid, userId, name);

                    } else {
                        // Failure: User ID may already be taken.
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Toast.makeText(SignupActivity.this, "Sign Up failed: User ID may already be taken.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(String uid, String userId, String name) {
        // --- 3. Save User Profile and Initial Financial Limits to Firestore ---

        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("userId", userId);
        user.put("name", name);

        // **UPDATED FINANCIAL FIELDS:** Initial balance set to $100.00
        user.put("balance", 100.00);       // Initial starting balance (in dollars)
        user.put("loanLimit", 1000.00);    // Fixed maximum loan limit (in dollars)
        user.put("loanTaken", 0.00);       // Starting loan outstanding is zero

        // Save the main user document
        db.collection("users").document(uid)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile and financial limits successfully written! Initializing transactions...");
                    // Once the profile is saved, proceed to create the first dummy transaction
                    initializeTransactionsSubcollection(uid);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error writing user profile", e);
                    Toast.makeText(SignupActivity.this, "Database error: Try again.", Toast.LENGTH_LONG).show();
                });
    }

    private void initializeTransactionsSubcollection(String uid) {
        // --- 4. Create the Transactions Sub-collection with a welcome entry ---

        // This transaction simulates the initial $100 Signup Bonus
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("type", "Income");
        transaction.put("amount", 100.00); // Matches initial balance
        transaction.put("description", "Signup Bonus");
        transaction.put("source", "System");
        transaction.put("timestamp", new Date()); // Live mobile date/time for sorting

        // Write the transaction document into the 'transactions' sub-collection
        db.collection("users").document(uid).collection("transactions")
                .add(transaction) // .add() creates a document with an auto-generated ID
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Transactions sub-collection initialized. Redirecting.");

                    // --- 5. Final Success: REDIRECT TO SIGN IN ---
                    Toast.makeText(SignupActivity.this, "Registration Successful! Please sign in.", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(SignupActivity.this, SigninActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error initializing transactions", e);
                    Toast.makeText(SignupActivity.this, "Transaction database error.", Toast.LENGTH_LONG).show();
                });
    }
}