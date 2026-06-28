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
import com.example.myapplication.utils.PinHasher;
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

    private EditText etEmail, etPassword, etPhone, etPin;
    private Button btnLogin;
    private TextView tvStatus;
    private TextView tvWelcomeTitle; // "Welcome Back"
    private TextView tvSubtitle;     // "Sign in to your account"
    private TextView tvLoginTypeEmail, tvLoginTypePhone;
    private com.google.android.material.textfield.TextInputLayout tilEmail, tilPassword, tilPhone, tilPin;
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
        etPin = findViewById(R.id.etPin);
        btnLogin = findViewById(R.id.btnLogin);
        tvStatus = findViewById(R.id.tvStatus);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilPhone = findViewById(R.id.tilPhone);
        tilPin = findViewById(R.id.tilPin);
        // Optional: these may be null if ids not present in layout
        tvWelcomeTitle = findViewById(getResources().getIdentifier("tvWelcomeTitle", "id", getPackageName()));
        tvSubtitle = findViewById(getResources().getIdentifier("tvSubtitle", "id", getPackageName()));
        tvLoginTypeEmail = findViewById(R.id.tvLoginTypeEmail);
        tvLoginTypePhone = findViewById(R.id.tvLoginTypePhone);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);

        EdgeToEdgeHelper.setupHeaderInsets(findViewById(R.id.headerLayout), this);

        android.widget.ScrollView loginScrollView = findViewById(R.id.loginScrollView);
        EdgeToEdgeHelper.setupImeInsets(loginScrollView);
        setupLoginFieldScroll(loginScrollView);

        setupLanguageSpinner();
        setupLoginTypeToggle();
        btnLogin.setOnClickListener(v -> loginUser());
        
        // No explicit apply here; locale is already applied via attachBaseContext
    }

    private void setupLoginFieldScroll(android.widget.ScrollView scrollView) {
        if (scrollView == null) {
            return;
        }

        View.OnFocusChangeListener scrollOnFocus = (v, hasFocus) -> {
            if (!hasFocus) {
                return;
            }
            scrollView.postDelayed(() -> {
                int fieldTop = v.getTop();
                View fieldParent = (View) v.getParent();
                while (fieldParent != null && fieldParent != scrollView.getChildAt(0)) {
                    fieldTop += fieldParent.getTop();
                    fieldParent = (View) fieldParent.getParent();
                }
                int offset = (int) (120 * getResources().getDisplayMetrics().density);
                scrollView.smoothScrollTo(0, Math.max(0, fieldTop - offset));
            }, 150);
        };

        etEmail.setOnFocusChangeListener(scrollOnFocus);
        etPassword.setOnFocusChangeListener(scrollOnFocus);
        if (etPhone != null) {
            etPhone.setOnFocusChangeListener(scrollOnFocus);
        }
        if (etPin != null) {
            etPin.setOnFocusChangeListener(scrollOnFocus);
        }
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
        // Default to phone + PIN login
        isPhoneLogin = true;
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
            // Phone + PIN mode
            tilEmail.setVisibility(View.GONE);
            tilPassword.setVisibility(View.GONE);
            tilPhone.setVisibility(View.VISIBLE);
            tilPin.setVisibility(View.VISIBLE);
            btnLogin.setVisibility(View.VISIBLE);

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
            tilPin.setVisibility(View.GONE);
            btnLogin.setVisibility(View.VISIBLE);

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
        if (etPin != null) etPin.setText("");
    }

    private void loginUser() {
        if (isPhoneLogin) {
            loginWithPhonePin();
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
        
        // Verify against stored hash (supports legacy plaintext migration)
        String storedPassword = credential.getPasswordHash();
        if (com.example.myapplication.utils.PasswordHasher.verifyPassword(password, storedPassword)) {
            if (!storedPassword.contains(":")) {
                sessionManager.storeCredentials(email, password, credential.getUserId());
            }
            Log.d("LoginActivity", "Password validated for offline login: " + email);
            return true;
        } else {
            Log.d("LoginActivity", "Invalid password for offline login: " + email);
            return false;
        }
    }

    // --- Phone + PIN login helpers (online only) ---

    // --- Phone + PIN login helpers (online only) ---

    private void loginWithPhonePin() {
        if (etPhone == null || etPin == null) {
            showError(getString(R.string.fill_all_fields));
            return;
        }

        String phone = etPhone.getText().toString().trim();
        String pin = etPin.getText().toString().trim();

        if (TextUtils.isEmpty(phone) || phone.length() < 8) {
            showError(getString(R.string.invalid_phone_number));
            return;
        }

        if (TextUtils.isEmpty(pin) || pin.length() != 6 || !pin.matches("\\d{6}")) {
            showError("Please enter a valid 6-digit PIN");
            return;
        }

        // Normalize phone number (remove spaces, dashes, parentheses, plus signs)
        String normalizedPhone = phone.replaceAll("[\\s\\-\\(\\)\\+]", "");

        showLoading(getString(R.string.signing_in));
        btnLogin.setEnabled(false);

        // Find user by phone number in Firestore
        // Since phone numbers might be stored in different formats, we need to fetch all users
        // and check normalized phone numbers
        db.collection("users")
                .get()
                .addOnCompleteListener(task -> {
                    btnLogin.setEnabled(true);
                    
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Log.e("LoginActivity", "Error fetching users: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                        showError("Failed to connect. Please check your internet connection.");
                        return;
                    }
                    
                    // Find ALL users with matching normalized phone number
                    java.util.List<com.google.firebase.firestore.QueryDocumentSnapshot> matchingUsers = new java.util.ArrayList<>();
                    
                    Log.d("LoginActivity", "Searching for phone: " + normalizedPhone + " (normalized from: " + phone + ")");
                    Log.d("LoginActivity", "Total users in database: " + task.getResult().size());
                    
                    for (com.google.firebase.firestore.QueryDocumentSnapshot document : task.getResult()) {
                        String userId = document.getId();
                        String storedPhone = document.getString("phone");
                        String userEmail = document.getString("email");
                        String userName = document.getString("name");
                        String userRole = document.getString("role");
                        
                        // Log each user for debugging
                        Log.d("LoginActivity", "Checking user: ID=" + userId + ", Email=" + userEmail + 
                            ", Name=" + userName + ", Role=" + userRole + ", Phone=" + 
                            (storedPhone != null ? storedPhone : "NULL"));
                        
                        // CRITICAL: Only check users that have a phone number
                        if (storedPhone == null || storedPhone.trim().isEmpty()) {
                            Log.d("LoginActivity", "Skipping user " + userId + " - no phone number");
                            continue;
                        }
                        
                        // Normalize stored phone number
                        String normalizedStoredPhone = storedPhone.replaceAll("[\\s\\-\\(\\)\\+]", "");
                        
                        Log.d("LoginActivity", "User " + userId + " phone: original='" + storedPhone + 
                            "', normalized='" + normalizedStoredPhone + "', matches=" + normalizedStoredPhone.equals(normalizedPhone));
                        
                        if (normalizedStoredPhone.equals(normalizedPhone)) {
                            Log.d("LoginActivity", "MATCH FOUND: User " + userId + " (" + userEmail + ") has matching phone");
                            matchingUsers.add(document);
                        }
                    }
                    
                    Log.d("LoginActivity", "Total matching users found: " + matchingUsers.size());
                    
                    if (matchingUsers.isEmpty()) {
                        Log.d("LoginActivity", "No user found with phone: " + normalizedPhone);
                        showError("User not found with this phone number");
                        return;
                    }
                    
                    if (matchingUsers.size() > 1) {
                        Log.w("LoginActivity", "WARNING: " + matchingUsers.size() + " users found with the same phone number: " + normalizedPhone);
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : matchingUsers) {
                            Log.w("LoginActivity", "  - UserID: " + doc.getId() + ", Email: " + doc.getString("email") + 
                                ", Name: " + doc.getString("name") + ", Role: " + doc.getString("role"));
                        }
                    }
                    
                    // Now verify PIN for each matching user and find the one with correct PIN
                    com.google.firebase.firestore.QueryDocumentSnapshot correctUser = null;
                    
                    for (com.google.firebase.firestore.QueryDocumentSnapshot document : matchingUsers) {
                        String userId = document.getId();
                        String hashedPin = document.getString("phonePin");
                        
                        Log.d("LoginActivity", "Checking user: ID=" + userId + ", Email=" + document.getString("email") + 
                            ", Name=" + document.getString("name") + ", Phone=" + document.getString("phone"));
                        
                        if (hashedPin == null || hashedPin.isEmpty()) {
                            Log.d("LoginActivity", "User " + userId + " has no PIN set, skipping");
                            continue;
                        }
                        
                        // Log the stored hash format for debugging
                        Log.d("LoginActivity", "Stored phonePin for user " + userId + ": length=" + hashedPin.length() + 
                            ", format=" + (hashedPin.contains(":") ? "salt:hash" : "invalid") + 
                            ", preview=" + (hashedPin.length() > 50 ? hashedPin.substring(0, 50) + "..." : hashedPin));
                        
                        // Verify PIN for this user
                        boolean pinValid = PinHasher.verifyPin(pin, hashedPin);
                        Log.d("LoginActivity", "PIN verification for user " + userId + " (" + 
                            document.getString("email") + "): " + (pinValid ? "VALID" : "INVALID") + 
                            " (entered PIN: " + pin + ")");
                        
                        if (pinValid) {
                            correctUser = document;
                            Log.d("LoginActivity", "Found correct user with matching phone and PIN: UserID=" + userId + 
                                ", Email=" + document.getString("email") + ", Name=" + document.getString("name"));
                            break;
                        } else {
                            Log.w("LoginActivity", "PIN mismatch for user " + userId + ". Stored hash format: " + 
                                (hashedPin.contains(":") ? "valid" : "invalid"));
                        }
                    }
                    
                    if (correctUser == null) {
                        Log.e("LoginActivity", "No user found with matching phone and PIN for phone: " + normalizedPhone);
                        showError("Invalid PIN. Please try again.");
                        return;
                    }
                    
                    // Use the correct user document (the one with matching phone AND PIN)
                    com.google.firebase.firestore.QueryDocumentSnapshot document = correctUser;
                    String userId = document.getId();
                    String email = document.getString("email");
                    String storedPhone = document.getString("phone");
                    
                    // CRITICAL SAFETY CHECK: Verify the user actually has a phone number
                    if (storedPhone == null || storedPhone.trim().isEmpty()) {
                        Log.e("LoginActivity", "SECURITY ERROR: Attempted to login user " + userId + 
                            " (" + email + ") but this user has NO phone number! This should never happen.");
                        showError("Security error: User data inconsistency. Please contact support.");
                        return;
                    }
                    
                    // Verify the phone number matches what we searched for
                    String normalizedStoredPhone = storedPhone.replaceAll("[\\s\\-\\(\\)\\+]", "");
                    if (!normalizedStoredPhone.equals(normalizedPhone)) {
                        Log.e("LoginActivity", "SECURITY ERROR: Phone mismatch! Searched for: " + normalizedPhone + 
                            ", but user has: " + normalizedStoredPhone);
                        showError("Security error: Phone number mismatch. Please contact support.");
                        return;
                    }
                    
                    Log.d("LoginActivity", "SECURITY CHECK PASSED: User " + userId + " has phone " + storedPhone + 
                        " (normalized: " + normalizedStoredPhone + ") matching search: " + normalizedPhone);
                    
                    // Check if user is disabled
                    Boolean disabled = document.getBoolean("disabled");
                    if (disabled != null && disabled) {
                        Log.w("LoginActivity", "User is disabled: " + userId);
                        showError("Your account has been disabled. Please contact support.");
                        return;
                    }

                    // Create User object from Firestore data - using the CORRECT document (matching phone AND PIN)
                    User user = new User();
                    user.setUid(userId);
                    user.setEmail(email != null ? email : "");
                    user.setName(document.getString("name"));
                    user.setPhone(storedPhone); // Use stored phone from document
                    user.setRole(document.getString("role"));
                    user.setDealerId(document.getString("dealerId"));
                    user.setDisabled(disabled != null && disabled);
                    
                    Log.d("LoginActivity", "=== LOGGING IN USER ===");
                    Log.d("LoginActivity", "UID: " + user.getUid());
                    Log.d("LoginActivity", "Email: " + user.getEmail());
                    Log.d("LoginActivity", "Name: " + user.getName());
                    Log.d("LoginActivity", "Role: " + user.getRole());
                    Log.d("LoginActivity", "Phone: " + user.getPhone());
                    Log.d("LoginActivity", "DealerId: " + user.getDealerId());
                    Log.d("LoginActivity", "Disabled: " + user.isDisabled());
                    
                    // Phone+PIN authentication is complete and verified
                    // Phone number and PIN have been validated from Firestore
                    // No email/password required - phone+PIN is sufficient for authentication
                    Log.d("LoginActivity", "Phone+PIN authentication successful - proceeding with license validation");
                    proceedWithPhoneLogin(user);
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

    private void proceedWithPhoneLogin(User user) {
        // Phone+PIN authentication is complete and verified
        // Phone number and PIN have been validated from Firestore
        // No email/password authentication needed - phone+PIN is sufficient
        
        Log.d("LoginActivity", "Phone+PIN authentication successful for user: " + user.getEmail());
        
        // CRITICAL: Save user to local database immediately so AuthManager can find them
        // This ensures the user is available even if license validation fails
        try {
            com.example.myapplication.database.entities.UserEntity userEntity = new com.example.myapplication.database.entities.UserEntity();
            userEntity.setUid(user.getUid());
            userEntity.setEmail(user.getEmail() != null ? user.getEmail() : "");
            userEntity.setName(user.getName() != null ? user.getName() : "");
            userEntity.setPhone(user.getPhone() != null ? user.getPhone() : "");
            userEntity.setRole(user.getRole() != null ? user.getRole() : "agent");
            userEntity.setDealerId(user.getDealerId());
            userEntity.setActive(user.isActive());
            userEntity.setDisabled(user.isDisabled());
            long now = System.currentTimeMillis();
            userEntity.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().getTime() : now);
            userEntity.setUpdatedAt(user.getUpdatedAt() != null ? user.getUpdatedAt().getTime() : now);
            userEntity.setLastSyncAt(now);
            
            com.example.myapplication.database.AppDatabase db =
                com.example.myapplication.database.AppDatabase.getDatabase(this);
            db.userDao().insertUser(userEntity);
            Log.d("LoginActivity", "User saved to local database: UID=" + user.getUid() + ", Email=" + user.getEmail());

            // Pending session: keep user for license screen but do not grant dashboard access yet
            sessionManager.beginPendingLicenseSession(user);

        } catch (Exception e) {
            Log.e("LoginActivity", "Error saving user/session to database: " + e.getMessage(), e);
        }
        
        Log.d("LoginActivity", "Proceeding with license validation");
        validateUserLicense(user);
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
                sessionManager.beginPendingLicenseSession(user);
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
    }
}


