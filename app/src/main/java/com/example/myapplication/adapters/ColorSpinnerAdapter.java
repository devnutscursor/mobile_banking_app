package com.example.myapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;

public class ColorSpinnerAdapter extends ArrayAdapter<String> {
    private final LayoutInflater inflater;

    public ColorSpinnerAdapter(@NonNull Context context, @NonNull String[] colors) {
        super(context, R.layout.spinner_color_selected, colors);
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.spinner_color_selected, parent, false);
        }
        bind(convertView, getItem(position));
        return convertView;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.spinner_color_dropdown, parent, false);
        }
        bind(convertView, getItem(position));
        return convertView;
    }

    private void bind(View view, String colorName) {
        TextView tv = view.findViewById(R.id.tvText);
        View swatch = view.findViewById(R.id.viewSwatch);
        if (tv != null) {
            // Display translated color name
            String translatedName = getTranslatedColorName(view.getContext(), colorName);
            tv.setText(translatedName);
        }
        if (swatch != null) {
            int colorRes = mapColor(colorName);
            swatch.getBackground().setTint(view.getContext().getColor(colorRes));
        }
    }
    
    private String getTranslatedColorName(Context context, String colorKey) {
        // Map color keys to string resource IDs
        int resId;
        switch (colorKey) {
            case "orange": resId = R.string.color_orange; break;
            case "purple": resId = R.string.color_purple; break;
            case "blue": resId = R.string.color_blue; break;
            case "green": resId = R.string.color_green; break;
            case "amber": resId = R.string.color_amber; break;
            case "red": resId = R.string.color_red; break;
            case "teal": resId = R.string.color_teal; break;
            case "indigo": resId = R.string.color_indigo; break;
            default: return colorKey; // Fallback to key if no translation
        }
        return context.getString(resId);
    }

    private int mapColor(String c) {
        if ("purple".equals(c)) return R.color.primary_purple;
        else if ("blue".equals(c)) return R.color.info_blue;
        else if ("green".equals(c)) return R.color.success_green;
        else if ("amber".equals(c)) return R.color.warning_amber;
        else if ("red".equals(c)) return R.color.error_red;
        else if ("teal".equals(c)) return R.color.teal_200;
        else if ("indigo".equals(c)) return R.color.primary_blue_dark;
        return R.color.primary_orange;
    }
}





