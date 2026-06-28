package com.example.myapplication.utils;

import android.content.Context;
import android.view.Gravity;
import android.widget.Toast;

/**
 * Shows toasts near the top of the screen so they are not clipped by
 * bottom buttons or the system navigation bar.
 */
public final class ToastHelper {

    private ToastHelper() {
    }

    public static void show(Context context, String message) {
        show(context, message, Toast.LENGTH_SHORT);
    }

    public static void showLong(Context context, String message) {
        show(context, message, Toast.LENGTH_LONG);
    }

    public static void show(Context context, String message, int duration) {
        Toast toast = Toast.makeText(context.getApplicationContext(), message, duration);
        int yOffset = (int) (72 * context.getResources().getDisplayMetrics().density);
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, yOffset);
        toast.show();
    }
}
