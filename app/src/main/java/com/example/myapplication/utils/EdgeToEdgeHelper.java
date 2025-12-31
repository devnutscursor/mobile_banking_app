package com.example.myapplication.utils;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.view.ViewCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;

/**
 * Helper class to enable edge-to-edge display and handle system window insets
 */
public class EdgeToEdgeHelper {
    
    /**
     * Enable edge-to-edge display for the activity
     * @param activity The activity to enable edge-to-edge for
     */
    public static void enableEdgeToEdge(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;

        // Use WindowCompat for consistent behavior on Android 21+.
        // This is important because transparent system bars can break adjustResize on some OEMs
        // unless we fully opt into insets-based layout.
        WindowCompat.setDecorFitsSystemWindows(window, false);
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

            // Don't consume: allow other views (content/scroll) to receive IME insets too.
            return insets;
        });
    }

    /**
     * Pads the given view's bottom to stay above the keyboard (IME) and navigation bar.
     * Use this on the root content view (or primary scrolling container) after setContentView.
     */
    public static void setupImeInsets(View targetView) {
        if (targetView == null) return;

        final int initialLeft = targetView.getPaddingLeft();
        final int initialTop = targetView.getPaddingTop();
        final int initialRight = targetView.getPaddingRight();
        final int initialBottom = targetView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(targetView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            // When using edge-to-edge (decorFitsSystemWindows=false), the system won't reserve
            // space for the navigation bar; we must.
            int bottomInset = imeVisible ? ime.bottom : systemBars.bottom;

            v.setPadding(
                initialLeft,
                initialTop,
                initialRight,
                initialBottom + Math.max(0, bottomInset)
            );

            return insets;
        });

        ViewCompat.requestApplyInsets(targetView);
    }

    /** Convenience: apply IME padding to the activity root content view. */
    public static void setupImeInsetsForRoot(Activity activity) {
        if (activity == null) return;
        View content = activity.findViewById(android.R.id.content);
        setupImeInsets(content);
    }
}
