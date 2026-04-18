package com.upo.batteryassistant.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.upo.batteryassistant.R;

/**
 * 统计主界面，承载“日/周/月”分页内容。
 */
public class StatsFragment extends Fragment {
    private StatsPeriodFragment periodFragment;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState == null) {
            periodFragment = StatsPeriodFragment.newInstance(StatsPeriodType.DAILY);
            getChildFragmentManager().beginTransaction()
                .replace(R.id.stats_content_container, periodFragment)
                .commitNow();
        } else {
            periodFragment = (StatsPeriodFragment) getChildFragmentManager()
                .findFragmentById(R.id.stats_content_container);
        }
    }

}
