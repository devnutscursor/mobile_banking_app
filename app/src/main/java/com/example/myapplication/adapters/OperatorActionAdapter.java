package com.example.myapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.database.entities.OperatorActionEntity;

import java.util.List;

public class OperatorActionAdapter extends RecyclerView.Adapter<OperatorActionAdapter.VH> {
    public interface OnEditListener { void onEdit(OperatorActionEntity a); }
    public interface OnDeleteListener { void onDelete(OperatorActionEntity a); }

    private final List<OperatorActionEntity> items; private final String currentUserId;
    private final OnEditListener onEdit; private final OnDeleteListener onDelete;

    public OperatorActionAdapter(List<OperatorActionEntity> items, String currentUserId,
                                 OnEditListener onEdit, OnDeleteListener onDelete) {
        this.items = items; this.currentUserId = currentUserId; this.onEdit = onEdit; this.onDelete = onDelete;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_operator_action, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        OperatorActionEntity a = items.get(position);
        
        // Translate action name
        String actionName = a.getName();
        String translatedName = translateActionName(h.itemView.getContext(), actionName);
        h.tvActionName.setText(translatedName);
        
        // Translate action type
        String actionType = a.getType();
        String translatedType = translateActionType(h.itemView.getContext(), actionType);
        h.tvActionType.setText(translatedType);
        h.tvUssdTemplate.setText(a.getUssdTemplate() == null ? "" : a.getUssdTemplate());
        
        // Set icon color based on action name
        String lowerActionName = actionName.toLowerCase();
        int iconColor;
        if (lowerActionName.contains("deposit")) {
            iconColor = h.itemView.getContext().getColor(R.color.error_red);
        } else if (lowerActionName.contains("withdrawal") || lowerActionName.contains("withdraw")) {
            iconColor = h.itemView.getContext().getColor(R.color.success_green);
        } else {
            iconColor = h.itemView.getContext().getColor(R.color.primary_purple);
        }
        h.ivActionIcon.setColorFilter(iconColor);
        
        boolean canEdit = currentUserId != null && currentUserId.equals(a.getAddedBy());
        h.ivEdit.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        h.ivDelete.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        h.ivEdit.setOnClickListener(v -> { if (onEdit != null) onEdit.onEdit(a); });
        h.ivDelete.setOnClickListener(v -> { if (onDelete != null) onDelete.onDelete(a); });
    }

    @Override public int getItemCount() { return items.size(); }
    
    private String translateActionName(Context context, String actionName) {
        if (actionName == null) return "";
        String lowerName = actionName.toLowerCase();
        if (lowerName.contains("deposit")) {
            return context.getString(R.string.deposit);
        } else if (lowerName.contains("withdrawal") || lowerName.contains("withdraw")) {
            return context.getString(R.string.withdrawal);
        } else if (lowerName.contains("transfer")) {
            return context.getString(R.string.transfer);
        }
        return actionName; // Return original if no match
    }
    
    private String translateActionType(Context context, String actionType) {
        if (actionType == null) return "";
        String upperType = actionType.toUpperCase();
        if (upperType.contains("USSD")) {
            return context.getString(R.string.type_ussd);
        } else if (upperType.contains("TRADITIONAL")) {
            return context.getString(R.string.type_traditional);
        }
        return actionType; // Return original if no match
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvActionName, tvActionType, tvUssdTemplate; 
        ImageView ivActionIcon, ivEdit, ivDelete;
        VH(@NonNull View itemView) { super(itemView);
            tvActionName = itemView.findViewById(R.id.tvActionName);
            tvActionType = itemView.findViewById(R.id.tvActionType);
            tvUssdTemplate = itemView.findViewById(R.id.tvUssdTemplate);
            ivActionIcon = itemView.findViewById(R.id.ivActionIcon);
            ivEdit = itemView.findViewById(R.id.ivEdit);
            ivDelete = itemView.findViewById(R.id.ivDelete);
        }
    }
}


