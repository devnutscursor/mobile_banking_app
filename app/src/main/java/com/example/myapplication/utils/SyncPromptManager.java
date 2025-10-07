package com.example.myapplication.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.widget.Toast;

import com.example.myapplication.services.DataSyncService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages sync prompts instead of automatic syncing
 * Shows dialog when internet connects, retries every 15 minutes if declined
 */
public class SyncPromptManager {
    private static final String TAG = "SyncPromptManager";
    private static final String PREFS_NAME = "sync_prompt_prefs";
    private static final String KEY_LAST_PROMPT_TIME = "last_prompt_time";
    private static final String KEY_USER_DECLINED = "user_declined_sync";
    private static final long PROMPT_RETRY_INTERVAL = 15 * 60 * 1000; // 15 minutes
    
    private final Context context;
    private final SessionManager sessionManager;
    private final ScheduledExecutorService scheduler;
    private boolean isPromptShowing = false;
    
    public SyncPromptManager(Context context) {
        this.context = context;
        this.sessionManager = new SessionManager(context);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }
    
    /**
     * Check if internet is available and show sync prompt if needed
     */
    public void checkAndPromptSync() {
        if (!isOnline()) {
            Log.d(TAG, "No internet connection, skipping sync prompt");
            return;
        }
        
        if (isPromptShowing) {
            Log.d(TAG, "Sync prompt already showing, skipping");
            return;
        }
        
        // Check if user recently declined and it's not time to retry yet
        if (shouldSkipPrompt()) {
            Log.d(TAG, "User recently declined sync, skipping prompt");
            return;
        }
        
        // Check if there's actually data to sync
        if (!hasDataToSync()) {
            Log.d(TAG, "No data to sync, skipping prompt");
            return;
        }
        
        showSyncPrompt();
    }
    
    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Exception e) {
            Log.e(TAG, "Error checking connectivity", e);
            return false;
        }
    }
    
    private boolean shouldSkipPrompt() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean userDeclined = prefs.getBoolean(KEY_USER_DECLINED, false);
        long lastPromptTime = prefs.getLong(KEY_LAST_PROMPT_TIME, 0);
        long currentTime = System.currentTimeMillis();
        
        if (userDeclined && (currentTime - lastPromptTime) < PROMPT_RETRY_INTERVAL) {
            return true;
        }
        
        return false;
    }
    
    private boolean hasDataToSync() {
        try {
            // Check if there are any pending sync items
            com.example.myapplication.database.AppDatabase database = 
                com.example.myapplication.database.AppDatabase.getDatabase(context);
            
            // Check for pending customers
            if (!database.customerDao().getCustomersNeedingSync().isEmpty()) {
                return true;
            }
            
            // Check for pending operators
            if (!database.operatorDao().getNeedingSync().isEmpty()) {
                return true;
            }
            
            // Check for pending operator actions
            if (!database.operatorActionDao().getNeedingSync().isEmpty()) {
                return true;
            }
            
            // Check for pending transactions
            com.example.myapplication.database.entities.UserEntity currentUser = sessionManager.getCurrentUser();
            if (currentUser != null) {
                java.util.List<com.example.myapplication.database.entities.TransactionEntity> pendingTransactions = 
                    database.transactionDao().getTransactionsByUser(currentUser.getUid());
                for (com.example.myapplication.database.entities.TransactionEntity tx : pendingTransactions) {
                    if (tx.isNeedsSync()) {
                        return true;
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking for data to sync", e);
            return false;
        }
    }
    
    private void showSyncPrompt() {
        isPromptShowing = true;
        
        // Record that we're showing a prompt
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis()).apply();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(com.example.myapplication.R.string.sync_required))
                .setMessage(context.getString(com.example.myapplication.R.string.data_needs_sync))
                .setPositiveButton(context.getString(com.example.myapplication.R.string.sync_now), (dialog, which) -> {
                    dialog.dismiss();
                    isPromptShowing = false;
                    performSync();
                })
                .setNegativeButton(context.getString(com.example.myapplication.R.string.sync_later), (dialog, which) -> {
                    dialog.dismiss();
                    isPromptShowing = false;
                    recordUserDeclined();
                    scheduleRetryPrompt();
                })
                .setOnDismissListener(dialog -> {
                    isPromptShowing = false;
                });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void recordUserDeclined() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_USER_DECLINED, true).apply();
    }
    
    private void scheduleRetryPrompt() {
        scheduler.schedule(() -> {
            if (context != null) {
                checkAndPromptSync();
            }
        }, 15, TimeUnit.MINUTES);
    }
    
    private void performSync() {
        com.example.myapplication.database.entities.UserEntity currentUser = sessionManager.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(context, "No active user", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(context);
        progressDialog.setMessage(context.getString(com.example.myapplication.R.string.syncing_data));
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        DataSyncService syncService = new DataSyncService(context);
        syncService.syncAllData(currentUser.getUid(), new DataSyncService.SyncCallback() {
            @Override
            public void onSyncStarted() {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        progressDialog.setMessage("Starting sync...");
                    });
                }
            }
            
            @Override
            public void onSyncProgress(String message, int current, int total) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        progressDialog.setMessage(message + " (" + current + "/" + total + ")");
                    });
                }
            }
            
            @Override
            public void onSyncComplete(boolean success, String message) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        progressDialog.dismiss();
                        if (success) {
                            Toast.makeText(context, "Sync completed successfully", Toast.LENGTH_SHORT).show();
                            // Clear declined flag since user accepted sync
                            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                            prefs.edit().putBoolean(KEY_USER_DECLINED, false).apply();
                        } else {
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }
    
    /**
     * Call this when app starts or when internet becomes available
     */
    public void onInternetAvailable() {
        // Only prompt on dashboard screens; caller should control when to call this.
        checkAndPromptSync();
    }
    
    /**
     * Call this when user manually triggers sync (clears declined flag)
     */
    public void onManualSync() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_USER_DECLINED, false).apply();
    }

    /**
     * Call this on dashboard resume to decide whether to prompt.
     * Only prompts if online, data needs sync, and either never prompted or 15 minutes elapsed.
     */
    public void maybePromptOnDashboardResume() {
        if (isOnline() && hasDataToSync() && !shouldSkipPrompt()) {
            showSyncPrompt();
        }
    }
    
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
