package cn.garymb.ygomobile.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.BotUtil;

/**
 * 人机列表适配器
 * 用于显示AI对手列表
 */
public class BotListAdapter extends BaseAdapter {

    private static final int SELECTED_BG_COLOR = 0x5587CEEB;
    private final Context context;
    private final List<BotUtil.BotInfo> botList;
    private int selectedPosition = -1;

    public BotListAdapter(Context context, List<BotUtil.BotInfo> botList) {
        this.context = context;
        this.botList = botList;
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @Override
    public int getCount() {
        return botList.size();
    }

    @Override
    public Object getItem(int position) {
        return botList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(
                    R.layout.item_bot_list, parent, false);
            holder = new ViewHolder();
            holder.textView = convertView.findViewById(R.id.tv_bot_item);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        BotUtil.BotInfo bot = botList.get(position);
        holder.textView.setText(bot.toString());

        if (position == selectedPosition) {
            holder.textView.setBackgroundColor(SELECTED_BG_COLOR);
        } else {
            holder.textView.setBackgroundColor(Color.TRANSPARENT);
        }

        // 根据AI等级设置不同颜色
        int color = 0xFFFFFFFF;
        switch (bot.aiLevel) {
            case 1: color = 0xFF90EE90; break;  // 简单 - 绿色
            case 2: color = 0xFF87CEEB; break;  // 普通 - 蓝色
            case 3: color = 0xFFFFD700; break;  // 困难 - 金色
            case 4: color = 0xFFFFA500; break;  // 专家 - 橙色
            case 5: color = 0xFFFF6347; break;  // 狂野 - 红色
            case 6: color = 0xFFDA70D6; break;  // 反Meta - 紫色
        }
        holder.textView.setTextColor(color);

        return convertView;
    }

    private static class ViewHolder {
        TextView textView;
    }
}