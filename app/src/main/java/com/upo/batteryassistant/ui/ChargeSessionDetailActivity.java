package com.upo.batteryassistant.ui;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.data.ChargeSession;
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_charge_session_detail);
        
        ChargeSession session = (ChargeSession) getIntent().getSerializableExtra(EXTRA_SESSION);
        if (session == null) {
            finish();
            return;
        }
        
        setupViews(session);
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
            pauseTimeText.setText("无");
        }
        
        ongoingText.setText(session.isOngoing() ? "进行中" : "已结束");
        
        // 电量信息
        TextView startLevelText = findViewById(R.id.detail_start_level_text);
        TextView endLevelText = findViewById(R.id.detail_end_level_text);
        TextView levelChangeText = findViewById(R.id.detail_level_change_text);
        TextView startChargeCounterText = findViewById(R.id.detail_start_charge_counter_text);
        TextView endChargeCounterText = findViewById(R.id.detail_end_charge_counter_text);
        TextView chargeCounterDiffText = findViewById(R.id.detail_charge_counter_diff_text);
        
        startLevelText.setText(session.getStartLevel() + "%");
        endLevelText.setText(session.getEndLevel() + "%");
        levelChangeText.setText((session.getEndLevel() - session.getStartLevel()) + "%");
        
        if (session.getStartChargeCounter() >= 0) {
            startChargeCounterText.setText(session.getStartChargeCounter() + " mAh");
        } else {
            startChargeCounterText.setText("无数据");
        }
        
        if (session.getEndChargeCounter() >= 0) {
            endChargeCounterText.setText(session.getEndChargeCounter() + " mAh");
        } else {
            endChargeCounterText.setText("无数据");
        }
        
        int counterDiff = session.getChargeCounterDiff();
        chargeCounterDiffText.setText(counterDiff + " mAh");
        
        // 温度信息
        TextView maxTempText = findViewById(R.id.detail_max_temp_text);
        TextView minTempText = findViewById(R.id.detail_min_temp_text);
        
        maxTempText.setText(String.format("%.1f°C", session.getMaxTemperatureCelsius()));
        minTempText.setText(String.format("%.1f°C", session.getMinTemperatureCelsius()));
        
        // 屏幕信息
        TextView screenOnDurationText = findViewById(R.id.detail_screen_on_duration_text);
        TextView screenOnLevelChangeText = findViewById(R.id.detail_screen_on_level_change_text);
        TextView screenOnChargeCounterDiffText = findViewById(R.id.detail_screen_on_charge_counter_diff_text);
        
        if (session.getScreenOnDuration() > 0) {
            screenOnDurationText.setText(formatDuration(session.getScreenOnDuration()));
        } else {
            screenOnDurationText.setText("无数据");
        }
        
        if (session.getScreenOnLevelChange() != 0) {
            screenOnLevelChangeText.setText(session.getScreenOnLevelChange() + "%");
        } else {
            screenOnLevelChangeText.setText("无数据");
        }
        
        if (session.getScreenOnChargeCounterDiff() != 0) {
            screenOnChargeCounterDiffText.setText(session.getScreenOnChargeCounterDiff() + " mAh");
        } else {
            screenOnChargeCounterDiffText.setText("无数据");
        }
        
        // Doze信息
        TextView dozeDurationText = findViewById(R.id.detail_doze_duration_text);
        TextView dozeChargeCounterDiffText = findViewById(R.id.detail_doze_charge_counter_diff_text);
        
        if (session.getDozeDuration() > 0) {
            dozeDurationText.setText(formatDuration(session.getDozeDuration()));
        } else {
            dozeDurationText.setText("无数据");
        }
        
        if (session.getDozeChargeCounterDiff() != 0) {
            dozeChargeCounterDiffText.setText(session.getDozeChargeCounterDiff() + " mAh");
        } else {
            dozeChargeCounterDiffText.setText("无数据");
        }
        
        // 容量和周期信息
        TextView estimatedCapacityText = findViewById(R.id.detail_estimated_capacity_text);
        TextView cycleCountText = findViewById(R.id.detail_cycle_count_text);
        
        if (session.getEstimatedCapacity() > 0) {
            estimatedCapacityText.setText(session.getEstimatedCapacity() + " mAh");
        } else {
            estimatedCapacityText.setText("无数据");
        }
        
        if (session.getCycleCount() > 0) {
            cycleCountText.setText(String.valueOf(session.getCycleCount()));
        } else {
            cycleCountText.setText("无数据");
        }
        
        // ID信息
        TextView idText = findViewById(R.id.detail_id_text);
        idText.setText(String.valueOf(session.getId()));
    }
    
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%d天%d小时%d分钟", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟", minutes);
        } else {
            return String.format("%d秒", seconds);
        }
    }
}
