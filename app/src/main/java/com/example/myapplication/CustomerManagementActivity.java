package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapters.CustomerAdapter;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.CustomerEntity;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.SessionManager;
import com.example.myapplication.utils.SyncManager;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CustomerManagementActivity extends AppCompatActivity {
    
    private static final String TAG = "CustomerManagement";
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    
    // UI Components
    private TextView tvWelcome, tvUserInfo;
    private ImageView btnMenu, ivLanguageFlag;
    private Button btnScanBarcode, btnManualEntry, btnStopScanning;
    private EditText etSearch;
    private RecyclerView recyclerViewCustomers;
    private CustomerAdapter customerAdapter;
    private android.view.View cameraPreviewCard;
    
    // Camera and Barcode Scanning
    private PreviewView previewView;
    private BarcodeScanner barcodeScanner;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private boolean isScanning = false;
    
    // Database and Session
    private AppDatabase database;
    private SessionManager sessionManager;
    private LanguageManager languageManager;
    private SyncManager syncManager;
    private UserEntity currentUser;
    
    // Data
    private List<CustomerEntity> customers = new ArrayList<>();
    private String currentUserRole;
    
    // Language change debounce
    private static long lastLanguageChangeTime = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_management);
        
        initDatabase();
        initViews();
        setupLanguageSpinner();
        setupClickListeners();
        loadCustomers();
    }
    
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        // Apply language configuration
        languageManager = LanguageManager.getInstance(newBase);
        android.content.res.Configuration config = newBase.getResources().getConfiguration();
        android.content.res.Configuration newConfig = new android.content.res.Configuration(config);
        
        String currentLang = languageManager.getCurrentLanguage();
        Locale locale = new Locale(currentLang);
        newConfig.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(newConfig));
    }
    
    private void initViews() {
        // Header components
        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        btnMenu = findViewById(R.id.btnMenu);
        ivLanguageFlag = findViewById(R.id.ivLanguageFlag);
        
        // Main components
        btnScanBarcode = findViewById(R.id.btnScanBarcode);
        btnManualEntry = findViewById(R.id.btnManualEntry);
        btnStopScanning = findViewById(R.id.btnStopScanning);
        etSearch = findViewById(R.id.etSearch);
        recyclerViewCustomers = findViewById(R.id.recyclerViewCustomers);
        previewView = findViewById(R.id.previewView);
        cameraPreviewCard = findViewById(R.id.cameraPreviewCard);
        
        // Setup RecyclerView
        recyclerViewCustomers.setLayoutManager(new LinearLayoutManager(this));
        String currentUserId = (currentUser != null) ? currentUser.getUid() : "";
        Log.d(TAG, "initViews: currentUser = " + (currentUser != null ? currentUser.getEmail() : "NULL"));
        Log.d(TAG, "initViews: currentUserId = " + currentUserId);
        customerAdapter = new CustomerAdapter(customers, currentUserId, this::onCustomerClick, this::onCustomerEdit, this::onCustomerDelete);
        recyclerViewCustomers.setAdapter(customerAdapter);
        
        // Update UI with user info now that UI elements are initialized
        if (currentUser != null) {
            tvWelcome.setText(getString(R.string.customer_management));
            tvUserInfo.setText(currentUser.getEmail() + " (" + currentUser.getRole() + ")");
        }
    }
    
    private void initDatabase() {
        try {
            Log.d(TAG, "initDatabase: Starting database initialization");
            database = AppDatabase.getDatabase(this);
            Log.d(TAG, "initDatabase: Database initialized");
            
            sessionManager = new SessionManager(this);
            Log.d(TAG, "initDatabase: SessionManager initialized");
            
            syncManager = new SyncManager(this);
            Log.d(TAG, "initDatabase: SyncManager initialized");
            
            languageManager = LanguageManager.getInstance(this);
            Log.d(TAG, "initDatabase: LanguageManager initialized");
            
            // Try both methods to get user
            currentUser = sessionManager.getUserFromSession();
            Log.d(TAG, "initDatabase: getUserFromSession() = " + (currentUser != null ? currentUser.getEmail() : "NULL"));
            
            if (currentUser == null) {
                currentUser = sessionManager.getCurrentUser();
                Log.d(TAG, "initDatabase: getCurrentUser() = " + (currentUser != null ? currentUser.getEmail() : "NULL"));
            }
            
            // Check session status
            boolean isLoggedIn = sessionManager.isLoggedIn();
            Log.d(TAG, "initDatabase: isLoggedIn = " + isLoggedIn);
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing database: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing app. Please restart.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        if (currentUser != null) {
            currentUserRole = currentUser.getRole();
            
            // UI will be updated in initViews() after UI elements are initialized
        } else {
            Log.e(TAG, "No current user found - creating fallback user");
            
            // Create a fallback user to prevent crashes
            currentUser = new com.example.myapplication.database.entities.UserEntity();
            currentUser.setUid("fallback_user");
            currentUser.setEmail("unknown@user.com");
            currentUser.setRole("agent");
            currentUser.setActive(true);
            currentUser.setCreatedAt(System.currentTimeMillis());
            currentUser.setUpdatedAt(System.currentTimeMillis());
            
            currentUserRole = "agent";
            
            Log.d(TAG, "Created fallback user to prevent crash");
        }
        
        // Initialize barcode scanner
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        
        // Start auto-sync and perform an initial pull so fresh installs see server data
        syncManager.startAutoSync();
        if (syncManager.isOnline()) {
            syncManager.downloadCustomers(this::loadCustomers);
        }
    }
    
    private void setupLanguageSpinner() {
        updateLanguageFlag();
        
        ivLanguageFlag.setOnClickListener(v -> {
            // Debounce mechanism to prevent rapid language changes
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLanguageChangeTime < 1000) {
                return;
            }
            lastLanguageChangeTime = currentTime;
            
            // Toggle language
            String currentLang = languageManager.getCurrentLanguage();
            String newLang = "en".equals(currentLang) ? "fr" : "en";
            
            languageManager.setLanguage(newLang);
            recreate();
        });
    }
    
    private void updateLanguageFlag() {
        String currentLang = languageManager.getCurrentLanguage();
        if ("fr".equals(currentLang)) {
            ivLanguageFlag.setImageResource(R.drawable.ic_flag_fr);
        } else {
            ivLanguageFlag.setImageResource(R.drawable.ic_flag_us);
        }
    }
    
    private void setupClickListeners() {
        btnMenu.setOnClickListener(v -> showUserDetailsDialog());
        
        btnScanBarcode.setOnClickListener(v -> startBarcodeScanning());
        btnManualEntry.setOnClickListener(v -> showManualEntryDialog());
        btnStopScanning.setOnClickListener(v -> stopBarcodeScanning());
        
        // Search functionality
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchCustomers(s.toString());
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }
    
    private void loadCustomers() {
        new Thread(() -> {
            List<CustomerEntity> customerList;
            
            // New requirement: every user sees only their own created customers
            if (currentUser != null) {
                customerList = database.customerDao().getCustomersByUser(currentUser.getUid());
            } else {
                customerList = new java.util.ArrayList<>();
            }
            
            runOnUiThread(() -> {
                customers.clear();
                customers.addAll(customerList);
                customerAdapter.notifyDataSetChanged();
            });
        }).start();
    }
    
    private void searchCustomers(String query) {
        if (TextUtils.isEmpty(query)) {
            loadCustomers();
            return;
        }
        
        new Thread(() -> {
            List<CustomerEntity> searchResults;
            
            // New requirement: every user searches only their own created customers
            if (currentUser != null) {
                searchResults = database.customerDao().searchCustomersByUser(currentUser.getUid(), query);
            } else {
                searchResults = new java.util.ArrayList<>();
            }
            
            runOnUiThread(() -> {
                customers.clear();
                customers.addAll(searchResults);
                customerAdapter.notifyDataSetChanged();
            });
        }).start();
    }
    
    private void startBarcodeScanning() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        
        isScanning = true;
        cameraPreviewCard.setVisibility(View.VISIBLE);
        previewView.setVisibility(View.VISIBLE);
        startCamera();
    }
    
    private void stopBarcodeScanning() {
        isScanning = false;
        cameraPreviewCard.setVisibility(View.GONE);
        previewView.setVisibility(View.GONE);
        try {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }
        } catch (Exception ignored) {}
        camera = null;
    }
    
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(this);
        
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::analyzeImage);
                
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                // Ensure no previous use cases remain bound
                try { cameraProvider.unbindAll(); } catch (Exception ignored) {}
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, 
                        preview, imageAnalysis);
                
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                Toast.makeText(this, getString(R.string.barcode_scan_failed), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }
    
    private void analyzeImage(ImageProxy image) {
        if (!isScanning) { image.close(); return; }
        if (image.getImage() == null) { image.close(); return; }
        
        InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());
        
        barcodeScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty() && isScanning) {
                        isScanning = false;
                        Barcode barcode = barcodes.get(0);
                        String rawValue = barcode.getRawValue();
                        
                        // Show raw data toast for testing and process
                        Toast.makeText(this, rawValue, Toast.LENGTH_LONG).show();
                        processBarcodeData(rawValue);
                        
                        // Stop camera
                        if (camera != null) {
                            camera.getCameraControl().setZoomRatio(1.0f);
                        }
                        stopBarcodeScanning();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Barcode scanning failed", e);
                })
                .addOnCompleteListener(result -> image.close());
    }
    
    private void processBarcodeData(String rawData) {
        // Parse PDF417 data (this is a simplified parser - real implementation would be more complex)
        try {
            Log.d(TAG, "RAW BARCODE DATA:\n" + rawData);
            // Print raw first for debugging
            Log.d(TAG, "RAW:" + rawData);

            // Support both DMV-like and key:value formats
            String[] lines = rawData.split("\n");
            CustomerEntity customer = new CustomerEntity();
            
            for (String line : lines) {
                if (line.contains("DCS")) { // Last name
                    customer.setFullName(line.substring(3).trim());
                } else if (line.contains("DCT")) { // First name
                    String firstName = line.substring(3).trim();
                    String fullName = customer.getFullName() + " " + firstName;
                    customer.setFullName(fullName);
                } else if (line.contains("DBB")) { // Date of birth
                    String dob = line.substring(3).trim();
                    customer.setDateOfBirth(formatDate(dob));
                } else if (line.contains("DAJ")) { // Issue date
                    String issueDate = line.substring(3).trim();
                    customer.setIssueDate(formatDate(issueDate));
                } else if (line.contains("DBA")) { // Expiry date
                    String expiryDate = line.substring(3).trim();
                    customer.setExpiryDate(formatDate(expiryDate));
                } else if (line.contains("DAG")) { // National ID
                    customer.setNationalIdNumber(line.substring(3).trim());
                } else if (line.contains(":")) { // key:value fallbacks
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim().toLowerCase();
                        String value = parts[1].trim();
                        switch (key) {
                            case "name":
                                customer.setFullName(value);
                                break;
                            case "dob":
                            case "dateofbirth":
                                customer.setDateOfBirth(formatDate(value.replace("-", "")));
                                break;
                            case "id":
                            case "nationalid":
                            case "national_id":
                                customer.setNationalIdNumber(value);
                                break;
                            case "issue":
                            case "issuedate":
                                customer.setIssueDate(formatDate(value.replace("-", "")));
                                break;
                            case "expiry":
                            case "expirydate":
                                customer.setExpiryDate(formatDate(value.replace("-", "")));
                                break;
                        }
                    }
                }
            }
            
            // Set metadata
            // Ensure ID exists; fallback to timestamp id
            if (customer.getNationalIdNumber() != null && !customer.getNationalIdNumber().isEmpty()) {
                customer.setId(customer.getNationalIdNumber());
            } else {
                customer.setId("customer_" + System.currentTimeMillis());
            }
            if (currentUser != null) {
                customer.setCreatedBy(currentUser.getUid());
            }
            
            // Show parsed preview
            String preview = "Name: " + (customer.getFullName() == null ? "" : customer.getFullName()) +
                    "\nDOB: " + (customer.getDateOfBirth() == null ? "" : customer.getDateOfBirth()) +
                    "\nID: " + (customer.getNationalIdNumber() == null ? "" : customer.getNationalIdNumber()) +
                    "\nIssue: " + (customer.getIssueDate() == null ? "" : customer.getIssueDate()) +
                    "\nExpiry: " + (customer.getExpiryDate() == null ? "" : customer.getExpiryDate());
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Scanned Data")
                    .setMessage(preview)
                    .setPositiveButton("Use Data", (d, w) -> showCustomerFormDialog(customer, true))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            Toast.makeText(this, getString(R.string.barcode_data_extracted), Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing barcode data", e);
            Toast.makeText(this, getString(R.string.barcode_scan_failed), Toast.LENGTH_SHORT).show();
        }
    }
    
    private String formatDate(String dateStr) {
        try {
            // Convert from various date formats to YYYY-MM-DD
            if (dateStr.length() == 8) { // YYYYMMDD
                return dateStr.substring(0, 4) + "-" + dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);
            } else if (dateStr.length() == 6) { // YYMMDD
                String year = "20" + dateStr.substring(0, 2);
                return year + "-" + dateStr.substring(2, 4) + "-" + dateStr.substring(4, 6);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting date: " + dateStr, e);
        }
        return dateStr;
    }
    
    private void showAddCustomerDialog() {
        showCustomerFormDialog(new CustomerEntity(), false);
    }
    
    private void showManualEntryDialog() {
        showCustomerFormDialog(new CustomerEntity(), false);
    }
    
    private void showCustomerFormDialog(CustomerEntity customer, boolean fromBarcode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_customer_form, null);
        builder.setView(dialogView);
        
        // Initialize form fields
        EditText etFullName = dialogView.findViewById(R.id.etFullName);
        EditText etDateOfBirth = dialogView.findViewById(R.id.etDateOfBirth);
        EditText etNationalId = dialogView.findViewById(R.id.etNationalId);
        EditText etIssueDate = dialogView.findViewById(R.id.etIssueDate);
        EditText etExpiryDate = dialogView.findViewById(R.id.etExpiryDate);
        EditText etPhoneNumber = dialogView.findViewById(R.id.etPhoneNumber);
        EditText etAddress = dialogView.findViewById(R.id.etAddress);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        
        // Pre-fill data if from barcode OR editing existing customer
        if (fromBarcode || customer.getId() != null) {
            etFullName.setText(customer.getFullName());
            etDateOfBirth.setText(customer.getDateOfBirth());
            etNationalId.setText(customer.getNationalIdNumber());
            etIssueDate.setText(customer.getIssueDate());
            etExpiryDate.setText(customer.getExpiryDate());
            etPhoneNumber.setText(customer.getPhoneNumber());
            etAddress.setText(customer.getAddress());
            etEmail.setText(customer.getEmail());
        }
        
        builder.setTitle(customer.getId() == null ? getString(R.string.add_customer) : getString(R.string.edit_customer))
                // Attach labels now; we will override positive button click after show to prevent auto-dismiss
                .setPositiveButton(getString(R.string.save_customer), null)
                .setNegativeButton(getString(android.R.string.cancel), null);
        
        if (customer.getId() != null) {
            builder.setNeutralButton(getString(R.string.delete_customer), (dialog, which) -> {
                deleteCustomer(customer);
            });
        }
        
        // Also print current values to help with testing
        builder.setOnDismissListener(d -> Log.d(TAG, "Customer form closed"));
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dlg -> {
            android.widget.Button btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setOnClickListener(v -> {
                if (validateCustomerForm(etFullName, etDateOfBirth, etNationalId, etIssueDate, etExpiryDate, etEmail)) {
                    saveCustomer(customer, etFullName, etDateOfBirth, etNationalId, etIssueDate, etExpiryDate, etPhoneNumber, etAddress, etEmail);
                    dialog.dismiss();
                } else {
                    Toast.makeText(this, getString(R.string.required_fields_missing), Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }
    
    private boolean validateCustomerForm(EditText etFullName, EditText etDateOfBirth, EditText etNationalId, 
                                       EditText etIssueDate, EditText etExpiryDate, EditText etEmail) {
        if (TextUtils.isEmpty(etFullName.getText())) {
            etFullName.setError(getString(R.string.required_fields_missing));
            return false;
        }
        if (TextUtils.isEmpty(etDateOfBirth.getText())) {
            etDateOfBirth.setError(getString(R.string.required_fields_missing));
            return false;
        }
        if (TextUtils.isEmpty(etNationalId.getText())) {
            etNationalId.setError(getString(R.string.required_fields_missing));
            return false;
        }
        
        // Validate date formats
        if (!isValidDateFormat(etDateOfBirth.getText().toString())) {
            etDateOfBirth.setError(getString(R.string.invalid_date_format));
            return false;
        }
        if (!TextUtils.isEmpty(etIssueDate.getText()) && !isValidDateFormat(etIssueDate.getText().toString())) {
            etIssueDate.setError(getString(R.string.invalid_date_format));
            return false;
        }
        if (!TextUtils.isEmpty(etExpiryDate.getText()) && !isValidDateFormat(etExpiryDate.getText().toString())) {
            etExpiryDate.setError(getString(R.string.invalid_date_format));
            return false;
        }

        // Optional email validation when provided
        String emailStr = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(emailStr)) {
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailStr).matches()) {
                etEmail.setError(getString(R.string.invalid_email));
                return false;
            }
        }
        
        return true;
    }
    
    private boolean isValidDateFormat(String date) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setLenient(false);
            sdf.parse(date);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void saveCustomer(CustomerEntity customer, EditText etFullName, EditText etDateOfBirth, 
                            EditText etNationalId, EditText etIssueDate, EditText etExpiryDate, 
                            EditText etPhoneNumber, EditText etAddress, EditText etEmail) {
        new Thread(() -> {
            try {
                // Check if customer with same National ID already exists (for new customers)
                if (customer.getId() == null) {
                    CustomerEntity existingCustomer = database.customerDao().getCustomerByNationalId(etNationalId.getText().toString());
                    if (existingCustomer != null) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, getString(R.string.customer_already_exists), Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                }
                
                // Set customer data
                if (customer.getId() == null) {
                    customer.setId(etNationalId.getText().toString());
                    if (currentUser != null) {
                        customer.setCreatedBy(currentUser.getUid());
                    }
                    customer.setCreatedAt(System.currentTimeMillis());
                }
                
                customer.setFullName(etFullName.getText().toString());
                customer.setDateOfBirth(etDateOfBirth.getText().toString());
                customer.setNationalIdNumber(etNationalId.getText().toString());
                customer.setIssueDate(etIssueDate.getText().toString());
                customer.setExpiryDate(etExpiryDate.getText().toString());
                customer.setPhoneNumber(etPhoneNumber.getText().toString());
                customer.setAddress(etAddress.getText().toString());
                customer.setEmail(etEmail.getText().toString());
                customer.setUpdatedAt(System.currentTimeMillis());
                customer.setNeedsSync(true);
                customer.setActive(true);
                
                // Save to database
                database.customerDao().insertCustomer(customer);
                int afterCount = (currentUser != null) ? database.customerDao().getCustomerCountByUser(currentUser.getUid()) : 0;
                
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.customer_saved) + " (#=" + afterCount + ")", Toast.LENGTH_SHORT).show();
                    loadCustomers();
                    
                    // Trigger sync after saving
                    syncManager.syncCustomers();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving customer", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error saving customer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void deleteCustomer(CustomerEntity customer) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_customer))
                .setMessage("Are you sure you want to delete this customer?")
                .setPositiveButton(getString(android.R.string.yes), (dialog, which) -> {
                    new Thread(() -> {
                        database.customerDao().softDeleteCustomer(customer.getId(), System.currentTimeMillis());
                        runOnUiThread(() -> {
                            Toast.makeText(this, getString(R.string.customer_deleted), Toast.LENGTH_SHORT).show();
                            loadCustomers();
                            // Trigger sync after deletion
                            syncManager.syncCustomers();
                        });
                    }).start();
                })
                .setNegativeButton(getString(android.R.string.no), null)
                .show();
    }
    
    private void onCustomerClick(CustomerEntity customer) {
        // Show customer details
        showCustomerDetailsDialog(customer);
    }
    
    private void onCustomerEdit(CustomerEntity customer) {
        showCustomerFormDialog(customer, false);
    }
    
    private void onCustomerDelete(CustomerEntity customer) {
        deleteCustomer(customer);
    }
    
    private void showCustomerDetailsDialog(CustomerEntity customer) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.customer_details))
                .setMessage("Name: " + customer.getFullName() + "\n" +
                           "National ID: " + customer.getNationalIdNumber() + "\n" +
                           "Date of Birth: " + customer.getDateOfBirth() + "\n" +
                           "Phone: " + customer.getPhoneNumber() + "\n" +
                           "Email: " + customer.getEmail())
                .setPositiveButton(getString(android.R.string.ok), null)
                .show();
    }
    
    private void showUserDetailsDialog() {
        if (currentUser == null) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.authenticated_as))
                .setMessage("Email: " + currentUser.getEmail() + "\n" +
                           "Role: " + currentUser.getRole() + "\n" +
                           "Status: " + (currentUser.isActive() ? getString(R.string.active) : getString(R.string.inactive)) + "\n" +
                           "Created: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(currentUser.getCreatedAt())))
                .setPositiveButton(getString(android.R.string.ok), null)
                .show();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBarcodeScanning();
            } else {
                Toast.makeText(this, "Camera permission is required for barcode scanning", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
    }
}
