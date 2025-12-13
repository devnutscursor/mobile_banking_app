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
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.services.DataSyncService;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
    
    private SessionManager sessionManager;
    private AppDatabase database;
    private FirebaseFirestore firestore;
    private UserEntity currentUser;
    
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
        setContentView(R.layout.activity_purchase_credit_cash);
        
        sessionManager = new SessionManager(this);
        database = AppDatabase.getDatabase(this);
        firestore = FirebaseFirestore.getInstance();
        currentUser = sessionManager.getCurrentUser();
        
        if (currentUser == null) {
            Toast.makeText(this, "No active user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        loadBalances();
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
                
                double operatorBalance = currentUser.getVirtualCredit();
                double cashBalance = currentUser.getCashBalance();
                
                NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());
                
                runOnUiThread(() -> {
                    tvOperatorBalance.setText(currencyFormat.format(operatorBalance));
                    tvCashBalance.setText(currencyFormat.format(cashBalance));
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
                if (currentUser.getVirtualCredit() < amount) {
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
                
                // Update operator balance (credit) - always increase
                double newOperatorBalance = user.getVirtualCredit() + amount;
                user.setVirtualCredit(newOperatorBalance);
                user.setCreditUpdatedAt(System.currentTimeMillis());
                
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
                
                // Update operator balance (credit) - decrease only if checkbox is not checked
                double newOperatorBalance = user.getVirtualCredit();
                if (!withoutOperator) {
                    newOperatorBalance = user.getVirtualCredit() - amount;
                    user.setVirtualCredit(newOperatorBalance);
                    user.setCreditUpdatedAt(System.currentTimeMillis());
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
            Map<String, Object> updates = new HashMap<>();
            updates.put("virtualCredit", user.getVirtualCredit());
            updates.put("cashBalance", user.getCashBalance());
            updates.put("creditUpdatedAt", user.getCreditUpdatedAt());
            updates.put("updatedAt", com.google.firebase.Timestamp.now());
            
            firestore.collection("users").document(user.getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Balances synced to Firestore");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to sync balances to Firestore", e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error syncing balances to Firestore", e);
        }
    }
}
