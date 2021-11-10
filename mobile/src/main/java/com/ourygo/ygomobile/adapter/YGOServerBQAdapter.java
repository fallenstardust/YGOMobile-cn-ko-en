package com.ourygo.ygomobile.adapter;


import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.util.OYUtil;

import java.util.List;

import cn.garymb.ygomobile.lite.R;

public class YGOServerBQAdapter extends BaseQuickAdapter<YGOServer, BaseViewHolder> {

    public YGOServerBQAdapter(@Nullable List<YGOServer> data) {
        super(R.layout.ygo_server_item, data);
    }

    @Override
    protected void convert(BaseViewHolder helper, YGOServer item) {
        helper.setText(R.id.tv_name, item.getName());
        helper.setText(R.id.tv_ip, item.getServerAddr() + "：" + item.getPort());


        switch (item.getMode()) {
            case YGOServer.MODE_ONE:
                helper.setText(R.id.tv_mode, OYUtil.s(R.string.duel_mode_one));
                break;
            case YGOServer.MODE_MATCH:
                helper.setText(R.id.tv_mode, OYUtil.s(R.string.duel_mode_match));
                break;
            default:
                helper.setText(R.id.tv_mode, OYUtil.s(R.string.duel_mode_one));
        }
        switch (item.getOpponentType()) {
            case YGOServer.OPPONENT_TYPE_FRIEND:
                helper.setImageResource(R.id.iv_opponent_type,R.drawable.ic_friend);
                helper.setText(R.id.tv_create_and_share,OYUtil.s(R.string.create_room_and_share));
                helper.setGone(R.id.line_mode,false);
//                helper.setGone(R.id.tv_mode,true);
                break;
            case YGOServer.OPPONENT_TYPE_RANDOM:
                helper.setImageResource(R.id.iv_opponent_type,R.drawable.ic_random);
                helper.setText(R.id.tv_create_and_share,"开始匹配");
                helper.setGone(R.id.line_mode,false);
//                helper.setGone(R.id.tv_mode,true);
                break;
            case YGOServer.OPPONENT_TYPE_AI:
                helper.setImageResource(R.id.iv_opponent_type,R.drawable.ic_ai);
                helper.setText(R.id.tv_create_and_share,"开始");
                helper.setGone(R.id.line_mode,true);
//                helper.setGone(R.id.tv_mode,false);
                helper.setText(R.id.tv_mode, "AI");
                break;
            default:
                helper.setImageResource(R.id.iv_opponent_type,R.drawable.ic_friend);
                helper.setText(R.id.tv_create_and_share,OYUtil.s(R.string.create_room_and_share));
                helper.setGone(R.id.line_mode,false);
//                helper.setGone(R.id.tv_mode,true);
                break;
        }

    }
}
