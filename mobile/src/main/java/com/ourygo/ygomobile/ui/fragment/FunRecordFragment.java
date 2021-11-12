package com.ourygo.ygomobile.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.king.view.circleprogressview.CircleProgressView;
import com.ourygo.ygomobile.base.listener.BaseDuelInfoFragment;
import com.ourygo.ygomobile.bean.McDuelInfo;
import com.ourygo.ygomobile.util.StatUtil;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;

/**
 * Create By feihua  On 2021/10/19
 */
public class FunRecordFragment extends BaseFragemnt implements BaseDuelInfoFragment {

    private CircleProgressView cpv_rank;
    private TextView tv_rank, tv_win, tv_lose;
    private McDuelInfo mcDuelInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.mycard_fun_rank_fragment, container, false);
        initView(view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        StatUtil.onResume(getClass().getName());
    }

    @Override
    public void onPause() {
        super.onPause();
        StatUtil.onPause(getClass().getName());
    }

    private void initView(View view) {
        cpv_rank = view.findViewById(R.id.cpv_rank);
        tv_rank = view.findViewById(R.id.tv_rank);
        tv_win = view.findViewById(R.id.tv_win);
        tv_lose = view.findViewById(R.id.tv_lose);
    }


    private void initData() {
        cpv_rank.setMax(10000);
        onBaseDuelInfo(mcDuelInfo, null);
        //设置进度改变监听
        cpv_rank.setOnChangeListener((progress, max) -> {

        });


//        cpv_rank.gette
    }

    @Override
    public void onFirstUserVisible() {
        initData();
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
    public void onBaseDuelInfo(McDuelInfo mcDuelInfo, String exception) {
        if (!TextUtils.isEmpty(exception))
            return;
        this.mcDuelInfo = mcDuelInfo;
        if (cpv_rank == null)
            return;
        if (mcDuelInfo == null) {
            cpv_rank.setProgress(0);
            cpv_rank.setLabelText("");
            tv_lose.setText("");
            tv_win.setText("");
            tv_rank.setText("");
            return;
        }

        int pro = (int) (mcDuelInfo.getFunWinRratio() * 100);
        //显示进度动画，进度，动画时长
        cpv_rank.showAnimation(pro, 600);
        //设置当前进度
        cpv_rank.setProgress(pro);
        cpv_rank.setLabelText(mcDuelInfo.getFunWinRratio() + "%");
        tv_lose.setText(mcDuelInfo.getFunLose() + "");
        tv_win.setText(mcDuelInfo.getFunWin() + "");
        tv_rank.setText(mcDuelInfo.getFunRank() + "");
    }
}
