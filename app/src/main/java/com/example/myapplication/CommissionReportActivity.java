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
    
    private Spinner spinnerPeriod, spinnerMonth, spinnerYear;
    private TextView tvTotalCommission, tvCommissionWithoutTax, tvTaxAmount, tvCommissionWithTax;
    private TextView tvReportTitle;
    private Button btnGenerateReport;
    
    private String selectedPeriod = "daily"; // daily, monthly, yearly
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_commission_report);
        
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
        tvTotalCommission = findViewById(R.id.tvTotalCommission);
        tvCommissionWithoutTax = findViewById(R.id.tvCommissionWithoutTax);
        tvTaxAmount = findViewById(R.id.tvTaxAmount);
        tvCommissionWithTax = findViewById(R.id.tvCommissionWithTax);
        tvReportTitle = findViewById(R.id.tvReportTitle);
        btnGenerateReport = findViewById(R.id.btnGenerateReport);
        
        // Setup period spinner
        String[] periods = {"Daily", "Monthly", "Yearly"};
        ArrayAdapter<String> periodAdapter = createOrangeSpinnerAdapter(periods);
        spinnerPeriod.setAdapter(periodAdapter);
        spinnerPeriod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPeriod = periods[position].toLowerCase();
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
        if ("daily".equals(selectedPeriod)) {
            spinnerMonth.setVisibility(View.VISIBLE);
            spinnerYear.setVisibility(View.VISIBLE);
            tvReportTitle.setText(getString(R.string.daily_commission));
        } else if ("monthly".equals(selectedPeriod)) {
            spinnerMonth.setVisibility(View.VISIBLE);
            spinnerYear.setVisibility(View.VISIBLE);
            tvReportTitle.setText(getString(R.string.monthly_commission));
        } else {
            spinnerMonth.setVisibility(View.GONE);
            spinnerYear.setVisibility(View.VISIBLE);
            tvReportTitle.setText(getString(R.string.yearly_commission));
        }
    }
    
    private void generateReport() {
        new Thread(() -> {
            try {
                Calendar cal = Calendar.getInstance();
                int year = Integer.parseInt(spinnerYear.getSelectedItem().toString());
                int month = spinnerMonth.getSelectedItemPosition() + 1; // 1-based
                int day = cal.get(Calendar.DAY_OF_MONTH);
                
                Double totalCommission = 0.0;
                Double commissionWithoutTax = 0.0;
                Double taxAmount = 0.0;
                
                if ("daily".equals(selectedPeriod)) {
                    totalCommission = database.commissionDao()
                            .getTotalCommissionByDay(currentUser.getUid(), year, month, day);
                    if (totalCommission == null) totalCommission = 0.0;
                    
                    // For daily, we need to calculate from individual commissions
                    List<CommissionEntity> commissions = database.commissionDao()
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
                }
                
                final Double finalTotal = totalCommission;
                final Double finalWithoutTax = commissionWithoutTax;
                final Double finalTax = taxAmount;
                
                runOnUiThread(() -> {
                    tvTotalCommission.setText(String.format(Locale.US, "%.2f", finalTotal));
                    tvCommissionWithoutTax.setText(String.format(Locale.US, "%.2f", finalWithoutTax));
                    tvTaxAmount.setText(String.format(Locale.US, "%.2f", finalTax));
                    tvCommissionWithTax.setText(String.format(Locale.US, "%.2f", finalTotal));
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
}


