package com.example.myapplication.utils;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Safe/idempotent Firestore seeder used once to align existing data with the
 * latest app requirements. This WILL:
 *  - update existing docs if they differ
 *  - insert missing docs
 *  - never delete existing docs
 *
 * Usage: Call DatabaseSeederExtended.runOnce() from a guarded admin-only path
 * (e.g., temporary debug button), then remove/comment the call.
 */
public final class DatabaseSeederExtended {
    private static final String TAG = "DatabaseSeederExtended";
    private static boolean ran = false;

    private DatabaseSeederExtended() {}

    public static void runOnce() {
        if (ran) {
            Log.d(TAG, "Seeder already executed in this session.");
            return;
        }
        ran = true;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1) Ensure dealer exists and has license
        ensureDealer(db, "dealer@test.com", new Callback<String>() {
            @Override public void onSuccess(String dealerUid) {
                ensureDealerLicense(db, dealerUid, "LIC-DEALER-001");

                // 2) Ensure agent exists, associated to dealer, and has license
                ensureAgent(db, "agent@test.com", dealerUid, new Callback<String>() {
                    @Override public void onSuccess(String agentUid) {
                        ensureAgentLicense(db, agentUid, "LIC-AGENT-001");
                    }
                    @Override public void onError(Exception e) {
                        Log.e(TAG, "ensureAgent error", e);
                    }
                });

                // 3) Create a second agent for demo, assigned to same dealer
                ensureAgent(db, "agent2@test.com", dealerUid, new Callback<String>() {
                    @Override public void onSuccess(String agentUid) {
                        ensureAgentLicense(db, agentUid, "LIC-AGENT-002");
                    }
                    @Override public void onError(Exception e) {
                        Log.e(TAG, "ensureAgent(2) error", e);
                    }
                });
            }
            @Override public void onError(Exception e) {
                Log.e(TAG, "ensureDealer error", e);
            }
        });

        // 4) Operators and actions already exist from previous seeding; if missing, add minimal ones
        // Mobile Money A operator
        upsert(db, "operators", "mobile-money-a", new HashMap<String, Object>() {{
            put("id", "mobile-money-a");
            put("name", "Mobile Money A");
            put("type", "USSD");
            put("active", true);
        }});

        // Example action (deposit)
        upsert(db, "operatorActions", "deposit-mobile-money-a", new HashMap<String, Object>() {{
            put("id", "deposit-mobile-money-a");
            put("operatorId", "mobile-money-a");
            put("name", "Deposit");
            put("kind", "deposit");
            put("requiredFields", java.util.Arrays.asList("number", "amount"));
            put("ussdTemplate", "*144*2*1*{number}*{amount}#");
        }});

