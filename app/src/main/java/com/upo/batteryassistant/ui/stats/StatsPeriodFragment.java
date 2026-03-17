package com.upo.batteryassistant.ui.stats;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.database.BatteryDatabaseHelper;
import com.upo.batteryassistant.manager.ChargeHistoryManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个周期（日/周/月）统计展示 Fragment。
 */
public class StatsPeriodFragment extends Fragment {
    private static final String ARG_PERIOD_TYPE = "arg_period_type";
    private static final int PAGE_SIZE = 20;
    private static final int PREFETCH_THRESHOLD = 5;

    private StatsPeriodType periodType = StatsPeriodType.DAILY;
    private ChargeHistoryManager historyManager;
    private Handler mainHandler;

    private SwipeRefreshHelper swipeHelper;
    private RecyclerView recyclerView;
    private StatsAdapter adapter;
    private LineChart lineChart;
    private TextView emptyView;

    private boolean isLoading = false;
    private boolean hasMore = true;
    private int currentOffset = 0;

    public static StatsPeriodFragment newInstance(StatsPeriodType type) {
        StatsPeriodFragment fragment = new StatsPeriodFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PERIOD_TYPE, type.ordinal());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int ordinal = getArguments() != null ? getArguments().getInt(ARG_PERIOD_TYPE, 0) : 0;
        periodType = StatsPeriodType.fromOrdinal(ordinal);
        historyManager = ChargeHistoryManager.getInstance(requireContext());
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats_period, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swipeHelper = new SwipeRefreshHelper(view.findViewById(R.id.swipe_refresh));
        recyclerView = view.findViewById(R.id.recycler_view);
        lineChart = view.findViewById(R.id.line_chart);
        emptyView = view.findViewById(R.id.empty_view);

        setupChart();
        setupRecyclerView();
        swipeHelper.setOnRefreshListener(this::refreshData);

