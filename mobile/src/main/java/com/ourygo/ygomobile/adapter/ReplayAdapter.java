package com.ourygo.ygomobile.adapter;


import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.ourygo.ygomobile.bean.Replay;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import androidx.annotation.Nullable;
import cn.garymb.ygomobile.lite.R;

public class ReplayAdapter extends BaseQuickAdapter<Replay, BaseViewHolder> {


    public ReplayAdapter(@Nullable List<Replay> data) {
        super(R.layout.replay_item, data);
    }

    @Override
    protected void convert(BaseViewHolder helper, Replay item) {
//        helper.setText(R.id.tv_name, item.getName());
//        helper.setText(R.id.tv_time,item.getTime());

        String name = item.getName();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
        Date date;
        try {
            date = sdf.parse(name);
            helper.setText(R.id.tv_name, "未命名录像");
            helper.setText(R.id.tv_time,new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date));
        } catch (ParseException e) {
            helper.setText(R.id.tv_name, name);
            helper.setText(R.id.tv_time, item.getTime());
        }

    }
}
