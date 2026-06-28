package com.example.myapplication.utils;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.entities.OperatorBalanceEntity;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pushes operator balances to Firestore immediately after local changes.
 */
public class OperatorBalanceSyncHelper {
    private static final String TAG = "OperatorBalanceSync";

    public static void pushBalanceIfNeeded(Context context, OperatorBalanceEntity balance) {
        if (context == null || balance == null || !balance.isNeedsSync()) {
            return;
        }
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        AppDatabase database = AppDatabase.getDatabase(context);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", balance.getUserId());
        data.put("operatorId", balance.getOperatorId());
        data.put("balance", balance.getBalance());
        data.put("totalCreditUsed", balance.getTotalCreditUsed());
        data.put("totalCreditEarned", balance.getTotalCreditEarned());
        data.put("createdAt", new com.google.firebase.Timestamp(new java.util.Date(balance.getCreatedAt())));
        data.put("updatedAt", new com.google.firebase.Timestamp(new java.util.Date(balance.getUpdatedAt())));

        String docId = balance.getId();
        firestore.collection("operator_balances").document(docId)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    balance.setNeedsSync(false);
                    balance.setLastSyncAt(System.currentTimeMillis());
                    database.operatorBalanceDao().updateBalance(balance);
                    Log.d(TAG, "Pushed operator balance: " + docId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to push operator balance: " + docId, e));
    }

    public static void pushPendingBalancesForUser(Context context, String userId) {
        if (context == null || userId == null) {
            return;
        }
        AppDatabase database = AppDatabase.getDatabase(context);
        List<OperatorBalanceEntity> pending = database.operatorBalanceDao().getNeedingSyncForUser(userId);
        for (OperatorBalanceEntity balance : pending) {
            pushBalanceIfNeeded(context, balance);
        }
    }
}
