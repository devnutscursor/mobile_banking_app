package com.example.myapplication;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapters.OperatorAdapter;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.OperatorEntity;
import com.example.myapplication.utils.LanguageManager;
import com.example.myapplication.utils.SessionManager;
import com.example.myapplication.utils.SyncManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class OperatorManagementActivity extends AppCompatActivity {
    private static final String TAG = "OperatorMgmt";
    private LanguageManager languageManager;
    private SessionManager sessionManager;
    private SyncManager syncManager;
    private AppDatabase database;

    private View btnAddOperator;
    private RecyclerView recyclerView; private OperatorAdapter adapter;

    private List<OperatorEntity> items = new ArrayList<>();
    private com.example.myapplication.database.entities.UserEntity currentUser;
    private long lastLanguageChangeTime = 0;

    @Override protected void attachBaseContext(android.content.Context newBase) {
        languageManager = LanguageManager.getInstance(newBase);
        String language = languageManager.getCurrentLanguage();
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operator_management);
        database = AppDatabase.getDatabase(this);
        sessionManager = new SessionManager(this);
        syncManager = new SyncManager(this);
        currentUser = sessionManager.getUserFromSession();
        initViews();
        loadOperators();
        // Removed sync prompt from Operator Management - only show on dashboards
        
        if (syncManager.isOnline()) {
            syncManager.downloadOperators();
            syncManager.downloadOperatorActions();
            // Removed auto-sync - user will be prompted instead
            // syncManager.syncOperators();
            // syncManager.syncOperatorActions();
        }
    }

    private void initViews() {
        // Header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.operator_management));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnAddOperator = findViewById(R.id.btnAddOperator);
        recyclerView = findViewById(R.id.recyclerViewOperators);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OperatorAdapter(items, currentUser != null ? currentUser.getUid() : "",
                this::onEdit, this::onDelete, this::onClick);
        recyclerView.setAdapter(adapter);
        if (currentUser != null) {
            // tvUserInfo is no longer part of this screen's design
            // tvUserInfo.setText(currentUser.getEmail() + " (" + currentUser.getRole() + ")");
        }
        btnAddOperator.setOnClickListener(v -> showAddEditDialog(null));
    }

    private void loadOperators() {
        new Thread(() -> {
            try {
                String uid = currentUser != null ? currentUser.getUid() : "";
                List<OperatorEntity> list = database.operatorDao().getByUser(uid);
                runOnUiThread(() -> { items.clear(); items.addAll(list); adapter.notifyDataSetChanged(); });
            } catch (Exception e) { Log.e(TAG, "load", e); }
        }).start();
    }

    private void onClick(OperatorEntity op) {
        android.content.Intent i = new android.content.Intent(this, OperatorActionsActivity.class);
        i.putExtra(OperatorActionsActivity.EXTRA_OPERATOR_ID, op.getId());
        startActivity(i);
    }

    private void onEdit(OperatorEntity op) { showAddEditDialog(op); }

    private void onDelete(OperatorEntity op) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_operator))
                .setMessage(getString(R.string.are_you_sure))
                .setPositiveButton(android.R.string.yes, (d,w) -> {
                    new Thread(() -> {
                        database.operatorDao().softDelete(op.getId(), System.currentTimeMillis());
                        runOnUiThread(() -> { loadOperators(); /* syncManager.syncOperators(); */ });
                    }).start();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void showAddEditDialog(@Nullable OperatorEntity existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_operator, null);
        EditText etName = view.findViewById(R.id.etOperatorName);
        EditText etCode = view.findViewById(R.id.etOperatorCode);
        EditText etTransferRate = view.findViewById(R.id.etTransferRate);
        Spinner spType = view.findViewById(R.id.spOperatorType);
        Spinner spColor = view.findViewById(R.id.spOperatorColor);
        android.widget.CheckBox cbEnabled = view.findViewById(R.id.cbEnabled);
        Button btnAddAction = view.findViewById(R.id.btnAddAction);
        // Populate spinner with types
        android.widget.ArrayAdapter<String> typeAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{getString(R.string.type_ussd), getString(R.string.type_traditional)});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(typeAdapter);
        // Colors list with visual swatch
        String[] colors = new String[]{"orange","purple","blue","green","amber","red","teal","indigo"};
        com.example.myapplication.adapters.ColorSpinnerAdapter colorAdapter =
                new com.example.myapplication.adapters.ColorSpinnerAdapter(this, colors);
        spColor.setAdapter(colorAdapter);

        if (existing != null) {
            etName.setText(existing.getName());
            if (existing.getCode() != null) etCode.setText(existing.getCode());
            etTransferRate.setText(String.format(java.util.Locale.US, "%.2f", existing.getTransferRate()));
            // simple spinner: 0 USSD, 1 Traditional
            spType.setSelection("USSD".equalsIgnoreCase(existing.getType()) ? 0 : 1);
            cbEnabled.setChecked(existing.isEnabled());
            // set color selection
            String c = existing.getColor() == null ? "orange" : existing.getColor();
            int idx = java.util.Arrays.asList(colors).indexOf(c);
            spColor.setSelection(idx >= 0 ? idx : 0);
            // Show Add Action button for existing operators
            btnAddAction.setVisibility(View.VISIBLE);
            btnAddAction.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, OperatorActionsActivity.class);
                intent.putExtra(OperatorActionsActivity.EXTRA_OPERATOR_ID, existing.getId());
                startActivity(intent);
            });
        }
        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton(R.string.save_operator, (d,w) -> {
                    String name = etName.getText().toString().trim();
                    String type = spType.getSelectedItemPosition() == 0 ? "USSD" : "TRADITIONAL";
                    String code = etCode.getText().toString().trim();
                    String color = (String) spColor.getSelectedItem();
                    boolean enabled = cbEnabled.isChecked();
                    String transferRateStr = etTransferRate.getText().toString().trim();
                    final double transferRate;
                    try {
                        if (!TextUtils.isEmpty(transferRateStr)) {
                            transferRate = Double.parseDouble(transferRateStr);
                        } else {
                            transferRate = 0.0;
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid transfer rate", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(code)) { 
                        Toast.makeText(this, R.string.required_fields_missing, Toast.LENGTH_SHORT).show(); return; 
                    }
                    new Thread(() -> {
                        OperatorEntity op = existing != null ? existing : new OperatorEntity(UUID.randomUUID().toString(), name, type, enabled, currentUser.getUid());
                        op.setName(name); op.setType(type); op.setEnabled(enabled); op.setAddedBy(currentUser.getUid());
                        op.setCode(code);
                        op.setColor(color);
                        op.setTransferRate(transferRate);
                        op.setUpdatedAt(System.currentTimeMillis()); op.setNeedsSync(true);
                        database.operatorDao().insertOperator(op);
                        runOnUiThread(() -> { loadOperators(); /* syncManager.syncOperators(); */ });
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}


