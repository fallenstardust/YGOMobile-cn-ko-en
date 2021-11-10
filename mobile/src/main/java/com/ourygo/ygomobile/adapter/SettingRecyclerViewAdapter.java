package com.ourygo.ygomobile.adapter;


import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.chad.library.adapter.base.module.LoadMoreModule;
import com.ourygo.ygomobile.base.listener.OnSettingItemClickListener;
import com.ourygo.ygomobile.bean.SettingItem;
import com.ourygo.ygomobile.util.OYUtil;

import java.util.List;

import cn.garymb.ygomobile.lite.R;

public class SettingRecyclerViewAdapter extends RecyclerView.Adapter<SettingRecyclerViewAdapter.ViewHolder> implements LoadMoreModule {
    private static final int ITEM_TYPE_SAME = 0;
    private static final int ITEM_TYPE_SWITCH=1;
    private static final int ITEM_TYPE_DIFFERENT = 2;
    private static final int ITEM_TYPE_END = 3;
    private static final int ITEM_ONE = 4;

    private final static int TYPE_VIEW_HEADER = 3;
    private final static int TYPE_VIEW_END = 4;
    private Context context;
    private List<SettingItem> data;
    private View headerView;
    private View endView;

    private OnSettingItemClickListener mOnSettingItemClickListener;

    public SettingRecyclerViewAdapter(Context context, List<SettingItem> data) {
        this.context = context;
        this.data = data;
    }

    @Override
    public int getItemViewType(int position) {

        if (headerView != null) {
            if (position == 0)
                return TYPE_VIEW_HEADER;
            position--;
        }
        if (endView != null) {
            if (position == getItemCount() - 1)
                return TYPE_VIEW_END;
        }

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

//            if (position == data.size() - 1) {
//                if (position == 0)
//                    return ITEM_ONE;
//                else if (data.get(position))
//            } else if (position == 0) {
//
//            } else {
//                if ( ==data.get(position + 1).getTypeId()){
//                    return ITEM_TYPE_SAME;
//                }
//                return ITEM_TYPE_DIFFERENT;
//            }

        //return TYPE_VIEW_ITEM;


    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup p1, int type) {
        if (type == TYPE_VIEW_HEADER) {
            return new ViewHolder(headerView, false);
        }
        if (type == TYPE_VIEW_END) {
            return new ViewHolder(endView, false);
        }

        View v = LayoutInflater.from(p1.getContext()).inflate(R.layout.setting_recycl_item, p1, false);

        return new ViewHolder(v, true);
    }

    @Override
    public void onBindViewHolder(ViewHolder vh, int position) {
        int mPosition=position;

        if (headerView != null) {
            if (position == 0) {
                return;
            }
            position--;
        }

        if (endView != null) {
            if (position == getItemCount() - 1) {
                return;
            }
        }


        SettingItem settingItem = data.get(position);

        final int p2 = position;

        vh.tv_name.setText(settingItem.getName());
        if (settingItem.isNext()) {
            vh.iv_guide_right.setVisibility(View.VISIBLE);
            String message = settingItem.getMessage();
            if (TextUtils.isEmpty(message)) {
                vh.tv_message.setVisibility(View.GONE);
            } else {
                vh.tv_message.setVisibility(View.VISIBLE);
                vh.tv_message.setText(message);
            }
        } else {
            vh.iv_guide_right.setVisibility(View.GONE);
            vh.tv_message.setVisibility(View.GONE);
        }
        int icon = settingItem.getIcon();
        if (icon != SettingItem.ICON_NULL) {
            vh.iv_icon.setVisibility(View.VISIBLE);
            vh.iv_icon.setImageResource(icon);
        } else {
            vh.iv_icon.setVisibility(View.GONE);
        }
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) vh.ll_item.getLayoutParams();
        switch (getItemViewType(mPosition)) {
            case ITEM_TYPE_SAME:
                vh.ll_item.setBackgroundResource(R.drawable.click_background);
                vh.tv_type_name.setVisibility(View.GONE);
                vh.line.setVisibility(View.VISIBLE);
                lp.setMargins(0, 0, 0, 0);
                break;
            case ITEM_TYPE_DIFFERENT:
                vh.ll_item.setBackgroundResource(R.drawable.click_top_background_radius);
                String typeName = settingItem.getGroupName();
                int topMargin;
                if (TextUtils.isEmpty(typeName)) {
                    vh.tv_type_name.setVisibility(View.GONE);
                    topMargin = OYUtil.dp2px(20);
                } else {
                    vh.tv_type_name.setVisibility(View.VISIBLE);
                    vh.tv_type_name.setText(typeName);
                    topMargin = OYUtil.dp2px(10);
                }
                vh.line.setVisibility(View.VISIBLE);
                lp.setMargins(0, topMargin, 0, 0);
                break;
            case ITEM_TYPE_END:
                vh.ll_item.setBackgroundResource(R.drawable.click_bottom_background_radius);
                vh.tv_type_name.setVisibility(View.GONE);
                vh.line.setVisibility(View.GONE);
                lp.setMargins(0, 0, 0, 0);
                break;
            case ITEM_ONE:
                vh.ll_item.setBackgroundResource(R.drawable.click_background_radius);
                break;
        }

