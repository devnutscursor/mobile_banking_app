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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

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

    private TextView tvWelcome, tvUserInfo, tvStats;
    private androidx.cardview.widget.CardView btnCustomers, btnCashRegister, btnBuyCredit, btnTransactions, btnReports;
    private Button btnLogout;
    private ImageView btnMenu;
    private Spinner spinnerLanguage;
    private AuthManager authManager;
    private SessionManager sessionManager;
    private LanguageManager languageManager;
    private UserEntity currentUser;
    private static long lastLanguageChangeTime = 0;
    private boolean isUserChangingLanguage = false;
    
    // Store real data to prevent glitch during language change
    private static String cachedCustomerCount = "";
    private static String cachedTransactionCount = "";
    private static String cachedVirtualBalance = "";
    private static String cachedCommission = "";
    private static String cachedDealerName = "";
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agent_dashboard);
        

        authManager = AuthManager.getInstance(this);
        sessionManager = new SessionManager(this);
        languageManager = LanguageManager.getInstance(this);
        firestore = FirebaseFirestore.getInstance();

        initViews();
        setupLanguageSpinner();
        setupClickListeners();
        loadUserData();
        // No explicit apply here; locale is already applied via attachBaseContext
    }

    private void initViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        tvStats = findViewById(R.id.tvStats);
        btnCustomers = findViewById(R.id.btnCustomers);
        btnCashRegister = findViewById(R.id.btnCashRegister);
        btnBuyCredit = findViewById(R.id.btnBuyCredit);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnReports = findViewById(R.id.btnReports);
        btnLogout = findViewById(R.id.btnLogout);
        btnMenu = findViewById(R.id.btnMenu);
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
            Toast.makeText(this, "Customer Management - " + getString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
            // TODO: Navigate to CustomerManagementActivity
        });

        btnCashRegister.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.cash_register) + " - " + getString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
            // TODO: Navigate to CashRegisterActivity
        });

        btnBuyCredit.setOnClickListener(v -> {
            Toast.makeText(this, "Buy Virtual Credit - " + getString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
            // TODO: Navigate to BuyCreditActivity
        });

        btnTransactions.setOnClickListener(v -> {
            Toast.makeText(this, "Process " + getString(R.string.transactions) + " - " + getString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
            // TODO: Navigate to TransactionsActivity
        });

        btnReports.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.manage_operators) + " - " + getString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
            // TODO: Navigate to ManageOperatorsActivity
        });

        btnLogout.setOnClickListener(v -> logout());
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
            tvUserInfo.setText(getString(R.string.role) + ": " + getString(R.string.agent).toUpperCase() + "\n" + getString(R.string.email) + ": " + currentUser.getEmail() + dealerInfo);
            // Load realtime stats for cards
            loadAgentCardStats();
            // Load dealer name from UUID
            loadDealerName();
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
                        tvUserInfo.setText("Role: AGENT\nEmail: " + user.getEmail() + dealerInfo);
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
        TextView tvCustomersCount = findViewById(R.id.tvCustomersCount);
        TextView tvTodayTransactionsCount = findViewById(R.id.tvTodayTransactionsCount);
        TextView tvVirtualBalance = findViewById(R.id.tvVirtualBalance);
        TextView tvCommissionEarned = findViewById(R.id.tvCommissionEarned);
        
        if (!cachedCustomerCount.isEmpty()) {
            tvCustomersCount.setText(cachedCustomerCount);
        }
        if (!cachedTransactionCount.isEmpty()) {
            tvTodayTransactionsCount.setText(cachedTransactionCount);
        }
        if (!cachedVirtualBalance.isEmpty()) {
            tvVirtualBalance.setText(cachedVirtualBalance);
        }
        if (!cachedCommission.isEmpty()) {
            tvCommissionEarned.setText(cachedCommission);
        }
        
        // Then load fresh data from Firestore
        Query q = firestore.collection("customers").whereEqualTo("createdBy", currentUser.getUid());
        q.get().addOnSuccessListener(snap -> {
            String count = String.valueOf(snap.size());
            tvCustomersCount.setText(count);
            cachedCustomerCount = count; // Cache the real data
        });
        
        // For now, keep placeholder data for other fields but cache them
        String transactionCount = "15";
        String virtualBalance = "$875";
        String commission = "$125";
        
        tvTodayTransactionsCount.setText(transactionCount);
        tvVirtualBalance.setText(virtualBalance);
        tvCommissionEarned.setText(commission);
        
        cachedTransactionCount = transactionCount;
        cachedVirtualBalance = virtualBalance;
        cachedCommission = commission;
    }

    private void loadDealerName() {
        if (currentUser == null || currentUser.getDealerId() == null) return;
        
        // If we already have cached dealer name, use it immediately
        if (!cachedDealerName.isEmpty()) {
            String dealerInfo = "\n" + getString(R.string.dealer) + ": " + cachedDealerName;
            tvUserInfo.setText(getString(R.string.role) + ": " + getString(R.string.agent).toUpperCase() + 
                             "\n" + getString(R.string.email) + ": " + currentUser.getEmail() + dealerInfo);
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
                            
                            // Update the user info text with dealer name instead of UUID
                            String dealerInfo = "\n" + getString(R.string.dealer) + ": " + dealerName;
                            tvUserInfo.setText(getString(R.string.role) + ": " + getString(R.string.agent).toUpperCase() + 
                                             "\n" + getString(R.string.email) + ": " + currentUser.getEmail() + dealerInfo);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AgentDashboard", "Error fetching dealer name: " + e.getMessage());
                });
    }

    private void showUserDetailsDialog() {
        if (currentUser == null) {
            Toast.makeText(this, "User information not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String message = "Email: " + currentUser.getEmail() + "\n" +
                        "Role: " + currentUser.getRole() + "\n" +
                        "Status: " + (currentUser.isActive() ? "Active" : "Inactive") + "\n" +
                        "Created: " + (currentUser.getCreatedAt() > 0 ? new java.util.Date(currentUser.getCreatedAt()).toString() : "Unknown");

        new android.app.AlertDialog.Builder(this)
                .setTitle("User Details")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
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
