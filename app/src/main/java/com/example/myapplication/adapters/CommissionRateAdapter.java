package com.example.myapplication.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.database.entities.CommissionRateEntity;

import java.util.List;
import java.util.Locale;

public class CommissionRateAdapter extends RecyclerView.Adapter<CommissionRateAdapter.ViewHolder> {
    
    public interface OnEditListener {
        void onEdit(CommissionRateEntity rate);
    }
    
    public interface OnDeleteListener {
        void onDelete(CommissionRateEntity rate);
    }
    
    private final List<CommissionRateEntity> items;
    private final OnEditListener onEdit;
    private final OnDeleteListener onDelete;
    
    public CommissionRateAdapter(List<CommissionRateEntity> items, 
                                 OnEditListener onEdit, 
                                 OnDeleteListener onDelete) {
        this.items = items;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_commission_rate, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CommissionRateEntity rate = items.get(position);
        
        // Recalculate commission rate with tax to ensure correct value is displayed
        // This fixes any rates that were saved with the old (incorrect) calculation
        rate.calculateRateWithTax();
        
        holder.tvOperatorName.setText(rate.getOperatorName() != null ? rate.getOperatorName() : "Unknown");
        holder.tvCommissionRate.setText(String.format(Locale.US, "%.2f%%", rate.getCommissionRate()));
        holder.tvTaxRate.setText(String.format(Locale.US, "%.2f%%", rate.getTaxRate()));
        holder.tvCommissionWithTax.setText(String.format(Locale.US, "%.4f%%", rate.getCommissionRateWithTax()));
        
        // Display transaction types
        String types = rate.getTransactionTypes();
        if (types != null && !types.isEmpty()) {
            java.util.List<String> typeList = new java.util.ArrayList<>();
            android.content.Context context = holder.itemView.getContext();
            if (types.contains("deposit")) typeList.add(context.getString(R.string.deposit));
            if (types.contains("withdrawal")) typeList.add(context.getString(R.string.withdrawal));
            if (types.contains("transfer")) typeList.add(context.getString(R.string.transfer));
            
            if (!typeList.isEmpty()) {
                // Join with comma and space for better readability
                holder.tvTransactionTypes.setText(android.text.TextUtils.join(", ", typeList));
            } else {
                holder.tvTransactionTypes.setText(types);
            }
        } else {
            holder.tvTransactionTypes.setText("N/A");
        }
        
        holder.ivEdit.setOnClickListener(v -> {
            if (onEdit != null) {
                onEdit.onEdit(rate);
            }
        });
        
        holder.ivDelete.setOnClickListener(v -> {
            if (onDelete != null) {
                onDelete.onDelete(rate);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOperatorName;
        TextView tvCommissionRate;
        TextView tvTaxRate;
        TextView tvCommissionWithTax;
        TextView tvTransactionTypes;
        ImageView ivEdit;
        ImageView ivDelete;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOperatorName = itemView.findViewById(R.id.tvOperatorName);
            tvCommissionRate = itemView.findViewById(R.id.tvCommissionRate);
            tvTaxRate = itemView.findViewById(R.id.tvTaxRate);
            tvCommissionWithTax = itemView.findViewById(R.id.tvCommissionWithTax);
            tvTransactionTypes = itemView.findViewById(R.id.tvTransactionTypes);
            ivEdit = itemView.findViewById(R.id.ivEdit);
            ivDelete = itemView.findViewById(R.id.ivDelete);
        }
    }
}






