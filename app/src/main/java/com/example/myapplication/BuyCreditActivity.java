package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.services.DataSyncService;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.OperatorBalanceHelper;
import com.example.myapplication.utils.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import android.view.View;

public class BuyCreditActivity extends AppCompatActivity {
    private static final String TAG = "BuyCreditActivity";
    
    private TextView tvOperatorBalance;
    private TextView tvCashBalance;
    private EditText etCreditAmount;
    private EditText etCashAmount;
    private CheckBox cbCreditWithoutCash;
    private CheckBox cbCashWithoutOperator;
    private Button btnPurchaseCredit;
    private Button btnPurchaseCash;
    private Spinner spinnerOperator;
    
    private SessionManager sessionManager;
    private AppDatabase database;
    private FirebaseFirestore firestore;
    private UserEntity currentUser;
    private OperatorBalanceHelper balanceHelper;
    private List<OperatorEntity> operators = new ArrayList<>();
    private OperatorEntity selectedOperator;
    
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge display
        com.example.myapplication.utils.EdgeToEdgeHelper.enableEdgeToEdge(this);
        
        setContentView(R.layout.activity_purchase_credit_cash);
        
        // Setup window insets for header
        com.example.myapplication.utils.EdgeToEdgeHelper.setupHeaderInsets(findViewById(R.id.headerLayout), this);
        com.example.myapplication.utils.EdgeToEdgeHelper.setupImeInsetsForRoot(this);
        
        sessionManager = new SessionManager(this);
        database = AppDatabase.getDatabase(this);
        firestore = FirebaseFirestore.getInstance();
        currentUser = sessionManager.getCurrentUser();
        balanceHelper = new OperatorBalanceHelper(database);
        
