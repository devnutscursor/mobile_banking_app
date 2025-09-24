package com.example.myapplication.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.database.entities.CustomerEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder> {
    
    private List<CustomerEntity> customers;
    private String currentUserId; // Current logged-in user ID
    private OnCustomerClickListener onCustomerClickListener;
    private OnCustomerEditListener onCustomerEditListener;
    private OnCustomerDeleteListener onCustomerDeleteListener;
    
    public interface OnCustomerClickListener {
        void onCustomerClick(CustomerEntity customer);
    }
    
    public interface OnCustomerEditListener {
        void onCustomerEdit(CustomerEntity customer);
    }
    
    public interface OnCustomerDeleteListener {
        void onCustomerDelete(CustomerEntity customer);
    }
    
    public CustomerAdapter(List<CustomerEntity> customers, 
                          String currentUserId,
                          OnCustomerClickListener onCustomerClickListener,
                          OnCustomerEditListener onCustomerEditListener,
                          OnCustomerDeleteListener onCustomerDeleteListener) {
        this.customers = customers;
        this.currentUserId = currentUserId;
        this.onCustomerClickListener = onCustomerClickListener;
        this.onCustomerEditListener = onCustomerEditListener;
        this.onCustomerDeleteListener = onCustomerDeleteListener;
    }
    
    @NonNull
    @Override
    public CustomerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false);
        return new CustomerViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull CustomerViewHolder holder, int position) {
        CustomerEntity customer = customers.get(position);
        holder.bind(customer, currentUserId);
    }
    
    @Override
    public int getItemCount() {
        return customers.size();
    }
    
    class CustomerViewHolder extends RecyclerView.ViewHolder {
        private TextView tvCustomerName;
        private TextView tvNationalId;
        private TextView tvPhoneNumber;
        private TextView tvAge;
        private TextView tvIdStatus;
        private TextView tvSyncStatus;
        private ImageView ivEdit;
        private ImageView ivDelete;
        
        public CustomerViewHolder(@NonNull View itemView) {
            super(itemView);
            
            tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
            tvNationalId = itemView.findViewById(R.id.tvNationalId);
            tvPhoneNumber = itemView.findViewById(R.id.tvPhoneNumber);
            tvAge = itemView.findViewById(R.id.tvAge);
            tvIdStatus = itemView.findViewById(R.id.tvIdStatus);
            tvSyncStatus = itemView.findViewById(R.id.tvSyncStatus);
            ivEdit = itemView.findViewById(R.id.ivEdit);
            ivDelete = itemView.findViewById(R.id.ivDelete);
            
            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (onCustomerClickListener != null) {
                    onCustomerClickListener.onCustomerClick(customers.get(getAdapterPosition()));
                }
            });
            
            ivEdit.setOnClickListener(v -> {
                if (onCustomerEditListener != null) {
                    onCustomerEditListener.onCustomerEdit(customers.get(getAdapterPosition()));
                }
            });
            
            ivDelete.setOnClickListener(v -> {
                if (onCustomerDeleteListener != null) {
                    onCustomerDeleteListener.onCustomerDelete(customers.get(getAdapterPosition()));
                }
            });
        }
        
        public void bind(CustomerEntity customer, String currentUserId) {
            tvCustomerName.setText(customer.getFullName());
            tvNationalId.setText(customer.getNationalIdNumber());
            tvPhoneNumber.setText(customer.getPhoneNumber());
            
            // Calculate and display age
            int age = customer.getAge();
            if (age > 0) {
                tvAge.setText(age + " " + itemView.getContext().getString(R.string.years_old));
            } else {
                tvAge.setText("-");
            }
            
            // ID status
            if (customer.isExpired()) {
                tvIdStatus.setText(itemView.getContext().getString(R.string.id_expired));
                tvIdStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_red_dark));
            } else {
                tvIdStatus.setText(itemView.getContext().getString(R.string.id_valid));
                tvIdStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_green_dark));
            }
            
            // Sync status
            if (customer.isNeedsSync()) {
                tvSyncStatus.setText(itemView.getContext().getString(R.string.pending_sync));
                tvSyncStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_orange_dark));
            } else {
                tvSyncStatus.setText(itemView.getContext().getString(R.string.synced));
                tvSyncStatus.setTextColor(itemView.getContext().getColor(android.R.color.holo_green_dark));
            }
            
            // Show edit/delete buttons only to the creator
            boolean canEdit = currentUserId != null && currentUserId.equals(customer.getCreatedBy());
            if (canEdit) {
                ivEdit.setVisibility(View.VISIBLE);
                ivDelete.setVisibility(View.VISIBLE);
            } else {
                ivEdit.setVisibility(View.GONE);
                ivDelete.setVisibility(View.GONE);
            }
        }
    }
}