        if (adapter.getItemCount() == 0) {
            swipeHelper.showRefreshing(true);
            refreshData();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void setupChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setNoDataText(getString(R.string.stats_chart_no_data));
        lineChart.setTouchEnabled(false);
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        lineChart.getAxisRight().setEnabled(false);
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new StatsAdapter(getResources());
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null || isLoading || !hasMore) {
                    return;
                }
                int visibleItemCount = lm.getChildCount();
                int totalItemCount = lm.getItemCount();
                int firstVisibleItem = lm.findFirstVisibleItemPosition();
                if ((firstVisibleItem + visibleItemCount) >= (totalItemCount - PREFETCH_THRESHOLD)) {
                    loadMore();
                }
            }
        });
    }

    private void refreshData() {
        if (isLoading) return;
        isLoading = true;
        hasMore = true;
        currentOffset = 0;
        loadStats(true);
    }

    private void loadMore() {
        if (isLoading || !hasMore) return;
        isLoading = true;
        loadStats(false);
    }

    private void loadStats(boolean replace) {
        final int offset = replace ? 0 : currentOffset;
        new Thread(() -> {
            List<StatsEntry> entries = queryStats(offset, PAGE_SIZE);
            boolean more = entries.size() >= PAGE_SIZE;
            mainHandler.post(() -> applyStatsResults(replace, entries, more));
        }).start();
    }

    private List<StatsEntry> queryStats(int offset, int limit) {
        List<StatsEntry> result = new ArrayList<>();
        switch (periodType) {
            case WEEKLY:
                List<BatteryDatabaseHelper.WeeklyStats> weeklyStats = historyManager.getWeeklyStats(offset, limit);
                for (BatteryDatabaseHelper.WeeklyStats stat : weeklyStats) {
                    String label = stat.getWeekStart();
                    String desc = getString(R.string.stats_period_week_desc, stat.getWeekStart());
                    result.add(buildEntry(label, desc, stat.getSessionCount(), stat.getTotalLevelChange(),
                        stat.getTotalChargeCounterDiff(), stat.getEstimatedCapacity(), stat.getCycleCount()));
                }
                break;
            case MONTHLY:
                List<BatteryDatabaseHelper.MonthlyStats> monthlyStats = historyManager.getMonthlyStats(offset, limit);
                for (BatteryDatabaseHelper.MonthlyStats stat : monthlyStats) {
                    String label = stat.getYearMonth();
                    String desc = getString(R.string.stats_period_month_desc, stat.getYearMonth());
                    result.add(buildEntry(label, desc, stat.getSessionCount(), stat.getTotalLevelChange(),
                        stat.getTotalChargeCounterDiff(), stat.getEstimatedCapacity(), stat.getCycleCount()));
                }
                break;
            case DAILY:
            default:
                List<BatteryDatabaseHelper.DailyStats> dailyStats = historyManager.getDailyStats(offset, limit);
                for (BatteryDatabaseHelper.DailyStats stat : dailyStats) {
                    String label = stat.getDate();
                    String desc = getString(R.string.stats_period_daily_desc, stat.getDate());
                    result.add(buildEntry(label, desc, stat.getSessionCount(), stat.getTotalLevelChange(),
                        stat.getTotalChargeCounterDiff(), stat.getEstimatedCapacity(), stat.getCycleCount()));
                }
                break;
        }
        return result;
    }

    private StatsEntry buildEntry(String label, String desc, int sessionCount,
                                  int totalLevelChange, int totalChargeCounterDiff,
                                  int estimatedCapacity, int cycleCount) {
        return new StatsEntry(label, desc, sessionCount, totalLevelChange,
            totalChargeCounterDiff, estimatedCapacity, cycleCount);
    }

    private void applyStatsResults(boolean replace, List<StatsEntry> newEntries, boolean more) {
        if (!isAdded()) {
            return;
        }
        if (replace) {
            adapter.setItems(newEntries);
            currentOffset = newEntries.size();
        } else {
            adapter.addItems(newEntries);
            currentOffset += newEntries.size();
        }
        hasMore = more;
        isLoading = false;
        swipeHelper.showRefreshing(false);
        updateEmptyState();
        updateChart(adapter.getItems());
    }

    private void updateEmptyState() {
        boolean empty = adapter.getItemCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        lineChart.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateChart(List<StatsEntry> entries) {
        if (entries.isEmpty()) {
            lineChart.clear();
            lineChart.invalidate();
            return;
        }
        List<StatsEntry> reversed = new ArrayList<>(entries);
        Collections.reverse(reversed);
        List<Entry> chartEntries = new ArrayList<>();
        for (int i = 0; i < reversed.size(); i++) {
            chartEntries.add(new Entry(i, reversed.get(i).getTotalLevelChange()));
        }
        LineDataSet dataSet = new LineDataSet(chartEntries, getString(R.string.stats_chart_dataset_label));
        int accentColor = ContextCompat.getColor(requireContext(), R.color.purple_500);
        dataSet.setColor(accentColor);
        dataSet.setCircleColor(accentColor);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < reversed.size()) {
                    return reversed.get(index).getPeriodLabel();
                }
                return "";
            }
        });
        lineChart.invalidate();
    }

    /**
     * 简单封装 SwipeRefreshLayout，避免空指针判断。
     */
    private static class SwipeRefreshHelper {
        private final androidx.swiperefreshlayout.widget.SwipeRefreshLayout layout;

        SwipeRefreshHelper(@Nullable androidx.swiperefreshlayout.widget.SwipeRefreshLayout layout) {
            this.layout = layout;
        }

        void setOnRefreshListener(Runnable runnable) {
            if (layout != null) {
                layout.setOnRefreshListener(() -> runnable.run());
            }
        }

        void showRefreshing(boolean refreshing) {
            if (layout != null) {
                layout.setRefreshing(refreshing);
            }
        }
    }
}
