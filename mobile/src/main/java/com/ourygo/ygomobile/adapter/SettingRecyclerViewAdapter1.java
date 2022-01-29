package com.ourygo.ygomobile.adapter;


import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.ourygo.ygomobile.base.listener.OnSettingCheckListener;
import com.ourygo.ygomobile.base.listener.OnSettingSelectListener;
import com.ourygo.ygomobile.bean.ImageSelectItem;
import com.ourygo.ygomobile.bean.OYSelect;
import com.ourygo.ygomobile.bean.SettingItem;
import com.ourygo.ygomobile.util.OYUtil;

import java.util.List;

import cn.garymb.ygomobile.lite.R;

public class SettingRecyclerViewAdapter1 extends BaseMultiItemQuickAdapter<SettingItem, BaseViewHolder> {
    private static final int ITEM_TYPE_SAME = 0;
    private static final int ITEM_TYPE_SWITCH = 1;
    private static final int ITEM_TYPE_DIFFERENT = 2;
    private static final int ITEM_TYPE_END = 3;
    private static final int ITEM_ONE = 4;

    private Context context;
    private OnSettingCheckListener onSettingCheckListener;
    private OnSettingSelectListener onSettingSelectListener;

    public SettingRecyclerViewAdapter1(Context context, List<SettingItem> data) {
        super(data);
        addItemType(SettingItem.ITEM_SAME, R.layout.setting_recycl_item);
        addItemType(SettingItem.ITEM_SWITCH, R.layout.setting_switch_item);
        addItemType(SettingItem.ITEM_IMAGE_SELECT, R.layout.setting_image_select_item);
        this.context = context;
    }

    public int getGroupType(int position) {
        List<SettingItem> data = getData();
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

    public void setOnSelectListener(OnSettingSelectListener onSettingSelectListener) {
        this.onSettingSelectListener = onSettingSelectListener;
    }

    public void setOnSettingCheckListener(OnSettingCheckListener onSettingCheckListener) {
        this.onSettingCheckListener = onSettingCheckListener;
    }

    public SettingItem getItem2Id(int id) {
        for (SettingItem settingItem : getData()) {
            if (settingItem.getId() == id)
                return settingItem;
        }
        return null;
    }

    public int getItem2IdPosition(int id) {
        for (int i = 0; i < getData().size(); i++) {
            SettingItem settingItem = getData().get(i);
            if (settingItem.getId() == id)
                return i + getHeaderLayoutCount();
        }
        return -1;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder baseViewHolder, SettingItem settingItem) {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) baseViewHolder.getView(R.id.ll_item).getLayoutParams();
        switch (getGroupType(baseViewHolder.getAdapterPosition() - getHeaderLayoutCount())) {
            case ITEM_TYPE_SAME:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.click_background);
                baseViewHolder.setGone(R.id.tv_type_name, true);
                baseViewHolder.setGone(R.id.line, false);
                lp.setMargins(0, 0, 0, 0);
                break;
            case ITEM_TYPE_DIFFERENT:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.click_top_background_radius);
                String typeName = settingItem.getGroupName();
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

                lp.setMargins(0, 0, 0,0);
                break;
            case ITEM_ONE:
                baseViewHolder.setBackgroundResource(R.id.ll_item, R.drawable.click_background_radius);
                baseViewHolder.setGone(R.id.line, true);

