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
import com.example.myapplication.database.entities.OperatorActionEntity;
import com.example.myapplication.database.entities.TransactionEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.utils.CommissionCalculator;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.OperatorBalanceHelper;
import com.example.myapplication.utils.SessionManager;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.core.content.FileProvider;

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
    private OperatorBalanceHelper balanceHelper;
    private LanguageManager languageManager;
    private static long lastLanguageChangeTime = 0;
    private java.util.List<TransactionEntity> allTransactions = new java.util.ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge display
        com.example.myapplication.utils.EdgeToEdgeHelper.enableEdgeToEdge(this);
        
        setContentView(R.layout.activity_cash_register);
        
        // Setup window insets for header
        com.example.myapplication.utils.EdgeToEdgeHelper.setupHeaderInsets(findViewById(R.id.headerLayout), this);
        
        // Initialize components
        database = AppDatabase.getDatabase(this);
        sessionManager = new SessionManager(this);
        firestore = FirebaseFirestore.getInstance();
        balanceHelper = new OperatorBalanceHelper(database);
        languageManager = LanguageManager.getInstance(this);
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
        
        // Setup language selector
        setupLanguageSelector();
        
        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CashRegisterAdapter(this, 
            transaction -> showTransactionDetailsDialog(transaction));
        // Set USSD retry listener separately
        adapter.setUssdRetryListener(transaction -> {
            Log.d(TAG, "USSD retry listener called from adapter for transaction: " + (transaction != null ? transaction.getId() : "null"));
            try {
                executeUssdRetry(transaction);
            } catch (Exception e) {
                Log.e(TAG, "Exception in executeUssdRetry", e);
                e.printStackTrace();
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        // Set print ticket listener
        adapter.setPrintTicketListener(transaction -> {
            Log.d(TAG, "Print ticket listener called for transaction: " + (transaction != null ? transaction.getId() : "null"));
            printTransactionTicket(transaction);
        });
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
    
    private void setupLanguageSelector() {
        // Find the language flag image view
        ImageView ivLanguageFlag = findViewById(R.id.ivLanguageFlag);
        if (ivLanguageFlag == null) {
            // Language selector might not be in the layout, skip setup
            return;
        }
        
        // Ensure languageManager is initialized
        if (languageManager == null) {
            languageManager = LanguageManager.getInstance(this);
        }
        
        // Set initial flag based on current language
        updateLanguageFlag();
        
        // Make the language selector clickable
        View languageSelector = findViewById(R.id.ivLanguageFlag);
        if (languageSelector != null) {
            languageSelector.setOnClickListener(v -> {
                // Prevent rapid language changes (debounce)
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastLanguageChangeTime < 1000) { // 1 second debounce
                    return;
                }
                
                // Toggle between English and French
                String currentLang = languageManager.getCurrentLanguage();
                String newLang = currentLang.equals("en") ? "fr" : "en";
                
                lastLanguageChangeTime = currentTime;
                
                // Set language and recreate activity
                languageManager.setLanguageWithTranslation(newLang)
                        .thenAccept(success -> {
                            runOnUiThread(() -> {
                                if (success) {
                                    // Recreate activity to apply new language
                                    recreate();
                                } else {
                                    Toast.makeText(this, "Failed to change language", Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
            });
        }
    }
    
    private void updateLanguageFlag() {
        ImageView ivLanguageFlag = findViewById(R.id.ivLanguageFlag);
        if (ivLanguageFlag == null) {
            return;
        }
        
        // Ensure languageManager is initialized
        if (languageManager == null) {
            languageManager = LanguageManager.getInstance(this);
        }
        
        String currentLang = languageManager.getCurrentLanguage();
        
        if ("fr".equals(currentLang)) {
            ivLanguageFlag.setImageResource(R.drawable.ic_flag_fr);
        } else {
            ivLanguageFlag.setImageResource(R.drawable.ic_flag_us);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Update language flag in case language was changed elsewhere
        updateLanguageFlag();
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
        // Transaction code - show full ID
        String transactionId = transaction.getId();
        tvTransactionCode.setText(transactionId.toUpperCase());
        tvCustomerName.setText(transaction.getCustomerName() + " (" + transaction.getCustomerPhone() + ")");
        tvOperator.setText(transaction.getOperatorName());
        tvAction.setText(transaction.getActionName());
        String formattedAmount = com.example.myapplication.utils.NumberFormatter.formatWithThousandsSeparator(transaction.getAmount());
        tvAmount.setText(formattedAmount + " F");
        tvType.setText(transaction.getTransactionType());
        // Display localized status value
        tvCurrentStatus.setText(convertStatusToDisplayValue(transaction.getStatus()));
        
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
        // Convert database status (English) to display value (localized)
        String displayStatus = convertStatusToDisplayValue(transaction.getStatus());
        int currentStatusIndex = statusAdapter.getPosition(displayStatus);
        if (currentStatusIndex >= 0) {
            spinnerStatus.setSelection(currentStatusIndex);
        }
        
        // Show cancel transaction button only if transaction is not already canceled
        String normalizedStatus = normalizeStatusToEnglish(transaction.getStatus());
        if (!"canceled".equalsIgnoreCase(normalizedStatus)) {
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
    
    /**
     * Normalize status value to English for database storage and internal processing.
     * This converts any localized status string (French, etc.) to its English equivalent.
     */
    private String normalizeStatusToEnglish(String status) {
        if (status == null) return "";
        
        String statusLower = status.toLowerCase().trim();
        
        // Handle French statuses
        if (statusLower.equals("en_attente") || statusLower.equals("en attente")) {
            return "pending";
        }
        if (statusLower.equals("réussi") || statusLower.equals("reussi")) {
            return "successful";
        }
        if (statusLower.equals("annulé") || statusLower.equals("annule")) {
            return "canceled";
        }
        if (statusLower.equals("échoué") || statusLower.equals("echoue")) {
            return "failed";
        }
        
        // Handle English statuses (normalize variations)
        if (statusLower.equals("success")) {
            return "successful";
        }
        if (statusLower.equals("cancelled")) {
            return "canceled";
        }
        if (statusLower.equals("failure")) {
            return "failed";
        }
        
        // Return as-is if already in standard English form (pending, successful, canceled, failed, processing)
        return statusLower;
    }
    
    /**
     * Convert database status (English) to display value (localized).
     * This converts English status to the appropriate localized string for UI display.
     */
    private String convertStatusToDisplayValue(String dbStatus) {
        if (dbStatus == null) return "";
        
        String statusLower = dbStatus.toLowerCase().trim();
        
        // Get current language
        String currentLang = languageManager != null ? languageManager.getCurrentLanguage() : "en";
        
        if ("fr".equals(currentLang)) {
            // French translations
            switch (statusLower) {
                case "pending":
                case "processing":
                    return "en_attente";
                case "successful":
                case "success":
                    return "réussi";
                case "canceled":
                case "cancelled":
                    return "annulé";
                case "failed":
                case "failure":
                    return "échoué";
                default:
                    return dbStatus;
            }
        } else {
            // English - normalize to standard forms
            switch (statusLower) {
                case "success":
                    return "successful";
                case "cancelled":
                    return "canceled";
                case "failure":
                    return "failed";
                default:
                    return statusLower;
            }
        }
    }
    
    private void updateTransactionStatus(TransactionEntity transaction, String newStatus) {
        new Thread(() -> {
            try {
                String oldStatus = transaction.getStatus();
                
                // Normalize status values to English for database storage and internal processing
                // This allows the UI to display in any language while maintaining consistent data
                String normalizedNewStatus = normalizeStatusToEnglish(newStatus);
                String normalizedOldStatus = normalizeStatusToEnglish(oldStatus);
                
                // Store normalized status in database
                transaction.setStatus(normalizedNewStatus);
                transaction.setUpdatedAt(System.currentTimeMillis());
                
                // Handle credit updates based on status changes (using normalized values)
                if (!normalizedOldStatus.equals(normalizedNewStatus)) {
                    handleCreditUpdate(transaction, normalizedOldStatus, normalizedNewStatus);
                    reconcileCommission(transaction, normalizedOldStatus, normalizedNewStatus);
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
            
            // Get operator ID from transaction
            String operatorId = transaction.getOperatorId();
            if (operatorId == null || operatorId.isEmpty()) {
                Log.e(TAG, "Transaction has no operator ID, cannot update operator balance");
                return;
            }
            
            // Get operator-specific balance
            double currentCredit = balanceHelper.getBalance(activeUserId, operatorId);
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
                final String finalOperatorId = operatorId;
                
                Log.d(TAG, "=== APPLYING BALANCE CHANGES ===");
                Log.d(TAG, "Operator ID: " + finalOperatorId);
                Log.d(TAG, "Credit Change: " + finalCreditChange);
                Log.d(TAG, "Old Credit: " + currentCredit);
                Log.d(TAG, "New Credit: " + newCredit);
                Log.d(TAG, "Cash Balance Change: " + finalCashBalanceChange);
                Log.d(TAG, "Old Cash Balance: " + currentCashBalance);
                Log.d(TAG, "New Cash Balance: " + newCashBalance);
                
                // Update operator-specific balance using OperatorBalanceHelper
                balanceHelper.updateBalance(activeUserId, finalOperatorId, newCredit);
                
                // Update cash balance (still global per user)
                user.setCashBalance(newCashBalance);
                database.userDao().updateUser(user);
                
                // Note: Operator-specific balances are stored in operator_balances table
                // Firestore sync for operator balances should be handled separately if needed
                
                // Show success message on UI thread
                runOnUiThread(() -> {
                    String message = String.format(Locale.getDefault(), 
                        "Credit: %+.0f (%.0f), Cash: %+.0f (%.0f", 
                        finalCreditChange, newCredit, finalCashBalanceChange, newCashBalance);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    Log.d(TAG, "✓ Toast shown: " + message);
                });
                
                // Notify other activities about credit change
                notifyCreditChange(newCredit, finalOperatorId);
                
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
    
    private void notifyCreditChange(double newCredit, String operatorId) {
        try {
            // Broadcast credit change to other activities
            android.content.Intent intent = new android.content.Intent("CREDIT_UPDATED");
            intent.putExtra("newCredit", newCredit);
            intent.putExtra("userId", activeUserId);
            intent.putExtra("operatorId", operatorId);
            sendBroadcast(intent);
            Log.d(TAG, "✓ Credit change broadcast sent for operator " + operatorId + ": " + newCredit);
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting credit change", e);
        }
    }
     
    /**
     * Print a 58mm-style ticket for the given transaction.
     * Displays: transaction number, customer name and phone, operator name,
     * transaction type, amount, fees, date, and agent company name.
     */
    private void printTransactionTicket(TransactionEntity tx) {
        if (tx == null) {
            Log.e(TAG, "Transaction is null, cannot print");
            return;
        }

        Log.d(TAG, "printTransactionTicket called for: " + tx.getId());

        // Agent / company name (use current user's name)
        String agentName = "";
        UserEntity user = sessionManager.getUserFromSession();
        if (user != null && user.getName() != null) {
            agentName = user.getName();
        }

        // Date
        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        String dateStr = df.format(new java.util.Date(tx.getCreatedAt()));

        // Compute fees (only for transfers, same logic as elsewhere)
        double fees = 0;
        String typeLower = tx.getTransactionType() != null ? tx.getTransactionType().toLowerCase() : "";
        if (typeLower.contains("transfer")) {
            try {
                OperatorEntity operator = database.operatorDao().getById(tx.getOperatorId());
                if (operator != null) {
                    double transferRate = operator.getTransferRate();
                    fees = tx.getAmount() * (transferRate / 100.0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error calculating transfer fees for ticket", e);
            }
        }

        String amountStr = com.example.myapplication.utils.NumberFormatter
                .formatWithThousandsSeparator(tx.getAmount());
        String feeStr = com.example.myapplication.utils.NumberFormatter
                .formatWithThousandsSeparator(fees);
        String currency = " F"; // keep same symbol as UI

        String ticketText =
                (agentName.isEmpty() ? "" : ("   " + agentName + "\n")) +
                "------------------------------\n" +
                "TXN: " + safe(tx.getId()).toUpperCase() + "\n" +
                "Customer: " + safe(tx.getCustomerName()) + "\n" +
                "Phone: " + safe(tx.getCustomerPhone()) + "\n" +
                "Operator: " + safe(tx.getOperatorName()) + "\n" +
                "Type: " + safe(tx.getTransactionType()) + "\n" +
                "Amount: " + amountStr + currency + "\n" +
                "Fees: " + feeStr + currency + "\n" +
                "Date: " + dateStr + "\n" +
                "------------------------------\n" +
                "Thank you.\n";

        try {
            // Render the ticket text into a bitmap suitable for 58mm printers
            String[] lines = ticketText.split("\\n");

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.BLACK);
            paint.setTextSize(24f); // reasonably small for 58mm

            float lineHeight = paint.getTextSize() + 8f;
            int width = 384; // common printable width for 58mm printers (in pixels)
            int height = (int) (lineHeight * lines.length + 40);

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            float x = 10f;
            float y = 30f;
            for (String line : lines) {
                canvas.drawText(line, x, y, paint);
                y += lineHeight;
            }

            // Save bitmap to cache and share via external printer app (avoids PrintManager restrictions)
            String jobName = "ticket_" + safe(tx.getId()) + ".png";
            java.io.File cacheDir = new java.io.File(getCacheDir(), "tickets");
            if (!cacheDir.exists()) {
                // noinspection ResultOfMethodCallIgnored
                cacheDir.mkdirs();
            }
            java.io.File file = new java.io.File(cacheDir, jobName);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Print / Share ticket"));
            Log.d(TAG, "Print/share chooser opened successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error printing ticket", e);
            Toast.makeText(this, "Error printing ticket: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
                // Normalize old status to English for consistency
                String normalizedOldStatus = normalizeStatusToEnglish(transaction.getStatus());
                transaction.setStatus("canceled");
                transaction.setUpdatedAt(System.currentTimeMillis());
                transaction.setNotes((transaction.getNotes() != null ? transaction.getNotes() + "\n" : "") + 
                        "Canceled at " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new java.util.Date()));
                
                // Handle balance reversal (using normalized values)
                if (!normalizedOldStatus.equals("canceled")) {
                    handleCreditUpdate(transaction, normalizedOldStatus, "canceled");
                    reconcileCommission(transaction, normalizedOldStatus, "canceled");
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
    
    /**
     * Execute USSD code retry for a transaction.
     * This opens the dialer with the USSD code without affecting balances.
     */
    private void executeUssdRetry(TransactionEntity transaction) {
        Log.d(TAG, "=== executeUssdRetry METHOD ENTERED ===");
        Log.d(TAG, "executeUssdRetry called for transaction: " + (transaction != null ? transaction.getId() : "null"));
        
        if (transaction == null) {
            Log.e(TAG, "Transaction is null");
            Toast.makeText(this, "Transaction not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String channel = transaction.getChannel();
        Log.d(TAG, "Transaction channel: " + channel);
        
        // If channel is null, try to infer from operator type
        if (channel == null || channel.isEmpty()) {
            Log.d(TAG, "Channel is null, checking operator type");
            OperatorEntity operator = database.operatorDao().getById(transaction.getOperatorId());
            if (operator != null && "USSD".equalsIgnoreCase(operator.getType())) {
                Log.d(TAG, "Inferred USSD from operator type");
                channel = "USSD";
            } else {
                Log.w(TAG, "Cannot infer USSD, operator type: " + (operator != null ? operator.getType() : "null"));
                Toast.makeText(this, "This transaction is not a USSD transaction", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // Only allow retry for USSD transactions
        if (!"USSD".equalsIgnoreCase(channel)) {
            Log.w(TAG, "Transaction is not USSD, channel: " + channel);
            Toast.makeText(this, "This transaction is not a USSD transaction", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Generate USSD code from transaction data
        new Thread(() -> {
            try {
                // Get operator and action from database
                OperatorEntity operator = database.operatorDao().getById(transaction.getOperatorId());
                OperatorActionEntity action = null;
                if (transaction.getActionId() != null) {
                    action = database.operatorActionDao().getById(transaction.getActionId());
                }
                
                if (operator == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Operator not found", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                // Generate USSD code
                String ussdCode = generateUssdCodeFromTransaction(transaction, operator, action);
                
                if (ussdCode == null || ussdCode.isEmpty()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Unable to generate USSD code", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                // Launch dialer on UI thread
                runOnUiThread(() -> {
                    launchUssdDialer(ussdCode);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating USSD code for retry", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    /**
     * Generate USSD code from transaction data
     */
    private String generateUssdCodeFromTransaction(TransactionEntity transaction, 
            OperatorEntity operator, 
            OperatorActionEntity action) {
        
        String operatorCode = operator.getCode();
        String actionCode = action != null ? action.getActionCode() : null;
        String customerPhone = transaction.getCustomerPhone();
        double amount = transaction.getAmount();
        
        Log.d(TAG, "Generating USSD code - Operator: " + operator.getName() + ", Code: " + operatorCode);
        if (action != null) {
            Log.d(TAG, "Action: " + action.getName() + ", Code: " + actionCode);
        }
        
        // Build USSD code using actual codes from database
        String ussdCode;
        
        if (operatorCode != null && !operatorCode.isEmpty()) {
            // Use actual operator code from database
            if (actionCode != null && !actionCode.isEmpty()) {
                // Use actual action code from database
                ussdCode = "*" + operatorCode + "*" + actionCode + "*" + customerPhone + "*" + (int)amount + "#";
            } else {
                // Fallback: use action name to determine code
                String actionName = action != null ? action.getName().toLowerCase() : 
                    (transaction.getActionName() != null ? transaction.getActionName().toLowerCase() : "");
                String defaultActionCode = actionName.contains("deposit") ? "1" : "2";
                ussdCode = "*" + operatorCode + "*" + defaultActionCode + "*" + customerPhone + "*" + (int)amount + "#";
            }
        } else {
            // Fallback: use operator name to determine code
            String operatorName = operator.getName().toLowerCase();
            String defaultOperatorCode;
            
            if (operatorName.contains("airtel")) {
                defaultOperatorCode = "144";
            } else if (operatorName.contains("mtn")) {
                defaultOperatorCode = "165";
            } else {
                defaultOperatorCode = "144"; // Default fallback
            }
            
            String actionName = action != null ? action.getName().toLowerCase() : 
                (transaction.getActionName() != null ? transaction.getActionName().toLowerCase() : "");
            String defaultActionCode = actionName.contains("deposit") ? "1" : "2";
            
            ussdCode = "*" + defaultOperatorCode + "*" + defaultActionCode + "*" + customerPhone + "*" + (int)amount + "#";
        }
        
        Log.d(TAG, "Generated USSD code for retry: " + ussdCode);
        
        // URL encode for dialer intent
        return android.net.Uri.encode(ussdCode);
    }
    
    /**
     * Launch USSD dialer (does not affect balances)
     */
    private void launchUssdDialer(String ussdCode) {
        try {
            // Open dialer app with USSD code pre-filled (don't call directly)
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(android.net.Uri.parse("tel:" + ussdCode));
            startActivity(intent);
            
            Toast.makeText(this, "USSD code opened in dialer. This will not affect your balance.", Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening USSD dialer", e);
            Toast.makeText(this, "Failed to open dialer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
