package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.utils.SessionManager;
import com.example.myapplication.utils.TermsAndPrivacyManager;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2000; // 2 seconds
    private SessionManager sessionManager;
    private TermsAndPrivacyManager termsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        sessionManager = new SessionManager(this);
        termsManager = TermsAndPrivacyManager.getInstance(this);

        // Temporary: run extended seeder exactly once (debug-safe).
        // This updates existing Firestore data idempotently (no deletes),
        // assigns dealer→agents, creates missing licenses, and backfills mappings
        // used by dashboard counts. Remove after it runs successfully once.
        //maybeRunSeederExtendedOnce();
        
        // Temporary: run agent2 seeder exactly once (debug-safe).
        // This creates agent2@test.com with password "agent2123" in Firebase Auth
        // and creates the corresponding Firestore document. Remove after it runs successfully once.
        //maybeRunAgent2SeederOnce();

        // Delay splash screen
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            checkTermsAndNavigate();
        }, SPLASH_DELAY);
    }

    private void maybeRunSeederExtendedOnce() {
        android.content.SharedPreferences sp = getSharedPreferences("seeders", MODE_PRIVATE);
        boolean done = sp.getBoolean("extended_v1", false);
        if (!done) {
            try {
                com.example.myapplication.utils.DatabaseSeederExtended.runOnce();
                sp.edit().putBoolean("extended_v1", true).apply();
            } catch (Exception e) {
                android.util.Log.e("SplashActivity", "Error running DatabaseSeederExtended: " + e.getMessage());
                // Continue without crashing
            }
        }
    }

    private void maybeRunAgent2SeederOnce() {
        android.content.SharedPreferences sp = getSharedPreferences("seeders", MODE_PRIVATE);
        boolean done = sp.getBoolean("agent2_v1", false);
        if (!done) {
            try {
                com.example.myapplication.utils.Agent2Seeder.runOnce();
                
                // Store credentials for agent2 in local database
                sessionManager.storeCredentials("agent2@test.com", "agent2123", "agent2-uid-placeholder");
                
                sp.edit().putBoolean("agent2_v1", true).apply();
            } catch (Exception e) {
                android.util.Log.e("SplashActivity", "Error running Agent2Seeder: " + e.getMessage());
                // Continue without crashing
            }
        }
    }

    /**
     * Check if terms are accepted, show dialog if not, then proceed with navigation
     */
    private void checkTermsAndNavigate() {
        if (termsManager.areTermsAccepted()) {
            // Terms already accepted, proceed with normal navigation
            checkUserAndNavigate();
        } else {
            // Terms not accepted, show dialog
            showTermsAndPrivacyDialog();
        }
    }
    
    /**
     * Show terms and privacy policy dialog
     * User must accept to continue using the app
     */
    private void showTermsAndPrivacyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_terms_privacy, null);
        builder.setView(dialogView);
        
        // Make dialog non-cancelable (user must accept or decline)
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        
        // Get views
        CheckBox cbAcceptTerms = dialogView.findViewById(R.id.cbAcceptTerms);
        Button btnAccept = dialogView.findViewById(R.id.btnAccept);
        Button btnDecline = dialogView.findViewById(R.id.btnDecline);
        
        // Accept button click
        btnAccept.setOnClickListener(v -> {
            if (!cbAcceptTerms.isChecked()) {
                Toast.makeText(this, getString(R.string.must_accept_terms), Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Mark terms as accepted
            termsManager.acceptTerms();
            dialog.dismiss();
            // Proceed with normal navigation
            checkUserAndNavigate();
        });
        
        // Decline button click - exit app
        btnDecline.setOnClickListener(v -> {
            // User declined, exit the application
            finishAffinity(); // Close all activities
            System.exit(0); // Exit the app
        });
        
        dialog.show();
    }
    
    /**
     * Check user login status and navigate accordingly
     */
    private void checkUserAndNavigate() {
        if (sessionManager.isLoggedIn()) {
            // User is logged in, navigate to appropriate dashboard
            navigateToDashboard();
        } else {
            // User is not logged in, go to login activity
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }
        
        finish(); // Close splash activity
    }
    
    private void navigateToDashboard() {
        // Get user role and navigate to appropriate dashboard
        com.example.myapplication.database.entities.UserEntity currentUser = sessionManager.getCurrentUser();
        
        Intent intent;
        if (currentUser != null && "dealer".equalsIgnoreCase(currentUser.getRole())) {
            intent = new Intent(this, DealerDashboardActivity.class);
        } else if (currentUser != null && "agent".equalsIgnoreCase(currentUser.getRole())) {
            intent = new Intent(this, AgentDashboardActivity.class);
        } else {
            // Default to login if role is unknown or user is null
            intent = new Intent(this, LoginActivity.class);
        }
        
        startActivity(intent);
    }
}




