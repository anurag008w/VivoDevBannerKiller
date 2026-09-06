package com.anurag.devbannerkiller;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    private final List<AppInfo> fullList = new ArrayList<>();
    private final List<AppInfo> displayedList = new ArrayList<>();
    private final OnSelectionChangedListener selectionListener;

    public AppListAdapter(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    public void setApps(List<AppInfo> apps) {
        fullList.clear();
        fullList.addAll(apps);
        filter("");
    }

    public void filter(String query) {
        displayedList.clear();
        if (query == null || query.trim().isEmpty()) {
            displayedList.addAll(fullList);
        } else {
            String lower = query.toLowerCase().trim();
            for (AppInfo app : fullList) {
                if (app.getAppName().toLowerCase().contains(lower) || app.getPackageName().toLowerCase().contains(lower)) {
                    displayedList.add(app);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void selectAll(boolean select) {
        for (AppInfo app : displayedList) {
            app.setSelected(select);
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(getSelectedPackages().size());
        }
    }

    public List<String> getSelectedPackages() {
        List<String> selected = new ArrayList<>();
        for (AppInfo app : fullList) {
            if (app.isSelected()) {
                selected.add(app.getPackageName());
            }
        }
        return selected;
    }

    public List<String> getAllPackages() {
        List<String> all = new ArrayList<>();
        for (AppInfo app : fullList) {
            all.add(app.getPackageName());
        }
        return all;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo app = displayedList.get(position);
        holder.tvAppName.setText(app.getAppName());
        holder.tvPackageName.setText(app.getPackageName());
        holder.ivAppIcon.setImageDrawable(app.getIcon());
        holder.cbSelected.setChecked(app.isSelected());

        holder.itemView.setOnClickListener(v -> {
            boolean newState = !app.isSelected();
            app.setSelected(newState);
            holder.cbSelected.setChecked(newState);
            if (selectionListener != null) {
                selectionListener.onSelectionChanged(getSelectedPackages().size());
            }
        });
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvPackageName;
        CheckBox cbSelected;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvPackageName = itemView.findViewById(R.id.tvPackageName);
            cbSelected = itemView.findViewById(R.id.cbSelected);
        }
    }
}
