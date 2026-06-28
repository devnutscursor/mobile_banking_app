package com.example.myapplication.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * Optional direct ESC/POS printing over Bluetooth SPP (58mm thermal).
 */
public class ReceiptPrinterHelper {
    private static final String TAG = "ReceiptPrinterHelper";
    private static final String PREFS = "receipt_printer_prefs";
    private static final String KEY_MAC = "printer_mac";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static void savePrinterMac(Context context, String mac) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MAC, mac).apply();
    }

    public static String getSavedPrinterMac(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MAC, null);
    }

    public static boolean printEscPos(Context context, String text) {
        String mac = getSavedPrinterMac(context);
        if (mac == null || mac.isEmpty()) {
            return false;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return false;
        }
        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(mac);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid printer MAC", e);
            return false;
        }
        try (BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP_UUID)) {
            adapter.cancelDiscovery();
            socket.connect();
            OutputStream out = socket.getOutputStream();
            out.write(new byte[]{0x1B, 0x40}); // init
            out.write(text.getBytes(Charset.forName("ISO-8859-1")));
            out.write(new byte[]{0x0A, 0x0A, 0x0A});
            out.write(new byte[]{0x1D, 0x56, 0x00}); // cut
            out.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Bluetooth print failed", e);
            return false;
        }
    }
}
