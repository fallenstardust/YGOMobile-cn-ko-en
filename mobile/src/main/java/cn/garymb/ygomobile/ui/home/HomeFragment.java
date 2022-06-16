package cn.garymb.ygomobile.ui.home;

import android.content.Intent;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.base.BaseFragemnt;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.FileLogActivity;
import cn.garymb.ygomobile.ui.mycard.MyCardActivity;
import cn.garymb.ygomobile.ui.widget.Shimmer;
import cn.garymb.ygomobile.ui.widget.ShimmerTextView;


public class HomeFragment extends BaseFragemnt {
    ShimmerTextView tv;
    Shimmer shimmer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View layoutView;
        if (isHorizontal)
            layoutView = inflater.inflate(R.layout.main_horizontal_fragment, container, false);
        else
            layoutView = inflater.inflate(R.layout.main_fragment, container, false);

        initView(layoutView, savedInstanceState);

        return layoutView;
    }

    private void initView(View view, Bundle saveBundle) {
        //萌卡
        ImageView iv_mc = view.findViewById(R.id.btn_mycard);
        iv_mc.setOnClickListener((v) -> {
            if (Constants.SHOW_MYCARD) {
                startActivity(new Intent(getActivity(), MyCardActivity.class));
            }
        });
        //
        iv_mc.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(new Intent(getActivity(), FileLogActivity.class));
                return true;
            }
        });

        tv = (ShimmerTextView) view.findViewById(R.id.shimmer_tv);
        toggleAnimation(tv);
    }
    public void BacktoDuel() {
        tv.setOnClickListener((v) -> {
            //openGame();
        });
        if (YGOStarter.isGameRunning(getActivity())) {
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    public void toggleAnimation(View target) {
        if (shimmer != null && shimmer.isAnimating()) {
            shimmer.cancel();
        } else {
            shimmer = new Shimmer();
            shimmer.start(tv);
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        BacktoDuel();
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

}
