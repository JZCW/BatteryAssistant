package com.upo.batteryassistant.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.data.ChargeSession;
import com.upo.batteryassistant.ui.util.ThemeHelper;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 充放电阶段详情Activity
 * 展示所有字段信息，便于调试
 */
public class ChargeSessionDetailActivity extends AppCompatActivity {
    public static final String EXTRA_SESSION = "session";
    
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHelper.getThemeResId(this));
        ThemeHelper.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_charge_session_detail);
        setupActionBar();
        applyWindowInsets();
        
        ChargeSession session = (ChargeSession) getIntent().getSerializableExtra(EXTRA_SESSION);
        if (session == null) {
            finish();
            return;
        }
        
        setupViews(session);
    }

    private void setupActionBar() {
        MaterialToolbar toolbar = findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.detail_root);
        View appBarContainer = findViewById(R.id.detail_app_bar_container);
        View scrollView = findViewById(R.id.detail_scroll_view);

        final int appBarLeft = appBarContainer.getPaddingLeft();
        final int appBarTop = appBarContainer.getPaddingTop();
        final int appBarRight = appBarContainer.getPaddingRight();
        final int appBarBottom = appBarContainer.getPaddingBottom();
        final int scrollLeft = scrollView.getPaddingLeft();
        final int scrollTop = scrollView.getPaddingTop();
        final int scrollRight = scrollView.getPaddingRight();
        final int scrollBottom = scrollView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            appBarContainer.setPadding(
                appBarLeft + systemBars.left,
                appBarTop + systemBars.top,
                appBarRight + systemBars.right,
                appBarBottom
            );
            scrollView.setPadding(
                scrollLeft + systemBars.left,
                scrollTop,
                scrollRight + systemBars.right,
                scrollBottom + systemBars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
    
    private void setupViews(ChargeSession session) {
        // 基础信息
        TextView typeText = findViewById(R.id.detail_type_text);
        TextView typeValueText = findViewById(R.id.detail_type_value);
        TextView startTimeText = findViewById(R.id.detail_start_time_text);
        TextView endTimeText = findViewById(R.id.detail_end_time_text);
        TextView durationText = findViewById(R.id.detail_duration_text);
        TextView pauseTimeText = findViewById(R.id.detail_pause_time_text);
        TextView ongoingText = findViewById(R.id.detail_ongoing_text);
        
        typeValueText.setText(session.getSessionTypeText());
        startTimeText.setText(dateFormat.format(new Date(session.getStartTimestamp())));
        endTimeText.setText(dateFormat.format(new Date(session.getEndTimestamp())));
        
        long duration = session.getEndTimestamp() - session.getStartTimestamp();
        durationText.setText(formatDuration(duration));
        
        if (session.getPauseTimestamp() > 0) {
            pauseTimeText.setText(dateFormat.format(new Date(session.getPauseTimestamp())));
        } else {
            pauseTimeText.setText(R.string.common_none);
        }
        
        ongoingText.setText(session.isOngoing() ? R.string.status_ongoing : R.string.status_completed);
        
        // 电量信息
        TextView startLevelText = findViewById(R.id.detail_start_level_text);
        TextView endLevelText = findViewById(R.id.detail_end_level_text);
        TextView levelChangeText = findViewById(R.id.detail_level_change_text);
        TextView startChargeCounterText = findViewById(R.id.detail_start_charge_counter_text);
        TextView endChargeCounterText = findViewById(R.id.detail_end_charge_counter_text);
        TextView chargeCounterDiffText = findViewById(R.id.detail_charge_counter_diff_text);
        
        if (session.getStartLevel() >= 0) {
            startLevelText.setText(session.getStartLevel() + "%");
        } else {
            startLevelText.setText(R.string.common_unavailable);
        }

        if (session.getEndLevel() >= 0) {
            endLevelText.setText(session.getEndLevel() + "%");
        } else {
            endLevelText.setText(R.string.common_unavailable);
        }

        if (session.getStartLevel() >= 0 && session.getEndLevel() >= 0) {
            levelChangeText.setText((session.getEndLevel() - session.getStartLevel()) + "%");
        } else {
            levelChangeText.setText(R.string.common_unavailable);
        }
        
        if (session.getStartChargeCounter() >= 0) {
            startChargeCounterText.setText(session.getStartChargeCounter() + " mAh");
        } else {
            startChargeCounterText.setText(R.string.common_no_data);
        }
        
        if (session.getEndChargeCounter() >= 0) {
            endChargeCounterText.setText(session.getEndChargeCounter() + " mAh");
        } else {
            endChargeCounterText.setText(R.string.common_no_data);
        }
        
        int counterDiff = session.getChargeCounterDiff();
        chargeCounterDiffText.setText(counterDiff + " mAh");
        
        // 温度信息
        TextView maxTempText = findViewById(R.id.detail_max_temp_text);
        TextView minTempText = findViewById(R.id.detail_min_temp_text);

        if (session.getMaxTemperature() > 0) {
            maxTempText.setText(String.format(Locale.getDefault(), "%.1f°C", session.getMaxTemperatureCelsius()));
        } else {
            maxTempText.setText(R.string.common_unavailable);
        }

        if (session.getMinTemperature() > 0) {
            minTempText.setText(String.format(Locale.getDefault(), "%.1f°C", session.getMinTemperatureCelsius()));
        } else {
            minTempText.setText(R.string.common_unavailable);
        }
        
        // 分状态统计信息（屏幕 / Doze / 息屏非Doze）
        boolean isChargingSession = session.getSessionType() == 0;
        View stateStatsUnavailable = findViewById(R.id.detail_state_stats_unavailable);
        View stateStatsContent = findViewById(R.id.detail_state_stats_content);

        boolean stateStatsAvailable = session.getScreenOnDuration() >= 0;
        stateStatsUnavailable.setVisibility(stateStatsAvailable ? View.GONE : View.VISIBLE);
        stateStatsContent.setVisibility(stateStatsAvailable ? View.VISIBLE : View.GONE);

        if (stateStatsAvailable) {
            // 屏幕信息
            TextView screenOnDurationText = findViewById(R.id.detail_screen_on_duration_text);
            TextView screenOnChargeCounterDiffText = findViewById(R.id.detail_screen_on_charge_counter_diff_text);

            screenOnDurationText.setText(formatDuration(session.getScreenOnDuration()));

            if (session.getScreenOnChargeCounterDiff() >= 0) {
                screenOnChargeCounterDiffText.setText(getString(R.string.detail_positive_mah, session.getScreenOnChargeCounterDiff()));
            } else {
                screenOnChargeCounterDiffText.setText(getString(R.string.detail_signed_mah, session.getScreenOnChargeCounterDiff()));
            }

            // Doze信息（充电会话中隐藏）
            View dozeSectionView = findViewById(R.id.detail_doze_section);
            dozeSectionView.setVisibility(isChargingSession ? View.GONE : View.VISIBLE);

            if (!isChargingSession) {
                // Doze信息
                TextView dozeDurationText = findViewById(R.id.detail_doze_duration_text);
                TextView dozeChargeCounterDiffText = findViewById(R.id.detail_doze_charge_counter_diff_text);

                if (session.getDozeDuration() >= 0) {
                    dozeDurationText.setText(formatDuration(session.getDozeDuration()));
                } else {
                    dozeDurationText.setText(R.string.common_unavailable);
                }

                if (session.getDozeChargeCounterDiff() >= 0) {
                    dozeChargeCounterDiffText.setText(getString(R.string.detail_positive_mah, session.getDozeChargeCounterDiff()));
                } else if (session.getDozeChargeCounterDiff() < 0) {
                    dozeChargeCounterDiffText.setText(getString(R.string.detail_signed_mah, session.getDozeChargeCounterDiff()));
                } else {
                    dozeChargeCounterDiffText.setText(R.string.common_unavailable);
                }
            }

            // 息屏非Doze信息（推断）
            TextView nondozeDurationText = findViewById(R.id.detail_nondoze_duration_text);
            TextView nondozeChargeCounterDiffText = findViewById(R.id.detail_nondoze_charge_counter_diff_text);

            long totalDuration = session.getEndTimestamp() - session.getStartTimestamp();
            int totalChargeCounterDiff = session.getChargeCounterDiff();

            long nondozeDuration = -1;
            if (session.getDozeDuration() >= 0) {
                nondozeDuration = totalDuration - session.getScreenOnDuration() - session.getDozeDuration();
            }

            int nondozeChargeCounterDiff = Integer.MIN_VALUE;
            if (session.getScreenOnChargeCounterDiff() >= 0 && session.getDozeChargeCounterDiff() >= 0) {
                nondozeChargeCounterDiff = totalChargeCounterDiff - session.getScreenOnChargeCounterDiff() - session.getDozeChargeCounterDiff();
            }

            if (nondozeDuration >= 0) {
                nondozeDurationText.setText(formatDuration(nondozeDuration));
            } else {
                nondozeDurationText.setText(R.string.common_unavailable);
            }

            if (nondozeChargeCounterDiff != Integer.MIN_VALUE) {
                if (nondozeChargeCounterDiff >= 0) {
                    nondozeChargeCounterDiffText.setText(getString(R.string.detail_positive_mah, nondozeChargeCounterDiff));
                } else {
                    nondozeChargeCounterDiffText.setText(getString(R.string.detail_signed_mah, nondozeChargeCounterDiff));
                }
            } else {
                nondozeChargeCounterDiffText.setText(R.string.common_unavailable);
            }
        }
        View capacityCycleCard = findViewById(R.id.detail_capacity_cycle_card);
        capacityCycleCard.setVisibility(isChargingSession ? View.VISIBLE : View.GONE);

        if (isChargingSession) {
            TextView estimatedCapacityText = findViewById(R.id.detail_estimated_capacity_text);
            TextView cycleCountText = findViewById(R.id.detail_cycle_count_text);

            if (session.getEstimatedCapacity() > 0) {
                estimatedCapacityText.setText(session.getEstimatedCapacity() + " mAh");
            } else {
                estimatedCapacityText.setText(R.string.common_no_data);
            }

            if (session.getCycleCount() > 0) {
                cycleCountText.setText(String.valueOf(session.getCycleCount()));
            } else {
                cycleCountText.setText(R.string.common_no_data);
            }
        }
        
        // ID信息和更新计数
        TextView idText = findViewById(R.id.detail_id_text);
        TextView counterText = findViewById(R.id.detail_counter_text);
        idText.setText(String.valueOf(session.getId()));
        counterText.setText(String.valueOf(session.getCounter()));

        updateFieldAccessibility(typeText, typeValueText);
        updateFieldAccessibility(findViewById(R.id.detail_start_time_text_label), startTimeText);
        updateFieldAccessibility(findViewById(R.id.detail_end_time_text_label), endTimeText);
        updateFieldAccessibility(findViewById(R.id.detail_duration_text_label), durationText);
        updateFieldAccessibility(findViewById(R.id.detail_pause_time_text_label), pauseTimeText);
        updateFieldAccessibility(findViewById(R.id.detail_ongoing_text_label), ongoingText);
        updateFieldAccessibility(findViewById(R.id.detail_start_level_text_label), startLevelText);
        updateFieldAccessibility(findViewById(R.id.detail_end_level_text_label), endLevelText);
        updateFieldAccessibility(findViewById(R.id.detail_level_change_text_label), levelChangeText);
        updateFieldAccessibility(findViewById(R.id.detail_max_temp_text_label), maxTempText);
        updateFieldAccessibility(findViewById(R.id.detail_min_temp_text_label), minTempText);
        updateFieldAccessibility(findViewById(R.id.detail_id_text_label), idText);
        updateFieldAccessibility(findViewById(R.id.detail_counter_text_label), counterText);
    }
    
    

    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return getString(R.string.detail_duration_days, days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return getString(R.string.detail_duration_hours, hours, minutes % 60);
        } else if (minutes > 0) {
            return getString(R.string.detail_duration_minutes, minutes);
        } else {
            return getString(R.string.detail_duration_seconds, seconds);
        }
    }

    private void updateFieldAccessibility(TextView labelView, TextView valueView) {
        if (labelView == null || valueView == null) {
            return;
        }
        valueView.setContentDescription(getString(R.string.a11y_field_value, labelView.getText(), valueView.getText()));
    }
}
