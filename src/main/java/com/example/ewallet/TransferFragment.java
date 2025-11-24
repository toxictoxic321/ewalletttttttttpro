package com.example.ewallet;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A Fragment for peer-to-peer (P2P) transfers within the eWallet system.
 * Handles validation, recipient lookup, and atomic double-entry bookkeeping.
 */
public class TransferFragment extends Fragment {

    private static final String TAG = "TransferFragment";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUid;
    private double currentCashBalance = 0.0; // Sender's current balance
    private String senderCustomUserId = null; // Sender's human-readable ID

    // UI Elements
    private EditText inputRecipientId, inputTransferAmount, inputDescription;
    private Button btnConfirmTransfer, btnOtherBankTransfer;

    // Data storage for the transaction
    private String recipientUid = null;
    private String recipientUserId = null;

    public TransferFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment (fragment_transfer.xml)
        View view = inflater.inflate(R.layout.fragment_transfer, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        // Link UI components
        inputRecipientId = view.findViewById(R.id.input_recipient_id);
        inputTransferAmount = view.findViewById(R.id.input_transfer_amount);
        inputDescription = view.findViewById(R.id.input_description);
        btnConfirmTransfer = view.findViewById(R.id.btn_confirm_transfer);
        btnOtherBankTransfer = view.findViewById(R.id.btn_other_bank_transfer);

        // Load the sender's current balance and custom User ID immediately
        loadSenderBalance();

        // Setup Button Listeners
        btnConfirmTransfer.setOnClickListener(v -> searchRecipientAndProcessTransfer());

        // Other Bank Transfer Alert Logic (as requested)
        btnOtherBankTransfer.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Other Bank Transfer: Feature coming soon!", Toast.LENGTH_SHORT).show();
        });

        // Back Button Logic: Pop fragment off the stack
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        return view;
    }

    private void loadSenderBalance() {
        if (currentUid == null) return;

        // Fetch the sender's document to check cash balance and get user ID
        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Number balanceNum = (Number) documentSnapshot.get("balance");
                        String customId = documentSnapshot.getString("userId");

                        if (balanceNum != null && customId != null) {
                            currentCashBalance = balanceNum.doubleValue();
                            senderCustomUserId = customId; // Store sender's custom ID
                            Log.d(TAG, "Sender balance and ID loaded: " + currentCashBalance);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error loading sender balance: ", e);
                    Toast.makeText(getContext(), "Could not load balance for transfer checks.", Toast.LENGTH_SHORT).show();
                });
    }

    private void searchRecipientAndProcessTransfer() {
        // --- 1. Basic Input Validation ---
        final String recipientIdStr = inputRecipientId.getText().toString().trim();
        final String amountStr = inputTransferAmount.getText().toString().trim();
        final String descriptionStr = inputDescription.getText().toString().trim();

        if (TextUtils.isEmpty(recipientIdStr) || TextUtils.isEmpty(amountStr)) {
            Toast.makeText(getContext(), "Recipient ID and Amount are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        double transferAmount;
        try {
            transferAmount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid amount entered.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (transferAmount <= 0) {
            Toast.makeText(getContext(), "Transfer amount must be positive.", Toast.LENGTH_SHORT).show();
            return;
        }

        // --- 2. Sender Balance Check (Validation) ---
        if (transferAmount > currentCashBalance) {
            Toast.makeText(getContext(), "Insufficient funds. Balance: $" + new DecimalFormat("#.00").format(currentCashBalance), Toast.LENGTH_LONG).show();
            return;
        }

        // Prevent sending money to yourself using the stored custom ID
        if (recipientIdStr.equals(senderCustomUserId)) {
            Toast.makeText(getContext(), "Cannot transfer to your own account.", Toast.LENGTH_LONG).show();
            return;
        }

        // Fix final variables for async search
        final double finalTransferAmount = transferAmount;
        final String finalDescription = TextUtils.isEmpty(descriptionStr) ? "P2P Transfer" : descriptionStr;

        // --- 3. Search for Recipient in Firestore ---
        db.collection("users")
                .whereEqualTo("userId", recipientIdStr) // Search using the custom User ID
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(getContext(), "Recipient User ID not found.", Toast.LENGTH_LONG).show();
                    } else {
                        // Recipient found, get their UID (Document ID)
                        DocumentReference recipientDoc = querySnapshot.getDocuments().get(0).getReference();
                        recipientUid = recipientDoc.getId();
                        recipientUserId = recipientIdStr;

                        // Proceed with the atomic double-entry transaction
                        executeAtomicTransfer(finalTransferAmount, finalDescription);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Recipient search failed: ", e);
                    Toast.makeText(getContext(), "Error searching recipient.", Toast.LENGTH_SHORT).show();
                });
    }

    private void executeAtomicTransfer(double amount, String description) {
        // --- 4. Atomic Double-Entry Transaction ---

        DocumentReference senderRef = db.collection("users").document(currentUid);
        DocumentReference recipientRef = db.collection("users").document(recipientUid);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            // A. Get Sender's data and B. Get Recipient's data (Essential for transaction integrity)
            Map<String, Object> senderData = transaction.get(senderRef).getData();
            Map<String, Object> recipientData = transaction.get(recipientRef).getData();

            if (senderData == null || recipientData == null) {
                throw new RuntimeException("Sender or Recipient data not found.");
            }

            // --- DEBIT SENDER ---
            double senderBalance = ((Number) senderData.get("balance")).doubleValue();
            double newSenderBalance = senderBalance - amount;

            Map<String, Object> senderUpdates = new HashMap<>();
            senderUpdates.put("balance", newSenderBalance);
            transaction.set(senderRef, senderUpdates, SetOptions.merge());

            // --- CREDIT RECIPIENT ---
            double recipientBalance = ((Number) recipientData.get("balance")).doubleValue();
            double newRecipientBalance = recipientBalance + amount;

            Map<String, Object> recipientUpdates = new HashMap<>();
            recipientUpdates.put("balance", newRecipientBalance);
            transaction.set(recipientRef, recipientUpdates, SetOptions.merge());

            // --- RECORD SENDER TRANSACTION (Expense) ---
            Map<String, Object> senderTransaction = new HashMap<>();
            senderTransaction.put("type", "Transfer (Sent)");
            senderTransaction.put("amount", amount);
            senderTransaction.put("description", description);
            senderTransaction.put("source", "eWallet Bank"); // CORRECTED: Set Source to eWallet Bank
            senderTransaction.put("timestamp", new Date());
            transaction.set(senderRef.collection("transactions").document(), senderTransaction);

            // --- RECORD RECIPIENT TRANSACTION (Income) ---
            Map<String, Object> recipientTransaction = new HashMap<>();
            recipientTransaction.put("type", "Transfer (Received)");
            recipientTransaction.put("amount", amount);
            recipientTransaction.put("description", description);
            recipientTransaction.put("source", "eWallet Bank"); // CORRECTED: Set Source to eWallet Bank
            recipientTransaction.put("timestamp", new Date());
            transaction.set(recipientRef.collection("transactions").document(), recipientTransaction);

            return null; // Commit transaction

        }).addOnSuccessListener(aVoid -> {
            // --- 5. Success Feedback and Splash Screen Display ---
            Log.d(TAG, "Atomic transfer successful.");

            // Navigate to the success splash screen
            showTransferSplash(recipientUserId, amount, description, senderCustomUserId); // Pass custom sender ID

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Atomic transfer failed: ", e);
            Toast.makeText(getContext(), "Transfer failed. Please check network/balance.", Toast.LENGTH_LONG).show();
        });
    }

    private void showTransferSplash(String recipientId, double amount, String description, String senderId) {
        if (getActivity() != null) {
            // Pass transaction details to the splash screen fragment
            TransferSplashFragment splashFragment = new TransferSplashFragment();
            Bundle args = new Bundle();
            args.putString("recipientId", recipientId);
            args.putDouble("amount", amount);
            args.putString("description", description);
            args.putString("senderId", senderId);

            splashFragment.setArguments(args);

            // Replace current fragment with the splash fragment
            FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.fragment_container, splashFragment);
            ft.commit();
        }
    }
}