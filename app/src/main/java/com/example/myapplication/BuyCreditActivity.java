package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.UserEntity;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.SessionManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class BuyCreditActivity extends AppCompatActivity {
    private static final String TAG = "BuyCreditActivity";
    
    private TextView tvDealerName;
    private TextView tvDealerEmail;
    private TextView tvDealerPhone;
    private TextView tvDealerCredit;
    private Button btnContactDealer;
    
    private SessionManager sessionManager;
    private AppDatabase database;
    private FirebaseFirestore firestore;
    private UserEntity currentUser;
    private UserEntity dealer;
    
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
        setContentView(R.layout.activity_buy_credit);
        
        sessionManager = new SessionManager(this);
        database = AppDatabase.getDatabase(this);
        firestore = FirebaseFirestore.getInstance();
        currentUser = sessionManager.getCurrentUser();
        
        initViews();
        loadDealerInfo();
    }
    
    private void initViews() {
        // Setup header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.buy_credit));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        tvDealerName = findViewById(R.id.tvDealerName);
        tvDealerEmail = findViewById(R.id.tvDealerEmail);
        tvDealerPhone = findViewById(R.id.tvDealerPhone);
        tvDealerCredit = findViewById(R.id.tvDealerCredit);
        btnContactDealer = findViewById(R.id.btnContactDealer);
        
        btnContactDealer.setOnClickListener(v -> {
            if (dealer != null && dealer.getPhone() != null && !dealer.getPhone().isEmpty()) {
                contactDealer(dealer.getPhone());
            } else {
                Toast.makeText(this, getString(R.string.dealer_phone_not_available), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void loadDealerInfo() {
        if (currentUser == null) {
            Toast.makeText(this, "No active user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        new Thread(() -> {
            try {
                // Get all users and find dealers
                List<UserEntity> allUsers = database.userDao().getAllUsers();
                UserEntity foundDealer = null;
                
                for (UserEntity user : allUsers) {
                    if ("dealer".equalsIgnoreCase(user.getRole())) {
                        foundDealer = user;
                        break; // Get first dealer
                    }
                }
                
                if (foundDealer != null) {
                    dealer = foundDealer;
                    runOnUiThread(this::displayDealerInfo);
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, getString(R.string.no_dealer_found), Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading dealer info", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading dealer info: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }
    
    private void displayDealerInfo() {
        if (dealer == null) return;
        
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());
        
        tvDealerName.setText(dealer.getName() != null ? dealer.getName() : "N/A");
        tvDealerEmail.setText(dealer.getEmail() != null ? dealer.getEmail() : "N/A");
        tvDealerPhone.setText(dealer.getPhone() != null ? dealer.getPhone() : "N/A");
        tvDealerCredit.setText(currencyFormat.format(dealer.getVirtualCredit()));
    }
    
    private void contactDealer(String phoneNumber) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phoneNumber));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening dialer", e);
            Toast.makeText(this, "Error opening dialer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

