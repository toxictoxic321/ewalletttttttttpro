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

// --- CHART LIBRARY IMPORTS (REQUIRED) ---
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
// NOTE: IndexAxisFormatter and its import have been intentionally REMOVED for stability.
// -----------------------------

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fragment to display user's balance history (Bar Chart) and transaction summaries.
 * Uses MPAndroidChart for professional rendering.
 */
public class StatsFragment extends Fragment {

    private static final String TAG = "StatsFragment";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUid;

    private BarChart chart; // Link to the BarChart view
    private LinearLayout summaryListContainer;

    public StatsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stats, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        // Link UI components
        chart = view.findViewById(R.id.balance_chart); // Link the BarChart
        summaryListContainer = view.findViewById(R.id.summary_list_container);

        // Load all transactions for analysis
        if (currentUid != null) {
            loadAllTransactions();
        } else {
            Toast.makeText(getContext(), "User not logged in.", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    private void loadAllTransactions() {
        // Fetch ALL transactions, ordered by timestamp, to rebuild history
        db.collection("users").document(currentUid).collection("transactions")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<DocumentSnapshot> documents = task.getResult().getDocuments();

                        processTransactionsForChart(documents);
                        processTransactionsForSummary(documents);
                    } else {
                        Log.e(TAG, "Error fetching transactions for stats.", task.getException());
                        Toast.makeText(getContext(), "Failed to load transaction history.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --- 1. Chart Data Processing and Setup (Aesthetics Focus) ---

    private void processTransactionsForChart(List<DocumentSnapshot> transactionDocuments) {
        if (transactionDocuments.isEmpty()) return;

        // TreeMap ensures balances are grouped and sorted by date
        TreeMap<String, Double> dailyBalances = new TreeMap<>();
        double currentBalance = 0.0;

        // Use short day names (EEE) for the X-axis mapping, though we won't label them directly
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE", Locale.US);

        for (DocumentSnapshot document : transactionDocuments) {
            Double amount = document.getDouble("amount");
            String type = document.getString("type");
            Date date = document.getDate("timestamp");

            if (amount != null && date != null && type != null) {
                // Determine transaction sign (+/-)
                double transactionValue = amount;

                // If it's a cash OUT, it reduces the balance
                if (type.equals("Loan Repayment") || type.equals("Transfer (Sent)")) {
                    transactionValue *= -1;
                }

                currentBalance += transactionValue;

                // Store the cumulative balance for this date
                String dateKey = dateFormat.format(date);
                dailyBalances.put(dateKey, currentBalance);
            }
        }

        // Setup the chart with the processed daily balances
        setupBarChart(dailyBalances);
    }

    private void setupBarChart(TreeMap<String, Double> dailyBalances) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> dates = new ArrayList<>();

        int i = 0;
        // Convert the TreeMap data into chart entries
        for (Map.Entry<String, Double> entry : dailyBalances.entrySet()) {
            // X-axis index is 'i', Y-axis value is 'balance'
            entries.add(new BarEntry(i, entry.getValue().floatValue()));
            dates.add(entry.getKey()); // Dates list is collected but won't be explicitly displayed on X-Axis
            i++;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Daily Balance ($)");
        dataSet.setColor(Color.parseColor("#8A63D2")); // Purple color
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(10f);

        // Remove legend and grid lines for a cleaner look
        chart.getLegend().setEnabled(false);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getXAxis().setDrawGridLines(false);
        chart.setDrawGridBackground(false);

        // Get Data
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f); // Thin bars for the desired aesthetic

        chart.setData(barData);

        // --- X-Axis Customization ---
        // We set the XAxis to accept the number index (0, 1, 2...) since IndexAxisFormatter is removed.
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.WHITE);
        // Displaying dates without the formatter would require custom IAxisValueFormatter, which we skip.

        // --- Y-Axis Customization ---
        chart.getAxisLeft().setAxisMinimum(0f); // Start Y-axis at zero
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisLeft().setTextSize(10f);
        chart.getAxisRight().setEnabled(false); // Hide right Y-axis

        chart.getDescription().setEnabled(false); // Hide description label
        chart.animateY(1000);
        chart.invalidate(); // Refresh the chart view
    }

    // --- 2. Summary Data Processing (Unchanged) ---

    private void processTransactionsForSummary(List<DocumentSnapshot> transactionDocuments) {
        Map<String, Double> summary = new HashMap<>();

        for (DocumentSnapshot document : transactionDocuments) {
            String description = document.getString("description");
            Double amount = document.getDouble("amount");
            String type = document.getString("type");

            if (amount != null && description != null && type != null) {
                if (type.equals("Loan Repayment") || type.equals("Transfer (Sent)")) {

                    Double currentTotal = summary.get(description);
                    if (currentTotal == null) {
                        currentTotal = 0.0;
                    }
                    summary.put(description, currentTotal + amount);
                }
            }
        }

        displaySummary(summary);
    }

    private void displaySummary(Map<String, Double> summary) {
        summaryListContainer.removeAllViews();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        if (summary.isEmpty()) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("No expense categories found.");
            emptyText.setTextColor(Color.parseColor("#A0A0A0"));
            summaryListContainer.addView(emptyText);
            return;
        }

        for (Map.Entry<String, Double> entry : summary.entrySet()) {
            String category = entry.getKey();
            double totalAmount = entry.getValue();

            // --- Dynamically create the Summary Item Layout ---
            LinearLayout itemLayout = new LinearLayout(getContext());
            itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setPadding(0, 20, 0, 20);

            TextView categoryTv = new TextView(getContext());
            categoryTv.setText(category);
            categoryTv.setTextColor(Color.WHITE);
            categoryTv.setTextSize(16f);
            categoryTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView amountTv = new TextView(getContext());
            String formattedAmount = "- $" + df.format(totalAmount);
            amountTv.setText(formattedAmount);
            amountTv.setTextColor(Color.parseColor("#FF5555"));
            amountTv.setTextSize(16f);
            amountTv.setTypeface(null, android.graphics.Typeface.BOLD);

            itemLayout.addView(categoryTv);
            itemLayout.addView(amountTv);
            summaryListContainer.addView(itemLayout);
        }
    }
}