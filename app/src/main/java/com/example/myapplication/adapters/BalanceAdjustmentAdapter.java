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
import com.example.myapplication.utils.LanguageManager;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BalanceAdjustmentAdapter extends RecyclerView.Adapter<BalanceAdjustmentAdapter.AdjustmentViewHolder> {
    
    private Context context;
    private List<BalanceAdjustmentEntity> adjustments;
    private SimpleDateFormat dateFormat;
    private DecimalFormat numberFormat;
    
    public BalanceAdjustmentAdapter(Context context, List<BalanceAdjustmentEntity> adjustments) {
        this.context = context;
        this.adjustments = adjustments != null ? adjustments : new ArrayList<>();
        
        // Get current language from LanguageManager
        LanguageManager languageManager = LanguageManager.getInstance(context);
        String language = languageManager.getCurrentLanguage();
        Locale locale = new Locale(language);
        
        // Create date format with correct locale
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", locale);
        
        // Create number format with comma as thousands separator (consistent across all languages)
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(','); // Comma for thousands separator
        symbols.setDecimalSeparator('.');  // Dot for decimal separator
        
        this.numberFormat = new DecimalFormat("#,##0.00", symbols);
        this.numberFormat.setGroupingUsed(true);
        this.numberFormat.setGroupingSize(3);
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
        String formattedAmount = numberFormat.format(Math.abs(amount));
        String amountText = (amount >= 0 ? "+" : "") + formattedAmount + " ¤";
        holder.tvAmount.setText(amountText);
        holder.tvAmount.setTextColor(ContextCompat.getColor(context, 
                amount >= 0 ? R.color.success_green : R.color.error_red));
        
        // Balance before/after
        String balanceBefore = numberFormat.format(adjustment.getBalanceBefore()) + " ¤";
        String balanceAfter = numberFormat.format(adjustment.getBalanceAfter()) + " ¤";
        holder.tvBalanceBefore.setText(balanceBefore);
        holder.tvBalanceAfter.setText(balanceAfter);
        
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



