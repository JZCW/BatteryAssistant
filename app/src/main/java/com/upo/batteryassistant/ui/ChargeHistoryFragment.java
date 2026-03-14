package com.upo.batteryassistant.ui;

import android.content.Intent;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
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
    private SwipeRefreshLayout swipeRefreshLayout;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_charge_history, container, false);
        
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        recyclerView = view.findViewById(R.id.recycler_view);
        historyManager = ChargeHistoryManager.getInstance(requireContext());
        mainHandler = new Handler(Looper.getMainLooper());
        
        setupRecyclerView();
        setupSwipeRefresh();
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 进入页面时刷新一次（强制持久化后拉取首页）
        if (!isLoading) {
            refreshAll();
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

    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::refreshAll);
        }
    }

    private void refreshAll() {
        if (isLoading) return;
        isLoading = true;
        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }

        new Thread(() -> {
            // 刷新前先强制持久化一次进行中会话
            historyManager.forcePersistNow();

            // 拉取第一页数据
            List<ChargeSession> sessions = historyManager.getSessions(0, PAGE_SIZE);

            mainHandler.post(() -> {
                adapter.setItems(sessions);
                currentPage = 1;
                hasMore = sessions.size() == PAGE_SIZE;
                isLoading = false;
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }).start();
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

        public void setItems(List<ChargeSession> newSessions) {
            sessions.clear();
            sessions.addAll(newSessions);
            notifyDataSetChanged();
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
                long duration = session.getEndTimestamp() - session.getStartTimestamp();
                long hours = duration / (60 * 60 * 1000);
                long minutes = (duration % (60 * 60 * 1000)) / (60 * 1000);
                String durationStr;
                if (hours > 0) {
                    durationStr = String.format("%d小时%d分钟", hours, minutes);
                } else {
                    durationStr = String.format("%d分钟", minutes);
                }
                durationText.setText("持续时间: " + durationStr);
                
                // 电量变化
                int levelChange = session.getLevelChange();
                String levelChangeStr = levelChange > 0 ? 
                    String.format("+%d%%", levelChange) : 
                    String.format("%d%%", levelChange);
                levelChangeText.setText("电量变化: " + levelChangeStr);
                
                // 开始状态（使用新的字段）
                String startInfo = String.format("开始: %d%%", session.getStartLevel());
                startInfoText.setText(startInfo);
                
                // 结束状态（使用新的字段）
                String endInfo = String.format("结束: %d%% | 最高%.1f°C | 最低%.1f°C",
                    session.getEndLevel(),
                    session.getMaxTemperatureCelsius(),
                    session.getMinTemperatureCelsius());
                endInfoText.setText(endInfo);
                
                // 点击跳转到详情页面
                itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), ChargeSessionDetailActivity.class);
                    intent.putExtra(ChargeSessionDetailActivity.EXTRA_SESSION, session);
                    itemView.getContext().startActivity(intent);
                });
            }
        }
    }
}

