package cn.garymb.ygomobile.ui.mycard.arena;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;

public class MycardDuelArenaFragment extends BaseFragemnt {

    private TabLayout tabLayout;
    private ViewPager viewPager;
    private ImageButton btnClose;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_mycard_duel_arena, container, false);
        initView(view);
        setupTabs();
        return view;
    }

    private void initView(View view) {
        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager = view.findViewById(R.id.view_pager);
        btnClose = view.findViewById(R.id.btn_close_fragment);

        btnClose.setOnClickListener(v -> goBack());
    }

    private void setupTabs() {
        TabPageAdapter adapter = new TabPageAdapter(getChildFragmentManager());
        viewPager.setAdapter(adapter);
        tabLayout.setupWithViewPager(viewPager);

        // 设置预加载所有Tab，避免切换时重新加载
        viewPager.setOffscreenPageLimit(2);
    }

    @Override
    public void onFirstUserVisible() {
    }

    @Override
    public void onUserVisible() {
    }

    @Override
    public void onFirstUserInvisible() {
    }

    @Override
    public void onUserInvisible() {
    }

    @Override
    public void onBackHome() {
    }

    @Override
    public boolean onBackPressed() {
        goBack();
        return true;
    }

    private void goBack() {
        if (getActivity() != null && getParentFragment() != null) {
            getParentFragment().getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .hide(this)
                    .commit();

            View mainContentView = getActivity().findViewById(R.id.ll_main_ui);
            if (mainContentView != null) {
                mainContentView.setVisibility(View.VISIBLE);
            }
        }
    }

    class TabPageAdapter extends FragmentPagerAdapter {

        private final String[] tabTitles = {
                YGOUtil.s(R.string.deck_ranking),
                YGOUtil.s(R.string.deck_winrate),
                YGOUtil.s(R.string.duel_ranking),
                YGOUtil.s(R.string.card_ranking)
        };

        public TabPageAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new DeckWinRateFragment();
                case 1:
                    return new DeckMatchupFragment();
                case 2:
                    return new DuelRankFragment();
                case 3:
                    return new CardRankFragment();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return tabTitles.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return tabTitles[position];
        }
    }
}
