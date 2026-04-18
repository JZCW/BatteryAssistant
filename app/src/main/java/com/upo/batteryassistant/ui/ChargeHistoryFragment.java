package com.upo.batteryassistant.ui;

import android.app.DatePickerDialog;
import android.util.TypedValue;
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
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.ui.adapter.ChargeSessionAdapter;
import com.upo.batteryassistant.data.ChargeSession;
import com.upo.batteryassistant.manager.ChargeHistoryManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
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
    private TextView emptyView;
    
    // 滚动位置保存
    private int savedScrollPosition = -1;
    private boolean needsDataReload = true;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_charge_history, container, false);
        
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        recyclerView = view.findViewById(R.id.recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
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
        // 如果有保存的位置且不需要强制重新加载，则恢复位置
        if (savedScrollPosition >= 0 && !needsDataReload) {
            restoreScrollPosition();
        } else {
            // 初次加载或需要强制刷新时重新加载数据
            reloadFirstPage(true);
            needsDataReload = false;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        saveScrollPosition();
        // 标记离开时有保存位置，下次返回时应恢复（除非主动刷新）
        needsDataReload = false;
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
            int accentColor = resolveThemeColor(R.attr.baColorStatsAccent);
            int progressBackgroundColor = resolveThemeColor(R.attr.baColorStatsCardBackground);
            swipeRefreshLayout.setColorSchemeColors(accentColor);
            swipeRefreshLayout.setProgressBackgroundColorSchemeColor(progressBackgroundColor);
            swipeRefreshLayout.setOnRefreshListener(() -> {
                needsDataReload = true;  // 手动刷新时强制重新加载
                onSwipeRefresh();
            });
        }
    }

    private int resolveThemeColor(int attrResId) {
        if (swipeRefreshLayout == null) {
            return 0;
        }
        TypedValue typedValue = new TypedValue();
        if (swipeRefreshLayout.getContext().getTheme().resolveAttribute(attrResId, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return swipeRefreshLayout.getContext().getColor(typedValue.resourceId);
            }
            return typedValue.data;
        }
        return 0;
    }

    private void onSwipeRefresh() {
        reloadFirstPage(true);
    }

    private void reloadFirstPage(boolean forcePersist) {
        if (isLoading) return;
        isLoading = true;
        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }

        new Thread(() -> {
            if (forcePersist) {
                historyManager.forcePersistNowSync();
            }
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
                updateEmptyState();
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

    private void updateEmptyState() {
        if (emptyView == null || recyclerView == null || adapter == null) {
            return;
        }
        boolean empty = adapter.getItemCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
    
    /**
     * 保存当前的滚动位置和数据偏移
     */
    private void saveScrollPosition() {
        if (recyclerView == null || recyclerView.getLayoutManager() == null) {
            return;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        savedScrollPosition = layoutManager.findFirstVisibleItemPosition();
    }
    
    /**
     * 恢复之前保存的滚动位置
     */
    private void restoreScrollPosition() {
        if (recyclerView == null || recyclerView.getLayoutManager() == null) {
            return;
        }
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        
        recyclerView.post(() -> {
            if (savedScrollPosition >= 0 && savedScrollPosition < adapter.getItemCount()) {
                layoutManager.scrollToPosition(savedScrollPosition);
            }
        });
    }
}

