package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
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
import com.example.myapplication.utils.EdgeToEdgeHelper;
import com.example.myapplication.utils.LicenseManager;
import com.example.myapplication.utils.SessionManager;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.adapters.LanguageSpinnerAdapter;
import com.example.myapplication.services.FirstLoginSyncService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

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

    private EditText etEmail, etPassword, etPhone, etOtp;
    private Button btnLogin, btnSendOtp, btnResendOtp;
    private TextView tvStatus;
    private TextView tvWelcomeTitle; // "Welcome Back"
    private TextView tvSubtitle;     // "Sign in to your account"
    private TextView tvLoginTypeEmail, tvLoginTypePhone;
    private com.google.android.material.textfield.TextInputLayout tilEmail, tilPassword, tilPhone, tilOtp;
    private Spinner spinnerLanguage;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private AuthManager authManager;
    private LicenseManager licenseManager;
    private SessionManager sessionManager;
    private LanguageManager languageManager;
    private static long lastLanguageChangeTime = 0;
    private boolean isUserChangingLanguage = false;
    private boolean isPhoneLogin = false;

    // Phone auth state
    private String verificationId = null;
    private PhoneAuthProvider.ForceResendingToken resendToken = null;
    
    // Phone login flow state
    private enum PhoneLoginState {
        INITIAL,      // Only phone field and Request OTP button visible
        OTP_SENT,     // OTP field and Login button visible, Request OTP hidden
        RESEND_AVAILABLE  // Resend OTP button visible
    }
    private PhoneLoginState phoneLoginState = PhoneLoginState.INITIAL;
    private android.os.CountDownTimer countDownTimer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge display
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        
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
        etOtp = findViewById(R.id.etOtp);
        btnLogin = findViewById(R.id.btnLogin);
        btnSendOtp = findViewById(R.id.btnSendOtp);
        btnResendOtp = findViewById(R.id.btnResendOtp);
        tvStatus = findViewById(R.id.tvStatus);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilPhone = findViewById(R.id.tilPhone);
        tilOtp = findViewById(R.id.tilOtp);
        // Optional: these may be null if ids not present in layout
        tvWelcomeTitle = findViewById(getResources().getIdentifier("tvWelcomeTitle", "id", getPackageName()));
        tvSubtitle = findViewById(getResources().getIdentifier("tvSubtitle", "id", getPackageName()));
        tvLoginTypeEmail = findViewById(R.id.tvLoginTypeEmail);
        tvLoginTypePhone = findViewById(R.id.tvLoginTypePhone);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);

        EdgeToEdgeHelper.setupHeaderInsets(findViewById(R.id.headerLayout), this);
        setupLanguageSpinner();
        setupLoginTypeToggle();
        btnLogin.setOnClickListener(v -> loginUser());
        btnSendOtp.setOnClickListener(v -> sendOtp());
        btnResendOtp.setOnClickListener(v -> resendOtp());
        
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

    private void setupLoginTypeToggle() {
        // Default to email login
        isPhoneLogin = false;
        updateLoginTypeUI();

        tvLoginTypeEmail.setOnClickListener(v -> {
            if (isPhoneLogin) {
                isPhoneLogin = false;
                updateLoginTypeUI();
            }
        });

        tvLoginTypePhone.setOnClickListener(v -> {
            if (!isPhoneLogin) {
                isPhoneLogin = true;
                updateLoginTypeUI();
            }
        });
    }

    private void updateLoginTypeUI() {
        if (isPhoneLogin) {
            // Phone + OTP mode
            tilEmail.setVisibility(View.GONE);
            tilPassword.setVisibility(View.GONE);
            tilPhone.setVisibility(View.VISIBLE);
            
            // Reset phone login state to initial
            phoneLoginState = PhoneLoginState.INITIAL;
            updatePhoneLoginUI();

            // Update toggle button states
            tvLoginTypeEmail.setBackgroundResource(R.drawable.toggle_button_inactive_selector);
            tvLoginTypeEmail.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
            tvLoginTypePhone.setBackgroundResource(R.drawable.toggle_button_active_selector);
            tvLoginTypePhone.setTextColor(getResources().getColor(R.color.text_white, getTheme()));
        } else {
            // Email/password mode
            tilEmail.setVisibility(View.VISIBLE);
            tilPassword.setVisibility(View.VISIBLE);
            tilPhone.setVisibility(View.GONE);
            tilOtp.setVisibility(View.GONE);
            btnSendOtp.setVisibility(View.GONE);
            btnResendOtp.setVisibility(View.GONE);
            btnLogin.setVisibility(View.VISIBLE);
            
            // Cancel any running timer
            if (countDownTimer != null) {
                countDownTimer.cancel();
                countDownTimer = null;
            }

            // Update toggle button states
            tvLoginTypeEmail.setBackgroundResource(R.drawable.toggle_button_active_selector);
            tvLoginTypeEmail.setTextColor(getResources().getColor(R.color.text_white, getTheme()));
            tvLoginTypePhone.setBackgroundResource(R.drawable.toggle_button_inactive_selector);
            tvLoginTypePhone.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
        }

        // Clear fields when switching modes
        etEmail.setText("");
        etPassword.setText("");
        if (etPhone != null) etPhone.setText("");
        if (etOtp != null) etOtp.setText("");
        verificationId = null;
        resendToken = null;
    }
    
    /**
     * Update UI visibility based on phone login state
     */
    private void updatePhoneLoginUI() {
        if (!isPhoneLogin) return;
        
        switch (phoneLoginState) {
            case INITIAL:
                // Only phone field and Request OTP button visible
                tilOtp.setVisibility(View.GONE);
                btnSendOtp.setVisibility(View.VISIBLE);
                btnResendOtp.setVisibility(View.GONE);
                btnLogin.setVisibility(View.GONE);
                break;
                
            case OTP_SENT:
                // OTP field and Login button visible, Request OTP hidden
                tilOtp.setVisibility(View.VISIBLE);
                btnSendOtp.setVisibility(View.GONE);
                btnResendOtp.setVisibility(View.GONE);
                btnLogin.setVisibility(View.VISIBLE);
                break;
                
            case RESEND_AVAILABLE:
                // OTP field, Login button, and Resend OTP button visible
                tilOtp.setVisibility(View.VISIBLE);
                btnSendOtp.setVisibility(View.GONE);
                btnResendOtp.setVisibility(View.VISIBLE);
                btnLogin.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void loginUser() {
        if (isPhoneLogin) {
            loginWithPhoneOtp();
            return;
        }

        // Email/password login (existing behaviour)
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

    // --- Phone + OTP login helpers (online only) ---

    private void sendOtp() {
        if (etPhone == null) {
            showError(getString(R.string.invalid_phone_number));
            return;
        }

        String phone = etPhone.getText().toString().trim();
        if (TextUtils.isEmpty(phone) || phone.length() < 8) {
            showError(getString(R.string.invalid_phone_number));
            return;
        }

        // Normalize phone number (remove spaces, dashes, parentheses)
        String normalizedPhone = phone.replaceAll("[\\s\\-\\(\\)]", "");

        showLoading(getString(R.string.signing_in));

        // IMPORTANT: Only allow OTP for phone numbers that exist in Firestore users
        db.collection("users")
                .whereEqualTo("phone", normalizedPhone)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                        Log.d("LoginActivity", "No user found with phone: " + normalizedPhone);
                        showError(getString(R.string.user_not_found_phone));
                        return;
                    }

                    // At least one user exists with this phone; proceed with Firebase Phone Auth
                    PhoneAuthOptions options =
                            PhoneAuthOptions.newBuilder(mAuth)
                                    .setPhoneNumber(normalizedPhone)
                                    .setTimeout(60L, TimeUnit.SECONDS)
                                    .setActivity(this)
                                    .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                        @Override
                                        public void onVerificationCompleted(PhoneAuthCredential credential) {
                                            // Auto-retrieval or instant verification
                                            Log.d("LoginActivity", "Phone verification completed automatically");
                                            signInWithPhoneCredential(credential);
                                        }

                                        @Override
                                        public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                                            Log.e("LoginActivity", "Phone verification failed", e);
                                            showError(getString(R.string.login_failed) + ": " + e.getMessage());
                                        }

                                        @Override
                                        public void onCodeSent(String verId,
                                                               PhoneAuthProvider.ForceResendingToken token) {
                                            Log.d("LoginActivity", "OTP code sent");
                                            verificationId = verId;
                                            resendToken = token;
                                            showSuccess(getString(R.string.otp_sent));
                                            
                                            // Update state to OTP_SENT and show OTP field + Login button
                                            phoneLoginState = PhoneLoginState.OTP_SENT;
                                            updatePhoneLoginUI();
                                            
                                            // Start 60-second countdown timer
                                            startResendCountdown();
                                        }
                                    })
                                    .build();

                    PhoneAuthProvider.verifyPhoneNumber(options);
                });
    }
    
    /**
     * Start 60-second countdown timer for resend OTP button
     */
    private void startResendCountdown() {
        // Cancel any existing timer
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        
        countDownTimer = new android.os.CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Update button text with countdown (optional - we'll just wait)
                long secondsRemaining = millisUntilFinished / 1000;
                // You can update a text view here if needed, but we'll just wait
            }
            
            @Override
            public void onFinish() {
                // After 60 seconds, show resend button
                if (phoneLoginState == PhoneLoginState.OTP_SENT) {
                    phoneLoginState = PhoneLoginState.RESEND_AVAILABLE;
                    updatePhoneLoginUI();
                }
            }
        };
        countDownTimer.start();
    }
    
    /**
     * Resend OTP using the resend token
     */
    private void resendOtp() {
        if (etPhone == null) {
            showError(getString(R.string.invalid_phone_number));
            return;
        }

        String phone = etPhone.getText().toString().trim();
        if (TextUtils.isEmpty(phone) || phone.length() < 8) {
            showError(getString(R.string.invalid_phone_number));
            return;
        }

        // Normalize phone number
        String normalizedPhone = phone.replaceAll("[\\s\\-\\(\\)]", "");

        if (resendToken == null) {
            showError("Cannot resend OTP. Please request a new OTP.");
            return;
        }

        showLoading("Resending OTP...");

        // Resend OTP using the resend token
        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(normalizedPhone)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            @Override
                            public void onVerificationCompleted(PhoneAuthCredential credential) {
                                Log.d("LoginActivity", "Phone verification completed automatically (resend)");
                                signInWithPhoneCredential(credential);
                            }

                            @Override
                            public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                                Log.e("LoginActivity", "Phone verification failed (resend)", e);
                                showError(getString(R.string.login_failed) + ": " + e.getMessage());
                            }

                            @Override
                            public void onCodeSent(String verId,
                                                   PhoneAuthProvider.ForceResendingToken token) {
                                Log.d("LoginActivity", "OTP code resent");
                                verificationId = verId;
                                resendToken = token;
                                showSuccess(getString(R.string.otp_sent));
                                
                                // Reset to OTP_SENT state and restart countdown
                                phoneLoginState = PhoneLoginState.OTP_SENT;
                                updatePhoneLoginUI();
                                startResendCountdown();
                            }
                        })
                        .setForceResendingToken(resendToken)
                        .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void loginWithPhoneOtp() {
        if (etPhone == null || etOtp == null) {
            showError(getString(R.string.fill_all_fields));
            return;
        }

        String phone = etPhone.getText().toString().trim();
        String code = etOtp.getText().toString().trim();

        if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(code)) {
            showError(getString(R.string.fill_all_fields));
            return;
        }

        if (verificationId == null) {
            showError(getString(R.string.otp_not_requested));
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneCredential(credential);
    }

    private void signInWithPhoneCredential(PhoneAuthCredential credential) {
        showLoading(getString(R.string.signing_in));
        btnLogin.setEnabled(false);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    btnLogin.setEnabled(true);
                    if (task.isSuccessful()) {
                        Log.d("LoginActivity", "Phone auth sign-in successful");
                        showSuccess(getString(R.string.login_successful));

                        // Reuse existing flow: get user profile and validate license
                        authManager.getCurrentUser(new AuthManager.AuthCallback() {
                            @Override
                            public void onSuccess(User user) {
                                // IMPORTANT: no offline password stored for phone login
                                // We just validate license and start online session.
                                validateUserLicense(user);
                            }

                            @Override
                            public void onError(String error) {
                                showError("Failed to get user profile: " + error);
                            }
                        });
                    } else {
                        Log.e("LoginActivity", "Phone auth sign-in failed", task.getException());
                        showError(getString(R.string.login_failed) + ": " +
                                (task.getException() != null ? task.getException().getMessage() : ""));
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel countdown timer to prevent memory leaks
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }
}


