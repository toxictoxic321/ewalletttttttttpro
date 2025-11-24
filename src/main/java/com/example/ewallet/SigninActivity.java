package com.example.ewallet;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Activity for user login (Sign In).
 * This class handles user login using the User ID and Password fields.
 */
public class SigninActivity extends AppCompatActivity {

    private static final String TAG = "SigninActivity";

    private FirebaseAuth mAuth;
    // We include inputName here to match the XML, but it is NOT used for authentication.
    private EditText inputUserId, inputPassword, inputName;
    private Button btnSignIn;
    private TextView linkToSignUp; // New variable for the clickable link
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

        // Initialize Firebase instance
        mAuth = FirebaseAuth.getInstance();

        // 1. Link UI elements to Java variables
        inputUserId = findViewById(R.id.input_user_id);
        inputPassword = findViewById(R.id.input_password);
        btnSignIn = findViewById(R.id.btn_signin);
        linkToSignUp = findViewById(R.id.link_to_signup); // Link the new TextView
        // 2. Set up the click listener for the Sign In button
        btnSignIn.setOnClickListener(v -> loginUser());

        // 3. Set up the click listener for the "Sign Up" link
        linkToSignUp.setOnClickListener(v -> {
            // Navigate the user to the Sign Up screen
            Intent intent = new Intent(SigninActivity.this, SignupActivity.class);
            startActivity(intent);
        });
    }

    private void loginUser() {
        // --- 1. Get and Validate User Input ---
        final String userId = inputUserId.getText().toString().trim();
        final String password = inputPassword.getText().toString().trim();

        // **CRITICAL STEP: THE EMAIL WORKAROUND**
        // We construct the hidden email format for Firebase Auth verification.
        final String emailId = userId + "@ewallet.com";

        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "User ID and Password are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        // --- 2. Authenticate User with Firebase ---
        // Authentication check only uses the unique emailId (User ID) and the password.
        // The Name field is ignored here as it is not needed for security verification.
        mAuth.signInWithEmailAndPassword(emailId, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Success! User ID and Password match the database record.
                        Log.d(TAG, "signInWithEmail:success");
                        Toast.makeText(SigninActivity.this, "Sign In Successful!", Toast.LENGTH_SHORT).show();

                        // Navigate to the main app activity (MainActivity)
                        Intent intent = new Intent(SigninActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();

                    } else {
                        // Failure: Credentials do not match any record.
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(SigninActivity.this, "Authentication failed. Check your User ID and Password.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}