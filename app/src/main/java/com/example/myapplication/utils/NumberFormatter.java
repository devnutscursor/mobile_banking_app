package com.example.myapplication.utils;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Utility class for formatting numbers with thousands separators
 */
public class NumberFormatter {
    
    private static final DecimalFormat NUMBER_FORMAT_INT = new DecimalFormat("#,###", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat NUMBER_FORMAT_DECIMAL = new DecimalFormat("#,###.##", new DecimalFormatSymbols(Locale.US));
    
    /**
     * Add a TextWatcher to an EditText that formats numbers with thousands separators
     * @param editText The EditText to format
     */
    public static void addThousandsSeparator(EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;
            
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            
            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) {
                    return;
                }
                
                isFormatting = true;
                
                try {
                    // Remove all commas and spaces
                    String originalText = s.toString().replaceAll("[,\\s]", "");
                    
                    if (originalText.isEmpty() || originalText.equals(".")) {
                        isFormatting = false;
                        return;
                    }
                    
                    // Check if it contains decimal point
                    boolean hasDecimal = originalText.contains(".");
                    String formatted;
                    
                    if (hasDecimal) {
                        // Parse decimal number
                        double number = Double.parseDouble(originalText);
                        formatted = NUMBER_FORMAT_DECIMAL.format(number);
                    } else {
                        // Parse integer
                        try {
                            long number = Long.parseLong(originalText);
                            formatted = NUMBER_FORMAT_INT.format(number);
                        } catch (NumberFormatException e) {
                            // If it's too large for long, try double
                            double number = Double.parseDouble(originalText);
                            formatted = NUMBER_FORMAT_INT.format((long) number);
                        }
                    }
                    
                    // Only update if the formatted text is different from current text
                    String currentText = s.toString();
                    if (!currentText.equals(formatted)) {
                        int cursorPosition = editText.getSelectionStart();
                        String textBeforeCursor = currentText.substring(0, Math.min(cursorPosition, currentText.length()));
                        
                        // Count numeric characters (digits and decimal point) before cursor
                        int numericCharsBeforeCursor = 0;
                        for (char c : textBeforeCursor.toCharArray()) {
                            if (Character.isDigit(c) || c == '.') {
                                numericCharsBeforeCursor++;
                            }
                        }
                        
                        s.clear();
                        s.append(formatted);
                        
                        // Find new cursor position by counting numeric characters
                        int newCursorPosition = 0;
                        int numericCharsCounted = 0;
                        for (int i = 0; i < formatted.length(); i++) {
                            char c = formatted.charAt(i);
                            if (Character.isDigit(c) || c == '.') {
                                numericCharsCounted++;
                            }
                            newCursorPosition++;
                            if (numericCharsCounted >= numericCharsBeforeCursor) {
                                break;
                            }
                        }
                        
                        // Ensure selection is within bounds
                        if (newCursorPosition > formatted.length()) {
                            newCursorPosition = formatted.length();
                        }
                        
                        editText.setSelection(Math.max(0, newCursorPosition));
                    }
                } catch (NumberFormatException e) {
                    // If parsing fails, keep the text as is (user might be typing)
                } finally {
                    isFormatting = false;
                }
            }
        });
    }
    
    /**
     * Get numeric value from formatted string (removes commas)
     * @param formattedText Text with thousands separators
     * @return Numeric value as double
     */
    public static double getNumericValue(String formattedText) {
        if (formattedText == null || formattedText.trim().isEmpty()) {
            return 0.0;
        }
        try {
            String cleanText = formattedText.replaceAll(",", "");
            return Double.parseDouble(cleanText);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    /**
     * Format a number with thousands separators
     * @param number The number to format
     * @return Formatted string with thousands separators
     */
    public static String formatWithThousandsSeparator(double number) {
        if (number == (long) number) {
            return NUMBER_FORMAT_INT.format((long) number);
        } else {
            return NUMBER_FORMAT_DECIMAL.format(number);
        }
    }
    
    /**
     * Set formatted text on an EditText field with thousands separators
     * This should be used when programmatically setting values
     * @param editText The EditText to set the value on
     * @param number The number to format and set
     */
    public static void setFormattedText(EditText editText, double number) {
        String formatted = formatWithThousandsSeparator(number);
        editText.setText(formatted);
    }
    
    /**
     * Set formatted text on an EditText field with thousands separators from a string
     * This parses the string first, then formats it
     * @param editText The EditText to set the value on
     * @param text The text to parse and format
     */
    public static void setFormattedTextFromString(EditText editText, String text) {
        if (text == null || text.trim().isEmpty()) {
            editText.setText("");
            return;
        }
        try {
            double number = getNumericValue(text);
            setFormattedText(editText, number);
        } catch (Exception e) {
            // If parsing fails, just set the text as-is
            editText.setText(text);
        }
    }
}

