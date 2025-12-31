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
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.OperatorBalanceHelper;
import com.example.myapplication.utils.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BalanceAdjustmentActivity extends AppCompatActivity {
    private static final String TAG = "BalanceAdjustmentActivity";
    
    private TextView tvCurrentOperatorBalance;
    private TextView tvCurrentCashBalance;
    private TextView tvCurrentTotalCredit;
    private RadioGroup rgAdjustmentType;
    private RadioButton rbOperatorBalance;
    private RadioButton rbCashBalance;
    private EditText etAdjustmentAmount;
    private EditText etReason;
    private Button btnApplyAdjustment;
    private RecyclerView rvAdjustmentHistory;
    private Spinner spinnerOperator;
    
    private AppDatabase database;
    private SessionManager sessionManager;
    private FirebaseFirestore firestore;
    private UserEntity currentUser;
    private BalanceAdjustmentAdapter adapter;
    private List<BalanceAdjustmentEntity> adjustmentHistory = new ArrayList<>();
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
        
        setContentView(R.layout.activity_balance_adjustment);
        
        // Setup window insets for header
        com.example.myapplication.utils.EdgeToEdgeHelper.setupHeaderInsets(findViewById(R.id.headerLayout), this);
        com.example.myapplication.utils.EdgeToEdgeHelper.setupImeInsetsForRoot(this);
        
        database = AppDatabase.getDatabase(this);
        sessionManager = new SessionManager(this);
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
        loadAdjustmentHistory();
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
        // Header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.adjust_balance));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        tvCurrentOperatorBalance = findViewById(R.id.tvCurrentOperatorBalance);
        tvCurrentCashBalance = findViewById(R.id.tvCurrentCashBalance);
        tvCurrentTotalCredit = findViewById(R.id.tvCurrentTotalCredit);
        rgAdjustmentType = findViewById(R.id.rgAdjustmentType);
        rbOperatorBalance = findViewById(R.id.rbOperatorBalance);
        rbCashBalance = findViewById(R.id.rbCashBalance);
        etAdjustmentAmount = findViewById(R.id.etAdjustmentAmount);
        etReason = findViewById(R.id.etReason);
        btnApplyAdjustment = findViewById(R.id.btnApplyAdjustment);
        rvAdjustmentHistory = findViewById(R.id.rvAdjustmentHistory);
        spinnerOperator = findViewById(R.id.spinnerOperator);
        
        // Setup RecyclerView for history
        rvAdjustmentHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BalanceAdjustmentAdapter(this, adjustmentHistory);
        rvAdjustmentHistory.setAdapter(adapter);
        
        // Default to operator balance
        rbOperatorBalance.setChecked(true);
        
        // Add thousands separator to amount field
        com.example.myapplication.utils.NumberFormatter.addThousandsSeparator(etAdjustmentAmount);
        
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
                
                // Ensure balances are recalculated from transactions if needed
                final OperatorEntity finalSelectedOperator = selectedOperator;
                if (finalSelectedOperator != null && currentUser != null) {
                    // Check if there are transactions that need to be accounted for
                    List<com.example.myapplication.database.entities.TransactionEntity> transactions = 
                        database.transactionDao().getTransactionsByUser(currentUser.getUid());
                    
                    // If there are transactions, ensure balances are up to date
                    if (transactions != null && !transactions.isEmpty()) {
                        // Check if this operator has a balance record
                        com.example.myapplication.database.entities.OperatorBalanceEntity existingBalance = 
                            database.operatorBalanceDao().getBalance(currentUser.getUid(), finalSelectedOperator.getId());
                        
                        // If no balance exists or balance seems incorrect, trigger recalculation
                        // (The recalculation will run in background and update all operators)
                        if (existingBalance == null) {
                            Log.d(TAG, "No balance record found for operator " + finalSelectedOperator.getName() + ", triggering recalculation");
                            balanceHelper.recalculateBalancesFromTransactions(currentUser.getUid());
                        }
                    }
                }
                
                // Get operator-specific balance (after ensuring it's calculated)
                double operatorBalance = 0.0;
                if (finalSelectedOperator != null && currentUser != null) {
                    operatorBalance = balanceHelper.getBalance(currentUser.getUid(), finalSelectedOperator.getId());
                    Log.d(TAG, "Loaded balance for operator " + finalSelectedOperator.getName() + " (ID: " + finalSelectedOperator.getId() + "): " + operatorBalance);
                }
                
                // Total Credits = virtualCredit only (global pool, not including operator balances)
                double virtualPool = currentUser.getVirtualCredit();
                
                final double finalOperatorBalance = operatorBalance;
                double cashBalance = currentUser.getCashBalance();
                final double finalCashBalance = cashBalance;
                final double finalTotalCredit = virtualPool;
                
                runOnUiThread(() -> {
                    String formattedOperatorBalance = com.example.myapplication.utils.NumberFormatter.formatWithThousandsSeparator(finalOperatorBalance);
                    String formattedCashBalance = com.example.myapplication.utils.NumberFormatter.formatWithThousandsSeparator(finalCashBalance);
                    String formattedTotalCredit = com.example.myapplication.utils.NumberFormatter.formatWithThousandsSeparator(finalTotalCredit);
                    tvCurrentOperatorBalance.setText(formattedOperatorBalance + " F");
                    tvCurrentCashBalance.setText(formattedCashBalance + " F");
                    if (tvCurrentTotalCredit != null) {
                        tvCurrentTotalCredit.setText(formattedTotalCredit + " F");
                    }
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
            double amount = com.example.myapplication.utils.NumberFormatter.getNumericValue(amountStr);
            if (amount == 0) {
                etAdjustmentAmount.setError(getString(R.string.invalid_amount));
                return;
            }
            
            String adjustmentType = rbOperatorBalance.isChecked() ? "operator" : "cash";
            
            // Validate operator selection for operator balance adjustments
            if ("operator".equals(adjustmentType) && selectedOperator == null) {
                Toast.makeText(this, "Please select an operator", Toast.LENGTH_SHORT).show();
                return;
            }
            
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
                
                // Store selectedOperator in final variable for lambda
                final OperatorEntity finalSelectedOperator = selectedOperator;
                
                // Create adjustment record
                BalanceAdjustmentEntity adjustment = new BalanceAdjustmentEntity();
                adjustment.setUserId(user.getUid());
                adjustment.setAdjustmentType(adjustmentType);
                adjustment.setAmount(amount);
                adjustment.setReason(reason);
                adjustment.setAdjustedBy(user.getUid());
                
                // Apply adjustment
                if ("operator".equals(adjustmentType)) {
                    if (finalSelectedOperator == null) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Please select an operator", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    // Operator balance is adjusted using the global virtualCredit pool
                    double operatorBefore = balanceHelper.getBalance(user.getUid(), finalSelectedOperator.getId());
                    double virtualBefore = user.getVirtualCredit();

                    double operatorAfter = operatorBefore;
                    double virtualAfter = virtualBefore;

                    if (amount > 0) {
                        // Move credits from global pool to this operator
                        if (virtualBefore < amount) {
                            runOnUiThread(() ->
                                    Toast.makeText(this, getString(R.string.insufficient_total_credits), Toast.LENGTH_SHORT).show()
                            );
                            return;
                        }
                        operatorAfter = operatorBefore + amount;
                        virtualAfter = virtualBefore - amount;
                    } else {
                        double delta = Math.abs(amount);
                        // Move credits back from operator to global pool
                        if (operatorBefore < delta) {
                            runOnUiThread(() ->
                                    Toast.makeText(this, getString(R.string.operator_balance_cannot_be_negative), Toast.LENGTH_SHORT).show()
                            );
                            return;
                        }
                        operatorAfter = operatorBefore - delta;
                        virtualAfter = virtualBefore + delta;
                    }

                    balanceHelper.updateBalance(user.getUid(), finalSelectedOperator.getId(), operatorAfter);
                    user.setVirtualCredit(virtualAfter);
                    // CRITICAL: Update creditUpdatedAt when virtualCredit changes for proper sync
                    long now = System.currentTimeMillis();
                    user.setCreditUpdatedAt(now);

                    balanceBefore = operatorBefore;
                    balanceAfter = operatorAfter;
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
                // Update user for both cash and virtual credit changes
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
            // Sync cash balance and global virtual credit (unallocated pool)
            updates.put("cashBalance", user.getCashBalance());
            updates.put("virtualCredit", user.getVirtualCredit());
            // CRITICAL: Sync creditUpdatedAt so Firestore knows when credit was last updated
            updates.put("creditUpdatedAt", new com.google.firebase.Timestamp(new java.util.Date(user.getCreditUpdatedAt())));
            updates.put("updatedAt", com.google.firebase.Timestamp.now());
            
            firestore.collection("users").document(user.getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Cash balance and virtual credit synced to Firestore (creditUpdatedAt: " + 
                            new java.util.Date(user.getCreditUpdatedAt()) + ")");
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



