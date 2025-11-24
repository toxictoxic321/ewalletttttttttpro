package com.example.ewallet;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Displays a beautiful success splash screen after a transfer,
 * showing transaction details and automatically returning to the HomeFragment.
 */
public class TransferSplashFragment extends Fragment {

    private static final String TAG = "TransferSplashFragment";
    private final int SPLASH_DURATION = 7000; // 7 seconds duration as requested

    public TransferSplashFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // We will use a minimal layout for the splash screen
        View view = inflater.inflate(R.layout.fragment_transfer_splash, container, false);

        // Retrieve the transaction details passed from TransferFragment
        Bundle args = getArguments();
        if (args != null) {
            String recipientId = args.getString("recipientId");
            double amount = args.getDouble("amount");
            String description = args.getString("description");
            String senderId = args.getString("senderId");

            // Populate UI details (Requires linking the TextViews inside fragment_transfer_splash.xml)
            TextView title = view.findViewById(R.id.splash_title);
            TextView amountTv = view.findViewById(R.id.splash_amount);
            TextView recipientTv = view.findViewById(R.id.splash_recipient_id);
            TextView sourceTv = view.findViewById(R.id.splash_source);
            TextView timeTv = view.findViewById(R.id.splash_time);

            // Set data
            DecimalFormat df = new DecimalFormat("#,##0.00");
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US);

            title.setText("Transfer Successful!");
            amountTv.setText("$" + df.format(amount));
            recipientTv.setText("Recipient id : " + recipientId);
            sourceTv.setText("Source id: " + senderId);
            timeTv.setText("Time: " + sdf.format(new Date()));
        }

        // --- Auto Navigation After Delay ---
        new Handler().postDelayed(() -> {
            // Check if fragment is still attached before navigating
            if (isAdded() && getActivity() != null) {
                // Navigate back to the HomeFragment (which clears the splash)
                ((MainActivity) getActivity()).loadFragment(new HomeFragment(), false);
            }
        }, SPLASH_DURATION);

        return view;
    }
}