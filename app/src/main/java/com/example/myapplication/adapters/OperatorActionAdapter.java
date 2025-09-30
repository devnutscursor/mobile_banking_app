package com.example.myapplication.adapters;

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
        h.tvActionName.setText(a.getName());
        h.tvActionType.setText(a.getType());
        h.tvUssdTemplate.setText(a.getUssdTemplate() == null ? "" : a.getUssdTemplate());
        boolean canEdit = currentUserId != null && currentUserId.equals(a.getAddedBy());
        h.ivEdit.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        h.ivDelete.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        h.ivEdit.setOnClickListener(v -> { if (onEdit != null) onEdit.onEdit(a); });
        h.ivDelete.setOnClickListener(v -> { if (onDelete != null) onDelete.onDelete(a); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvActionName, tvActionType, tvUssdTemplate; ImageView ivEdit, ivDelete;
        VH(@NonNull View itemView) { super(itemView);
            tvActionName = itemView.findViewById(R.id.tvActionName);
            tvActionType = itemView.findViewById(R.id.tvActionType);
            tvUssdTemplate = itemView.findViewById(R.id.tvUssdTemplate);
            ivEdit = itemView.findViewById(R.id.ivEdit);
            ivDelete = itemView.findViewById(R.id.ivDelete);
        }
    }
}


