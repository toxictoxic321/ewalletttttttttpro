package com.example.ewallet;

import android.content.Context; // Required for Toast and context
import android.content.Intent;
import android.graphics.Color; // NEW: Required for Color.parseColor()
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.content.ContextCompat; // NEW: Required for modern color fetching
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The main Home Fragment displaying user balance, quick actions, and dynamic transactions.
 * This fragment is hosted by MainActivity.
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUid;

    // Hardcoded colors for temporary use until colors.xml is created
    private final int COLOR_GRAY_LIGHT = Color.parseColor("#A0A0A0");

    // UI Elements
    private TextView userNameGreeting;
    private TextView balanceAmount;
    private LinearLayout transactionsListLayout;

    private TextView seeAllLink; // Declare a variable for the See All link

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the Home Screen layout (fragment_home.xml) into this fragment's view
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        // Link UI components
        userNameGreeting = view.findViewById(R.id.user_name_greeting);
        balanceAmount = view.findViewById(R.id.balance_amount);
        transactionsListLayout = view.findViewById(R.id.transactions_list_layout);

        // Load data and set up listeners
        loadUserData();
        loadRecentTransactions();
        setupQuickActionListeners(view);
// Link the See All TextView
        seeAllLink = view.findViewById(R.id.see_all_link);
