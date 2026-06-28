package com.example.myapplication.utils;

import com.example.myapplication.database.entities.TransactionEntity;

/**
 * Separates user-entered comments from system metadata stored in notes.
 */
public class TransactionNotesHelper {

    public static String getUserNotes(TransactionEntity transaction) {
        if (transaction == null) {
            return "";
        }
        String userNotes = transaction.getUserNotes();
        if (userNotes != null && !userNotes.trim().isEmpty()) {
            return userNotes.trim();
        }
        return extractUserNotesFromLegacyNotes(transaction.getNotes());
    }

    public static String extractUserNotesFromLegacyNotes(String notes) {
        if (notes == null || notes.trim().isEmpty()) {
            return "";
        }
        StringBuilder userPart = new StringBuilder();
        for (String line : notes.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("Account Number:")
                    || trimmed.startsWith("Use Account Number in USSD:")
                    || trimmed.startsWith("USSD code:")
                    || trimmed.startsWith("Failed to open dialer:")) {
                continue;
            }
            if (userPart.length() > 0) {
                userPart.append("\n");
            }
            userPart.append(trimmed);
        }
        return userPart.toString();
    }
}
