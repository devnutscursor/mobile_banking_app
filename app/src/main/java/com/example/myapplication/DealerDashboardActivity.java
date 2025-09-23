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

    private TextView tvWelcome, tvUserInfo, tvStats;
    private androidx.cardview.widget.CardView btnManageAgents, btnCashRegister, btnVirtualAccounts, btnTransactions, btnReports;
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
    private static String cachedActiveAgentsCount = "";
    private static String cachedTodayTransactionsCount = "";
    private static String cachedVirtualBalance = "";
    private static String cachedCustomersCount = "";
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
        // No explicit apply here; locale is already applied via attachBaseContext
    }

    private void initViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        tvStats = findViewById(R.id.tvStats);
        btnManageAgents = findViewById(R.id.btnManageAgents);
        btnCashRegister = findViewById(R.id.btnCashRegister);
        btnVirtualAccounts = findViewById(R.id.btnVirtualAccounts);
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

    private void setupClickListeners() {
        // Menu button click handler
        btnMenu.setOnClickListener(v -> {
            showUserDetailsDialog();
        });

        btnManageAgents.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.manage_agents) + " - " + getString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
            // TODO: Navigate to AgentManagementActivity
        });

        btnCashRegister.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.cash_register) + " - " + getString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
            // TODO: Navigate to CashRegisterActivity
        });

        btnVirtualAccounts.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.virtual_accounts) + " - " + getString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
            // TODO: Navigate to VirtualAccountsActivity
        });

        btnTransactions.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.transactions) + " - " + getString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
            // TODO: Navigate to TransactionsActivity
        });

        btnReports.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.manage_operators) + " - " + getString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
            // TODO: Navigate to ManageOperatorsActivity
        });

        btnLogout.setOnClickListener(v -> logout());
    }

    private void loadUserData() {
        // Prefer local session user for offline-first
        currentUser = sessionManager.getCurrentUser();
        if (currentUser != null) {
            tvWelcome.setText(getString(R.string.welcome) + ", " + currentUser.getName());
            tvUserInfo.setText(getString(R.string.role) + ": " + getString(R.string.dealer).toUpperCase() + "\n" + getString(R.string.email) + ": " + currentUser.getEmail());
            // Load stats into cards from Firestore
            loadDealerCardStats();
            Log.d("DealerDashboard", "Dealer dashboard loaded for: " + currentUser.getName());
        } else {
            // Fallback to AuthManager if needed
            authManager.getCurrentUser(new AuthManager.AuthCallback() {
                @Override
                public void onSuccess(com.example.myapplication.entities.User user) {
                    runOnUiThread(() -> {
                        tvWelcome.setText("Welcome, " + user.getName());
                        tvUserInfo.setText("Role: DEALER\nEmail: " + user.getEmail());
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
        TextView tvCustomersCount = findViewById(R.id.tvCustomersCount);
        
        if (!cachedActiveAgentsCount.isEmpty()) {
            tvActiveAgentsCount.setText(cachedActiveAgentsCount);
        }
        if (!cachedTodayTransactionsCount.isEmpty()) {
            tvTodayTransactionsCount.setText(cachedTodayTransactionsCount);
        }
        if (!cachedVirtualBalance.isEmpty()) {
            tvVirtualBalance.setText(cachedVirtualBalance);
        }
        if (!cachedCustomersCount.isEmpty()) {
            tvCustomersCount.setText(cachedCustomersCount);
        }
        
        // Then load fresh data from Firestore
        // Active agents under this dealer
        FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("role", "agent")
                .whereEqualTo("dealerId", currentUser.getUid())
                .get()
                .addOnSuccessListener(q -> {
                    String count = String.valueOf(q.size());
                    tvActiveAgentsCount.setText(count);
                    cachedActiveAgentsCount = count; // Cache the real data
                });

        // All customers count under this dealer's agents
        FirebaseFirestore.getInstance().collection("customers")
                .whereEqualTo("createdByDealerId", currentUser.getUid())
                .get()
                .addOnSuccessListener(q -> {
                    String count = String.valueOf(q.size());
                    tvCustomersCount.setText(count);
                    cachedCustomersCount = count; // Cache the real data
                });
        
        // For now, keep placeholder data for other fields but cache them
        String transactionCount = "47";
        String virtualBalance = "$12,450";
        
        tvTodayTransactionsCount.setText(transactionCount);
        tvVirtualBalance.setText(virtualBalance);
        
        cachedTodayTransactionsCount = transactionCount;
        cachedVirtualBalance = virtualBalance;
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
