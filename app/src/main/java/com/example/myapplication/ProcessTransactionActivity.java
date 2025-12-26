package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.CustomerEntity;
import com.example.myapplication.database.entities.OperatorActionEntity;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.database.entities.TransactionEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.SessionManager;
import com.example.myapplication.utils.CommissionCalculator;
import com.example.myapplication.utils.OperatorBalanceHelper;
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
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import android.util.Size;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.example.myapplication.utils.MRZParser;
import java.util.concurrent.ExecutionException;

public class ProcessTransactionActivity extends AppCompatActivity {

    private static final String TAG = "ProcessTransaction";
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final String REGULAR_CUSTOMER_ID = "REGULAR_CUSTOMER";

    // UI Elements
    private Spinner spinnerOperator, spinnerAction, spinnerCustomer;
    private EditText etAmount, etNote, etCustomerSearch, etAccountNumber;
    private TextView tvAvailableCredit;
    private Button btnExecuteTransaction;
    private android.widget.CheckBox cbUseAccountNumberInUssd;
    private android.widget.ScrollView scrollView;
    
    // Scanner components (for customer form dialog)
    private PreviewView previewView;
    private TextRecognizer textRecognizer;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private boolean isScanning = false;
    private android.view.View cameraPreviewCard;
    private Button btnStopScanning;
    private java.util.Map<String, Integer> mrzDetectionCounts = new java.util.HashMap<>();
    private int detectionAttempts = 0;
    private int frameCounter = 0;
    private long lastProcessTime = 0;
    private static final long MIN_PROCESS_INTERVAL_MS = 200;
    private long scanningStartTime = 0;
    private String lastDetectedMRZ = null;

    // Data
    private AppDatabase database;
    private FirebaseFirestore db;
    private FirebaseUser firebaseUser; // only for online auth when needed
    private SessionManager sessionManager;
    private UserEntity currentUserEntity;
    private String activeUserId; // single source of truth for current user id
    private OperatorBalanceHelper balanceHelper;
    private LanguageManager languageManager;
    private static long lastLanguageChangeTime = 0;

    private List<OperatorEntity> operators = new ArrayList<>();
    private List<OperatorActionEntity> actions = new ArrayList<>();
    private List<CustomerEntity> customers = new ArrayList<>();

    private OperatorEntity selectedOperator;
    private OperatorActionEntity selectedAction;
    private CustomerEntity selectedCustomer;
    private String selectedTransactionType;

    private double availableCredit = 0.0; // Current operator-specific balance
    
    /**
     * Create a Regular Customer entity for quick transactions
     */
    private CustomerEntity createRegularCustomer() {
        CustomerEntity regularCustomer = new CustomerEntity();
        regularCustomer.setId(REGULAR_CUSTOMER_ID);
        regularCustomer.setFullName("Regular Customer");
        regularCustomer.setPhoneNumber(""); // Will be set from account number field
        return regularCustomer;
    }

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
        
        // Apply language handled via base context in app; no explicit call needed here
        LanguageManager languageManager = LanguageManager.getInstance(this);
        
        setContentView(R.layout.activity_process_transaction);
        
        // Setup window insets for header
        com.example.myapplication.utils.EdgeToEdgeHelper.setupHeaderInsets(findViewById(R.id.headerLayout), this);

        // Initialize services
        db = FirebaseFirestore.getInstance();
        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        database = AppDatabase.getDatabase(this);
        sessionManager = new SessionManager(this);
        balanceHelper = new OperatorBalanceHelper(database);
        languageManager = LanguageManager.getInstance(this);

        // Initialize UI
        initViews();
        
