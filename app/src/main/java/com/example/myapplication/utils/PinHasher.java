package com.example.myapplication.utils;

import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for hashing and verifying 6-digit PINs
 * Uses SHA-256 with salt for secure PIN storage
 */
public class PinHasher {
    private static final String TAG = "PinHasher";
    private static final String ALGORITHM = "SHA-256";
    
    /**
     * Hash a 6-digit PIN with a random salt
     * @param pin The 6-digit PIN to hash
     * @return A string containing the salt and hash separated by ":"
     *         Format: "salt:hash" (both base64 encoded)
     */
    public static String hashPin(String pin) {
        if (pin == null || pin.length() != 6 || !pin.matches("\\d{6}")) {
            throw new IllegalArgumentException("PIN must be exactly 6 digits");
        }
        
        try {
            // Generate random salt
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            
            // Hash PIN with salt
            // This must match the TypeScript implementation: hash.update(salt); hash.update(pin);
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(salt);
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            
            // Encode salt and hash to base64
            String saltBase64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP);
            String hashBase64 = android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP);
            
            // Return "salt:hash"
            return saltBase64 + ":" + hashBase64;
            
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error hashing PIN", e);
            throw new RuntimeException("Error hashing PIN", e);
        }
    }
    
    /**
     * Verify a PIN against a stored hash
     * @param pin The PIN to verify (6 digits)
     * @param storedHash The stored hash in format "salt:hash"
     * @return true if PIN matches, false otherwise
     */
    public static boolean verifyPin(String pin, String storedHash) {
        if (pin == null || pin.length() != 6 || !pin.matches("\\d{6}")) {
            Log.w(TAG, "Invalid PIN format: " + (pin == null ? "null" : "length=" + pin.length()));
            return false;
        }
        
        if (storedHash == null || storedHash.isEmpty()) {
            Log.w(TAG, "Stored hash is null or empty");
            return false;
        }
        
        if (!storedHash.contains(":")) {
            Log.w(TAG, "Stored hash does not contain ':' separator. Hash: " + storedHash);
            return false;
        }
        
        try {
            // Split salt and hash (limit to 2 parts in case hash contains ':')
            String[] parts = storedHash.split(":", 2);
            if (parts.length != 2) {
                Log.w(TAG, "Invalid hash format: expected 'salt:hash', got " + parts.length + " parts");
                return false;
            }
            
            String saltBase64 = parts[0].trim();
            String hashBase64 = parts[1].trim();
            
            if (saltBase64.isEmpty() || hashBase64.isEmpty()) {
                Log.w(TAG, "Salt or hash is empty. Salt length: " + saltBase64.length() + ", Hash length: " + hashBase64.length());
                return false;
            }
            
            Log.d(TAG, "Verifying PIN. Salt length: " + saltBase64.length() + ", Hash length: " + hashBase64.length());
            
            // Decode salt and hash
            byte[] salt = android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP);
            byte[] storedHashBytes = android.util.Base64.decode(hashBase64, android.util.Base64.NO_WRAP);
            
            if (salt == null || salt.length == 0) {
                Log.e(TAG, "Failed to decode salt");
                return false;
            }
            
            if (storedHashBytes == null || storedHashBytes.length == 0) {
                Log.e(TAG, "Failed to decode hash");
                return false;
            }
            
            Log.d(TAG, "Decoded salt bytes: " + salt.length + ", Decoded hash bytes: " + storedHashBytes.length);
            
            // Hash the provided PIN with the same salt
            // This must match the TypeScript implementation: hash.update(salt); hash.update(pin);
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(salt);
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            byte[] computedHash = digest.digest();
            
            Log.d(TAG, "Computed hash bytes: " + computedHash.length + ", Stored hash bytes: " + storedHashBytes.length);
            
            // Compare hashes (constant-time comparison to prevent timing attacks)
            boolean matches = constantTimeEquals(computedHash, storedHashBytes);
            Log.d(TAG, "PIN verification result: " + matches);
            
            return matches;
            
        } catch (Exception e) {
            Log.e(TAG, "Error verifying PIN: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Constant-time comparison to prevent timing attacks
     */
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

