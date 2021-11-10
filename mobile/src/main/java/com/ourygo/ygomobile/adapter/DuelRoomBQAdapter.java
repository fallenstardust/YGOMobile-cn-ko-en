package com.ourygo.ygomobile.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.ourygo.ygomobile.bean.DuelRoom;
import com.ourygo.ygomobile.bean.SettingItem;
import com.ourygo.ygomobile.util.ImageUtil;
import com.ourygo.ygomobile.util.OYUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;

/**
 * Create By feihua  On 2021/11/4
 */
public class DuelRoomBQAdapter extends BaseQuickAdapter<DuelRoom, BaseViewHolder> {
    private Context context;
    private static final int ITEM_TYPE_SAME = 0;
    private static final int ITEM_TYPE_SWITCH = 1;
    private static final int ITEM_TYPE_DIFFERENT = 2;
    private static final int ITEM_TYPE_END = 3;
    private static final int ITEM_ONE = 4;

    public DuelRoomBQAdapter(Context context,List<DuelRoom> data) {
        super(R.layout.duel_room_item,data);
        this.context=context;
    }

    public int getGroupType(int position) {
        List<DuelRoom> data = getData();
        int currentTypeId = data.get(position).getArenaType();
        Integer lastTypeId = null, nextTypeId = null;
        if (position != 0)
            lastTypeId = data.get(position - 1).getArenaType();
        if (position != data.size() - 1)
            nextTypeId = data.get(position + 1).getArenaType();

        if (lastTypeId != null && nextTypeId != null) {
            if (currentTypeId == lastTypeId && currentTypeId == nextTypeId)
                return ITEM_TYPE_SAME;
            else if (currentTypeId == lastTypeId)
                return ITEM_TYPE_END;
            else if (currentTypeId == nextTypeId)
                return ITEM_TYPE_DIFFERENT;
            else
                return ITEM_ONE;

        } else if (lastTypeId != null) {
            if (currentTypeId == lastTypeId)
                return ITEM_TYPE_END;
            else
                return ITEM_ONE;
        } else if (nextTypeId != null) {
            if (currentTypeId == nextTypeId)
                return ITEM_TYPE_DIFFERENT;
            else
                return ITEM_ONE;
        } else {
            return ITEM_ONE;
        }

    }

    @Override
    protected void convert(@NonNull BaseViewHolder baseViewHolder, DuelRoom duelRoom) {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) baseViewHolder.getView(R.id.ll_item).getLayoutParams();
        switch (getGroupType(baseViewHolder.getAdapterPosition() - getHeaderLayoutCount())) {
            case ITEM_TYPE_SAME:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.click_background);
                baseViewHolder.setGone(R.id.tv_title, true);
                baseViewHolder.setGone(R.id.line, true);
                lp.setMargins(0, 0, 0, 0);
                break;
            case ITEM_TYPE_DIFFERENT:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.click_top_background_radius);
                String typeName;
                switch (duelRoom.getArenaType()){
                    case DuelRoom.TYPE_ARENA_MATCH:
                        typeName="竞技匹配";
                        break;
                    case DuelRoom.TYPE_ARENA_FUN:
                        typeName="娱乐匹配";
                        break;
                    case DuelRoom.TYPE_ARENA_AI:
                        typeName="人机模式";
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_SINGLE:
                        typeName="单局模式";
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_MATCH:
                        typeName="比赛模式";
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_TAG:
                        typeName="双打模式";
                        break;
                    default:
                        typeName="未知房间";
                }
                int topMargin;
                if (TextUtils.isEmpty(typeName)) {
                    baseViewHolder.setGone(R.id.tv_title, true);
                    topMargin = OYUtil.dp2px(20);
                } else {
                    baseViewHolder.setGone(R.id.tv_title, false);
                    baseViewHolder.setText(R.id.tv_title, typeName);
                    topMargin = OYUtil.dp2px(10);
                }
                baseViewHolder.setGone(R.id.line, true);
                lp.setMargins(0, topMargin, 0, 0);
                break;
            case ITEM_TYPE_END:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.click_bottom_background_radius);
                baseViewHolder.setGone(R.id.tv_title, true);
                baseViewHolder.setGone(R.id.line, true);

                lp.setMargins(0, 0, 0, OYUtil.dp2px(10));
                break;
            case ITEM_ONE:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.click_background_radius);
                baseViewHolder.setGone(R.id.line, true);

                String typeName1;
                switch (duelRoom.getArenaType()){
                    case DuelRoom.TYPE_ARENA_MATCH:
                        typeName1="竞技匹配";
                        break;
                    case DuelRoom.TYPE_ARENA_FUN:
                        typeName1="娱乐匹配";
                        break;
                    case DuelRoom.TYPE_ARENA_AI:
                        typeName1="人机模式";
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_SINGLE:
                        typeName1="单局模式";
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_MATCH:
                        typeName1="比赛模式";
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_TAG:
                        typeName1="双打模式";
                        break;
                    default:
                        typeName1="未知房间";
                }
                int topMargin1;
                if (TextUtils.isEmpty(typeName1)) {
                    baseViewHolder.setGone(R.id.tv_title, true);
                    topMargin1 = OYUtil.dp2px(20);
                } else {
                    baseViewHolder.setGone(R.id.tv_title, false);
                    baseViewHolder.setText(R.id.tv_title, typeName1);
                    topMargin1 = OYUtil.dp2px(10);
                }
                lp.setMargins(0, topMargin1, 0, OYUtil.dp2px(10));

                break;
            default:
                Log.e("SettingAdp", "其他情况" + (baseViewHolder.getAdapterPosition() - getHeaderLayoutCount()));
                break;
        }

        baseViewHolder.getView(R.id.ll_item).setLayoutParams(lp);



        baseViewHolder.setText(R.id.tv_name1,duelRoom.getUsers().get(0).getUsername());
        baseViewHolder.setText(R.id.tv_name2,duelRoom.getUsers().get(1).getUsername());
    }

    @Override
    public void addData(@NonNull Collection<? extends DuelRoom> newData) {
        super.addData(newData);
        Collections.sort(getData(),roomCom);
        notifyDataSetChanged();
    }

    public void remove(List<DuelRoom> duelRoomList) {
        for (int i=0;i<getData().size();i++){
            DuelRoom duelRoom=getData().get(i);
            for (DuelRoom duelRoom1:duelRoomList){
                if (duelRoom1.getId().equals(duelRoom.getId())) {
                    remove(duelRoom);
                    i--;
                }
            }
        }
    }


    private final static Comparator<DuelRoom> roomCom = new Comparator<DuelRoom>() {
        @Override
        public int compare(DuelRoom duelRoom1, DuelRoom duelRoom2) {
            return duelRoom1.getArenaType()-duelRoom2.getArenaType();
        }
    };

}