        // Setup language selector after views are initialized
        setupLanguageSelector();
        
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
        // Update language flag in case language was changed elsewhere
        updateLanguageFlag();
        // Refresh data when returning to activity (e.g., after adding customers/operators)
        loadCustomers();
        loadOperators();
        loadUserData(); // Refresh credit as well (this will reload operator balance)
    }

    private void initViews() {
        spinnerOperator = findViewById(R.id.spinnerOperator);
        spinnerAction = findViewById(R.id.spinnerAction);
        spinnerCustomer = findViewById(R.id.spinnerCustomer);
        etAmount = findViewById(R.id.etAmount);
        etNote = findViewById(R.id.etNote);
        etCustomerSearch = findViewById(R.id.etCustomerSearch);
        etAccountNumber = findViewById(R.id.etAccountNumber);
        cbUseAccountNumberInUssd = findViewById(R.id.cbUseAccountNumberInUssd);
        tvAvailableCredit = findViewById(R.id.tvAvailableCredit);
        btnExecuteTransaction = findViewById(R.id.btnExecuteTransaction);
        
        // Initialize MRZ text recognizer (offline)
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        
        // Header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.process_transaction));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Setup listeners
        setupListeners();
        
        // Add thousands separator to amount field
        com.example.myapplication.utils.NumberFormatter.addThousandsSeparator(etAmount);
        
        // Setup auto-scroll for input fields when keyboard appears
        setupAutoScrollForInputFields();
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

    private void setupListeners() {
        spinnerOperator.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < operators.size()) { // No placeholder, direct index
                    selectedOperator = operators.get(position);
                    loadActions(selectedOperator.getId());
                    // Load operator-specific balance when operator is selected
                    loadOperatorBalance();
                } else {
                    selectedOperator = null;
                    actions.clear();
                    updateActionSpinner();
                    availableCredit = 0.0;
                    updateCreditDisplay();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedOperator = null;
                availableCredit = 0.0;
                updateCreditDisplay();
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
                // Position 0 is "Add Customer" option
                if (position == 0) {
                    // Open customer form dialog
                    showAddCustomerDialog();
                    // Reset spinner to Regular Customer (position 1) after dialog
                    spinnerCustomer.setSelection(1);
                } else if (position == 1) {
                    // Position 1 is "Regular Customer"
                    selectedCustomer = createRegularCustomer();
                } else if (position > 1 && position <= customers.size() + 1) {
                    // Position 2+ are actual customers (position - 2 because of "Add Customer" and "Regular Customer")
                    selectedCustomer = customers.get(position - 2);
                } else {
                    selectedCustomer = null;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedCustomer = null;
            }
        });

        // Setup customer phone number search
        etCustomerSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchCustomersByPhone(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        btnExecuteTransaction.setOnClickListener(v -> executeTransaction());
    }
    
    /**
     * Setup auto-scroll for input fields to ensure they are visible above keyboard
     */
    private void setupAutoScrollForInputFields() {
        if (scrollView == null) {
            return;
        }
        
        // List of all EditText fields that need auto-scroll
        EditText[] inputFields = {etAmount, etAccountNumber, etNote, etCustomerSearch};
        
        for (EditText field : inputFields) {
            if (field == null) continue;
            
            field.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    // Post to ensure layout is complete before scrolling
                    field.post(() -> {
                        scrollToView(field);
                    });
                }
            });
            
            // Also handle when field is clicked (for cases where focus might already be set)
            field.setOnClickListener(v -> {
                field.post(() -> {
                    scrollToView(field);
                });
            });
        }
    }
    
    /**
     * Scroll the ScrollView to make the given view visible above the keyboard
     */
    private void scrollToView(View view) {
        if (scrollView == null || view == null) {
            return;
        }
        
        // Use a small delay to ensure keyboard animation has started
        view.postDelayed(() -> {
            // Get the location of the view relative to its parent ScrollView
            int scrollY = scrollView.getScrollY();
            
            // Get view position relative to ScrollView content
            int viewTop = 0;
            View currentView = view;
            while (currentView != null && currentView != scrollView) {
                viewTop += currentView.getTop();
                ViewParent parent = currentView.getParent();
                if (parent instanceof ViewGroup) {
                    currentView = (View) parent;
                } else {
                    break;
                }
            }
            
            // Get visible area of ScrollView
            android.graphics.Rect scrollBounds = new android.graphics.Rect();
            scrollView.getHitRect(scrollBounds);
            
            // Get keyboard height
            android.graphics.Rect windowRect = new android.graphics.Rect();
            getWindow().getDecorView().getWindowVisibleDisplayFrame(windowRect);
            int screenHeight = getWindow().getDecorView().getHeight();
            int keyboardHeight = screenHeight - windowRect.bottom;
            
            // Calculate target scroll position
            // We want the view to be visible with some padding above the keyboard
            int scrollPadding = 150; // Padding in pixels above keyboard
            int targetScrollY;
            
            if (keyboardHeight > 0) {
                // Keyboard is visible - scroll to show field above keyboard
                int visibleAreaHeight = scrollView.getHeight() - keyboardHeight;
                int viewBottom = viewTop + view.getHeight();
                
                // If view is below visible area (accounting for keyboard), scroll up
                if (viewBottom > visibleAreaHeight - scrollPadding) {
                    targetScrollY = viewTop - scrollPadding;
                } else if (viewTop < scrollY) {
                    // View is above current scroll position, scroll to show it
                    targetScrollY = viewTop - scrollPadding;
                } else {
                    // View is already visible, no need to scroll
                    return;
                }
            } else {
                // Keyboard not visible, just ensure view is visible
                if (viewTop < scrollY) {
                    targetScrollY = viewTop - scrollPadding;
                } else if (viewTop + view.getHeight() > scrollY + scrollView.getHeight()) {
                    targetScrollY = viewTop - scrollPadding;
                } else {
                    // View is already visible
                    return;
                }
            }
            
            // Ensure we don't scroll beyond bounds
            int maxScroll = Math.max(0, scrollView.getChildAt(0).getHeight() - scrollView.getHeight());
            targetScrollY = Math.max(0, Math.min(targetScrollY, maxScroll));
            
            // Smooth scroll to target position
            scrollView.smoothScrollTo(0, targetScrollY);
        }, 100); // Small delay to allow keyboard animation to start
    }

    private void loadUserData() {
        if (activeUserId == null) {
            Log.e(TAG, "No current user found");
            return;
        }

        Log.d(TAG, "Loading data for user: " + activeUserId);

        new Thread(() -> {
            try {
                // Load from local database first (offline-first approach)
                currentUserEntity = database.userDao().getUserById(activeUserId);
                
                if (currentUserEntity == null) {
                    Log.e(TAG, "No user found in local database for: " + activeUserId);
                }

                // Load operator-specific balance if operator is selected
                runOnUiThread(() -> {
                    if (selectedOperator != null) {
                        loadOperatorBalance();
                    } else {
                        availableCredit = 0.0;
                        updateCreditDisplay();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading user data", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading user data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * Load operator-specific balance for the currently selected operator.
     */
    private void loadOperatorBalance() {
        if (activeUserId == null || selectedOperator == null) {
            availableCredit = 0.0;
            updateCreditDisplay();
            return;
        }
        
        new Thread(() -> {
            try {
                // Ensure balances are recalculated from transactions if needed
                // Check if there are transactions that need to be accounted for
                List<com.example.myapplication.database.entities.TransactionEntity> transactions = 
                    database.transactionDao().getTransactionsByUser(activeUserId);
                
                // If there are transactions, ensure balances are up to date
                if (transactions != null && !transactions.isEmpty()) {
                    // Check if this operator has a balance record
                    com.example.myapplication.database.entities.OperatorBalanceEntity existingBalance = 
                        database.operatorBalanceDao().getBalance(activeUserId, selectedOperator.getId());
                    
                    // If no balance exists or balance seems incorrect, trigger recalculation
                    // (The recalculation will run in background and update all operators)
                    if (existingBalance == null) {
                        Log.d(TAG, "No balance record found for operator " + selectedOperator.getName() + ", triggering recalculation");
                        balanceHelper.recalculateBalancesFromTransactions(activeUserId);
                    }
                }
                
                // Get operator-specific balance (after ensuring it's calculated)
                double operatorBalance = balanceHelper.getBalance(activeUserId, selectedOperator.getId());
                availableCredit = operatorBalance;
                
                Log.d(TAG, "Loaded balance for operator " + selectedOperator.getName() + " (ID: " + selectedOperator.getId() + "): " + operatorBalance);
                
                runOnUiThread(() -> updateCreditDisplay());
            } catch (Exception e) {
                Log.e(TAG, "Error loading operator balance", e);
                runOnUiThread(() -> {
                    availableCredit = 0.0;
                    updateCreditDisplay();
                });
            }
        }).start();
    }

    private void updateCreditDisplay() {
        // Display with thousands separators
        tvAvailableCredit.setText(com.example.myapplication.utils.NumberFormatter.formatWithThousandsSeparator(availableCredit));
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
                        final long finalFirestoreUpdateTime = firestoreUpdateTime;
                        Log.d(TAG, "Updated credit from Firestore for user " + activeUserId + ": " + availableCredit);
                        
                        // Update local database
                        new Thread(() -> {
                            try {
                                if (currentUserEntity != null) {
                                    currentUserEntity.setVirtualCredit(availableCredit);
                                    // CRITICAL: Set creditUpdatedAt to Firestore's timestamp
                                    currentUserEntity.setCreditUpdatedAt(finalFirestoreUpdateTime);
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
                    
                    // If no timestamp in Firestore, use current time
                    if (firestoreUpdateTime == 0) {
                        firestoreUpdateTime = System.currentTimeMillis();
                    }
                    
                    final long finalFirestoreUpdateTime = firestoreUpdateTime;
                    Log.d(TAG, "Updated credit from Firestore for user " + activeUserId + ": " + availableCredit);
                    
                    // Update local database
                    new Thread(() -> {
                        try {
                            if (currentUserEntity != null) {
                                currentUserEntity.setVirtualCredit(availableCredit);
                                // CRITICAL: Set creditUpdatedAt to Firestore's timestamp
                                currentUserEntity.setCreditUpdatedAt(finalFirestoreUpdateTime);
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

    private void searchCustomersByPhone(String phoneQuery) {
        if (TextUtils.isEmpty(phoneQuery)) {
            // If search is empty, load all customers
            loadCustomers();
            return;
        }

        new Thread(() -> {
            try {
                List<CustomerEntity> searchResults;
                if (activeUserId != null) {
                    // Search customers by phone number for current user
                    searchResults = database.customerDao().searchCustomersByUser(activeUserId, phoneQuery);
                } else {
                    searchResults = new ArrayList<>();
                }

                runOnUiThread(() -> {
                    customers.clear();
                    customers.addAll(searchResults);
                    updateCustomerSpinner();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error searching customers by phone", e);
                runOnUiThread(() -> Toast.makeText(this, "Error searching customers", Toast.LENGTH_SHORT).show());
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
            // Temporarily remove listener to avoid triggering loadActions during initial setup
            AdapterView.OnItemSelectedListener currentListener = spinnerOperator.getOnItemSelectedListener();
            spinnerOperator.setOnItemSelectedListener(null);
            spinnerOperator.setSelection(0, false); // false = don't trigger listener
            // Now manually trigger action loading and balance loading for the first operator
            if (operators.size() > 0) {
                selectedOperator = operators.get(0);
                loadActions(selectedOperator.getId());
                // Load operator-specific balance for the first operator
                loadOperatorBalance();
            }
            // Restore listener
            spinnerOperator.setOnItemSelectedListener(currentListener);
        } else {
            // Clear actions if no operators
            actions.clear();
            updateActionSpinner();
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
        
        // Temporarily remove listener to avoid triggering during adapter update
        AdapterView.OnItemSelectedListener currentListener = spinnerAction.getOnItemSelectedListener();
        spinnerAction.setOnItemSelectedListener(null);
        
        spinnerAction.setAdapter(adapter);
        
        if (!actionNames.isEmpty()) {
            spinnerAction.setSelection(0, false); // false = don't trigger listener
            selectedAction = actions.get(0);
        } else {
            selectedAction = null;
            // Clear selection if no actions
            spinnerAction.setSelection(0, false);
        }
        
        // Restore listener
        spinnerAction.setOnItemSelectedListener(currentListener);
    }

    private void updateCustomerSpinner() {
        List<String> customerNames = new ArrayList<>();
        
        // Add "Add Customer" option at the beginning
        customerNames.add("+ " + getString(R.string.add_customer));
        
        // Add "Regular Customer" as default option (position 1)
        customerNames.add("Regular Customer");
        
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
                if (position == 0) {
                    // "Add Customer" option - use primary orange color
                    textView.setTextColor(getResources().getColor(R.color.primary_orange, null));
                } else if (position == 1) {
                    // "Regular Customer" option - use primary purple color
                    textView.setTextColor(getResources().getColor(R.color.primary_purple, null));
                } else {
                    textView.setTextColor(getResources().getColor(R.color.text_primary, null));
                }
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                if (position == 0) {
                    // "Add Customer" option - use primary orange color
                    textView.setTextColor(getResources().getColor(R.color.primary_orange, null));
                } else if (position == 1) {
                    // "Regular Customer" option - use primary purple color
                    textView.setTextColor(getResources().getColor(R.color.primary_purple, null));
                } else {
                    textView.setTextColor(getResources().getColor(R.color.text_primary, null));
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCustomer.setAdapter(adapter);
        
        // Store the current listener
        AdapterView.OnItemSelectedListener currentListener = spinnerCustomer.getOnItemSelectedListener();
        
        // Temporarily remove listener to prevent triggering on initial setup
        spinnerCustomer.setOnItemSelectedListener(null);
        
        // Always select Regular Customer as default (position 1)
        spinnerCustomer.setSelection(1, false); // Position 1 = Regular Customer - false = don't trigger listener
        selectedCustomer = createRegularCustomer();
        
        // Clear any touch listener that might have been set when there were no customers
        spinnerCustomer.setOnTouchListener(null);
        
        // Restore listener
        spinnerCustomer.setOnItemSelectedListener(currentListener);
    }
    
    private void showAddCustomerDialog() {
        // Use CustomerManagementActivity's customer form dialog logic
        // We'll create a simplified version here
        CustomerEntity newCustomer = new CustomerEntity();
        
        // Inflate and show customer form dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_customer_form, null);
        builder.setView(dialogView);
        
        // Initialize form fields
        EditText etFullName = dialogView.findViewById(R.id.etFullName);
        Spinner spinnerDocumentType = dialogView.findViewById(R.id.spinnerDocumentType);
        EditText etDateOfBirth = dialogView.findViewById(R.id.etDateOfBirth);
        EditText etNationalId = dialogView.findViewById(R.id.etNationalId);
        EditText etIssueDate = dialogView.findViewById(R.id.etIssueDate);
        EditText etExpiryDate = dialogView.findViewById(R.id.etExpiryDate);
        EditText etPhoneNumber = dialogView.findViewById(R.id.etPhoneNumber);
        EditText etAddress = dialogView.findViewById(R.id.etAddress);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        
        // Setup document type spinner (same as CustomerManagementActivity)
        String[] documentTypes = getResources().getStringArray(R.array.document_types);
        android.widget.ArrayAdapter<String> styledAdapter = new android.widget.ArrayAdapter<String>(this, 
                android.R.layout.simple_spinner_item, documentTypes) {
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
        styledAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDocumentType.setAdapter(styledAdapter);
        
        // Setup date of birth auto-formatting - use DD-MM-YYYY format
        setupDateOfBirthFormatting(etDateOfBirth);
        setupDateFormatting(etIssueDate);
        setupDateFormatting(etExpiryDate);
        
        builder.setTitle(getString(R.string.add_customer))
                .setPositiveButton(getString(R.string.save_customer), null)
                .setNegativeButton(getString(android.R.string.cancel), null);
        
        AlertDialog dialog = builder.create();
        
        // Setup scanner button (after dialog is created so we can pass it)
        android.view.View btnScanBarcode = dialogView.findViewById(R.id.btnScanBarcode);
        if (btnScanBarcode != null) {
            btnScanBarcode.setOnClickListener(v -> {
                // Open scanner dialog
                showScannerDialog(dialog, etFullName, etDateOfBirth, spinnerDocumentType, 
                        documentTypes, etNationalId, etIssueDate, etExpiryDate);
            });
        }
        
        dialog.setOnShowListener(dlg -> {
            android.widget.Button btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setOnClickListener(v -> {
                // Validate and save customer
                if (validateAndSaveCustomer(newCustomer, etFullName, etDateOfBirth, 
                        spinnerDocumentType, documentTypes, etNationalId, etIssueDate, etExpiryDate, 
                        etPhoneNumber, etAddress, etEmail)) {
                    dialog.dismiss();
                    // Reload customers and select the new one
                    loadCustomers();
                }
            });
        });
        dialog.show();
    }
    
    private void showScannerDialog(AlertDialog parentDialog, EditText etFullName, EditText etDateOfBirth,
                                  Spinner spinnerDocumentType, String[] documentTypes,
                                  EditText etNationalId, EditText etIssueDate, EditText etExpiryDate) {
        // Create scanner dialog
        AlertDialog.Builder scannerBuilder = new AlertDialog.Builder(this);
        android.view.View scannerView = getLayoutInflater().inflate(R.layout.dialog_scanner, null);
        scannerBuilder.setView(scannerView);
        
        PreviewView scannerPreviewView = scannerView.findViewById(R.id.previewView);
        Button btnStopScanning = scannerView.findViewById(R.id.btnStopScanning);
        cameraPreviewCard = scannerView.findViewById(R.id.cameraPreviewCard);
        
        AlertDialog scannerDialog = scannerBuilder.create();
        // Make dialog window background transparent so only our scanner card is visible
        if (scannerDialog.getWindow() != null) {
            scannerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        btnStopScanning.setOnClickListener(v -> {
            stopBarcodeScanning();
            scannerDialog.dismiss();
        });
        
        scannerDialog.setOnDismissListener(d -> stopBarcodeScanning());
        
        // Start scanning
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        
        previewView = scannerPreviewView;
        isScanning = true;
        mrzDetectionCounts.clear();
        detectionAttempts = 0;
        frameCounter = 0;
        lastProcessTime = 0;
        scanningStartTime = System.currentTimeMillis();
        lastDetectedMRZ = null;
        
        startCameraForDialog(parentDialog, etFullName, etDateOfBirth, spinnerDocumentType, 
                documentTypes, etNationalId, etIssueDate, etExpiryDate, scannerDialog);
        
        scannerDialog.show();
    }
    
    private void startCameraForDialog(AlertDialog parentDialog, EditText etFullName, EditText etDateOfBirth,
                                     Spinner spinnerDocumentType, String[] documentTypes,
                                     EditText etNationalId, EditText etIssueDate, EditText etExpiryDate,
                                     AlertDialog scannerDialog) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(this);
        
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1920, 1920))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build();
                
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), image -> analyzeImageForDialog(
                        image, parentDialog, etFullName, etDateOfBirth, spinnerDocumentType, 
                        documentTypes, etNationalId, etIssueDate, etExpiryDate, scannerDialog));
                
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                try { cameraProvider.unbindAll(); } catch (Exception ignored) {}
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                Toast.makeText(this, "Error starting camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }
    
    private void analyzeImageForDialog(ImageProxy image, AlertDialog parentDialog, EditText etFullName, 
                                      EditText etDateOfBirth, Spinner spinnerDocumentType, String[] documentTypes,
                                      EditText etNationalId, EditText etIssueDate, EditText etExpiryDate,
                                      AlertDialog scannerDialog) {
        if (!isScanning) { image.close(); return; }
        if (image.getImage() == null) { image.close(); return; }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime < MIN_PROCESS_INTERVAL_MS) {
            image.close();
            return;
        }
        
        frameCounter++;
        lastProcessTime = currentTime;
        
        int rotationDegrees = image.getImageInfo().getRotationDegrees();
        InputImage inputImage = InputImage.fromMediaImage(image.getImage(), rotationDegrees);
        
        textRecognizer.process(inputImage)
                .addOnSuccessListener(text -> {
                    if (text != null && isScanning) {
                        // Extract and correct MRZ text using same logic as CustomerManagementActivity
                        String mrzText = extractAndCorrectMRZ(text);
                        if (mrzText != null && !mrzText.trim().isEmpty() && isValidMRZ(mrzText)) {
                            String normalizedMRZ = normalizeMRZ(mrzText);
                            isScanning = false;
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Processing MRZ...", Toast.LENGTH_SHORT).show();
                                processMRZDataForDialog(normalizedMRZ, parentDialog, etFullName, etDateOfBirth, 
                                        spinnerDocumentType, documentTypes, etNationalId, etIssueDate, etExpiryDate);
                                scannerDialog.dismiss();
                                stopBarcodeScanning();
                            });
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "MRZ text recognition failed", e))
                .addOnCompleteListener(result -> image.close());
    }
    
    // === MRZ extraction and validation (shared behavior with CustomerManagementActivity) ===

    private String normalizeMRZ(String mrz) {
        if (mrz == null) return "";
        return mrz.toUpperCase().trim()
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n");
    }

    private String extractAndCorrectMRZ(Text text) {
        if (text == null) {
            return null;
        }

        String rawMRZ = extractMRZText(text);
        if (rawMRZ == null || rawMRZ.trim().isEmpty()) {
            return null;
        }

        return correctMRZOCRErrors(rawMRZ);
    }

    private String extractMRZText(Text text) {
        if (text == null) {
            Log.w(TAG, "Text recognition returned null");
            return null;
        }

        String allText = text.getText();
        Log.d(TAG, "========== RECOGNIZED TEXT (Sale MRZ) ==========");
        Log.d(TAG, "Full text: " + allText);

        java.util.List<String> mrzLines = new java.util.ArrayList<>();

        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String lineText = line.getText().trim();
                lineText = lineText.toUpperCase();

                String normalized = lineText
                        .replaceAll("\\|+", "<")
                        .replaceAll("[/\\\\]+", "<")
                        .replaceAll("\\s+", "")
                        .replaceAll("[^A-Z0-9<]", "");

                if ((normalized.length() >= 28 && normalized.length() <= 32) ||
                    (normalized.length() >= 42 && normalized.length() <= 46)) {

                    long alphanumericCount = normalized.chars()
                            .filter(c -> (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '<')
                            .count();

                    double alphanumericRatio = (double) alphanumericCount / normalized.length();

                    if (alphanumericRatio >= 0.90) {
                        boolean looksLikeMRZ = false;

                        if (normalized.length() >= 2) {
                            char firstChar = normalized.charAt(0);
                            char secondChar = normalized.length() > 1 ? normalized.charAt(1) : ' ';

                            if ((firstChar == 'I' || firstChar == 'P' || firstChar == 'A' || firstChar == 'C') &&
                                    (secondChar == '<' || secondChar == 'D' || secondChar == 'P')) {
                                if (normalized.length() >= 5) {
                                    String possibleCountry = normalized.substring(2, 5);
                                    if (possibleCountry.matches("[A-Z]{3}")) {
                                        looksLikeMRZ = true;
                                    }
                                }
                            } else if (Character.isDigit(firstChar) && normalized.length() >= 14) {
                                String first6 = normalized.substring(0, 6);
                                if (first6.matches("\\d{6}") || first6.replaceAll("[OIL]", "").matches("\\d+")) {
                                    if (normalized.length() > 7) {
                                        char genderPos = normalized.charAt(7);
                                        if (genderPos == 'M' || genderPos == 'F' || genderPos == 'X' || genderPos == 'H' || genderPos == 'N') {
                                            looksLikeMRZ = true;
                                        }
                                    }
                                }
                            } else if (normalized.length() >= 42 && normalized.length() <= 46) {
                                int sixDigitSequences = 0;
                                for (int i = 0; i <= normalized.length() - 6; i++) {
                                    String sub = normalized.substring(i, i + 6);
                                    if (sub.matches("\\d{6}")) {
                                        sixDigitSequences++;
                                        if (sixDigitSequences >= 2) break;
                                    }
                                }

                                if (sixDigitSequences >= 2) {
                                    looksLikeMRZ = true;
                                }
                            } else if (normalized.contains("<<")) {
                                long letterCount = normalized.chars().filter(c -> c >= 'A' && c <= 'Z').count();
                                long fillerCount = normalized.chars().filter(c -> c == '<').count();
                                if (letterCount >= 10 && (letterCount + fillerCount) >= normalized.length() * 0.95) {
                                    looksLikeMRZ = true;
                                }
                            }
                        }

                        if (looksLikeMRZ) {
                            mrzLines.add(normalized);
                        }
                    }
                }
            }
        }

        if (mrzLines.size() == 3 || mrzLines.size() == 2) {
            return String.join("\n", mrzLines);
        }

        return null;
    }

    private String correctMRZOCRErrors(String mrz) {
        if (mrz == null) return null;

        String[] lines = mrz.split("\n");
        StringBuilder corrected = new StringBuilder();

        boolean isTD3 = (lines.length == 2 && lines[0].length() >= 42);
        boolean isTD1 = (lines.length == 3 && lines[0].length() >= 28 && lines[0].length() <= 32);

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            String line = lines[lineIdx].toUpperCase();
            StringBuilder correctedLine = new StringBuilder();

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                char correctedChar = c;

                if (isTD1) {
                    if (lineIdx == 0) {
                        if (i == 0) {
                            if (c == '1' || c == 'L' || c == 'J') correctedChar = 'I';
                            else if (c == '0' || c == 'O' || c == 'D') correctedChar = 'O';
                        } else if (i >= 2 && i <= 4) {
                            correctedChar = correctToLetter(c);
                        } else if (i >= 5 && i <= 14) {
                            if (c == 'O') correctedChar = '0';
                            else if (c == 'I' && i > 5) correctedChar = '1';
                        }
                    } else if (lineIdx == 1) {
                        if (i >= 0 && i <= 5) {
                            correctedChar = correctToDigit(c);
                        } else if (i == 7) {
                            if (c == 'N' || c == 'H') correctedChar = 'M';
                            else if (c == 'E' || c == 'P') correctedChar = 'F';
                        } else if (i >= 8 && i <= 13) {
                            correctedChar = correctToDigit(c);
                        } else if (i >= 15 && i <= 17) {
                            correctedChar = correctToLetter(c);
                        }
                    } else if (lineIdx == 2) {
                        if (c != '<') {
                            correctedChar = correctToLetter(c);
                        }
                    }
                } else if (isTD3) {
                    if (lineIdx == 0) {
                        if (i == 0) {
                            if (c == '0' || c == 'O' || c == 'D' || c == '1' || c == 'I' || c == 'L') correctedChar = 'P';
                        } else if (i >= 2 && i <= 4) {
                            correctedChar = correctToLetter(c);
                        } else if (i >= 5) {
                            if (c != '<' && !Character.isLetter(c)) {
                                correctedChar = correctToLetter(c);
                            }
                        }
                    } else if (lineIdx == 1) {
                        if (i >= 13 && i <= 18) {
                            correctedChar = correctToDigit(c);
                        } else if (i == 20) {
                            if (c == 'N' || c == 'H') correctedChar = 'M';
                            else if (c == 'E' || c == 'P') correctedChar = 'F';
                        } else if (i >= 21 && i <= 26) {
                            correctedChar = correctToDigit(c);
                        } else if (i >= 10 && i <= 12) {
                            correctedChar = correctToLetter(c);
                        }
                    }
                }

                if (correctedChar == c) {
                    switch (c) {
                        case '|': case '/': case '\\': case 'l': case 'L': case '!': case '1':
                            if ((i > 0 && line.charAt(i-1) == '<') ||
                                (i < line.length()-1 && line.charAt(i+1) == '<') ||
                                i >= line.length() - 15) {
                                correctedChar = '<';
                            }
                            break;
                    }
                }

                correctedLine.append(correctedChar);
            }

            if (lineIdx > 0) corrected.append('\n');
            corrected.append(correctedLine);
        }

        return corrected.toString();
    }

    private char correctToDigit(char c) {
        switch (c) {
            case 'O': case 'Q': case 'D': return '0';
            case 'I': case 'L': case 'l': case '|': return '1';
            case 'Z': case 'z': return '2';
            case 'S': case 's': return '5';
            case 'G': case 'g': return '6';
            case 'T': case 't': return '7';
            case 'B': return '8';
            default: return c;
        }
    }

    private char correctToLetter(char c) {
        switch (c) {
            case '0': return 'O';
            case '1': return 'I';
            case '2': return 'Z';
            case '5': return 'S';
            case '6': return 'G';
            case '7': return 'T';
            case '8': return 'B';
            default: return Character.isLetter(c) ? Character.toUpperCase(c) : c;
        }
    }

    private boolean isValidMRZ(String mrzText) {
        if (mrzText == null || mrzText.trim().isEmpty()) {
            return false;
        }

        String[] lines = mrzText.split("\n");

        if (lines.length != 2 && lines.length != 3) {
            return false;
        }

        for (String line : lines) {
            String l = line.trim();
            if (l.length() < 28) {
                return false;
            }

            long validChars = l.chars()
                    .filter(c -> (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '<')
                    .count();
            double ratio = (double) validChars / l.length();
            if (ratio < 0.95) {
                return false;
            }
        }

        return true;
    }
    
    private void processMRZDataForDialog(String mrzText, AlertDialog parentDialog, EditText etFullName, 
                                         EditText etDateOfBirth, Spinner spinnerDocumentType, String[] documentTypes,
                                         EditText etNationalId, EditText etIssueDate, EditText etExpiryDate) {
        try {
            MRZParser.MRZData mrzData = MRZParser.parseMRZ(mrzText);
            if (mrzData == null || !mrzData.isValid()) {
                Toast.makeText(this, "Unable to read MRZ data. Please try again.", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Populate form fields
            if (mrzData.fullName != null) etFullName.setText(mrzData.fullName);
            if (mrzData.nationalIdNumber != null) {
                etNationalId.setText(mrzData.nationalIdNumber);
            } else if (mrzData.documentNumber != null) {
                etNationalId.setText(mrzData.documentNumber);
            }
            
            // Convert dates from YYYY-MM-DD to DD-MM-YYYY for display
            if (mrzData.dateOfBirth != null) {
                String dobDisplay = convertDateToDDMMYYYY(mrzData.dateOfBirth);
                etDateOfBirth.setText(dobDisplay);
            }
            // Note: MRZ doesn't contain issue date, only expiry date
            if (mrzData.expiryDate != null) {
                String expiryDisplay = convertDateToDDMMYYYY(mrzData.expiryDate);
                etExpiryDate.setText(expiryDisplay);
            }
            
            // Set document type
            if (mrzData.documentTypeName != null) {
                for (int i = 0; i < documentTypes.length; i++) {
                    if (documentTypes[i].equalsIgnoreCase(mrzData.documentTypeName)) {
                        spinnerDocumentType.setSelection(i);
                        break;
                    }
                }
            }
            
            Toast.makeText(this, "MRZ data loaded successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error processing MRZ data", e);
            Toast.makeText(this, "Error processing MRZ data", Toast.LENGTH_SHORT).show();
        }
    }
    
    private String convertDateToDDMMYYYY(String date) {
        if (date == null || date.trim().isEmpty()) return "";
        try {
            String cleanDate = date.trim();
            if (cleanDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                String[] parts = cleanDate.split("-");
                return parts[2] + "-" + parts[1] + "-" + parts[0];
            }
            return date;
        } catch (Exception e) {
            return date;
        }
    }
    
    private void stopBarcodeScanning() {
        isScanning = false;
        if (cameraPreviewCard != null) {
            cameraPreviewCard.setVisibility(View.GONE);
        }
        if (previewView != null) {
            previewView.setVisibility(View.GONE);
        }
        mrzDetectionCounts.clear();
        detectionAttempts = 0;
        frameCounter = 0;
        lastProcessTime = 0;
        scanningStartTime = 0;
        lastDetectedMRZ = null;
        try {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }
        } catch (Exception ignored) {}
        camera = null;
    }
    
    // Date formatting method - DD-MM-YYYY format
    private void setupDateOfBirthFormatting(EditText etDateOfBirth) {
        etDateOfBirth.addTextChangedListener(new android.text.TextWatcher() {
            private boolean isFormatting = false;
            
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isFormatting) return;
                isFormatting = true;
                
                String input = s.toString().replaceAll("[^\\d]", "");
                StringBuilder formatted = new StringBuilder();
                
                // Format as DD-MM-YYYY
                for (int i = 0; i < input.length() && i < 8; i++) {
                    if (i == 2 || i == 4) {
                        formatted.append("-");
                    }
                    formatted.append(input.charAt(i));
                }
                
                int cursorPos = formatted.length();
                if (cursorPos > 10) cursorPos = 10;
                
                etDateOfBirth.setText(formatted.toString());
                etDateOfBirth.setSelection(Math.min(cursorPos, formatted.length()));
                
                isFormatting = false;
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }
    
    private void setupDateFormatting(EditText etDate) {
        etDate.addTextChangedListener(new android.text.TextWatcher() {
            private boolean isFormatting = false;
            
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isFormatting) return;
                isFormatting = true;
                
                String input = s.toString().replaceAll("[^\\d]", "");
                StringBuilder formatted = new StringBuilder();
                
                // Format as DD-MM-YYYY
                for (int i = 0; i < input.length() && i < 8; i++) {
                    if (i == 2 || i == 4) {
                        formatted.append("-");
                    }
                    formatted.append(input.charAt(i));
                }
                
                int cursorPos = formatted.length();
                if (cursorPos > 10) cursorPos = 10;
                
                etDate.setText(formatted.toString());
                etDate.setSelection(Math.min(cursorPos, formatted.length()));
                
                isFormatting = false;
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }
    
    private void showDuplicatePhoneDialogInTransaction(CustomerEntity existingCustomer, CustomerEntity newCustomer,
                                                       EditText etFullName, EditText etDateOfBirth,
                                                       Spinner spinnerDocumentType, String[] documentTypes,
                                                       EditText etNationalId, EditText etIssueDate,
                                                       EditText etExpiryDate, EditText etPhoneNumber,
                                                       EditText etAddress, EditText etEmail) {
        String customerName = existingCustomer.getFullName() != null ? existingCustomer.getFullName() : "N/A";
        String customerPhone = existingCustomer.getPhoneNumber() != null ? existingCustomer.getPhoneNumber() : "N/A";
        String message = String.format(getString(R.string.customer_exists_phone_message), customerName, customerPhone);
        
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.select_existing_customer))
                .setMessage(message)
                .setPositiveButton(getString(R.string.select_customer), (dialog, which) -> {
                    // Select the existing customer in the spinner
                    selectExistingCustomerInTransaction(existingCustomer);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }
    
    private void selectExistingCustomerInTransaction(CustomerEntity customer) {
        // Reload customers to ensure the list is up to date
        loadCustomers();
        
        // After customers are loaded, select the existing customer
        new android.os.Handler().postDelayed(() -> {
            // Find the customer in the list
            int position = -1;
            for (int i = 0; i < customers.size(); i++) {
                if (customers.get(i).getId().equals(customer.getId())) {
                    position = i + 1; // +1 because position 0 is "Add Customer" option
                    break;
                }
            }
            
            if (position > 0 && position <= spinnerCustomer.getCount()) {
                // Temporarily remove listener to prevent triggering on programmatic selection
                AdapterView.OnItemSelectedListener currentListener = spinnerCustomer.getOnItemSelectedListener();
                spinnerCustomer.setOnItemSelectedListener(null);
                
                // Select the customer
                spinnerCustomer.setSelection(position);
                selectedCustomer = customer;
                
                // Restore listener
                spinnerCustomer.setOnItemSelectedListener(currentListener);
                
                Toast.makeText(this, getString(R.string.customer_saved), Toast.LENGTH_SHORT).show();
            }
        }, 300); // Small delay to ensure customers list is updated
    }
    
    private boolean validateAndSaveCustomer(CustomerEntity customer, EditText etFullName, EditText etDateOfBirth,
            Spinner spinnerDocumentType, String[] documentTypes, EditText etNationalId, EditText etIssueDate,
            EditText etExpiryDate, EditText etPhoneNumber, EditText etAddress, EditText etEmail) {
        // Basic validation - Mandatory fields: Name, Document number, Phone number
        if (TextUtils.isEmpty(etFullName.getText())) {
            etFullName.setError(getString(R.string.required_fields_missing));
            return false;
        }
        if (TextUtils.isEmpty(etNationalId.getText())) {
            etNationalId.setError(getString(R.string.required_fields_missing));
            return false;
        }
        if (TextUtils.isEmpty(etPhoneNumber.getText())) {
            etPhoneNumber.setError(getString(R.string.required_fields_missing));
            return false;
        }
        
        // Save customer in background thread
        new Thread(() -> {
            try {
                String phoneNumber = etPhoneNumber.getText().toString().trim();
                String nationalId = etNationalId.getText().toString().trim();
                
                // Check if exact duplicate exists (same National ID + same phone number) for this agent/dealer
                // This allows same National ID with different phone numbers
                if (currentUserEntity != null && nationalId != null && !nationalId.isEmpty() && 
                    phoneNumber != null && !phoneNumber.isEmpty()) {
                    CustomerEntity existingCustomer = database.customerDao().getCustomerByNationalIdAndPhoneAndUser(
                            nationalId, phoneNumber, currentUserEntity.getUid());
                    if (existingCustomer != null) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Customer with this National ID and phone number already exists for your account", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                }
                
                // Check if customer with same phone number already exists for this agent (for new customers only)
                // This is separate check to show dialog for phone duplicates
                if (phoneNumber != null && !phoneNumber.isEmpty() && currentUserEntity != null) {
                    CustomerEntity existingCustomerByPhone = database.customerDao().getCustomerByPhoneNumberAndUser(phoneNumber, currentUserEntity.getUid());
                    if (existingCustomerByPhone != null) {
                        // Show dialog offering to select existing customer
                        CustomerEntity finalExistingCustomer = existingCustomerByPhone;
                        runOnUiThread(() -> {
                            showDuplicatePhoneDialogInTransaction(finalExistingCustomer, customer, etFullName, etDateOfBirth,
                                    spinnerDocumentType, documentTypes, etNationalId, etIssueDate, etExpiryDate, etPhoneNumber, etAddress, etEmail);
                        });
                        return;
                    }
                }
                
                // Set customer data
                customer.setId(etNationalId.getText().toString());
                if (currentUserEntity != null) {
                    customer.setCreatedBy(currentUserEntity.getUid());
                }
                customer.setCreatedAt(System.currentTimeMillis());
                customer.setFullName(etFullName.getText().toString());
                
                String selectedDocType = spinnerDocumentType.getSelectedItemPosition() >= 0 ? 
                        documentTypes[spinnerDocumentType.getSelectedItemPosition()] : null;
                customer.setDocumentType(selectedDocType);
                
                String dobText = etDateOfBirth.getText().toString().trim();
                customer.setDateOfBirth(dobText);
                
                customer.setNationalIdNumber(etNationalId.getText().toString());
                customer.setIssueDate(etIssueDate.getText().toString());
                customer.setExpiryDate(etExpiryDate.getText().toString());
                customer.setPhoneNumber(etPhoneNumber.getText().toString());
                customer.setAddress(etAddress.getText().toString());
                customer.setEmail(etEmail.getText().toString());
                customer.setActive(true);
                customer.setNeedsSync(true);
                customer.setUpdatedAt(System.currentTimeMillis());
                
                // Save to database
                database.customerDao().insertCustomer(customer);
                
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.customer_saved), Toast.LENGTH_SHORT).show();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving customer", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.transaction_failed) + ": " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
        
        return true;
    }

    private void executeTransaction() {
        Log.d(TAG, "Starting transaction execution");
        
        // Validation
        if (!validateTransaction()) {
            Log.d(TAG, "Transaction validation failed");
            return;
        }

        double amount = com.example.myapplication.utils.NumberFormatter.getNumericValue(etAmount.getText().toString());
        
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
        // Check if operator is USSD type AND action doesn't have USSD disabled
        boolean isUssd = "USSD".equals(selectedOperator.getType()) && 
                        (selectedAction != null && !selectedAction.isDisableUssd());
        Log.d(TAG, "Transaction type: " + (isUssd ? "USSD" : "NON_USSD"));
        if (selectedAction != null) {
            Log.d(TAG, "Action disableUssd flag: " + selectedAction.isDisableUssd());
        }

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

        // For Regular Customer, account number field must be filled (it contains the phone number)
        if (REGULAR_CUSTOMER_ID.equals(selectedCustomer.getId())) {
            if (etAccountNumber == null || etAccountNumber.getText() == null) {
                Toast.makeText(this, "Please enter phone number in Account Number field", Toast.LENGTH_SHORT).show();
                return false;
            }
            String accountNumber = etAccountNumber.getText().toString().trim();
            if (accountNumber.isEmpty()) {
                Toast.makeText(this, "Please enter phone number in Account Number field", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        // Transaction type is now auto-detected from action name

        String amountStr = etAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_amount), Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            double amount = com.example.myapplication.utils.NumberFormatter.getNumericValue(amountStr);
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
        // For transfers, user needs credit (base amount + fees)
        // For withdrawals, user gains credit
        if ("deposit".equalsIgnoreCase(transactionType)) {
            return availableCredit >= amount;
        } else if ("transfer".equalsIgnoreCase(transactionType)) {
            // Transfer needs credit for base amount + fees
            double transferRate = selectedOperator.getTransferRate();
            double transferFee = amount * (transferRate / 100.0);
            double totalAmount = amount + transferFee;
            return availableCredit >= totalAmount;
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
        
        // Check for transfer keywords
        if (actionName.contains("transfer") || 
            actionName.contains("virement") || 
            actionName.contains("transfert")) {
            return "transfer";
        }
        
        // Default to deposit for all other actions (deposit, bills, etc.)
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
        
        // For Regular Customer, get phone from account number field
        String customerPhone;
        String customerName;
        if (REGULAR_CUSTOMER_ID.equals(selectedCustomer.getId())) {
            // Regular Customer: use account number field for phone
            if (etAccountNumber != null && etAccountNumber.getText() != null) {
                customerPhone = etAccountNumber.getText().toString().trim();
            } else {
                customerPhone = "";
            }
            customerName = "Regular Customer";
        } else {
            // Regular customer: use customer's phone number
            customerPhone = selectedCustomer.getPhoneNumber() != null ? selectedCustomer.getPhoneNumber() : "";
            customerName = selectedCustomer.getFullName();
            if (customerName == null || customerName.trim().isEmpty()) {
                customerName = customerPhone != null && !customerPhone.trim().isEmpty()
                        ? customerPhone
                        : selectedCustomer.getId();
            }
        }
        
        transaction.setCustomerName(customerName);
        transaction.setCustomerPhone(customerPhone);
        transaction.setAmount(amount);
        
        // Auto-detect transaction type from action name
        transaction.setTransactionType(detectTransactionType());
        
        transaction.setChannel(channel);
        
        if (currentUserEntity != null) {
            transaction.setUserId(currentUserEntity.getUid());
            transaction.setUserName(currentUserEntity.getName());
            transaction.setUserRole(currentUserEntity.getRole());
        }
        
        // Get current operator-specific balance
        double currentOperatorBalance = balanceHelper.getBalance(activeUserId, selectedOperator.getId());
        transaction.setCreditBefore(currentOperatorBalance);
        
        // Calculate credit after based on transaction type
        double creditAfter = currentOperatorBalance;
        String transactionType = transaction.getTransactionType();
        
        if ("deposit".equals(transactionType)) {
            creditAfter -= amount; // Deposit subtracts credit
        } else if ("transfer".equals(transactionType)) {
            // Transfer: debit amount + fees from operator credit
            double transferRate = selectedOperator.getTransferRate();
            double transferFee = amount * (transferRate / 100.0);
            double totalAmount = amount + transferFee;
            creditAfter -= totalAmount; // Debit total amount (base + fees)
        } else if ("withdrawal".equals(transactionType)) {
            creditAfter += amount; // Withdrawal adds credit
        }
        transaction.setCreditAfter(creditAfter);
        
        // Set note if provided
        if (etNote != null && etNote.getText() != null) {
            String note = etNote.getText().toString().trim();
            if (!note.isEmpty()) {
                transaction.setNotes(note);
            }
        }
        
        // Set account number if provided (append to notes)
        if (etAccountNumber != null && etAccountNumber.getText() != null) {
            String accountNumber = etAccountNumber.getText().toString().trim();
            if (!accountNumber.isEmpty()) {
                String existingNotes = transaction.getNotes() != null ? transaction.getNotes() : "";
                String newNotes = existingNotes.isEmpty() 
                    ? "Account Number: " + accountNumber 
                    : existingNotes + "\nAccount Number: " + accountNumber;
                
                // Store checkbox state for USSD retry
                boolean useAccountInUssd = cbUseAccountNumberInUssd != null && cbUseAccountNumberInUssd.isChecked();
                if (useAccountInUssd) {
                    newNotes += "\nUse Account Number in USSD: true";
                }
                
                transaction.setNotes(newNotes);
            }
        }
        
        return transaction;
    }

    private String generateUssdCode(double amount) {
        // Get actual operator code and action code from database
        String operatorCode = selectedOperator.getCode();
        String actionCode = selectedAction.getActionCode();
        
        Log.d(TAG, "Operator: " + selectedOperator.getName() + ", Code: " + operatorCode);
        Log.d(TAG, "Action: " + selectedAction.getName() + ", Code: " + actionCode);
        
        // Determine which phone/account number to use in USSD code
        String phoneNumber;
        
        // Check if checkbox is checked to use account number in USSD
        boolean useAccountNumber = cbUseAccountNumberInUssd != null && cbUseAccountNumberInUssd.isChecked();
        
        if (useAccountNumber) {
            // Checkbox is checked: use account number field
            if (etAccountNumber != null && etAccountNumber.getText() != null) {
                phoneNumber = etAccountNumber.getText().toString().trim();
            } else {
                phoneNumber = "";
            }
            Log.d(TAG, "Using account number in USSD (checkbox checked): " + phoneNumber);
        } else if (REGULAR_CUSTOMER_ID.equals(selectedCustomer.getId())) {
            // Regular Customer: get phone from account number field (default behavior)
            if (etAccountNumber != null && etAccountNumber.getText() != null) {
                phoneNumber = etAccountNumber.getText().toString().trim();
            } else {
                phoneNumber = "";
            }
            Log.d(TAG, "Regular Customer - using phone from account number field: " + phoneNumber);
        } else {
            // Regular customer: use customer's phone number
            phoneNumber = selectedCustomer.getPhoneNumber() != null ? selectedCustomer.getPhoneNumber() : "";
            Log.d(TAG, "Regular customer - using customer phone: " + phoneNumber);
        }
        
        // Build USSD code using actual codes from database
        String ussdCode;
        
        if (operatorCode != null && !operatorCode.isEmpty()) {
            // Use actual operator code from database
            if (actionCode != null && !actionCode.isEmpty()) {
                // Use actual action code from database
                ussdCode = "*" + operatorCode + "*" + actionCode + "*" + phoneNumber + "*" + (int)amount + "#";
            } else {
                // Fallback: use action name to determine code
                String actionName = selectedAction.getName().toLowerCase();
                String defaultActionCode = actionName.contains("deposit") ? "1" : "2";
                ussdCode = "*" + operatorCode + "*" + defaultActionCode + "*" + phoneNumber + "*" + (int)amount + "#";
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
            
            ussdCode = "*" + defaultOperatorCode + "*" + defaultActionCode + "*" + phoneNumber + "*" + (int)amount + "#";
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
                    // Update creditUpdatedAt when rolling back credit
                    currentUserEntity.setCreditUpdatedAt(System.currentTimeMillis());
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
                
                // Calculate commission if transaction is successful
                if (("successful".equals(transaction.getStatus()) || "completed".equals(transaction.getStatus())) 
                        && currentUserEntity != null) {
                    CommissionCalculator calculator = new CommissionCalculator(database);
                    calculator.calculateCommission(transaction, currentUserEntity);
                }
                
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
        if (activeUserId == null || selectedOperator == null) {
            Log.e(TAG, "Cannot update credit: activeUserId or selectedOperator is null");
            return;
        }

        double newCredit = transaction.getCreditAfter();
        double amount = transaction.getAmount();
        String transactionType = transaction.getTransactionType().toLowerCase();
        String operatorId = selectedOperator.getId();
        
        // Update local database
        new Thread(() -> {
            try {
                // Refresh user data to get current cash balance
                UserEntity user = database.userDao().getUserById(activeUserId);
                if (user == null) {
                    Log.e(TAG, "User not found for credit/cash update");
                    return;
                }
                
                double currentCashBalance = user.getCashBalance();
                double newCashBalance = currentCashBalance;
                
                // Update cash balance based on transaction type
                // Deposit: customer gives cash to agent, so cash balance increases
                // Transfer: customer gives cash to agent (base amount + fees), so cash balance increases by total
                // Withdrawal: agent gives cash to customer, so cash balance decreases
                if ("deposit".equalsIgnoreCase(transactionType)) {
                    newCashBalance = currentCashBalance + amount; // Increase cash (customer gives cash)
                    Log.d(TAG, "Deposit: Cash balance increased by " + amount + " (from " + currentCashBalance + " to " + newCashBalance + ")");
                } else if ("transfer".equalsIgnoreCase(transactionType)) {
                    // Transfer: cash balance increases by base amount + fees
                    // Need to get the operator to calculate fees
                    OperatorEntity operator = database.operatorDao().getById(transaction.getOperatorId());
                    if (operator != null) {
                        double transferRate = operator.getTransferRate();
                        double transferFee = amount * (transferRate / 100.0);
                        double totalAmount = amount + transferFee;
                        newCashBalance = currentCashBalance + totalAmount; // Increase cash by total (base + fees)
                        Log.d(TAG, "Transfer: Cash balance increased by " + totalAmount + " (base: " + amount + ", fee: " + transferFee + ") from " + currentCashBalance + " to " + newCashBalance);
                    } else {
                        // Fallback: if operator not found, just use base amount
                        newCashBalance = currentCashBalance + amount;
                        Log.d(TAG, "Transfer: Operator not found, using base amount. Cash balance increased by " + amount);
                    }
                } else if (transactionType.contains("withdrawal")) {
                    newCashBalance = currentCashBalance - amount; // Decrease cash (agent gives cash)
                    Log.d(TAG, "Withdrawal: Cash balance decreased by " + amount + " (from " + currentCashBalance + " to " + newCashBalance + ")");
                }
                
                // Update operator-specific balance using OperatorBalanceHelper
                balanceHelper.updateBalance(activeUserId, operatorId, newCredit);
                
                // Update tracking fields in operator balance entity
                if ("deposit".equalsIgnoreCase(transactionType)) {
                    balanceHelper.incrementCreditUsed(activeUserId, operatorId, amount);
                } else if ("transfer".equalsIgnoreCase(transactionType)) {
                    // For transfers, track the total amount debited (base + fees)
                    OperatorEntity operator = database.operatorDao().getById(transaction.getOperatorId());
                    double trackedAmount = amount;
                    if (operator != null) {
                        double transferRate = operator.getTransferRate();
                        double transferFee = amount * (transferRate / 100.0);
                        trackedAmount = amount + transferFee; // Track total debited
                    }
                    balanceHelper.incrementCreditUsed(activeUserId, operatorId, trackedAmount);
                } else {
                    balanceHelper.incrementCreditEarned(activeUserId, operatorId, amount);
                }
                
                // Update cash balance (cash balance is still global per user, not per operator)
                user.setCashBalance(newCashBalance);
                database.userDao().updateUser(user);
                currentUserEntity = user; // Update reference
                
                availableCredit = newCredit;
                runOnUiThread(() -> updateCreditDisplay());
                
                Log.d(TAG, "Operator-specific balance updated for " + selectedOperator.getName() + ": " + newCredit + ", Cash balance updated: " + newCashBalance);
            } catch (Exception e) {
                Log.e(TAG, "Error updating credit/cash locally", e);
            }
        }).start();
    }
    
    private void updateCreditInFirestoreBackground(double newCredit, TransactionEntity transaction, double newCashBalance) {
        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("virtualCredit", newCredit);
            updates.put("cashBalance", newCashBalance);
            updates.put("creditUpdatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            
            String txType = transaction.getTransactionType();
            if ("deposit".equals(txType)) {
                updates.put("totalCreditUsed", com.google.firebase.firestore.FieldValue.increment(transaction.getAmount()));
            } else if ("transfer".equals(txType)) {
                // For transfers, increment by total amount (base + fees)
                // Need to get operator to calculate fees
                OperatorEntity operator = database.operatorDao().getById(transaction.getOperatorId());
                double incrementAmount = transaction.getAmount();
                if (operator != null) {
                    double transferRate = operator.getTransferRate();
                    double transferFee = transaction.getAmount() * (transferRate / 100.0);
                    incrementAmount = transaction.getAmount() + transferFee;
                }
                updates.put("totalCreditUsed", com.google.firebase.firestore.FieldValue.increment(incrementAmount));
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
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted. Please try scanning again.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera permission is required for barcode scanning", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textRecognizer != null) {
            textRecognizer.close();
        }
        stopBarcodeScanning();
    }
    
}
