package com.example.myapplication;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapters.AgentListAdapter;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.services.DataSyncService;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddCreditsActivity extends AppCompatActivity {
    private static final String TAG = "AddCreditsActivity";
    
    private RecyclerView rvAgents;
    private AgentListAdapter adapter;
    private SessionManager sessionManager;
    private AppDatabase database;
    private FirebaseFirestore firestore;
    private UserEntity currentUser;
    private List<UserEntity> agentList = new ArrayList<>();
    
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge display
        com.example.myapplication.utils.EdgeToEdgeHelper.enableEdgeToEdge(this);
        
        setContentView(R.layout.activity_add_credits);
        
        // Setup window insets for header
        com.example.myapplication.utils.EdgeToEdgeHelper.setupHeaderInsets(findViewById(R.id.headerLayout), this);
        com.example.myapplication.utils.EdgeToEdgeHelper.setupImeInsetsForRoot(this);
        
        sessionManager = new SessionManager(this);
        database = AppDatabase.getDatabase(this);
        firestore = FirebaseFirestore.getInstance();
        currentUser = sessionManager.getCurrentUser();
        
        initViews();
        loadAgents();
    }
    
    private void initViews() {
        // Setup header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.add_credits));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        rvAgents = findViewById(R.id.rvAgents);
        rvAgents.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new AgentListAdapter(this, agentList, agent -> {
            showAddCreditDialog(agent);
        });
        rvAgents.setAdapter(adapter);
    }
    
    private void loadAgents() {
        if (currentUser == null) {
            Toast.makeText(this, "No active user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // If online: load directly from Firestore so dealer always sees active agents
        if (isOnline()) {
            firestore.collection("users")
                    .whereEqualTo("role", "agent")
                    .whereEqualTo("dealerId", currentUser.getUid())
                    .whereEqualTo("active", true)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<UserEntity> agents = new ArrayList<>();
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                            try {
                                UserEntity agent = new UserEntity();
                                String uid = doc.getString("uid");
                                if (uid == null || uid.isEmpty()) uid = doc.getId();
                                agent.setUid(uid);
                                agent.setEmail(doc.getString("email"));
                                agent.setName(doc.getString("name"));
                                agent.setPhone(doc.getString("phone"));
                                agent.setRole("agent");
                                agent.setDealerId(doc.getString("dealerId"));
                                Boolean active = doc.getBoolean("active");
                                agent.setActive(active != null ? active : true);

                                Object vc = doc.get("virtualCredit");
                                if (vc instanceof Number) agent.setVirtualCredit(((Number) vc).doubleValue());

                                agent.setCreatedAt(toMillis(doc.get("createdAt")));
                                agent.setUpdatedAt(toMillis(doc.get("updatedAt")));
                                agent.setLastSyncAt(System.currentTimeMillis());

                                agents.add(agent);

                                // Cache to local DB for offline use
                                UserEntity finalAgent = agent;
                                new Thread(() -> {
                                    try { database.userDao().insertUser(finalAgent); } catch (Exception ignored) {}
                                }).start();
                            } catch (Exception ex) {
                                Log.w(TAG, "Skipping malformed agent record", ex);
                            }
                        }

                        runOnUiThread(() -> {
                            agentList.clear();
                            agentList.addAll(agents);
                            adapter.notifyDataSetChanged();
                            if (agents.isEmpty()) {
                                Toast.makeText(this, getString(R.string.no_agents_found), Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading agents from Firestore", e);
                        // Fallback to local if Firestore fails
                        loadAgentsFromLocal();
                    });
        } else {
            // Offline: fallback to local database
            loadAgentsFromLocal();
        }
    }

    private void loadAgentsFromLocal() {
        new Thread(() -> {
            try {
                List<UserEntity> allUsers = database.userDao().getAllUsers();
                List<UserEntity> agents = new ArrayList<>();
                for (UserEntity user : allUsers) {
                    if ("agent".equalsIgnoreCase(user.getRole()) &&
                            currentUser.getUid().equals(user.getDealerId()) &&
                            user.isActive()) {
                        agents.add(user);
                    }
                }
                runOnUiThread(() -> {
                    agentList.clear();
                    agentList.addAll(agents);
                    adapter.notifyDataSetChanged();
                    if (agents.isEmpty()) {
                        Toast.makeText(this, getString(R.string.no_agents_found), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading agents locally", e);
                runOnUiThread(() -> Toast.makeText(this, "Error loading agents: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private long toMillis(Object value) {
        try {
            if (value == null) return System.currentTimeMillis();
            if (value instanceof java.util.Date) return ((java.util.Date) value).getTime();
            if (value instanceof com.google.firebase.Timestamp) return ((com.google.firebase.Timestamp) value).toDate().getTime();
            if (value instanceof Number) return ((Number) value).longValue();
        } catch (Exception ignored) {}
        return System.currentTimeMillis();
    }
    
    private void showAddCreditDialog(UserEntity agent) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_credit, null);
        
        TextView tvAgentName = dialogView.findViewById(R.id.tvAgentName);
        TextView tvCurrentCredit = dialogView.findViewById(R.id.tvCurrentCredit);
        EditText etCreditAmount = dialogView.findViewById(R.id.etCreditAmount);
        
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());
        tvAgentName.setText(agent.getName());
        tvCurrentCredit.setText(getString(R.string.current_credit_label) + ": " + 
                currencyFormat.format(agent.getVirtualCredit()));
        
        // Add thousands separator to amount field
        com.example.myapplication.utils.NumberFormatter.addThousandsSeparator(etCreditAmount);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.add_credits))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.add), null)
                .setNegativeButton(getString(R.string.cancel), (d, which) -> d.dismiss())
                .create();
        
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String amountStr = etCreditAmount.getText().toString().trim();
                if (TextUtils.isEmpty(amountStr)) {
                    etCreditAmount.setError(getString(R.string.invalid_amount));
                    return;
                }
                
                try {
                    double amount = com.example.myapplication.utils.NumberFormatter.getNumericValue(amountStr);
                    if (amount <= 0) {
                        etCreditAmount.setError(getString(R.string.invalid_amount));
                        return;
                    }
                    
                    // Check if dealer has enough credit
                    if (currentUser.getVirtualCredit() < amount) {
                        Toast.makeText(this, getString(R.string.insufficient_credit), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    dialog.dismiss();
                    performCreditTransfer(agent, amount);
                } catch (NumberFormatException e) {
                    etCreditAmount.setError(getString(R.string.invalid_amount));
                }
            });
        });
        
        dialog.show();
    }
    
    private void performCreditTransfer(UserEntity agent, double amount) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.processing_please_wait));
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        new Thread(() -> {
            try {
                // Update credits in local database
                double dealerNewCredit = currentUser.getVirtualCredit() - amount;
                double agentNewCredit = agent.getVirtualCredit() + amount;
                
                long now = System.currentTimeMillis();
                currentUser.setVirtualCredit(dealerNewCredit);
                currentUser.setCreditUpdatedAt(now);
                currentUser.setUpdatedAt(now);
                database.userDao().updateUser(currentUser);
                
                agent.setVirtualCredit(agentNewCredit);
                agent.setCreditUpdatedAt(now);
                agent.setUpdatedAt(now);
                database.userDao().updateUser(agent);
                
                // Update current user reference
                currentUser = database.userDao().getUserById(currentUser.getUid());
                
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showSyncDialog();
                    loadAgents(); // Refresh the list
                });
            } catch (Exception e) {
                Log.e(TAG, "Error transferring credit", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void showSyncDialog() {
        boolean isOnline = isOnline();
        
        String message = isOnline ? 
                getString(R.string.sync_needed) : 
                getString(R.string.sync_needed_connect_internet);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.sync_required))
                .setMessage(message)
                .setCancelable(false);
        
        if (isOnline) {
            builder.setPositiveButton(getString(R.string.sync_now), (dialog, which) -> {
                dialog.dismiss();
                performSync();
            })
            .setNegativeButton(getString(R.string.sync_later), (dialog, which) -> {
                dialog.dismiss();
                // Monitor connectivity and show dialog when online
                monitorConnectivityAndSync();
            });
        } else {
            builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                dialog.dismiss();
                // Monitor connectivity and show dialog when online
                monitorConnectivityAndSync();
            });
        }
        
        builder.show();
    }
    
    private void monitorConnectivityAndSync() {
        // Simple polling approach - check every 5 seconds
        new Thread(() -> {
            while (!isOnline() && !isFinishing()) {
                try {
                    Thread.sleep(5000); // Check every 5 seconds
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            if (!isFinishing() && isOnline()) {
                runOnUiThread(this::showSyncDialog);
            }
        }).start();
    }
    
    private void performSync() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.syncing_data));
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        DataSyncService syncService = new DataSyncService(this);
        syncService.syncAllData(currentUser.getUid(), new DataSyncService.SyncCallback() {
            @Override
            public void onSyncStarted() {
                runOnUiThread(() -> {
                    progressDialog.setMessage(getString(R.string.starting_sync));
                });
            }
            
            @Override
            public void onSyncProgress(String message, int current, int total) {
                runOnUiThread(() -> {
                    progressDialog.setMessage(message + " (" + current + "/" + total + ")");
                });
            }
            
            @Override
            public void onSyncComplete(boolean success, String message) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (success) {
                        Toast.makeText(AddCreditsActivity.this, 
                                getString(R.string.sync_completed_successfully), 
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(AddCreditsActivity.this, 
                                getString(R.string.sync_failed) + ": " + message, 
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
    
    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Exception e) {
            Log.e(TAG, "Error checking connectivity", e);
            return false;
        }
    }
}

