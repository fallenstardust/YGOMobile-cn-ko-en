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
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.MyCardWebFragment;
import cn.garymb.ygomobile.utils.YGOUtil;

public class MycardDuelArenaFragment extends BaseFragemnt {

    private TabLayout tabLayout;
    private ViewPager viewPager;
    private ImageButton btnClose;
    private ImageButton btnJumpOut;
    private HomeActivity homeActivity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        homeActivity = (HomeActivity) getActivity();
        View view = inflater.inflate(R.layout.fragment_mycard_duel_arena, container, false);
        initView(view);
        setupTabs();
        return view;
    }

    private void initView(View view) {
        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager = view.findViewById(R.id.view_pager);
        btnClose = view.findViewById(R.id.btn_close_fragment);
        btnJumpOut = view.findViewById(R.id.btn_jump_out);

        btnClose.setOnClickListener(v -> goBack());
        
        btnJumpOut.setOnClickListener(v -> openArenaWebPage());
    }

    private void openArenaWebPage() {
        if (homeActivity == null || getParentFragment() == null) {
            return;
        }

        // 判断 MyCardWebFragment 是否已经显示
        boolean isShowing = homeActivity.fragment_mycard_web != null &&
                homeActivity.fragment_mycard_web.isAdded() &&
                homeActivity.fragment_mycard_web.isVisible();

        if (isShowing) {
            // 如果正在显示，则移除它
            getParentFragment().getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .remove(homeActivity.fragment_mycard_web)
                    .commit();
            homeActivity.fragment_mycard_web = null;
            
            // 显示 MycardDuelArenaFragment
            getParentFragment().getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .show(MycardDuelArenaFragment.this)
                    .commit();
            
            // 恢复萌卡论坛按钮状态
            if (getParentFragment() instanceof cn.garymb.ygomobile.ui.mycard.MycardFragment) {
                ((cn.garymb.ygomobile.ui.mycard.MycardFragment) getParentFragment())
                        .updateToolBarButtonStatePublic(null);
            }
        } else {
            // 如果未显示，则打开竞技场网页
            homeActivity.fragment_mycard_web = MyCardWebFragment.newInstance(
                    MyCard.getArenaUrl(),
                    YGOUtil.s(R.string.arena)
            );

            // 隐藏 MycardDuelArenaFragment
            getParentFragment().getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .hide(MycardDuelArenaFragment.this)
                    .commit();

            // 在独立的 fragment_web_content 容器中添加 Web Fragment
            getParentFragment().getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .add(R.id.fragment_web_content, homeActivity.fragment_mycard_web)
                    .commit();
            
            // 将萌卡论坛按钮设置为关闭状态（传入 null 表示没有按钮被激活，但这里我们需要传入 btn_mycard_bbs）
            // 由于无法直接访问 btn_mycard_bbs，我们通过反射或其他方式获取
            View parentView = getParentFragment().getView();
            if (parentView != null) {
                View btnBbs = parentView.findViewById(R.id.btn_mycard_bbs);
                if (getParentFragment() instanceof cn.garymb.ygomobile.ui.mycard.MycardFragment) {
                    ((cn.garymb.ygomobile.ui.mycard.MycardFragment) getParentFragment())
                            .updateToolBarButtonStatePublic(btnBbs);
                }
            }
        }
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
