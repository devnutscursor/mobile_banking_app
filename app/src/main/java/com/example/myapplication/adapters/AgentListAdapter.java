package com.example.myapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.database.entities.UserEntity;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class AgentListAdapter extends RecyclerView.Adapter<AgentListAdapter.AgentViewHolder> {
    
    private Context context;
    private List<UserEntity> agentList;
    private OnAgentClickListener listener;
    
    public interface OnAgentClickListener {
        void onAgentClick(UserEntity agent);
    }
    
    public AgentListAdapter(Context context, List<UserEntity> agentList, OnAgentClickListener listener) {
        this.context = context;
        this.agentList = agentList;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public AgentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_agent, parent, false);
        return new AgentViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull AgentViewHolder holder, int position) {
        UserEntity agent = agentList.get(position);
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());
        
        holder.tvAgentName.setText(agent.getName() != null ? agent.getName() : "N/A");
        holder.tvAgentEmail.setText(agent.getEmail() != null ? agent.getEmail() : "N/A");
        holder.tvAgentCredit.setText("Credit: " + 
                currencyFormat.format(agent.getVirtualCredit()));
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAgentClick(agent);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return agentList.size();
    }
    
    static class AgentViewHolder extends RecyclerView.ViewHolder {
        TextView tvAgentName;
        TextView tvAgentEmail;
        TextView tvAgentCredit;
        
        public AgentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAgentName = itemView.findViewById(R.id.tvAgentName);
            tvAgentEmail = itemView.findViewById(R.id.tvAgentEmail);
            tvAgentCredit = itemView.findViewById(R.id.tvAgentCredit);
        }
    }
}

