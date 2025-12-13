package com.example.myapplication.utils;

import android.util.Log;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * MRZ (Machine Readable Zone) Parser
 * Supports TD1 (ID cards, 3 lines), TD2 (ID cards, 2 lines), and TD3 (Passports, 2 lines)
 * Based on ICAO 9303 standard
 */
public class MRZParser {
    private static final String TAG = "MRZParser";
    
    /**
     * Parse MRZ text and extract customer information
     * @param mrzText Raw MRZ text (should contain 2-3 lines of MRZ data)
     * @return MRZData object with parsed fields, or null if parsing fails
     */
    public static MRZData parseMRZ(String mrzText) {
        if (mrzText == null || mrzText.trim().isEmpty()) {
            Log.w(TAG, "MRZ text is null or empty");
            return null;
        }
        
        // Clean the text - remove extra whitespace, keep only letters, numbers, and <
        String cleaned = mrzText.trim().replaceAll("[^A-Z0-9<\\n\\r]", "").toUpperCase();
        String[] lines = cleaned.split("[\\n\\r]+");
        
        // Filter empty lines - be more flexible with length (OCR might miss/duplicate chars)
        java.util.List<String> mrzLines = new java.util.ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            // Accept lines that are 28-32 chars for TD1, 34-38 for TD2, 42-46 for TD3
            // OCR might not get exact length
            if (trimmed.length() >= 28 && trimmed.length() <= 46 && trimmed.matches("[A-Z0-9<]+")) {
                mrzLines.add(trimmed);
            }
        }
        
        if (mrzLines.isEmpty()) {
            Log.w(TAG, "No valid MRZ lines found after filtering");
            return null;
        }
        
        Log.d(TAG, "Found " + mrzLines.size() + " MRZ lines");
        for (int i = 0; i < mrzLines.size(); i++) {
            Log.d(TAG, "Line " + (i + 1) + " (" + mrzLines.get(i).length() + " chars): " + mrzLines.get(i));
        }
        
        // Determine format based on line count and first character
        String firstLine = mrzLines.get(0);
        if (firstLine.length() < 2) {
            Log.w(TAG, "MRZ line too short");
            return null;
        }
        
        char docType = firstLine.charAt(0);
        int lineCount = mrzLines.size();
        int firstLineLength = firstLine.length();
        
        // Determine format - be flexible with line lengths
        if (lineCount >= 3 && firstLineLength >= 28 && firstLineLength <= 32) {
            // TD1 format (ID card, 3 lines of ~30 chars) - allow 28-32 chars
            Log.d(TAG, "Detected TD1 format (3 lines, ~30 chars each)");
            return parseTD1(mrzLines);
        } else if (lineCount >= 2 && firstLineLength >= 34 && firstLineLength <= 38) {
            // TD2 format (ID card, 2 lines of ~36 chars) - allow 34-38 chars
            Log.d(TAG, "Detected TD2 format (2 lines, ~36 chars each)");
            return parseTD2(mrzLines);
        } else if (lineCount >= 2 && firstLineLength >= 42 && firstLineLength <= 46) {
            // TD3 format (Passport, 2 lines of ~44 chars) - allow 42-46 chars
            Log.d(TAG, "Detected TD3 format (2 lines, ~44 chars each)");
            return parseTD3(mrzLines);
        } else {
            // Try to parse anyway based on document type
            if (docType == 'P' && lineCount >= 2) {
                Log.d(TAG, "Trying TD3 format (passport type)");
                return parseTD3(mrzLines);
            } else if ((docType == 'I' || docType == 'A' || docType == 'C') && lineCount >= 2) {
                // Try TD1 first (most common for ID cards)
                if (lineCount >= 3 || firstLineLength >= 28) {
                    Log.d(TAG, "Trying TD1 format (ID card type)");
                    return parseTD1(mrzLines);
                }
            }
        }
        
