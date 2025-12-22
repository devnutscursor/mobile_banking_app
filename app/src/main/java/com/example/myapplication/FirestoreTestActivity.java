package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class FirestoreTestActivity extends AppCompatActivity {

    private EditText etName, etPhone, etAmount;
    private Button btnAddCustomer, btnReadCustomers, btnAddTransaction;
    private TextView tvLog;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firestore_test);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etAmount = findViewById(R.id.etAmount);
        btnAddCustomer = findViewById(R.id.btnAddCustomer);
        btnReadCustomers = findViewById(R.id.btnReadCustomers);
        btnAddTransaction = findViewById(R.id.btnAddTransaction);
        tvLog = findViewById(R.id.tvLog);

        btnAddCustomer.setOnClickListener(v -> addCustomer());
        btnReadCustomers.setOnClickListener(v -> readCustomers());
        btnAddTransaction.setOnClickListener(v -> addTransaction());
        
        // Add thousands separator to amount field
        com.example.myapplication.utils.NumberFormatter.addThousandsSeparator(etAmount);
    }

    private void addCustomer() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (name.isEmpty() || phone.isEmpty()) {
            showLog("Please fill name and phone");
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showLog("Not logged in");
            return;
        }

        Map<String, Object> customer = new HashMap<>();
        customer.put("name", name);
        customer.put("phone", phone);
        customer.put("createdBy", user.getUid());
        customer.put("createdAt", System.currentTimeMillis());

        db.collection("customers")
                .add(customer)
                .addOnSuccessListener(documentReference -> {
                    showLog("Customer added with ID: " + documentReference.getId());
                    etName.setText("");
                    etPhone.setText("");
                })
                .addOnFailureListener(e -> {
                    showLog("Error adding customer: " + e.getMessage());
                });
    }

    private void readCustomers() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showLog("Not logged in");
            return;
        }

        showLog("Reading customers...");
        
        db.collection("customers")
                .whereEqualTo("createdBy", user.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        StringBuilder log = new StringBuilder("Customers:\n");
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            log.append("ID: ").append(document.getId())
                               .append(", Name: ").append(document.getString("name"))
                               .append(", Phone: ").append(document.getString("phone"))
                               .append("\n");
                        }
                        showLog(log.toString());
                    } else {
                        showLog("Error reading customers: " + task.getException().getMessage());
                    }
                });
    }

    private void addTransaction() {
        String phone = etPhone.getText().toString().trim();
        String amountStr = etAmount.getText().toString().trim();

        if (phone.isEmpty() || amountStr.isEmpty()) {
            showLog("Please fill phone and amount");
            return;
        }

        try {
            double amount = com.example.myapplication.utils.NumberFormatter.getNumericValue(amountStr);
            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                showLog("Not logged in");
                return;
            }

            Map<String, Object> transaction = new HashMap<>();
            transaction.put("customerPhone", phone);
            transaction.put("amount", amount);
            transaction.put("type", "deposit");
            transaction.put("createdBy", user.getUid());
            transaction.put("createdAt", System.currentTimeMillis());
            transaction.put("status", "completed");

            db.collection("transactions")
                    .add(transaction)
                    .addOnSuccessListener(documentReference -> {
                        showLog("Transaction added with ID: " + documentReference.getId());
                        etAmount.setText("");
                    })
                    .addOnFailureListener(e -> {
                        showLog("Error adding transaction: " + e.getMessage());
                    });
        } catch (NumberFormatException e) {
            showLog("Invalid amount format");
        }
    }

    private void showLog(String message) {
        String currentLog = tvLog.getText().toString();
        String newLog = currentLog + "\n" + message;
        tvLog.setText(newLog);
    }
}






