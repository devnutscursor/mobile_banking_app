package com.example.myapplication;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
                    runOnUiThread(() -> { load(); sync.syncOperatorActions(); });
                }).start())
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void showAddEditDialog(@Nullable OperatorActionEntity existing) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_action, null);
        EditText etName = v.findViewById(R.id.etActionName);
        EditText etType = v.findViewById(R.id.etActionType);
        EditText etCode = v.findViewById(R.id.etActionCode);
        if (existing != null) {
            etName.setText(existing.getName()); 
            etType.setText(existing.getType());
            etCode.setText(existing.getActionCode());
        }
        new AlertDialog.Builder(this)
                .setTitle(existing == null ? getString(R.string.add_action) : getString(R.string.edit_customer))
                .setView(v)
                .setPositiveButton(R.string.save_action, (d,w) -> {
                    String name = etName.getText().toString().trim();
                    String type = etType.getText().toString().trim();
                    String actionCode = etCode.getText().toString().trim();
                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(type) || TextUtils.isEmpty(actionCode)) {
                        Toast.makeText(this, R.string.required_fields_missing, Toast.LENGTH_SHORT).show(); return;
                    }
                    new Thread(() -> {
                        OperatorActionEntity a = existing != null ? existing : new OperatorActionEntity(UUID.randomUUID().toString(), operatorId, name, type, null, null, uid);
                        a.setName(name); a.setType(type); a.setAddedBy(uid);
                        a.setActionCode(actionCode);
                        a.setUpdatedAt(System.currentTimeMillis()); a.setNeedsSync(true);
                        db.operatorActionDao().insertAction(a);
                        runOnUiThread(() -> { load(); sync.syncOperatorActions(); });
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

}


