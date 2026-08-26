package cn.garymb.ygomobile.ui.cards.deck_square;


import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;

public class DeckManageDialog extends DialogFragment implements YGODeckDialogUtil.OnDeckDialogListener {

    //所有入口统一使用同一个tag，保证任何入口都能复用同一个被隐藏的对话框实例
    public static final String DIALOG_TAG = "pagerDialog";

    private int initialTabPosition = 0;
    private String searchKeyword = null;

    private ViewPager2 viewPager;

    /**
     * 打开卡组管理对话框：若该对话框之前已打开过且只是被隐藏，则直接恢复显示；否则创建新的对话框
     */
    public static void showDeckManageDialog(FragmentManager manager, YGODeckDialogUtil.OnDeckMenuListener listener, int initialTabPosition, String searchKeyword) {
        DeckManageDialog dialog = (DeckManageDialog) manager.findFragmentByTag(DIALOG_TAG);
        if (dialog != null && dialog.isAdded() && dialog.getDialog() != null) {
            dialog.restore(initialTabPosition, searchKeyword);
            return;
        }
        new DeckManageDialog(listener, initialTabPosition, searchKeyword).show(manager, DIALOG_TAG);
    }

    /**
     * 恢复显示被隐藏的对话框，不重新构筑布局；
     * 若调用方指定了新的页签/搜索关键词（如从战绩页带卡组名打开），则在现有实例上切换到该状态
     */
    private void restore(int tabPosition, String keyword) {
        Dialog dialog = getDialog();
        if (dialog == null) {
            return;
        }
        dialog.show();
        if (keyword != null && !keyword.isEmpty()) {
            searchKeyword = keyword;
            for (Fragment fragment : getChildFragmentManager().getFragments()) {
                if (fragment instanceof DeckSquareFragment) {
                    ((DeckSquareFragment) fragment).searchDeck(keyword);
                    break;
                }
            }
        }
        if (viewPager != null && tabPosition > 0 && tabPosition < 3) {
            viewPager.setCurrentItem(tabPosition, false);
        }
    }

    public void onDismiss() {
        // 只隐藏对话框而不销毁，保留内部状态，下次打开时直接恢复显示
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.hide();
        }
    }

    public void onShow() {
    }

    private YGODeckDialogUtil.OnDeckMenuListener mOnDeckMenuListener;

    public DeckManageDialog(YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener) {
        this(onDeckMenuListener, 0, null);
    }

    public DeckManageDialog(YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener, int initialTabPosition, String searchKeyword) {
        super();
        mOnDeckMenuListener = onDeckMenuListener;
        this.initialTabPosition = initialTabPosition;
        this.searchKeyword = searchKeyword;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new Dialog(requireContext(), getTheme()) {
            @Override
            public void onBackPressed() {
                // 返回键只隐藏对话框窗口而不销毁，保留当前页签、搜索关键词等状态
                hide();
            }
        };
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_deck_manager, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewPager = view.findViewById(R.id.deck_view_pager);
        TabLayout tabLayout = view.findViewById(R.id.deck_manager_tab_layout);
        viewPager.setUserInputEnabled(true);
        
        ViewPagerAdapter adapter = new ViewPagerAdapter(this, mOnDeckMenuListener, this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                new TabLayoutMediator.TabConfigurationStrategy() {
                    @Override
                    public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                        switch (position) {
                            case 0:
                                tab.setIcon(R.drawable.ic_deck_box);
                                tab.setText(R.string.local_deck);
                                break;
                            case 1:
                                tab.setIcon(R.drawable.ic_deck_square);
                                tab.setText(R.string.deck_square);
                                break;
                            case 2:
                                tab.setIcon(R.drawable.my_deck_square);
                                tab.setText(R.string.my_deck_online);
                                break;
                        }
                    }
                }).attach();

        if (initialTabPosition > 0 && initialTabPosition < 3) {
            viewPager.setCurrentItem(initialTabPosition, false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog().getWindow();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }

    private static class ViewPagerAdapter extends FragmentStateAdapter {

        private YGODeckDialogUtil.OnDeckMenuListener mOnDeckMenuListener;
        private YGODeckDialogUtil.OnDeckDialogListener onDeckDialogListener;

        public ViewPagerAdapter(@NonNull Fragment fragment, YGODeckDialogUtil.OnDeckMenuListener listener, YGODeckDialogUtil.OnDeckDialogListener dialogListener) {
            super(fragment);
            mOnDeckMenuListener = listener;
            onDeckDialogListener = dialogListener;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new DeckSelectFragment(mOnDeckMenuListener, onDeckDialogListener);
                case 1:
                    return new DeckSquareFragment(mOnDeckMenuListener, onDeckDialogListener);
                case 2:
                    return new DeckSquareMyDeckFragment(mOnDeckMenuListener, onDeckDialogListener);
                default:
                    throw new IllegalArgumentException();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }

    }
}