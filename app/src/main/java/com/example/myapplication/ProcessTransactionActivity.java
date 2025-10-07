package com.example.myapplication;

import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.CustomerEntity;
import com.example.myapplication.database.entities.OperatorActionEntity;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.TransactionEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import com.example.myapplication.adapters.CustomSpinnerAdapter;
import com.example.myapplication.adapters.CustomerSpinnerAdapter;

public class ProcessTransactionActivity extends AppCompatActivity {

    private static final String TAG = "ProcessTransaction";

    // UI Elements
    private Spinner spinnerOperator, spinnerAction, spinnerCustomer;
    private EditText etAmount;
    private TextView tvAvailableCredit;
    private Button btnExecuteTransaction;

    // Data
    private AppDatabase database;
    private FirebaseFirestore db;
    private FirebaseUser firebaseUser; // only for online auth when needed
    private SessionManager sessionManager;
    private UserEntity currentUserEntity;
    private String activeUserId; // single source of truth for current user id

    private List<OperatorEntity> operators = new ArrayList<>();
    private List<OperatorActionEntity> actions = new ArrayList<>();
    private List<CustomerEntity> customers = new ArrayList<>();

    private OperatorEntity selectedOperator;
    private OperatorActionEntity selectedAction;
    private CustomerEntity selectedCustomer;
    private String selectedTransactionType;

    private double availableCredit = 0.0;
    

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
        
        // Apply language handled via base context in app; no explicit call needed here
        LanguageManager languageManager = LanguageManager.getInstance(this);
        
        setContentView(R.layout.activity_process_transaction);

        // Initialize services
        db = FirebaseFirestore.getInstance();
        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        database = AppDatabase.getDatabase(this);
        sessionManager = new SessionManager(this);

        // Initialize UI
        initViews();
        
        // Resolve active user from session (offline-first)
        currentUserEntity = sessionManager.getCurrentUser();
        if (currentUserEntity == null) {
            // When offline, prefer session-stored user even if login flag isn't set
            currentUserEntity = sessionManager.getUserFromSession();
        }
        if (currentUserEntity == null && firebaseUser != null) {
            // Fallback to Firebase user id if session not initialized for some reason
            currentUserEntity = database.userDao().getUserById(firebaseUser.getUid());
        }
        activeUserId = currentUserEntity != null ? currentUserEntity.getUid() : (firebaseUser != null ? firebaseUser.getUid() : null);

        // Load user data and credit from local DB first
        loadUserData();
        
