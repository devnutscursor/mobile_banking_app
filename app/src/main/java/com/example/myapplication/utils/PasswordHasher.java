package com.example.myapplication.utils;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * SHA-256 + salt hashing for offline email password storage.
 */
public class PasswordHasher {
    private static final String TAG = "PasswordHasher";
    private static final String ALGORITHM = "SHA-256";
    private static final String LEGACY_PREFIX = "plain:";

    public static String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);

            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(salt);
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();

            String saltBase64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP);
            String hashBase64 = android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP);
            return saltBase64 + ":" + hashBase64;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error hashing password", e);
            throw new RuntimeException("Error hashing password", e);
        }
    }

    public static boolean verifyPassword(String password, String storedHash) {
        if (password == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        if (storedHash.startsWith(LEGACY_PREFIX)) {
            return password.equals(storedHash.substring(LEGACY_PREFIX.length()));
        }
        if (!storedHash.contains(":")) {
            return password.equals(storedHash);
        }
        try {
            String[] parts = storedHash.split(":", 2);
            if (parts.length != 2) {
                return false;
            }
            byte[] salt = android.util.Base64.decode(parts[0].trim(), android.util.Base64.NO_WRAP);
            byte[] storedHashBytes = android.util.Base64.decode(parts[1].trim(), android.util.Base64.NO_WRAP);

            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(salt);
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            byte[] computedHash = digest.digest();

            return constantTimeEquals(computedHash, storedHashBytes);
        } catch (Exception e) {
            Log.e(TAG, "Error verifying password", e);
            return false;
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
