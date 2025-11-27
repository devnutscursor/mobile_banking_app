package com.example.myapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.database.entities.TransactionEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CashRegisterAdapter extends RecyclerView.Adapter<CashRegisterAdapter.TransactionViewHolder> {
    
    private Context context;
    private List<TransactionEntity> transactions;
    private OnTransactionClickListener listener;
    private SimpleDateFormat dateFormat;
    
    public interface OnTransactionClickListener {
        void onTransactionClick(TransactionEntity transaction);
    }
    
    public CashRegisterAdapter(Context context, OnTransactionClickListener listener) {
        this.context = context;
        this.transactions = new ArrayList<>();
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    }
    
    public void setTransactions(List<TransactionEntity> transactions) {
        this.transactions = transactions;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_cash_register_transaction, parent, false);
        return new TransactionViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        TransactionEntity transaction = transactions.get(position);
        
        // Transaction code (first 8 characters of ID)
        holder.tvTransactionCode.setText(transaction.getId().substring(0, 8).toUpperCase());
        
        // Customer name
        holder.tvCustomerName.setText(transaction.getCustomerName());
        
        // Operator and action
        holder.tvOperatorAction.setText(transaction.getOperatorName() + " - " + transaction.getActionName());
        
        // Amount
        holder.tvAmount.setText(String.format("%.2f XAF", transaction.getAmount()));
        
        // Transaction type
        holder.tvType.setText(transaction.getTransactionType());
        
        // Status with proper colors
        String status = transaction.getStatus().toLowerCase();
        holder.tvStatus.setText(transaction.getStatus());
        
        // Set status background color based on status
        int statusBackgroundRes;
        switch (status) {
            case "successful":
            case "success":
                statusBackgroundRes = R.drawable.badge_success;
                break;
            case "failed":
            case "failure":
                statusBackgroundRes = R.drawable.badge_failed;
                break;
            case "canceled":
            case "cancelled":
                statusBackgroundRes = R.drawable.badge_failed;
                break;
            case "pending":
            case "processing":
            default:
                statusBackgroundRes = R.drawable.badge_pending;
                break;
        }
        holder.tvStatus.setBackgroundResource(statusBackgroundRes);
        
        // Date - with debugging
        long createdAt = transaction.getCreatedAt();
        String dateString = dateFormat.format(new Date(createdAt));
        holder.tvDate.setText(dateString);
        
        // Debug log for this specific transaction
        android.util.Log.d("CashRegister", "Transaction " + transaction.getId() + 
            " - createdAt: " + createdAt + " (" + new java.util.Date(createdAt).toString() + 
            "), displayed as: " + dateString);
        
        // Edit icon click listener (opens transaction details)
        holder.ivEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTransactionClick(transaction);
            }
        });
        
        // Remove card click listener - now only edit icon opens details
        holder.cardView.setOnClickListener(null);
    }
    
    @Override
    public int getItemCount() {
        return transactions.size();
    }
    
    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvTransactionCode;
        TextView tvCustomerName;
        TextView tvOperatorAction;
        TextView tvAmount;
        TextView tvType;
        TextView tvStatus;
        TextView tvDate;
        ImageView ivEdit;
        
        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            tvTransactionCode = itemView.findViewById(R.id.tvTransactionCode);
            tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
            tvOperatorAction = itemView.findViewById(R.id.tvOperatorAction);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvType = itemView.findViewById(R.id.tvType);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvDate = itemView.findViewById(R.id.tvDate);
            ivEdit = itemView.findViewById(R.id.ivEdit);
        }
    }
}


