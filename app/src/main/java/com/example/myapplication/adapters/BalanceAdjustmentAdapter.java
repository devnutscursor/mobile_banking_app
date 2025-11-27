package com.example.myapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.database.entities.BalanceAdjustmentEntity;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BalanceAdjustmentAdapter extends RecyclerView.Adapter<BalanceAdjustmentAdapter.AdjustmentViewHolder> {
    
    private Context context;
    private List<BalanceAdjustmentEntity> adjustments;
    private SimpleDateFormat dateFormat;
    private NumberFormat currencyFormat;
    
    public BalanceAdjustmentAdapter(Context context, List<BalanceAdjustmentEntity> adjustments) {
        this.context = context;
        this.adjustments = adjustments != null ? adjustments : new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        this.currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());
    }
    
    @NonNull
    @Override
    public AdjustmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_balance_adjustment, parent, false);
        return new AdjustmentViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull AdjustmentViewHolder holder, int position) {
        BalanceAdjustmentEntity adjustment = adjustments.get(position);
        
        // Type
        String type = "operator".equals(adjustment.getAdjustmentType()) ? 
                context.getString(R.string.operator_balance) : 
                context.getString(R.string.cash_balance);
        holder.tvType.setText(type);
        
        // Amount (with +/- sign)
        double amount = adjustment.getAmount();
        String amountText = (amount >= 0 ? "+" : "") + currencyFormat.format(amount);
        holder.tvAmount.setText(amountText);
        holder.tvAmount.setTextColor(ContextCompat.getColor(context, 
                amount >= 0 ? R.color.success_green : R.color.error_red));
        
        // Balance before/after
        holder.tvBalanceBefore.setText(currencyFormat.format(adjustment.getBalanceBefore()));
        holder.tvBalanceAfter.setText(currencyFormat.format(adjustment.getBalanceAfter()));
        
        // Reason
        holder.tvReason.setText(adjustment.getReason() != null ? adjustment.getReason() : "");
        
        // Date
        holder.tvDate.setText(dateFormat.format(new Date(adjustment.getCreatedAt())));
    }
    
    @Override
    public int getItemCount() {
        return adjustments.size();
    }
    
    static class AdjustmentViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvType;
        TextView tvAmount;
        TextView tvBalanceBefore;
        TextView tvBalanceAfter;
        TextView tvReason;
        TextView tvDate;
        
        public AdjustmentViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            tvType = itemView.findViewById(R.id.tvType);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvBalanceBefore = itemView.findViewById(R.id.tvBalanceBefore);
            tvBalanceAfter = itemView.findViewById(R.id.tvBalanceAfter);
            tvReason = itemView.findViewById(R.id.tvReason);
            tvDate = itemView.findViewById(R.id.tvDate);
        }
    }
}