        Log.w(TAG, "Unable to determine MRZ format - Line count: " + lineCount + ", First line length: " + firstLineLength);
        return null;
    }
    
    /**
     * Parse TD1 format (ID cards, 3 lines × 30 characters)
     * Format based on Burkina Faso ID cards:
     * Line 1: I<BFAB213719491<<<<<<<<<<<<<<< (Document type, country, document number)
     * Line 2: 0001085M3504244BFA<<<<<<<<<<<4 (DOB, gender, expiry, nationality)
     * Line 3: TIEMA<<DANLE<CHEICK<ABOUBACA<S (Surname, given names)
     */
    private static MRZData parseTD1(java.util.List<String> lines) {
        if (lines.size() < 2 || lines.get(0).length() < 28) {
            Log.w(TAG, "TD1: Not enough lines or first line too short. Lines: " + lines.size() + ", First line length: " + (lines.size() > 0 ? lines.get(0).length() : 0));
            return null;
        }
        
        // Normalize line lengths - pad with < if too short, truncate if too long
        // TD1 lines should be 30 chars, but OCR might give slightly different lengths
        java.util.List<String> normalizedLines = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size() && i < 3; i++) {
            String line = lines.get(i);
            if (line.length() < 30) {
                // Pad with < to reach 30 chars
                StringBuilder padded = new StringBuilder(line);
                while (padded.length() < 30) {
                    padded.append('<');
                }
                normalizedLines.add(padded.toString());
            } else if (line.length() > 30) {
                // Truncate to 30 chars
                normalizedLines.add(line.substring(0, 30));
            } else {
                normalizedLines.add(line);
            }
        }
        
        // Ensure we have at least 2 lines, pad line 3 if missing
        while (normalizedLines.size() < 3) {
            StringBuilder emptyLine = new StringBuilder();
            while (emptyLine.length() < 30) {
                emptyLine.append('<');
            }
            normalizedLines.add(emptyLine.toString());
        }
        
        String line1 = normalizedLines.get(0);
        String line2 = normalizedLines.get(1);
        String line3 = normalizedLines.size() > 2 ? normalizedLines.get(2) : "";
        
        MRZData data = new MRZData();
        
        // Line 1: I<BFAB213719491<<<<<<<<<<<<<<<
        // Position 0-1: Document type (e.g., "I<")
        // Position 2-4: Country code (e.g., "BFA")
        // Position 5-14: Document number with check digit (10 chars, but actual ID is 5-13, 14 is check digit)
        data.documentType = line1.substring(0, 2); // Document type + subtype
        data.countryCode = line1.substring(2, 5); // Country code (BFA)
        
        // Extract document number: positions 5-13 (9 chars), skip check digit at position 14
        // For Burkina Faso ID: "B21371949" is 9 characters at positions 5-13
        // The format is: country code (3) + document number (9) + check digit (1) + fillers
        String docNumberWithCheck = line1.substring(5, Math.min(15, line1.length()));
        // Remove all < characters to get the actual number
        docNumberWithCheck = docNumberWithCheck.replaceAll("<", "");
        
        // Document number is typically 9 characters (e.g., "B21371949")
        // If there's a 10th character, it's likely a check digit
        if (docNumberWithCheck.length() >= 9) {
            // Take first 9 characters as the document number
            data.documentNumber = docNumberWithCheck.substring(0, 9);
            Log.d(TAG, "Extracted document number: '" + data.documentNumber + "' from '" + docNumberWithCheck + "'");
        } else if (docNumberWithCheck.length() > 0) {
            // If less than 9, use what we have (might be OCR error)
            data.documentNumber = docNumberWithCheck;
            Log.d(TAG, "Extracted document number (short): '" + data.documentNumber + "' from '" + docNumberWithCheck + "'");
        } else {
            data.documentNumber = "";
            Log.w(TAG, "No document number found in line 1");
        }
        
        // Line 2: 0001085M3504244BFA<<<<<<<<<<<4
        // Position 0-5: Date of birth (YYMMDD) = 000108 = 2000-01-08
        // Position 6: Check digit for DOB (excluded from date extraction)
        // Position 7: Gender (M/F)
        // Position 8-13: Expiry date (YYMMDD) = 350424 = 2035-04-24
        // Position 14: Check digit for expiry (excluded from date extraction)
        // Position 15-17: Nationality (BFA)
        if (line2.length() >= 14) {
            // Date of birth: positions 0-5 (6 characters, YYMMDD format)
            // Note: substring(0, 6) extracts positions 0-5 (6 chars), excluding position 6 (check digit)
            String dob = line2.substring(0, 6);
            Log.d(TAG, "Extracted DOB raw from line2: '" + dob + "'");
            data.dateOfBirth = parseDate(dob, true); // Convert to YYYY-MM-DD
            
            // Gender: position 7
            if (line2.length() > 7) {
                String genderChar = line2.substring(7, 8).replace("<", "");
                if (!genderChar.isEmpty()) {
                    data.gender = genderChar;
                }
            }
            
            // Expiry date: positions 8-13 (6 characters, YYMMDD format)
            // Note: substring(8, 14) extracts positions 8-13 (6 chars), excluding position 14 (check digit)
            if (line2.length() >= 14) {
                String expiry = line2.substring(8, 14);
                Log.d(TAG, "Extracted expiry raw from line2: '" + expiry + "'");
                data.expiryDate = parseDate(expiry, false); // Convert to YYYY-MM-DD
            }
            
            // Nationality: positions 15-17
            if (line2.length() >= 18) {
                data.nationality = line2.substring(15, 18);
            } else if (line2.length() >= 15) {
                // Try to extract if line is slightly shorter
                String possibleNationality = line2.substring(15).replaceAll("<", "").trim();
                if (possibleNationality.length() >= 3) {
                    data.nationality = possibleNationality.substring(0, 3);
                }
            }
        } else {
            Log.w(TAG, "Line 2 too short for date extraction. Length: " + line2.length() + ", Content: " + line2);
        }
        
        // Line 3: TIEMA<<DANLE<CHEICK<ABOUBACA<S
        // Contains surname and given names separated by <<
        // Format: SURNAME<<GIVEN<NAME1<NAME2<NAME3...
        if (line3.length() >= 30) {
            // Parse names from line 3
            parseNames(line3, data);
        }
        
        // Use document number as national ID
        if (data.documentNumber != null && !data.documentNumber.isEmpty()) {
            data.nationalIdNumber = data.documentNumber;
        }
        
        // Map document type
        data.documentTypeName = mapDocumentType(data.documentType);
        
        Log.d(TAG, "Parsed TD1 - Name: " + data.fullName + ", DOB: " + data.dateOfBirth + 
              ", ID: " + data.nationalIdNumber + ", Expiry: " + data.expiryDate);
        
        return data;
    }
    
    /**
     * Parse TD2 format (ID cards, 2 lines × 36 characters)
     */
    private static MRZData parseTD2(java.util.List<String> lines) {
        if (lines.size() < 2 || lines.get(0).length() < 36 || lines.get(1).length() < 36) {
            return null;
        }
        
        String line1 = lines.get(0);
        String line2 = lines.get(1);
        
        MRZData data = new MRZData();
        
        // Line 1
        data.documentType = line1.substring(0, 2);
        data.countryCode = line1.substring(2, 5);
        String nameField = line1.substring(5).trim();
        parseNames(nameField, data);
        
        // Line 2
        String docNumber = line2.substring(0, 9).replaceAll("<", "").trim();
        data.documentNumber = docNumber;
        data.nationality = line2.substring(10, 13);
        String dob = line2.substring(13, 19);
        data.dateOfBirth = parseDate(dob, true);
        data.gender = line2.substring(20, 21).replace("<", "");
        String expiry = line2.substring(21, 27);
        data.expiryDate = parseDate(expiry, false);
        
        // Additional data
        if (line2.length() > 27) {
            String personalNumber = line2.substring(28).replaceAll("<", "").trim();
            if (!personalNumber.isEmpty()) {
                data.nationalIdNumber = personalNumber;
            }
        }
        
        if (data.nationalIdNumber == null || data.nationalIdNumber.isEmpty()) {
            data.nationalIdNumber = data.documentNumber;
        }
        
        data.documentTypeName = mapDocumentType(data.documentType);
        
        return data;
    }
    
    /**
     * Parse TD3 format (Passports, 2 lines × 44 characters)
     */
    private static MRZData parseTD3(java.util.List<String> lines) {
        if (lines.size() < 2 || lines.get(0).length() < 44 || lines.get(1).length() < 44) {
            return null;
        }
        
        String line1 = lines.get(0);
        String line2 = lines.get(1);
        
        MRZData data = new MRZData();
        
        // Line 1
        data.documentType = line1.substring(0, 2);
        data.countryCode = line1.substring(2, 5);
        String nameField = line1.substring(5, 44).trim();
        parseNames(nameField, data);
        
        // Line 2
        String docNumber = line2.substring(0, 9).replaceAll("<", "").trim();
        data.documentNumber = docNumber;
        data.nationality = line2.substring(10, 13);
        String dob = line2.substring(13, 19);
        data.dateOfBirth = parseDate(dob, true);
        data.gender = line2.substring(20, 21).replace("<", "");
        String expiry = line2.substring(21, 27);
        data.expiryDate = parseDate(expiry, false);
        
        // Personal number (optional)
        if (line2.length() > 28) {
            String personalNumber = line2.substring(28, 42).replaceAll("<", "").trim();
            if (!personalNumber.isEmpty()) {
                data.nationalIdNumber = personalNumber;
            }
        }
        
        if (data.nationalIdNumber == null || data.nationalIdNumber.isEmpty()) {
            data.nationalIdNumber = data.documentNumber;
        }
        
        data.documentTypeName = mapDocumentType(data.documentType);
        
        return data;
    }
    
    /**
     * Parse names from MRZ name field
     * Format examples:
     * - TD1/TD2: SURNAME<<GIVEN<NAME1<NAME2<NAME3 (e.g., "TIEMA<<DANLE<CHEICK<ABOUBACA<S")
     * - TD3: SURNAME<<GIVEN<NAMES (e.g., "ERIKSSON<<ANNA<MARIA")
     * 
     * In TD1/TD2, names are separated by << between surname and given names,
     * and given names are separated by <
     */
    private static void parseNames(String nameField, MRZData data) {
        if (nameField == null || nameField.isEmpty()) {
            return;
        }
        
        // Clean the name field - remove trailing check digits or filler characters
        // Sometimes there's a single character at the end (like 'S' in the example)
        nameField = nameField.trim();
        
        // Split by << to separate surname from given names
        String[] parts = nameField.split("<<", 2);
        
        if (parts.length >= 1) {
            // Surname is first part (before <<)
            // Remove any < characters and trim
            data.surname = parts[0].replaceAll("<", " ").trim();
        }
        
        if (parts.length >= 2 && !parts[1].isEmpty()) {
            // Given names are in second part (after <<)
            // Split by < to get individual given names
            String givenNamesPart = parts[1];
            
            // Remove trailing single characters that might be check digits (like 'S' at the end)
            // But keep actual name parts
            givenNamesPart = givenNamesPart.trim();
            
            // Split by < to get individual names
            String[] givenNameParts = givenNamesPart.split("<");
            java.util.List<String> validNames = new java.util.ArrayList<>();
            
            for (String namePart : givenNameParts) {
                String trimmed = namePart.trim();
                // Filter out single characters that are likely check digits or filler
                // Keep only names with 2+ characters
                // Also handle cases where name might be truncated (e.g., "ABOUBACA" instead of "ABOUBACAR")
                if (trimmed.length() >= 2) {
                    validNames.add(trimmed);
                } else if (trimmed.length() == 1 && validNames.size() > 0) {
                    // Single character after a valid name might be part of the last name
                    // For example: "ABOUBACA" + "S" might be "ABOUBACAR" or "ABOUBACAR SIDIK"
                    // In this case, we'll append it to the last name if it makes sense
                    String lastValidName = validNames.get(validNames.size() - 1);
                    // Only append if the last name is short (might be truncated)
                    if (lastValidName.length() < 8) {
                        validNames.set(validNames.size() - 1, lastValidName + trimmed);
                    }
                }
            }
            
            // Join valid given names with spaces
            if (!validNames.isEmpty()) {
                data.givenNames = String.join(" ", validNames);
            }
            
            // Combine surname and given names for full name
            if (!data.surname.isEmpty()) {
                data.fullName = data.surname;
                if (data.givenNames != null && !data.givenNames.isEmpty()) {
                    data.fullName += " " + data.givenNames;
                }
            } else if (data.givenNames != null && !data.givenNames.isEmpty()) {
                data.fullName = data.givenNames;
            }
        } else {
            // No given names, only surname
            data.fullName = data.surname;
        }
        
        // Clean up multiple spaces
        if (data.fullName != null) {
            data.fullName = data.fullName.replaceAll("\\s+", " ").trim();
        }
        if (data.surname != null) {
            data.surname = data.surname.replaceAll("\\s+", " ").trim();
        }
        if (data.givenNames != null) {
            data.givenNames = data.givenNames.replaceAll("\\s+", " ").trim();
        }
        
        Log.d(TAG, "Parsed names - Surname: " + data.surname + ", Given: " + data.givenNames + ", Full: " + data.fullName);
    }
    
    /**
     * Clean date string to fix common OCR errors
     * OCR often confuses similar-looking characters:
     * - O (letter) → 0 (zero)
     * - I, l (letters) → 1 (one)
     * - S (letter) → 5 (five)
     * - B (letter) → 8 (eight)
     * - Z (letter) → 2 (two)
     * - G (letter) → 6 (six)
     * 
     * Dates in MRZ are always digits, so we can safely convert these
     * @param dateStr Raw date string that may contain OCR errors
     * @return Cleaned date string with only digits (6 digits expected)
     */
    private static String cleanDateString(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return "";
        }
        
        // Remove all non-alphanumeric characters first (spaces, <, etc.)
        String cleaned = dateStr.replaceAll("[^A-Z0-9]", "").toUpperCase();
        
        // Convert common OCR errors: letters that look like digits
        // Since dates are always digits in MRZ, convert these letters to digits
        cleaned = cleaned
            .replace('O', '0')  // Letter O → zero
            .replace('I', '1')  // Letter I → one
            .replace('L', '1')  // Letter L → one (also lowercase l handled by uppercase)
            .replace('S', '5')  // Letter S → five
            .replace('B', '8')  // Letter B → eight
            .replace('Z', '2')  // Letter Z → two
            .replace('G', '6')  // Letter G → six
            .replace('T', '7')  // Letter T → seven (less common but possible)
            .replace('Q', '0')  // Letter Q → zero (Q can look like 0)
            .replace('D', '0'); // Letter D → zero (in some fonts)
        
        // Extract only digits now
        cleaned = cleaned.replaceAll("[^0-9]", "");
        
        // Log the cleaning for debugging
        if (!cleaned.equals(dateStr)) {
            Log.d(TAG, "Cleaned date: '" + dateStr + "' → '" + cleaned + "'");
        }
        
        return cleaned;
    }
    
    /**
     * Parse date from MRZ format (YYMMDD) to DD-MM-YYYY or YYYY-MM-DD
     * Handles OCR errors by cleaning the date string first
     * 
     * MRZ date format: YYMMDD (6 digits)
     * - YY: 2-digit year (00-99)
     * - MM: 2-digit month (01-12)
     * - DD: 2-digit day (01-31)
     * 
     * Century determination:
     * - For DOB: Years 00-30 are 2000-2030, 31-99 are 1931-1999
     * - For Expiry: Years 00-99 are 2000-2099 (always future)
     * 
     * @param dateStr Date string in YYMMDD format (may contain OCR errors)
     * @param forDOB If true, return YYYY-MM-DD format (for dateOfBirth), else YYYY-MM-DD (for expiry)
     * @return Parsed date in YYYY-MM-DD format, or null if parsing fails
     */
    private static String parseDate(String dateStr, boolean forDOB) {
        if (dateStr == null || dateStr.isEmpty()) {
            Log.w(TAG, "Date string is null or empty");
            return null;
        }
        
        // Clean the date string to fix OCR errors
        String cleaned = cleanDateString(dateStr);
        
        // Check if we have at least 6 digits
        if (cleaned.length() < 6) {
            Log.w(TAG, "Date string has less than 6 digits after cleaning. Original: '" + dateStr + "', Cleaned: '" + cleaned + "'");
            return null;
        }
        
        // Take only first 6 digits (in case there are extra characters from OCR)
        cleaned = cleaned.substring(0, 6);
        
        // Verify it's all digits
        if (!cleaned.matches("\\d{6}")) {
            Log.w(TAG, "Date string is not exactly 6 digits after cleaning: '" + cleaned + "' (original: '" + dateStr + "')");
            return null;
        }
        
        try {
            int yy = Integer.parseInt(cleaned.substring(0, 2));
            int mm = Integer.parseInt(cleaned.substring(2, 4));
            int dd = Integer.parseInt(cleaned.substring(4, 6));
            
            // Determine century based on date type
            int yyyy;
            if (forDOB) {
                // For Date of Birth: years 00-30 are 2000-2030, 31-99 are 1931-1999
                // (Most people born 1931-1999, few born 2000-2030)
                yyyy = (yy <= 30) ? (2000 + yy) : (1900 + yy);
            } else {
                // For Expiry Date: all years 00-99 are 2000-2099
                // (Expiry dates are always in the future, so 35 = 2035, not 1935)
                yyyy = 2000 + yy;
            }
            
            // Validate date ranges
            if (mm < 1 || mm > 12) {
                Log.w(TAG, "Invalid month in date: " + cleaned + " (month: " + mm + ")");
                return null;
            }
            if (dd < 1 || dd > 31) {
                Log.w(TAG, "Invalid day in date: " + cleaned + " (day: " + dd + ")");
                return null;
            }
            
            // Additional validation: check if day is valid for the month
            if (dd > getMaxDaysInMonth(mm, yyyy)) {
                Log.w(TAG, "Invalid day for month: " + cleaned + " (day " + dd + " not valid for month " + mm + ")");
                return null;
            }
            
            if (forDOB) {
                // Return YYYY-MM-DD format (ISO format for date of birth)
                String result = String.format("%04d-%02d-%02d", yyyy, mm, dd);
                Log.d(TAG, "Parsed DOB: '" + cleaned + "' → '" + result + "'");
                return result;
            } else {
                // Return YYYY-MM-DD format (ISO format for expiry dates)
                String result = String.format("%04d-%02d-%02d", yyyy, mm, dd);
                Log.d(TAG, "Parsed expiry date: '" + cleaned + "' → '" + result + "'");
                return result;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing date: '" + cleaned + "' (original: '" + dateStr + "')", e);
            return null;
        }
    }
    
    /**
     * Get maximum days in a month (handles leap years)
     * @param month Month (1-12)
     * @param year Year (full 4-digit year)
     * @return Maximum days in the month
     */
    private static int getMaxDaysInMonth(int month, int year) {
        int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        if (month == 2 && isLeapYear(year)) {
            return 29;
        }
        if (month >= 1 && month <= 12) {
            return daysInMonth[month - 1];
        }
        return 31; // Default
    }
    
    /**
     * Check if a year is a leap year
     * @param year Year to check
     * @return true if leap year, false otherwise
     */
    private static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }
    
    /**
     * Map document type code to document type name
     */
    private static String mapDocumentType(String docType) {
        if (docType == null || docType.isEmpty()) {
            return "CNI";
        }
        
        char firstChar = docType.charAt(0);
        switch (firstChar) {
            case 'P':
                return "PASSPORT";
            case 'I':
                return "CNI";
            case 'A':
                return "CNI AES";
            case 'C':
                return "CNI CEDEAO";
            default:
                return "CNI";
        }
    }
    
    /**
     * Data class to hold parsed MRZ information
     */
    public static class MRZData {
        public String documentType;
        public String documentTypeName;
        public String countryCode;
        public String surname;
        public String givenNames;
        public String fullName;
        public String documentNumber;
        public String nationalIdNumber;
        public String nationality;
        public String dateOfBirth; // DD-MM-YYYY format
        public String gender;
        public String expiryDate; // YYYY-MM-DD format
        
        public MRZData() {
            // Initialize with empty strings
        }
        
        public boolean isValid() {
            return (fullName != null && !fullName.isEmpty()) ||
                   (documentNumber != null && !documentNumber.isEmpty());
        }
    }
}