        // Load initial data
        loadOperators();
        loadCustomers();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to activity (e.g., after adding customers/operators)
        loadCustomers();
        loadOperators();
        loadUserData(); // Refresh credit as well
    }

    private void initViews() {
        spinnerOperator = findViewById(R.id.spinnerOperator);
        spinnerAction = findViewById(R.id.spinnerAction);
        spinnerCustomer = findViewById(R.id.spinnerCustomer);
        etAmount = findViewById(R.id.etAmount);
        tvAvailableCredit = findViewById(R.id.tvAvailableCredit);
        btnExecuteTransaction = findViewById(R.id.btnExecuteTransaction);
        
        // Header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.process_transaction));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Setup listeners
        setupListeners();
    }

    private void setupListeners() {
        spinnerOperator.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < operators.size()) { // No placeholder, direct index
                    selectedOperator = operators.get(position);
                    loadActions(selectedOperator.getId());
                } else {
                    selectedOperator = null;
                    actions.clear();
                    updateActionSpinner();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedOperator = null;
            }
        });

        spinnerAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < actions.size()) {
                    selectedAction = actions.get(position);
                } else {
                    selectedAction = null;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedAction = null;
            }
        });

        spinnerCustomer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < customers.size()) {
                    selectedCustomer = customers.get(position);
                } else {
                    selectedCustomer = null;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedCustomer = null;
            }
        });

        btnExecuteTransaction.setOnClickListener(v -> executeTransaction());
    }

    private void loadUserData() {
        if (activeUserId == null) {
            Log.e(TAG, "No current user found");
            return;
        }

        Log.d(TAG, "Loading data for user: " + activeUserId);

        new Thread(() -> {
            try {
                // Reset credit to 0 first to avoid showing old user's credit
                availableCredit = 0.0;
                runOnUiThread(() -> updateCreditDisplay());
                
                // Load from local database first (offline-first approach)
                currentUserEntity = database.userDao().getUserById(activeUserId);
                
                if (currentUserEntity != null) {
                    availableCredit = currentUserEntity.getVirtualCredit();
                    Log.d(TAG, "Loaded credit from local DB for user " + activeUserId + ": " + availableCredit);
                    runOnUiThread(() -> updateCreditDisplay());
                } else {
                    Log.e(TAG, "No user found in local database for: " + activeUserId);
                    // Set default credit if no user found
                    availableCredit = 0.0;
                    runOnUiThread(() -> updateCreditDisplay());
                }

                // Always try to sync with Firestore to get latest credit for current user
                syncWithFirestoreIfOnline();

            } catch (Exception e) {
                Log.e(TAG, "Error loading user data", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading user data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void updateCreditDisplay() {
        // Display integer-like when possible, else 2 decimals
        if (Math.abs(availableCredit - Math.rint(availableCredit)) < 0.005) {
            tvAvailableCredit.setText(String.format(java.util.Locale.getDefault(), "%.0f", availableCredit));
        } else {
            tvAvailableCredit.setText(String.format(java.util.Locale.getDefault(), "%.2f", availableCredit));
        }
    }
    
    private void syncWithFirestoreIfOnline() {
        if (activeUserId == null) return;
        
        Log.d(TAG, "Syncing with Firestore for user: " + activeUserId);
        
        // Check if we have internet connection
        try {
            db.collection("users").document(activeUserId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Double credit = documentSnapshot.getDouble("virtualCredit");
                            if (credit != null) {
                // Check if local credit is more recent than Firestore
                if (currentUserEntity != null && currentUserEntity.getCreditUpdatedAt() > 0) {
                    long localUpdateTime = currentUserEntity.getCreditUpdatedAt();
                    long firestoreUpdateTime = 0;
                    
                    // Get Firestore update time if available
                    if (documentSnapshot.contains("creditUpdatedAt")) {
                        Object creditUpdatedAt = documentSnapshot.get("creditUpdatedAt");
                        if (creditUpdatedAt instanceof com.google.firebase.Timestamp) {
                            firestoreUpdateTime = ((com.google.firebase.Timestamp) creditUpdatedAt).toDate().getTime();
                        } else if (creditUpdatedAt instanceof Long) {
                            firestoreUpdateTime = (Long) creditUpdatedAt;
                        }
                    }
                    
                    // Only update if Firestore is more recent
                    if (firestoreUpdateTime > localUpdateTime) {
                        availableCredit = credit;
                        Log.d(TAG, "Updated credit from Firestore for user " + activeUserId + ": " + availableCredit);
                        
                        // Update local database
                        new Thread(() -> {
                            try {
                                if (currentUserEntity != null) {
                                    currentUserEntity.setVirtualCredit(availableCredit);
                                    currentUserEntity.setLastSyncAt(System.currentTimeMillis());
                                    database.userDao().updateUser(currentUserEntity);
                                    Log.d(TAG, "Updated local credit for user " + activeUserId + ": " + availableCredit);
                                } else {
                                    Log.e(TAG, "currentUserEntity is null, cannot update local credit");
                                }
                                runOnUiThread(() -> updateCreditDisplay());
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating local credit", e);
                            }
                        }).start();
                    } else {
                        Log.d(TAG, "Local credit is more recent than Firestore, keeping local value: " + availableCredit);
                        runOnUiThread(() -> updateCreditDisplay());
                    }
                } else {
                    // No local update time, use Firestore value
                    availableCredit = credit;
                    Log.d(TAG, "Updated credit from Firestore for user " + activeUserId + ": " + availableCredit);
                    
                    // Update local database
                    new Thread(() -> {
                        try {
                            if (currentUserEntity != null) {
                                currentUserEntity.setVirtualCredit(availableCredit);
                                currentUserEntity.setLastSyncAt(System.currentTimeMillis());
                                database.userDao().updateUser(currentUserEntity);
                                Log.d(TAG, "Updated local credit for user " + activeUserId + ": " + availableCredit);
                            } else {
                                Log.e(TAG, "currentUserEntity is null, cannot update local credit");
                            }
                            runOnUiThread(() -> updateCreditDisplay());
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating local credit", e);
                        }
                    }).start();
                }
                            } else {
                                Log.e(TAG, "No credit found in Firestore for user: " + activeUserId);
                            }
                        } else {
                            Log.e(TAG, "User document not found in Firestore: " + activeUserId);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.d(TAG, "Offline mode - using local data only: " + e.getMessage());
                        // No error shown to user - this is expected in offline mode
                    });
        } catch (Exception e) {
            Log.d(TAG, "Offline mode - no internet connection");
        }
    }

    private void loadOperators() {
        new Thread(() -> {
            try {
                if (activeUserId != null) {
                    operators = database.operatorDao().getActiveForUser(activeUserId);
                } else {
                    operators = new ArrayList<>();
                }

                runOnUiThread(() -> {
                    if (operators.isEmpty()) {
                        Toast.makeText(this, getString(R.string.no_operators), Toast.LENGTH_SHORT).show();
                    }
                    updateOperatorSpinner();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading operators", e);
                runOnUiThread(() -> Toast.makeText(this, "Error loading operators", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadActions(String operatorId) {
        new Thread(() -> {
            try {
                String uid = activeUserId;
                actions = (uid != null) ?
                        database.operatorActionDao().getByOperatorForUser(operatorId, uid) :
                        new ArrayList<>();

                runOnUiThread(() -> {
                    if (actions.isEmpty()) {
                        Toast.makeText(this, getString(R.string.no_actions), Toast.LENGTH_SHORT).show();
                    }
                    updateActionSpinner();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading actions", e);
                runOnUiThread(() -> Toast.makeText(this, "Error loading actions", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadCustomers() {
        new Thread(() -> {
            try {
                if (activeUserId != null) {
                    customers = database.customerDao().getCustomersByUser(activeUserId);
                } else {
                    customers = new ArrayList<>();
                }

                runOnUiThread(() -> {
                    if (customers.isEmpty()) {
                        Toast.makeText(this, getString(R.string.no_customers), Toast.LENGTH_SHORT).show();
                    }
                    updateCustomerSpinner();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading customers", e);
                runOnUiThread(() -> Toast.makeText(this, "Error loading customers", Toast.LENGTH_SHORT).show());
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
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                textView.setTextColor(getResources().getColor(R.color.text_primary));
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                textView.setTextColor(getResources().getColor(R.color.text_primary));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOperator.setAdapter(adapter);
        if (!operatorNames.isEmpty()) {
            spinnerOperator.setSelection(0);
        }
    }
    
    private int getOperatorColorResource(String color) {
        if ("purple".equals(color)) return R.color.primary_purple;
        else if ("blue".equals(color)) return R.color.info_blue;
        else if ("green".equals(color)) return R.color.success_green;
        else if ("amber".equals(color)) return R.color.warning_amber;
        else if ("red".equals(color)) return R.color.error_red;
        else if ("teal".equals(color)) return R.color.teal_200;
        else if ("indigo".equals(color)) return R.color.primary_blue_dark;
        return R.color.primary_orange; // default
    }

    private void updateActionSpinner() {
        List<String> actionNames = new ArrayList<>();
        
        for (OperatorActionEntity action : actions) {
            actionNames.add(action.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            this, 
            android.R.layout.simple_spinner_item,
            actionNames
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                textView.setTextColor(getResources().getColor(R.color.text_primary));
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                textView.setTextColor(getResources().getColor(R.color.text_primary));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAction.setAdapter(adapter);
        if (!actionNames.isEmpty()) {
            spinnerAction.setSelection(0);
            selectedAction = actions.get(0);
        }
    }

    private void updateCustomerSpinner() {
        List<String> customerNames = new ArrayList<>();
        
        for (CustomerEntity customer : customers) {
            String name = customer.getFullName();
            if (name == null || name.trim().isEmpty()) {
                name = customer.getPhoneNumber() != null && !customer.getPhoneNumber().trim().isEmpty()
                        ? customer.getPhoneNumber()
                        : (customer.getId() != null ? customer.getId() : "Unknown");
            }
            customerNames.add(name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            this, 
            android.R.layout.simple_spinner_item,
            customerNames
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                textView.setTextColor(getResources().getColor(R.color.text_primary));
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                textView.setTextColor(getResources().getColor(R.color.text_primary));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCustomer.setAdapter(adapter);
        if (!customerNames.isEmpty()) {
            spinnerCustomer.setSelection(0);
            selectedCustomer = customers.get(0);
        }
    }

    private void executeTransaction() {
        Log.d(TAG, "Starting transaction execution");
        
        // Validation
        if (!validateTransaction()) {
            Log.d(TAG, "Transaction validation failed");
            return;
        }

        double amount = Double.parseDouble(etAmount.getText().toString());
        
        Log.d(TAG, "Transaction amount: " + amount);
        Log.d(TAG, "Available credit: " + availableCredit);
        Log.d(TAG, "Selected operator: " + (selectedOperator != null ? selectedOperator.getName() : "null"));
        Log.d(TAG, "Selected action: " + (selectedAction != null ? selectedAction.getName() : "null"));
        Log.d(TAG, "Selected customer: " + (selectedCustomer != null ? selectedCustomer.getFullName() : "null"));

        // Check credit availability
        if (!checkCreditAvailability(amount)) {
            Log.d(TAG, "Insufficient credit");
            Toast.makeText(this, getString(R.string.insufficient_credit), Toast.LENGTH_LONG).show();
            return;
        }

        // Determine if USSD or Non-USSD
        boolean isUssd = "USSD".equals(selectedOperator.getType());
        Log.d(TAG, "Transaction type: " + (isUssd ? "USSD" : "NON_USSD"));

        if (isUssd) {
            executeUssdTransaction(amount);
        } else {
            executeNonUssdTransaction(amount);
        }
    }

    private boolean validateTransaction() {
        if (selectedOperator == null) {
            Toast.makeText(this, getString(R.string.select_operator), Toast.LENGTH_SHORT).show();
            return false;
        }

        if (selectedAction == null) {
            Toast.makeText(this, getString(R.string.select_action), Toast.LENGTH_SHORT).show();
            return false;
        }

        if (selectedCustomer == null) {
            Toast.makeText(this, getString(R.string.select_customer), Toast.LENGTH_SHORT).show();
            return false;
        }

        // Transaction type is now auto-detected from action name

        String amountStr = etAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_amount), Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                Toast.makeText(this, getString(R.string.invalid_amount), Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.invalid_amount), Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private boolean checkCreditAvailability(double amount) {
        // Auto-detect transaction type from action name
        String transactionType = detectTransactionType();
        
        // For deposits, user needs credit
        // For withdrawals, user gains credit
        if ("deposit".equalsIgnoreCase(transactionType)) {
            return availableCredit >= amount;
        }
        
        return true; // Withdrawals don't need credit check
    }
    
    private String detectTransactionType() {
        if (selectedAction == null) return "deposit";
        
        String actionName = selectedAction.getName().toLowerCase();
        
        // Check for withdrawal keywords
        if (actionName.contains("withdraw") || 
            actionName.contains("retrait") || 
            actionName.contains("cash out")) {
            return "withdrawal";
        }
        
        // Default to deposit for all other actions (deposit, transfer, bills, etc.)
        return "deposit";
    }

    private void executeUssdTransaction(double amount) {
        // Create transaction record - default to successful
        TransactionEntity transaction = createTransaction(amount, "USSD");
        transaction.setStatus("successful"); // Default all transactions to successful
        
        // Generate USSD code
        String ussdCode = generateUssdCode(amount);
        
        if (ussdCode == null || ussdCode.isEmpty()) {
            Toast.makeText(this, getString(R.string.transaction_failed), Toast.LENGTH_SHORT).show();
            return;
        }

        // Save transaction and update credit immediately
        saveTransaction(transaction, () -> {
            // Update credit immediately for successful transaction
            updateUserCredit(transaction);
            // Launch USSD dialer
            launchUssdDialer(ussdCode, transaction);
        });
    }

    private void executeNonUssdTransaction(double amount) {
        Log.d(TAG, "Executing Non-USSD transaction");
        
        try {
            // Create transaction record - default to successful
            TransactionEntity transaction = createTransaction(amount, "NON_USSD");
            transaction.setStatus("successful"); // Default all transactions to successful
            
            Log.d(TAG, "Created transaction: " + transaction.getId());
            
            // Save transaction and update credit immediately
            saveTransaction(transaction, () -> {
                Log.d(TAG, "Transaction saved, updating credit");
                // Update credit immediately for successful transaction
                updateUserCredit(transaction);
                
                Toast.makeText(this, getString(R.string.transaction_success), Toast.LENGTH_LONG).show();
                finish();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in executeNonUssdTransaction", e);
            Toast.makeText(this, "Transaction failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private TransactionEntity createTransaction(double amount, String channel) {
        TransactionEntity transaction = new TransactionEntity();
        
        transaction.setOperatorId(selectedOperator.getId());
        transaction.setOperatorName(selectedOperator.getName());
        transaction.setActionId(selectedAction.getId());
        transaction.setActionName(selectedAction.getName());
        transaction.setCustomerId(selectedCustomer.getId());
        String customerName = selectedCustomer.getFullName();
        if (customerName == null || customerName.trim().isEmpty()) {
            customerName = selectedCustomer.getPhoneNumber() != null && !selectedCustomer.getPhoneNumber().trim().isEmpty()
                    ? selectedCustomer.getPhoneNumber()
                    : selectedCustomer.getId();
        }
        transaction.setCustomerName(customerName);
        transaction.setCustomerPhone(selectedCustomer.getPhoneNumber());
        transaction.setAmount(amount);
        
        // Auto-detect transaction type from action name
        transaction.setTransactionType(detectTransactionType());
        
        transaction.setChannel(channel);
        
        if (currentUserEntity != null) {
            transaction.setUserId(currentUserEntity.getUid());
            transaction.setUserName(currentUserEntity.getName());
            transaction.setUserRole(currentUserEntity.getRole());
        }
        
        transaction.setCreditBefore(availableCredit);
        
        // Calculate credit after
        double creditAfter = availableCredit;
        if ("deposit".equals(transaction.getTransactionType())) {
            creditAfter -= amount; // Deposit subtracts credit
        } else {
            creditAfter += amount; // Withdrawal adds credit
        }
        transaction.setCreditAfter(creditAfter);
        
        return transaction;
    }

    private String generateUssdCode(double amount) {
        // Get actual operator code and action code from database
        String operatorCode = selectedOperator.getCode();
        String actionCode = selectedAction.getActionCode();
        
        Log.d(TAG, "Operator: " + selectedOperator.getName() + ", Code: " + operatorCode);
        Log.d(TAG, "Action: " + selectedAction.getName() + ", Code: " + actionCode);
        
        // Build USSD code using actual codes from database
        String ussdCode;
        
        if (operatorCode != null && !operatorCode.isEmpty()) {
            // Use actual operator code from database
            if (actionCode != null && !actionCode.isEmpty()) {
                // Use actual action code from database
                ussdCode = "*" + operatorCode + "*" + actionCode + "*" + selectedCustomer.getPhoneNumber() + "*" + (int)amount + "#";
            } else {
                // Fallback: use action name to determine code
                String actionName = selectedAction.getName().toLowerCase();
                String defaultActionCode = actionName.contains("deposit") ? "1" : "2";
                ussdCode = "*" + operatorCode + "*" + defaultActionCode + "*" + selectedCustomer.getPhoneNumber() + "*" + (int)amount + "#";
            }
        } else {
            // Fallback: use operator name to determine code
            String operatorName = selectedOperator.getName().toLowerCase();
            String defaultOperatorCode;
            
            if (operatorName.contains("airtel")) {
                defaultOperatorCode = "144";
            } else if (operatorName.contains("mtn")) {
                defaultOperatorCode = "165";
            } else {
                defaultOperatorCode = "144"; // Default fallback
            }
            
            String actionName = selectedAction.getName().toLowerCase();
            String defaultActionCode = actionName.contains("deposit") ? "1" : "2";
            
            ussdCode = "*" + defaultOperatorCode + "*" + defaultActionCode + "*" + selectedCustomer.getPhoneNumber() + "*" + (int)amount + "#";
        }
        
        Log.d(TAG, "Generated USSD code: " + ussdCode);
        
        // URL encode for dialer intent
        return Uri.encode(ussdCode);
    }

    private void launchUssdDialer(String ussdCode, TransactionEntity transaction) {
        try {
            // Open dialer app with USSD code pre-filled (don't call directly)
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + ussdCode));
            startActivity(intent);
            
            Toast.makeText(this, "USSD code opened in dialer. Transaction marked as successful.", Toast.LENGTH_LONG).show();
            
            // Transaction already marked as successful and credit already updated
            // Just add a note for reference
            new Thread(() -> {
                transaction.setNotes("USSD code: " + ussdCode);
                database.transactionDao().updateTransaction(transaction);
            }).start();
            
            finish();
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening USSD dialer", e);
            Toast.makeText(this, getString(R.string.transaction_failed), Toast.LENGTH_SHORT).show();
            
            // Mark transaction as failed and reverse credit
            new Thread(() -> {
                transaction.setStatus("failed");
                transaction.setNotes("Failed to open dialer: " + e.getMessage());
                database.transactionDao().updateTransaction(transaction);
                
                // Reverse credit change
                if (currentUserEntity != null) {
                    currentUserEntity.setVirtualCredit(transaction.getCreditBefore());
                    database.userDao().updateUser(currentUserEntity);
                }
            }).start();
        }
    }

    private void saveTransaction(TransactionEntity transaction, Runnable onSuccess) {
        new Thread(() -> {
            try {
                // Save to local database first (offline-first)
                database.transactionDao().insertTransaction(transaction);
                
                Log.d(TAG, "Transaction saved locally: " + transaction.getId());
                
                // Try to sync to Firestore in background (don't block on this)
                syncTransactionToFirestoreInBackground(transaction);
                
                runOnUiThread(() -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving transaction", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.transaction_failed) + ": " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void syncTransactionToFirestoreInBackground(TransactionEntity transaction) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", transaction.getId());
        data.put("operatorId", transaction.getOperatorId());
        data.put("operatorName", transaction.getOperatorName());
        data.put("actionId", transaction.getActionId());
        data.put("actionName", transaction.getActionName());
        data.put("customerId", transaction.getCustomerId());
        data.put("customerName", transaction.getCustomerName());
        data.put("customerPhone", transaction.getCustomerPhone());
        data.put("amount", transaction.getAmount());
        data.put("transactionType", transaction.getTransactionType());
        data.put("channel", transaction.getChannel());
        data.put("userId", transaction.getUserId());
        data.put("userName", transaction.getUserName());
        data.put("userRole", transaction.getUserRole());
        data.put("creditBefore", transaction.getCreditBefore());
        data.put("creditAfter", transaction.getCreditAfter());
        data.put("status", transaction.getStatus());
        data.put("notes", transaction.getNotes());
        data.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(transaction.getCreatedAt())));
        data.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(transaction.getUpdatedAt())));

        db.collection("transactions").document(transaction.getId())
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Transaction synced to Firestore");
                    new Thread(() -> {
                        transaction.setNeedsSync(false);
                        transaction.setLastSyncAt(System.currentTimeMillis());
                        database.transactionDao().updateTransaction(transaction);
                    }).start();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error syncing transaction to Firestore", e);
                });
    }

    private void updateUserCredit(TransactionEntity transaction) {
        if (activeUserId == null || currentUserEntity == null) {
            Log.e(TAG, "Cannot update credit: currentUser or currentUserEntity is null");
            return;
        }

        double newCredit = transaction.getCreditAfter();
        
        // Update local database
        new Thread(() -> {
            try {
                currentUserEntity.setVirtualCredit(newCredit);
                currentUserEntity.setCreditUpdatedAt(System.currentTimeMillis());
                
                // Update tracking fields
                if ("deposit".equals(transaction.getTransactionType())) {
                    currentUserEntity.setTotalCreditUsed(
                        currentUserEntity.getTotalCreditUsed() + transaction.getAmount());
                } else {
                    currentUserEntity.setTotalCreditEarned(
                        currentUserEntity.getTotalCreditEarned() + transaction.getAmount());
                }
                
                database.userDao().updateUser(currentUserEntity);
                
                availableCredit = newCredit;
                runOnUiThread(() -> updateCreditDisplay());
                
                Log.d(TAG, "Credit updated locally: " + newCredit);
            } catch (Exception e) {
                Log.e(TAG, "Error updating credit locally", e);
            }
        }).start();

        // Try to update Firestore in background (don't block on this)
        updateCreditInFirestoreBackground(newCredit, transaction);
    }
    
    private void updateCreditInFirestoreBackground(double newCredit, TransactionEntity transaction) {
        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("virtualCredit", newCredit);
            updates.put("creditUpdatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            
            if ("deposit".equals(transaction.getTransactionType())) {
                updates.put("totalCreditUsed", com.google.firebase.firestore.FieldValue.increment(transaction.getAmount()));
            } else {
                updates.put("totalCreditEarned", com.google.firebase.firestore.FieldValue.increment(transaction.getAmount()));
            }

            db.collection("users").document(activeUserId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Credit synced to Firestore in background");
                    })
                    .addOnFailureListener(e -> {
                        Log.d(TAG, "Background Firestore sync failed (offline mode): " + e.getMessage());
                    });
        } catch (Exception e) {
            Log.d(TAG, "Background Firestore sync failed (offline mode): " + e.getMessage());
        }
    }
    
}
