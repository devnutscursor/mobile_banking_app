package com.example.myapplication.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.database.entities.OperatorEntity;

import java.util.List;

public class OperatorAdapter extends RecyclerView.Adapter<OperatorAdapter.VH> {
    public interface OnEditListener { void onEdit(OperatorEntity operator); }
    public interface OnDeleteListener { void onDelete(OperatorEntity operator); }
    public interface OnClickListener { void onClick(OperatorEntity operator); }

    private final List<OperatorEntity> items;
    private final String currentUserId;
    private final OnEditListener onEdit;
    private final OnDeleteListener onDelete;
    private final OnClickListener onClick;

    public OperatorAdapter(List<OperatorEntity> items, String currentUserId,
                           OnEditListener onEdit, OnDeleteListener onDelete, OnClickListener onClick) {
        this.items = items;
        this.currentUserId = currentUserId;
        this.onEdit = onEdit; this.onDelete = onDelete; this.onClick = onClick;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_operator, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        OperatorEntity op = items.get(position);
        h.tvOperatorName.setText(op.getName());
        String type = op.getType();
        String code = op.getCode();
        String typeText = code == null || code.isEmpty() ? type : type + "  •  " + code;
        h.tvOperatorType.setText(typeText);
        h.tvOperatorStatus.setText(op.isEnabled() ? h.itemView.getContext().getString(R.string.active)
                                                  : h.itemView.getContext().getString(R.string.inactive));
        boolean canEdit = currentUserId != null && currentUserId.equals(op.getAddedBy());
        h.ivEdit.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        h.ivDelete.setVisibility(canEdit ? View.VISIBLE : View.GONE);

        // Apply color bar based on operator color
        View colorBar = h.itemView.findViewById(R.id.colorBar);
        if (colorBar != null) {
            int colorRes = R.color.primary_orange;
            String c = op.getColor();
            if ("purple".equals(c)) colorRes = R.color.primary_purple;
            else if ("blue".equals(c)) colorRes = R.color.info_blue;
            else if ("green".equals(c)) colorRes = R.color.success_green;
            else if ("amber".equals(c)) colorRes = R.color.warning_amber;
            else if ("red".equals(c)) colorRes = R.color.error_red;
            else if ("teal".equals(c)) colorRes = R.color.teal_200;
            else if ("indigo".equals(c)) colorRes = R.color.primary_blue_dark;
            colorBar.setBackgroundResource(colorRes);
        }

        h.itemView.setOnClickListener(v -> { if (onClick != null) onClick.onClick(op); });
        h.ivEdit.setOnClickListener(v -> { if (onEdit != null) onEdit.onEdit(op); });
        h.ivDelete.setOnClickListener(v -> { if (onDelete != null) onDelete.onDelete(op); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOperatorName, tvOperatorType, tvOperatorStatus; ImageView ivEdit, ivDelete;
        VH(@NonNull View itemView) { super(itemView);
            tvOperatorName = itemView.findViewById(R.id.tvOperatorName);
            tvOperatorType = itemView.findViewById(R.id.tvOperatorType);
            tvOperatorStatus = itemView.findViewById(R.id.tvOperatorStatus);
            ivEdit = itemView.findViewById(R.id.ivEdit);
            ivDelete = itemView.findViewById(R.id.ivDelete);
        }
    }
}


