package com.example.ewallet;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.util.Log;

/**
 * This Activity represents the initial "Manage your wallet" onboarding screen.
 * It now handles navigation to both SignInActivity and SignUpActivity.
 */
public class WelcomeActivity extends AppCompatActivity {

    private static final String TAG = "WelcomeActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // 1. Find the two new buttons by their IDs
        Button signInButton = findViewById(R.id.btn_sign_in_welcome);
        Button signUpButton = findViewById(R.id.btn_sign_up_welcome);

        // 2. Set up the click listener for the SIGN IN button
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "User clicked 'Sign In'. Navigating to SignInActivity.");

                // Navigate to the Sign In screen
                Intent intent = new Intent(WelcomeActivity.this, SigninActivity.class);
                startActivity(intent);

                // NOTE: We don't use finish() here because the user might want to go back to Sign Up.
            }
        });

        // 3. Set up the click listener for the SIGN UP button
        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "User clicked 'Sign Up'. Navigating to SignUpActivity.");

                // Navigate to the Sign Up screen
                Intent intent = new Intent(WelcomeActivity.this, SignupActivity.class);
                startActivity(intent);
            }
        });
    }
}