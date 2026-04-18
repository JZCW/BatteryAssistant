package com.upo.batteryassistant.ui.adapter;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.upo.batteryassistant.R;
import com.upo.batteryassistant.data.ChargeSession;
import com.upo.batteryassistant.ui.ChargeSessionDetailActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChargeSessionAdapter extends RecyclerView.Adapter<ChargeSessionAdapter.ViewHolder> {
    private final List<ChargeSession> sessions = new ArrayList<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private long highlightedSessionId = -1;

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_charge_session, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(sessions.get(position), highlightedSessionId);
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    public void addItems(List<ChargeSession> newSessions) {
        int startPosition = sessions.size();
        sessions.addAll(newSessions);
        notifyItemRangeInserted(startPosition, newSessions.size());
    }

    public void setItems(List<ChargeSession> newSessions) {
        sessions.clear();
        sessions.addAll(newSessions);
        notifyDataSetChanged();
    }

    public void highlightSession(long sessionId) {
        long previousSessionId = highlightedSessionId;
        highlightedSessionId = sessionId;
        notifyHighlightChanged(previousSessionId);
        notifyHighlightChanged(highlightedSessionId);
    }

    public void clearHighlight() {
        long previousSessionId = highlightedSessionId;
        highlightedSessionId = -1;
        notifyHighlightChanged(previousSessionId);
    }

    private void notifyHighlightChanged(long sessionId) {
        if (sessionId < 0) {
            return;
        }
        int position = findPositionById(sessionId);
        if (position >= 0) {
            notifyItemChanged(position);
        }
    }

    private int findPositionById(long sessionId) {
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getId() == sessionId) {
                return i;
            }
        }
        return -1;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView typeText;
        private final TextView timeText;
        private final TextView durationText;
        private final TextView levelChangeText;
        private final TextView startInfoText;
        private final TextView endInfoText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            typeText = itemView.findViewById(R.id.type_text);
            timeText = itemView.findViewById(R.id.time_text);
            durationText = itemView.findViewById(R.id.duration_text);
            levelChangeText = itemView.findViewById(R.id.level_change_text);
            startInfoText = itemView.findViewById(R.id.start_info_text);
            endInfoText = itemView.findViewById(R.id.end_info_text);
        }

        void bind(ChargeSession session, long highlightedSessionId) {
            android.content.Context context = itemView.getContext();

            typeText.setText(session.getSessionTypeText());

            String startTime = dateFormat.format(new Date(session.getStartTimestamp()));
            String endTime = dateFormat.format(new Date(session.getEndTimestamp()));
            String timeRange = context.getString(R.string.charge_history_time_range, startTime, endTime);
            timeText.setText(timeRange);

            long duration = session.getEndTimestamp() - session.getStartTimestamp();
            long hours = duration / (60 * 60 * 1000);
            long minutes = (duration % (60 * 60 * 1000)) / (60 * 1000);
            String durationStr;
            if (hours > 0) {
                durationStr = context.getString(R.string.charge_history_duration_hours_minutes, hours, minutes);
            } else {
                durationStr = context.getString(R.string.charge_history_duration_minutes, minutes);
            }
            String durationLabel = context.getString(R.string.charge_history_duration_label, durationStr);
            durationText.setText(durationLabel);

            int levelChange = session.getLevelChange();
            String levelChangeStr = levelChange > 0
                ? String.format("+%d%%", levelChange)
                : String.format("%d%%", levelChange);
            String levelChangeLabel = context.getString(R.string.charge_history_level_change_label, levelChangeStr);
            levelChangeText.setText(levelChangeLabel);

            String startInfo = context.getString(R.string.charge_history_start_level, session.getStartLevel());
            startInfoText.setText(startInfo);

            String endInfo = context.getString(R.string.charge_history_end_info,
                session.getEndLevel(),
                session.getMaxTemperatureCelsius(),
                session.getMinTemperatureCelsius());
            endInfoText.setText(endInfo);

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), ChargeSessionDetailActivity.class);
                intent.putExtra(ChargeSessionDetailActivity.EXTRA_SESSION, session);
                itemView.getContext().startActivity(intent);
            });

            if (session.getId() == highlightedSessionId) {
                itemView.setBackgroundColor(resolveThemeColor(itemView, R.attr.baColorListItemHighlight));
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        private int resolveThemeColor(@NonNull View view, int attrResId) {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            if (view.getContext().getTheme().resolveAttribute(attrResId, typedValue, true)) {
                if (typedValue.resourceId != 0) {
                    return view.getContext().getColor(typedValue.resourceId);
                }
                return typedValue.data;
            }
            return Color.TRANSPARENT;
        }
    }
}