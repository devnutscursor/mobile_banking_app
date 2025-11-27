package com.example.myapplication;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapters.CommissionRateAdapter;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.CommissionRateEntity;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommissionConfigurationActivity extends AppCompatActivity {
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
    private static final String TAG = "CommissionConfig";
    
    private AppDatabase database;
    private SessionManager sessionManager;
    private UserEntity currentUser;
    
    private RecyclerView recyclerView;
    private CommissionRateAdapter adapter;
    private List<CommissionRateEntity> commissionRates = new ArrayList<>();
    private List<OperatorEntity> operators = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_commission_configuration);
        
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
        tvHeaderTitle.setText(getString(R.string.commission_configuration));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        // Setup RecyclerView
        recyclerView = findViewById(R.id.recyclerViewCommissionRates);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CommissionRateAdapter(commissionRates, this::onEdit, this::onDelete);
        recyclerView.setAdapter(adapter);
        
        // Add button
        Button btnAdd = findViewById(R.id.btnAddCommissionRate);
        btnAdd.setOnClickListener(v -> showAddEditDialog(null));
        
        // Load data
        loadOperators();
        loadCommissionRates();
    }
    
    private void loadOperators() {
        new Thread(() -> {
            try {
                operators = database.operatorDao().getByUser(currentUser.getUid());
            } catch (Exception e) {
                Log.e(TAG, "Error loading operators", e);
            }
        }).start();
    }
    
    private void loadCommissionRates() {
        new Thread(() -> {
            try {
                List<CommissionRateEntity> rates = database.commissionRateDao()
                        .getCommissionRatesByUser(currentUser.getUid());
                runOnUiThread(() -> {
                    commissionRates.clear();
                    commissionRates.addAll(rates);
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading commission rates", e);
            }
        }).start();
    }
    
    private void onEdit(CommissionRateEntity rate) {
        showAddEditDialog(rate);
    }
    
    private void onDelete(CommissionRateEntity rate) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Commission Rate")
                .setMessage("Are you sure you want to delete this commission rate?")
                .setPositiveButton(android.R.string.yes, (d, w) -> {
                    new Thread(() -> {
                        try {
                            database.commissionRateDao().deleteCommissionRate(rate.getId());
                            runOnUiThread(() -> {
                                loadCommissionRates();
                                Toast.makeText(this, "Commission rate deleted", Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error deleting commission rate", e);
                        }
                    }).start();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }
    
    private void showAddEditDialog(@Nullable CommissionRateEntity existing) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_commission_rate, null);
        
        Spinner spinnerOperator = dialogView.findViewById(R.id.spinnerOperator);
        EditText etCommissionRate = dialogView.findViewById(R.id.etCommissionRate);
        EditText etTaxRate = dialogView.findViewById(R.id.etTaxRate);
        TextView tvCommissionWithTax = dialogView.findViewById(R.id.tvCommissionWithTax);
        CheckBox cbDeposit = dialogView.findViewById(R.id.cbDeposit);
        CheckBox cbWithdrawal = dialogView.findViewById(R.id.cbWithdrawal);
        Button btnSave = dialogView.findViewById(R.id.btnSave);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        
        // Set dialog title
        if (tvDialogTitle != null) {
            tvDialogTitle.setText(existing == null ? getString(R.string.add_commission_rate) : getString(R.string.edit_commission_rate));
        }
        
        // Setup operator spinner
        new Thread(() -> {
            try {
                List<OperatorEntity> ops = database.operatorDao().getByUser(currentUser.getUid());
                runOnUiThread(() -> {
                    ArrayAdapter<OperatorEntity> operatorAdapter = new ArrayAdapter<OperatorEntity>(
                            this, android.R.layout.simple_spinner_item, ops) {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            View view = super.getView(position, convertView, parent);
                            TextView textView = (TextView) view.findViewById(android.R.id.text1);
                            if (textView != null) {
                                OperatorEntity operator = getItem(position);
                                if (operator != null) {
                                    textView.setText(operator.getName());
                                }
                                textView.setTextColor(getResources().getColor(R.color.black));
                            }
                            return view;
                        }
                        
                        @Override
                        public View getDropDownView(int position, View convertView, ViewGroup parent) {
                            View view = super.getDropDownView(position, convertView, parent);
                            TextView textView = (TextView) view.findViewById(android.R.id.text1);
                            if (textView != null) {
                                OperatorEntity operator = getItem(position);
                                if (operator != null) {
                                    textView.setText(operator.getName());
                                }
                                textView.setTextColor(getResources().getColor(R.color.primary_orange));
                            }
                            return view;
                        }
                    };
                    operatorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerOperator.setAdapter(operatorAdapter);
                    
                    // Select existing operator if editing
                    if (existing != null) {
                        for (int i = 0; i < ops.size(); i++) {
                            if (ops.get(i).getId().equals(existing.getOperatorId())) {
                                spinnerOperator.setSelection(i);
                                break;
                            }
                        }
                        etCommissionRate.setText(String.format(Locale.US, "%.2f", existing.getCommissionRate()));
                        etTaxRate.setText(String.format(Locale.US, "%.2f", existing.getTaxRate()));
                        tvCommissionWithTax.setText(String.format(Locale.US, "%.4f%%", existing.getCommissionRateWithTax()));
                        
                        String types = existing.getTransactionTypes();
                        cbDeposit.setChecked(types != null && types.contains("deposit"));
                        cbWithdrawal.setChecked(types != null && types.contains("withdrawal"));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading operators for spinner", e);
            }
        }).start();
        
        // Calculate commission with tax when rates change
        View.OnFocusChangeListener rateChangeListener = (v, hasFocus) -> {
            if (!hasFocus) {
                calculateAndDisplayCommissionWithTax(etCommissionRate, etTaxRate, tvCommissionWithTax);
            }
        };
        etCommissionRate.setOnFocusChangeListener(rateChangeListener);
        etTaxRate.setOnFocusChangeListener(rateChangeListener);
        
        // Real-time calculation
        etCommissionRate.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                calculateAndDisplayCommissionWithTax(etCommissionRate, etTaxRate, tvCommissionWithTax);
            }
        });
        etTaxRate.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                calculateAndDisplayCommissionWithTax(etCommissionRate, etTaxRate, tvCommissionWithTax);
            }
        });
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        
        // Handle cancel button
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        
        btnSave.setOnClickListener(v -> {
            // Validate
            if (spinnerOperator.getSelectedItem() == null) {
                Toast.makeText(this, getString(R.string.please_select_operator), Toast.LENGTH_SHORT).show();
                return;
            }
            
            String commissionRateStr = etCommissionRate.getText().toString().trim();
            String taxRateStr = etTaxRate.getText().toString().trim();
            
            if (TextUtils.isEmpty(commissionRateStr)) {
                Toast.makeText(this, getString(R.string.please_enter_commission_rate), Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (TextUtils.isEmpty(taxRateStr)) {
                Toast.makeText(this, getString(R.string.please_enter_tax_rate), Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!cbDeposit.isChecked() && !cbWithdrawal.isChecked()) {
                Toast.makeText(this, getString(R.string.select_at_least_one_transaction_type), Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                double commissionRate = Double.parseDouble(commissionRateStr);
                double taxRate = Double.parseDouble(taxRateStr);
                
                if (commissionRate < 0 || commissionRate > 100) {
                    Toast.makeText(this, getString(R.string.commission_rate_range_error), Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (taxRate < 0 || taxRate > 100) {
                    Toast.makeText(this, getString(R.string.tax_rate_range_error), Toast.LENGTH_SHORT).show();
                    return;
                }
                
                OperatorEntity selectedOperator = (OperatorEntity) spinnerOperator.getSelectedItem();
                
                // Build transaction types string
                List<String> types = new ArrayList<>();
                if (cbDeposit.isChecked()) types.add("deposit");
                if (cbWithdrawal.isChecked()) types.add("withdrawal");
                String transactionTypes = TextUtils.join(",", types);
                
                // Create or update commission rate
                CommissionRateEntity rate = existing != null ? existing : new CommissionRateEntity();
                if (existing == null) {
                    rate.setId(currentUser.getUid() + "_" + selectedOperator.getId());
                }
                rate.setUserId(currentUser.getUid());
                rate.setUserRole(currentUser.getRole());
                rate.setOperatorId(selectedOperator.getId());
                rate.setOperatorName(selectedOperator.getName());
                rate.setCommissionRate(commissionRate);
                rate.setTaxRate(taxRate);
                rate.setTransactionTypes(transactionTypes);
                rate.setUpdatedAt(System.currentTimeMillis());
                rate.setNeedsSync(true);
                
                // Save
                new Thread(() -> {
                    try {
                        database.commissionRateDao().insertCommissionRate(rate);
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            loadCommissionRates();
                            Toast.makeText(this, getString(R.string.commission_rate_saved), Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving commission rate", e);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Error saving commission rate: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                }).start();
                
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.please_enter_valid_numbers), Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
    }
    
    private void calculateAndDisplayCommissionWithTax(EditText etCommissionRate, EditText etTaxRate, TextView tvCommissionWithTax) {
        try {
            String commissionStr = etCommissionRate.getText().toString().trim();
            String taxStr = etTaxRate.getText().toString().trim();
            
            if (!TextUtils.isEmpty(commissionStr) && !TextUtils.isEmpty(taxStr)) {
                double commissionRate = Double.parseDouble(commissionStr);
                double taxRate = Double.parseDouble(taxStr);
                double commissionWithTax = commissionRate * (1 + (taxRate / 100.0));
                tvCommissionWithTax.setText(String.format(Locale.US, "%.4f%%", commissionWithTax));
            } else {
                tvCommissionWithTax.setText("0.0000%");
            }
        } catch (NumberFormatException e) {
            tvCommissionWithTax.setText("0.0000%");
        }
    }
}


