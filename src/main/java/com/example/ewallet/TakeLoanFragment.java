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
import android.widget.TextView;
import android.widget.Toast;

// Needed Firebase and Firestore imports
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A Fragment for users to take a loan.
 * Handles UI interaction, calculates available loan limits, processes the loan transaction atomically,
 * and records the loan as an 'Income' transaction.
 */
public class TakeLoanFragment extends Fragment {

    private static final String TAG = "TakeLoanFragment";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUid;
    // This stores the currently available limit (LoanLimit - LoanTaken) fetched from DB
    private double availableLimit = 0.0;

    // UI Elements
    private TextView textAvailableLimit;
    private EditText inputLoanAmount;
    private Button btnContinueLoan;

    public TakeLoanFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment (fragment_take_loan.xml)
        View view = inflater.inflate(R.layout.fragment_take_loan, container, false);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        // Link UI components from the XML layout
        textAvailableLimit = view.findViewById(R.id.text_available_limit);
        inputLoanAmount = view.findViewById(R.id.input_loan_amount);
        btnContinueLoan = view.findViewById(R.id.btn_continue_loan);

        // Load the available limit from the database immediately when the fragment loads
        loadAvailableLimit();

        // Setup Button Listeners for presets and the main 'Continue' button
        setupPresets(view);

        btnContinueLoan.setOnClickListener(v -> processLoan());

        // Back Button Logic: Allows the user to return to the HomeFragment
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                // popBackStack removes the current fragment from the screen
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        return view;
    }

    private void setupPresets(View view) {
        // Find preset buttons
        Button preset200 = view.findViewById(R.id.btn_preset_200);
        Button preset500 = view.findViewById(R.id.btn_preset_500);
        Button preset800 = view.findViewById(R.id.btn_preset_800);

        // Common click behavior: auto-fill the input box with the button's amount
        View.OnClickListener presetListener = v -> {
            Button clickedButton = (Button) v;
            String amountText = clickedButton.getText().toString().replace("$", "");
            inputLoanAmount.setText(amountText);
            inputLoanAmount.setSelection(amountText.length()); // Move cursor to the end
        };

        preset200.setOnClickListener(presetListener);
        preset500.setOnClickListener(presetListener);
        preset800.setOnClickListener(presetListener);
    }

    private void loadAvailableLimit() {
        if (currentUid == null) return;

        // Fetch the user's document to calculate available loan limit
        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Retrieve all necessary loan/balance data
                        Double loanLimit = documentSnapshot.getDouble("loanLimit"); // Max loan allowed (1000.00)
                        Double loanTaken = documentSnapshot.getDouble("loanTaken"); // Current outstanding loan (0.00 initially)

                        if (loanLimit != null && loanTaken != null) {
                            // Calculate available limit: LoanLimit - LoanTaken
                            availableLimit = loanLimit - loanTaken;

                            // Display the available limit with formatting
                            DecimalFormat df = new DecimalFormat("#,##0.00");
                            textAvailableLimit.setText("$" + df.format(availableLimit));
                            Log.d(TAG, "Available limit loaded: " + availableLimit);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error loading limit: ", e);
                    textAvailableLimit.setText("$0.00");
                    Toast.makeText(getContext(), "Could not fetch limits.", Toast.LENGTH_SHORT).show();
                });
    }

    private void processLoan() {
        String amountStr = inputLoanAmount.getText().toString().trim();

        // --- 1. Input Validation ---
        if (TextUtils.isEmpty(amountStr)) {
            Toast.makeText(getContext(), "Please enter an amount.", Toast.LENGTH_SHORT).show();
            return;
        }

        double loanAmount;
        try {
            loanAmount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid amount entered.", Toast.LENGTH_SHORT).show();
            return;
        }

        // --- FIX: Make loanAmount final for use in the lambda (runTransaction) ---
        final double finalLoanAmount = loanAmount;

        if (finalLoanAmount <= 0) {
            Toast.makeText(getContext(), "Loan amount must be positive.", Toast.LENGTH_SHORT).show();
            return;
        }

        // --- 2. Limit Check ---
        if (finalLoanAmount > availableLimit) {
            DecimalFormat df = new DecimalFormat("#.00");
            Toast.makeText(getContext(), "Loan exceeds available limit of $" + df.format(availableLimit), Toast.LENGTH_LONG).show();
            return;
        }

        // --- 3. Update Transaction and User Profile (Atomic Batch Write) ---
        // We use Firestore Transactions to ensure the balance update and transaction record happen together,
        // preventing corruption if the app crashes halfway.

        DocumentReference userRef = db.collection("users").document(currentUid);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            // Get the user data snapshot within the transaction (locks the data)
            DocumentReference snapshotRef = db.collection("users").document(currentUid);
            Map<String, Object> userData = transaction.get(snapshotRef).getData();

            if (userData == null) {
                throw new RuntimeException("User data not found for loan process.");
            }

            // Calculate new balances based on data retrieved above
            double currentBalance = (Double) userData.get("balance");
            double currentLoanTaken = (Double) userData.get("loanTaken");

            double newBalance = currentBalance + finalLoanAmount; // Loan adds to cash balance
            double newLoanTaken = currentLoanTaken + finalLoanAmount; // Loan adds to debt balance

            // --- Record Loan as Transaction (Income) ---
            Map<String, Object> loanTransaction = new HashMap<>();
            loanTransaction.put("type", "Loan Taken");
            loanTransaction.put("amount", finalLoanAmount);
            loanTransaction.put("description", "Loan Disbursed");
            loanTransaction.put("source", "eWallet Bank");
            loanTransaction.put("timestamp", new Date()); // Record the current date/time

            // Add transaction document to the sub-collection (uses auto-ID)
            transaction.set(userRef.collection("transactions").document(), loanTransaction);

            // --- Update Main User Document (Atomic Update) ---
            Map<String, Object> updates = new HashMap<>();
            updates.put("balance", newBalance);
            updates.put("loanTaken", newLoanTaken);

            // Set the updates on the user document
            transaction.set(userRef, updates, SetOptions.merge());

            return null; // Return null to indicate success and commit the transaction

        }).addOnSuccessListener(aVoid -> {
            // --- 4. Success Feedback ---
            Log.d(TAG, "Loan transaction committed successfully.");
            showSuccessAlert(); // Shows success message and navigates back

            // Reload the limit display to show the updated debt
            loadAvailableLimit();

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Transaction failure: Loan process failed.", e);
            Toast.makeText(getContext(), "Transaction failed. Please try again.", Toast.LENGTH_LONG).show();
        });
    }

    // Custom method to display the alert-type success message and navigate back
    private void showSuccessAlert() {
        Toast.makeText(getContext(), "Loan Taken Successfully!", Toast.LENGTH_LONG).show();

        // Go back to the Home screen
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().popBackStack();
        }
    }
}