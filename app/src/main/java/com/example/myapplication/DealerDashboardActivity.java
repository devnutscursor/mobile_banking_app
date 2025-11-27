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

public class DealerDashboardActivity extends AppCompatActivity {
    
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

    private TextView tvWelcome, tvUserInfo, tvStats, tvLicenseExpiry;
    private View btnCustomers, btnCashRegister, btnTransactions, btnReports;
    private Button btnLogout;
    private ImageView btnMenu, btnSync;
    private Spinner spinnerLanguage;
    private AuthManager authManager;
    private SessionManager sessionManager;
    private LanguageManager languageManager;
    private UserEntity currentUser;
    private static long lastLanguageChangeTime = 0;
    private boolean isUserChangingLanguage = false;
    
    // Store real data to prevent glitch during language change
    private static String cachedActiveAgentsCount = "";
    private static String cachedTodayTransactionsCount = "";
    private static String cachedVirtualBalance = "";
    private static String cachedCashBalance = "";
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dealer_dashboard);
        

        authManager = AuthManager.getInstance(this);
        sessionManager = new SessionManager(this);
        languageManager = LanguageManager.getInstance(this);
        firestore = FirebaseFirestore.getInstance();

        initViews();
        setupLanguageSpinner();
        setupClickListeners();
        loadUserData();
        
        // Check if we need to refresh stats after first-time sync
        boolean refreshStatsAfterSync = getIntent().getBooleanExtra("refreshStatsAfterSync", false);
        if (refreshStatsAfterSync) {
            // Delay refresh to allow first-time sync to complete
            new android.os.Handler().postDelayed(() -> {
                Log.d("DealerDashboard", "Refreshing stats after first-time sync");
                loadDealerCardStats();
            }, 2000); // 2 second delay
        }
        
        // No explicit apply here; locale is already applied via attachBaseContext
    }

    private void initViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        tvStats = findViewById(R.id.tvStats);
        tvLicenseExpiry = findViewById(R.id.tvLicenseExpiry);
        btnCustomers = findViewById(R.id.btnCustomers);
        btnCashRegister = findViewById(R.id.btnCashRegister);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnReports = findViewById(R.id.btnReports);
        btnLogout = findViewById(R.id.btnLogout);
        btnMenu = findViewById(R.id.btnMenu);
        btnSync = findViewById(R.id.btnSync);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
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

        btnTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProcessTransactionActivity.class);
            startActivity(intent);
        });
        
        // Buy Credit button
        findViewById(R.id.btnBuyCredit).setOnClickListener(v -> {
            Intent intent = new Intent(this, BuyCreditActivity.class);
            startActivity(intent);
        });

        btnReports.setOnClickListener(v -> {
            // Navigate to Operator Management
            android.content.Intent intent = new android.content.Intent(this, OperatorManagementActivity.class);
            startActivity(intent);
        });
        
        // Commission Configuration button
        View btnCommissionConfigDealer = findViewById(R.id.btnCommissionConfig);
        if (btnCommissionConfigDealer != null) {
            btnCommissionConfigDealer.setOnClickListener(v -> {
                Intent intent = new Intent(this, CommissionConfigurationActivity.class);
                startActivity(intent);
            });
        }
        
        // Commission Reports button
        View btnCommissionReportsDealer = findViewById(R.id.btnCommissionReports);
        if (btnCommissionReportsDealer != null) {
            btnCommissionReportsDealer.setOnClickListener(v -> {
                Intent intent = new Intent(this, CommissionReportActivity.class);
                startActivity(intent);
            });
        }

        btnLogout.setOnClickListener(v -> logout());
        
        btnSync.setOnClickListener(v -> performDataSync());
    }

    private void loadUserData() {
        // Prefer local session user for offline-first
        currentUser = sessionManager.getCurrentUser();
        if (currentUser != null) {
            tvWelcome.setText(getString(R.string.welcome) + ", " + currentUser.getName());
            tvUserInfo.setText("Dealer Account");
            // Load stats into cards from Firestore
            loadDealerCardStats();
            // Load license expiry date
            loadLicenseExpiry();
            Log.d("DealerDashboard", "Dealer dashboard loaded for: " + currentUser.getName());
        } else {
            // Fallback to AuthManager if needed
            authManager.getCurrentUser(new AuthManager.AuthCallback() {
                @Override
                public void onSuccess(com.example.myapplication.entities.User user) {
                    runOnUiThread(() -> {
                        tvWelcome.setText("Welcome, " + user.getName());
                        tvUserInfo.setText("Dealer Account");
                        loadDealerCardStats();
                        Log.d("DealerDashboard", "Dealer dashboard loaded for: " + user.getName());
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(DealerDashboardActivity.this, "Error loading user data: " + error, Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            });
        }
    }

    private void loadDealerCardStats() {
        if (currentUser == null) return;
        
        // Restore cached data immediately to prevent glitch
        TextView tvActiveAgentsCount = findViewById(R.id.tvActiveAgentsCount);
        TextView tvTodayTransactionsCount = findViewById(R.id.tvTodayTransactionsCount);
        TextView tvVirtualBalance = findViewById(R.id.tvVirtualBalance);
        TextView tvCashBalance = findViewById(R.id.tvCashBalance);
        
        if (!cachedActiveAgentsCount.isEmpty()) {
            tvActiveAgentsCount.setText(cachedActiveAgentsCount);
        }
        if (!cachedTodayTransactionsCount.isEmpty()) {
            tvTodayTransactionsCount.setText(cachedTodayTransactionsCount);
        }
        if (!cachedVirtualBalance.isEmpty() && tvVirtualBalance != null) {
            tvVirtualBalance.setText(cachedVirtualBalance);
        }
        if (!cachedCashBalance.isEmpty() && tvCashBalance != null) {
            tvCashBalance.setText(cachedCashBalance);
        }
        
        // Load agents count from local database first (offline-first approach)
        new Thread(() -> {
            try {
                com.example.myapplication.database.AppDatabase database = 
                    com.example.myapplication.database.AppDatabase.getDatabase(this);
                // Count agents under this dealer from local database
                int localAgentsCount = database.userDao().getAgentCountByDealer(currentUser.getUid());
                
                runOnUiThread(() -> {
                    String count = String.valueOf(localAgentsCount);
                    tvActiveAgentsCount.setText(count);
                    cachedActiveAgentsCount = count; // Cache the real data
                });
                
            } catch (Exception e) {
                android.util.Log.e("DealerDashboardActivity", "Error loading agents count from local DB", e);
                runOnUiThread(() -> {
                    tvActiveAgentsCount.setText("0");
                    cachedActiveAgentsCount = "0";
                });
            }
        }).start();

        // Load cash balance from local database first (offline-first approach)
        new Thread(() -> {
            try {
                com.example.myapplication.database.AppDatabase database = 
                    com.example.myapplication.database.AppDatabase.getDatabase(this);
                com.example.myapplication.database.entities.UserEntity localUser = database.userDao().getUserById(currentUser.getUid());
                
                if (localUser != null) {
                    double localCashBalance = localUser.getCashBalance();
                    String display = String.format(java.util.Locale.getDefault(), "%.0f", localCashBalance);
                    runOnUiThread(() -> {
                        if (tvCashBalance != null) {
                            tvCashBalance.setText(display);
                        }
                        cachedCashBalance = display;
                    });
                    android.util.Log.d("DealerDashboard", "Loaded cash balance from local DB: " + localCashBalance);
                } else {
                    runOnUiThread(() -> {
                        if (tvCashBalance != null) {
                            tvCashBalance.setText("0");
                        }
                        cachedCashBalance = "0";
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("DealerDashboard", "Error loading cash balance", e);
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
                android.util.Log.d("DealerDashboard", "Last 7 days count calculation:");
                android.util.Log.d("DealerDashboard", "- User ID: " + currentUser.getUid());
                android.util.Log.d("DealerDashboard", "- Start of 7 days ago: " + startOf7DaysAgo + " (" + new java.util.Date(startOf7DaysAgo).toString() + ")");
                android.util.Log.d("DealerDashboard", "- Count result: " + last7DaysCount);
                
                runOnUiThread(() -> {
                    String count = String.valueOf(last7DaysCount);
                    tvTodayTransactionsCount.setText(count);
                    cachedTodayTransactionsCount = count;
                });
            } catch (Exception e) {
                android.util.Log.e("DealerDashboard", "Error loading last 7 days transactions", e);
            }
        }).start();

        // Load virtual credit from local database first (offline-first approach)
        new Thread(() -> {
            try {
                com.example.myapplication.database.AppDatabase database = 
                    com.example.myapplication.database.AppDatabase.getDatabase(this);
                com.example.myapplication.database.entities.UserEntity localUser = database.userDao().getUserById(currentUser.getUid());
                
                if (localUser != null) {
                    double localCredit = localUser.getVirtualCredit();
                    String display = String.format(java.util.Locale.getDefault(), "%.0f", localCredit);
                    runOnUiThread(() -> {
                        if (tvVirtualBalance != null) {
                            tvVirtualBalance.setText(display);
                        }
                        cachedVirtualBalance = display;
                    });
                    android.util.Log.d("DealerDashboard", "Loaded credit from local DB: " + localCredit);
                } else {
                    runOnUiThread(() -> {
                        if (tvVirtualBalance != null) {
                            tvVirtualBalance.setText("0");
                        }
                        cachedVirtualBalance = "0";
                    });
                }
                
            } catch (Exception e) {
                android.util.Log.e("DealerDashboard", "Error loading credit from local DB", e);
                runOnUiThread(() -> {
                    if (tvVirtualBalance != null) {
                        tvVirtualBalance.setText("0");
                    }
                    cachedVirtualBalance = "0";
                });
            }
        }).start();
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
        
        // Check if user is disabled and logout if necessary
        checkUserDisabledStatus();
        
        // Clear cached data to force fresh load
        cachedCashBalance = "";
        // Refresh stats when returning
        loadDealerCardStats();
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
                    .setTitle("Internet Required")
                    .setMessage("Sync requires internet connection. Please check your network and try again.\n\nNote: The app works offline, but sync needs internet to update cloud data.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        
        // Clear declined flag since user manually triggered sync
        com.example.myapplication.utils.SyncPromptManager syncPromptManager = 
            new com.example.myapplication.utils.SyncPromptManager(this);
        syncPromptManager.onManualSync();
        
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Syncing data...");
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
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (success) {
                        Toast.makeText(DealerDashboardActivity.this, 
                                "Sync completed successfully", Toast.LENGTH_SHORT).show();
                        // Check if user is disabled after sync
                        checkUserDisabledStatus();
                        // Refresh UI with synced data
                        loadDealerCardStats();
                    } else {
                        Toast.makeText(DealerDashboardActivity.this, 
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
        Log.d("DealerDashboardActivity", "checkUserDisabledStatus called");
        Log.d("DealerDashboardActivity", "currentUser: " + (currentUser != null ? "exists" : "null"));
        
        if (currentUser != null) {
            Log.d("DealerDashboardActivity", "User disabled status: " + currentUser.isDisabled());
            Log.d("DealerDashboardActivity", "User email: " + currentUser.getEmail());
            Log.d("DealerDashboardActivity", "User role: " + currentUser.getRole());
        }
        
        if (currentUser != null && currentUser.isDisabled()) {
            Log.d("DealerDashboardActivity", "User is disabled, logging out");
            Toast.makeText(this, "Your account has been disabled. Please contact support.", Toast.LENGTH_LONG).show();
            sessionManager.fullLogout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            Log.d("DealerDashboardActivity", "User is not disabled or currentUser is null");
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
