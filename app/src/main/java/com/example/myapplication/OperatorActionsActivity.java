package com.example.myapplication;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapters.OperatorActionAdapter;
import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.OperatorActionEntity;
import com.example.myapplication.utils.SessionManager;
import com.example.myapplication.utils.SyncManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OperatorActionsActivity extends AppCompatActivity {
    public static final String EXTRA_OPERATOR_ID = "operatorId";
    private AppDatabase db; private SessionManager sm; private SyncManager sync;
    private String operatorId; private String uid;
    private RecyclerView recyclerView; private OperatorActionAdapter adapter;
    private List<OperatorActionEntity> items = new ArrayList<>();

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operator_actions);
        db = AppDatabase.getDatabase(this); sm = new SessionManager(this); sync = new SyncManager(this);
        operatorId = getIntent().getStringExtra(EXTRA_OPERATOR_ID);
        uid = sm.getUserFromSession() != null ? sm.getUserFromSession().getUid() : "";
        
        // Setup header
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(getString(R.string.operator_actions));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        recyclerView = findViewById(R.id.recyclerViewActions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OperatorActionAdapter(items, uid, this::onEdit, this::onDelete);
        recyclerView.setAdapter(adapter);
        Button btnAdd = findViewById(R.id.btnAddAction); btnAdd.setOnClickListener(v -> showAddEditDialog(null));
        load();
    }

    private void load() {
        new Thread(() -> {
            List<OperatorActionEntity> list = db.operatorActionDao().getByOperatorForUser(operatorId, uid);
            runOnUiThread(() -> { items.clear(); items.addAll(list); adapter.notifyDataSetChanged(); });
        }).start();
    }

    private void onEdit(OperatorActionEntity a) { showAddEditDialog(a); }
    private void onDelete(OperatorActionEntity a) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_action))
                .setMessage(getString(R.string.are_you_sure))
                .setPositiveButton(android.R.string.yes, (d,w) -> new Thread(() -> {
                    db.operatorActionDao().softDelete(a.getId(), System.currentTimeMillis());
                    runOnUiThread(() -> { load(); /* sync.syncOperatorActions(); */ });
                }).start())
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void showAddEditDialog(@Nullable OperatorActionEntity existing) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_action, null);
        Spinner spinnerName = v.findViewById(R.id.spinnerActionName);
        // Remove manual type field; type comes from operator
        EditText etCode = v.findViewById(R.id.etActionCode);
        CheckBox cbDisableUssd = v.findViewById(R.id.cbDisableUssd);
        
        // Setup action name spinner with Deposit/Withdrawal/Transfer
        String[] actionNames = new String[]{"Deposit", "Withdrawal", "Transfer"};
        android.widget.ArrayAdapter<String> nameAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, actionNames);
        nameAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerName.setAdapter(nameAdapter);
        
        if (existing != null) {
            // Set spinner selection based on existing name
            String existingName = existing.getName();
            if (existingName != null) {
                String lowerName = existingName.toLowerCase();
                if (lowerName.contains("withdraw")) {
                    spinnerName.setSelection(1); // Withdrawal
                } else if (lowerName.contains("transfer")) {
                    spinnerName.setSelection(2); // Transfer
                } else {
                    spinnerName.setSelection(0); // Deposit
                }
            } else {
                spinnerName.setSelection(0); // Default to Deposit
            }
            etCode.setText(existing.getActionCode());
            cbDisableUssd.setChecked(existing.isDisableUssd());
        }
        new AlertDialog.Builder(this)
                .setTitle(existing == null ? getString(R.string.add_action) : getString(R.string.edit_customer))
                .setView(v)
                .setPositiveButton(R.string.save_action, (d,w) -> {
                    String name = (String) spinnerName.getSelectedItem();
                    String actionCode = etCode.getText().toString().trim();
                    boolean disableUssd = cbDisableUssd.isChecked();
                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(actionCode)) {
                        Toast.makeText(this, R.string.required_fields_missing, Toast.LENGTH_SHORT).show(); return;
                    }
                    new Thread(() -> {
                        // Derive type from operator
                        String derivedType = "USSD";
                        try {
                            com.example.myapplication.database.entities.OperatorEntity op = db.operatorDao().getById(operatorId);
                            if (op != null && !TextUtils.isEmpty(op.getType())) derivedType = op.getType();
                        } catch (Exception ignore) {}
                        OperatorActionEntity a = existing != null ? existing : new OperatorActionEntity(UUID.randomUUID().toString(), operatorId, name, derivedType, null, null, uid);
                        a.setName(name); a.setType(derivedType); a.setAddedBy(uid);
                        a.setActionCode(actionCode);
                        a.setDisableUssd(disableUssd);
                        a.setUpdatedAt(System.currentTimeMillis()); a.setNeedsSync(true);
                        db.operatorActionDao().insertAction(a);
                        runOnUiThread(() -> { load(); /* sync.syncOperatorActions(); */ });
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

}


