package com.ourygo.ygomobile.adapter;


import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.ourygo.ygomobile.bean.LocalDuel;

import java.util.List;

import androidx.annotation.Nullable;
import cn.garymb.ygomobile.lite.R;

public class LocalBQAdapter extends BaseQuickAdapter<LocalDuel, BaseViewHolder> {



    public LocalBQAdapter( @Nullable List<LocalDuel> data) {
        super(R.layout.local_duel_item, data);
    }

    @Override
    protected void convert(BaseViewHolder helper, LocalDuel item) {

        helper.setText(R.id.tv_name,item.getName());
        helper.setText(R.id.tv_message,item.getMessage());

    }
}
