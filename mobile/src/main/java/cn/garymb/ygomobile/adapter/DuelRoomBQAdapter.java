package cn.garymb.ygomobile.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.bean.DuelRoom;
import cn.garymb.ygomobile.utils.YGOUtil;

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

    public DuelRoomBQAdapter(Context context, List<DuelRoom> data) {
        super(R.layout.item_duel_room, data);
        this.context = context;
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
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) baseViewHolder.getView(R.id.ll_item).getLayoutParams();
        switch (getGroupType(baseViewHolder.getLayoutPosition() - getHeaderLayoutCount())) {
            case ITEM_TYPE_SAME:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.list_item_bg);
                baseViewHolder.setGone(R.id.tv_title, true);
                baseViewHolder.setGone(R.id.line, true);
                lp.setMargins(0, 0, 0, 0);
                break;
            case ITEM_TYPE_DIFFERENT:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.list_item_bg);
                String typeName;
                switch (duelRoom.getArenaType()) {
                    case DuelRoom.TYPE_ARENA_MATCH:
                        typeName = YGOUtil.s(R.string.match_start);
                        break;
                    case DuelRoom.TYPE_ARENA_FUN:
                        typeName = YGOUtil.s(R.string.fun_start);
                        break;
                    case DuelRoom.TYPE_ARENA_AI:
                        typeName = YGOUtil.s(R.string.bot_mode);
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_SINGLE:
                        typeName = YGOUtil.s(R.string.single_duel);
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_MATCH:
                        typeName = YGOUtil.s(R.string.match_duel);
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_TAG:
                        typeName = YGOUtil.s(R.string.tag_duel);
                        break;
                    default:
                        typeName = YGOUtil.s(R.string.unknown_room);
                }
                int typeCount = getTypeCount(duelRoom.getArenaType());
                typeName = typeName + " [" + typeCount + "]";
                
                int topMargin;
                if (TextUtils.isEmpty(typeName)) {
                    baseViewHolder.setGone(R.id.tv_title, true);
                    topMargin = YGOUtil.dp2px(20);
                } else {
                    baseViewHolder.setGone(R.id.tv_title, false);
                    baseViewHolder.setText(R.id.tv_title, typeName);
                    topMargin = YGOUtil.dp2px(10);
                }
                baseViewHolder.setGone(R.id.line, true);
                lp.setMargins(0, topMargin, 0, 0);
                break;
            case ITEM_TYPE_END:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.list_item_bg);
                baseViewHolder.setGone(R.id.tv_title, true);
                baseViewHolder.setGone(R.id.line, true);

                lp.setMargins(0, 0, 0, YGOUtil.dp2px(10));
                break;
            case ITEM_ONE:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.list_item_bg);
                baseViewHolder.setGone(R.id.line, true);

                String typeName1;
                switch (duelRoom.getArenaType()) {
                    case DuelRoom.TYPE_ARENA_MATCH:
                        typeName1 = YGOUtil.s(R.string.match_start);
                        break;
                    case DuelRoom.TYPE_ARENA_FUN:
                        typeName1 = YGOUtil.s(R.string.fun_start);
                        break;
                    case DuelRoom.TYPE_ARENA_AI:
                        typeName1 = YGOUtil.s(R.string.bot_mode);
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_SINGLE:
                        typeName1 = YGOUtil.s(R.string.single_duel);
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_MATCH:
                        typeName1 = YGOUtil.s(R.string.match_duel);
                        break;
                    case DuelRoom.TYPE_ARENA_FUN_TAG:
                        typeName1 = YGOUtil.s(R.string.tag_duel);
                        break;
                    default:
                        typeName1 = YGOUtil.s(R.string.unknown_room);
                }
                int typeCount1 = getTypeCount(duelRoom.getArenaType());
                typeName1 = typeName1 + " (" + typeCount1 + ")";
                
                int topMargin1;
                if (TextUtils.isEmpty(typeName1)) {
                    baseViewHolder.setGone(R.id.tv_title, true);
                    topMargin1 = YGOUtil.dp2px(20);
                } else {
                    baseViewHolder.setGone(R.id.tv_title, false);
                    baseViewHolder.setText(R.id.tv_title, typeName1);
                    topMargin1 = YGOUtil.dp2px(10);
                }
                lp.setMargins(0, topMargin1, 0, YGOUtil.dp2px(10));

                break;
            default:
                Log.e("SettingAdp", "其他情况" + (baseViewHolder.getAdapterPosition() - getHeaderLayoutCount()));
                break;
        }

        baseViewHolder.getView(R.id.ll_item).setLayoutParams(lp);


        List<DuelRoom.UserBean> users = duelRoom.getUsers();
        String leftName = "";
        String rightName = "";
        if (users != null && users.size() >= 2) {
            leftName = users.get(0).getUsername();
            rightName = users.get(1).getUsername();
        } else {
            leftName = !TextUtils.isEmpty(duelRoom.getTitle()) ? duelRoom.getTitle() : duelRoom.getId();
            if (users != null && users.size() == 1) {
                rightName = users.get(0).getUsername();
            }
        }
        baseViewHolder.setText(R.id.tv_name1, leftName);
        baseViewHolder.setText(R.id.tv_name2, rightName);
    }

    private int getTypeCount(int arenaType) {
        int count = 0;
        for (DuelRoom room : getData()) {
            if (room.getArenaType() == arenaType) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void addData(@NonNull Collection<? extends DuelRoom> newData) {
        super.addData(newData);
        Collections.sort(getData(), roomCom);
        notifyDataSetChanged();
    }

    public void remove(List<DuelRoom> duelRoomList) {
        for (int i = 0; i < getData().size(); i++) {
            DuelRoom duelRoom = getData().get(i);
            for (DuelRoom duelRoom1 : duelRoomList) {
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
            return duelRoom1.getArenaType() - duelRoom2.getArenaType();
        }
    };

}
