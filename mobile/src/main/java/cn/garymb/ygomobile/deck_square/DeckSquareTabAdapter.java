package cn.garymb.ygomobile.deck_square;

import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import com.google.android.material.tabs.TabLayout;

import cn.garymb.ygomobile.lite.R;

//管理tab
public class DeckSquareTabAdapter extends FragmentStatePagerAdapter {

    TabLayout tabLayout;
    /* 仅用于获取strings.xml中的字符串。It's used just for getting strings from strings.xml */
    Context context;

    public DeckSquareTabAdapter(FragmentManager fm, TabLayout _tabLayout, Context context) {
        super(fm);
        this.tabLayout = _tabLayout;
        this.context = context;
    }

    @Override
    public Fragment getItem(int position) {
        Fragment fragment = null;
        if (position == 0) {
            fragment = new DeckSquareFragment();
        } else if (position == 1) {
            fragment = new DeckSquareMyDeckFragment();
        } else if (position == 2) {
            fragment = new MCOnlineManageFragment();
        }
        return fragment;
    }

    @Override
    public int getCount() {
        return 3;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        String title = null;
        if (position == 0) {

            title = context.getString(R.string.ex_card_list_title);
        } else if (position == 1) {
            title = "我的卡组";
        }else if (position == 2) {
            title = "我的登录";
        }
        return title;
    }
}
