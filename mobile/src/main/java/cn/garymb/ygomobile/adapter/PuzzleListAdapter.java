package cn.garymb.ygomobile.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;

import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.PuzzleUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

/**
 * 残局列表适配器
 * 用于显示残局文件列表
 */
public class PuzzleListAdapter extends BaseAdapter {

    private static final int SELECTED_BG_COLOR = 0x5587CEEB;
    private final Context context;
    private final List<PuzzleUtil.PuzzleInfo> puzzleList;
    private int selectedPosition = -1;

    public PuzzleListAdapter(Context context, List<PuzzleUtil.PuzzleInfo> puzzleList) {
        this.context = context;
        this.puzzleList = puzzleList;
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
        return puzzleList.size();
    }

    @Override
    public Object getItem(int position) {
        return puzzleList.get(position);
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

        PuzzleUtil.PuzzleInfo puzzle = puzzleList.get(position);
        holder.textView.setText(puzzle.toString());
        holder.textView.setTextColor(YGOUtil.c(R.color.white));

        if (position == selectedPosition) {
            holder.textView.setBackgroundColor(SELECTED_BG_COLOR);
        } else {
            holder.textView.setBackgroundColor(Color.TRANSPARENT);
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView textView;
    }
}