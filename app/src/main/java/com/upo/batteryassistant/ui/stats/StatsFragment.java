package com.upo.batteryassistant.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.upo.batteryassistant.R;

/**
 * 统计主界面，承载“日/周/月”分页内容。
 */
public class StatsFragment extends Fragment {
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

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
        viewPager = view.findViewById(R.id.stats_view_pager);

        StatsPagerAdapter adapter = new StatsPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (getContext() == null) {
                return;
            }
            switch (position) {
                case 0:
                    tab.setText(R.string.stats_tab_daily);
                    break;
                case 1:
                    tab.setText(R.string.stats_tab_weekly);
                    break;
                case 2:
                default:
                    tab.setText(R.string.stats_tab_monthly);
                    break;
            }
        }).attach();
    }
}
