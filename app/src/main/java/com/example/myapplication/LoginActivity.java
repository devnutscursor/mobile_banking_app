package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.database.entities.LicenseEntity;
import com.example.myapplication.entities.User;
import com.example.myapplication.entities.License;
import com.example.myapplication.utils.AuthManager;
import com.example.myapplication.utils.LicenseManager;
import com.example.myapplication.utils.SessionManager;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.adapters.LanguageSpinnerAdapter;
import com.example.myapplication.services.FirstLoginSyncService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {
    
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

    private EditText etEmail, etPassword, etPhone;
    private Button btnLogin;
    private TextView tvStatus;
    private TextView tvWelcomeTitle; // "Welcome Back"
    private TextView tvSubtitle;     // "Sign in to your account"
    private TextView tvLoginTypeEmail, tvLoginTypePhone;
    private com.google.android.material.textfield.TextInputLayout tilEmail, tilPhone, tilPassword;
    private Spinner spinnerLanguage;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private AuthManager authManager;
    private LicenseManager licenseManager;
    private SessionManager sessionManager;
    private LanguageManager languageManager;
    private static long lastLanguageChangeTime = 0;
    private boolean isUserChangingLanguage = false;
    private boolean isPhoneLogin = false; // Track login mode

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        authManager = AuthManager.getInstance(this);
        licenseManager = LicenseManager.getInstance(this);
        sessionManager = new SessionManager(this);
        languageManager = LanguageManager.getInstance(this);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etPhone = findViewById(R.id.etPhone);
        btnLogin = findViewById(R.id.btnLogin);
        tvStatus = findViewById(R.id.tvStatus);
        tvLoginTypeEmail = findViewById(R.id.tvLoginTypeEmail);
        tvLoginTypePhone = findViewById(R.id.tvLoginTypePhone);
        tilEmail = findViewById(R.id.tilEmail);
        tilPhone = findViewById(R.id.tilPhone);
        tilPassword = findViewById(R.id.tilPassword);
        // Optional: these may be null if ids not present in layout
        tvWelcomeTitle = findViewById(getResources().getIdentifier("tvWelcomeTitle", "id", getPackageName()));
        tvSubtitle = findViewById(getResources().getIdentifier("tvSubtitle", "id", getPackageName()));
        spinnerLanguage = findViewById(R.id.spinnerLanguage);

        setupLanguageSpinner();
        setupLoginTypeToggle();
        btnLogin.setOnClickListener(v -> loginUser());
        
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
                                tvStatus.setText("");
                                // Recreate activity to apply new language
                                recreate();
                            } else {
                                showError("Failed to change language");
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
    
    private void setupLoginTypeToggle() {
        // Set initial state (email login)
        updateLoginTypeUI(false);
        
        tvLoginTypeEmail.setOnClickListener(v -> {
            if (isPhoneLogin) {
                isPhoneLogin = false;
                updateLoginTypeUI(false);
            }
        });
        
        tvLoginTypePhone.setOnClickListener(v -> {
            if (!isPhoneLogin) {
                isPhoneLogin = true;
                updateLoginTypeUI(true);
            }
        });
    }
    
    private void updateLoginTypeUI(boolean phoneMode) {
        if (phoneMode) {
            // Phone login mode
            tilEmail.setVisibility(View.GONE);
            tilPhone.setVisibility(View.VISIBLE);
            tilPassword.setHint(getString(R.string.password_pin_hint));
            etPassword.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            etPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
            
            tvLoginTypeEmail.setAlpha(0.6f);
            tvLoginTypeEmail.setBackgroundResource(R.color.card_background);
            tvLoginTypePhone.setAlpha(1.0f);
            tvLoginTypePhone.setBackgroundResource(R.drawable.modern_button_primary);
        } else {
            // Email login mode
            tilEmail.setVisibility(View.VISIBLE);
            tilPhone.setVisibility(View.GONE);
            tilPassword.setHint(getString(R.string.password_hint));
            etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            etPassword.setFilters(new InputFilter[0]); // Remove length filter
            
            tvLoginTypeEmail.setAlpha(1.0f);
            tvLoginTypeEmail.setBackgroundResource(R.drawable.modern_button_primary);
            tvLoginTypePhone.setAlpha(0.6f);
            tvLoginTypePhone.setBackgroundResource(R.color.card_background);
        }
        
        // Clear fields when switching
        etEmail.setText("");
        etPhone.setText("");
        etPassword.setText("");
    }
    
    /**
     * Apply current language to login screen UI elements
     */
    private void applyCurrentLanguage() {
        // No-op now; locale is handled via attachBaseContext
    }
    
    /**
     * Apply current language to login screen UI elements
     */
    private void translateLoginUI() {
        // No-op
    }

    private void loginUser() {
        if (isPhoneLogin) {
            // Phone login
            String phone = etPhone.getText().toString().trim();
            String pin = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(pin)) {
                showError(getString(R.string.fill_all_fields));
                return;
            }

            // Validate PIN is 6 digits
            if (pin.length() != 6 || !pin.matches("\\d{6}")) {
                showError(getString(R.string.invalid_pin));
                return;
            }

            // Validate phone number format (basic check)
            if (phone.length() < 8) {
                showError(getString(R.string.invalid_phone_number));
                return;
            }

            performPhoneLogin(phone, pin);
        } else {
            // Email login
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                showError(getString(R.string.fill_all_fields));
                return;
            }

            // Check if THIS specific user can work offline
            if (canUserWorkOffline(email)) {
                // Try offline login
                attemptOfflineLogin(email, password);
            } else {
                // Online login required
                performOnlineLogin(email, password);
            }
        }
    }
    
    /**
     * Check if a specific user can work offline (has valid license in local DB)
     */
    private boolean canUserWorkOffline(String email) {
        // Use the new user-specific method
        boolean canWorkOffline = sessionManager.isFirstLoginCompleteForUser(email);
        Log.d("LoginActivity", "User " + email + " can work offline: " + canWorkOffline);
        
        // Debug: Check if credentials exist for this user
        com.example.myapplication.database.entities.CredentialEntity credential = 
            sessionManager.getCredentialByEmail(email);
        Log.d("LoginActivity", "Stored credentials for " + email + ": " + (credential != null ? "YES" : "NO"));
        
        // Debug: Show all stored credentials
        java.util.List<com.example.myapplication.database.entities.CredentialEntity> allCreds = 
            sessionManager.getAllStoredCredentials();
        Log.d("LoginActivity", "All stored credentials: " + allCreds.size());
        for (com.example.myapplication.database.entities.CredentialEntity cred : allCreds) {
            Log.d("LoginActivity", "  - " + cred.getEmail() + " (UserID: " + cred.getUserId() + ")");
        }
        
        return canWorkOffline;
    }

    private void attemptOfflineLogin(String email, String password) {
        showLoading(getString(R.string.checking_offline_access));
        btnLogin.setEnabled(false);

        // Get user from local database
        UserEntity userEntity = sessionManager.getUserByEmail(email);
        if (userEntity == null) {
            showError("User not found in offline data");
            btnLogin.setEnabled(true);
            return;
        }
        
        // CRITICAL: Check if user is disabled
        if (userEntity.isDisabled()) {
            Log.d("LoginActivity", "User is disabled, cannot login offline: " + email);
            showError("Your account has been disabled. Please contact support.");
            btnLogin.setEnabled(true);
            return;
        }
        
        // CRITICAL: Validate password against stored credentials
        if (!validateOfflinePassword(email, password)) {
            showError("Invalid password");
            btnLogin.setEnabled(true);
            return;
        }
        
        // CRITICAL: Check license expiry for offline login
        com.example.myapplication.database.entities.LicenseEntity licenseEntity = 
            sessionManager.getLicenseByUser(userEntity.getUid());
        if (licenseEntity != null) {
            if (licenseEntity.isExpired()) {
                showError("Your license has expired. Please contact support.");
                btnLogin.setEnabled(true);
                return;
            }
            if (!licenseEntity.isValid()) {
                showError("License is invalid or inactive. Please contact support.");
                btnLogin.setEnabled(true);
                return;
            }
        } else {
            showError("No license found for this user. Please contact support.");
            btnLogin.setEnabled(true);
            return;
        }
        
        // Start offline session for this specific user
        if (sessionManager.startOfflineSessionForUser(userEntity)) {
            showSuccess(getString(R.string.login_successful));
            navigateToDashboard();
        } else {
            showError(getString(R.string.login_failed));
            btnLogin.setEnabled(true);
        }
    }
    
    /**
     * Validate password for offline login
     * This checks against stored credentials in local database
     */
    private boolean validateOfflinePassword(String email, String password) {
        // Get stored credential from database
        com.example.myapplication.database.entities.CredentialEntity credential = 
            sessionManager.getCredentialByEmail(email);
        
        if (credential == null) {
            Log.d("LoginActivity", "No stored credentials found for: " + email);
            return false;
        }
        
        // For now, we'll use simple string comparison
        // In production, you should use proper password hashing (BCrypt, etc.)
        String storedPassword = credential.getPasswordHash();
        
        if (password.equals(storedPassword)) {
            Log.d("LoginActivity", "Password validated for offline login: " + email);
            return true;
        } else {
            Log.d("LoginActivity", "Invalid password for offline login: " + email);
            return false;
        }
    }

    private void performPhoneLogin(String phone, String pin) {
        showLoading(getString(R.string.signing_in));
        btnLogin.setEnabled(false);

        // Normalize phone number (remove spaces, dashes, etc.)
        String normalizedPhone = phone.replaceAll("[\\s\\-\\(\\)]", "");

        // Query Firestore for user by phone number
        db.collection("users")
                .whereEqualTo("phone", normalizedPhone)
                .whereEqualTo("role", "agent") // Only agents can login with phone
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        com.google.firebase.firestore.QueryDocumentSnapshot document = 
                            (com.google.firebase.firestore.QueryDocumentSnapshot) task.getResult().getDocuments().get(0);
                        
                        // Get user data
                        String userId = document.getId();
                        String email = document.getString("email");
                        String storedPin = document.getString("pin"); // 6-digit PIN stored in Firestore
                        Boolean disabled = document.getBoolean("disabled");
                        Boolean active = document.getBoolean("active");
                        
                        // Validate PIN
                        if (storedPin == null || !storedPin.equals(pin)) {
                            btnLogin.setEnabled(true);
                            showError("Invalid PIN");
                            return;
                        }
                        
                        // Check if user is disabled
                        if (Boolean.TRUE.equals(disabled) || Boolean.FALSE.equals(active)) {
                            btnLogin.setEnabled(true);
                            showError("Your account has been disabled. Please contact support.");
                            return;
                        }
                        
                        // Get user's email and sign in with Firebase Auth
                        if (TextUtils.isEmpty(email)) {
                            btnLogin.setEnabled(true);
                            showError("User email not found. Please contact support.");
                            return;
                        }
                        
                        // Now sign in with Firebase Auth using email
                        // We need to get the password from Firestore or use a default
                        // For now, we'll query for the password hash or use email-based auth
                        // Since Firebase Auth requires email/password, we'll need to store password in Firestore
                        // OR use custom authentication token
                        
                        // Get password from Firestore (stored when user was created)
                        // Note: In production, consider using Firebase Custom Authentication tokens
                        String password = document.getString("password");
                        
                        if (TextUtils.isEmpty(password)) {
                            btnLogin.setEnabled(true);
                            showError("Password not configured. Please use email login or contact support.");
                            return;
                        }
                        
                        // Sign in with email and password
                        mAuth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener(authTask -> {
                                btnLogin.setEnabled(true);
                                if (authTask.isSuccessful()) {
                                    showSuccess(getString(R.string.login_successful));
                                    
                                    // Get user profile and validate license
                                    authManager.getCurrentUser(new AuthManager.AuthCallback() {
                                        @Override
                                        public void onSuccess(User user) {
                                            // CRITICAL: Check if user is disabled
                                            if (user.isDisabled()) {
                                                Log.d("LoginActivity", "User is disabled, cannot login: " + email);
                                                showError("Your account has been disabled. Please contact support.");
                                                mAuth.signOut();
                                                return;
                                            }
                                            
                                            // Store credentials for offline login
                                            sessionManager.storeCredentials(email, password, user.getUid());
                                            validateUserLicense(user);
                                        }

                                        @Override
                                        public void onError(String error) {
                                            showError("Failed to get user profile: " + error);
                                        }
                                    });
                                } else {
                                    showError(getString(R.string.login_failed) + ": " + authTask.getException().getMessage());
                                }
                            });
                        
                    } else {
                        btnLogin.setEnabled(true);
                        if (task.getResult().isEmpty()) {
                            showError(getString(R.string.user_not_found_phone));
                        } else {
                            showError(getString(R.string.login_failed) + ": " + task.getException().getMessage());
                        }
                    }
                });
    }

    private void performOnlineLogin(String email, String password) {
        showLoading(getString(R.string.signing_in));
        btnLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    btnLogin.setEnabled(true);
                    if (task.isSuccessful()) {
                        showSuccess(getString(R.string.login_successful));
                        
                        // Get user profile and validate license
                        authManager.getCurrentUser(new AuthManager.AuthCallback() {
                            @Override
                            public void onSuccess(User user) {
                                // CRITICAL: Check if user is disabled
                                if (user.isDisabled()) {
                                    Log.d("LoginActivity", "User is disabled, cannot login online: " + email);
                                    showError("Your account has been disabled. Please contact support.");
                                    mAuth.signOut();
                                    return;
                                }
                                
                                // Store credentials for offline login
                                sessionManager.storeCredentials(email, password, user.getUid());
                                validateUserLicense(user);
                            }

                            @Override
                            public void onError(String error) {
                                showError("Failed to get user profile: " + error);
                            }
                        });
                    } else {
                        showError(getString(R.string.login_failed) + ": " + task.getException().getMessage());
                    }
                });
    }

    private void validateUserLicense(User user) {
        showLoading(getString(R.string.validating_license));
        
        licenseManager.validateLicense(user.getUid(), new LicenseManager.LicenseCallback() {
            @Override
            public void onValid(License license) {
                // Start online session with validated user and license
                sessionManager.startOnlineSession(user, license);
                
                showSuccess(getString(R.string.license_validated));
                
                // Navigate to dashboard
                navigateToDashboard();
            }

            @Override
            public void onInvalid(String reason) {
                showError(getString(R.string.license_invalid) + ": " + reason);
                // Navigate to license activation
                Intent intent = new Intent(LoginActivity.this, LicenseActivationActivity.class);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                showError("License validation error: " + error);
            }
        });
    }


    private void showLoading(String message) {
        tvStatus.setText(message);
        tvStatus.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
    }

    private void showSuccess(String message) {
        tvStatus.setText(message);
        tvStatus.setTextColor(getResources().getColor(R.color.success_green, getTheme()));
    }

    private void showError(String message) {
        tvStatus.setText(message);
        tvStatus.setTextColor(getResources().getColor(R.color.error_red, getTheme()));
    }

    private void navigateToDashboard() {
        // Get user role and navigate to appropriate dashboard
        if (sessionManager.getUserFromSession() != null) {
            String userId = sessionManager.getUserFromSession().getUid();
            
            // Check if this is first login and needs compulsory sync
            FirstLoginSyncService syncService = new FirstLoginSyncService(this);
            syncService.checkAndSyncFirstLogin(userId, new FirstLoginSyncService.FirstLoginSyncCallback() {
                @Override
                public void onSyncStarted() {
                    // Sync started - user will see progress dialog
                }

                @Override
                public void onSyncProgress(String message, int current, int total) {
                    // Progress updates handled by the service
                }

                @Override
                public void onSyncComplete(boolean success, String message) {
                    runOnUiThread(() -> {
                        if (success) {
                            // Sync completed (or no data to sync), proceed to dashboard
                            proceedToDashboard();
                        } else {
                            // Sync failed, show error but still allow navigation
                            Toast.makeText(LoginActivity.this, "Sync failed: " + message, Toast.LENGTH_LONG).show();
                            proceedToDashboard();
                        }
                    });
                }
            });
        }
    }
    
    private void proceedToDashboard() {
        if (sessionManager.getUserFromSession() != null) {
            String role = sessionManager.getUserFromSession().getRole();
            Intent intent;
            
            if ("dealer".equals(role)) {
                intent = new Intent(LoginActivity.this, DealerDashboardActivity.class);
            } else if ("agent".equals(role)) {
                intent = new Intent(LoginActivity.this, AgentDashboardActivity.class);
            } else {
                // Default to dealer dashboard
                intent = new Intent(LoginActivity.this, DealerDashboardActivity.class);
            }
            
            // Signal that stats should be refreshed after first-time sync
            intent.putExtra("refreshStatsAfterSync", true);
            
            startActivity(intent);
            finish();
        }
    }
}


