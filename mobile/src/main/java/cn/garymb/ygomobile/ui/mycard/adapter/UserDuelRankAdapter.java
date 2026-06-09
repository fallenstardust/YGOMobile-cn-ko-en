package cn.garymb.ygomobile.ui.mycard.adapter;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.king.view.circleprogressview.CircleProgressView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.bean.UserDuelRank;

public class UserDuelRankAdapter extends RecyclerView.Adapter<UserDuelRankAdapter.ViewHolder> {

    private List<UserDuelRank> mData = new ArrayList<>();

    public interface OnItemClickListener {
        void onItemClick(UserDuelRank rank);
    }

    private OnItemClickListener mOnItemClickListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mOnItemClickListener = listener;
    }

    public void setNewData(List<UserDuelRank> data) {
        this.mData = data != null ? data : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_duel_rank, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserDuelRank rank = mData.get(position);
        holder.bind(rank, position + 1);
        holder.itemView.setOnClickListener(v -> {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(rank);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank;
        TextView tvUsername;
        TextView tvPt, tvExp;
        TextView tvAthleticWin;
        TextView tvAthleticLose;
        TextView tvAthleticDraw;
        TextView tvAthleticAll;
        TextView tvEntertainWin;
        TextView tvEntertainLose;
        TextView tvEntertainDraw;
        TextView tvEntertainAll;
        CircleProgressView cpvAthleticWinRate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tv_rank);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvPt = itemView.findViewById(R.id.tv_pt);
            tvExp = itemView.findViewById(R.id.tv_exp);
            tvAthleticWin = itemView.findViewById(R.id.tv_athletic_win);
            tvAthleticLose = itemView.findViewById(R.id.tv_athletic_lose);
            tvAthleticDraw = itemView.findViewById(R.id.tv_athletic_draw);
            tvAthleticAll = itemView.findViewById(R.id.tv_athletic_all);
            tvEntertainWin = itemView.findViewById(R.id.tv_entertain_win);
            tvEntertainLose = itemView.findViewById(R.id.tv_entertain_lose);
            tvEntertainDraw = itemView.findViewById(R.id.tv_entertain_draw);
            tvEntertainAll = itemView.findViewById(R.id.tv_entertain_all);
            cpvAthleticWinRate = itemView.findViewById(R.id.cpv_athletic_win_rate);
        }

        void bind(UserDuelRank rank, int rankNum) {
            tvRank.setText(String.valueOf(rankNum));
            tvUsername.setText(rank.getUsername());
            tvPt.setText(String.format("%.2f", rank.getPt()));
            tvExp.setText(String.format("%.2f", rank.getExp()));
            tvAthleticWin.setText(String.valueOf(rank.getAthleticWin()));
            tvAthleticLose.setText(String.valueOf(rank.getAthleticLose()));
            tvAthleticDraw.setText(String.valueOf(rank.getAthleticDraw()));
            tvAthleticAll.setText(String.valueOf(rank.getAthleticAll()));
            
            tvEntertainWin.setText(String.valueOf(rank.getEntertainWin()));
            tvEntertainLose.setText(String.valueOf(rank.getEntertainLose()));
            tvEntertainDraw.setText(String.valueOf(rank.getEntertainDraw()));
            tvEntertainAll.setText(String.valueOf(rank.getEntertainAll()));
            
            float athleticWinRate = rank.getAthleticWinRate();
            cpvAthleticWinRate.setProgress((int) athleticWinRate);
            cpvAthleticWinRate.setLabelText(String.format("%.2f%%", athleticWinRate));
        }
    }
}
