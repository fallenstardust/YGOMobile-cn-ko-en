package cn.garymb.ygomobile.ui.mycard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.king.view.circleprogressview.CircleProgressView;

import java.util.List;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.adapter.DeckWinRateAdapter;
import cn.garymb.ygomobile.utils.YGOUtil;

public class DeckWinRateFragment extends BaseFragemnt {

    private SwipeRefreshLayout srlRefresh;
    private RecyclerView rvDeckList;
    private ProgressBar pbLoading;
    private TextView tvEmpty;
    private DeckWinRateAdapter adapter;
    private LinearLayout llPieChartContainer;
    private LinearLayout llPieCharts;
    private TextView tvTotalMatches;
    private View btnBack;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_deck_win_rate, container, false);
        initView(view);
        loadData();
        return view;
    }

    private void initView(View view) {
        srlRefresh = view.findViewById(R.id.srl_refresh);
        rvDeckList = view.findViewById(R.id.rv_deck_list);
        pbLoading = view.findViewById(R.id.pb_loading);
        tvEmpty = view.findViewById(R.id.tv_empty);
        llPieChartContainer = view.findViewById(R.id.ll_pie_chart_container);
        llPieCharts = view.findViewById(R.id.ll_pie_charts);
        tvTotalMatches = view.findViewById(R.id.tv_total_matches);
        btnBack = view.findViewById(R.id.btn_back);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> goBack());
        }

        srlRefresh.setColorSchemeColors(YGOUtil.c(R.color.colorAccent));
        srlRefresh.setOnRefreshListener(() -> loadData());

        rvDeckList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DeckWinRateAdapter();
        rvDeckList.setAdapter(adapter);
    }

    private void loadData() {
        srlRefresh.setRefreshing(true);
        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        MyCardPieChart.getDayAthleticDeckTypeAnalytics(new MyCardPieChart.OnMyCardPieChartListener() {
            @Override
            public void onMyCardPieChartQuery(MyCardPieChart pieChart, String exception) {
                if (getActivity() == null) {
                    return;
                }
                
                getActivity().runOnUiThread(() -> {
                    srlRefresh.setRefreshing(false);
                    pbLoading.setVisibility(View.GONE);

                    if (exception != null) {
                        YGOUtil.show("加载失败: " + exception);
                        return;
                    }

                    if (pieChart != null && !pieChart.isEmpty()) {
                        List<MyCardPieChart.Item> sortedList = new java.util.ArrayList<>(pieChart);
                        sortedList.sort((item1, item2) -> {
                            int matches1 = getDeckTotalMatches(item1);
                            int matches2 = getDeckTotalMatches(item2);
                            return Integer.compare(matches2, matches1);
                        });
                        
                        adapter.setNewData(sortedList);
                        tvEmpty.setVisibility(View.GONE);
                        updatePieChart(pieChart);
                    } else {
                        tvEmpty.setVisibility(View.VISIBLE);
                        llPieChartContainer.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void updatePieChart(MyCardPieChart pieChart) {
        if (pieChart == null || pieChart.isEmpty()) {
            llPieChartContainer.setVisibility(View.GONE);
            return;
        }

        llPieCharts.removeAllViews();
        
        int totalMatches = 0;
        for (MyCardPieChart.Item item : pieChart) {
            MyCardPieChart.Matchup matchup = item.getMatchup();
            if (matchup != null) {
                int firstTotal = calculateTotal(matchup.getFirst());
                int secondTotal = calculateTotal(matchup.getSecond());
                totalMatches += firstTotal + secondTotal;
            }
        }

        if (totalMatches == 0) {
            llPieChartContainer.setVisibility(View.GONE);
            return;
        }

        List<MyCardPieChart.Item> sortedForPie = new java.util.ArrayList<>(pieChart);
        sortedForPie.sort((item1, item2) -> {
            int total1 = getDeckTotalMatches(item1);
            int total2 = getDeckTotalMatches(item2);
            return Integer.compare(total2, total1);
        });

        int colorIndex = 0;
        int[] colors = {
            R.color.holo_green_bright,
            R.color.holo_blue_bright,
            R.color.holo_orange_bright,
            R.color.colorAccentDark,
            R.color.yellow,
            R.color.grayDark2
        };

        for (MyCardPieChart.Item item : sortedForPie) {
            MyCardPieChart.Matchup matchup = item.getMatchup();
            if (matchup == null) continue;

            int firstTotal = calculateTotal(matchup.getFirst());
            int secondTotal = calculateTotal(matchup.getSecond());
            int deckTotal = firstTotal + secondTotal;

            if (deckTotal == 0) continue;

            View pieItemView = LayoutInflater.from(getContext()).inflate(R.layout.item_pie_chart_deck, llPieCharts, false);
            
            CircleProgressView cpv = pieItemView.findViewById(R.id.cpv_deck_percentage);
            TextView tvDeckName = pieItemView.findViewById(R.id.tv_deck_name_short);
            TextView tvDeckMatches = pieItemView.findViewById(R.id.tv_deck_matches);

            float percentage = (float) deckTotal / totalMatches * 100;
            int progress = (int) percentage;
            
            cpv.setMax(100);
            cpv.setProgress(progress);
            cpv.setLabelText(String.format("%.1f%%", percentage));
            
            int colorResId = colors[colorIndex % colors.length];
            cpv.setProgressColor(YGOUtil.c(colorResId));

            tvDeckName.setText(item.getName());
            tvDeckMatches.setText(String.valueOf(deckTotal) + "场");

            llPieCharts.addView(pieItemView);
            colorIndex++;
        }

        tvTotalMatches.setText("总场次: " + totalMatches);
        llPieChartContainer.setVisibility(View.VISIBLE);
    }

    private int getDeckTotalMatches(MyCardPieChart.Item item) {
        MyCardPieChart.Matchup matchup = item.getMatchup();
        if (matchup == null) return 0;
        
        int firstTotal = calculateTotal(matchup.getFirst());
        int secondTotal = calculateTotal(matchup.getSecond());
        return firstTotal + secondTotal;
    }

    private float getDeckWinRate(MyCardPieChart.Item item) {
        MyCardPieChart.Matchup matchup = item.getMatchup();
        if (matchup == null) return 0;
        
        int totalWin = (matchup.getFirst() != null ? parseInt(matchup.getFirst().getWin()) : 0) 
                     + (matchup.getSecond() != null ? parseInt(matchup.getSecond().getWin()) : 0);
        int totalDraw = (matchup.getFirst() != null ? parseInt(matchup.getFirst().getDraw()) : 0) 
                      + (matchup.getSecond() != null ? parseInt(matchup.getSecond().getDraw()) : 0);
        int totalLose = (matchup.getFirst() != null ? parseInt(matchup.getFirst().getLose()) : 0) 
                      + (matchup.getSecond() != null ? parseInt(matchup.getSecond().getLose()) : 0);
        int totalMatches = totalWin + totalDraw + totalLose;
        
        return totalMatches > 0 ? (float) totalWin / totalMatches * 100 : 0;
    }

    private int calculateTotal(MyCardPieChart.First first) {
        if (first == null) return 0;
        return parseInt(first.getWin()) + parseInt(first.getDraw()) + parseInt(first.getLose());
    }

    private int calculateTotal(MyCardPieChart.Second second) {
        if (second == null) return 0;
        return parseInt(second.getWin()) + parseInt(second.getDraw()) + parseInt(second.getLose());
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

    @Override
    public void onFirstUserVisible() {
    }

    @Override
    public void onUserVisible() {
    }

    @Override
    public void onFirstUserInvisible() {
    }

    @Override
    public void onUserInvisible() {
    }

    @Override
    public void onBackHome() {
    }

    @Override
    public boolean onBackPressed() {
        goBack();
        return true;
    }

    private void goBack() {
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().popBackStack();
        }
    }
}
