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

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
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

    private StatsPeriodType periodType = StatsPeriodType.DAILY;
    private ChargeHistoryManager historyManager;
    private Handler mainHandler;

    private SwipeRefreshHelper swipeHelper;
    private LineChart lineChart;
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
        lineChart = view.findViewById(R.id.line_chart);
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

        swipeHelper.showRefreshing(true);
        refreshData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void setupChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setNoDataText(getString(R.string.stats_chart_no_data));
        lineChart.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.stats_secondary_text));
        lineChart.setTouchEnabled(true);
        lineChart.setHighlightPerTapEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);

        int primaryText = ContextCompat.getColor(requireContext(), R.color.stats_primary_text);
        int secondaryText = ContextCompat.getColor(requireContext(), R.color.stats_secondary_text);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(secondaryText);
        xAxis.setAxisLineColor(secondaryText);

        lineChart.getAxisRight().setEnabled(false);
        lineChart.getAxisLeft().setTextColor(secondaryText);
        lineChart.getAxisLeft().setGridColor(secondaryText);
        lineChart.getLegend().setTextColor(primaryText);
    }

    private void refreshData() {
        if (isLoading) return;
        isLoading = true;
        loadStats();
    }

    private void loadStats() {
        new Thread(() -> {
            List<StatsEntry> entries = queryStats(0, PAGE_SIZE);
            mainHandler.post(() -> applyStatsResults(entries));
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

    private void applyStatsResults(List<StatsEntry> newEntries) {
        if (!isAdded()) {
            return;
        }
        statsEntries.clear();
        statsEntries.addAll(newEntries);
        isLoading = false;
        swipeHelper.showRefreshing(false);
        clearSelection();
        updateEmptyState();
        updateChart();
    }

    private void updateEmptyState() {
        boolean empty = statsEntries.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        lineChart.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        detailHint.setVisibility(empty ? View.GONE : View.VISIBLE);
        detailCard.setVisibility(View.GONE);
    }

    private void updateChart() {
        if (statsEntries.isEmpty()) {
            lineChart.clear();
            lineChart.invalidate();
            return;
        }
        chartOrderedEntries.clear();
        chartOrderedEntries.addAll(statsEntries);
        Collections.reverse(chartOrderedEntries); // oldest to newest for chart X axis

        List<Entry> chartEntries = new ArrayList<>();
        for (int i = 0; i < chartOrderedEntries.size(); i++) {
            chartEntries.add(new Entry(i, chartOrderedEntries.get(i).getTotalLevelChange()));
        }
        LineDataSet dataSet = new LineDataSet(chartEntries, getString(R.string.stats_chart_dataset_label));
        int accentColor = ContextCompat.getColor(requireContext(), R.color.stats_accent);
        dataSet.setColor(accentColor);
        dataSet.setCircleColor(accentColor);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setHighLightColor(accentColor);
        dataSet.setHighlightLineWidth(1.5f);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < chartOrderedEntries.size()) {
                    return chartOrderedEntries.get(index).getPeriodLabel();
                }
                return "";
            }
        });
        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = (int) h.getX();
                if (index >= 0 && index < chartOrderedEntries.size()) {
                    showDetail(chartOrderedEntries.get(index));
                }
            }

            @Override
            public void onNothingSelected() {
                clearSelection();
            }
        });
        lineChart.invalidate();
    }

    private void showDetail(@NonNull StatsEntry entry) {
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

    private void clearSelection() {
        detailCard.setVisibility(View.GONE);
        if (!statsEntries.isEmpty()) {
            detailHint.setVisibility(View.VISIBLE);
        }
        lineChart.highlightValue(null);
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
