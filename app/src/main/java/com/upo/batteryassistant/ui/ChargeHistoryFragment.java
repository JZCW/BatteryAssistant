package com.upo.batteryassistant.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.data.ChargeSession;
import com.upo.batteryassistant.manager.ChargeHistoryManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 充放电历史Fragment
 */
public class ChargeHistoryFragment extends Fragment {
    private RecyclerView recyclerView;
    private ChargeSessionAdapter adapter;
    private ChargeHistoryManager historyManager;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private int currentPage = 0;
    private static final int PAGE_SIZE = 20;
    private Handler mainHandler;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_charge_history, container, false);
        
        recyclerView = view.findViewById(R.id.recycler_view);
        historyManager = ChargeHistoryManager.getInstance(requireContext());
        mainHandler = new Handler(Looper.getMainLooper());
        
        setupRecyclerView();
        
        // 如果adapter为空或没有数据，加载数据
        if (adapter == null || adapter.getItemCount() == 0) {
            currentPage = 0;
            hasMore = true;
            loadMoreData();
        }
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 确保数据已加载
        if (adapter != null && adapter.getItemCount() == 0 && !isLoading) {
            currentPage = 0;
            hasMore = true;
            loadMoreData();
        }
    }
    
    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        
        adapter = new ChargeSessionAdapter();
        recyclerView.setAdapter(adapter);
        
        // 滚动监听，实现分页加载
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;
                
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                
                // 当滚动到底部附近时加载更多
                if (!isLoading && hasMore && 
                    (firstVisibleItem + visibleItemCount) >= totalItemCount - 5) {
                    loadMoreData();
                }
            }
        });
    }
    
    private void loadMoreData() {
        if (isLoading || !hasMore) return;
        
        isLoading = true;
        
        // 在后台线程加载数据
        new Thread(() -> {
            List<ChargeSession> sessions = historyManager.getSessions(
                currentPage * PAGE_SIZE, PAGE_SIZE);
            
            if (sessions.isEmpty()) {
                hasMore = false;
            } else {
                currentPage++;
            }
            
            // 在主线程更新UI
            mainHandler.post(() -> {
                adapter.addItems(sessions);
                isLoading = false;
            });
        }).start();
    }
    
    /**
     * 充放电阶段适配器
     */
    private static class ChargeSessionAdapter extends RecyclerView.Adapter<ChargeSessionAdapter.ViewHolder> {
        private List<ChargeSession> sessions = new ArrayList<>();
        private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_charge_session, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChargeSession session = sessions.get(position);
            holder.bind(session);
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
        
        class ViewHolder extends RecyclerView.ViewHolder {
            private android.widget.TextView typeText;
            private android.widget.TextView timeText;
            private android.widget.TextView durationText;
            private android.widget.TextView levelChangeText;
            private android.widget.TextView startInfoText;
            private android.widget.TextView endInfoText;
            
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                typeText = itemView.findViewById(R.id.type_text);
                timeText = itemView.findViewById(R.id.time_text);
                durationText = itemView.findViewById(R.id.duration_text);
                levelChangeText = itemView.findViewById(R.id.level_change_text);
                startInfoText = itemView.findViewById(R.id.start_info_text);
                endInfoText = itemView.findViewById(R.id.end_info_text);
            }
            
            void bind(ChargeSession session) {
                // 阶段类型
                typeText.setText(session.getSessionTypeText());
                
                // 开始时间
                String startTime = dateFormat.format(new Date(session.getStartTimestamp()));
                String endTime = dateFormat.format(new Date(session.getEndTimestamp()));
                timeText.setText(String.format("%s - %s", startTime, endTime));
                
                // 持续时间
                durationText.setText("持续时间: " + session.getDurationText());
                
                // 电量变化
                int levelChange = session.getLevelChange();
                String levelChangeStr = levelChange > 0 ? 
                    String.format("+%d%%", levelChange) : 
                    String.format("%d%%", levelChange);
                levelChangeText.setText("电量变化: " + levelChangeStr);
                
                // 开始状态
                String startInfo = String.format("开始: %d%% | %.1f°C | %.2fV",
                    session.getStartLevel(),
                    session.getStartTemperatureCelsius(),
                    session.getStartVoltageVolts());
                startInfoText.setText(startInfo);
                
                // 结束状态
                String endInfo = String.format("结束: %d%% | %.1f°C | %.2fV",
                    session.getEndLevel(),
                    session.getEndTemperatureCelsius(),
                    session.getEndVoltageVolts());
                endInfoText.setText(endInfo);
            }
        }
    }
}

