package com.example.ewallet;

import android.graphics.Color;
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
 * A Fragment for users to repay an outstanding loan.
 * Handles debt retrieval, cash balance checks, Firebase updates (loanTaken, balance, loanLimit, transaction).
 */
public class RepayLoanFragment extends Fragment {

    private static final String TAG = "RepayLoanFragment";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUid;

    // Stores the user's current outstanding debt and current cash balance
    private double currentLoanTaken = 0.0;
    private double currentCashBalance = 0.0;

    // UI Elements
    private TextView textLoanTakenAmount;
    private EditText inputRepayAmount;
    private Button btnRepayComplete;
    private Button btnContinueRepay;

    public RepayLoanFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_repay_loan, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        // Link UI components
        textLoanTakenAmount = view.findViewById(R.id.text_loan_taken_amount);
        inputRepayAmount = view.findViewById(R.id.input_repay_amount);
        btnRepayComplete = view.findViewById(R.id.btn_repay_complete);
        btnContinueRepay = view.findViewById(R.id.btn_continue_repay);

        // Load the current loan amount and cash balance from the database
        loadLoanData();

        // Setup Button Listeners
        setupRepayAllPreset();

        btnContinueRepay.setOnClickListener(v -> processRepayment());

        // Back Button Logic: Returns to the Home screen
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                // Pop the fragment stack to go back to the HomeFragment
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        return view;
    }

    private void setupRepayAllPreset() {
        btnRepayComplete.setOnClickListener(v -> {
            // Check if there is a loan to repay
            if (currentLoanTaken > 0) {
                // Auto-fill the input box with the exact outstanding loan amount
                DecimalFormat df = new DecimalFormat("#.00");
                String amountText = df.format(currentLoanTaken);
                inputRepayAmount.setText(amountText);
                inputRepayAmount.setSelection(amountText.length()); // Put cursor at end
            } else {
                Toast.makeText(getContext(), "You have no outstanding loan to repay.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadLoanData() {
        if (currentUid == null) return;

        // Fetch the user's document to get the current outstanding loan and cash balance
        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // FIX: Safely retrieve Double values using the Number cast method
                        Number loanTakenNum = (Number) documentSnapshot.get("loanTaken");
                        Number cashBalanceNum = (Number) documentSnapshot.get("balance");

                        if (loanTakenNum != null && cashBalanceNum != null) {
                            currentLoanTaken = loanTakenNum.doubleValue();
                            currentCashBalance = cashBalanceNum.doubleValue();

                            // Display the outstanding loan amount
                            DecimalFormat df = new DecimalFormat("#,##0.00");
                            textLoanTakenAmount.setText("$" + df.format(currentLoanTaken));

                            Log.d(TAG, "Loan data loaded. Taken: " + currentLoanTaken + ", Balance: " + currentCashBalance);

                            // Disable buttons if no loan is outstanding and set color based on debt status
                            if (currentLoanTaken <= 0) {
                                btnRepayComplete.setEnabled(false);
                                btnContinueRepay.setEnabled(false);
                                // Green if 0 debt (safe color assignment)
                                textLoanTakenAmount.setTextColor(Color.parseColor("#A5D6A7"));
                            } else {
                                // Red if debt is outstanding
                                textLoanTakenAmount.setTextColor(Color.parseColor("#FF5555"));
                            }
                        } else {
                            Log.w(TAG, "Loan fields are missing in document.");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error loading loan data: ", e);
                    textLoanTakenAmount.setText("$0.00");
                    Toast.makeText(getContext(), "Could not fetch loan data.", Toast.LENGTH_SHORT).show();
                });
    }

    private void processRepayment() {
        String amountStr = inputRepayAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            Toast.makeText(getContext(), "Please enter an amount to repay.", Toast.LENGTH_SHORT).show();
            return;
        }

        double repayAmount;
        try {
            repayAmount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid amount entered.", Toast.LENGTH_SHORT).show();
            return;
        }

        final double finalRepayAmount = repayAmount;

        // --- 1. Validation Checks (Uses globally loaded currentLoanTaken/currentCashBalance) ---
        if (finalRepayAmount <= 0) {
            Toast.makeText(getContext(), "Repayment amount must be positive.", Toast.LENGTH_SHORT).show();
            return;
        }

        // A. Check if repayment amount is greater than outstanding loan
        if (finalRepayAmount > currentLoanTaken) {
            Toast.makeText(getContext(), "Repayment amount exceeds the outstanding loan.", Toast.LENGTH_LONG).show();
            return;
        }

        // B. Check if user has enough cash balance to make the repayment
        if (finalRepayAmount > currentCashBalance) {
            Toast.makeText(getContext(), "Insufficient balance. Your current balance is only $" + new DecimalFormat("#.00").format(currentCashBalance), Toast.LENGTH_LONG).show();
            return;
        }

        // --- 2. Update Transaction and User Profile (Atomic Batch Write) ---

        DocumentReference userRef = db.collection("users").document(currentUid);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            // Re-read user data inside the transaction for maximum accuracy
            DocumentReference snapshotRef = db.collection("users").document(currentUid);
            Map<String, Object> userData = transaction.get(snapshotRef).getData();

            if (userData == null) {
                throw new RuntimeException("User data not found for repayment process.");
            }

            // Safely retrieve current values again
            double updatedCurrentBalance = ((Number) userData.get("balance")).doubleValue();
            double updatedCurrentLoanTaken = ((Number) userData.get("loanTaken")).doubleValue();

            // Calculate new balances/limits
            double newBalance = updatedCurrentBalance - finalRepayAmount; // Cash balance decreases
            double newLoanTaken = updatedCurrentLoanTaken - finalRepayAmount; // Debt decreases

            // **FIXED BUG:** loanLimit is NOT changed. It remains the fixed value (e.g., $1000.00).
            // The available capacity increases naturally because 'newLoanTaken' decreases.

            // --- Record Repayment as Transaction (Expense) ---
            Map<String, Object> repaymentTransaction = new HashMap<>();
            repaymentTransaction.put("type", "Loan Repayment");
            repaymentTransaction.put("amount", finalRepayAmount);
            repaymentTransaction.put("description", "Loan Repayment Made");
            repaymentTransaction.put("source", "Debt Repayment");
            repaymentTransaction.put("timestamp", new Date());

            // Add transaction document to the sub-collection
            transaction.set(userRef.collection("transactions").document(), repaymentTransaction);

            // --- Update Main User Document ---
            Map<String, Object> updates = new HashMap<>();
            updates.put("balance", newBalance);
            updates.put("loanTaken", newLoanTaken);
            // We intentionally do NOT update loanLimit here.

            // Set the updates on the user document
            transaction.set(userRef, updates, SetOptions.merge());

            return null; // Commit transaction

        }).addOnSuccessListener(aVoid -> {
            // --- 3. Success Feedback and Navigation ---
            Toast.makeText(getContext(), "Loan Repayment Successful! Your capacity is restored.", Toast.LENGTH_LONG).show();

            // Go back to the Home screen to see updated balances
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Transaction failure: Repayment process failed.", e);
            Toast.makeText(getContext(), "Repayment failed. Please try again.", Toast.LENGTH_LONG).show();
        });
    }
}