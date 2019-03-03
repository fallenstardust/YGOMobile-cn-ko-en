package com.ourygo.ygomobile.adapter;


import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.util.OYUtil;

import java.util.List;

import androidx.annotation.Nullable;
import cn.garymb.ygomobile.lite.R;

public class YGOServerBQAdapter extends BaseQuickAdapter<YGOServer, BaseViewHolder> {

    public YGOServerBQAdapter(@Nullable List<YGOServer> data) {
        super(R.layout.ygo_server_item, data);
    }

    @Override
    protected void convert(BaseViewHolder helper, YGOServer item) {
        helper.setText(R.id.tv_name, item.getName());
        helper.setText(R.id.tv_ip, item.getServerAddr() + "：" + item.getPort());
        helper.addOnClickListener(R.id.tv_create_and_share);

        switch (item.getMode()) {
            case YGOServer.MODE_ONE:
                helper.setText(R.id.tv_mode, OYUtil.s(R.string.duel_mode_one));
                break;
            case YGOServer.MODE_MATCH:
                helper.setText(R.id.tv_mode, OYUtil.s(R.string.duel_mode_match));
                break;
        }

        if (item.getLflistCode() < 2)
            helper.setText(R.id.tv_lflist, "默认");
        else
            helper.setText(R.id.tv_lflist, item.getLflistName());

    }
}
