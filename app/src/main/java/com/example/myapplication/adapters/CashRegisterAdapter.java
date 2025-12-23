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
    
    public interface OnUssdRetryClickListener {
        void onUssdRetryClick(TransactionEntity transaction);
    }

    public interface OnPrintTicketClickListener {
        void onPrintTicketClick(TransactionEntity transaction);
    }
    
    public CashRegisterAdapter(Context context, OnTransactionClickListener listener) {
        this.context = context;
        this.transactions = new ArrayList<>();
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    }
    
    public void setUssdRetryListener(OnUssdRetryClickListener ussdRetryListener) {
        this.ussdRetryListener = ussdRetryListener;
    }

    public void setPrintTicketListener(OnPrintTicketClickListener printTicketListener) {
        this.printTicketListener = printTicketListener;
    }
    
    private OnUssdRetryClickListener ussdRetryListener;
    private OnPrintTicketClickListener printTicketListener;
    
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
        
        // Transaction code - show full ID
        String transactionId = transaction.getId();
        holder.tvTransactionCode.setText(transactionId.toUpperCase());
        
        // Customer name
        holder.tvCustomerName.setText(transaction.getCustomerName());
        
        // Operator and action - localize action name
        String actionName = transaction.getActionName();
        String localizedActionName = getLocalizedActionName(actionName);
        holder.tvOperatorAction.setText(transaction.getOperatorName() + " - " + localizedActionName);
        
        // Amount with thousands separator
        String formattedAmount = com.example.myapplication.utils.NumberFormatter.formatWithThousandsSeparator(transaction.getAmount());
        holder.tvAmount.setText(formattedAmount + " F");
        
        // Transaction type - use localized string
        String transactionType = transaction.getTransactionType();
        String localizedType = getLocalizedTransactionType(transactionType);
        holder.tvType.setText(localizedType);
        
        // Status with proper colors and localized text
        String status = transaction.getStatus().toLowerCase();
        String localizedStatus = getLocalizedStatus(status);
        holder.tvStatus.setText(localizedStatus);
        
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

        // Print ticket click listener - always available
        holder.ivPrintTicket.setOnClickListener(v -> {
            if (printTicketListener != null) {
                printTicketListener.onPrintTicketClick(transaction);
            } else {
                android.util.Log.e("CashRegisterAdapter", "printTicketListener is null!");
                android.widget.Toast.makeText(context, "Print ticket listener not set", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        
        // USSD retry button - show for USSD transactions or if channel is null (for backward compatibility)
        String channel = transaction.getChannel();
        android.util.Log.d("CashRegisterAdapter", "Transaction " + transaction.getId() + " - Channel: " + channel + ", Operator: " + transaction.getOperatorName());
        
        // Show button if channel is USSD, or if channel is null but we can infer it's USSD from operator type
        boolean isUssdTransaction = "USSD".equalsIgnoreCase(channel);
        
        // If channel is null, check operator type (for backward compatibility with old transactions)
        if (channel == null || channel.isEmpty()) {
            // Try to infer from operator - this is a fallback for old transactions
            // We'll show the button and let the activity handle the check
            isUssdTransaction = true; // Show button, let activity decide
        }
        
        if (isUssdTransaction) {
            holder.ivUssdRetry.setVisibility(View.VISIBLE);
            holder.ivUssdRetry.setClickable(true);
            holder.ivUssdRetry.setFocusable(true);
            holder.ivUssdRetry.setEnabled(true);
            
            // Clear any previous listener to avoid issues with view recycling
            holder.ivUssdRetry.setOnClickListener(null);
            
            // Set new listener
            holder.ivUssdRetry.setOnClickListener(v -> {
                android.util.Log.d("CashRegisterAdapter", "USSD retry button clicked for transaction: " + transaction.getId());
                if (ussdRetryListener != null) {
                    android.util.Log.d("CashRegisterAdapter", "Calling ussdRetryListener.onUssdRetryClick");
                    ussdRetryListener.onUssdRetryClick(transaction);
                } else {
                    android.util.Log.e("CashRegisterAdapter", "ussdRetryListener is null!");
                    android.widget.Toast.makeText(context, "USSD retry listener not set", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            holder.ivUssdRetry.setVisibility(View.GONE);
            holder.ivUssdRetry.setClickable(false);
            holder.ivUssdRetry.setOnClickListener(null);
        }
        
        // Remove card click listener - now only edit icon opens details
        holder.cardView.setOnClickListener(null);
    }
    
    @Override
    public int getItemCount() {
        return transactions.size();
    }
    
    /**
     * Get localized transaction type string
     */
    private String getLocalizedTransactionType(String type) {
        if (type == null) return "";
        
        String typeLower = type.toLowerCase();
        switch (typeLower) {
            case "deposit":
                return context.getString(R.string.deposit);
            case "withdrawal":
                return context.getString(R.string.withdrawal);
            case "transfer":
                return context.getString(R.string.transfer);
            default:
                return type; // Return original if no translation found
        }
    }
    
    /**
     * Get localized action name string
     * Actions like "Transfer", "Deposit", "Withdrawal" should be translated
     */
    private String getLocalizedActionName(String actionName) {
        if (actionName == null) return "";
        
        String actionLower = actionName.toLowerCase().trim();
        
        // Check for common transaction action names and translate them
        if (actionLower.equals("transfer") || actionLower.equals("transfert")) {
            return context.getString(R.string.transfer);
        } else if (actionLower.equals("deposit") || actionLower.equals("dépôt")) {
            return context.getString(R.string.deposit);
        } else if (actionLower.equals("withdrawal") || actionLower.equals("retrait")) {
            return context.getString(R.string.withdrawal);
        }
        
        // Return original if no translation found
        return actionName;
    }
    
    /**
     * Get localized status string
     */
    private String getLocalizedStatus(String status) {
        if (status == null) return "";
        
        String statusLower = status.toLowerCase();
        switch (statusLower) {
            case "successful":
            case "success":
                return context.getString(R.string.status_successful);
            case "pending":
            case "processing":
                return context.getString(R.string.status_pending);
            case "failed":
            case "failure":
                return context.getString(R.string.status_failed);
            case "canceled":
            case "cancelled":
                return context.getString(R.string.status_cancelled);
            default:
                return status; // Return original if no translation found
        }
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
        ImageView ivUssdRetry;
        ImageView ivPrintTicket;
        
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
            ivUssdRetry = itemView.findViewById(R.id.ivUssdRetry);
            ivPrintTicket = itemView.findViewById(R.id.ivPrintTicket);
        }
    }
}