// Set the click listener
        seeAllLink.setOnClickListener(v -> {
            if (getActivity() != null) {
                // Navigate to the AllTransactionsFragment
                ((MainActivity) getActivity()).loadFragment(new AllTransactionsFragment(), true);
            }
        });

        return view;
    }

    // --- Data Loading and Display ---
    private void loadUserData() {
        if (currentUid == null) {
            Log.e(TAG, "User not authenticated in HomeFragment.");
            return;
        }

        // Fetch the user's main profile document from Firestore
        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Retrieve user data using the keys defined in SignUpActivity
                        String name = documentSnapshot.getString("name");
                        // Use DoubleValue or check for Long when retrieving numbers from Firestore
                        Double balance = documentSnapshot.getDouble("balance");

                        // Format balance to display currency correctly
                        DecimalFormat df = new DecimalFormat("#,##0.00");

                        // Update UI with live data
                        if (name != null) {
                            userNameGreeting.setText("Hi, " + name);
                        }
                        if (balance != null) {
                            balanceAmount.setText("$" + df.format(balance));
                        }
                        Log.d(TAG, "User data loaded successfully.");
                    } else {
                        Log.e(TAG, "User document does not exist in Firestore.");
                        Toast.makeText(getContext(), "Profile data missing.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user data: ", e);
                    Toast.makeText(getContext(), "Failed to load balance.", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadRecentTransactions() {
        if (currentUid == null) return;

        // Clear any placeholder/old transactions from the layout
        transactionsListLayout.removeAllViews();

        // Query the 'transactions' sub-collection for the current user
        db.collection("users").document(currentUid).collection("transactions")
                // Order by timestamp descending (newest first)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                // Limit to show only the top 5 transactions on the home screen
                .limit(5)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {

                        // Check if there are any transactions returned
                        if (task.getResult().isEmpty()) {
                            displayEmptyTransactionsMessage();
                            return;
                        }

                        // Loop through every transaction document retrieved
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // Retrieve transaction details
                            String title = document.getString("description"); // Use description as the title
                            // Ensure we safely retrieve Double, handling potential nulls
                            Double amount = document.getDouble("amount");
                            String type = document.getString("type"); // e.g., Income, Loan Taken
                            Date date = document.getDate("timestamp");

                            // Proceed only if mandatory fields are present
                            if (amount != null && date != null) {
                                // Dynamically create and populate the transaction list item UI
                                addTransactionItemToUI(title, amount, type, date);
                            }
                        }

                    } else {
                        Log.w(TAG, "Error fetching recent transactions: ", task.getException());
                        // Display a message if fetching failed or returned nothing
                        displayEmptyTransactionsMessage();
                    }
                });
    }

    // Function to display the 'No transactions' message
    private void displayEmptyTransactionsMessage() {
        // Only display if the list is truly empty
        if (transactionsListLayout.getChildCount() == 0) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("No recent transactions found.");
            emptyText.setTextColor(COLOR_GRAY_LIGHT); // Use hardcoded color
            emptyText.setPadding(0, 16, 0, 0);
            transactionsListLayout.addView(emptyText);
        }
    }

    // HomeFragment.java: Update the addTransactionItemToUI method

    private void addTransactionItemToUI(String title, double amount, String type, Date date) {
        // Inflate the reusable transaction_list_item.xml layout
        View transactionView = getLayoutInflater().inflate(R.layout.transaction_list_item, transactionsListLayout, false);

        // --- 1. Find UI components in the inflated layout ---
        TextView titleTv = transactionView.findViewById(R.id.transaction_title);
        TextView dateTv = transactionView.findViewById(R.id.transaction_date);
        TextView amountTv = transactionView.findViewById(R.id.transaction_amount);

        // --- 2. Format and Set Data ---

        // Format the amount string (e.g., +$100.00 or -$24.00)
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String formattedAmount;
        int color;

        // Determine color and sign based on transaction type
        // FIX: Explicitly check for "Transfer (Received)" to ensure it displays as green income.
        if ("Income".equals(type) || "Loan Taken".equals(type) || "Transfer (Received)".equals(type)) {
            formattedAmount = "+ $" + df.format(amount);
            color = Color.parseColor("#A5D6A7"); // Light Green for Income/Positive Action
        } else {
            // This handles all expenses: Loan Repayment, Transfer (Sent)
            formattedAmount = "- $" + df.format(amount);
            color = Color.parseColor("#FF5555"); // Red for Expense
        }

        // Format the date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

        // Set the formatted values to the TextViews
        titleTv.setText(title);
        dateTv.setText(dateFormat.format(date));
        amountTv.setText(formattedAmount);
        amountTv.setTextColor(color);

        // --- 3. Add the complete item to the list ---
        transactionsListLayout.addView(transactionView);
    }

    // --- Fragment Navigation ---
    private void setupQuickActionListeners(View view) {
        // Find the 'Take Loan' quick action container
        View takeLoanAction = view.findViewById(R.id.action_take_loan);

        View repayLoanAction = view.findViewById(R.id.action_repay_loan); // NEW
        // Find the 'Transfer' quick action container (NEW)
        View transferAction = view.findViewById(R.id.action_transfer); // 1. FIND THE NEW VIEW

        View settingsQuickAction = view.findViewById(R.id.action_settings_quick); // 1. FIND THE NEW VIEW


        // When Take Loan is clicked, navigate to TakeLoanFragment
        takeLoanAction.setOnClickListener(v -> {
            if (getActivity() != null) {
                // Use the hosting MainActivity's loadFragment method
                // true means add the fragment to the back stack (can use back button to return)
                ((MainActivity) getActivity()).loadFragment(new TakeLoanFragment(), true);
            }
        });

        // 2. Repay Loan Button Listener (NEW)
        repayLoanAction.setOnClickListener(v -> {
            if (getActivity() != null) {
                // Navigate to RepayLoanFragment
                ((MainActivity) getActivity()).loadFragment(new RepayLoanFragment(), true);
            }
        });


        // 3. Transfer Button Listener (NEW LOGIC)
        transferAction.setOnClickListener(v -> {
            if (getActivity() != null) {
                // Navigate to the TransferFragment when the button is clicked
                ((MainActivity) getActivity()).loadFragment(new TransferFragment(), true);
            }
        });

        settingsQuickAction.setOnClickListener(v -> {
            if (getActivity() != null) {
                // Navigate to the SettingsFragment when the button is clicked
                ((MainActivity) getActivity()).loadFragment(new SettingsFragment(), true);
            }
        });

        // FUTURE TO DO: Add listeners for Repay Loan, Transfer, and Settings quick actions here.
    }
}