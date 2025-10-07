package com.example.myapplication;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.utils.LanguageManager;

public class App extends Application {
    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application onCreate: Initializing Firebase and databases.");

        // Initialize Firebase (if not already done by FirebaseAppProvider)
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
        }

        // Enable Firestore offline persistence
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build();
        FirebaseFirestore.getInstance().setFirestoreSettings(settings);

        Log.d(TAG, "Firestore offline persistence enabled.");
        
        // Initialize Room Database
        AppDatabase.getDatabase(this);
        Log.d(TAG, "Room database initialized.");
        
        // Initialize Language Manager and apply saved language
        LanguageManager.getInstance(this).applySavedLanguage();
        Log.d(TAG, "Language manager initialized.");
    }
}