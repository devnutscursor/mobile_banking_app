package com.example.myapplication.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages terms of use and privacy policy acceptance status.
 * This is a one-time acceptance that persists across app sessions.
 */
public class TermsAndPrivacyManager {
    private static final String PREFS_NAME = "terms_privacy_prefs";
    private static final String KEY_TERMS_ACCEPTED = "terms_accepted";
    
    private static TermsAndPrivacyManager instance;
    private SharedPreferences prefs;
    
    private TermsAndPrivacyManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized TermsAndPrivacyManager getInstance(Context context) {
        if (instance == null) {
            instance = new TermsAndPrivacyManager(context);
        }
        return instance;
    }
    
    /**
     * Check if terms and privacy policy have been accepted
     */
    public boolean areTermsAccepted() {
        return prefs.getBoolean(KEY_TERMS_ACCEPTED, false);
    }
    
    /**
     * Mark terms and privacy policy as accepted
     */
    public void acceptTerms() {
        prefs.edit().putBoolean(KEY_TERMS_ACCEPTED, true).apply();
    }
    
    /**
     * Reset terms acceptance (for testing purposes)
     */
    public void resetTermsAcceptance() {
        prefs.edit().putBoolean(KEY_TERMS_ACCEPTED, false).apply();
    }
}

