package com.example.ewallet;

import android.graphics.Color;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fragment to display a user's entire transaction history.
 */
public class AllTransactionsFragment extends Fragment {

    private static final String TAG = "AllTransactionsFragment";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUid;

    private LinearLayout transactionsListContainer;

    public AllTransactionsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_transactions, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        // Link UI components
        transactionsListContainer = view.findViewById(R.id.all_transactions_list_container);

        // Load all transactions
        if (currentUid != null) {
            loadAllTransactions();
        } else {
            Toast.makeText(getContext(), "User not authenticated.", Toast.LENGTH_SHORT).show();
        }

        // Back Button Logic: Returns to the Home screen
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                // Pop the fragment stack to go back to the HomeFragment
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        return view;
    }

    private void loadAllTransactions() {
        // Query the 'transactions' sub-collection
        db.collection("users").document(currentUid).collection("transactions")
                // Order by timestamp descending (newest first)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {

                        if (task.getResult().isEmpty()) {
                            displayEmptyMessage();
                            return;
                        }

                        // Loop through every transaction document retrieved
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String title = document.getString("description");
                            Double amount = document.getDouble("amount");
                            String type = document.getString("type");
                            Date date = document.getDate("timestamp");

                            if (amount != null && date != null) {
                                // Dynamically create and populate the transaction list item UI
                                addTransactionItemToUI(title, amount, type, date);
                            }
                        }

                    } else {
                        Log.e(TAG, "Error fetching all transactions: ", task.getException());
                        Toast.makeText(getContext(), "Failed to load transaction history.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void addTransactionItemToUI(String title, double amount, String type, Date date) {
        // Inflate the reusable transaction_list_item.xml layout
        View transactionView = getLayoutInflater().inflate(R.layout.transaction_list_item, transactionsListContainer, false);

        // --- 1. Find UI components in the inflated layout ---
        TextView titleTv = transactionView.findViewById(R.id.transaction_title);
        TextView dateTv = transactionView.findViewById(R.id.transaction_date);
        TextView amountTv = transactionView.findViewById(R.id.transaction_amount);

        // --- 2. Format and Set Data (Same logic as HomeFragment) ---
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String formattedAmount;
        int color;

        // Determine color and sign based on transaction type
        if ("Income".equals(type) || "Loan Taken".equals(type) || "Transfer (Received)".equals(type)) {
            formattedAmount = "+ $" + df.format(amount);
            color = Color.parseColor("#A5D6A7"); // Green
        } else {
            formattedAmount = "- $" + df.format(amount);
            color = Color.parseColor("#FF5555"); // Red
        }

        // Format the date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

        // Set the formatted values
        titleTv.setText(title);
        dateTv.setText(dateFormat.format(date));
        amountTv.setText(formattedAmount);
        amountTv.setTextColor(color);

        // --- 3. Add the complete item to the list ---
        transactionsListContainer.addView(transactionView);
    }

    private void displayEmptyMessage() {
        TextView emptyText = new TextView(getContext());
        emptyText.setText("You have no transaction history.");
        emptyText.setTextColor(Color.parseColor("#A0A0A0"));
        emptyText.setPadding(0, 32, 0, 0);
        transactionsListContainer.addView(emptyText);
    }
}