package com.example.myapplication.utils;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple seeder to create agent2 with proper Firebase Auth account and Firestore document.
 * This will create the user in Firebase Auth with password "agent2123" and automatically
 * create the Firestore document.
 */
public class Agent2Seeder {
    private static final String TAG = "Agent2Seeder";
    private static boolean ran = false;

    private Agent2Seeder() {}

    public static void runOnce() {
        if (ran) {
            Log.d(TAG, "Agent2Seeder already executed in this session.");
            return;
        }
        ran = true;

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Log.d(TAG, "Starting Agent2Seeder...");

        // Create Firebase Auth account for agent2
        auth.createUserWithEmailAndPassword("agent2@test.com", "agent2123")
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    Log.d(TAG, "Agent2 Firebase Auth account created with UID: " + uid);
                    
                    // Create Firestore document for agent2
                    Map<String, Object> agent2Data = new HashMap<>();
                    agent2Data.put("uid", uid);
                    agent2Data.put("email", "agent2@test.com");
                    agent2Data.put("name", "Agent 2");
                    agent2Data.put("phone", "+1234567892");
                    agent2Data.put("role", "agent");
                    agent2Data.put("dealerId", "RhKD03mk1CdSFyJBgDjVY9nOfO52"); // Will be updated with actual dealer UID
                    agent2Data.put("active", true);
                    agent2Data.put("createdAt", System.currentTimeMillis());
                    agent2Data.put("updatedAt", System.currentTimeMillis());

                    db.collection("users").document(uid)
                            .set(agent2Data)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Agent2 Firestore document created successfully");
                                
                                // Create license for agent2
                                createAgent2License(db, uid);
                                
                                // Store credentials in local database for offline login
                                storeAgent2Credentials(uid);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error creating Agent2 Firestore document: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    if (e.getMessage().contains("already in use")) {
                        Log.d(TAG, "Agent2 account already exists in Firebase Auth");
                        // Still try to create/update the Firestore document
                        createAgent2FirestoreDoc(db);
                    } else {
                        Log.e(TAG, "Error creating Agent2 Firebase Auth account: " + e.getMessage());
                    }
                });
    }

    private static void createAgent2FirestoreDoc(FirebaseFirestore db) {
        // If auth account exists, we need to get the UID from existing user
        FirebaseAuth.getInstance().signInWithEmailAndPassword("agent2@test.com", "agent2123")
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    Log.d(TAG, "Found existing Agent2 UID: " + uid);
                    
                    Map<String, Object> agent2Data = new HashMap<>();
                    agent2Data.put("uid", uid);
                    agent2Data.put("email", "agent2@test.com");
                    agent2Data.put("name", "Agent 2");
                    agent2Data.put("phone", "+1234567892");
                    agent2Data.put("role", "agent");
                    agent2Data.put("dealerId", "dealer-test-123"); // Will be updated with actual dealer UID
                    agent2Data.put("active", true);
                    agent2Data.put("createdAt", System.currentTimeMillis());
                    agent2Data.put("updatedAt", System.currentTimeMillis());

                    db.collection("users").document(uid)
                            .set(agent2Data)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Agent2 Firestore document created/updated successfully");
                                createAgent2License(db, uid);
                                
                                // Store credentials in local database for offline login
                                storeAgent2Credentials(uid);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error creating/updating Agent2 Firestore document: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error signing in to existing Agent2 account: " + e.getMessage());
                });
    }

    private static void createAgent2License(FirebaseFirestore db, String agentUid) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 1);
        
        Map<String, Object> licenseData = new HashMap<>();
        licenseData.put("licenseKey", "LIC-AGENT-002");
        licenseData.put("assignedToUserId", agentUid);
        licenseData.put("issueDate", Calendar.getInstance().getTime());
        licenseData.put("expiryDate", calendar.getTime());
        licenseData.put("isActive", true);

        db.collection("licenses").document("LIC-AGENT-002")
                .set(licenseData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Agent2 license created successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating Agent2 license: " + e.getMessage());
                });
    }
    
    private static void storeAgent2Credentials(String uid) {
        // Store credentials in local database for offline login
        android.content.Context context = null; // We need context for this
        // For now, we'll handle this in the main app when the seeder runs
        Log.d(TAG, "Agent2 credentials should be stored for UID: " + uid);
    }
}
