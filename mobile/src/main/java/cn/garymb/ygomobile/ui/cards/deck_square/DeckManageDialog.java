package cn.garymb.ygomobile.ui.cards.deck_square;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGODeckDialogUtil;

public class DeckManageDialog extends DialogFragment implements YGODeckDialogUtil.OnDeckDialogListener {


    public void onDismiss() {
        dismiss();
    }

    public void onShow() {
        //todo

    }

    private YGODeckDialogUtil.OnDeckMenuListener mOnDeckMenuListener;

    public DeckManageDialog(YGODeckDialogUtil.OnDeckMenuListener onDeckMenuListener) {
        super();
        mOnDeckMenuListener = onDeckMenuListener;

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_deck_manager, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewPager2 viewPager = view.findViewById(R.id.deck_view_pager);
        TabLayout tabLayout = view.findViewById(R.id.deck_manager_tab_layout);
        viewPager.setUserInputEnabled(false);
        // Setup adapter
        ViewPagerAdapter adapter = new ViewPagerAdapter(this, mOnDeckMenuListener, this);
        viewPager.setAdapter(adapter);

        // Connect TabLayout with ViewPager
        new TabLayoutMediator(tabLayout, viewPager,
                new TabLayoutMediator.TabConfigurationStrategy() {
                    @Override
                    public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                        //tab.setText("Tab " + (position + 1));
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
    }

    @Override
    public void onStart() {
        super.onStart();
        // Set dialog dimensions
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