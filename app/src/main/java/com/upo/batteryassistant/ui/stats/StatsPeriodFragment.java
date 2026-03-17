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

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.database.BatteryDatabaseHelper;
import com.upo.batteryassistant.manager.ChargeHistoryManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 单个周期（日/周/月）统计展示 Fragment。
 */
public class StatsPeriodFragment extends Fragment {
    private static final String ARG_PERIOD_TYPE = "arg_period_type";
    private static final int DAILY_MAX = 30;
    private static final int WEEKLY_MAX = 15;
    private static final int MONTHLY_MAX = 12;

    private StatsPeriodType periodType = StatsPeriodType.DAILY;
    private ChargeHistoryManager historyManager;
    private Handler mainHandler;

    private SwipeRefreshHelper swipeHelper;
    private BarChart statsChart;
    private TextView emptyView;
    private TextView detailHint;
    private View detailCard;
    private TextView detailTitle;
    private TextView detailDescription;
    private TextView detailSessionCount;
    private TextView detailLevelChange;
    private TextView detailChargeCounter;
    private TextView detailEstimatedCapacity;
    private TextView detailCycleCount;

    private final List<StatsEntry> statsEntries = new ArrayList<>();
    private final List<StatsEntry> chartOrderedEntries = new ArrayList<>();
    private boolean isLoading = false;
    private boolean pendingReload = false;
    private boolean isViewReady = false;
    private int highlightedIndex = -1;

