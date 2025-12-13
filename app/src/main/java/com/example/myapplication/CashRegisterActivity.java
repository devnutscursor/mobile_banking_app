package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapters.CashRegisterAdapter;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.TransactionEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.utils.CommissionCalculator;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.SessionManager;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CashRegisterActivity extends AppCompatActivity {
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
    private static final String TAG = "CashRegisterActivity";
    
    private RecyclerView recyclerView;
    private CashRegisterAdapter adapter;
    private TextView tvEmpty;
    private ImageView btnBack;
    private EditText etSearchTransactions;
    
    private AppDatabase database;
    private SessionManager sessionManager;
    private FirebaseFirestore firestore;
    private String activeUserId;
    private java.util.List<TransactionEntity> allTransactions = new java.util.ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_register);
        
        // Initialize components
        database = AppDatabase.getDatabase(this);
        sessionManager = new SessionManager(this);
        firestore = FirebaseFirestore.getInstance();
        activeUserId = sessionManager.getCurrentUser() != null ? sessionManager.getCurrentUser().getUid() : null;
        
        if (activeUserId == null) {
            Toast.makeText(this, "No active user session", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize views
        recyclerView = findViewById(R.id.recyclerViewTransactions);
        tvEmpty = findViewById(R.id.tvEmpty);
        etSearchTransactions = findViewById(R.id.etSearchTransactions);
        
        // Header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.cash_register_title));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CashRegisterAdapter(this, transaction -> showTransactionDetailsDialog(transaction));
        recyclerView.setAdapter(adapter);
        
        // Setup search
        etSearchTransactions.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTransactions(s.toString());
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Load transactions
        loadPendingTransactions();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh transactions when returning to activity (e.g., after processing new transactions)
        loadPendingTransactions();
    }
    
    private void loadPendingTransactions() {
        new Thread(() -> {
            try {
                // Get ALL transactions for this user (not just pending)
                List<TransactionEntity> transactions = database.transactionDao()
                        .getTransactionsByUser(activeUserId);
                
                allTransactions = transactions;
                
                runOnUiThread(() -> {
                    filterTransactions(etSearchTransactions != null ? etSearchTransactions.getText().toString() : "");
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading transactions", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading transactions", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void filterTransactions(String searchQuery) {
        if (allTransactions == null) {
            return;
        }
        
        List<TransactionEntity> filtered = new java.util.ArrayList<>();
        
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            filtered.addAll(allTransactions);
        } else {
            String query = searchQuery.toLowerCase().trim();
            for (TransactionEntity transaction : allTransactions) {
                // Search in customer name, customer phone, operator name, action name, transaction ID
                boolean matches = 
                    (transaction.getCustomerName() != null && transaction.getCustomerName().toLowerCase().contains(query)) ||
                    (transaction.getCustomerPhone() != null && transaction.getCustomerPhone().toLowerCase().contains(query)) ||
                    (transaction.getOperatorName() != null && transaction.getOperatorName().toLowerCase().contains(query)) ||
                    (transaction.getActionName() != null && transaction.getActionName().toLowerCase().contains(query)) ||
                    (transaction.getId() != null && transaction.getId().toLowerCase().contains(query)) ||
                    (transaction.getTransactionType() != null && transaction.getTransactionType().toLowerCase().contains(query)) ||
                    (transaction.getStatus() != null && transaction.getStatus().toLowerCase().contains(query)) ||
                    String.valueOf(transaction.getAmount()).contains(query);
                
                if (matches) {
                    filtered.add(transaction);
                }
            }
        }
        
        if (filtered.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            adapter.setTransactions(filtered);
        }
    }
    
    private void showTransactionDetailsDialog(TransactionEntity transaction) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_transaction_status, null);
        builder.setView(dialogView);
        
        // Initialize dialog views
        TextView tvTransactionCode = dialogView.findViewById(R.id.tvTransactionCode);
        TextView tvCustomerName = dialogView.findViewById(R.id.tvCustomerName);
        TextView tvOperator = dialogView.findViewById(R.id.tvOperator);
        TextView tvAction = dialogView.findViewById(R.id.tvAction);
        TextView tvAmount = dialogView.findViewById(R.id.tvAmount);
        TextView tvType = dialogView.findViewById(R.id.tvType);
        TextView tvCurrentStatus = dialogView.findViewById(R.id.tvCurrentStatus);
        Spinner spinnerStatus = dialogView.findViewById(R.id.spinnerStatus);
        Button btnUpdateStatus = dialogView.findViewById(R.id.btnUpdateStatus);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnCancelTransaction = dialogView.findViewById(R.id.btnCancelTransaction);
        
        // Populate transaction details
        tvTransactionCode.setText(transaction.getId().substring(0, 8).toUpperCase());
        tvCustomerName.setText(transaction.getCustomerName() + " (" + transaction.getCustomerPhone() + ")");
        tvOperator.setText(transaction.getOperatorName());
        tvAction.setText(transaction.getActionName());
        tvAmount.setText(NumberFormat.getCurrencyInstance(Locale.getDefault()).format(transaction.getAmount()));
        tvType.setText(transaction.getTransactionType());
        tvCurrentStatus.setText(transaction.getStatus());
        
        // Setup status spinner with custom adapter for white text
        ArrayAdapter<CharSequence> statusAdapter = new ArrayAdapter<CharSequence>(this,
                R.layout.spinner_status_item, getResources().getTextArray(R.array.transaction_statuses)) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setTextColor(getResources().getColor(R.color.primary_purple));
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setTextColor(getResources().getColor(R.color.white));
                return view;
            }
        };
        spinnerStatus.setAdapter(statusAdapter);
        
        // Set current status in spinner
        int currentStatusIndex = statusAdapter.getPosition(transaction.getStatus());
        if (currentStatusIndex >= 0) {
            spinnerStatus.setSelection(currentStatusIndex);
        }
        
        // Show cancel transaction button only if transaction is not already canceled
        if (!"canceled".equalsIgnoreCase(transaction.getStatus())) {
            btnCancelTransaction.setVisibility(View.VISIBLE);
        } else {
            btnCancelTransaction.setVisibility(View.GONE);
        }
        
        AlertDialog dialog = builder.create();
        
        // Ensure cancel button uses white outlined style (avoid theme tinting)
        btnCancel.setBackgroundResource(R.drawable.button_outline_purple);
        btnCancel.setTextColor(getResources().getColor(R.color.primary_purple));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            btnCancel.setBackgroundTintList(null);
        }

        // Handle update button
        btnUpdateStatus.setOnClickListener(v -> {
            String newStatus = spinnerStatus.getSelectedItem().toString();
            updateTransactionStatus(transaction, newStatus);
            dialog.dismiss();
        });
        
        // Handle cancel transaction button
        btnCancelTransaction.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.cancel_transaction))
                    .setMessage(getString(R.string.cancel_transaction_confirmation))
                    .setPositiveButton(android.R.string.yes, (d, w) -> {
                        cancelTransaction(transaction);
                        dialog.dismiss();
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .show();
        });
        
        // Handle cancel button (close dialog)
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void updateTransactionStatus(TransactionEntity transaction, String newStatus) {
        new Thread(() -> {
            try {
                String oldStatus = transaction.getStatus();
                transaction.setStatus(newStatus);
                transaction.setUpdatedAt(System.currentTimeMillis());
                
                // Handle credit updates based on status changes
                if (!oldStatus.equals(newStatus)) {
                    handleCreditUpdate(transaction, oldStatus, newStatus);
                    reconcileCommission(transaction, oldStatus, newStatus);
                }
                
                // Update in local database
                database.transactionDao().updateTransaction(transaction);
                
                // Sync to Firestore in background
                syncTransactionToFirestore(transaction);
                
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.status_updated), Toast.LENGTH_SHORT).show();
                    loadPendingTransactions(); // Refresh list
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error updating transaction status", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.error_updating_status) + ": " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void handleCreditUpdate(TransactionEntity transaction, String oldStatus, String newStatus) {
        try {
            // Get fresh user data to avoid conflicts
            UserEntity user = database.userDao().getUserById(activeUserId);
            if (user == null) {
                Log.e(TAG, "User not found for credit update");
                return;
            }
            
            double currentCredit = user.getVirtualCredit();
            double currentCashBalance = user.getCashBalance();
            double amount = transaction.getAmount();
            String transactionType = transaction.getTransactionType().toLowerCase();
            
            Log.d(TAG, "=== BALANCE UPDATE DEBUG ===");
            Log.d(TAG, "Transaction ID: " + transaction.getId());
            Log.d(TAG, "Transaction Type: " + transactionType);
            Log.d(TAG, "Transaction Amount: " + amount);
            Log.d(TAG, "Status Change: " + oldStatus + " -> " + newStatus);
            Log.d(TAG, "Current Credit: " + currentCredit);
            Log.d(TAG, "Current Cash Balance: " + currentCashBalance);
            
            // Calculate credit and cash balance changes based on status transition
            double creditChange = 0;
            double cashBalanceChange = 0;
            
            // IMPORTANT: Check canceled -> successful FIRST, before checking pending -> successful
            // From canceled to successful (apply the transaction - re-apply after cancellation)
            if (newStatus.equalsIgnoreCase("successful") && oldStatus.equalsIgnoreCase("canceled")) {
                Log.d(TAG, "✓ Status: canceled -> successful (APPLYING TRANSACTION)");
                if (transactionType.contains("withdrawal")) {
                    creditChange = amount; // Add credit for successful withdrawal
                    cashBalanceChange = -amount; // Decrease cash (agent gives cash to customer)
                    Log.d(TAG, "✓ Withdrawal successful: +" + amount + " to credit, -" + amount + " to cash");
                } else if (transactionType.contains("deposit")) {
                    creditChange = -amount; // Subtract credit for successful deposit
                    cashBalanceChange = amount; // Increase cash (customer gives cash to agent)
                    Log.d(TAG, "✓ Deposit successful: -" + amount + " from credit, +" + amount + " to cash");
                } else if (transactionType.contains("transfer")) {
                    // Transfer: debit amount + fees from credit, add total to cash
                    OperatorEntity operator = database.operatorDao().getById(transaction.getOperatorId());
                    if (operator != null) {
                        double transferRate = operator.getTransferRate();
                        double transferFee = amount * (transferRate / 100.0);
                        double totalAmount = amount + transferFee;
                        creditChange = -totalAmount; // Debit total amount (base + fees)
                        cashBalanceChange = totalAmount; // Increase cash by total (base + fees)
                        Log.d(TAG, "✓ Transfer successful: -" + totalAmount + " from credit (base: " + amount + ", fee: " + transferFee + "), +" + totalAmount + " to cash");
                    } else {
                        // Fallback: treat as deposit
                        creditChange = -amount;
                        cashBalanceChange = amount;
                        Log.d(TAG, "✓ Transfer successful (operator not found, treating as deposit): -" + amount + " from credit, +" + amount + " to cash");
                    }
                }
            }
            // From pending/processing to successful (apply the transaction)
            else if (newStatus.equalsIgnoreCase("successful") && (oldStatus.equalsIgnoreCase("pending") || oldStatus.equalsIgnoreCase("processing"))) {
                Log.d(TAG, "✓ Status: pending/processing -> successful");
                if (transactionType.contains("withdrawal")) {
                    creditChange = amount; // Add credit for successful withdrawal
                    cashBalanceChange = -amount; // Decrease cash (agent gives cash to customer)
                    Log.d(TAG, "✓ Withdrawal successful: +" + amount + " to credit, -" + amount + " to cash");
                } else if (transactionType.contains("deposit")) {
                    creditChange = -amount; // Subtract credit for successful deposit
                    cashBalanceChange = amount; // Increase cash (customer gives cash to agent)
                    Log.d(TAG, "✓ Deposit successful: -" + amount + " from credit, +" + amount + " to cash");
                } else if (transactionType.contains("transfer")) {
                    // Transfer: debit amount + fees from credit, add total to cash
                    OperatorEntity operator = database.operatorDao().getById(transaction.getOperatorId());
                    if (operator != null) {
                        double transferRate = operator.getTransferRate();
                        double transferFee = amount * (transferRate / 100.0);
                        double totalAmount = amount + transferFee;
                        creditChange = -totalAmount; // Debit total amount (base + fees)
                        cashBalanceChange = totalAmount; // Increase cash by total (base + fees)
                        Log.d(TAG, "✓ Transfer successful: -" + totalAmount + " from credit (base: " + amount + ", fee: " + transferFee + "), +" + totalAmount + " to cash");
                    } else {
                        // Fallback: treat as deposit
                        creditChange = -amount;
                        cashBalanceChange = amount;
                        Log.d(TAG, "✓ Transfer successful (operator not found, treating as deposit): -" + amount + " from credit, +" + amount + " to cash");
                    }
                }
            }
            // From successful to canceled (reverse the transaction, return balance)
            else if (newStatus.equalsIgnoreCase("canceled") && !oldStatus.equalsIgnoreCase("canceled")) {
                Log.d(TAG, "✓ Status: " + oldStatus + " -> canceled (REVERSING)");
                // Only reverse if transaction was previously successful
                if (oldStatus.equalsIgnoreCase("successful") || oldStatus.equalsIgnoreCase("success")) {
                    if (transactionType.contains("withdrawal")) {
                        creditChange = -amount; // Reverse: subtract credit (return to operator balance)
                        cashBalanceChange = amount; // Reverse: increase cash (return cash to agent)
                        Log.d(TAG, "✓ Withdrawal canceled: -" + amount + " from credit, +" + amount + " to cash (reversing)");
                    } else if (transactionType.contains("deposit")) {
                        creditChange = amount; // Reverse: add credit back (return to operator balance)
                        cashBalanceChange = -amount; // Reverse: decrease cash (return cash to customer)
                        Log.d(TAG, "✓ Deposit canceled: +" + amount + " to credit, -" + amount + " from cash (reversing)");
                    } else if (transactionType.contains("transfer")) {
                        // Transfer: reverse total amount (base + fees)
                        OperatorEntity operator = database.operatorDao().getById(transaction.getOperatorId());
                        if (operator != null) {
                            double transferRate = operator.getTransferRate();
                            double transferFee = amount * (transferRate / 100.0);
                            double totalAmount = amount + transferFee;
                            creditChange = totalAmount; // Reverse: add total back to credit
                            cashBalanceChange = -totalAmount; // Reverse: decrease cash by total
                            Log.d(TAG, "✓ Transfer canceled: +" + totalAmount + " to credit (base: " + amount + ", fee: " + transferFee + "), -" + totalAmount + " from cash (reversing)");
                        } else {
                            creditChange = amount;
                            cashBalanceChange = -amount;
                            Log.d(TAG, "✓ Transfer canceled (operator not found, treating as deposit): +" + amount + " to credit, -" + amount + " from cash (reversing)");
                        }
                    }
                }
            }
            // From successful to pending (reverse the transaction, return balance)
            else if (newStatus.equalsIgnoreCase("pending") && (oldStatus.equalsIgnoreCase("successful") || oldStatus.equalsIgnoreCase("success"))) {
                Log.d(TAG, "✓ Status: successful -> pending (REVERSING)");
                if (transactionType.contains("withdrawal")) {
                    creditChange = -amount; // Reverse: subtract credit (return to operator balance)
                    cashBalanceChange = amount; // Reverse: increase cash (return cash to agent)
                    Log.d(TAG, "✓ Withdrawal set to pending: -" + amount + " from credit, +" + amount + " to cash (reversing)");
                } else if (transactionType.contains("deposit")) {
                    creditChange = amount; // Reverse: add credit back (return to operator balance)
                    cashBalanceChange = -amount; // Reverse: decrease cash (return cash to customer)
                    Log.d(TAG, "✓ Deposit set to pending: +" + amount + " to credit, -" + amount + " from cash (reversing)");
                } else if (transactionType.contains("transfer")) {
                    // Transfer: reverse total amount (base + fees)
                    OperatorEntity operator = database.operatorDao().getById(transaction.getOperatorId());
                    if (operator != null) {
                        double transferRate = operator.getTransferRate();
                        double transferFee = amount * (transferRate / 100.0);
                        double totalAmount = amount + transferFee;
                        creditChange = totalAmount; // Reverse: add total back to credit
                        cashBalanceChange = -totalAmount; // Reverse: decrease cash by total
                        Log.d(TAG, "✓ Transfer set to pending: +" + totalAmount + " to credit (base: " + amount + ", fee: " + transferFee + "), -" + totalAmount + " from cash (reversing)");
                    } else {
                        creditChange = amount;
                        cashBalanceChange = -amount;
                        Log.d(TAG, "✓ Transfer set to pending (operator not found, treating as deposit): +" + amount + " to credit, -" + amount + " from cash (reversing)");
                    }
                }
            }
            else {
                Log.d(TAG, "✗ No balance change for status transition: " + oldStatus + " -> " + newStatus);
            }
            
            // Apply the credit and cash balance changes
            if (creditChange != 0 || cashBalanceChange != 0) {
                final double finalCreditChange = creditChange;
                final double finalCashBalanceChange = cashBalanceChange;
                final double newCredit = currentCredit + creditChange;
                final double newCashBalance = currentCashBalance + cashBalanceChange;
                
                Log.d(TAG, "=== APPLYING BALANCE CHANGES ===");
                Log.d(TAG, "Credit Change: " + finalCreditChange);
                Log.d(TAG, "Old Credit: " + currentCredit);
                Log.d(TAG, "New Credit: " + newCredit);
                Log.d(TAG, "Cash Balance Change: " + finalCashBalanceChange);
                Log.d(TAG, "Old Cash Balance: " + currentCashBalance);
                Log.d(TAG, "New Cash Balance: " + newCashBalance);
                
                // Update user credit and cash balance with timestamp
                user.setVirtualCredit(newCredit);
                user.setCashBalance(newCashBalance);
                user.setCreditUpdatedAt(System.currentTimeMillis());
                database.userDao().updateUser(user);
                
                // Sync credit and cash balance to Firestore with timestamp
                updateCreditInFirestore(activeUserId, newCredit, newCashBalance);
                
                // Show success message on UI thread
                runOnUiThread(() -> {
                    String message = String.format(Locale.getDefault(), 
                        "Credit: %+.0f (%.0f), Cash: %+.0f (%.0f", 
                        finalCreditChange, newCredit, finalCashBalanceChange, newCashBalance);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    Log.d(TAG, "✓ Toast shown: " + message);
                });
                
                // Notify other activities about credit change
                notifyCreditChange(newCredit);
                
                Log.d(TAG, "=== BALANCE UPDATE COMPLETED ===");
            } else {
                Log.d(TAG, "✗ No balance change needed for status transition: " + oldStatus + " -> " + newStatus);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling credit update", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "Error updating credit: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }
    
    private void reconcileCommission(TransactionEntity transaction, String oldStatus, String newStatus) {
        try {
            CommissionCalculator calculator = new CommissionCalculator(database);
            UserEntity user = database.userDao().getUserById(activeUserId);
            if (user == null) {
                Log.w(TAG, "User not found for commission reconciliation");
                return;
            }
            
            boolean newIsSuccessful = isSuccessfulStatus(newStatus);
            boolean oldWasSuccessful = isSuccessfulStatus(oldStatus);
            
            if (newIsSuccessful) {
                calculator.calculateCommission(transaction, user);
            } else if (oldWasSuccessful) {
                // Remove commission if transaction was successful but is now not successful (canceled or pending)
                calculator.removeCommissionForTransaction(transaction.getId());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reconciling commission for transaction " + transaction.getId(), e);
        }
    }
    
    private boolean isSuccessfulStatus(String status) {
        return status != null && 
                ("successful".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status));
    }
    
    private void notifyCreditChange(double newCredit) {
        try {
            // Broadcast credit change to other activities
            android.content.Intent intent = new android.content.Intent("CREDIT_UPDATED");
            intent.putExtra("newCredit", newCredit);
            intent.putExtra("userId", activeUserId);
            sendBroadcast(intent);
            Log.d(TAG, "✓ Credit change broadcast sent: " + newCredit);
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting credit change", e);
        }
    }
    
    private void updateUserCredit(TransactionEntity transaction) {
        try {
            UserEntity user = database.userDao().getUserById(activeUserId);
            if (user != null) {
                double currentCredit = user.getVirtualCredit();
                double amount = transaction.getAmount();
                
                // Add for withdrawal, subtract for deposit
                if (transaction.getTransactionType().toLowerCase().contains("withdrawal")) {
                    user.setVirtualCredit(currentCredit + amount);
                    Log.d(TAG, "Added credit: " + amount + ", New balance: " + (currentCredit + amount));
                } else if (transaction.getTransactionType().toLowerCase().contains("deposit")) {
                    user.setVirtualCredit(currentCredit - amount);
                    Log.d(TAG, "Subtracted credit: " + amount + ", New balance: " + (currentCredit - amount));
                }
                
                database.userDao().updateUser(user);
                
                // Sync credit to Firestore
                updateCreditInFirestore(activeUserId, user.getVirtualCredit());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating user credit", e);
        }
    }
    
    private void reverseUserCredit(TransactionEntity transaction) {
        try {
            UserEntity user = database.userDao().getUserById(activeUserId);
            if (user != null) {
                double currentCredit = user.getVirtualCredit();
                double amount = transaction.getAmount();
                
                // Reverse the transaction: opposite of updateUserCredit
                // Subtract for withdrawal (reverse add), add for deposit (reverse subtract)
                if (transaction.getTransactionType().toLowerCase().contains("withdrawal")) {
                    user.setVirtualCredit(currentCredit - amount);
                    Log.d(TAG, "Reversed withdrawal credit: -" + amount + ", New balance: " + (currentCredit - amount));
                } else if (transaction.getTransactionType().toLowerCase().contains("deposit")) {
                    user.setVirtualCredit(currentCredit + amount);
                    Log.d(TAG, "Reversed deposit credit: +" + amount + ", New balance: " + (currentCredit + amount));
                }
                
                database.userDao().updateUser(user);
                
                // Sync credit to Firestore
                updateCreditInFirestore(activeUserId, user.getVirtualCredit());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reversing user credit", e);
        }
    }
    
    private void syncTransactionToFirestore(TransactionEntity transaction) {
        try {
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("status", transaction.getStatus());
            transactionData.put("updatedAt", transaction.getUpdatedAt());
            transactionData.put("lastUpdated", FieldValue.serverTimestamp());
            
            firestore.collection("transactions").document(transaction.getId())
                    .update(transactionData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Transaction synced to Firestore");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to sync transaction to Firestore", e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error syncing to Firestore", e);
        }
    }
    
    private void cancelTransaction(TransactionEntity transaction) {
        new Thread(() -> {
            try {
                String oldStatus = transaction.getStatus();
                transaction.setStatus("canceled");
                transaction.setUpdatedAt(System.currentTimeMillis());
                transaction.setNotes((transaction.getNotes() != null ? transaction.getNotes() + "\n" : "") + 
                        "Canceled at " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new java.util.Date()));
                
                // Handle balance reversal
                if (!oldStatus.equals("canceled")) {
                    handleCreditUpdate(transaction, oldStatus, "canceled");
                    reconcileCommission(transaction, oldStatus, "canceled");
                }
                
                // Update in local database
                database.transactionDao().updateTransaction(transaction);
                
                // Sync to Firestore
                syncTransactionToFirestore(transaction);
                
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.transaction_canceled), Toast.LENGTH_SHORT).show();
                    loadPendingTransactions();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error canceling transaction", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error canceling transaction: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void updateCreditInFirestore(String userId, double newCredit) {
        updateCreditInFirestore(userId, newCredit, null);
    }
    
    private void updateCreditInFirestore(String userId, double newCredit, Double newCashBalance) {
        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("virtualCredit", newCredit);
            if (newCashBalance != null) {
                updates.put("cashBalance", newCashBalance);
            }
            updates.put("lastUpdated", FieldValue.serverTimestamp());
            
            firestore.collection("users").document(userId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        if (newCashBalance != null) {
                            Log.d(TAG, "Credit and cash balance synced to Firestore: Credit=" + newCredit + ", Cash=" + newCashBalance);
                        } else {
                        Log.d(TAG, "Credit synced to Firestore: " + newCredit);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to sync credit to Firestore", e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error syncing credit to Firestore", e);
        }
    }
}
