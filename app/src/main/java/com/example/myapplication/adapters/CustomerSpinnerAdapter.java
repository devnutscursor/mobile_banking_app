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
import com.example.myapplication.database.entities.CustomerEntity;

import java.util.List;

public class CustomerSpinnerAdapter extends ArrayAdapter<CustomerEntity> {

    private final LayoutInflater inflater;
    private final List<CustomerEntity> customers;

    public CustomerSpinnerAdapter(@NonNull Context context, List<CustomerEntity> customers) {
        super(context, 0, customers);
        this.inflater = LayoutInflater.from(context);
        this.customers = customers;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createSelectedView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createDropdownView(position, convertView, parent);
    }

    private View createSelectedView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.spinner_customer_selected, parent, false);
        }

        CustomerEntity customer = customers.get(position);
        
        TextView tvName = convertView.findViewById(R.id.tvName);
        TextView tvPhone = convertView.findViewById(R.id.tvPhone);

        if (tvName != null && tvPhone != null) {
            String name = customer.getFullName();
            if (name == null || name.trim().isEmpty()) {
                name = customer.getPhoneNumber() != null && !customer.getPhoneNumber().trim().isEmpty()
                        ? customer.getPhoneNumber()
                        : (customer.getId() != null ? customer.getId() : "Unknown");
            }

            tvName.setText(name);
            tvName.setTextColor(getContext().getColor(android.R.color.white));
            
            String phone = customer.getPhoneNumber() != null ? customer.getPhoneNumber() : "";
            tvPhone.setText(phone);
            tvPhone.setTextColor(getContext().getColor(android.R.color.white));
        }

        return convertView;
    }

    private View createDropdownView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.spinner_customer_item, parent, false);
        }

        CustomerEntity customer = customers.get(position);
        
        TextView tvName = convertView.findViewById(R.id.tvName);
        TextView tvPhone = convertView.findViewById(R.id.tvPhone);

        String name = customer.getFullName();
        if (name == null || name.trim().isEmpty()) {
            name = customer.getPhoneNumber() != null && !customer.getPhoneNumber().trim().isEmpty()
                    ? customer.getPhoneNumber()
                    : (customer.getId() != null ? customer.getId() : "Unknown");
        }

        tvName.setText(name);
        tvPhone.setText(customer.getPhoneNumber() != null ? customer.getPhoneNumber() : "");

        return convertView;
    }
}

