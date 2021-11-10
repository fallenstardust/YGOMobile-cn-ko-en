package com.ourygo.ygomobile.adapter;

import android.util.Log;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.ourygo.ygomobile.bean.OtherApp;
import com.ourygo.ygomobile.util.OYUtil;

import java.util.List;

import cn.garymb.ygomobile.lite.R;

/**
 * Create By feihua  On 2021/11/9
 */
public class OtherAppBQAdapter extends BaseQuickAdapter<OtherApp, BaseViewHolder> {

    private static final int ITEM_TYPE_SAME = 0;
    private static final int ITEM_TYPE_SWITCH = 1;
    private static final int ITEM_TYPE_DIFFERENT = 2;
    private static final int ITEM_TYPE_END = 3;
    private static final int ITEM_ONE = 4;

    public OtherAppBQAdapter(List<OtherApp> data) {
        super(R.layout.other_app_item, data);
    }

    public int getGroupType(int position) {
        List<OtherApp> data = getData();
        int currentTypeId = data.get(position).getGroupId();
        Integer lastTypeId = null, nextTypeId = null;
        if (position != 0)
            lastTypeId = data.get(position - 1).getGroupId();
        if (position != data.size() - 1)
            nextTypeId = data.get(position + 1).getGroupId();

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
    protected void convert(@NonNull BaseViewHolder baseViewHolder, OtherApp otherApp) {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) baseViewHolder.getView(R.id.rl_item).getLayoutParams();
        switch (getGroupType(baseViewHolder.getAdapterPosition() - getHeaderLayoutCount())) {
            case ITEM_TYPE_SAME:
                baseViewHolder.setBackgroundResource(R.id.rl_item, R.drawable.click_background);
                baseViewHolder.setGone(R.id.line, false);
                lp.setMargins(0, 0, 0, 0);
                break;
            case ITEM_TYPE_DIFFERENT:
                baseViewHolder.setBackgroundResource(R.id.rl_item, R.drawable.click_top_background_radius);
                int topMargin;
                topMargin = OYUtil.dp2px(20);
                baseViewHolder.setGone(R.id.line, false);
                lp.setMargins(0, topMargin, 0, 0);
                break;
            case ITEM_TYPE_END:
                baseViewHolder.setBackgroundResource(R.id.rl_item, R.drawable.click_bottom_background_radius);
                baseViewHolder.setGone(R.id.line, true);

                lp.setMargins(0, 0, 0, OYUtil.dp2px(20));
                break;
            case ITEM_ONE:
                baseViewHolder.setBackgroundResource(R.id.rl_item, R.drawable.click_background_radius);
                baseViewHolder.setGone(R.id.line, true);

                int topMargin1;
                topMargin1 = OYUtil.dp2px(20);
                lp.setMargins(0, topMargin1, 0, OYUtil.dp2px(20));

                break;
            default:
                Log.e("SettingAdp", "其他情况" + (baseViewHolder.getAdapterPosition() - getHeaderLayoutCount()));
                break;
        }
        baseViewHolder.getView(R.id.rl_item).setLayoutParams(lp);

        baseViewHolder.setText(R.id.tv_name, otherApp.getName());
        baseViewHolder.setText(R.id.tv_message, otherApp.getMessage());
        baseViewHolder.setImageResource(R.id.iv_icon, otherApp.getIcon());
    }
}
