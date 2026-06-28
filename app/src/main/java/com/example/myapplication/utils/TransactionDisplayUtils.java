package com.example.myapplication.utils;

import android.content.Context;

import com.example.myapplication.R;

/**
 * Localized labels for transaction types and action names shown in the UI.
 */
public final class TransactionDisplayUtils {

    private TransactionDisplayUtils() {
    }

    public static String getLocalizedTransactionType(Context context, String type) {
        if (context == null || type == null || type.isEmpty()) {
            return type != null ? type : "";
        }
        switch (type.toLowerCase().trim()) {
            case "deposit":
                return context.getString(R.string.deposit);
            case "withdrawal":
                return context.getString(R.string.withdrawal);
            case "transfer":
                return context.getString(R.string.transfer);
            default:
                return type;
        }
    }

    public static String getLocalizedActionName(Context context, String actionName) {
        if (context == null || actionName == null || actionName.isEmpty()) {
            return actionName != null ? actionName : "";
        }
        String actionLower = actionName.toLowerCase().trim();
        if (actionLower.equals("transfer") || actionLower.equals("transfert")) {
            return context.getString(R.string.transfer);
        }
        if (actionLower.equals("deposit") || actionLower.equals("dépôt") || actionLower.equals("depot")) {
            return context.getString(R.string.deposit);
        }
        if (actionLower.equals("withdrawal") || actionLower.equals("retrait")) {
            return context.getString(R.string.withdrawal);
        }
        return actionName;
    }
}
