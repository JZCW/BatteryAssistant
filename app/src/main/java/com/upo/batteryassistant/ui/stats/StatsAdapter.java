package com.upo.batteryassistant.ui.stats;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.upo.batteryassistant.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 统计条目列表适配器。
 */
public class StatsAdapter extends RecyclerView.Adapter<StatsAdapter.ViewHolder> {
    private final List<StatsEntry> items = new ArrayList<>();
    private final Resources resources;

    public StatsAdapter(Resources resources) {
        this.resources = resources;
    }

    public void setItems(List<StatsEntry> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void addItems(List<StatsEntry> newItems) {
        if (newItems == null || newItems.isEmpty()) {
            return;
        }
        int start = items.size();
        items.addAll(newItems);
        notifyItemRangeInserted(start, newItems.size());
    }

    public List<StatsEntry> getItems() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_stats_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), resources);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView periodLabel;
        private final TextView periodDescription;
        private final TextView sessionCount;
        private final TextView levelChange;
        private final TextView chargeCounterDiff;
        private final TextView estimatedCapacity;
        private final TextView cycleCount;
        private final TextView capacity;
        private final TextView maxLevelChange;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            periodLabel = itemView.findViewById(R.id.period_label);
            periodDescription = itemView.findViewById(R.id.period_description);
            sessionCount = itemView.findViewById(R.id.session_count);
            levelChange = itemView.findViewById(R.id.level_change);
            chargeCounterDiff = itemView.findViewById(R.id.charge_counter_diff);
            estimatedCapacity = itemView.findViewById(R.id.estimated_capacity);
            cycleCount = itemView.findViewById(R.id.cycle_count);
            capacity = itemView.findViewById(R.id.capacity);
            maxLevelChange = itemView.findViewById(R.id.max_level_change);
        }

        void bind(StatsEntry entry, Resources resources) {
            periodLabel.setText(entry.getPeriodLabel());
            periodDescription.setText(entry.getPeriodDescription());
            sessionCount.setText(resources.getString(R.string.stats_session_count_value, entry.getSessionCount()));

            String levelChangeFormatted = resources.getString(
                R.string.stats_signed_value_with_unit,
                entry.getTotalLevelChange(), "%");
            levelChange.setText(resources.getString(R.string.stats_level_change_value, levelChangeFormatted));

            String counterDiffFormatted = resources.getString(
                R.string.stats_signed_value_with_unit,
                entry.getTotalChargeCounterDiff(), "mAh");
            chargeCounterDiff.setText(resources.getString(R.string.stats_charge_counter_diff_value, counterDiffFormatted));

            estimatedCapacity.setText(resources.getString(R.string.stats_estimated_capacity_value, entry.getEstimatedCapacity()));
            cycleCount.setText(resources.getString(R.string.stats_cycle_count_value, entry.getCycleCount()));
            capacity.setText(resources.getString(R.string.stats_estimated_capacity_value, entry.getCapacity()));
            maxLevelChange.setText(resources.getString(R.string.stats_level_change_value, entry.getMaxLevelChange()) + "%");
        }
    }
}