        if (currentUser == null) {
            Toast.makeText(this, "No active user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        loadOperators();
        loadBalances();
    }
    
    private void loadOperators() {
        new Thread(() -> {
            try {
                if (currentUser != null) {
                    operators = database.operatorDao().getActiveForUser(currentUser.getUid());
                } else {
                    operators = new ArrayList<>();
                }
                
                runOnUiThread(() -> {
                    updateOperatorSpinner();
                    if (!operators.isEmpty()) {
                        selectedOperator = operators.get(0);
                        loadBalances();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading operators", e);
            }
        }).start();
    }
    
    private void updateOperatorSpinner() {
        List<String> operatorNames = new ArrayList<>();
        for (OperatorEntity operator : operators) {
            operatorNames.add(operator.getName());
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            operatorNames
        ) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(getResources().getColor(R.color.black, null));
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(getResources().getColor(R.color.primary_orange, null));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOperator.setAdapter(adapter);
        
        spinnerOperator.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < operators.size()) {
                    selectedOperator = operators.get(position);
                    loadBalances();
                } else {
                    selectedOperator = null;
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedOperator = null;
            }
        });
    }
    
    private void initViews() {
        // Setup header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.buy_credit));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        tvOperatorBalance = findViewById(R.id.tvOperatorBalance);
        tvCashBalance = findViewById(R.id.tvCashBalance);
        etCreditAmount = findViewById(R.id.etCreditAmount);
        etCashAmount = findViewById(R.id.etCashAmount);
        cbCreditWithoutCash = findViewById(R.id.cbCreditWithoutCash);
        cbCashWithoutOperator = findViewById(R.id.cbCashWithoutOperator);
        btnPurchaseCredit = findViewById(R.id.btnPurchaseCredit);
        btnPurchaseCash = findViewById(R.id.btnPurchaseCash);
        spinnerOperator = findViewById(R.id.spinnerOperator);
        
        btnPurchaseCredit.setOnClickListener(v -> purchaseCredit());
        btnPurchaseCash.setOnClickListener(v -> purchaseCash());
        
        // Add thousands separator to amount fields
        com.example.myapplication.utils.NumberFormatter.addThousandsSeparator(etCreditAmount);
        com.example.myapplication.utils.NumberFormatter.addThousandsSeparator(etCashAmount);
        
        // Setup adjust balance button
        Button btnAdjustBalance = findViewById(R.id.btnAdjustBalance);
        if (btnAdjustBalance != null) {
            btnAdjustBalance.setOnClickListener(v -> {
                Intent intent = new Intent(this, BalanceAdjustmentActivity.class);
                startActivity(intent);
            });
        }
    }
    
    private void loadBalances() {
        new Thread(() -> {
            try {
                // Refresh user data from database
                currentUser = database.userDao().getUserById(currentUser.getUid());
                if (currentUser == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }
                
                // Get operator-specific balance
                double operatorBalance = 0.0;
                final OperatorEntity finalSelectedOperator = selectedOperator;
                if (finalSelectedOperator != null && currentUser != null) {
                    operatorBalance = balanceHelper.getBalance(currentUser.getUid(), finalSelectedOperator.getId());
                }
                
                final double finalOperatorBalance = operatorBalance;
                double cashBalance = currentUser.getCashBalance();
                final double finalCashBalance = cashBalance;
                
                runOnUiThread(() -> {
                    String formattedOperatorBalance = com.example.myapplication.utils.NumberFormatter.formatWithThousandsSeparator(finalOperatorBalance);
                    String formattedCashBalance = com.example.myapplication.utils.NumberFormatter.formatWithThousandsSeparator(finalCashBalance);
                    tvOperatorBalance.setText(formattedOperatorBalance + " F");
                    tvCashBalance.setText(formattedCashBalance + " F");
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading balances", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading balances: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void purchaseCredit() {
        String amountStr = etCreditAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            etCreditAmount.setError(getString(R.string.invalid_amount));
            return;
        }
        
        try {
            double amount = com.example.myapplication.utils.NumberFormatter.getNumericValue(amountStr);
            if (amount <= 0) {
                etCreditAmount.setError(getString(R.string.invalid_amount));
                return;
            }
            
            boolean withoutCash = cbCreditWithoutCash.isChecked();
            
            // Check if cash balance is sufficient (if not using checkbox)
            if (!withoutCash) {
                if (currentUser.getCashBalance() < amount) {
                    Toast.makeText(this, getString(R.string.insufficient_cash_balance), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            performCreditPurchase(amount, withoutCash);
        } catch (NumberFormatException e) {
            etCreditAmount.setError(getString(R.string.invalid_amount));
        }
    }
    
    private void purchaseCash() {
        String amountStr = etCashAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            etCashAmount.setError(getString(R.string.invalid_amount));
            return;
        }
        
        try {
            double amount = com.example.myapplication.utils.NumberFormatter.getNumericValue(amountStr);
            if (amount <= 0) {
                etCashAmount.setError(getString(R.string.invalid_amount));
                return;
            }
            
            boolean withoutOperator = cbCashWithoutOperator.isChecked();
            
            // Check if operator balance is sufficient (if not using checkbox)
            if (!withoutOperator) {
                if (selectedOperator == null) {
                    Toast.makeText(this, "Please select an operator", Toast.LENGTH_SHORT).show();
                    return;
                }
                double operatorBalance = balanceHelper.getBalance(currentUser.getUid(), selectedOperator.getId());
                if (operatorBalance < amount) {
                    Toast.makeText(this, getString(R.string.insufficient_credit), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            performCashPurchase(amount, withoutOperator);
        } catch (NumberFormatException e) {
            etCashAmount.setError(getString(R.string.invalid_amount));
        }
    }
    
    private void performCreditPurchase(double amount, boolean withoutCash) {
        new Thread(() -> {
            try {
                // Refresh user data
                UserEntity user = database.userDao().getUserById(currentUser.getUid());
                if (user == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                // Update operator-specific balance (credit) - always increase
                if (selectedOperator == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Please select an operator", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                double currentOperatorBalance = balanceHelper.getBalance(user.getUid(), selectedOperator.getId());
                double newOperatorBalance = currentOperatorBalance + amount;
                balanceHelper.updateBalance(user.getUid(), selectedOperator.getId(), newOperatorBalance);
                
                // Update cash balance - decrease only if checkbox is not checked
                double newCashBalance = user.getCashBalance();
                if (!withoutCash) {
                    newCashBalance = user.getCashBalance() - amount;
                    user.setCashBalance(newCashBalance);
                }
                
                user.setUpdatedAt(System.currentTimeMillis());
                database.userDao().updateUser(user);
                
                // Update current user reference
                currentUser = user;
                
                // Sync to Firestore
                syncBalancesToFirestore(user);
                
                runOnUiThread(() -> {
                    Toast.makeText(this, 
                        getString(R.string.credit_purchased_successfully), 
                        Toast.LENGTH_SHORT).show();
                    etCreditAmount.setText("");
                    cbCreditWithoutCash.setChecked(false);
                    loadBalances();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error purchasing credit", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void performCashPurchase(double amount, boolean withoutOperator) {
        new Thread(() -> {
            try {
                // Refresh user data
                UserEntity user = database.userDao().getUserById(currentUser.getUid());
                if (user == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                // Update cash balance - always increase
                double newCashBalance = user.getCashBalance() + amount;
                user.setCashBalance(newCashBalance);
                
                // Update operator-specific balance (credit) - decrease only if checkbox is not checked
                if (!withoutOperator) {
                    if (selectedOperator == null) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Please select an operator", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                    double currentOperatorBalance = balanceHelper.getBalance(user.getUid(), selectedOperator.getId());
                    double newOperatorBalance = currentOperatorBalance - amount;
                    balanceHelper.updateBalance(user.getUid(), selectedOperator.getId(), newOperatorBalance);
                }
                
                user.setUpdatedAt(System.currentTimeMillis());
                database.userDao().updateUser(user);
                
                // Update current user reference
                currentUser = user;
                
                // Sync to Firestore
                syncBalancesToFirestore(user);
                
                runOnUiThread(() -> {
                    Toast.makeText(this, 
                        getString(R.string.cash_purchased_successfully), 
                        Toast.LENGTH_SHORT).show();
                    etCashAmount.setText("");
                    cbCashWithoutOperator.setChecked(false);
                    loadBalances();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error purchasing cash", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void syncBalancesToFirestore(UserEntity user) {
        try {
            // Sync cash balance (still global per user)
            Map<String, Object> updates = new HashMap<>();
            updates.put("cashBalance", user.getCashBalance());
            updates.put("updatedAt", com.google.firebase.Timestamp.now());
            
            firestore.collection("users").document(user.getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Cash balance synced to Firestore");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to sync balances to Firestore", e);
                    });
            
            // Note: Operator-specific balances are stored in operator_balances collection
            // They should be synced separately if needed
        } catch (Exception e) {
            Log.e(TAG, "Error syncing balances to Firestore", e);
        }
    }
}
