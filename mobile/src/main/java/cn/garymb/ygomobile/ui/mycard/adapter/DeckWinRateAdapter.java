package cn.garymb.ygomobile.ui.mycard.adapter;

import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.bean.MyCardPieChart;

public class DeckWinRateAdapter extends BaseQuickAdapter<MyCardPieChart.Item, BaseViewHolder> {

    private OnItemClickListener mListener;

    public interface OnItemClickListener {
        void onItemClick(String deckName);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mListener = listener;
    }

    public DeckWinRateAdapter() {
        super(R.layout.item_mycard_deck_win_rate);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, MyCardPieChart.Item item) {
        TextView tvDeckName = helper.getView(R.id.tv_deck_name);
        TextView tvFirstWinRate = helper.getView(R.id.tv_first_win_rate);
        TextView tvSecondWinRate = helper.getView(R.id.tv_second_win_rate);
        TextView tvTotalWinRate = helper.getView(R.id.tv_total_win_rate);
        TextView tvFirstRecordWin = helper.getView(R.id.tv_first_record_win);
        TextView tvFirstRecordDraw = helper.getView(R.id.tv_first_record_draw);
        TextView tvFirstRecordLose = helper.getView(R.id.tv_first_record_lose);
        TextView tvSecondRecordWin = helper.getView(R.id.tv_second_record_win);
        TextView tvSecondRecordDraw = helper.getView(R.id.tv_second_record_draw);
        TextView tvSecondRecordLose = helper.getView(R.id.tv_second_record_lose);
        TextView tvTotalRecordWin = helper.getView(R.id.tv_total_record_win);
        TextView tvTotalRecordDraw = helper.getView(R.id.tv_total_record_draw);
        TextView tvTotalRecordLose = helper.getView(R.id.tv_total_record_lose);
        TextView tvMatchCount = helper.getView(R.id.tv_match_count);

        tvDeckName.setText(item.getName());

        MyCardPieChart.Matchup matchup = item.getMatchup();

        if (matchup != null) {
            MyCardPieChart.First first = matchup.getFirst();
            MyCardPieChart.Second second = matchup.getSecond();

            if (first != null) {
                int firstWin = parseInt(first.getWin());
                int firstDraw = parseInt(first.getDraw());
                int firstLose = parseInt(first.getLose());
                int firstTotal = firstWin + firstDraw + firstLose;

                float firstWinRate = firstTotal > 0 ? (float) firstWin / firstTotal * 100 : 0;
                tvFirstWinRate.setText(String.format("%.1f%%", firstWinRate));
                tvFirstRecordWin.setText(String.valueOf(firstWin));
                tvFirstRecordDraw.setText(String.valueOf(firstDraw));
                tvFirstRecordLose.setText(String.valueOf(firstLose));
            } else {
                tvFirstWinRate.setText("N/A");
                tvFirstRecordWin.setText("-");
                tvFirstRecordDraw.setText("-");
                tvFirstRecordLose.setText("-");
            }

            if (second != null) {
                int secondWin = parseInt(second.getWin());
                int secondDraw = parseInt(second.getDraw());
                int secondLose = parseInt(second.getLose());
                int secondTotal = secondWin + secondDraw + secondLose;

                float secondWinRate = secondTotal > 0 ? (float) secondWin / secondTotal * 100 : 0;
                tvSecondWinRate.setText(String.format("%.1f%%", secondWinRate));
                tvSecondRecordWin.setText(String.valueOf(secondWin));
                tvSecondRecordDraw.setText(String.valueOf(secondDraw));
                tvSecondRecordLose.setText(String.valueOf(secondLose));
            } else {
                tvSecondWinRate.setText("N/A");
                tvSecondRecordWin.setText("-");
                tvSecondRecordDraw.setText("-");
                tvSecondRecordLose.setText("-");
            }

            int totalWin = (first != null ? parseInt(first.getWin()) : 0) + (second != null ? parseInt(second.getWin()) : 0);
            int totalDraw = (first != null ? parseInt(first.getDraw()) : 0) + (second != null ? parseInt(second.getDraw()) : 0);
            int totalLose = (first != null ? parseInt(first.getLose()) : 0) + (second != null ? parseInt(second.getLose()) : 0);
            int totalMatches = totalWin + totalDraw + totalLose;

            float totalWinRate = totalMatches > 0 ? (float) totalWin / totalMatches * 100 : 0;
            tvTotalWinRate.setText(String.format("%.1f%%", totalWinRate));
            tvTotalRecordWin.setText(String.valueOf(totalWin));
            tvTotalRecordDraw.setText(String.valueOf(totalDraw));
            tvTotalRecordLose.setText(String.valueOf(totalLose));
            tvMatchCount.setText(String.valueOf(totalMatches));
        } else {
            tvFirstWinRate.setText("N/A");
            tvSecondWinRate.setText("N/A");
            tvTotalWinRate.setText("N/A");
            tvFirstRecordWin.setText("-");
            tvFirstRecordDraw.setText("-");
            tvFirstRecordLose.setText("-");
            tvSecondRecordWin.setText("-");
            tvSecondRecordDraw.setText("-");
            tvSecondRecordLose.setText("-");
            tvTotalRecordWin.setText("-");
            tvTotalRecordDraw.setText("-");
            tvTotalRecordLose.setText("-");
            tvMatchCount.setText("-");
        }

        helper.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onItemClick(item.getName());
            }
        });
    }

    private int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
