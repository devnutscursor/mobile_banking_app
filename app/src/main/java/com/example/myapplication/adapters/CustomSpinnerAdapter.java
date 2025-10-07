package com.example.myapplication.adapters;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

import java.util.List;

public class CustomSpinnerAdapter extends ArrayAdapter<CustomSpinnerAdapter.SpinnerItem> {

    private final LayoutInflater inflater;
    private final List<SpinnerItem> items;
    private final int iconResource;

    public static class SpinnerItem {
        private String text;
        private int colorRes;

        public SpinnerItem(String text, int colorRes) {
            this.text = text;
            this.colorRes = colorRes;
        }

        public String getText() {
            return text;
        }

        public int getColorRes() {
            return colorRes;
        }
    }

    public CustomSpinnerAdapter(@NonNull Context context, List<SpinnerItem> items, int iconResource) {
        super(context, 0, items);
        this.inflater = LayoutInflater.from(context);
        this.items = items;
        this.iconResource = iconResource;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createView(position, convertView, parent, R.layout.spinner_item);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createView(position, convertView, parent, R.layout.spinner_dropdown_item);
    }

    private View createView(int position, View convertView, ViewGroup parent, int layoutResource) {
        if (convertView == null) {
            convertView = inflater.inflate(layoutResource, parent, false);
        }

        SpinnerItem item = items.get(position);

        ImageView ivIcon = convertView.findViewById(R.id.ivIcon);
        TextView tvText = convertView.findViewById(R.id.tvText);

        if (item != null && tvText != null) {
            tvText.setText(item.getText());
            tvText.setTextColor(ContextCompat.getColor(getContext(), android.R.color.white));
            
            // Set icon if present in layout (dropdown rows include icon, selected view may not)
            if (ivIcon != null) {
                ivIcon.setImageResource(iconResource);
                // Set icon color based on item color
                if (item.getColorRes() != 0) {
                    int color = ContextCompat.getColor(getContext(), item.getColorRes());
                    ivIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                } else {
                    ivIcon.clearColorFilter();
                }
            }
        }

        return convertView;
    }
}