    public static StatsPeriodFragment newInstance(StatsPeriodType type) {
        StatsPeriodFragment fragment = new StatsPeriodFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PERIOD_TYPE, type.ordinal());
        fragment.setArguments(args);
        return fragment;
    }

    public StatsPeriodType getPeriodType() {
        return periodType;
    }

    public void setPeriodType(@NonNull StatsPeriodType newType) {
        if (periodType == newType) {
            return;
        }
        periodType = newType;
        if (!isViewReady) {
            return;
        }
        if (swipeHelper != null) {
            swipeHelper.showRefreshing(true);
        }
        refreshData();
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
        statsChart = view.findViewById(R.id.stats_chart);
        emptyView = view.findViewById(R.id.empty_view);
        detailHint = view.findViewById(R.id.detail_hint);
        detailCard = view.findViewById(R.id.detail_card);
        detailTitle = view.findViewById(R.id.detail_title);
        detailDescription = view.findViewById(R.id.detail_description);
        detailSessionCount = view.findViewById(R.id.detail_session_count);
        detailLevelChange = view.findViewById(R.id.detail_level_change);
        detailChargeCounter = view.findViewById(R.id.detail_charge_counter);
        detailEstimatedCapacity = view.findViewById(R.id.detail_estimated_capacity);
        detailCycleCount = view.findViewById(R.id.detail_cycle_count);

        setupChart();
        swipeHelper.setOnRefreshListener(this::refreshData);

        isViewReady = true;
        swipeHelper.showRefreshing(true);
        refreshData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isViewReady = false;
        mainHandler.removeCallbacksAndMessages(null);
        statsChart = null;
        emptyView = null;
        detailHint = null;
        detailCard = null;
        detailTitle = null;
        detailDescription = null;
        detailSessionCount = null;
        detailLevelChange = null;
        detailChargeCounter = null;
        detailEstimatedCapacity = null;
        detailCycleCount = null;
        swipeHelper = null;
    }

    private void setupChart() {
        if (statsChart == null) {
            return;
        }
        statsChart.getDescription().setEnabled(false);
        statsChart.setNoDataText(getString(R.string.stats_chart_no_data));
        statsChart.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.stats_secondary_text));
        statsChart.setTouchEnabled(true);
        statsChart.setHighlightPerTapEnabled(true);
        statsChart.setDragEnabled(true);
        statsChart.setScaleEnabled(false);

        int primaryText = ContextCompat.getColor(requireContext(), R.color.stats_primary_text);
        int secondaryText = ContextCompat.getColor(requireContext(), R.color.stats_secondary_text);

        XAxis xAxis = statsChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(secondaryText);
        xAxis.setAxisLineColor(secondaryText);

        statsChart.getAxisRight().setEnabled(false);
        statsChart.getAxisLeft().setTextColor(secondaryText);
        statsChart.getAxisLeft().setGridColor(secondaryText);
        statsChart.getLegend().setTextColor(primaryText);
    }

    private void refreshData() {
        if (!isViewReady) {
            return;
        }
        if (isLoading) {
            pendingReload = true;
            return;
        }
        pendingReload = false;
        isLoading = true;
        loadStats();
    }

    private void loadStats() {
        final StatsPeriodType targetPeriod = periodType;
        final int limit = getMaxEntryCount(targetPeriod);
        new Thread(() -> {
            List<StatsEntry> entries = queryStats(targetPeriod, 0, limit);
            mainHandler.post(() -> applyStatsResults(targetPeriod, entries));
        }).start();
    }

    private int getMaxEntryCount(@NonNull StatsPeriodType type) {
        switch (type) {
            case WEEKLY:
                return WEEKLY_MAX;
            case MONTHLY:
                return MONTHLY_MAX;
            case DAILY:
            default:
                return DAILY_MAX;
        }
    }

    private List<StatsEntry> queryStats(StatsPeriodType targetPeriod, int offset, int limit) {
        List<StatsEntry> result = new ArrayList<>();
        switch (targetPeriod) {
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

    private void applyStatsResults(StatsPeriodType resultPeriod, List<StatsEntry> newEntries) {
        if (!isAdded() || !isViewReady) {
            isLoading = false;
            return;
        }
        if (resultPeriod != periodType) {
            isLoading = false;
            if (swipeHelper != null) {
                swipeHelper.showRefreshing(false);
            }
            if (pendingReload) {
                pendingReload = false;
                refreshData();
            }
            return;
        }
        List<StatsEntry> displayEntries = buildDisplayEntries(resultPeriod, newEntries);
        statsEntries.clear();
        statsEntries.addAll(displayEntries);
        isLoading = false;
        swipeHelper.showRefreshing(false);
        updateEmptyState();
        updateChart();

        if (pendingReload) {
            pendingReload = false;
            refreshData();
        }
    }

    private void updateEmptyState() {
        if (emptyView == null || statsChart == null || detailHint == null || detailCard == null) {
            return;
        }
        boolean empty = statsEntries.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        statsChart.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (empty) {
            detailHint.setVisibility(View.GONE);
            detailCard.setVisibility(View.GONE);
            highlightedIndex = -1;
            statsChart.highlightValue(null);
        }
    }

    private void updateChart() {
        if (statsChart == null) {
            return;
        }
        if (statsEntries.isEmpty()) {
            statsChart.clear();
            statsChart.invalidate();
            highlightedIndex = -1;
            return;
        }
        chartOrderedEntries.clear();
        chartOrderedEntries.addAll(statsEntries);
        Collections.reverse(chartOrderedEntries); // oldest to newest for chart X axis

        List<BarEntry> chartEntries = new ArrayList<>();
        for (int i = 0; i < chartOrderedEntries.size(); i++) {
            chartEntries.add(new BarEntry(i, chartOrderedEntries.get(i).getTotalLevelChange()));
        }
        BarDataSet dataSet = new BarDataSet(chartEntries, getString(R.string.stats_chart_dataset_label));
        int accentColor = ContextCompat.getColor(requireContext(), R.color.stats_accent);
        dataSet.setColor(accentColor);
        dataSet.setDrawValues(false);
        dataSet.setHighLightColor(accentColor);
        dataSet.setHighLightAlpha(180);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);
        statsChart.setData(barData);
        final StatsPeriodType axisPeriodType = periodType;
        statsChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < chartOrderedEntries.size()) {
                    return getAxisLabelForEntry(chartOrderedEntries.get(index), axisPeriodType);
                }
                return "";
            }
        });
        int visibleCount = Math.min(chartOrderedEntries.size(), getMaxEntryCount(periodType));
        statsChart.getXAxis().setLabelCount(visibleCount, true);
        statsChart.getXAxis().setAxisMinimum(-0.5f);
        statsChart.getXAxis().setAxisMaximum(Math.max(chartOrderedEntries.size() - 0.5f, visibleCount - 0.5f));
        statsChart.setVisibleXRangeMaximum(visibleCount);
        statsChart.setVisibleXRangeMinimum(visibleCount);
        statsChart.setFitBars(true);
        if (chartOrderedEntries.size() > visibleCount) {
            statsChart.moveViewToX(chartOrderedEntries.size() - visibleCount);
        } else {
            statsChart.moveViewToX(0f);
        }
        statsChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(com.github.mikephil.charting.data.Entry e, Highlight h) {
                int index = (int) h.getX();
                if (index >= 0 && index < chartOrderedEntries.size()) {
                    highlightedIndex = index;
                    showDetail(chartOrderedEntries.get(index));
                }
            }

            @Override
            public void onNothingSelected() {
                showLatestEntry();
            }
        });
        statsChart.invalidate();
        showLatestEntry();
    }

    private void showDetail(@NonNull StatsEntry entry) {
        if (detailHint == null || detailCard == null || detailTitle == null || detailDescription == null
            || detailSessionCount == null || detailLevelChange == null || detailChargeCounter == null
            || detailEstimatedCapacity == null || detailCycleCount == null) {
            return;
        }
        detailHint.setVisibility(View.GONE);
        detailCard.setVisibility(View.VISIBLE);

        detailTitle.setText(entry.getPeriodLabel());
        detailDescription.setText(entry.getPeriodDescription());
        detailSessionCount.setText(getString(R.string.stats_session_count_value, entry.getSessionCount()));

        String levelChangeFormatted = getString(R.string.stats_signed_value_with_unit,
            entry.getTotalLevelChange(), "%");
        detailLevelChange.setText(getString(R.string.stats_level_change_value, levelChangeFormatted));

        String chargeCounterFormatted = getString(R.string.stats_signed_value_with_unit,
            entry.getTotalChargeCounterDiff(), "mAh");
        detailChargeCounter.setText(getString(R.string.stats_charge_counter_diff_value, chargeCounterFormatted));

        detailEstimatedCapacity.setText(getString(R.string.stats_estimated_capacity_value, entry.getEstimatedCapacity()));
        detailCycleCount.setText(getString(R.string.stats_cycle_count_value, entry.getCycleCount()));
    }

    private void showLatestEntry() {
        if (statsEntries.isEmpty() || chartOrderedEntries.isEmpty()) {
            if (detailCard != null) {
                detailCard.setVisibility(View.GONE);
            }
            if (detailHint != null) {
                detailHint.setVisibility(View.GONE);
            }
            if (statsChart != null) {
                statsChart.highlightValue(null);
            }
            highlightedIndex = -1;
            return;
        }
        int latestIndex = chartOrderedEntries.size() - 1;
        highlightedIndex = latestIndex;
        StatsEntry latestEntry = chartOrderedEntries.get(latestIndex);
        showDetail(latestEntry);
        if (statsChart != null && statsChart.getData() != null) {
            statsChart.highlightValue(latestIndex, 0);
        }
    }

    private void clearSelection() {
        if (detailCard == null || detailHint == null || statsChart == null) {
            return;
        }
        if (statsEntries.isEmpty()) {
            detailCard.setVisibility(View.GONE);
            detailHint.setVisibility(View.GONE);
            statsChart.highlightValue(null);
            highlightedIndex = -1;
        } else {
            showLatestEntry();
        }
    }

    private List<StatsEntry> buildDisplayEntries(StatsPeriodType type, List<StatsEntry> rawEntries) {
        int limit = getMaxEntryCount(type);
        if (limit <= 0) {
            return new ArrayList<>();
        }
        Map<String, StatsEntry> entryMap = new HashMap<>();
        for (StatsEntry entry : rawEntries) {
            entryMap.put(entry.getPeriodLabel(), entry);
        }

        List<StatsEntry> filled = new ArrayList<>(limit);
        Calendar calendar = createAlignedCalendar(type);
        shiftCalendar(calendar, type, -(limit - 1));
        for (int i = 0; i < limit; i++) {
            String label = formatLabel(calendar, type);
            StatsEntry entry = entryMap.get(label);
            if (entry == null) {
                String description = buildDescription(type, label);
                entry = buildEntry(label, description, 0, 0, 0, 0, 0);
            }
            filled.add(entry);
            shiftCalendar(calendar, type, 1);
        }
        Collections.reverse(filled); // keep newest first for downstream logic
        return filled;
    }

    private Calendar createAlignedCalendar(StatsPeriodType type) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        switch (type) {
            case WEEKLY:
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                break;
            case MONTHLY:
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                break;
            case DAILY:
            default:
                break;
        }
        return calendar;
    }

    private void shiftCalendar(Calendar calendar, StatsPeriodType type, int steps) {
        switch (type) {
            case WEEKLY:
                calendar.add(Calendar.WEEK_OF_YEAR, steps);
                break;
            case MONTHLY:
                calendar.add(Calendar.MONTH, steps);
                break;
            case DAILY:
            default:
                calendar.add(Calendar.DAY_OF_YEAR, steps);
                break;
        }
    }

    private String formatLabel(Calendar calendar, StatsPeriodType type) {
        String pattern = type == StatsPeriodType.MONTHLY ? "yyyy-MM" : "yyyy-MM-dd";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(calendar.getTime());
    }

    private String buildDescription(StatsPeriodType type, String label) {
        switch (type) {
            case WEEKLY:
                return getString(R.string.stats_period_week_desc, label);
            case MONTHLY:
                return getString(R.string.stats_period_month_desc, label);
            case DAILY:
            default:
                return getString(R.string.stats_period_daily_desc, label);
        }
    }

    private String getAxisLabelForEntry(StatsEntry entry, StatsPeriodType type) {
        if (entry == null) {
            return "";
        }
        String label = entry.getPeriodLabel();
        if (label == null || label.isEmpty()) {
            return "";
        }
        try {
            switch (type) {
                case WEEKLY: {
                    Integer weekOfYear = parseWeekOfYear(label);
                    if (weekOfYear != null) {
                        return String.valueOf(weekOfYear);
                    }
                    break;
                }
                case MONTHLY: {
                    String[] parts = label.split("-");
                    if (parts.length >= 2) {
                        int month = Integer.parseInt(parts[1]);
                        return String.valueOf(month);
                    }
                    break;
                }
                case DAILY:
                default: {
                    String[] parts = label.split("-");
                    if (parts.length >= 3) {
                        int day = Integer.parseInt(parts[2]);
                        return String.valueOf(day);
                    }
                    break;
                }
            }
        } catch (NumberFormatException ignored) {
            // fallback to empty label
        }
        return "";
    }

    private Integer parseWeekOfYear(String label) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(sdf.parse(label));
            return calendar.get(Calendar.WEEK_OF_YEAR);
        } catch (ParseException e) {
            return null;
        }
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
