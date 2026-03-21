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
import com.github.mikephil.charting.charts.ScatterChart;
import android.graphics.Color;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.data.DailyStats;
import com.upo.batteryassistant.database.BatteryDatabaseHelper;
import com.upo.batteryassistant.manager.ChargeHistoryManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.text.SimpleDateFormat;
import java.text.ParseException;
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
    private MaterialButtonToggleGroup periodToggle;
    private MaterialButton btnDaily;
    private MaterialButton btnWeekly;
    private MaterialButton btnMonthly;
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
    private TextView detailCapacity;
    private TextView detailMaxLevelChange;
    private ScatterChart capacityChart;
    private TextView capacityDetailText;
    private MaterialButtonToggleGroup capacityRangeToggle;
    private MaterialButton btnCap3m, btnCap12m, btnCapAll;
    private MaterialButton btnChargeHistory;
    private BatteryDatabaseHelper dbHelper;
    private SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private enum CapacityRange { M3, M12, ALL }

    private final List<StatsEntry> statsEntries = new ArrayList<>();
    private final List<StatsEntry> chartOrderedEntries = new ArrayList<>();
    private boolean isLoading = false;
    private boolean pendingReload = false;
    private boolean isViewReady = false;
    private int highlightedIndex = -1;
    private boolean suppressSelectionCallback = false;

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
        periodToggle = view.findViewById(R.id.stats_period_toggle);
        btnDaily = view.findViewById(R.id.btn_daily);
        btnWeekly = view.findViewById(R.id.btn_weekly);
        btnMonthly = view.findViewById(R.id.btn_monthly);
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
        detailCapacity = view.findViewById(R.id.detail_capacity);
        detailMaxLevelChange = view.findViewById(R.id.detail_max_level_change);
        capacityChart = view.findViewById(R.id.capacity_scatter_chart);
        capacityDetailText = view.findViewById(R.id.capacity_detail_text);
        capacityRangeToggle = view.findViewById(R.id.capacity_range_toggle);
        btnCap3m = view.findViewById(R.id.btn_capacity_3m);
        btnCap12m = view.findViewById(R.id.btn_capacity_12m);
        btnCapAll = view.findViewById(R.id.btn_capacity_all);
        btnChargeHistory = view.findViewById(R.id.btn_charge_history);
        dbHelper = new BatteryDatabaseHelper(requireContext());

        setupPeriodToggle();
        setupChart();
        setupCapacityChart();
        if (capacityRangeToggle != null && btnCap3m != null) {
            capacityRangeToggle.check(btnCap3m.getId());
            loadCapacityData(CapacityRange.M3);
        }
        if (capacityRangeToggle != null) {
            capacityRangeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (btnCap3m != null && checkedId == btnCap3m.getId()) {
                    loadCapacityData(CapacityRange.M3);
                } else if (btnCap12m != null && checkedId == btnCap12m.getId()) {
                    loadCapacityData(CapacityRange.M12);
                } else if (btnCapAll != null && checkedId == btnCapAll.getId()) {
                    loadCapacityData(CapacityRange.ALL);
                }
            });
        }
        if (btnChargeHistory != null) {
            btnChargeHistory.setOnClickListener(v -> {
                com.upo.batteryassistant.ui.ChargeHistoryFragment chargeHistoryFragment = new com.upo.batteryassistant.ui.ChargeHistoryFragment();
                requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, chargeHistoryFragment)
                    .addToBackStack(null)
                    .commit();
            });
        }
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
        periodToggle = null;
        btnDaily = null;
        btnWeekly = null;
        btnMonthly = null;
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
        detailCapacity = null;
        detailMaxLevelChange = null;
        swipeHelper = null;
    }

    private void setupPeriodToggle() {
        if (periodToggle == null) {
            return;
        }
        periodToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            StatsPeriodType type = null;
            if (checkedId == R.id.btn_daily) {
                type = StatsPeriodType.DAILY;
            } else if (checkedId == R.id.btn_weekly) {
                type = StatsPeriodType.WEEKLY;
            } else if (checkedId == R.id.btn_monthly) {
                type = StatsPeriodType.MONTHLY;
            }
            if (type != null && periodType != type) {
                setPeriodType(type);
            }
        });

        if (btnDaily != null) {
            btnDaily.setChecked(true);
        }
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
        xAxis.setDrawLabels(false);

        statsChart.getAxisRight().setEnabled(false);
        statsChart.getAxisLeft().setTextColor(secondaryText);
        statsChart.getAxisLeft().setGridColor(secondaryText);
        statsChart.getAxisLeft().setAxisMinimum(0f);
        statsChart.getLegend().setEnabled(false);
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
                List<DailyStats> weeklyStats = historyManager.getWeeklyStats(offset, limit);
                for (DailyStats stat : weeklyStats) {
                    String desc = getString(R.string.stats_period_week_desc, stat.getDate());
                    result.add(buildEntry(stat, desc));
                }
                break;
            case MONTHLY:
                List<DailyStats> monthlyStats = historyManager.getMonthlyStats(offset, limit);
                for (DailyStats stat : monthlyStats) {
                    String desc = getString(R.string.stats_period_month_desc, stat.getDate());
                    result.add(buildEntry(stat, desc));
                }
                break;
            case DAILY:
            default:
                List<DailyStats> dailyStats = historyManager.getDailyStats(offset, limit);
                for (DailyStats stat : dailyStats) {
                    String desc = getString(R.string.stats_period_daily_desc, stat.getDate());
                    result.add(buildEntry(stat, desc));
                }
                break;
        }
        return result;
    }

    private StatsEntry buildEntry(DailyStats data, String desc) {
        return new StatsEntry(data.getDate(), desc, data.getSessionCount(), data.getTotalLevelChange(),
            data.getTotalChargeCounterDiff(), data.getEstimatedCapacity(), data.getCycleCount(), data.getCapacity(), data.getMaxLevelChange());
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
        dataSet.setHighLightColor(Color.WHITE);
        dataSet.setHighLightAlpha(200);
        dataSet.setHighlightEnabled(true);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);
        barData.setHighlightEnabled(true);
        statsChart.setData(barData);
        statsChart.setHighlightPerDragEnabled(false);
        statsChart.setHighlightPerTapEnabled(true);
        statsChart.setFitBars(true);
        final int visibleCount = Math.min(chartOrderedEntries.size(), getMaxEntryCount(periodType));
        if (chartOrderedEntries.size() > visibleCount) {
            statsChart.moveViewToX(chartOrderedEntries.size() - visibleCount);
        } else {
            statsChart.moveViewToX(0f);
        }
        statsChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (suppressSelectionCallback) {
                    return;
                }
                if (e == null || h == null) {
                    return;
                }
                if (e.getY() == 0f) {
                    // 点击到0值柱：忽略点击，恢复之前的高亮或保持未选状态
                    restorePreviousHighlightOrClear();
                    return;
                }
                int index = (int) h.getX();
                if (index >= 0 && index < chartOrderedEntries.size()) {
                    highlightedIndex = index;
                    showDetail(chartOrderedEntries.get(index));
                    highlightChartEntry(index);
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

    private void setupCapacityChart() {
        if (capacityChart == null) return;
        capacityChart.getDescription().setEnabled(false);
        capacityChart.setNoDataText(getString(R.string.stats_chart_no_data));
        capacityChart.setTouchEnabled(true);
        capacityChart.setHighlightPerTapEnabled(true);
        capacityChart.setDragEnabled(true);
        capacityChart.setScaleEnabled(true);
        capacityChart.setPinchZoom(true);

        int secondaryText = ContextCompat.getColor(requireContext(), R.color.stats_secondary_text);

        XAxis xAxis2 = capacityChart.getXAxis();
        xAxis2.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis2.setGranularity(24f * 60f * 60f * 1000f);
        xAxis2.setTextColor(secondaryText);
        xAxis2.setAxisLineColor(secondaryText);
        xAxis2.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return dayFormat.format(new java.util.Date((long) value));
            }
        });

        YAxis left2 = capacityChart.getAxisLeft();
        left2.setTextColor(secondaryText);
        left2.setGridColor(secondaryText);
        capacityChart.getAxisRight().setEnabled(false);
        capacityChart.getLegend().setEnabled(false);

        capacityChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (e == null) return;
                String date = dayFormat.format(new java.util.Date((long) e.getX()));
                int cap = (int) e.getY();
                if (capacityDetailText != null) {
                    capacityDetailText.setText(date + "  估计容量: " + cap + " mAh");
                }
            }

            @Override
            public void onNothingSelected() {
                if (capacityDetailText != null) capacityDetailText.setText("");
            }
        });
    }

    private void loadCapacityData(CapacityRange range) {
        if (dbHelper == null || capacityChart == null) return;

        String start = null;
        String end = dayFormat.format(new java.util.Date());
        Calendar cal = Calendar.getInstance();
        if (range == CapacityRange.M3) {
            cal.add(Calendar.MONTH, -3);
            start = dayFormat.format(cal.getTime());
        } else if (range == CapacityRange.M12) {
            cal.add(Calendar.MONTH, -12);
            start = dayFormat.format(cal.getTime());
        }

        java.util.LinkedHashMap<String, Integer> map = dbHelper.getDailyEstimatedCapacities(start, range == CapacityRange.ALL ? null : end);
        List<Entry> entries = new ArrayList<>();
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (String d : map.keySet()) {
            try {
                long x = dayFormat.parse(d).getTime();
                int y = map.get(d);
                entries.add(new Entry((float) x, (float) y));
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            } catch (java.text.ParseException ignored) {}
        }

        YAxis left = capacityChart.getAxisLeft();
        if (entries.isEmpty()) {
            left.setAxisMinimum(0f);
            left.setAxisMaximum(1000f);
            capacityChart.setData(new ScatterData());
            capacityChart.invalidate();
            if (capacityDetailText != null) capacityDetailText.setText("");
            return;
        }

        float padding = Math.max(10f, (maxY - minY) * 0.1f);
        left.setAxisMinimum(Math.max(0f, minY - padding));
        left.setAxisMaximum(maxY + padding);

        ScatterDataSet dataSet = new ScatterDataSet(entries, "Estimated Capacity");
        int accentColor = ContextCompat.getColor(requireContext(), R.color.stats_accent);
        dataSet.setColor(accentColor);
        dataSet.setDrawValues(false);
        dataSet.setScatterShape(ScatterChart.ScatterShape.CIRCLE);
        dataSet.setScatterShapeSize(6f);
        dataSet.setHighlightEnabled(true);
        dataSet.setHighLightColor(Color.WHITE);
        dataSet.setHighlightLineWidth(1.2f);
        dataSet.setDrawHorizontalHighlightIndicator(true);
        dataSet.setDrawVerticalHighlightIndicator(true);

        ScatterData data = new ScatterData(dataSet);
        capacityChart.setData(data);
        capacityChart.invalidate();
    }

    private void showDetail(@NonNull StatsEntry entry) {
        if (detailHint == null || detailCard == null || detailTitle == null || detailDescription == null
            || detailSessionCount == null || detailLevelChange == null || detailChargeCounter == null
            || detailEstimatedCapacity == null || detailCycleCount == null
            || detailCapacity == null || detailMaxLevelChange == null) {
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
        detailCapacity.setText(getString(R.string.stats_estimated_capacity_value, entry.getCapacity()));
        detailMaxLevelChange.setText(getString(R.string.stats_level_change_value, entry.getMaxLevelChange()) + "%");
    }

    private void showLatestEntry() {
        if (statsEntries.isEmpty() || chartOrderedEntries.isEmpty()) {
            if (detailCard != null) {
                detailCard.setVisibility(View.GONE);
            }
            if (detailHint != null) {
                detailHint.setVisibility(View.GONE);
            }
            clearChartHighlight();
            highlightedIndex = -1;
            return;
        }
        int latestIndex = chartOrderedEntries.size() - 1;
        highlightedIndex = latestIndex;
        StatsEntry entryToShow = chartOrderedEntries.get(latestIndex);
        showDetail(entryToShow);
        highlightChartEntry(latestIndex);
    }

    private void clearSelection() {
        if (detailCard == null || detailHint == null || statsChart == null) {
            return;
        }
        if (statsEntries.isEmpty()) {
            detailCard.setVisibility(View.GONE);
            detailHint.setVisibility(View.GONE);
            clearChartHighlight();
            highlightedIndex = -1;
        } else {
            showLatestEntry();
        }
    }

    private void highlightChartEntry(int index) {
        if (statsChart == null || statsChart.getData() == null) {
            return;
        }
        if (index < 0 || index >= chartOrderedEntries.size()) {
            clearChartHighlight();
            return;
        }
        suppressSelectionCallback = true;
        statsChart.highlightValue(index, 0);
        suppressSelectionCallback = false;
        statsChart.invalidate();
    }

    private void clearChartHighlight() {
        if (statsChart == null) {
            return;
        }
        suppressSelectionCallback = true;
        statsChart.highlightValue(null);
        suppressSelectionCallback = false;
        statsChart.invalidate();
    }

    private void restorePreviousHighlightOrClear() {
        if (highlightedIndex >= 0 && highlightedIndex < chartOrderedEntries.size()) {
            highlightChartEntry(highlightedIndex);
            showDetail(chartOrderedEntries.get(highlightedIndex));
        } else {
            clearChartHighlight();
        }
    }

    private int findLastNonZeroIndex() {
        for (int i = chartOrderedEntries.size() - 1; i >= 0; i--) {
            if (chartOrderedEntries.get(i).getTotalLevelChange() != 0f) {
                return i;
            }
        }
        return -1;
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
                entry = new StatsEntry(label, description, 0, 0, 0, 0, 0, 0, 0);
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
        String pattern;
        if (type == StatsPeriodType.MONTHLY) {
            pattern = "yyyy-MM";
        } else if (type == StatsPeriodType.WEEKLY) {
            pattern = "YYww";
        } else {
            pattern = "yyyy-MM-dd";
        }
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

    // 已无 X 轴标签需求，删除旧的标签辅助方法

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
