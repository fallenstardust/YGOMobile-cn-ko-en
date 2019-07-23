package cn.garymb.ygomobile.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cn.garymb.ygomobile.bean.TextSelect;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;

public class TextSelectAdapter extends RecyclerView.Adapter<TextSelectAdapter.ViewHolder> {

    private OnItemSelectListener onItemSelectListener;
    private List<? extends TextSelect> data;
    private int selectPosition;

    public TextSelectAdapter(List<? extends TextSelect> data, int selectPosition) {
        this.data = data;
        this.selectPosition = selectPosition;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.text_select_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.tv_name.setText(data.get(position).getName());
        if (position==selectPosition){
            holder.view.setBackgroundColor(YGOUtil.c(R.color.gray));
        }else {
            holder.view.setBackgroundColor(YGOUtil.c(R.color.white));
        }
        holder.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (position==selectPosition)
                    return;
                selectPosition = position;
                notifyDataSetChanged();
                onItemSelectListener.onItemSelect(position, data.get(position).getObject());
            }
        });
    }

    public void setOnItemSelectListener(OnItemSelectListener onItemSelectListener) {
        this.onItemSelectListener = onItemSelectListener;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        View view;
        TextView tv_name;

        public ViewHolder(View view) {
            super(view);
            this.view = view;
            this.tv_name = view.findViewById(R.id.tv_name);
        }
    }

    public interface OnItemSelectListener<T> {
        void onItemSelect(int position, T item);
    }

}
