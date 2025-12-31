package com.example.myapplication;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.CommissionEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CommissionReportActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        LanguageManager languageManager = LanguageManager.getInstance(newBase);
        String language = languageManager.getCurrentLanguage();
        
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
        
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }
    private static final String TAG = "CommissionReport";
    
    private AppDatabase database;
    private SessionManager sessionManager;
    private UserEntity currentUser;
    
    private Spinner spinnerPeriod, spinnerMonth, spinnerYear, spinnerDay;
    private TextView tvTotalCommission, tvCommissionWithoutTax, tvTaxAmount, tvCommissionWithTax;
    private TextView tvReportTitle;
    private Button btnGenerateReport;
    private android.widget.LinearLayout llBreakdownByOperator, llBreakdownByAction;
    
    private String selectedPeriod = "daily"; // daily, monthly, yearly
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge display
        com.example.myapplication.utils.EdgeToEdgeHelper.enableEdgeToEdge(this);
        
        setContentView(R.layout.activity_commission_report);
        
        // Setup window insets for header
        com.example.myapplication.utils.EdgeToEdgeHelper.setupHeaderInsets(findViewById(R.id.headerLayout), this);
        com.example.myapplication.utils.EdgeToEdgeHelper.setupImeInsetsForRoot(this);
        
        database = AppDatabase.getDatabase(this);
        sessionManager = new SessionManager(this);
        currentUser = sessionManager.getUserFromSession();
        
        if (currentUser == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Setup header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.commission_reports));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        // Initialize views
        spinnerPeriod = findViewById(R.id.spinnerPeriod);
        spinnerMonth = findViewById(R.id.spinnerMonth);
        spinnerYear = findViewById(R.id.spinnerYear);
        spinnerDay = findViewById(R.id.spinnerDay);
        tvTotalCommission = findViewById(R.id.tvTotalCommission);
        tvCommissionWithoutTax = findViewById(R.id.tvCommissionWithoutTax);
        tvTaxAmount = findViewById(R.id.tvTaxAmount);
        tvCommissionWithTax = findViewById(R.id.tvCommissionWithTax);
        tvReportTitle = findViewById(R.id.tvReportTitle);
        btnGenerateReport = findViewById(R.id.btnGenerateReport);
        llBreakdownByOperator = findViewById(R.id.llBreakdownByOperator);
        llBreakdownByAction = findViewById(R.id.llBreakdownByAction);
        
        // Setup period spinner
        String[] periods = {
            getString(R.string.daily),
            getString(R.string.monthly),
            getString(R.string.yearly)
        };
        ArrayAdapter<String> periodAdapter = createOrangeSpinnerAdapter(periods);
        spinnerPeriod.setAdapter(periodAdapter);
        spinnerPeriod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Map translated strings back to period keys
                String[] periodKeys = {"daily", "monthly", "yearly"};
                selectedPeriod = periodKeys[position];
                updateUIForPeriod();
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // Setup month spinner
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        ArrayAdapter<String> monthAdapter = createOrangeSpinnerAdapter(months);
        spinnerMonth.setAdapter(monthAdapter);
        spinnerMonth.setSelection(Calendar.getInstance().get(Calendar.MONTH));
        
        // Setup year spinner
        List<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int i = currentYear; i >= currentYear - 5; i--) {
            years.add(String.valueOf(i));
        }
        ArrayAdapter<String> yearAdapter = createOrangeSpinnerAdapter(
                years.toArray(new String[0]));
        spinnerYear.setAdapter(yearAdapter);
        
        // Setup day spinner (for daily reports)
        if (spinnerDay != null) {
            List<String> days = new ArrayList<>();
            for (int i = 1; i <= 31; i++) {
                days.add(String.valueOf(i));
            }
            ArrayAdapter<String> dayAdapter = createOrangeSpinnerAdapter(
                    days.toArray(new String[0]));
            spinnerDay.setAdapter(dayAdapter);
            spinnerDay.setSelection(Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1);
        }
        
        // Generate report button
        btnGenerateReport.setOnClickListener(v -> generateReport());
        
        // Generate report for current period by default
        updateUIForPeriod();
        generateReport();
    }
    
    private ArrayAdapter<String> createOrangeSpinnerAdapter(String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                items
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                tintTextView(view);
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                tintTextView(view);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }
    
    private void tintTextView(View view) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(ContextCompat.getColor(
                    this,
                    R.color.primary_orange
            ));
        } else {
            TextView tv = view.findViewById(android.R.id.text1);
            if (tv != null) {
                tv.setTextColor(ContextCompat.getColor(this, R.color.primary_orange));
            }
        }
    }
    
    private void updateUIForPeriod() {
        TextView tvSelectDay = findViewById(R.id.tvSelectDay);
        if ("daily".equals(selectedPeriod)) {
            spinnerMonth.setVisibility(View.VISIBLE);
            spinnerYear.setVisibility(View.VISIBLE);
            if (spinnerDay != null) spinnerDay.setVisibility(View.VISIBLE);
            if (tvSelectDay != null) tvSelectDay.setVisibility(View.VISIBLE);
            tvReportTitle.setText(getString(R.string.daily_commission));
        } else if ("monthly".equals(selectedPeriod)) {
            spinnerMonth.setVisibility(View.VISIBLE);
            spinnerYear.setVisibility(View.VISIBLE);
            if (spinnerDay != null) spinnerDay.setVisibility(View.GONE);
            if (tvSelectDay != null) tvSelectDay.setVisibility(View.GONE);
            tvReportTitle.setText(getString(R.string.monthly_commission));
        } else {
            spinnerMonth.setVisibility(View.GONE);
            spinnerYear.setVisibility(View.VISIBLE);
            if (spinnerDay != null) spinnerDay.setVisibility(View.GONE);
            if (tvSelectDay != null) tvSelectDay.setVisibility(View.GONE);
            tvReportTitle.setText(getString(R.string.yearly_commission));
        }
    }
    
    private void generateReport() {
        new Thread(() -> {
            try {
                Calendar cal = Calendar.getInstance();
                int year = Integer.parseInt(spinnerYear.getSelectedItem().toString());
                int month = spinnerMonth.getSelectedItemPosition() + 1; // 1-based
                int day = "daily".equals(selectedPeriod) && spinnerDay != null && spinnerDay.getSelectedItem() != null ? 
                    Integer.parseInt(spinnerDay.getSelectedItem().toString()) : 
                    cal.get(Calendar.DAY_OF_MONTH);
                
                Double totalCommission = 0.0;
                Double commissionWithoutTax = 0.0;
                Double taxAmount = 0.0;
                List<CommissionEntity> commissions = new ArrayList<>();
                
                if ("daily".equals(selectedPeriod)) {
                    totalCommission = database.commissionDao()
                            .getTotalCommissionByDay(currentUser.getUid(), year, month, day);
                    if (totalCommission == null) totalCommission = 0.0;
                    
                    // Get individual commissions for breakdown
                    commissions = database.commissionDao()
                            .getCommissionsByDay(currentUser.getUid(), year, month, day);
                    for (CommissionEntity comm : commissions) {
                        commissionWithoutTax += comm.getCommissionAmount();
                        taxAmount += comm.getTaxAmount();
                    }
                } else if ("monthly".equals(selectedPeriod)) {
                    totalCommission = database.commissionDao()
                            .getTotalCommissionByMonth(currentUser.getUid(), year, month);
                    commissionWithoutTax = database.commissionDao()
                            .getTotalCommissionWithoutTaxByMonth(currentUser.getUid(), year, month);
                    taxAmount = database.commissionDao()
                            .getTotalTaxByMonth(currentUser.getUid(), year, month);
                    
                    if (totalCommission == null) totalCommission = 0.0;
                    if (commissionWithoutTax == null) commissionWithoutTax = 0.0;
                    if (taxAmount == null) taxAmount = 0.0;
                    
                    // Get individual commissions for breakdown
                    commissions = database.commissionDao()
                            .getCommissionsByMonth(currentUser.getUid(), year, month);
                } else { // yearly
                    totalCommission = database.commissionDao()
                            .getTotalCommissionByYear(currentUser.getUid(), year);
                    commissionWithoutTax = database.commissionDao()
                            .getTotalCommissionWithoutTaxByYear(currentUser.getUid(), year);
                    taxAmount = database.commissionDao()
                            .getTotalTaxByYear(currentUser.getUid(), year);
                    
                    if (totalCommission == null) totalCommission = 0.0;
                    if (commissionWithoutTax == null) commissionWithoutTax = 0.0;
                    if (taxAmount == null) taxAmount = 0.0;
                    
                    // Get individual commissions for breakdown
                    commissions = database.commissionDao()
                            .getCommissionsByYear(currentUser.getUid(), year);
                }
                
                // Calculate breakdown by Operator and by Action
                java.util.Map<String, CommissionBreakdown> operatorBreakdown = new java.util.HashMap<>();
                java.util.Map<String, CommissionBreakdown> actionBreakdown = new java.util.HashMap<>();
                
                for (CommissionEntity comm : commissions) {
                    // By Operator
                    String operatorKey = comm.getOperatorName() != null ? comm.getOperatorName() : "Unknown";
                    CommissionBreakdown opBreakdown = operatorBreakdown.getOrDefault(operatorKey, new CommissionBreakdown());
                    opBreakdown.commissionAmount += comm.getCommissionAmount();
                    opBreakdown.taxAmount += comm.getTaxAmount();
                    opBreakdown.totalCommission += comm.getTotalCommission();
                    operatorBreakdown.put(operatorKey, opBreakdown);
                    
                    // By Action (Transaction Type)
                    String actionKey = comm.getTransactionType() != null ? comm.getTransactionType() : "Unknown";
                    CommissionBreakdown actBreakdown = actionBreakdown.getOrDefault(actionKey, new CommissionBreakdown());
                    actBreakdown.commissionAmount += comm.getCommissionAmount();
                    actBreakdown.taxAmount += comm.getTaxAmount();
                    actBreakdown.totalCommission += comm.getTotalCommission();
                    actionBreakdown.put(actionKey, actBreakdown);
                }
                
                final Double finalTotal = totalCommission;
                final Double finalWithoutTax = commissionWithoutTax;
                final Double finalTax = taxAmount;
                final java.util.Map<String, CommissionBreakdown> finalOperatorBreakdown = operatorBreakdown;
                final java.util.Map<String, CommissionBreakdown> finalActionBreakdown = actionBreakdown;
                
                runOnUiThread(() -> {
                    tvTotalCommission.setText(String.format(Locale.US, "%.2f", finalTotal));
                    tvCommissionWithoutTax.setText(String.format(Locale.US, "%.2f", finalWithoutTax));
                    tvTaxAmount.setText(String.format(Locale.US, "%.2f", finalTax));
                    tvCommissionWithTax.setText(String.format(Locale.US, "%.2f", finalTotal));
                    
                    // Display breakdowns
                    displayBreakdownByOperator(finalOperatorBreakdown);
                    displayBreakdownByAction(finalActionBreakdown);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating report", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error generating report: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private static class CommissionBreakdown {
        double commissionAmount = 0.0;
        double taxAmount = 0.0;
        double totalCommission = 0.0;
    }
    
    private void displayBreakdownByOperator(java.util.Map<String, CommissionBreakdown> breakdown) {
        if (llBreakdownByOperator == null) return;
        
        llBreakdownByOperator.removeAllViews();
        
        if (breakdown.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(getString(R.string.no_data_available));
            emptyView.setTextColor(getResources().getColor(R.color.text_secondary, null));
            emptyView.setPadding(16, 16, 16, 16);
            llBreakdownByOperator.addView(emptyView);
            return;
        }
        
        for (java.util.Map.Entry<String, CommissionBreakdown> entry : breakdown.entrySet()) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);
            
            TextView operatorName = new TextView(this);
            operatorName.setText(entry.getKey());
            operatorName.setTextColor(getResources().getColor(R.color.text_primary, null));
            operatorName.setTextSize(14);
            operatorName.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            
            TextView commissionValue = new TextView(this);
            commissionValue.setText(String.format(Locale.US, "%.2f F", entry.getValue().totalCommission));
            commissionValue.setTextColor(getResources().getColor(R.color.primary_orange, null));
            commissionValue.setTextSize(14);
            commissionValue.setTypeface(null, android.graphics.Typeface.BOLD);
            commissionValue.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            
            row.addView(operatorName);
            row.addView(commissionValue);
            llBreakdownByOperator.addView(row);
        }
    }
    
    private void displayBreakdownByAction(java.util.Map<String, CommissionBreakdown> breakdown) {
        if (llBreakdownByAction == null) return;
        
        llBreakdownByAction.removeAllViews();
        
        if (breakdown.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(getString(R.string.no_data_available));
            emptyView.setTextColor(getResources().getColor(R.color.text_secondary, null));
            emptyView.setPadding(16, 16, 16, 16);
            llBreakdownByAction.addView(emptyView);
            return;
        }
        
        for (java.util.Map.Entry<String, CommissionBreakdown> entry : breakdown.entrySet()) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);
            
            TextView actionName = new TextView(this);
            String actionDisplay = entry.getKey();
            // Capitalize first letter
            if (actionDisplay != null && !actionDisplay.isEmpty()) {
                actionDisplay = actionDisplay.substring(0, 1).toUpperCase() + 
                    (actionDisplay.length() > 1 ? actionDisplay.substring(1) : "");
            }
            actionName.setText(actionDisplay);
            actionName.setTextColor(getResources().getColor(R.color.text_primary, null));
            actionName.setTextSize(14);
            actionName.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            
            TextView commissionValue = new TextView(this);
            commissionValue.setText(String.format(Locale.US, "%.2f F", entry.getValue().totalCommission));
            commissionValue.setTextColor(getResources().getColor(R.color.primary_orange, null));
            commissionValue.setTextSize(14);
            commissionValue.setTypeface(null, android.graphics.Typeface.BOLD);
            commissionValue.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            
            row.addView(actionName);
            row.addView(commissionValue);
            llBreakdownByAction.addView(row);
        }
    }
}


