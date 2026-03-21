package com.upo.batteryassistant.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
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
import java.util.Calendar;
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
    private int currentOffsetStart = 0;
    private static final int PAGE_SIZE = 20;
    private static final int JUMP_WINDOW_PAGES = 3;
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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 进入页面时刷新一次（强制持久化后拉取首页）
        if (!isLoading) {
            refreshAll();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_charge_history, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_jump_to_date) {
            showDatePicker();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
            swipeRefreshLayout.setOnRefreshListener(this::onSwipeRefresh);
        }
    }

    private void onSwipeRefresh() {
        if (isLoading) return;
        isLoading = true;
        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }

        new Thread(() -> {
            Log.d("ChargeHistoryFragment", "Swipe refreshing with force persist");
            // 下拉刷新时强制持久化一次进行中会话
            historyManager.forcePersistNow();

            // 拉取第一页数据
            List<ChargeSession> sessions = historyManager.getSessions(0, PAGE_SIZE);

            mainHandler.post(() -> {
                adapter.setItems(sessions);
                currentOffsetStart = 0;
                hasMore = sessions.size() >= PAGE_SIZE;
                isLoading = false;
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }).start();
    }

    private void refreshAll() {
        if (isLoading) return;
        isLoading = true;
        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }

        new Thread(() -> {
            Log.d("ChargeHistoryFragment", "Refreshing all data");

            // 拉取第一页数据
            List<ChargeSession> sessions = historyManager.getSessions(0, PAGE_SIZE);

            mainHandler.post(() -> {
                adapter.setItems(sessions);
                currentOffsetStart = 0;
                hasMore = sessions.size() >= PAGE_SIZE;
                isLoading = false;
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }).start();
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(Calendar.YEAR, year);
            selected.set(Calendar.MONTH, month);
            selected.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            selected.set(Calendar.HOUR_OF_DAY, 23);
            selected.set(Calendar.MINUTE, 59);
            selected.set(Calendar.SECOND, 59);
            selected.set(Calendar.MILLISECOND, 999);

            long endOfDay = selected.getTimeInMillis();
            jumpToDate(selected, endOfDay);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.setTitle(R.string.charge_history_select_date);
        dialog.show();
    }

    private void jumpToDate(Calendar selectedDate, long endOfDayMillis) {
        if (isLoading) {
            Toast.makeText(requireContext(), R.string.charge_history_jump_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        isLoading = true;
        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }

        new Thread(() -> {
            ChargeSession targetSession = historyManager.getLastSessionEndBefore(endOfDayMillis);
            if (targetSession == null) {
                mainHandler.post(() -> {
                    isLoading = false;
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    Toast.makeText(requireContext(), R.string.charge_history_jump_no_result, Toast.LENGTH_SHORT).show();
                });
                return;
            }

            int beforeCount = historyManager.countSessionsStartAfter(targetSession.getStartTimestamp());
            int targetPosition = beforeCount;
            int windowStart = Math.max(0, targetPosition - PAGE_SIZE);
            int windowLimit = PAGE_SIZE * JUMP_WINDOW_PAGES;
            List<ChargeSession> windowData = historyManager.getSessions(windowStart, windowLimit);
            int targetLocalPosition = targetPosition - windowStart;

            mainHandler.post(() -> {
                if (targetLocalPosition < 0 || targetLocalPosition >= windowData.size()) {
                    isLoading = false;
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    Toast.makeText(requireContext(), R.string.charge_history_jump_failed, Toast.LENGTH_SHORT).show();
                    return;
                }

                adapter.setItems(windowData);
                currentOffsetStart = windowStart;
                hasMore = windowData.size() == windowLimit;
                isLoading = false;
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }

                adapter.highlightSession(targetSession.getId());
                recyclerView.post(() -> recyclerView.smoothScrollToPosition(targetLocalPosition));

                String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.getTime());
                Toast.makeText(requireContext(), getString(R.string.charge_history_highlight_today, dateStr), Toast.LENGTH_SHORT).show();

                mainHandler.postDelayed(() -> adapter.clearHighlight(), 2000);
            });
        }).start();
    }
    
    private void loadMoreData() {
        if (isLoading || !hasMore) return;

        isLoading = true;

        new Thread(() -> {
            int offset = currentOffsetStart + adapter.getItemCount();
            List<ChargeSession> sessions = historyManager.getSessions(offset, PAGE_SIZE);

            if (sessions.isEmpty()) {
                hasMore = false;
            }

            mainHandler.post(() -> {
                adapter.addItems(sessions);
                isLoading = false;
                if (sessions.size() < PAGE_SIZE) {
                    hasMore = false;
                }
            });
        }).start();
    }
    
    /**
     * 充放电阶段适配器
     */
    private static class ChargeSessionAdapter extends RecyclerView.Adapter<ChargeSessionAdapter.ViewHolder> {
        private List<ChargeSession> sessions = new ArrayList<>();
        private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
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

        public void highlightSession(long sessionId) {
            highlightedSessionId = sessionId;
            notifyDataSetChanged();
        }

        public void clearHighlight() {
            highlightedSessionId = -1;
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

                if (session.getId() == highlightedSessionId) {
                    itemView.setBackgroundColor(0x33FF9800);
                } else {
                    itemView.setBackgroundColor(Color.TRANSPARENT);
                }
            }
        }
    }
}

