package com.upo.batteryassistant.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.upo.batteryassistant.R;

/**
 * 统计主界面，承载“日/周/月”分页内容。
 */
public class StatsFragment extends Fragment {
    private TabLayout tabLayout;
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
        tabLayout = view.findViewById(R.id.stats_tab_layout);

        if (savedInstanceState == null) {
            periodFragment = StatsPeriodFragment.newInstance(StatsPeriodType.DAILY);
            getChildFragmentManager().beginTransaction()
                .replace(R.id.stats_content_container, periodFragment)
                .commitNow();
        } else {
            periodFragment = (StatsPeriodFragment) getChildFragmentManager()
                .findFragmentById(R.id.stats_content_container);
        }

        setupTabs();
    }

    private void setupTabs() {
        tabLayout.removeAllTabs();
        addTab(StatsPeriodType.DAILY, R.string.stats_tab_daily);
        addTab(StatsPeriodType.WEEKLY, R.string.stats_tab_weekly);
        addTab(StatsPeriodType.MONTHLY, R.string.stats_tab_monthly);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                handleTabSelection(tab);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // no-op
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                handleTabSelection(tab);
            }
        });

        TabLayout.Tab defaultTab = tabLayout.getTabAt(0);
        if (defaultTab != null) {
            defaultTab.select();
        }
    }

    private void addTab(@NonNull StatsPeriodType type, int titleRes) {
        TabLayout.Tab tab = tabLayout.newTab().setText(titleRes);
        tab.setTag(type);
        tabLayout.addTab(tab, tabLayout.getTabCount() == 0);
    }

    private void handleTabSelection(@Nullable TabLayout.Tab tab) {
        if (tab == null || periodFragment == null) {
            return;
        }
        Object tag = tab.getTag();
        if (tag instanceof StatsPeriodType) {
            StatsPeriodType type = (StatsPeriodType) tag;
            if (periodFragment.getPeriodType() != type) {
                periodFragment.setPeriodType(type);
            }
        }
    }
}
