package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.entities.License;
import com.example.myapplication.entities.User;
import com.example.myapplication.utils.AuthManager;
import com.example.myapplication.utils.DatabaseSeeder;
import com.example.myapplication.utils.LicenseManager;
import com.example.myapplication.utils.SessionManager;

public class MainActivity extends AppCompatActivity {

    private Button btnOnlineLogin, btnOfflineLogin, btnTestFirestore, btnLogout;
    private TextView tvStatus, tvLog, tvUserInfo;
    private AuthManager authManager;
    private LicenseManager licenseManager;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        authManager = AuthManager.getInstance(this);
        licenseManager = LicenseManager.getInstance(this);
        sessionManager = new SessionManager(this);

        // Seed test data (only once). IMPORTANT: keep disabled for normal use/offline.
        // DatabaseSeeder.seedTestData();

        btnOnlineLogin = findViewById(R.id.btnLogin);
        btnOfflineLogin = findViewById(R.id.btnOfflineLogin);
        btnTestFirestore = findViewById(R.id.btnTestFirestore);
        btnLogout = findViewById(R.id.btnLogout);
        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        tvUserInfo = findViewById(R.id.tvUserInfo);

        btnOnlineLogin.setOnClickListener(v -> {
            // If user can work offline, resume session, otherwise go to login
            if (sessionManager.canWorkOffline()) {
                startOfflineSession();
            } else {
                Intent intent = new Intent(this, LoginActivity.class);
                startActivity(intent);
            }
        });

        btnOfflineLogin.setOnClickListener(v -> startOfflineSession());

        btnTestFirestore.setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                Intent intent = new Intent(this, FirestoreTestActivity.class);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            }
        });

        btnLogout.setOnClickListener(v -> showLogoutDialog());

        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Only auto-proceed if user is currently logged in
        if (sessionManager.isLoggedIn()) {
            updateUI();
            return;
        }

        // Show login UI - let user choose online/offline login
        updateUI();
    }

    private void updateUI() {
        if (sessionManager.isLoggedIn()) {
            showAuthenticatedUI();
        } else {
            showLoginUI();
        }
    }

    private void showLoginUI() {
        tvStatus.setText("Status: Not logged in");
        tvUserInfo.setText("");
        
        // Check if user can work offline after logout
        if (sessionManager.canWorkOffline()) {
            btnOnlineLogin.setVisibility(View.VISIBLE);
            btnOnlineLogin.setText("Resume Session");
            btnOfflineLogin.setVisibility(View.GONE);
            addLog("Session locked - tap Resume to continue");
        } else {
            btnOnlineLogin.setVisibility(View.VISIBLE);
            btnOnlineLogin.setText("Online Login (Internet Required)");
            btnOfflineLogin.setVisibility(View.GONE);
            addLog("First-time login required");
        }
        
        btnTestFirestore.setVisibility(View.GONE);
        btnLogout.setVisibility(View.GONE);
    }

    private void showAuthenticatedUI() {
        // Try to get user from current session (works in both online and offline states)
        UserEntity user = sessionManager.isLoggedIn() ? 
            sessionManager.getCurrentUser() : sessionManager.getUserFromSession();
            
        if (user != null) {
            tvStatus.setText("Status: Logged in as " + user.getEmail());
            tvUserInfo.setText("Role: " + user.getRole().toUpperCase() + 
                             "\nName: " + user.getName() +
                             "\nMode: " + (sessionManager.needsOnlineSync() ? "Needs Sync" : "Up to date"));
            
            btnOnlineLogin.setVisibility(View.GONE);
            btnOfflineLogin.setVisibility(View.GONE);
            btnTestFirestore.setVisibility(View.VISIBLE);
            btnLogout.setVisibility(View.VISIBLE);
            
            addLog("Authenticated as: " + user.getEmail());
            
            // Navigate to dashboard immediately to avoid flickering
            navigateToRoleDashboard(user);
        }
    }

    private void startOfflineSession() {
        addLog("Attempting offline login...");
        tvStatus.setText("Status: Starting offline session...");
        
        if (sessionManager.startOfflineSession()) {
            addLog("Offline session started successfully");
            showAuthenticatedUI();
        } else {
            addLog("Offline login failed - need internet validation");
            tvStatus.setText("Status: Offline login failed");
            Toast.makeText(this, "Cannot work offline. Please login online first.", Toast.LENGTH_LONG).show();
        }
    }

    private void navigateToRoleDashboard(UserEntity user) {
        Intent intent;
        if ("dealer".equals(user.getRole())) {
            addLog("Navigating to Dealer Dashboard");
            intent = new Intent(this, DealerDashboardActivity.class);
        } else if ("agent".equals(user.getRole())) {
            addLog("Navigating to Agent Dashboard");
            intent = new Intent(this, AgentDashboardActivity.class);
        } else {
            addLog("Unknown role: " + user.getRole());
            Toast.makeText(this, "Unknown user role: " + user.getRole(), Toast.LENGTH_LONG).show();
            return;
        }
        
        startActivity(intent);
        finish(); // Close MainActivity
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (sessionManager.isLoggedIn()) {
            getMenuInflater().inflate(R.menu.main_menu, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_lock) {
            lockSession();
            return true;
        } else if (id == R.id.action_logout) {
            showLogoutDialog();
            return true;
        } else if (id == R.id.action_sync) {
            // TODO: Implement background sync
            Toast.makeText(this, "Sync feature coming soon", Toast.LENGTH_SHORT).show();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void lockSession() {
        sessionManager.lockSession();
        addLog("Session locked");
        updateUI();
        Toast.makeText(this, "Session locked. Use 'Continue Offline' to resume.", Toast.LENGTH_SHORT).show();
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Logout Options")
            .setMessage("Choose logout type:")
            .setPositiveButton("Lock Session", (dialog, which) -> lockSession())
            .setNegativeButton("Full Logout", (dialog, which) -> fullLogout())
            .setNeutralButton("Cancel", null)
            .show();
    }

    private void fullLogout() {
        try {
            // Only clear session data, don't call Firebase logout to avoid errors
            sessionManager.fullLogout();
            // Also clear any globally cached license to avoid cross-user reuse
            com.example.myapplication.utils.LicenseManager.getInstance(this).clearLicense();
            
            addLog("Full logout completed");
            updateUI();
            Toast.makeText(this, "Logged out completely. Internet required for next login.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            addLog("Logout error: " + e.getMessage());
            Toast.makeText(this, "Logout completed with minor issues", Toast.LENGTH_SHORT).show();
            updateUI();
        }
    }

    private void addLog(String message) {
        String currentLog = tvLog.getText().toString();
        String newLog = currentLog + "\n• " + message;
        tvLog.setText(newLog);
        
        // Auto-scroll to bottom (optional)
        tvLog.post(() -> {
            if (tvLog.getLineCount() > 10) {
                int scrollAmount = tvLog.getLayout().getLineTop(tvLog.getLineCount()) - tvLog.getHeight();
                if (scrollAmount > 0) {
                    tvLog.scrollTo(0, scrollAmount);
                }
            }
        });
    }
}