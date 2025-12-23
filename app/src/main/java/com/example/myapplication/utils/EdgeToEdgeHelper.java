package com.example.myapplication.utils;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Helper class to enable edge-to-edge display and handle system window insets
 */
public class EdgeToEdgeHelper {
    
    /**
     * Enable edge-to-edge display for the activity
     * @param activity The activity to enable edge-to-edge for
     */
    public static void enableEdgeToEdge(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Window window = activity.getWindow();
            if (window != null) {
                window.setDecorFitsSystemWindows(false);
            }
        }
    }
    
    /**
     * Setup system window insets for a header layout to avoid status bar overlap
     * @param headerLayout The header view to apply insets to
     * @param activity The activity context
     */
    public static void setupHeaderInsets(View headerLayout, Activity activity) {
        if (headerLayout == null) return;
        
        ViewCompat.setOnApplyWindowInsetsListener(headerLayout, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            
            // Add minimal padding: status bar height + small offset for breathing room
            int topPadding = topInset + (int) (4 * activity.getResources().getDisplayMetrics().density);
            int bottomPadding = (int) (4 * activity.getResources().getDisplayMetrics().density);
            
            v.setPadding(
                v.getPaddingLeft(), 
                topPadding, 
                v.getPaddingRight(), 
                bottomPadding
            );
            
            // Consume the insets
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
