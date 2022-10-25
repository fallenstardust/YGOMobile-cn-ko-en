package com.ourygo.ygomobile.adapter;

import android.text.TextUtils;
import android.util.Log;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.ourygo.ygomobile.util.LogUtil;
import com.ourygo.ygomobile.util.OYUtil;

import java.util.List;

import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;

/**
 * Create By feihua  On 2021/11/10
 */
public class DeckListBQAdapter extends BaseQuickAdapter<DeckFile, BaseViewHolder> {


    private static final int ITEM_TYPE_SAME = 0;
    private static final int ITEM_TYPE_SWITCH = 1;
    private static final int ITEM_TYPE_DIFFERENT = 2;
    private static final int ITEM_TYPE_END = 3;
    private static final int ITEM_ONE = 4;
    private ImageLoader imageLoader;

    public DeckListBQAdapter(ImageLoader imageLoader,List<DeckFile> data) {
        super(R.layout.deck_list_item, data);
        this.imageLoader=imageLoader;
    }


    public int getGroupType(int position) {
        List<DeckFile> data = getData();
        String currentTypeId = data.get(position).getTypeName();
        String lastTypeId = null, nextTypeId = null;
        if (position != 0)
            lastTypeId = data.get(position - 1).getTypeName();
        if (position != data.size() - 1)
            nextTypeId = data.get(position + 1).getTypeName();

        if (!TextUtils.isEmpty(lastTypeId) && !TextUtils.isEmpty(nextTypeId)) {
            if (currentTypeId.equals(lastTypeId) && currentTypeId.equals(nextTypeId))
                return ITEM_TYPE_SAME;
            else if (currentTypeId.equals(lastTypeId))
                return ITEM_TYPE_END;
            else if (currentTypeId.equals(nextTypeId))
                return ITEM_TYPE_DIFFERENT;
            else
                return ITEM_ONE;

        } else if (!TextUtils.isEmpty(lastTypeId )) {
            if (currentTypeId.equals(lastTypeId))
                return ITEM_TYPE_END;
            else
                return ITEM_ONE;
        } else if (!TextUtils.isEmpty(nextTypeId)) {
            if (currentTypeId.equals(nextTypeId))
                return ITEM_TYPE_DIFFERENT;
            else
                return ITEM_ONE;
        } else {
            return ITEM_ONE;
        }

    }


    @Override
    protected void convert(@NonNull BaseViewHolder baseViewHolder, DeckFile deck) {
        LogUtil.time("DeckAdapter","开始");
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) baseViewHolder.getView(R.id.ll_item).getLayoutParams();
        switch (getGroupType(getItemPosition(deck))) {
            case ITEM_TYPE_SAME:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.click_background);
                baseViewHolder.setGone(R.id.tv_type_name, true);
                baseViewHolder.setGone(R.id.line, false);
                lp.setMargins(0, 0, 0, 0);
                break;
            case ITEM_TYPE_DIFFERENT:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.click_top_background_radius);
                String typeName = deck.getTypeName();
                int topMargin;
                if (TextUtils.isEmpty(typeName)) {
                    baseViewHolder.setGone(R.id.tv_type_name, true);
                    topMargin = OYUtil.dp2px(20);
                } else {
                    baseViewHolder.setGone(R.id.tv_type_name, false);
                    baseViewHolder.setText(R.id.tv_type_name, typeName);
                    topMargin = OYUtil.dp2px(10);
                }
                baseViewHolder.setGone(R.id.line, false);
                lp.setMargins(0, topMargin, 0, 0);
                break;
            case ITEM_TYPE_END:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.click_bottom_background_radius);
                baseViewHolder.setGone(R.id.tv_type_name, true);
                baseViewHolder.setGone(R.id.line, true);

                lp.setMargins(0, 0, 0, 0);
                break;
            case ITEM_ONE:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.click_background_radius);
                baseViewHolder.setGone(R.id.line, true);

                String typeName1 = deck.getTypeName();
                int topMargin1;
                if (TextUtils.isEmpty(typeName1)) {
                    baseViewHolder.setGone(R.id.tv_type_name, true);
                    topMargin1 = OYUtil.dp2px(20);
                } else {
                    baseViewHolder.setGone(R.id.tv_type_name, false);
                    baseViewHolder.setText(R.id.tv_type_name, typeName1);
                    topMargin1 = OYUtil.dp2px(10);
                }
                lp.setMargins(0, topMargin1, 0, 0);

                break;
            default:
//                Log.e("SettingAdp", "其他情况" + (baseViewHolder.getAdapterPosition() - getHeaderLayoutCount()));
                break;
        }

        baseViewHolder.getView(R.id.ll_item).setLayoutParams(lp);
        imageLoader.bindImage(baseViewHolder.getView(R.id.iv_card),deck.getFirstCode(),ImageLoader.Type.small);
        baseViewHolder.setText(R.id.tv_name,deck.getName());
        LogUtil.time("DeckAdapter","完成");
    }
}
