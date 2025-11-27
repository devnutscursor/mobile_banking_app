package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapters.BalanceAdjustmentAdapter;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.BalanceAdjustmentEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BalanceAdjustmentActivity extends AppCompatActivity {
    private static final String TAG = "BalanceAdjustmentActivity";
    
    private TextView tvCurrentOperatorBalance;
    private TextView tvCurrentCashBalance;
    private RadioGroup rgAdjustmentType;
    private RadioButton rbOperatorBalance;
    private RadioButton rbCashBalance;
    private EditText etAdjustmentAmount;
    private EditText etReason;
    private Button btnApplyAdjustment;
    private RecyclerView rvAdjustmentHistory;
    
    private AppDatabase database;
    private SessionManager sessionManager;
    private FirebaseFirestore firestore;
    private UserEntity currentUser;
    private BalanceAdjustmentAdapter adapter;
    private List<BalanceAdjustmentEntity> adjustmentHistory = new ArrayList<>();
    
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
        setContentView(R.layout.activity_balance_adjustment);
        
        database = AppDatabase.getDatabase(this);
        sessionManager = new SessionManager(this);
        firestore = FirebaseFirestore.getInstance();
        currentUser = sessionManager.getCurrentUser();
        
        if (currentUser == null) {
            Toast.makeText(this, "No active user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        loadBalances();
        loadAdjustmentHistory();
    }
    
    private void initViews() {
        // Header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.adjust_balance));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        tvCurrentOperatorBalance = findViewById(R.id.tvCurrentOperatorBalance);
        tvCurrentCashBalance = findViewById(R.id.tvCurrentCashBalance);
        rgAdjustmentType = findViewById(R.id.rgAdjustmentType);
        rbOperatorBalance = findViewById(R.id.rbOperatorBalance);
        rbCashBalance = findViewById(R.id.rbCashBalance);
        etAdjustmentAmount = findViewById(R.id.etAdjustmentAmount);
        etReason = findViewById(R.id.etReason);
        btnApplyAdjustment = findViewById(R.id.btnApplyAdjustment);
        rvAdjustmentHistory = findViewById(R.id.rvAdjustmentHistory);
        
        // Setup RecyclerView for history
        rvAdjustmentHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BalanceAdjustmentAdapter(this, adjustmentHistory);
        rvAdjustmentHistory.setAdapter(adapter);
        
        // Default to operator balance
        rbOperatorBalance.setChecked(true);
        
        // Apply adjustment button
        btnApplyAdjustment.setOnClickListener(v -> applyAdjustment());
    }
    
    private void loadBalances() {
        new Thread(() -> {
            try {
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
                    tvCurrentOperatorBalance.setText(currencyFormat.format(operatorBalance));
                    tvCurrentCashBalance.setText(currencyFormat.format(cashBalance));
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading balances", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading balances: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void loadAdjustmentHistory() {
        new Thread(() -> {
            try {
                List<BalanceAdjustmentEntity> adjustments = database.balanceAdjustmentDao()
                        .getAdjustmentsByUser(currentUser.getUid());
                
                runOnUiThread(() -> {
                    adjustmentHistory.clear();
                    adjustmentHistory.addAll(adjustments);
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading adjustment history", e);
            }
        }).start();
    }
    
    private void applyAdjustment() {
        String amountStr = etAdjustmentAmount.getText().toString().trim();
        String reason = etReason.getText().toString().trim();
        
        if (TextUtils.isEmpty(amountStr)) {
            etAdjustmentAmount.setError(getString(R.string.invalid_amount));
            return;
        }
        
        if (TextUtils.isEmpty(reason)) {
            etReason.setError(getString(R.string.required_fields_missing));
            return;
        }
        
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount == 0) {
                etAdjustmentAmount.setError(getString(R.string.invalid_amount));
                return;
            }
            
            String adjustmentType = rbOperatorBalance.isChecked() ? "operator" : "cash";
            
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.adjust_balance))
                    .setMessage(getString(R.string.confirm_adjustment))
                    .setPositiveButton(android.R.string.yes, (d, w) -> {
                        performAdjustment(amount, adjustmentType, reason);
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .show();
        } catch (NumberFormatException e) {
            etAdjustmentAmount.setError(getString(R.string.invalid_amount));
        }
    }
    
    private void performAdjustment(double amount, String adjustmentType, String reason) {
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
                
                double balanceBefore;
                double balanceAfter;
                
                // Create adjustment record
                BalanceAdjustmentEntity adjustment = new BalanceAdjustmentEntity();
                adjustment.setUserId(user.getUid());
                adjustment.setAdjustmentType(adjustmentType);
                adjustment.setAmount(amount);
                adjustment.setReason(reason);
                adjustment.setAdjustedBy(user.getUid());
                
                // Apply adjustment
                if ("operator".equals(adjustmentType)) {
                    balanceBefore = user.getVirtualCredit();
                    balanceAfter = balanceBefore + amount;
                    user.setVirtualCredit(balanceAfter);
                    user.setCreditUpdatedAt(System.currentTimeMillis());
                } else {
                    balanceBefore = user.getCashBalance();
                    balanceAfter = balanceBefore + amount;
                    user.setCashBalance(balanceAfter);
                }
                
                adjustment.setBalanceBefore(balanceBefore);
                adjustment.setBalanceAfter(balanceAfter);
                adjustment.setUpdatedAt(System.currentTimeMillis());
                
                // Save adjustment and update user
                database.balanceAdjustmentDao().insertAdjustment(adjustment);
                user.setUpdatedAt(System.currentTimeMillis());
                database.userDao().updateUser(user);
                
                // Update current user reference
                currentUser = user;
                
                // Sync to Firestore
                syncAdjustmentToFirestore(adjustment);
                syncBalancesToFirestore(user);
                
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.balance_adjusted_successfully), Toast.LENGTH_SHORT).show();
                    etAdjustmentAmount.setText("");
                    etReason.setText("");
                    loadBalances();
                    loadAdjustmentHistory();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error applying adjustment", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void syncAdjustmentToFirestore(BalanceAdjustmentEntity adjustment) {
        try {
            Map<String, Object> adjustmentData = new HashMap<>();
            adjustmentData.put("id", adjustment.getId());
            adjustmentData.put("userId", adjustment.getUserId());
            adjustmentData.put("adjustmentType", adjustment.getAdjustmentType());
            adjustmentData.put("amount", adjustment.getAmount());
            adjustmentData.put("balanceBefore", adjustment.getBalanceBefore());
            adjustmentData.put("balanceAfter", adjustment.getBalanceAfter());
            adjustmentData.put("reason", adjustment.getReason());
            adjustmentData.put("adjustedBy", adjustment.getAdjustedBy());
            adjustmentData.put("createdAt", com.google.firebase.Timestamp.now());
            adjustmentData.put("updatedAt", com.google.firebase.Timestamp.now());
            
            firestore.collection("balance_adjustments").document(adjustment.getId())
                    .set(adjustmentData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Adjustment synced to Firestore: " + adjustment.getId());
                        new Thread(() -> {
                            adjustment.setNeedsSync(false);
                            adjustment.setLastSyncAt(System.currentTimeMillis());
                            database.balanceAdjustmentDao().updateAdjustment(adjustment);
                        }).start();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to sync adjustment to Firestore", e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error syncing adjustment to Firestore", e);
        }
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



