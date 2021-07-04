package com.ourygo.ygomobile.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.ourygo.ygomobile.adapter.ReplayAdapter;
import com.ourygo.ygomobile.base.listener.OnMyCardNewsQueryListener;
import com.ourygo.ygomobile.bean.FragmentData;
import com.ourygo.ygomobile.bean.MyCardNews;
import com.ourygo.ygomobile.ui.activity.NewServerActivity;
import com.ourygo.ygomobile.util.MyCardUtil;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.YGOUtil;
import com.ourygo.ygomobile.view.OYTabLayout;
import com.stx.xhb.xbanner.XBanner;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;

public class MainFragment extends BaseFragemnt {

    private static final int TYPE_BANNER_QUERY_OK = 0;
    private static final int TYPE_BANNER_QUERY_EXCEPTION = 1;

    private XBanner xb_banner;
    private List<MyCardNews> myCardNewsList;
    private TextView tv_add_setting;
    private OYTabLayout tl_game_option,tl_replay;
    private ViewPager vp_game;

    private List<FragmentData> fragmentDataList;
    private RecyclerView rv_replay;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.main_fragment, null);

        initView(v);
        return v;
    }

    private void initView(View v) {
        xb_banner = v.findViewById(R.id.xb_banner);
        tv_add_setting=v.findViewById(R.id.tv_add_setting);
        tl_game_option=v.findViewById(R.id.tl_game_option);
        vp_game=v.findViewById(R.id.vp_game);
        tl_replay=v.findViewById(R.id.tl_replay);
        rv_replay=v.findViewById(R.id.rv_replay);


        fragmentDataList=new ArrayList<>();

        tv_add_setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), NewServerActivity.class));
            }
        });

        LinearLayoutManager linearLayoutManager=new LinearLayoutManager(getActivity());
        linearLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        rv_replay.setLayoutManager(linearLayoutManager);
        rv_replay.setAdapter(new ReplayAdapter(YGOUtil.getReplayList()));

        fragmentDataList.add(FragmentData.toFragmentData(OYUtil.s(R.string.start_game),new YGOServerFragemnt()));
        fragmentDataList.add(FragmentData.toFragmentData(OYUtil.s(R.string.local_duel),new LocalDuelFragment()));
        tl_replay.addTab(tl_replay.newTab().setText("对战录像"));
        tl_replay.initTabTextStyle(null);
//        tb_game_option.initTabTextStyle(null);



        vp_game.setAdapter(new FmPagerAdapter(getActivity().getSupportFragmentManager()));
        //TabLayout加载viewpager
        tl_game_option.setupWithViewPager(vp_game);
        //缓存两个页面
        vp_game.setOffscreenPageLimit(3);


        xb_banner.post(new Runnable() {
            @Override
            public void run() {
//                Log.e("MainFragment","宽"+xb_banner.getWidth());
//                Log.e("MainFragment","算数"+OYUtil.px2dp(xb_banner.getHeight())*2);
                xb_banner.setClipChildrenLeftRightMargin((OYUtil.px2dp(xb_banner.getWidth())-OYUtil.px2dp(xb_banner.getHeight()-OYUtil.px2dp(10))*2)/2);

//                xb_banner.setClipChildrenLeftRightMargin(75);
//                ViewGroup.LayoutParams layoutParams=xb_banner.getLayoutParams();
//                layoutParams.width=xb_banner.getWidth();
//                layoutParams.height=layoutParams.width*9/16;
//                xb_banner.setLayoutParams(layoutParams);

//                xb_banner.setClipChildrenLeftRightMargin(50);
//                xb_banner.setma

            }
        });
//        xb_banner.setOnItemClickListener(new XBanner.OnItemClickListener() {
//            @Override
//            public void onItemClick(XBanner banner, Object model, View view, int position) {
//                startActivity(IntentUtil.getUrlIntent(myCardNewsList.get(position).getNews_url()));
//            }
//        });
        xb_banner.loadImage(new XBanner.XBannerAdapter() {
            @Override
            public void loadBanner(XBanner banner, Object model, View view, int position) {
                TextView tv_time,tv_title,tv_type;
                ImageView iv_image;

                tv_time=view.findViewById(R.id.tv_time);
                tv_title=view.findViewById(R.id.tv_title);
                tv_type=view.findViewById(R.id.tv_type);
                iv_image=view.findViewById(R.id.iv_image);

                MyCardNews myCardNews=myCardNewsList.get(position);
                ImageUtil.setImage(getContext(),myCardNews.getImage_url(),iv_image);
                tv_time.setText(MyCardUtil.getMyCardNewsData(myCardNews.getCreate_time()));
                tv_title.setText(myCardNews.getTitle());
                tv_type.setVisibility(View.GONE);
//
//                Log.e("MainFragment","Height"+view.getHeight());
//                Log.e("MainFragment","Width"+view.getWidth());

            }
        });
        MyCardUtil.findMyCardNews(new OnMyCardNewsQueryListener() {
            @Override
            public void onMyCardNewsQuery(List<MyCardNews> myCardNewsList, String exception) {
                Message message = new Message();
                if (TextUtils.isEmpty(exception)) {
                    Log.e("MainFragemnt", "查询成功");
                    while (myCardNewsList.size()>5){
                        myCardNewsList.remove(myCardNewsList.size()-1);
                    }
                    MainFragment.this.myCardNewsList = myCardNewsList;
                    message.what = TYPE_BANNER_QUERY_OK;
                } else {
                    Log.e("MainFragemnt", "查询失败" + exception);
                    message.obj = exception;
                    message.what = TYPE_BANNER_QUERY_EXCEPTION;
                }
                handler.sendMessage(message);
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        xb_banner.startAutoPlay();
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
    public void onStop() {
        super.onStop();
        xb_banner.stopAutoPlay();
    }


    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_BANNER_QUERY_OK:
                    xb_banner.setBannerData(R.layout.banner_main_item, myCardNewsList);
                    break;
                case TYPE_BANNER_QUERY_EXCEPTION:
                    OYUtil.snackExceptionToast(getActivity(),xb_banner,getString(R.string.query_exception),msg.obj.toString());
                    break;
            }

        }
    };

    class FmPagerAdapter extends FragmentPagerAdapter{

        public FmPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return fragmentDataList.get(position).getFragment();
        }

        @Override
        public int getCount() {
            return fragmentDataList.size();
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return fragmentDataList.get(position).getTitle();
        }
    }

}
