package com.example.myapplication.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class LanguageManager {
    private static final String PREFS_NAME = "language_prefs";
    private static final String KEY_LANGUAGE = "selected_language";
    
    public static final String ENGLISH = "en";
    public static final String FRENCH = "fr";
    
    private static LanguageManager instance;
    private Context context;
    private SharedPreferences prefs;
    
    private LanguageManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static LanguageManager getInstance(Context context) {
        if (instance == null) {
            instance = new LanguageManager(context);
        }
        return instance;
    }
    
    /**
     * Get current selected language
     */
    public String getCurrentLanguage() {
        return prefs.getString(KEY_LANGUAGE, ENGLISH); // Default to English
    }
    
    /**
     * Set language preference
     */
    public void setLanguage(String languageCode) {
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
        updateAppLanguage(languageCode);
    }
    
    /**
     * Set language preference (simplified - no ML Kit needed)
     */
    public CompletableFuture<Boolean> setLanguageWithTranslation(String languageCode) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // No download needed - just set the language
        setLanguage(languageCode);
        future.complete(true);
        
        return future;
    }
    
    /**
     * Update app language
     */
    public void updateAppLanguage(String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        
        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
        
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
    
    /**
     * Update language for specific context (activity)
     */
    public void updateLanguageForContext(Context activityContext, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        
        Resources resources = activityContext.getResources();
        Configuration config = resources.getConfiguration();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
        
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
    
    /**
     * Apply saved language on app start
     */
    public void applySavedLanguage() {
        String savedLanguage = getCurrentLanguage();
        updateAppLanguage(savedLanguage);
    }
    
    /**
     * Get language flag emoji
     */
    public String getLanguageFlag(String languageCode) {
        switch (languageCode) {
            case ENGLISH:
                return "🇺🇸";
            case FRENCH:
                return "🇫🇷";
            default:
                return "🇺🇸";
        }
    }
    
    /**
     * Get available languages
     */
    public String[] getAvailableLanguages() {
        return new String[]{ENGLISH, FRENCH};
    }
    
    /**
     * Get language display name
     */
    public String getLanguageDisplayName(String languageCode) {
        switch (languageCode) {
            case ENGLISH:
                return "English";
            case FRENCH:
                return "Français";
            default:
                return "English";
        }
    }
    
    /**
     * Check if language model is downloaded (always true for string resources)
     */
    public boolean isLanguageModelDownloaded(String languageCode) {
        return true; // String resources are always "downloaded"
    }
}
