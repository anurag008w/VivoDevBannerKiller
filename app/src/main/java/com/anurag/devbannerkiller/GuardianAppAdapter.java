package com.anurag.devbannerkiller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GuardianAppAdapter extends RecyclerView.Adapter<GuardianAppAdapter.GuardianViewHolder> {

    public interface OnGuardianToggleListener {
        void onGuardianToggled(AppInfo app, boolean unkillable);
    }

    private final Context context;
    private final List<AppInfo> fullList = new ArrayList<>();
    private final List<AppInfo> displayedList = new ArrayList<>();
    private final OnGuardianToggleListener toggleListener;
    private boolean filterOnlyGuarded = false;
    private String currentQuery = "";

    public GuardianAppAdapter(Context context, OnGuardianToggleListener listener) {
        this.context = context;
        this.toggleListener = listener;
    }

    public void setApps(List<AppInfo> apps) {
        fullList.clear();
        fullList.addAll(apps);
        applyFilters();
    }

    public void filter(String query) {
        this.currentQuery = query != null ? query : "";
        applyFilters();
    }

    public void setFilterOnlyGuarded(boolean onlyGuarded) {
        this.filterOnlyGuarded = onlyGuarded;
        applyFilters();
    }

    public void applyFilters() {
        displayedList.clear();
        Set<String> guardedSet = AppGuardianHelper.getGuardedPackages(context);
        String lowerQuery = currentQuery.toLowerCase().trim();

        for (AppInfo app : fullList) {
            boolean matchesQuery = lowerQuery.isEmpty() ||
                    app.getAppName().toLowerCase().contains(lowerQuery) ||
                    app.getPackageName().toLowerCase().contains(lowerQuery);

            boolean matchesGuarded = !filterOnlyGuarded || guardedSet.contains(app.getPackageName());

            if (matchesQuery && matchesGuarded) {
                displayedList.add(app);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GuardianViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_guardian_app, parent, false);
        return new GuardianViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull GuardianViewHolder holder, int position) {
        AppInfo app = displayedList.get(position);
        holder.tvAppName.setText(app.getAppName());
        holder.tvPackageName.setText(app.getPackageName());

        if (app.getIcon() != null) {
            holder.ivAppIcon.setImageDrawable(app.getIcon());
        } else {
            holder.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        boolean isGuarded = AppGuardianHelper.isAppGuarded(context, app.getPackageName());

        holder.switchGuarded.setOnCheckedChangeListener(null);
        holder.switchGuarded.setChecked(isGuarded);
        holder.tvStatusBadge.setVisibility(isGuarded ? View.VISIBLE : View.GONE);

        holder.switchGuarded.setOnCheckedChangeListener((buttonView, isChecked) -> {
            holder.tvStatusBadge.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (toggleListener != null) {
                toggleListener.onGuardianToggled(app, isChecked);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            holder.switchGuarded.toggle();
        });
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    public static class GuardianViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvPackageName;
        TextView tvStatusBadge;
        SwitchCompat switchGuarded;

        public GuardianViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvPackageName = itemView.findViewById(R.id.tvPackageName);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            switchGuarded = itemView.findViewById(R.id.switchGuarded);
        }
    }
}
