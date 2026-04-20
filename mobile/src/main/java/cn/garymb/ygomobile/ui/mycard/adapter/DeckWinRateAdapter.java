package cn.garymb.ygomobile.ui.mycard.adapter;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.MyCardPieChart;

public class DeckWinRateAdapter extends BaseQuickAdapter<MyCardPieChart.Item, BaseViewHolder> {

    public DeckWinRateAdapter() {
        super(R.layout.item_deck_win_rate);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, MyCardPieChart.Item item) {
        TextView tvDeckName = helper.getView(R.id.tv_deck_name);
        TextView tvFirstWinRate = helper.getView(R.id.tv_first_win_rate);
        TextView tvSecondWinRate = helper.getView(R.id.tv_second_win_rate);
        TextView tvTotalWinRate = helper.getView(R.id.tv_total_win_rate);
        TextView tvFirstRecord = helper.getView(R.id.tv_first_record);
        TextView tvSecondRecord = helper.getView(R.id.tv_second_record);
        TextView tvTotalRecord = helper.getView(R.id.tv_total_record);
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
                tvFirstRecord.setText(String.format("%d胜/%d平/%d负", firstWin, firstDraw, firstLose));
            } else {
                tvFirstWinRate.setText("N/A");
                tvFirstRecord.setText("无数据");
            }

            if (second != null) {
                int secondWin = parseInt(second.getWin());
                int secondDraw = parseInt(second.getDraw());
                int secondLose = parseInt(second.getLose());
                int secondTotal = secondWin + secondDraw + secondLose;

                float secondWinRate = secondTotal > 0 ? (float) secondWin / secondTotal * 100 : 0;
                tvSecondWinRate.setText(String.format("%.1f%%", secondWinRate));
                tvSecondRecord.setText(String.format("%d胜/%d平/%d负", secondWin, secondDraw, secondLose));
            } else {
                tvSecondWinRate.setText("N/A");
                tvSecondRecord.setText("无数据");
            }

            int totalWin = (first != null ? parseInt(first.getWin()) : 0) + (second != null ? parseInt(second.getWin()) : 0);
            int totalDraw = (first != null ? parseInt(first.getDraw()) : 0) + (second != null ? parseInt(second.getDraw()) : 0);
            int totalLose = (first != null ? parseInt(first.getLose()) : 0) + (second != null ? parseInt(second.getLose()) : 0);
            int totalMatches = totalWin + totalDraw + totalLose;

            float totalWinRate = totalMatches > 0 ? (float) totalWin / totalMatches * 100 : 0;
            tvTotalWinRate.setText(String.format("%.1f%%", totalWinRate));
            tvTotalRecord.setText(String.format("%d胜/%d平/%d负", totalWin, totalDraw, totalLose));
            tvMatchCount.setText("总场次: " + totalMatches);
        } else {
            tvFirstWinRate.setText("N/A");
            tvSecondWinRate.setText("N/A");
            tvTotalWinRate.setText("N/A");
            tvFirstRecord.setText("无数据");
            tvSecondRecord.setText("无数据");
            tvTotalRecord.setText("无数据");
            tvMatchCount.setText("总场次: 0");
        }
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
