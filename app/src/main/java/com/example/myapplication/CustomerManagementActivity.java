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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.example.myapplication.utils.MRZParser;

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
    
    // Camera and MRZ Scanning
    private PreviewView previewView;
    private TextRecognizer textRecognizer;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private boolean isScanning = false;
    
    // Frame buffering for better MRZ detection
    private java.util.List<String> mrzFrameBuffer = new java.util.ArrayList<>();
    private static final int FRAME_BUFFER_SIZE = 5; // Process every 5th frame to reduce load
    private static final int MIN_CONSISTENT_DETECTIONS = 2; // Require MRZ detected in 2+ frames
    private int frameCounter = 0;
    private long lastProcessTime = 0;
    private static final long MIN_PROCESS_INTERVAL_MS = 500; // Process at most every 500ms
    private static final long MIN_SCANNING_DURATION_MS = 5000; // Wait at least 5 seconds before processing
    private long scanningStartTime = 0; // Track when scanning started
    
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
        
        // Initialize MRZ text recognizer (offline)
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Log.d(TAG, "MRZ text recognizer initialized");
        
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
        
        // Reset frame buffer and counters
        mrzFrameBuffer.clear();
        frameCounter = 0;
        lastProcessTime = 0;
        scanningStartTime = System.currentTimeMillis(); // Record when scanning started
        
        isScanning = true;
        cameraPreviewCard.setVisibility(View.VISIBLE);
        previewView.setVisibility(View.VISIBLE);
        startCamera();
        
        // Show message to user to focus and position document
        Toast.makeText(this, "Please focus and position the MRZ zone clearly. Scanning will start in 5 seconds...", Toast.LENGTH_LONG).show();
        
        // Show message to user to focus and position document
        Toast.makeText(this, "Please focus and position the MRZ zone clearly. Scanning will start in 5 seconds...", Toast.LENGTH_LONG).show();
    }
    
    private void stopBarcodeScanning() {
        isScanning = false;
        cameraPreviewCard.setVisibility(View.GONE);
        previewView.setVisibility(View.GONE);
        // Reset frame buffer and timing
        mrzFrameBuffer.clear();
        frameCounter = 0;
        lastProcessTime = 0;
        scanningStartTime = 0;
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
        
        // Throttle processing to avoid overwhelming the system
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime < MIN_PROCESS_INTERVAL_MS) {
            image.close();
            return;
        }
        
        // Process every Nth frame to reduce load and allow time for stabilization
        frameCounter++;
        if (frameCounter % FRAME_BUFFER_SIZE != 0) {
            image.close();
            return;
        }
        
        lastProcessTime = currentTime;
        
        // Get rotation and image dimensions for better debugging
        int rotationDegrees = image.getImageInfo().getRotationDegrees();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        
        Log.d(TAG, "Analyzing image for MRZ (frame #" + frameCounter + "): " + imageWidth + "x" + imageHeight + 
              ", rotation: " + rotationDegrees + "°");
        
        InputImage inputImage = InputImage.fromMediaImage(image.getImage(), rotationDegrees);
        
        textRecognizer.process(inputImage)
                .addOnSuccessListener(text -> {
                    if (text != null && isScanning) {
                        // Extract MRZ lines from recognized text
                        String mrzText = extractMRZText(text);
                        
                        if (mrzText != null && !mrzText.trim().isEmpty()) {
                            // Check if minimum scanning duration has elapsed
                            long elapsedTime = System.currentTimeMillis() - scanningStartTime;
                            boolean minDurationElapsed = elapsedTime >= MIN_SCANNING_DURATION_MS;
                            
                            if (!minDurationElapsed) {
                                // Still within the 5-second focus period - don't process yet, just log
                                long remainingTime = (MIN_SCANNING_DURATION_MS - elapsedTime + 999) / 1000; // Round up
                                Log.d(TAG, "MRZ detected in frame #" + frameCounter + " but waiting " + remainingTime + " more second(s) for focusing...");
                                // Continue scanning, don't process yet
                                return;
                            }
                            
                            // Add to frame buffer for consistency checking
                            mrzFrameBuffer.add(mrzText);
                            Log.d(TAG, "MRZ detected in frame #" + frameCounter + " (buffer size: " + mrzFrameBuffer.size() + ", elapsed: " + (elapsedTime / 1000) + "s)");
                            
                            // Require consistent detection across multiple frames to avoid false positives
                            if (mrzFrameBuffer.size() >= MIN_CONSISTENT_DETECTIONS) {
                                // Use the most recent detection (clearest image)
                                String finalMrzText = mrzFrameBuffer.get(mrzFrameBuffer.size() - 1);
                                Log.d(TAG, "========== MRZ TEXT CONFIRMED (detected " + mrzFrameBuffer.size() + " times after " + (elapsedTime / 1000) + "s) ==========");
                                Log.d(TAG, "MRZ Text:\n" + finalMrzText);
                                
                                isScanning = false;
                                mrzFrameBuffer.clear();
                                processMRZData(finalMrzText);
                                
                                // Stop camera
                                stopBarcodeScanning();
                            } else {
                                // Continue scanning to get more consistent detections
                                Log.d(TAG, "MRZ detected but need " + (MIN_CONSISTENT_DETECTIONS - mrzFrameBuffer.size()) + " more consistent detections");
                            }
                        } else if (isScanning) {
                            // Reset buffer if MRZ not detected in this frame
                            if (mrzFrameBuffer.size() > 0) {
                                Log.d(TAG, "MRZ lost in frame #" + frameCounter + ", resetting buffer");
                                mrzFrameBuffer.clear();
                            }
                            // Continue scanning - MRZ not found yet
                            Log.v(TAG, "Scanning... MRZ not detected yet. Image: " + 
                              imageWidth + "x" + imageHeight);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "MRZ text recognition failed", e);
                    Log.e(TAG, "Error details - Image size: " + imageWidth + "x" + imageHeight + 
                          ", Rotation: " + rotationDegrees + "°, Error: " + e.getMessage());
                    if (e.getCause() != null) {
                        Log.e(TAG, "Error cause: " + e.getCause().getMessage());
                    }
                    // Reset buffer on error
                    mrzFrameBuffer.clear();
                })
                .addOnCompleteListener(result -> image.close());
    }
    
    /**
     * Extract MRZ lines from recognized text
     * Looks for lines that match MRZ format (long lines with letters, numbers, and <)
     */
    private String extractMRZText(Text text) {
        if (text == null) {
            Log.w(TAG, "Text recognition returned null");
            return null;
        }
        
        // Debug: log all recognized text
        String allText = text.getText();
        Log.d(TAG, "========== RECOGNIZED TEXT ==========");
        Log.d(TAG, "Full text: " + allText);
        Log.d(TAG, "Text blocks count: " + text.getTextBlocks().size());
        
        java.util.List<String> mrzLines = new java.util.ArrayList<>();
        
        // Get all text blocks and lines
        for (Text.TextBlock block : text.getTextBlocks()) {
            Log.d(TAG, "TextBlock: " + block.getText());
            for (Text.Line line : block.getLines()) {
                String lineText = line.getText().trim();
                String originalLine = lineText;
                Log.d(TAG, "Line: [" + lineText + "]");
                
                // Convert to uppercase for processing
                lineText = lineText.toUpperCase();
                
                // Handle common OCR mistakes for < character:
                // < might be recognized as: |, /, \, etc.
                // DO NOT replace I or 1 globally as they are legitimate MRZ characters:
                // - I can be part of document type (e.g., "I<" at start)
                // - 1 can be part of dates or document numbers
                // Date OCR errors will be handled separately in MRZParser.cleanDateString()
                String normalized = lineText
                    .replaceAll("\\|+", "<")    // | (pipe) might be <
                    .replaceAll("[/\\\\]+", "<")  // / or \ might be <
                    .replaceAll("\\s+", "");     // Remove all spaces
                
                // More conservative: check if line has MRZ characteristics
                // MRZ lines are long (30+ chars) and contain mix of letters/numbers
                // They may or may not have < characters (OCR might miss them)
                
                // Check if line is long enough
                if (normalized.length() >= 28) { // Slightly lower threshold (28 instead of 30)
                    // Check if it contains mostly alphanumeric (and possibly <)
                    long alphanumericCount = normalized.chars()
                        .filter(c -> (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '<')
                        .count();
                    
                    // If at least 90% is alphanumeric or <, it's likely MRZ
                    double alphanumericRatio = (double) alphanumericCount / normalized.length();
                    
                    if (alphanumericRatio >= 0.85) {
                        // Check if line looks like MRZ format
                        // TD1 lines are exactly 30 chars, but OCR might not be perfect
                        // Look for pattern: starts with document type (I, P, A, C) or country code
                        boolean looksLikeMRZ = false;
                        
                        // Check if starts with document type indicators
                        if (normalized.length() > 2) {
                            char firstChar = normalized.charAt(0);
                            // TD1/TD2/TD3 typically start with P, I, A, C, or have country code at pos 2-4
                            if (firstChar == 'P' || firstChar == 'I' || firstChar == 'A' || firstChar == 'C') {
                                looksLikeMRZ = true;
                            } else if (normalized.length() > 5) {
                                // Check if positions 2-4 might be country code (3 uppercase letters)
                                String possibleCountry = normalized.substring(2, Math.min(5, normalized.length()));
                                if (possibleCountry.matches("[A-Z]{3}")) {
                                    looksLikeMRZ = true;
                                }
                            }
                        }
                        
                        // Also check if line has pattern of dates (YYMMDD)
                        if (normalized.matches(".*\\d{6}.*")) {
                            looksLikeMRZ = true;
                        }
                        
                        if (looksLikeMRZ || alphanumericRatio >= 0.95) {
                            mrzLines.add(normalized);
                            Log.d(TAG, "Found potential MRZ line: " + normalized.substring(0, Math.min(44, normalized.length())));
                            Log.d(TAG, "Original line was: " + originalLine);
                        }
                    }
                }
            }
        }
        
        Log.d(TAG, "Total MRZ lines found: " + mrzLines.size());
        for (int i = 0; i < mrzLines.size(); i++) {
            Log.d(TAG, "MRZ Line " + (i + 1) + ": " + mrzLines.get(i));
        }
        
        // For TD1 format, we need 3 lines, but accept 2+ lines to be more flexible
        if (mrzLines.size() >= 2) {
            // Join lines with newline
            String result = String.join("\n", mrzLines);
            Log.d(TAG, "Returning MRZ text with " + mrzLines.size() + " lines");
            return result;
        }
        
        Log.w(TAG, "Not enough MRZ lines found (found " + mrzLines.size() + ", need at least 2)");
        return null;
    }
    
    /**
     * Process MRZ data and populate customer entity
     */
    private void processMRZData(String mrzText) {
        try {
            Log.d(TAG, "Processing MRZ data:\n" + mrzText);
            
            // Parse MRZ using MRZParser
            MRZParser.MRZData mrzData = MRZParser.parseMRZ(mrzText);
            
            if (mrzData == null || !mrzData.isValid()) {
                Log.w(TAG, "Failed to parse MRZ or invalid MRZ data");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Unable to read MRZ data. Please hold the document steady, ensure good lighting, and try again.", Toast.LENGTH_LONG).show();
                });
                // Continue scanning - reset buffer to allow fresh detection
                mrzFrameBuffer.clear();
                frameCounter = 0;
                isScanning = true;
                return;
            }
            
            // Create customer entity from MRZ data
            CustomerEntity customer = new CustomerEntity();
            
            // Map MRZ data to customer fields
            customer.setFullName(mrzData.fullName);
            customer.setNationalIdNumber(mrzData.nationalIdNumber != null ? mrzData.nationalIdNumber : mrzData.documentNumber);
            customer.setDateOfBirth(mrzData.dateOfBirth); // Already in YYYY-MM-DD format
            customer.setExpiryDate(mrzData.expiryDate); // Already in YYYY-MM-DD format
            customer.setDocumentType(mrzData.documentTypeName);
            
            // Set ID based on national ID number or document number
            if (customer.getNationalIdNumber() != null && !customer.getNationalIdNumber().isEmpty()) {
                customer.setId(customer.getNationalIdNumber());
            } else if (mrzData.documentNumber != null && !mrzData.documentNumber.isEmpty()) {
                customer.setId(mrzData.documentNumber);
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
            
            if (!hasParsedData) {
                Log.w(TAG, "No valid data parsed from MRZ");
                runOnUiThread(() -> {
                    Toast.makeText(this, "No valid data found in MRZ. Please ensure the MRZ zone is clearly visible and try again.", Toast.LENGTH_LONG).show();
                });
                // Continue scanning - reset buffer to allow fresh detection
                mrzFrameBuffer.clear();
                frameCounter = 0;
                isScanning = true;
                return;
            }
            
            // Show parsed preview
            String preview = "Name: " + (customer.getFullName() != null ? customer.getFullName() : "") +
                    "\nDOB: " + (customer.getDateOfBirth() != null ? customer.getDateOfBirth() : "") +
                    "\nID: " + (customer.getNationalIdNumber() != null ? customer.getNationalIdNumber() : "") +
                    "\nDocument Type: " + (customer.getDocumentType() != null ? customer.getDocumentType() : "") +
                    "\nExpiry: " + (customer.getExpiryDate() != null ? customer.getExpiryDate() : "");
            
            runOnUiThread(() -> {
            new android.app.AlertDialog.Builder(this)
                        .setTitle("Scanned MRZ Data")
                    .setMessage(preview)
                    .setPositiveButton("Use Data", (d, w) -> showCustomerFormDialog(customer, true))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            });
            
            Log.d(TAG, "MRZ data processed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing MRZ data", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "Error processing MRZ. Please try again.", Toast.LENGTH_SHORT).show();
            });
            // Continue scanning on error - reset buffer
            mrzFrameBuffer.clear();
            frameCounter = 0;
            isScanning = true;
        }
    }
    
    /**
     * Setup automatic date formatting for YYYY-MM-DD format with dashes
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
                
                // Format as YYYY-MM-DD
                for (int i = 0; i < input.length() && i < 8; i++) {
                    if (i == 4 || i == 6) {
                        formatted.append("-");
                    }
                    formatted.append(input.charAt(i));
                }
                
                // Update cursor position
                int cursorPos = formatted.length();
                if (cursorPos > 10) cursorPos = 10; // Max length YYYY-MM-DD
                
                etDateOfBirth.setText(formatted.toString());
                etDateOfBirth.setSelection(Math.min(cursorPos, formatted.length()));
                
                isFormatting = false;
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }
    
    /**
     * Convert date from various formats to YYYY-MM-DD format for display
     */
    private String convertDateToYYYYMMDD(String date) {
        if (date == null || date.trim().isEmpty()) return "";
        
        try {
            String cleanDate = date.trim();
            
            // If already in YYYY-MM-DD format
            if (cleanDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return cleanDate;
            }
            
            // If in DD-MM-YYYY format
            if (cleanDate.matches("\\d{2}-\\d{2}-\\d{4}")) {
                String[] parts = cleanDate.split("-");
                return parts[2] + "-" + parts[1] + "-" + parts[0]; // YYYY-MM-DD
            }
            
            // If in YYYYMMDD format
            if (cleanDate.matches("\\d{8}")) {
                return cleanDate.substring(0, 4) + "-" + cleanDate.substring(4, 6) + "-" + cleanDate.substring(6, 8);
            }
            
            // If in YYMMDD format
            if (cleanDate.matches("\\d{6}")) {
                int year = Integer.parseInt(cleanDate.substring(0, 2));
                String fullYear = (year >= 0 && year <= 30) ? "20" + String.format("%02d", year) : "19" + String.format("%02d", year);
                return fullYear + "-" + cleanDate.substring(2, 4) + "-" + cleanDate.substring(4, 6);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting date to YYYY-MM-DD: " + date, e);
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
            String dobDisplay = convertDateToYYYYMMDD(customer.getDateOfBirth());
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
                if (validateCustomerForm(etFullName, etDateOfBirth, etNationalId, etIssueDate, etExpiryDate, etEmail, etPhoneNumber)) {
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
                                       EditText etIssueDate, EditText etExpiryDate, EditText etEmail, EditText etPhoneNumber) {
        if (TextUtils.isEmpty(etFullName.getText())) {
            etFullName.setError(getString(R.string.required_fields_missing));
            return false;
        }
        if (TextUtils.isEmpty(etNationalId.getText())) {
            etNationalId.setError(getString(R.string.required_fields_missing));
            return false;
        }
        if (TextUtils.isEmpty(etPhoneNumber.getText())) {
            etPhoneNumber.setError(getString(R.string.required_fields_missing));
            return false;
        }
        
        // Validate date formats - All dates use YYYY-MM-DD format
        if (!TextUtils.isEmpty(etDateOfBirth.getText()) && !isValidDateFormatYYYYMMDD(etDateOfBirth.getText().toString())) {
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
                String phoneNumber = etPhoneNumber.getText().toString().trim();
                
                // Check if customer with same National ID already exists (for new customers)
                if (customer.getId() == null) {
                    CustomerEntity existingCustomerById = database.customerDao().getCustomerByNationalId(etNationalId.getText().toString());
                    if (existingCustomerById != null) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, getString(R.string.customer_already_exists), Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                    
                    // Check if customer with same phone number already exists for this agent (for new customers only)
                    if (phoneNumber != null && !phoneNumber.isEmpty() && currentUser != null) {
                        CustomerEntity existingCustomerByPhone = database.customerDao().getCustomerByPhoneNumberAndUser(phoneNumber, currentUser.getUid());
                        if (existingCustomerByPhone != null) {
                            // Show dialog offering to select existing customer
                            CustomerEntity finalExistingCustomer = existingCustomerByPhone;
                            runOnUiThread(() -> {
                                showDuplicatePhoneDialog(finalExistingCustomer, customer, etFullName, etDateOfBirth, 
                                        documentType, etNationalId, etIssueDate, etExpiryDate, etPhoneNumber, etAddress, etEmail);
                            });
                            return;
                        }
                    }
                } else {
                    // For editing existing customers, check phone number but exclude current customer ID
                    if (phoneNumber != null && !phoneNumber.isEmpty() && currentUser != null) {
                        CustomerEntity existingCustomerByPhone = database.customerDao().getCustomerByPhoneNumberAndUserExcluding(
                                phoneNumber, currentUser.getUid(), customer.getId());
                        if (existingCustomerByPhone != null) {
                            // Show dialog offering to select existing customer
                            CustomerEntity finalExistingCustomer = existingCustomerByPhone;
                            runOnUiThread(() -> {
                                showDuplicatePhoneDialog(finalExistingCustomer, customer, etFullName, etDateOfBirth, 
                                        documentType, etNationalId, etIssueDate, etExpiryDate, etPhoneNumber, etAddress, etEmail);
                            });
                            return;
                        }
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
                
                // Format and validate date of birth (stored as YYYY-MM-DD)
                String dobText = etDateOfBirth.getText().toString().trim();
                if (!dobText.isEmpty()) {
                    String normalizedDob = normalizeDateFormat(dobText);
                    if (normalizedDob != null && isValidDateFormatYYYYMMDD(normalizedDob)) {
                        customer.setDateOfBirth(normalizedDob); // Store as YYYY-MM-DD
                        Log.d(TAG, "Date of birth normalized: " + dobText + " -> " + normalizedDob);
                    } else {
                        customer.setDateOfBirth(null);
                        Log.w(TAG, "Invalid date of birth format: " + dobText);
                    }
                } else {
                    customer.setDateOfBirth(null);
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
    
    private void showDuplicatePhoneDialog(CustomerEntity existingCustomer, CustomerEntity newCustomer, 
                                         EditText etFullName, EditText etDateOfBirth, String documentType,
                                         EditText etNationalId, EditText etIssueDate, EditText etExpiryDate,
                                         EditText etPhoneNumber, EditText etAddress, EditText etEmail) {
        String customerName = existingCustomer.getFullName() != null ? existingCustomer.getFullName() : "N/A";
        String customerPhone = existingCustomer.getPhoneNumber() != null ? existingCustomer.getPhoneNumber() : "N/A";
        String message = String.format(getString(R.string.customer_exists_phone_message), customerName, customerPhone);
        
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.select_existing_customer))
                .setMessage(message)
                .setPositiveButton(getString(R.string.select_customer), (dialog, which) -> {
                    // Select the existing customer - scroll to it in the list
                    selectExistingCustomer(existingCustomer);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }
    
    private void selectExistingCustomer(CustomerEntity customer) {
        // Reload customers to ensure the list is up to date
        loadCustomers();
        
        // Scroll to the customer in the RecyclerView
        runOnUiThread(() -> {
            int position = -1;
            for (int i = 0; i < customers.size(); i++) {
                if (customers.get(i).getId().equals(customer.getId())) {
                    position = i;
                    break;
                }
            }
            
            if (position >= 0) {
                recyclerViewCustomers.scrollToPosition(position);
                // Highlight the customer by showing their details
                showCustomerDetailsDialog(customer);
            } else {
                Toast.makeText(this, getString(R.string.customer_details), Toast.LENGTH_SHORT).show();
            }
        });
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
        if (textRecognizer != null) {
            textRecognizer.close();
        }
    }
}
