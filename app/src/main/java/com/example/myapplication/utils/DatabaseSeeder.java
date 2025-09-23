package com.example.myapplication.utils;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.entities.License;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;

public class DatabaseSeeder {
    private static final String TAG = "DatabaseSeeder";
    private static boolean seeded = false; // To prevent multiple seeding
    
    private static final String TEST_LICENSE_KEY = "TEST-LICENSE-2024-001";
    private static final String TEST_USER_ID = "test-user-123";
    private static final String TEST_DEVICE_ID = "test-device-456";

    public static void seedTestLicense(Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Create a test license valid for 1 year
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 1);
        java.util.Date expiryDate = calendar.getTime();

        License testLicense = new License(
            TEST_LICENSE_KEY,
            TEST_USER_ID,
            new java.util.Date(),
            expiryDate,
            true
        );

        // Add to Firestore
        db.collection("licenses")
                .document("test-license-id")
                .set(testLicense)
                .addOnSuccessListener(aVoid -> {
                    // License seeded successfully
                })
                .addOnFailureListener(e -> {
                    // Handle error
                });
    }

    public static void seedTestData() {
        if (seeded) {
            Log.d(TAG, "Test data already seeded.");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        
        Log.d(TAG, "Starting test data seeding...");

        // Create Firebase Auth accounts for testing (this will also create user documents and licenses)
        createTestAuthAccounts(auth, db);

        // Seed test operators
        db.collection("operators")
                .document("mobile-money-a")
                .set(new java.util.HashMap<String, Object>() {{
                    put("id", "mobile-money-a");
                    put("name", "Mobile Money A");
                    put("type", "USSD");
                    put("active", true);
                    put("createdAt", System.currentTimeMillis());
                }});

        db.collection("operators")
                .document("western-union")
                .set(new java.util.HashMap<String, Object>() {{
                    put("id", "western-union");
                    put("name", "Western Union");
                    put("type", "NON_USSD");
                    put("active", true);
                    put("createdAt", System.currentTimeMillis());
                }});

        // Seed test operator actions
        db.collection("operatorActions")
                .document("deposit-mobile-money-a")
                .set(new java.util.HashMap<String, Object>() {{
                    put("id", "deposit-mobile-money-a");
                    put("operatorId", "mobile-money-a");
                    put("name", "Deposit");
                    put("kind", "deposit");
                    put("requiredFields", java.util.Arrays.asList("number", "amount"));
                    put("ussdTemplate", "*144*2*1*{number}*{amount}#");
                    put("createdAt", System.currentTimeMillis());
                }});

        db.collection("operatorActions")
                .document("withdraw-mobile-money-a")
                .set(new java.util.HashMap<String, Object>() {{
                    put("id", "withdraw-mobile-money-a");
                    put("operatorId", "mobile-money-a");
                    put("name", "Withdraw");
                    put("kind", "withdraw");
                    put("requiredFields", java.util.Arrays.asList("number", "amount"));
                    put("ussdTemplate", "*144*2*2*{number}*{amount}#");
                    put("createdAt", System.currentTimeMillis());
                }});

        db.collection("operatorActions")
                .document("transfer-mobile-money-a")
                .set(new java.util.HashMap<String, Object>() {{
                    put("id", "transfer-mobile-money-a");
                    put("operatorId", "mobile-money-a");
                    put("name", "Transfer");
                    put("kind", "transfer");
                    put("requiredFields", java.util.Arrays.asList("number", "amount"));
                    put("ussdTemplate", "*144*2*3*{number}*{amount}#");
                    put("createdAt", System.currentTimeMillis());
                }});

        db.collection("operatorActions")
                .document("payout-western-union")
                .set(new java.util.HashMap<String, Object>() {{
                    put("id", "payout-western-union");
                    put("operatorId", "western-union");
                    put("name", "Payout");
                    put("kind", "payout");
                    put("requiredFields", java.util.Arrays.asList("referenceNumber", "amount", "customerName"));
                    put("createdAt", System.currentTimeMillis());
                }});

        seeded = true;
        Log.d(TAG, "Test data seeding completed!");
    }

    private static void createTestAuthAccounts(FirebaseAuth auth, FirebaseFirestore db) {
        Log.d(TAG, "Creating test Firebase Auth accounts...");
        
        // Create dealer account
        auth.createUserWithEmailAndPassword("dealer@test.com", "dealer123")
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    Log.d(TAG, "Dealer account created with UID: " + uid);
                    
                    // Update the dealer document with correct UID
                    db.collection("users").document(uid)
                            .set(new java.util.HashMap<String, Object>() {{
                                put("uid", uid);
                                put("email", "dealer@test.com");
                                put("name", "Test Dealer");
                                put("phone", "+1234567890");
                                put("role", "dealer");
                                put("dealerId", null);
                                put("active", true);
                                put("createdAt", System.currentTimeMillis());
                                put("updatedAt", System.currentTimeMillis());
                            }})
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Dealer document updated with correct UID");
                                
                                // Create license for dealer
                                Calendar calendar = Calendar.getInstance();
                                calendar.add(Calendar.YEAR, 1);
                                java.util.Date expiryDate = calendar.getTime();
                                
                                db.collection("licenses").document("LIC-DEALER-001")
                                        .set(new java.util.HashMap<String, Object>() {{
                                            put("licenseKey", "LIC-DEALER-001");
                                            put("assignedToUserId", uid);
                                            put("issueDate", new java.util.Date());
                                            put("expiryDate", expiryDate);
                                            put("isActive", true);
                                        }})
                                        .addOnSuccessListener(aVoid2 -> Log.d(TAG, "Dealer license created"))
                                        .addOnFailureListener(e -> Log.e(TAG, "Error creating dealer license: " + e.getMessage()));
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Error updating dealer document: " + e.getMessage()));
                })
                .addOnFailureListener(e -> {
                    if (e.getMessage().contains("already in use")) {
                        Log.d(TAG, "Dealer account already exists");
                    } else {
                        Log.e(TAG, "Error creating dealer account: " + e.getMessage());
                    }
                });

        // Create agent account
        auth.createUserWithEmailAndPassword("agent@test.com", "agent123")
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    Log.d(TAG, "Agent account created with UID: " + uid);
                    
                    // Update the agent document with correct UID
                    db.collection("users").document(uid)
                            .set(new java.util.HashMap<String, Object>() {{
                                put("uid", uid);
                                put("email", "agent@test.com");
                                put("name", "Test Agent");
                                put("phone", "+1234567891");
                                put("role", "agent");
                                put("dealerId", "dealer-test-123"); // Will be updated later with actual dealer UID
                                put("active", true);
                                put("createdAt", System.currentTimeMillis());
                                put("updatedAt", System.currentTimeMillis());
                            }})
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Agent document updated with correct UID");
                                
                                // Create license for agent
                                Calendar calendar = Calendar.getInstance();
                                calendar.add(Calendar.YEAR, 1);
                                java.util.Date expiryDate = calendar.getTime();
                                
                                db.collection("licenses").document("LIC-AGENT-001")
                                        .set(new java.util.HashMap<String, Object>() {{
                                            put("licenseKey", "LIC-AGENT-001");
                                            put("assignedToUserId", uid);
                                            put("issueDate", new java.util.Date());
                                            put("expiryDate", expiryDate);
                                            put("isActive", true);
                                        }})
                                        .addOnSuccessListener(aVoid2 -> Log.d(TAG, "Agent license created"))
                                        .addOnFailureListener(e -> Log.e(TAG, "Error creating agent license: " + e.getMessage()));
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Error updating agent document: " + e.getMessage()));
                })
                .addOnFailureListener(e -> {
                    if (e.getMessage().contains("already in use")) {
                        Log.d(TAG, "Agent account already exists");
                    } else {
                        Log.e(TAG, "Error creating agent account: " + e.getMessage());
                    }
                });
    }
}