        // 5) Backfill customers with the dealer mapping for aggregation
        backfillCustomersWithDealerMapping(db);
    }

    private static void ensureDealer(FirebaseFirestore db, String email, Callback<String> cb) {
        db.collection("users").whereEqualTo("email", email).limit(1).get()
            .addOnSuccessListener(qs -> {
                if (!qs.isEmpty()) {
                    DocumentSnapshot d = qs.getDocuments().get(0);
                    String uid = d.getString("uid");
                    if (uid != null) {
                        // ensure role dealer
                        Map<String, Object> patch = new HashMap<>();
                        patch.put("role", "dealer");
                        patch.put("active", true);
                        db.collection("users").document(d.getId()).set(patch, com.google.firebase.firestore.SetOptions.merge());
                        cb.onSuccess(uid);
                        return;
                    }
                }
                // Not found -> create minimal dealer doc (assumes account exists from auth flow)
                Map<String, Object> dealer = new HashMap<>();
                String uidGen = java.util.UUID.randomUUID().toString();
                dealer.put("uid", uidGen);
                dealer.put("email", email);
                dealer.put("name", "Seed Dealer");
                dealer.put("role", "dealer");
                dealer.put("active", true);
                dealer.put("createdAt", System.currentTimeMillis());
                db.collection("users").document(uidGen).set(dealer)
                    .addOnSuccessListener(v -> cb.onSuccess(uidGen))
                    .addOnFailureListener(cb::onError);
            })
            .addOnFailureListener(cb::onError);
    }

    private static void ensureAgent(FirebaseFirestore db, String email, String dealerUid, Callback<String> cb) {
        db.collection("users").whereEqualTo("email", email).limit(1).get()
            .addOnSuccessListener(qs -> {
                if (!qs.isEmpty()) {
                    DocumentSnapshot d = qs.getDocuments().get(0);
                    String uid = d.getString("uid");
                    if (uid != null) {
                        Map<String, Object> patch = new HashMap<>();
                        patch.put("role", "agent");
                        patch.put("dealerId", dealerUid);
                        patch.put("active", true);
                        db.collection("users").document(d.getId()).set(patch, com.google.firebase.firestore.SetOptions.merge());
                        cb.onSuccess(uid);
                        return;
                    }
                }
                // Not found -> create minimal agent doc
                Map<String, Object> agent = new HashMap<>();
                String uidGen = java.util.UUID.randomUUID().toString();
                agent.put("uid", uidGen);
                agent.put("email", email);
                agent.put("name", "Seed Agent");
                agent.put("role", "agent");
                agent.put("dealerId", dealerUid);
                agent.put("active", true);
                agent.put("createdAt", System.currentTimeMillis());
                db.collection("users").document(uidGen).set(agent)
                    .addOnSuccessListener(v -> cb.onSuccess(uidGen))
                    .addOnFailureListener(cb::onError);
            })
            .addOnFailureListener(cb::onError);
    }

    private static void ensureDealerLicense(FirebaseFirestore db, String dealerUid, String licenseKey) {
        upsert(db, "licenses", licenseKey, new HashMap<String, Object>() {{
            put("licenseKey", licenseKey);
            put("assignedToUserId", dealerUid);
            Calendar c = Calendar.getInstance();
            put("issueDate", c.getTime());
            c.add(Calendar.YEAR, 1);
            put("expiryDate", c.getTime());
            put("isActive", true);
        }});
    }

    private static void ensureAgentLicense(FirebaseFirestore db, String agentUid, String licenseKey) {
        upsert(db, "licenses", licenseKey, new HashMap<String, Object>() {{
            put("licenseKey", licenseKey);
            put("assignedToUserId", agentUid);
            Calendar c = Calendar.getInstance();
            put("issueDate", c.getTime());
            c.add(Calendar.YEAR, 1);
            put("expiryDate", c.getTime());
            put("isActive", true);
        }});
    }

    private static void upsert(FirebaseFirestore db, String col, String id, Map<String, Object> data) {
        DocumentReference ref = db.collection(col).document(id);
        ref.get().addOnSuccessListener(ds -> {
            if (ds.exists()) {
                ref.set(data, com.google.firebase.firestore.SetOptions.merge());
            } else {
                ref.set(data);
            }
        }).addOnFailureListener(e -> Log.e(TAG, "upsert(" + col + "/" + id + ") failed", e));
    }

    /**
     * For each agent that has dealerId set, tag all their customers with createdByDealerId
     * so dealer-level aggregations can query quickly.
     */
    private static void backfillCustomersWithDealerMapping(FirebaseFirestore db) {
        db.collection("users").whereEqualTo("role", "agent").get()
            .addOnSuccessListener(users -> {
                for (DocumentSnapshot user : users) {
                    String agentUid = user.getString("uid");
                    String dealerUid = user.getString("dealerId");
                    if (agentUid == null || dealerUid == null) continue;
                    db.collection("customers").whereEqualTo("createdBy", agentUid).get()
                        .addOnSuccessListener(customers -> {
                            for (DocumentSnapshot c : customers) {
                                Map<String, Object> patch = new HashMap<>();
                                patch.put("createdByDealerId", dealerUid);
                                db.collection("customers").document(c.getId())
                                    .set(patch, com.google.firebase.firestore.SetOptions.merge());
                            }
                        });
                }
            })
            .addOnFailureListener(e -> Log.e(TAG, "backfillCustomersWithDealerMapping error", e));
    }

    public interface Callback<T> {
        void onSuccess(T t);
        void onError(Exception e);
    }
}