        vh.ll_item.setLayoutParams(lp);

        vh.ll_item.setOnClickListener(p1 -> {

            if (mOnSettingItemClickListener != null) {
                mOnSettingItemClickListener.onSettingItemClick(data.get(p2), data.get(p2).getId(), p2);
            }
        });
    }

    @Override
    public int getItemCount() {
        if (headerView != null && endView != null) {
            return data.size() + 2;
        } else if (headerView != null || endView != null) {
            return data.size() + 1;
        }

        return data.size();
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
        if (manager instanceof GridLayoutManager) {
            final GridLayoutManager gridManager = ((GridLayoutManager) manager);
            gridManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return getItemViewType(position) == TYPE_VIEW_HEADER
                            ? gridManager.getSpanCount() : 1;
                }
            });
        }
    }

    @Override
    public void onViewAttachedToWindow(ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        if (lp != null
                && lp instanceof StaggeredGridLayoutManager.LayoutParams) {
            StaggeredGridLayoutManager.LayoutParams p = (StaggeredGridLayoutManager.LayoutParams) lp;
            if (headerView != null)
                p.setFullSpan(holder.getLayoutPosition() == 0);
//            if(mFooterView!=null)
//                 p.setFullSpan(holder.getLayoutPosition() ==datas.size());
        }
    }

    public void sx() {
        notifyDataSetChanged();

    }

    public void setHeader(View headerView) {
        this.headerView = headerView;
        notifyDataSetChanged();
    }

    public void removeHeader(){
        if (headerView==null)
            return;
        this.headerView=null;
        notifyDataSetChanged();
    }

    public void setEnd(View endView) {
        this.endView = endView;
        notifyDataSetChanged();
    }

    public void setSettingItemClickListener(OnSettingItemClickListener mOnSettingItemClickListener) {
        this.mOnSettingItemClickListener = mOnSettingItemClickListener;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        View v;
        View line;
        TextView tv_name, tv_type_name, tv_message;
        ImageView iv_icon, iv_guide_right;
        private LinearLayout ll_item;


        public ViewHolder(View v, boolean isFind) {
            super(v);
            if (isFind) {
                this.v = v;
                tv_name = v.findViewById(R.id.tv_name);
                line = v.findViewById(R.id.line);
                tv_type_name = v.findViewById(R.id.tv_type_name);
                tv_message = v.findViewById(R.id.tv_message);
                iv_icon = v.findViewById(R.id.iv_icon);
                iv_guide_right = v.findViewById(R.id.iv_guide_right);
                ll_item = v.findViewById(R.id.ll_item);
            }
        }
    }
}