                String typeName1 = settingItem.getGroupName();
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
                Log.e("SettingAdp", "其他情况" + (baseViewHolder.getAdapterPosition() - getHeaderLayoutCount()));
                break;
        }

        baseViewHolder.getView(R.id.ll_item).setLayoutParams(lp);

        switch (baseViewHolder.getItemViewType()) {
            case SettingItem.ITEM_SAME:
                baseViewHolder.setText(R.id.tv_name, settingItem.getName());
                baseViewHolder.setTextColor(R.id.tv_name, settingItem.getNameColor());

                baseViewHolder.setGone(R.id.pb_loading, !settingItem.isLoading());
                baseViewHolder.setGone(R.id.ll_item, !settingItem.isContent());
                LinearLayout.LayoutParams messageLayout = (LinearLayout.LayoutParams) baseViewHolder.getView(R.id.tv_message).getLayoutParams();
                String message = settingItem.getMessage();
                if (TextUtils.isEmpty(message)) {
                    baseViewHolder.setGone(R.id.tv_message, true);
                } else {
                    baseViewHolder.setGone(R.id.tv_message, false);
                    baseViewHolder.setText(R.id.tv_message, message);
                    baseViewHolder.setTextColor(R.id.tv_message, settingItem.getMessageColor());
                }

                if (settingItem.isNext()) {
                    baseViewHolder.setGone(R.id.iv_guide_right, false);
                    messageLayout.setMargins(0, 0, OYUtil.dp2px(6), 0);
                } else {
                    baseViewHolder.setGone(R.id.iv_guide_right, true);
                    messageLayout.setMargins(0, 0, OYUtil.dp2px(20), 0);
                }
                baseViewHolder.getView(R.id.tv_message).setLayoutParams(messageLayout);

                int icon = settingItem.getIcon();
                LinearLayout.LayoutParams textLayout = (LinearLayout.LayoutParams) baseViewHolder.getView(R.id.tv_name).getLayoutParams();
                RelativeLayout.LayoutParams lineLayout = (RelativeLayout.LayoutParams) baseViewHolder.getView(R.id.line).getLayoutParams();

                if (icon != SettingItem.ICON_NULL) {
                    baseViewHolder.setGone(R.id.iv_icon, false);
                    baseViewHolder.setImageResource(R.id.iv_icon, icon);
                    textLayout.setMargins(OYUtil.dp2px(10), 0, 0, 0);
                    lineLayout.setMargins(OYUtil.dp2px(51), 0, 0, 0);
                } else {
                    baseViewHolder.setGone(R.id.iv_icon, true);
                    textLayout.setMargins(OYUtil.dp2px(25), 0, 0, 0);
                    lineLayout.setMargins(OYUtil.dp2px(25), 0, 0, 0);
                }
                baseViewHolder.getView(R.id.tv_name).setLayoutParams(textLayout);
                baseViewHolder.getView(R.id.line).setLayoutParams(lineLayout);
                break;
            case SettingItem.ITEM_SWITCH:
                baseViewHolder.setText(R.id.tv_name, settingItem.getName());
                baseViewHolder.setTextColor(R.id.tv_name, settingItem.getNameColor());

                SwitchCompat sw_switch = baseViewHolder.getView(R.id.sw_switch);
                sw_switch.setChecked((boolean) settingItem.getObject());
                sw_switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    settingItem.setObject(isChecked);
                    if (onSettingCheckListener != null)
                        onSettingCheckListener.onSettingCheck(settingItem.getId(), isChecked);
                });

                int icon1 = settingItem.getIcon();
                LinearLayout.LayoutParams textLayout1 = (LinearLayout.LayoutParams) baseViewHolder.getView(R.id.tv_name).getLayoutParams();
                RelativeLayout.LayoutParams lineLayout1 = (RelativeLayout.LayoutParams) baseViewHolder.getView(R.id.line).getLayoutParams();

                if (icon1 != SettingItem.ICON_NULL) {
                    baseViewHolder.setGone(R.id.iv_icon, false);
                    baseViewHolder.setImageResource(R.id.iv_icon, icon1);
                    textLayout1.setMargins(OYUtil.dp2px(10), 0, 0, 0);
                    lineLayout1.setMargins(OYUtil.dp2px(51), 0, 0, 0);
                } else {
                    baseViewHolder.setGone(R.id.iv_icon, true);
                    textLayout1.setMargins(OYUtil.dp2px(25), 0, 0, 0);
                    lineLayout1.setMargins(OYUtil.dp2px(25), 0, 0, 0);
                }
                baseViewHolder.getView(R.id.tv_name).setLayoutParams(textLayout1);
                baseViewHolder.getView(R.id.line).setLayoutParams(lineLayout1);
                break;
            case SettingItem.ITEM_IMAGE_SELECT:
                RecyclerView rv_list = baseViewHolder.getView(R.id.rv_list);
                LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
                linearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                rv_list.setLayoutManager(linearLayoutManager);
                OYSelect oySelect = (OYSelect) settingItem.getObject();
                ImageSelectBQAdapter imageSelectBQAdapter = new ImageSelectBQAdapter((List<ImageSelectItem>) oySelect.getObject());
                imageSelectBQAdapter.setSelectPosition(oySelect.getPosition());
                rv_list.setAdapter(imageSelectBQAdapter);
                imageSelectBQAdapter.setOnSelectListener((imageSelectItem, lastPosition, position) -> {
                    oySelect.setPosition(position);
                    if (onSettingSelectListener != null)
                        onSettingSelectListener.onSettingSelect(settingItem.getId(), imageSelectItem, lastPosition, position);
                });
                break;
        }
    }
}
