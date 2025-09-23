package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.entities.User;
import com.example.myapplication.entities.License;
import com.example.myapplication.utils.AuthManager;
import com.example.myapplication.utils.LicenseManager;
import com.example.myapplication.utils.SessionManager;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.adapters.LanguageSpinnerAdapter;

public class LicenseActivationActivity extends AppCompatActivity {

    private EditText etLicenseKey;
    private Button btnActivate;
    private TextView tvStatus;
    private Spinner spinnerLanguage;
    private AuthManager authManager;
    private LicenseManager licenseManager;
    private SessionManager sessionManager;
    private LanguageManager languageManager;
    private static long lastLanguageChangeTime = 0;
    
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        LanguageManager lm = LanguageManager.getInstance(newBase);
        String language = lm.getCurrentLanguage();
        java.util.Locale locale = new java.util.Locale(language);
        java.util.Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
        android.content.Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license_activation);
        

        authManager = AuthManager.getInstance(this);
        licenseManager = LicenseManager.getInstance(this);
        sessionManager = new SessionManager(this);
        languageManager = LanguageManager.getInstance(this);

        etLicenseKey = findViewById(R.id.etLicenseKey);
        btnActivate = findViewById(R.id.btnActivate);
        tvStatus = findViewById(R.id.tvStatus);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);

        setupLanguageSpinner();
        btnActivate.setOnClickListener(v -> onActivate());
        // No explicit apply here; locale is already applied via attachBaseContext
    }

    private void setupLanguageSpinner() {
        // Find the language flag image view
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
                                recreate();
                            } else {
                                tvStatus.setText("Failed to change language");
                            }
                        });
                    });
        });
    }
    
    private void updateLanguageFlag() {
        ImageView ivLanguageFlag = findViewById(R.id.ivLanguageFlag);
        String currentLang = languageManager.getCurrentLanguage();
        
        if (currentLang.equals("fr")) {
            ivLanguageFlag.setImageResource(R.drawable.ic_flag_fr);
        } else {
            ivLanguageFlag.setImageResource(R.drawable.ic_flag_us);
        }
    }

    private void applyCurrentLanguage() {
        // No-op now; locale is handled via attachBaseContext
    }

    private void translateActivationUI() {
        // No-op
    }

    private void onActivate() {
        String key = etLicenseKey.getText().toString().trim();
        if (key.isEmpty()) {
            tvStatus.setText(getString(R.string.enter_license_key));
            return;
        }

        authManager.getCurrentUser(new AuthManager.AuthCallback() {
            @Override
            public void onSuccess(User user) {
                licenseManager.activateLicense(user.getUid(), key, new LicenseManager.LicenseCallback() {
                    @Override
                    public void onValid(License license) {
                        runOnUiThread(() -> {
                            // Start online session with activated license
                            sessionManager.startOnlineSession(user, license);
                            
                            Toast.makeText(LicenseActivationActivity.this, getString(R.string.license_activated), Toast.LENGTH_SHORT).show();
                            
                            // Navigate to appropriate dashboard based on user role
                            navigateToDashboard(user);
                        });
                    }

                    @Override
                    public void onInvalid(String reason) {
                        runOnUiThread(() -> tvStatus.setText(getString(R.string.activation_failed) + ": " + reason));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> tvStatus.setText("Error: " + error));
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> tvStatus.setText("Not logged in: " + error));
            }
        });
    }

    private void navigateToDashboard(User user) {
        android.content.Intent intent;
        
        if ("dealer".equalsIgnoreCase(user.getRole())) {
            intent = new android.content.Intent(this, DealerDashboardActivity.class);
        } else if ("agent".equalsIgnoreCase(user.getRole())) {
            intent = new android.content.Intent(this, AgentDashboardActivity.class);
        } else {
            // Default to dealer dashboard if role is unknown
            intent = new android.content.Intent(this, DealerDashboardActivity.class);
        }
        
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}


