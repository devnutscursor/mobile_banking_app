package com.example.myapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.myapplication.R;
import com.example.myapplication.utils.LanguageManager;

import java.util.ArrayList;
import java.util.List;

public class LanguageSpinnerAdapter extends BaseAdapter {
    
    private Context context;
    private List<LanguageItem> languages;
    private LayoutInflater inflater;
    
    public static class LanguageItem {
        public String code;
        public String name;
        public String flag;
        public boolean modelDownloaded;
        
        public LanguageItem(String code, String name, String flag, boolean modelDownloaded) {
            this.code = code;
            this.name = name;
            this.flag = flag;
            this.modelDownloaded = modelDownloaded;
        }
    }
    
    public LanguageSpinnerAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.languages = new ArrayList<>();
        
        // Add languages (no download needed for string resources)
        LanguageManager languageManager = LanguageManager.getInstance(context);
        String[] availableLanguages = languageManager.getAvailableLanguages();
        for (String langCode : availableLanguages) {
            String name = languageManager.getLanguageDisplayName(langCode);
            String flag = languageManager.getLanguageFlag(langCode);
            boolean downloaded = true; // String resources are always available
            languages.add(new LanguageItem(langCode, name, flag, downloaded));
        }
    }

    /**
     * Refresh model downloaded status (no-op for string resources)
     */
    public void refreshModelStatus() {
        // No-op: string resources are always available
        notifyDataSetChanged();
    }
    
    @Override
    public int getCount() {
        return languages.size();
    }
    
    @Override
    public LanguageItem getItem(int position) {
        return languages.get(position);
    }
    
    @Override
    public long getItemId(int position) {
        return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
        }
        
        LanguageItem item = getItem(position);
        TextView textView = (TextView) view;
        textView.setText(getDisplayText(item));
        
        return view;
    }
    
    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
        }
        
        LanguageItem item = getItem(position);
        TextView textView = (TextView) view;
        textView.setText(getDisplayText(item));
        
        return view;
    }

    private String getDisplayText(LanguageItem item) {
        return item.flag + " " + item.name; // No download required for string resources
    }
    
    public int getPositionForLanguage(String languageCode) {
        for (int i = 0; i < languages.size(); i++) {
            if (languages.get(i).code.equals(languageCode)) {
                return i;
            }
        }
        return 0; // Default to English
    }
}
