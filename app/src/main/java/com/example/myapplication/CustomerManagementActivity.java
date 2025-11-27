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
import android.widget.Spinner;
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
import android.util.Size;
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
    private TextView tvHeaderTitle;
    private View btnScanBarcode, btnManualEntry;
    private Button btnStopScanning;
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
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.customer_management));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
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
            // tvWelcome and tvUserInfo are removed from this screen's new design
            // tvWelcome.setText(getString(R.string.customer_management));
            // tvUserInfo.setText(currentUser.getEmail() + " (" + currentUser.getRole() + ")");
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
        // Focus on PDF417 for ID cards, but enable all formats as fallback
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_PDF417,  // Primary format for ID cards
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_DATA_MATRIX,
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_AZTEC
                )
                // Don't enable all potential barcodes - it might cause false positives
                // .enableAllPotentialBarcodes()
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        Log.d(TAG, "Barcode scanner initialized with PDF417 as primary format");
        
        // Removed sync prompt from Customer Management - only show on dashboards
        
        // Only download customers if online (no auto-sync)
        if (syncManager.isOnline()) {
            syncManager.downloadCustomers(this::loadCustomers);
        }
    }
    
    private void setupClickListeners() {
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
                
                // Configure ImageAnalysis with higher resolution for better PDF417 detection
                // Use square resolution to match camera's native format (2176x2176)
                // PDF417 barcodes work better with square/higher resolution images
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1920, 1920)) // Square resolution for better PDF417 detection
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build();
                
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::analyzeImage);
                
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                // Ensure no previous use cases remain bound
                try { cameraProvider.unbindAll(); } catch (Exception ignored) {}
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, 
                        preview, imageAnalysis);
                
                // Try without zoom first - zoom might be causing focus issues
                // If barcode is not detected, user can manually adjust distance
                // Uncomment below if zoom is needed:
                /*
                previewView.postDelayed(() -> {
                    if (camera != null) {
                        try {
                            float targetZoom = 1.2f; // Reduced zoom
                            camera.getCameraControl().setZoomRatio(targetZoom);
                            Log.d(TAG, "Zoom set to: " + targetZoom + " for better PDF417 detection");
                        } catch (Exception e) {
                            Log.w(TAG, "Could not set zoom ratio", e);
                        }
                    }
                }, 500);
                */
                Log.d(TAG, "Camera initialized without zoom - adjust phone distance manually for best focus");
                
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                Toast.makeText(this, getString(R.string.barcode_scan_failed), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }
    
    private void analyzeImage(ImageProxy image) {
        if (!isScanning) { image.close(); return; }
        if (image.getImage() == null) { image.close(); return; }
        
        // Get rotation and image dimensions for better debugging
        int rotationDegrees = image.getImageInfo().getRotationDegrees();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        
        Log.d(TAG, "Analyzing image: " + imageWidth + "x" + imageHeight + 
              ", rotation: " + rotationDegrees + "°");
        
        InputImage inputImage = InputImage.fromMediaImage(image.getImage(), rotationDegrees);
        
        barcodeScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty() && isScanning) {
                        isScanning = false;
                        Barcode barcode = barcodes.get(0);
                        
                        // Comprehensive logging for debugging PDF417 issues
                        Log.d(TAG, "========== BARCODE DETECTED ==========");
                        Log.d(TAG, "Format: " + barcode.getFormat());
                        Log.d(TAG, "Format Name: " + getBarcodeFormatName(barcode.getFormat()));
                        
                        String rawValue = barcode.getRawValue();
                        String displayValue = barcode.getDisplayValue();
                        byte[] rawBytes = barcode.getRawBytes();
                        
                        Log.d(TAG, "RawValue: " + (rawValue != null ? rawValue : "NULL"));
                        Log.d(TAG, "RawValue Length: " + (rawValue != null ? rawValue.length() : 0));
                        Log.d(TAG, "DisplayValue: " + (displayValue != null ? displayValue : "NULL"));
                        Log.d(TAG, "DisplayValue Length: " + (displayValue != null ? displayValue.length() : 0));
                        Log.d(TAG, "RawBytes: " + (rawBytes != null ? rawBytes.length + " bytes" : "NULL"));
                        
                        // Log bounding box for debugging
                        if (barcode.getBoundingBox() != null) {
                            Log.d(TAG, "BoundingBox: " + barcode.getBoundingBox().toString());
                        }
                        
                        // Try multiple methods to extract data
                        String extractedData = null;
                        
                        // Method 1: Try rawValue first
                        if (rawValue != null && !rawValue.trim().isEmpty()) {
                            extractedData = rawValue;
                            Log.d(TAG, "Using rawValue for extraction");
                        }
                        // Method 2: Try displayValue
                        else if (displayValue != null && !displayValue.trim().isEmpty()) {
                            extractedData = displayValue;
                            Log.d(TAG, "Using displayValue for extraction");
                        }
                        // Method 3: Try to convert rawBytes to string with multiple encodings
                        else if (rawBytes != null && rawBytes.length > 0) {
                            String[] encodings = {"UTF-8", "ISO-8859-1", "Windows-1252", "US-ASCII"};
                            for (String encoding : encodings) {
                                try {
                                    String testData = new String(rawBytes, encoding);
                                    // Check if this encoding produces readable text (not too many garbled chars)
                                    long readableChars = testData.chars().filter(c -> 
                                        (c >= 32 && c <= 126) || c == 10 || c == 13).count();
                                    if (readableChars > testData.length() * 0.7) { // At least 70% readable
                                        extractedData = testData;
                                        Log.d(TAG, "Using rawBytes converted with encoding: " + encoding);
                                        break;
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Failed to convert rawBytes with " + encoding, e);
                                }
                            }
                            // If no good encoding found, use UTF-8 as fallback
                            if (extractedData == null) {
                                try {
                                    extractedData = new String(rawBytes, "UTF-8");
                                    Log.d(TAG, "Using UTF-8 as fallback encoding");
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to convert rawBytes with any encoding", e);
                                }
                            }
                        }
                        
                        // Process the extracted data
                        if (extractedData != null && !extractedData.trim().isEmpty()) {
                            Log.d(TAG, "Successfully extracted data: " + extractedData.substring(0, Math.min(100, extractedData.length())));
                            // Remove debug Toast - data will be shown in dialog
                            // Toast.makeText(this, "Data extracted: " + extractedData.substring(0, Math.min(50, extractedData.length())) + "...", Toast.LENGTH_LONG).show();
                            processBarcodeData(extractedData);
                        } else {
                            // No data could be extracted
                            Log.e(TAG, "========== FAILED TO EXTRACT DATA ==========");
                            Log.e(TAG, "Barcode was detected but no readable data found!");
                            Log.e(TAG, "This might be due to:");
                            Log.e(TAG, "1. Encrypted/encoded PDF417 barcode");
                            Log.e(TAG, "2. Poor image quality");
                            Log.e(TAG, "3. ML Kit limitation with this specific barcode format");
                            
                            String errorMsg = getString(R.string.barcode_no_data) + "\nFormat: " + getBarcodeFormatName(barcode.getFormat());
                            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                            
                            // Don't stop scanning - let user try again
                            isScanning = true;
                            return;
                        }
                        
                        // Stop camera
                        if (camera != null) {
                            try {
                                camera.getCameraControl().setZoomRatio(1.0f);
                            } catch (Exception e) {
                                Log.w(TAG, "Error resetting zoom", e);
                            }
                        }
                        stopBarcodeScanning();
                    } else if (isScanning) {
                        // Log when scanning but no barcode found (for debugging)
                        Log.v(TAG, "Scanning... No barcode detected yet. Image: " + 
                              imageWidth + "x" + imageHeight);
                    }
                })
                .addOnFailureListener(e -> {
                    // Enhanced error logging for debugging PDF417 scanning issues
                    Log.e(TAG, "Barcode scanning failed", e);
                    Log.e(TAG, "Error details - Image size: " + imageWidth + "x" + imageHeight + 
                          ", Rotation: " + rotationDegrees + "°, Error: " + e.getMessage());
                    if (e.getCause() != null) {
                        Log.e(TAG, "Error cause: " + e.getCause().getMessage());
                    }
                })
                .addOnCompleteListener(result -> image.close());
    }
    
    private void processBarcodeData(String rawData) {
        // Parse PDF417 data (this is a simplified parser - real implementation would be more complex)
        try {
            Log.d(TAG, "RAW BARCODE DATA:\n" + rawData);
            // Print raw first for debugging
            Log.d(TAG, "RAW:" + rawData);

            // Clean the data - remove or replace garbled characters
            String cleanedData = rawData.replaceAll("[\\uFFFD\\u0000-\\u001F]", " "); // Replace replacement chars and control chars
            cleanedData = cleanedData.replaceAll("\\s+", " "); // Normalize whitespace
            
            // Support both DMV-like and key:value formats
            String[] lines = cleanedData.split("[\n\r]+");
            CustomerEntity customer = new CustomerEntity();
            
            // Method 1: Try DMV/AAMVA format (DCS, DCT, DBB, etc.)
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (line.contains("DCS")) { // Last name
                    customer.setFullName(line.substring(3).trim());
                } else if (line.contains("DCT")) { // First name
                    String firstName = line.substring(3).trim();
                    String fullName = (customer.getFullName() != null ? customer.getFullName() + " " : "") + firstName;
                    customer.setFullName(fullName);
                } else if (line.contains("DBB")) { // Date of birth
                    String dob = line.substring(3).trim();
                    customer.setDateOfBirth(formatDate(dob));
                } else if (line.contains("DAJ")) { // Issue date
                    String issueDate = line.substring(3).trim();
                    customer.setIssueDate(formatDateYYYYMMDD(issueDate));
                } else if (line.contains("DBA")) { // Expiry date
                    String expiryDate = line.substring(3).trim();
                    customer.setExpiryDate(formatDateYYYYMMDD(expiryDate));
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
                                customer.setIssueDate(formatDateYYYYMMDD(value.replace("-", "")));
                                break;
                            case "expiry":
                            case "expirydate":
                                customer.setExpiryDate(formatDateYYYYMMDD(value.replace("-", "")));
                                break;
                        }
                    }
                }
            }
            
            // Method 2: Pattern-based extraction (for unstructured data like ID cards)
            // Extract ID - look for "ID" followed by numbers/letters
            if (customer.getNationalIdNumber() == null || customer.getNationalIdNumber().isEmpty()) {
                java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("ID\\s*([A-Z0-9]{10,})", java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher idMatcher = idPattern.matcher(cleanedData);
                if (idMatcher.find()) {
                    customer.setNationalIdNumber(idMatcher.group(1));
                    Log.d(TAG, "Extracted ID using pattern: " + idMatcher.group(1));
                }
            }
            
            // Extract names - look for sequences of capital letters (likely names like "FETIEMA KASSOUM")
            if (customer.getFullName() == null || customer.getFullName().isEmpty()) {
                java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile("([A-Z]{3,}(?:\\s+[A-Z]{3,}){1,3})");
                java.util.regex.Matcher nameMatcher = namePattern.matcher(cleanedData);
                java.util.ArrayList<String> names = new java.util.ArrayList<>();
                while (nameMatcher.find()) {
                    String name = nameMatcher.group(1);
                    // Filter out common non-name patterns (country codes, etc.)
                    if (!name.matches("(BFA|SXM|HT|FO|AN|ID|DOB|EXP|ISS|DFN).*")) {
                        names.add(name);
                    }
                }
                if (!names.isEmpty()) {
                    // Use the longest name as it's likely the full name
                    String fullName = names.stream()
                            .max((a, b) -> Integer.compare(a.length(), b.length()))
                            .orElse("");
                    if (fullName.length() > 5) { // Minimum reasonable name length
                        customer.setFullName(fullName);
                        Log.d(TAG, "Extracted name using pattern: " + fullName);
                    }
                }
            }
            
            // Extract dates - look for date patterns (YYYYMMDD, YYMMDD, etc.) with validation
            if (customer.getDateOfBirth() == null || customer.getDateOfBirth().isEmpty()) {
                java.util.ArrayList<String> validDates = new java.util.ArrayList<>();
                
                // Try 6-digit dates first (YYMMDD) - more common in ID cards
                java.util.regex.Pattern datePattern6 = java.util.regex.Pattern.compile("(\\d{6})");
                java.util.regex.Matcher dateMatcher6 = datePattern6.matcher(cleanedData);
                
                while (dateMatcher6.find()) {
                    String dateStr = dateMatcher6.group(1);
                    try {
                        int year = Integer.parseInt(dateStr.substring(0, 2));
                        int month = Integer.parseInt(dateStr.substring(2, 4));
                        int day = Integer.parseInt(dateStr.substring(4, 6));
                        
                        // Validate: month (1-12) and day (1-31)
                        if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                            // Prefer years 20-99 (1920-1999) or 00-30 (2000-2030)
                            if ((year >= 20 && year <= 99) || (year >= 0 && year <= 30)) {
                                validDates.add(dateStr);
                                Log.d(TAG, "Found valid 6-digit date: " + dateStr);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error validating 6-digit date: " + dateStr, e);
                    }
                }
                
                // Try 8-digit dates (YYYYMMDD) with validation
                java.util.regex.Pattern datePattern8 = java.util.regex.Pattern.compile("(\\d{8})");
                java.util.regex.Matcher dateMatcher8 = datePattern8.matcher(cleanedData);
                
                while (dateMatcher8.find()) {
                    String dateStr = dateMatcher8.group(1);
                    try {
                        int year = Integer.parseInt(dateStr.substring(0, 4));
                        int month = Integer.parseInt(dateStr.substring(4, 6));
                        int day = Integer.parseInt(dateStr.substring(6, 8));
                        
                        // Validate: year should be reasonable (1900-2100)
                        if (year >= 1900 && year <= 2100 && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                            validDates.add(dateStr);
                            Log.d(TAG, "Found valid 8-digit date: " + dateStr);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error validating 8-digit date: " + dateStr, e);
                    }
                }
                
                // Use the first valid date found (usually DOB appears first in ID cards)
                if (!validDates.isEmpty()) {
                    String dateStr = validDates.get(0);
                    customer.setDateOfBirth(formatDate(dateStr));
                    Log.d(TAG, "Extracted DOB using pattern: " + dateStr + " -> " + customer.getDateOfBirth());
                } else {
                    Log.d(TAG, "No valid dates found in barcode data");
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
            
            // Check if any data was parsed
            boolean hasParsedData = (customer.getFullName() != null && !customer.getFullName().isEmpty()) ||
                                   (customer.getNationalIdNumber() != null && !customer.getNationalIdNumber().isEmpty()) ||
                                   (customer.getDateOfBirth() != null && !customer.getDateOfBirth().isEmpty());
            
            // Show parsed preview
            String preview = "Name: " + (customer.getFullName() == null || customer.getFullName().isEmpty() ? "" : customer.getFullName()) +
                    "\nDOB: " + (customer.getDateOfBirth() == null || customer.getDateOfBirth().isEmpty() ? "" : customer.getDateOfBirth()) +
                    "\nID: " + (customer.getNationalIdNumber() == null || customer.getNationalIdNumber().isEmpty() ? "" : customer.getNationalIdNumber()) +
                    "\nIssue: " + (customer.getIssueDate() == null || customer.getIssueDate().isEmpty() ? "" : customer.getIssueDate()) +
                    "\nExpiry: " + (customer.getExpiryDate() == null || customer.getExpiryDate().isEmpty() ? "" : customer.getExpiryDate());
            
            // If no data was parsed, show raw data for debugging
            if (!hasParsedData) {
                preview += "\n\n--- Raw Barcode Data (Not Parsed) ---\n" + 
                          rawData.substring(0, Math.min(500, rawData.length()));
                Log.w(TAG, "No data could be parsed from barcode. Raw data: " + rawData);
            }
            
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Scanned Data")
                    .setMessage(preview)
                    .setPositiveButton("Use Data", (d, w) -> showCustomerFormDialog(customer, true))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            
            if (hasParsedData) {
                Toast.makeText(this, getString(R.string.barcode_data_extracted), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Data extracted but format not recognized. Check dialog for raw data.", Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing barcode data", e);
            Toast.makeText(this, getString(R.string.barcode_scan_failed), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Setup automatic date formatting for DD-MM-YYYY format with dashes
     */
    private void setupDateOfBirthFormatting(EditText etDateOfBirth) {
        etDateOfBirth.addTextChangedListener(new android.text.TextWatcher() {
            private boolean isFormatting = false;
            
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isFormatting) return;
                isFormatting = true;
                
                String input = s.toString().replaceAll("[^\\d]", ""); // Remove non-digits
                StringBuilder formatted = new StringBuilder();
                
                // Format as DD-MM-YYYY
                for (int i = 0; i < input.length() && i < 8; i++) {
                    if (i == 2 || i == 4) {
                        formatted.append("-");
                    }
                    formatted.append(input.charAt(i));
                }
                
                // Update cursor position
                int cursorPos = formatted.length();
                if (cursorPos > 10) cursorPos = 10; // Max length DD-MM-YYYY
                
                etDateOfBirth.setText(formatted.toString());
                etDateOfBirth.setSelection(Math.min(cursorPos, formatted.length()));
                
                isFormatting = false;
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }
    
    /**
     * Convert date from various formats to DD-MM-YYYY format for display
     */
    private String convertDateToDDMMYYYY(String date) {
        if (date == null || date.trim().isEmpty()) return "";
        
        try {
            String cleanDate = date.trim();
            
            // If already in DD-MM-YYYY format
            if (cleanDate.matches("\\d{2}-\\d{2}-\\d{4}")) {
                return cleanDate;
            }
            
            // If in YYYY-MM-DD format
            if (cleanDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                String[] parts = cleanDate.split("-");
                return parts[2] + "-" + parts[1] + "-" + parts[0]; // DD-MM-YYYY
            }
            
            // If in YYYYMMDD format
            if (cleanDate.matches("\\d{8}")) {
                return cleanDate.substring(6, 8) + "-" + cleanDate.substring(4, 6) + "-" + cleanDate.substring(0, 4);
            }
            
            // If in YYMMDD format
            if (cleanDate.matches("\\d{6}")) {
                int year = Integer.parseInt(cleanDate.substring(0, 2));
                String fullYear = (year >= 0 && year <= 30) ? "20" + String.format("%02d", year) : "19" + String.format("%02d", year);
                return cleanDate.substring(4, 6) + "-" + cleanDate.substring(2, 4) + "-" + fullYear;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting date to DD-MM-YYYY: " + date, e);
        }
        
        return date; // Return as-is if conversion fails
    }
    
    private String formatDate(String dateStr) {
        try {
            // Convert from various date formats to DD-MM-YYYY with validation (for Date of Birth)
            if (dateStr.length() == 8) { // YYYYMMDD
                int year = Integer.parseInt(dateStr.substring(0, 4));
                int month = Integer.parseInt(dateStr.substring(4, 6));
                int day = Integer.parseInt(dateStr.substring(6, 8));
                
                // Validate: year (1900-2100), month (1-12), day (1-31)
                if (year >= 1900 && year <= 2100 && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    // Return DD-MM-YYYY format
                    return String.format("%02d", day) + "-" + String.format("%02d", month) + "-" + year;
                } else {
                    Log.w(TAG, "Invalid 8-digit date: " + dateStr + " (year=" + year + ", month=" + month + ", day=" + day + ")");
                    return ""; // Return empty string for invalid dates
                }
            } else if (dateStr.length() == 6) { // YYMMDD
                int year = Integer.parseInt(dateStr.substring(0, 2));
                int month = Integer.parseInt(dateStr.substring(2, 4));
                int day = Integer.parseInt(dateStr.substring(4, 6));
                
                // Validate: month (1-12), day (1-31)
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    // Prefer years 20-99 (1920-1999) or 00-30 (2000-2030)
                    if ((year >= 20 && year <= 99) || (year >= 0 && year <= 30)) {
                        String fullYear = (year >= 0 && year <= 30) ? "20" + String.format("%02d", year) : "19" + String.format("%02d", year);
                        // Return DD-MM-YYYY format
                        return String.format("%02d", day) + "-" + String.format("%02d", month) + "-" + fullYear;
                    } else {
                        Log.w(TAG, "Invalid 6-digit date year: " + dateStr + " (year=" + year + ")");
                        return ""; // Return empty string for invalid dates
                    }
                } else {
                    Log.w(TAG, "Invalid 6-digit date: " + dateStr + " (month=" + month + ", day=" + day + ")");
                    return ""; // Return empty string for invalid dates
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting date: " + dateStr, e);
        }
        return ""; // Return empty string if date format is not recognized or invalid
    }
    
    private String formatDateYYYYMMDD(String dateStr) {
        try {
            // Convert from various date formats to YYYY-MM-DD with validation (for Issue/Expiry dates)
            if (dateStr.length() == 8) { // YYYYMMDD
                int year = Integer.parseInt(dateStr.substring(0, 4));
                int month = Integer.parseInt(dateStr.substring(4, 6));
                int day = Integer.parseInt(dateStr.substring(6, 8));
                
                // Validate: year (1900-2100), month (1-12), day (1-31)
                if (year >= 1900 && year <= 2100 && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    return dateStr.substring(0, 4) + "-" + dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);
                } else {
                    Log.w(TAG, "Invalid 8-digit date: " + dateStr + " (year=" + year + ", month=" + month + ", day=" + day + ")");
                    return ""; // Return empty string for invalid dates
                }
            } else if (dateStr.length() == 6) { // YYMMDD
                int year = Integer.parseInt(dateStr.substring(0, 2));
                int month = Integer.parseInt(dateStr.substring(2, 4));
                int day = Integer.parseInt(dateStr.substring(4, 6));
                
                // Validate: month (1-12), day (1-31)
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    // Prefer years 20-99 (1920-1999) or 00-30 (2000-2030)
                    if ((year >= 20 && year <= 99) || (year >= 0 && year <= 30)) {
                        String fullYear = (year >= 0 && year <= 30) ? "20" + String.format("%02d", year) : "19" + String.format("%02d", year);
                        return fullYear + "-" + dateStr.substring(2, 4) + "-" + dateStr.substring(4, 6);
                    } else {
                        Log.w(TAG, "Invalid 6-digit date year: " + dateStr + " (year=" + year + ")");
                        return ""; // Return empty string for invalid dates
                    }
                } else {
                    Log.w(TAG, "Invalid 6-digit date: " + dateStr + " (month=" + month + ", day=" + day + ")");
                    return ""; // Return empty string for invalid dates
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting date: " + dateStr, e);
        }
        return ""; // Return empty string if date format is not recognized or invalid
    }
    
    private String getBarcodeFormatName(int format) {
        switch (format) {
            case Barcode.FORMAT_PDF417:
                return "PDF417";
            case Barcode.FORMAT_CODE_128:
                return "CODE_128";
            case Barcode.FORMAT_CODE_39:
                return "CODE_39";
            case Barcode.FORMAT_CODE_93:
                return "CODE_93";
            case Barcode.FORMAT_CODABAR:
                return "CODABAR";
            case Barcode.FORMAT_DATA_MATRIX:
                return "DATA_MATRIX";
            case Barcode.FORMAT_EAN_13:
                return "EAN_13";
            case Barcode.FORMAT_EAN_8:
                return "EAN_8";
            case Barcode.FORMAT_ITF:
                return "ITF";
            case Barcode.FORMAT_QR_CODE:
                return "QR_CODE";
            case Barcode.FORMAT_UPC_A:
                return "UPC_A";
            case Barcode.FORMAT_UPC_E:
                return "UPC_E";
            case Barcode.FORMAT_AZTEC:
                return "AZTEC";
            default:
                return "UNKNOWN (" + format + ")";
        }
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
        Spinner spinnerDocumentType = dialogView.findViewById(R.id.spinnerDocumentType);
        EditText etDateOfBirth = dialogView.findViewById(R.id.etDateOfBirth);
        EditText etNationalId = dialogView.findViewById(R.id.etNationalId);
        EditText etIssueDate = dialogView.findViewById(R.id.etIssueDate);
        EditText etExpiryDate = dialogView.findViewById(R.id.etExpiryDate);
        EditText etPhoneNumber = dialogView.findViewById(R.id.etPhoneNumber);
        EditText etAddress = dialogView.findViewById(R.id.etAddress);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        
        // Setup document type spinner
        String[] documentTypes = getResources().getStringArray(R.array.document_types);
        android.widget.ArrayAdapter<String> documentTypeAdapter = new android.widget.ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, documentTypes);
        documentTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDocumentType.setAdapter(documentTypeAdapter);
        
        // Set spinner text colors to match theme (black for selected, primary orange for dropdown)
        spinnerDocumentType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (view != null && view instanceof TextView) {
                    ((TextView) view).setTextColor(getResources().getColor(R.color.black, null));
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        
        // Override adapter getView and getDropDownView for spinner styling
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
        
        // Setup date of birth auto-formatting (DD-MM-YYYY with automatic dashes)
        setupDateOfBirthFormatting(etDateOfBirth);
        
        // Pre-fill data if from barcode OR editing existing customer
        if (fromBarcode || customer.getId() != null) {
            etFullName.setText(customer.getFullName());
            // Convert date from stored format (could be YYYY-MM-DD or DD-MM-YYYY) to DD-MM-YYYY for display
            String dobDisplay = convertDateToDDMMYYYY(customer.getDateOfBirth());
            etDateOfBirth.setText(dobDisplay);
            
            // Set document type spinner
            String docType = customer.getDocumentType();
            if (docType != null && !docType.isEmpty()) {
                for (int i = 0; i < documentTypes.length; i++) {
                    if (documentTypes[i].equalsIgnoreCase(docType)) {
                        spinnerDocumentType.setSelection(i);
                        break;
                    }
                }
            }
            
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
                    String selectedDocType = spinnerDocumentType.getSelectedItemPosition() >= 0 ? 
                            documentTypes[spinnerDocumentType.getSelectedItemPosition()] : null;
                    saveCustomer(customer, etFullName, etDateOfBirth, selectedDocType, etNationalId, etIssueDate, etExpiryDate, etPhoneNumber, etAddress, etEmail);
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
        
        // Validate date formats - Date of Birth uses DD-MM-YYYY, Issue/Expiry use YYYY-MM-DD
        if (!isValidDateFormat(etDateOfBirth.getText().toString())) {
            etDateOfBirth.setError(getString(R.string.invalid_date_format));
            return false;
        }
        if (!TextUtils.isEmpty(etIssueDate.getText()) && !isValidDateFormatYYYYMMDD(etIssueDate.getText().toString())) {
            etIssueDate.setError(getString(R.string.invalid_date_format));
            return false;
        }
        if (!TextUtils.isEmpty(etExpiryDate.getText()) && !isValidDateFormatYYYYMMDD(etExpiryDate.getText().toString())) {
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
        if (date == null || date.trim().isEmpty()) return false;
        try {
            // Check DD-MM-YYYY format for Date of Birth
            if (date.matches("\\d{2}-\\d{2}-\\d{4}")) {
                String[] parts = date.split("-");
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);
                
                // Validate ranges
                if (year >= 1900 && year <= 2100 && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    // Additional validation using Calendar
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                    sdf.setLenient(false);
                    sdf.parse(date);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isValidDateFormatYYYYMMDD(String date) {
        if (date == null || date.trim().isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setLenient(false);
            sdf.parse(date);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Normalizes date format to YYYY-MM-DD with zero-padding
     * Handles formats like: 2003-9-9, 2003-09-9, 2003-9-09, 2003-09-09
     */
    private String normalizeDateFormat(String date) {
        if (date == null || date.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Remove any extra spaces
            String cleanDate = date.trim();
            
            // Handle different date formats
            if (cleanDate.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                // Format: YYYY-M-D, YYYY-MM-D, YYYY-M-DD, YYYY-MM-DD
                String[] parts = cleanDate.split("-");
                if (parts.length == 3) {
                    String year = parts[0];
                    String month = String.format("%02d", Integer.parseInt(parts[1]));
                    String day = String.format("%02d", Integer.parseInt(parts[2]));
                    return year + "-" + month + "-" + day;
                }
            } else if (cleanDate.matches("\\d{8}")) {
                // Format: YYYYMMDD
                return cleanDate.substring(0, 4) + "-" + 
                       cleanDate.substring(4, 6) + "-" + 
                       cleanDate.substring(6, 8);
            } else if (cleanDate.matches("\\d{6}")) {
                // Format: YYMMDD (assume 20xx)
                String year = "20" + cleanDate.substring(0, 2);
                String month = cleanDate.substring(2, 4);
                String day = cleanDate.substring(4, 6);
                return year + "-" + month + "-" + day;
            }
            
            // If none of the patterns match, try to parse as-is
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setLenient(true);
            java.util.Date parsedDate = sdf.parse(cleanDate);
            
            // If parsing succeeded, format it properly
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            return outputFormat.format(parsedDate);
            
        } catch (Exception e) {
            Log.w(TAG, "Could not normalize date format: " + date, e);
            return null;
        }
    }
    
    private void saveCustomer(CustomerEntity customer, EditText etFullName, EditText etDateOfBirth, 
                            String documentType, EditText etNationalId, EditText etIssueDate, EditText etExpiryDate, 
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
                customer.setDocumentType(documentType);
                
                // Format and validate date of birth (stored as DD-MM-YYYY)
                String dobText = etDateOfBirth.getText().toString().trim();
                if (!dobText.isEmpty() && isValidDateFormat(dobText)) {
                    customer.setDateOfBirth(dobText); // Store as DD-MM-YYYY
                    Log.d(TAG, "Date of birth saved: " + dobText);
                } else {
                    customer.setDateOfBirth(null);
                    Log.w(TAG, "Invalid date of birth format: " + dobText);
                }
                
                customer.setNationalIdNumber(etNationalId.getText().toString());
                
                // Format and validate issue date
                String issueText = etIssueDate.getText().toString().trim();
                if (!issueText.isEmpty()) {
                    String normalizedIssue = normalizeDateFormat(issueText);
                    if (normalizedIssue != null && isValidDateFormatYYYYMMDD(normalizedIssue)) {
                        customer.setIssueDate(normalizedIssue);
                        Log.d(TAG, "Issue date normalized: " + issueText + " -> " + normalizedIssue);
                    } else {
                        customer.setIssueDate(null);
                        Log.w(TAG, "Invalid issue date format: " + issueText);
                    }
                } else {
                    customer.setIssueDate(null);
                }
                
                // Format and validate expiry date
                String expiryText = etExpiryDate.getText().toString().trim();
                if (!expiryText.isEmpty()) {
                    String normalizedExpiry = normalizeDateFormat(expiryText);
                    if (normalizedExpiry != null && isValidDateFormatYYYYMMDD(normalizedExpiry)) {
                        customer.setExpiryDate(normalizedExpiry);
                        Log.d(TAG, "Expiry date normalized: " + expiryText + " -> " + normalizedExpiry);
                    } else {
                        customer.setExpiryDate(null);
                        Log.w(TAG, "Invalid expiry date format: " + expiryText);
                    }
                } else {
                    customer.setExpiryDate(null);
                }
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
                    
                    // Mark for sync prompt (no auto-sync)
                    // syncManager.syncCustomers(); // Removed auto-sync
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
                            // Mark for sync prompt (no auto-sync)
                            // syncManager.syncCustomers(); // Removed auto-sync
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
