package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.utils.EdgeToEdgeHelper;

import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.utils.AuthManager;
import com.example.myapplication.utils.SessionManager;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.adapters.LanguageSpinnerAdapter;
import com.example.myapplication.services.DataSyncService;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import android.app.ProgressDialog;
import android.app.AlertDialog;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class AgentDashboardActivity extends AppCompatActivity {
    
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

    private TextView tvWelcome, tvStats, tvLicenseExpiry;
    private View btnCustomers, btnCashRegister, btnBuyCredit, btnTransactions, btnReports;
    private Button btnLogout;
    private ImageView btnMenu, btnSync;
    private View headerLayout;
    private Spinner spinnerLanguage;
    private AuthManager authManager;
    private SessionManager sessionManager;
    private LanguageManager languageManager;
    private UserEntity currentUser;
    private static long lastLanguageChangeTime = 0;
    private boolean isUserChangingLanguage = false;
    
    // Store real data to prevent glitch during language change
    private static String cachedCashBalance = "";
    private static String cachedTransactionCount = "";
    private static String cachedVirtualBalance = "";
    // Removed commission stat from UI; no cache needed
    private static String cachedDealerName = "";
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge display
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        
        setContentView(R.layout.activity_agent_dashboard);
        

        authManager = AuthManager.getInstance(this);
        sessionManager = new SessionManager(this);
        languageManager = LanguageManager.getInstance(this);
        firestore = FirebaseFirestore.getInstance();

        initViews();
        EdgeToEdgeHelper.setupHeaderInsets(headerLayout, this);
        setupLanguageSpinner();
        setupClickListeners();
        loadUserData();
        
        // Check if we need to refresh stats after first-time sync
        boolean refreshStatsAfterSync = getIntent().getBooleanExtra("refreshStatsAfterSync", false);
        if (refreshStatsAfterSync) {
            // Delay refresh to allow first-time sync to complete
            new android.os.Handler().postDelayed(() -> {
                Log.d("AgentDashboard", "Refreshing stats after first-time sync");
                loadAgentCardStats();
            }, 2000); // 2 second delay
        }
        
        // No explicit apply here; locale is already applied via attachBaseContext
    }

    private void initViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvStats = findViewById(R.id.tvStats);
        tvLicenseExpiry = findViewById(R.id.tvLicenseExpiry);
        btnCustomers = findViewById(R.id.btnCustomers);
        btnCashRegister = findViewById(R.id.btnCashRegister);
        btnBuyCredit = findViewById(R.id.btnBuyCredit);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnReports = findViewById(R.id.btnReports);
        btnLogout = findViewById(R.id.btnLogout);
        btnMenu = findViewById(R.id.btnMenu);
        btnSync = findViewById(R.id.btnSync);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        headerLayout = findViewById(R.id.headerLayout);
    }

    private void setupLanguageSpinner() {
        // Get the language flag ImageView from the included layout
        ImageView ivLanguageFlag = findViewById(R.id.ivLanguageFlag);
        
        // Set initial flag based on current language
        updateLanguageFlag();
        
        // Make the language selector clickable
        View languageSelector = findViewById(R.id.ivLanguageFlag);
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
    
    private void updateLanguageFlag() {
        ImageView ivLanguageFlag = findViewById(R.id.ivLanguageFlag);
        String currentLang = languageManager.getCurrentLanguage();
        
        if ("fr".equals(currentLang)) {
            ivLanguageFlag.setImageResource(R.drawable.ic_flag_fr);
        } else {
            ivLanguageFlag.setImageResource(R.drawable.ic_flag_us);
        }
    }
    
    /**
     * Apply current language to dashboard UI elements
     */
    private void translateDashboardUI() {
        // No-op
    }

    private void applyCurrentLanguage() {
        // No-op now; locale is handled via attachBaseContext
    }

    private void setupClickListeners() {
        // Menu button click handler
        btnMenu.setOnClickListener(v -> {
            showUserDetailsDialog();
        });

        btnCustomers.setOnClickListener(v -> {
            // Navigate to Customer Management
            android.content.Intent intent = new android.content.Intent(this, CustomerManagementActivity.class);
            startActivity(intent);
        });

        btnCashRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, CashRegisterActivity.class);
            startActivity(intent);
        });

        btnBuyCredit.setOnClickListener(v -> {
            Intent intent = new Intent(this, BuyCreditActivity.class);
            startActivity(intent);
        });

        btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProcessTransactionActivity.class);
            startActivity(intent);
        });

        btnReports.setOnClickListener(v -> {
            // Navigate to Operator Management
            android.content.Intent intent = new android.content.Intent(this, OperatorManagementActivity.class);
            startActivity(intent);
        });
        
        // Commission Configuration button
        View btnCommissionConfig = findViewById(R.id.btnCommissionConfig);
        if (btnCommissionConfig != null) {
            btnCommissionConfig.setOnClickListener(v -> {
                Intent intent = new Intent(this, CommissionConfigurationActivity.class);
                startActivity(intent);
            });
        }
        
        // Commission Reports button
        View btnCommissionReports = findViewById(R.id.btnCommissionReports);
        if (btnCommissionReports != null) {
            btnCommissionReports.setOnClickListener(v -> {
                Intent intent = new Intent(this, CommissionReportActivity.class);
                startActivity(intent);
            });
        }

        btnLogout.setOnClickListener(v -> logout());
        
        btnSync.setOnClickListener(v -> performDataSync());
    }

    private void loadUserData() {
        currentUser = sessionManager.getCurrentUser();
        if (currentUser != null) {
            // Debug: Log current language
            String currentLang = languageManager.getCurrentLanguage();
            Log.d("AgentDashboard", "Current language: " + currentLang);
            Log.d("AgentDashboard", "Welcome string: " + getString(R.string.welcome));
            
            tvWelcome.setText(getString(R.string.welcome) + ", " + currentUser.getName());
            
            // Use cached dealer name if available, otherwise show UUID temporarily
            String dealerInfo;
            if (!cachedDealerName.isEmpty()) {
                dealerInfo = "\n" + getString(R.string.dealer) + ": " + cachedDealerName;
            } else {
                dealerInfo = currentUser.getDealerId() != null ?
                        "\n" + getString(R.string.dealer) + ": " + currentUser.getDealerId() :
                        "\nNo dealer assigned";
            }
            // User info is now static "Agents Account" in XML
            // Load realtime stats for cards
            loadAgentCardStats();
            // Load dealer name from UUID
            loadDealerName();
            // Load license expiry date
            loadLicenseExpiry();
            Log.d("AgentDashboard", "Agent dashboard loaded for: " + currentUser.getName());
        } else {
            authManager.getCurrentUser(new AuthManager.AuthCallback() {
                @Override
                public void onSuccess(com.example.myapplication.entities.User user) {
                    runOnUiThread(() -> {
                        tvWelcome.setText("Welcome, " + user.getName());
                        String dealerInfo = user.getDealerId() != null ?
                                "\nDealer: " + user.getDealerId() :
                                "\nNo dealer assigned";
                        // User info is now static "Agents Account" in XML
                        loadAgentCardStats();
                        Log.d("AgentDashboard", "Agent dashboard loaded for: " + user.getName());
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(AgentDashboardActivity.this, "Error loading user data: " + error, Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            });
        }
    }

    private void loadAgentCardStats() {
        if (currentUser == null) return;
        
        // Restore cached data immediately to prevent glitch
        TextView tvCashBalance = findViewById(R.id.tvCashBalance);
        TextView tvTodayTransactionsCount = findViewById(R.id.tvTodayTransactionsCount);
        TextView tvVirtualBalance = findViewById(R.id.tvVirtualBalance);
        
        if (!cachedCashBalance.isEmpty() && tvCashBalance != null) {
            tvCashBalance.setText(cachedCashBalance);
        }
        if (!cachedTransactionCount.isEmpty()) {
            tvTodayTransactionsCount.setText(cachedTransactionCount);
        }
        if (!cachedVirtualBalance.isEmpty()) {
            tvVirtualBalance.setText(cachedVirtualBalance);
        }
        // Commission stat removed from UI
        
        // Load cash balance from local database first (offline-first approach)
        new Thread(() -> {
            try {
                com.example.myapplication.database.AppDatabase database = 
                    com.example.myapplication.database.AppDatabase.getDatabase(this);
                com.example.myapplication.database.entities.UserEntity localUser = database.userDao().getUserById(currentUser.getUid());
                
                if (localUser != null) {
                    double localCashBalance = localUser.getCashBalance();
                    String display = com.example.myapplication.utils.NumberFormatter.formatWithThousandsSeparator(localCashBalance);
                    runOnUiThread(() -> {
                        if (tvCashBalance != null) {
                            tvCashBalance.setText(display);
                        }
                        cachedCashBalance = display;
                    });
                    Log.d("AgentDashboard", "Loaded cash balance from local DB: " + localCashBalance);
                } else {
                    runOnUiThread(() -> {
                        if (tvCashBalance != null) {
                            tvCashBalance.setText("0");
                        }
                        cachedCashBalance = "0";
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("AgentDashboard", "Error loading cash balance", e);
                runOnUiThread(() -> {
                    if (tvCashBalance != null) {
                        tvCashBalance.setText("0");
                    }
                    cachedCashBalance = "0";
                });
            }
        }).start();
        
        // Last 7 days transactions - compute since 7 days ago using local transactions table
        new Thread(() -> {
            try {
                long startOf7DaysAgo = java.time.LocalDate.now()
                        .minusDays(7)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
                com.example.myapplication.database.AppDatabase dbLocal = com.example.myapplication.database.AppDatabase.getDatabase(this);
                int last7DaysCount = dbLocal.transactionDao().getLast7DaysTransactionCount(currentUser.getUid(), startOf7DaysAgo);
                
                // Debug logging
                android.util.Log.d("AgentDashboard", "Last 7 days count calculation:");
                android.util.Log.d("AgentDashboard", "- User ID: " + currentUser.getUid());
                android.util.Log.d("AgentDashboard", "- Start of 7 days ago: " + startOf7DaysAgo + " (" + new java.util.Date(startOf7DaysAgo).toString() + ")");
                android.util.Log.d("AgentDashboard", "- Count result: " + last7DaysCount);
                
                runOnUiThread(() -> {
                    String count = String.valueOf(last7DaysCount);
                    tvTodayTransactionsCount.setText(count);
                    cachedTransactionCount = count;
                });
            } catch (Exception e) {
                android.util.Log.e("AgentDashboard", "Error loading last 7 days transactions", e);
            }
        }).start();

        // Load total credits: global virtualCredit pool + sum of all operator balances
        new Thread(() -> {
            try {
                com.example.myapplication.database.AppDatabase database = 
                    com.example.myapplication.database.AppDatabase.getDatabase(this);
                com.example.myapplication.utils.OperatorBalanceHelper balanceHelper = 
                    new com.example.myapplication.utils.OperatorBalanceHelper(database);
                
                // Use DAO method to get total operator balance (more efficient and accurate)
                double totalOperatorBalance = database.operatorBalanceDao().getTotalBalanceForUser(currentUser.getUid());
                
                // Check if we need to recalculate balances from existing transactions
                java.util.List<com.example.myapplication.database.entities.TransactionEntity> transactions = 
                    database.transactionDao().getTransactionsByUser(currentUser.getUid());
                
                // Always recalculate if there are transactions - this ensures balances are always correct
                // The recalculation will preserve purchases/adjustments while fixing transaction-based calculations
                if (transactions != null && !transactions.isEmpty()) {
                    // Count successful/completed transactions
                    int successfulTransactions = 0;
                    for (com.example.myapplication.database.entities.TransactionEntity tx : transactions) {
                        String status = tx.getStatus();
                        if (status != null && (status.equalsIgnoreCase("successful") || status.equalsIgnoreCase("completed"))) {
                            successfulTransactions++;
                        }
                    }
                    
                    // If there are successful transactions, always recalculate to ensure accuracy
                    if (successfulTransactions > 0) {
                        android.util.Log.d("AgentDashboard", "Found " + successfulTransactions + " successful transactions. Recalculating balances to ensure accuracy.");
                        balanceHelper.recalculateBalancesFromTransactions(currentUser.getUid());
                        // Re-fetch total operator balance after recalculation
                        totalOperatorBalance = database.operatorBalanceDao().getTotalBalanceForUser(currentUser.getUid());
                        android.util.Log.d("AgentDashboard", "After recalculation - Total operator balance: " + totalOperatorBalance);
                    }

                }

                // Load user's global virtual credit pool (ALWAYS fetch fresh from database)
                com.example.myapplication.database.entities.UserEntity localUser =
                        database.userDao().getUserById(currentUser.getUid());
                double virtualPool = (localUser != null) ? localUser.getVirtualCredit() : 0.0;
                
                android.util.Log.d("AgentDashboard", "Dashboard credit calculation:");
                android.util.Log.d("AgentDashboard", "- Virtual Pool (virtualCredit): " + virtualPool);
                android.util.Log.d("AgentDashboard", "- Total Operator Balance: " + totalOperatorBalance);

                double totalCredits = virtualPool + totalOperatorBalance;
                String display = com.example.myapplication.utils.NumberFormatter.formatWithThousandsSeparator(totalCredits);
                runOnUiThread(() -> {
                    tvVirtualBalance.setText(display);
                    cachedVirtualBalance = display;
                });
                android.util.Log.d("AgentDashboard", "Loaded total credits (virtual pool + operators): " + totalCredits + " -> Display: " + display);
            } catch (Exception e) {
                android.util.Log.e("AgentDashboard", "Error loading total operator balance from local DB", e);
                runOnUiThread(() -> {
                    tvVirtualBalance.setText("0");
                    cachedVirtualBalance = "0";
                });
            }
        }).start();
    }

    private void loadDealerName() {
        if (currentUser == null || currentUser.getDealerId() == null) return;
        
        // If we already have cached dealer name, use it immediately
        if (!cachedDealerName.isEmpty()) {
            // User info is now static "Agents Account" in XML
            return;
        }
        
        // Fetch dealer name from UUID
        firestore.collection("users").document(currentUser.getDealerId())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String dealerName = documentSnapshot.getString("name");
                        if (dealerName != null) {
                            // Cache the dealer name
                            cachedDealerName = dealerName;
                            
                            // User info is now static "Agents Account" in XML
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AgentDashboard", "Error fetching dealer name: " + e.getMessage());
                });
    }

    private void loadLicenseExpiry() {
        if (currentUser == null || tvLicenseExpiry == null) {
            return;
        }
        
        // Try to get license from database first (more reliable)
        com.example.myapplication.database.entities.LicenseEntity licenseEntity = 
            sessionManager.getLicenseByUser(currentUser.getUid());
        
        if (licenseEntity != null && licenseEntity.getExpiryDate() > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault());
            String expiryDateStr = sdf.format(new java.util.Date(licenseEntity.getExpiryDate()));
            boolean isExpired = licenseEntity.isExpired();
            
            String displayText = "License Expires: " + expiryDateStr;
            if (isExpired) {
                displayText += " (Expired)";
                tvLicenseExpiry.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_red_light));
            } else {
                tvLicenseExpiry.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
            }
            tvLicenseExpiry.setText(displayText);
        } else {
            // Fallback: try LicenseManager
            com.example.myapplication.utils.LicenseManager licenseManager = 
                com.example.myapplication.utils.LicenseManager.getInstance(this);
            com.example.myapplication.entities.License license = licenseManager.getCurrentLicense();
            
            if (license != null && license.getExpiryDate() != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault());
                String expiryDateStr = sdf.format(license.getExpiryDate());
                boolean isExpired = license.isExpired();
                
                String displayText = "License Expires: " + expiryDateStr;
                if (isExpired) {
                    displayText += " (Expired)";
                    tvLicenseExpiry.setTextColor(getColor(android.R.color.holo_red_light));
                } else {
                    tvLicenseExpiry.setTextColor(getColor(android.R.color.white));
                }
                tvLicenseExpiry.setText(displayText);
            } else {
                tvLicenseExpiry.setText("");
            }
        }
    }

    private void showUserDetailsDialog() {
        if (currentUser == null) {
            Toast.makeText(this, "User information not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Try to get license from database first (more reliable)
        com.example.myapplication.database.entities.LicenseEntity licenseEntity = 
            sessionManager.getLicenseByUser(currentUser.getUid());
        com.example.myapplication.entities.License license = null;
        
        if (licenseEntity != null) {
            // Convert entity to License object
            license = sessionManager.convertEntityToLicense(licenseEntity);
        } else {
            // Fallback: try LicenseManager
            com.example.myapplication.utils.LicenseManager licenseManager = 
                com.example.myapplication.utils.LicenseManager.getInstance(this);
            license = licenseManager.getCurrentLicense();
        }
        
        String message = "Email: " + currentUser.getEmail() + "\n" +
                        "Role: " + currentUser.getRole() + "\n" +
                        "Status: " + (currentUser.isActive() ? "Active" : "Inactive") + "\n";
        
        if (license != null && license.getExpiryDate() != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault());
            String expiryDateStr = sdf.format(license.getExpiryDate());
            boolean isExpired = license.isExpired();
            message += "License Expiry: " + expiryDateStr + (isExpired ? " (Expired)" : "") + "\n";
        } else {
            message += "License Expiry: Not Available\n";
        }
        
        message += "Created: " + (currentUser.getCreatedAt() > 0 ? new java.util.Date(currentUser.getCreatedAt()).toString() : "Unknown");

        new android.app.AlertDialog.Builder(this)
                .setTitle("User Details")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("AgentDashboardActivity", "onResume called, checking disabled status");
        
        // Check if user is disabled and logout if necessary
        checkUserDisabledStatus();
        
        // Clear cached data to force fresh load
        cachedCashBalance = "";
        cachedVirtualBalance = ""; // Clear virtual balance cache to refresh total credit
        // Refresh stats when returning
        loadAgentCardStats();
        // Refresh license expiry date
        loadLicenseExpiry();
        
        // Check for sync prompt on dashboard resume
        com.example.myapplication.utils.SyncPromptManager syncPromptManager = 
            new com.example.myapplication.utils.SyncPromptManager(this);
        syncPromptManager.maybePromptOnDashboardResume();
    }

    private void performDataSync() {
        if (currentUser == null) {
            Toast.makeText(this, "No active user", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check internet connectivity
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        
        if (!isConnected) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.internet_required))
                    .setMessage(getString(R.string.sync_requires_internet))
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        
        // Clear declined flag since user manually triggered sync
        com.example.myapplication.utils.SyncPromptManager syncPromptManager = 
            new com.example.myapplication.utils.SyncPromptManager(this);
        syncPromptManager.onManualSync();
        
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.syncing_data));
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        DataSyncService syncService = new DataSyncService(this);
        syncService.syncAllData(currentUser.getUid(), new DataSyncService.SyncCallback() {
            @Override
            public void onSyncStarted() {
                runOnUiThread(() -> {
                    progressDialog.setMessage("Starting sync...");
                });
            }
            
            @Override
            public void onSyncProgress(String message, int current, int total) {
                runOnUiThread(() -> {
                    progressDialog.setMessage(message + " (" + current + "/" + total + ")");
                });
            }
            
            @Override
            public void onSyncComplete(boolean success, String message) {
                Log.d("AgentDashboardActivity", "onSyncComplete called - success: " + success + ", message: " + message);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (success) {
                        Log.d("AgentDashboardActivity", "Sync successful, calling checkUserDisabledStatus");
                        Toast.makeText(AgentDashboardActivity.this, 
                                "Sync completed successfully", Toast.LENGTH_SHORT).show();
                        // Check if user is disabled after sync
                        checkUserDisabledStatus();
                        // Refresh UI with synced data
                        loadAgentCardStats();
                    } else {
                        Log.d("AgentDashboardActivity", "Sync failed: " + message);
                        Toast.makeText(AgentDashboardActivity.this, 
                                message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
    
    /**
     * Check if the current user is disabled and logout if necessary
     */
    private void checkUserDisabledStatus() {
        UserEntity currentUser = sessionManager.getCurrentUser();
        Log.d("AgentDashboardActivity", "checkUserDisabledStatus called");
        Log.d("AgentDashboardActivity", "currentUser: " + (currentUser != null ? "exists" : "null"));
        
        if (currentUser != null) {
            Log.d("AgentDashboardActivity", "User disabled status: " + currentUser.isDisabled());
            Log.d("AgentDashboardActivity", "User email: " + currentUser.getEmail());
            Log.d("AgentDashboardActivity", "User role: " + currentUser.getRole());
        }
        
        if (currentUser != null && currentUser.isDisabled()) {
            Log.d("AgentDashboardActivity", "User is disabled, logging out");
            Toast.makeText(this, "Your account has been disabled. Please contact support.", Toast.LENGTH_LONG).show();
            sessionManager.fullLogout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            Log.d("AgentDashboardActivity", "User is not disabled or currentUser is null");
        }
    }
    
    private void logout() {
        // Lock session - keep local data for offline access
        sessionManager.lockSession();
        // Clear any globally cached license to avoid cross-user reuse
        com.example.myapplication.utils.LicenseManager.getInstance(this).clearLicense();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

