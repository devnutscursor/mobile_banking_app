package com.example.myapplication.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
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
    private static final String KEY_LAST_SYNC_COMPLETE = "last_sync_complete_time";
    private static final long PROMPT_RETRY_INTERVAL = 15 * 60 * 1000; // 15 minutes
    private static final long SYNC_PROMPT_COOLDOWN_MS = 3 * 60 * 1000; // 3 minutes after sync

    private static volatile boolean globalSyncInProgress = false;
    private static volatile boolean globalPromptShowing = false;

    private final Activity activity;
    private final Context appContext;
    private final SessionManager sessionManager;
    private final ScheduledExecutorService scheduler;

    public SyncPromptManager(Context context) {
        if (context instanceof Activity) {
            this.activity = (Activity) context;
        } else {
            this.activity = null;
        }
        this.appContext = context.getApplicationContext();
        this.sessionManager = new SessionManager(context);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public static boolean isSyncInProgress() {
        return globalSyncInProgress;
    }

    public static void markSyncStarted() {
        globalSyncInProgress = true;
    }

    public static void markSyncCompleted(Context context) {
        globalSyncInProgress = false;
        if (context != null) {
            context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_LAST_SYNC_COMPLETE, System.currentTimeMillis())
                    .putBoolean(KEY_USER_DECLINED, false)
                    .apply();
        }
    }

    public static void markSyncFailed() {
        globalSyncInProgress = false;
    }

    private boolean canShowUi() {
        if (activity == null) {
            return false;
        }
        if (activity.isFinishing()) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed();
    }

    private boolean shouldSuppressPrompt() {
        if (globalSyncInProgress || globalPromptShowing) {
            return true;
        }
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastSyncComplete = prefs.getLong(KEY_LAST_SYNC_COMPLETE, 0);
        return System.currentTimeMillis() - lastSyncComplete < SYNC_PROMPT_COOLDOWN_MS;
    }

    public void checkAndPromptSync() {
        if (!canShowUi()) {
            Log.d(TAG, "No valid activity for sync prompt");
            return;
        }
        if (!isOnline()) {
            Log.d(TAG, "No internet connection, skipping sync prompt");
            return;
        }
        if (shouldSuppressPrompt()) {
            Log.d(TAG, "Sync in progress, prompt showing, or post-sync cooldown - skipping");
            return;
        }
        if (shouldSkipPrompt()) {
            Log.d(TAG, "User recently declined sync, skipping prompt");
            return;
        }
        if (!hasDataToSync()) {
            Log.d(TAG, "No data to sync, skipping prompt");
            return;
        }
        postShowSyncPrompt();
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Exception e) {
            Log.e(TAG, "Error checking connectivity", e);
            return false;
        }
    }

    private boolean shouldSkipPrompt() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean userDeclined = prefs.getBoolean(KEY_USER_DECLINED, false);
        long lastPromptTime = prefs.getLong(KEY_LAST_PROMPT_TIME, 0);
        long currentTime = System.currentTimeMillis();
        return userDeclined && (currentTime - lastPromptTime) < PROMPT_RETRY_INTERVAL;
    }

    private boolean hasDataToSync() {
        try {
            com.example.myapplication.database.AppDatabase database =
                    com.example.myapplication.database.AppDatabase.getDatabase(appContext);

            if (!database.customerDao().getCustomersNeedingSync().isEmpty()) {
                return true;
            }
            if (!database.operatorDao().getNeedingSync().isEmpty()) {
                return true;
            }
            if (!database.operatorActionDao().getNeedingSync().isEmpty()) {
                return true;
            }

            com.example.myapplication.database.entities.UserEntity currentUser = sessionManager.getCurrentUser();
            if (currentUser != null) {
                java.util.List<com.example.myapplication.database.entities.TransactionEntity> pendingTransactions =
                        database.transactionDao().getTransactionsByUser(currentUser.getUid());
                for (com.example.myapplication.database.entities.TransactionEntity tx : pendingTransactions) {
                    if (tx.isNeedsSync()) {
                        return true;
                    }
                }
                if (!database.operatorBalanceDao().getNeedingSyncForUser(currentUser.getUid()).isEmpty()) {
                    return true;
                }
                currentUser = database.userDao().getUserById(currentUser.getUid());
                if (currentUser != null && currentUser.getUpdatedAt() > currentUser.getLastSyncAt()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking for data to sync", e);
            return false;
        }
    }

    private void postShowSyncPrompt() {
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (activity.getWindow() != null && activity.getWindow().getDecorView() != null) {
                activity.getWindow().getDecorView().post(this::showSyncPrompt);
            }
        });
    }

    private void showSyncPrompt() {
        if (!canShowUi() || globalPromptShowing || shouldSuppressPrompt()) {
            return;
        }

        globalPromptShowing = true;

        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis()).apply();

        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(activity.getString(com.example.myapplication.R.string.sync_required))
                    .setMessage(activity.getString(com.example.myapplication.R.string.data_needs_sync))
                    .setPositiveButton(activity.getString(com.example.myapplication.R.string.sync_now), (dialog, which) -> {
                        globalPromptShowing = false;
                        performSync();
                    })
                    .setNegativeButton(activity.getString(com.example.myapplication.R.string.sync_later), (dialog, which) -> {
                        globalPromptShowing = false;
                        recordUserDeclined();
                        scheduleRetryPrompt();
                    })
                    .setOnDismissListener(dialog -> globalPromptShowing = false);

            AlertDialog dialog = builder.create();
            dialog.show();
        } catch (Exception e) {
            globalPromptShowing = false;
            Log.e(TAG, "Failed to show sync prompt", e);
        }
    }

    private void recordUserDeclined() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_USER_DECLINED, true)
                .apply();
    }

    private void scheduleRetryPrompt() {
        scheduler.schedule(this::checkAndPromptSync, 15, TimeUnit.MINUTES);
    }

    private void performSync() {
        if (!canShowUi()) {
            markSyncFailed();
            return;
        }

        com.example.myapplication.database.entities.UserEntity currentUser = sessionManager.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(appContext, "No active user", Toast.LENGTH_SHORT).show();
            return;
        }

        markSyncStarted();

        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(activity);
        progressDialog.setMessage(activity.getString(com.example.myapplication.R.string.syncing_data));
        progressDialog.setCancelable(false);
        try {
            progressDialog.show();
        } catch (Exception e) {
            markSyncFailed();
            Log.e(TAG, "Failed to show sync progress dialog", e);
            return;
        }

        DataSyncService syncService = new DataSyncService(appContext);
        syncService.syncAllData(currentUser.getUid(), new DataSyncService.SyncCallback() {
            @Override
            public void onSyncStarted() {
                activity.runOnUiThread(() -> progressDialog.setMessage("Starting sync..."));
            }

            @Override
            public void onSyncProgress(String message, int current, int total) {
                activity.runOnUiThread(() ->
                        progressDialog.setMessage(message + " (" + current + "/" + total + ")"));
            }

            @Override
            public void onSyncComplete(boolean success, String message) {
                if (success) {
                    markSyncCompleted(appContext);
                } else {
                    markSyncFailed();
                }

                activity.runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    if (success) {
                        Toast.makeText(appContext, appContext.getString(com.example.myapplication.R.string.sync_completed_successfully), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    public void onInternetAvailable() {
        checkAndPromptSync();
    }

    public void onManualSync() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_USER_DECLINED, false)
                .apply();
        markSyncStarted();
    }

    public void maybePromptOnDashboardResume() {
        if (!canShowUi()) {
            return;
        }
        if (shouldSuppressPrompt()) {
            Log.d(TAG, "Suppressing sync prompt on resume");
            return;
        }
        if (isOnline() && hasDataToSync() && !shouldSkipPrompt()) {
            postShowSyncPrompt();
        }
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
