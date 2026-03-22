package com.upo.batteryassistant.ui.stats;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * ViewPager2 适配器，提供日/周/月三个统计页面。
 */
public class StatsPagerAdapter extends FragmentStateAdapter {

    public StatsPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        StatsPeriodType type = StatsPeriodType.fromOrdinal(position);
        return StatsPeriodFragment.newInstance(type);
    }

    @Override
    public int getItemCount() {
        return StatsPeriodType.values().length;
    }
}
