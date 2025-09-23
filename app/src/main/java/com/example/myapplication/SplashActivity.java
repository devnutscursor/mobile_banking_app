package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2000; // 2 seconds
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        sessionManager = new SessionManager(this);

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
            checkUserAndNavigate();
        }, SPLASH_DELAY);
    }

    private void maybeRunSeederExtendedOnce() {
        android.content.SharedPreferences sp = getSharedPreferences("seeders", MODE_PRIVATE);
        boolean done = sp.getBoolean("extended_v1", false);
        if (!done) {
            com.example.myapplication.utils.DatabaseSeederExtended.runOnce();
            sp.edit().putBoolean("extended_v1", true).apply();
        }
    }

    private void maybeRunAgent2SeederOnce() {
        android.content.SharedPreferences sp = getSharedPreferences("seeders", MODE_PRIVATE);
        boolean done = sp.getBoolean("agent2_v1", false);
        if (!done) {
            com.example.myapplication.utils.Agent2Seeder.runOnce();
            sp.edit().putBoolean("agent2_v1", true).apply();
        }
    }

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




