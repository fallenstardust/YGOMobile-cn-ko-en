package cn.garymb.ygomobile.ui.mycard.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.gson.Gson;
import com.king.view.circleprogressview.CircleProgressView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.bean.McDuelInfo;
import cn.garymb.ygomobile.ui.mycard.bean.McHistoryResponse;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class UserDuelDetailAdapter {

    private static final String TAG = "UserDuelDetailAdapter";

    private Context context;
    private View contentView;

    private TextView tvUsername;
    private TextView tvPt;
    private TextView tvExp;
    private TextView tvArenaRank;
    private TextView tvExpRank;
    private TextView tvAthleticWin;
    private TextView tvAthleticLose;
    private TextView tvAthleticDraw;
    private TextView tvAthleticAll;
    private CircleProgressView cpvAthleticWlRatio;
    private TextView tvEntertainWin;
    private TextView tvEntertainLose;
    private TextView tvEntertainDraw;
    private TextView tvEntertainAll;

    private LineChart chartPtHistory;
    private Button btnHistory20;
    private Button btnHistory50;
    private Button btnHistory100;
    private ProgressBar pbChartLoading;
    private TextView tvShowAllMatches;
    private TextView tvMatchHistoryTitle;
    private RecyclerView rvMatchHistory;

    private MatchHistoryAdapter matchHistoryAdapter;
    private List<McHistoryResponse.HistoryItem> historyDataList;
    private int currentPageSize = 20;
    private String currentUsername;

    public UserDuelDetailAdapter(@NonNull Context context, @NonNull View contentView) {
        this.context = context;
        this.contentView = contentView;
        initViews();
    }

    private void initViews() {
        tvUsername = contentView.findViewById(R.id.tv_detail_username);
        tvPt = contentView.findViewById(R.id.tv_detail_pt);
        tvExp = contentView.findViewById(R.id.tv_detail_exp);
        tvArenaRank = contentView.findViewById(R.id.tv_detail_arena_rank);
        tvExpRank = contentView.findViewById(R.id.tv_detail_exp_rank);
        tvAthleticWin = contentView.findViewById(R.id.tv_detail_athletic_win);
        tvAthleticLose = contentView.findViewById(R.id.tv_detail_athletic_lose);
        tvAthleticDraw = contentView.findViewById(R.id.tv_detail_athletic_draw);
        tvAthleticAll = contentView.findViewById(R.id.tv_detail_athletic_all);
        cpvAthleticWlRatio = contentView.findViewById(R.id.cpv_detail_athletic_wl_ratio);
        tvEntertainWin = contentView.findViewById(R.id.tv_detail_entertain_win);
        tvEntertainLose = contentView.findViewById(R.id.tv_detail_entertain_lose);
        tvEntertainDraw = contentView.findViewById(R.id.tv_detail_entertain_draw);
        tvEntertainAll = contentView.findViewById(R.id.tv_detail_entertain_all);

        chartPtHistory = contentView.findViewById(R.id.chart_pt_history);
        btnHistory20 = contentView.findViewById(R.id.btn_history_20);
        btnHistory50 = contentView.findViewById(R.id.btn_history_50);
        btnHistory100 = contentView.findViewById(R.id.btn_history_100);
        pbChartLoading = contentView.findViewById(R.id.pb_chart_loading);
        tvShowAllMatches = contentView.findViewById(R.id.tv_show_all_matches);
        tvMatchHistoryTitle = contentView.findViewById(R.id.tv_match_history_title);
        rvMatchHistory = contentView.findViewById(R.id.rv_match_history);

        setupRecyclerView();
        setupButtons();
    }

    private void setupRecyclerView() {
        rvMatchHistory.setLayoutManager(new LinearLayoutManager(context));
        rvMatchHistory.setNestedScrollingEnabled(false);
        matchHistoryAdapter = new MatchHistoryAdapter();
        rvMatchHistory.setAdapter(matchHistoryAdapter);
    }

    private void setupButtons() {
        btnHistory20.setText("20" + YGOUtil.s(R.string.unit_match_count));
        btnHistory50.setText("50" + YGOUtil.s(R.string.unit_match_count));
        btnHistory100.setText("100" + YGOUtil.s(R.string.unit_match_count));

        btnHistory20.setOnClickListener(v -> {
            currentPageSize = 20;
            updatePageSizeButtonsState();
            loadHistoryData(currentUsername, true);
        });

        btnHistory50.setOnClickListener(v -> {
            currentPageSize = 50;
            updatePageSizeButtonsState();
            loadHistoryData(currentUsername, true);
        });

        btnHistory100.setOnClickListener(v -> {
            currentPageSize = 100;
            updatePageSizeButtonsState();
            loadHistoryData(currentUsername, true);
        });

        tvShowAllMatches.setOnClickListener(v -> {
            if (chartPtHistory != null) {
                chartPtHistory.highlightValues(null);
            }
            showMatchHistoryList(historyDataList);
        });
    }

    public void bindDuelInfo(@NonNull McDuelInfo duelInfo, @NonNull String username) {
        currentUsername = username;

        tvUsername.setText(username);
        tvPt.setText(String.valueOf(duelInfo.getDp() != null ? duelInfo.getDp() : 0));
        tvExp.setText(String.valueOf(duelInfo.getExp() != null ? duelInfo.getExp() : 0));
        tvArenaRank.setText(String.valueOf(duelInfo.getMatchRank() != null ? duelInfo.getMatchRank() : 0));
        tvExpRank.setText(String.valueOf(duelInfo.getFunRank() != null ? duelInfo.getFunRank() : 0));

        tvAthleticWin.setText(String.valueOf(duelInfo.getMatchWin() != null ? duelInfo.getMatchWin() : 0));
        tvAthleticLose.setText(String.valueOf(duelInfo.getMatchLose() != null ? duelInfo.getMatchLose() : 0));
        tvAthleticDraw.setText(String.valueOf(duelInfo.getMatchDraw() != null ? duelInfo.getMatchDraw() : 0));
        tvAthleticAll.setText(String.valueOf(duelInfo.getMatchAll() != null ? duelInfo.getMatchAll() : 0));

        float winRatio = duelInfo.getMatchWinRatio();
        cpvAthleticWlRatio.setProgress((int) winRatio);
        cpvAthleticWlRatio.setLabelText(String.format("%.2f%%", winRatio));

        tvEntertainWin.setText(String.valueOf(duelInfo.getFunWin() != null ? duelInfo.getFunWin() : 0));
        tvEntertainLose.setText(String.valueOf(duelInfo.getFunLose() != null ? duelInfo.getFunLose() : 0));
        tvEntertainDraw.setText(String.valueOf(duelInfo.getFunDraw() != null ? duelInfo.getFunDraw() : 0));
        tvEntertainAll.setText(String.valueOf(duelInfo.getFunAll() != null ? duelInfo.getFunAll() : 0));

        currentPageSize = 20;
        updatePageSizeButtonsState();
        loadHistoryData(username, false);
    }

    private void loadHistoryData(String username, boolean isPageSizeChange) {
        currentUsername = username;
        pbChartLoading.setVisibility(View.VISIBLE);
        if (!isPageSizeChange && chartPtHistory != null) {
            chartPtHistory.clear();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("username", username);
        params.put("type", "1");
        params.put("page_num", String.valueOf(currentPageSize));

        OkhttpUtil.get(MyCard.MYCARD_USER_HISTORY_URL, params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取历史数据失败: " + e.getMessage());
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        pbChartLoading.setVisibility(View.GONE);
                        YGOUtil.show(YGOUtil.s(R.string.loading_failed) + ": " + e);
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> pbChartLoading.setVisibility(View.GONE));
                    }
                    return;
                }

                String json = response.body().string();
                Log.d(TAG, "历史数据JSON: " + json);

                try {
                    McHistoryResponse historyResponse = new Gson().fromJson(json, McHistoryResponse.class);
                    List<McHistoryResponse.HistoryItem> items = historyResponse != null ? historyResponse.getData() : null;

                    if (items != null) {
                        Collections.reverse(items);
                    }

                    List<McHistoryResponse.HistoryItem> finalItems = items;
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            pbChartLoading.setVisibility(View.GONE);
                            if (finalItems != null && !finalItems.isEmpty()) {
                                historyDataList = finalItems;
                                setupPtChart(finalItems, username);
                                showMatchHistoryList(finalItems);
                            } else {
                                historyDataList = null;
                                if (chartPtHistory != null) {
                                    chartPtHistory.clear();
                                    chartPtHistory.setNoDataText(YGOUtil.s(R.string.no_duel_history));
                                    chartPtHistory.invalidate();
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析历史数据失败: " + e.getMessage());
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> pbChartLoading.setVisibility(View.GONE));
                    }
                }
            }
        });
    }

    private void setupPtChart(List<McHistoryResponse.HistoryItem> items, String username) {
        if (chartPtHistory == null) return;

        chartPtHistory.getDescription().setEnabled(false);
        chartPtHistory.setNoDataText(YGOUtil.s(R.string.loading));

        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> dateLabels = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            McHistoryResponse.HistoryItem item = items.get(i);
            boolean isUserA = username.equals(item.getUsernamea());

            Double ptAfter = isUserA ? item.getPta() : item.getPtb();
            if (ptAfter == null) continue;

            String dateStr = formatDate(item.getStartTime());
            dateLabels.add(dateStr);
            Entry entry = new Entry(i, ptAfter.floatValue());
            entry.setData(i);
            entries.add(entry);
        }

        if (entries.isEmpty()) {
            chartPtHistory.clear();
            chartPtHistory.setNoDataText(YGOUtil.s(R.string.no_duel_history));
            chartPtHistory.invalidate();
            return;
        }

        XAxis xAxis = chartPtHistory.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularityEnabled(true);
        xAxis.setGranularity(Math.max(1, entries.size() / 8));
        xAxis.setLabelRotationAngle(entries.size() > 30 ? -45f : -30f);
        xAxis.setDrawAxisLine(true);
        xAxis.setTextColor(ContextCompat.getColor(context, R.color.grayLight));
        xAxis.setTextSize(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(dateLabels));

        chartPtHistory.getAxisLeft().setTextColor(ContextCompat.getColor(context, R.color.grayLight));
        chartPtHistory.getAxisLeft().setGridColor(ContextCompat.getColor(context, R.color.klein_blue));
        chartPtHistory.getAxisLeft().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.0f", value);
            }
        });
        chartPtHistory.getAxisRight().setEnabled(false);
        chartPtHistory.getAxisLeft().setDrawAxisLine(true);

        Legend legend = chartPtHistory.getLegend();
        legend.setEnabled(false);

        chartPtHistory.setTouchEnabled(true);
        chartPtHistory.setDragEnabled(true);
        chartPtHistory.setScaleEnabled(true);
        chartPtHistory.setPinchZoom(true);
        chartPtHistory.setHighlightPerDragEnabled(false);

        chartPtHistory.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = (int) e.getData();
                if (historyDataList != null && index >= 0 && index < historyDataList.size()) {
                    filterMatchHistory(index);
                }
            }

            @Override
            public void onNothingSelected() {
                showMatchHistoryList(historyDataList);
            }
        });

        LineDataSet dataSet = new LineDataSet(entries, "PT");
        dataSet.setColor(ContextCompat.getColor(context, R.color.holo_blue_bright));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3.5f);
        dataSet.setCircleColor(ContextCompat.getColor(context, R.color.holo_blue_bright));
        dataSet.setValueTextColor(ContextCompat.getColor(context, R.color.grayLight));
        dataSet.setValueTextSize(5f);
        dataSet.setDrawValues(true);
        dataSet.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.0f", value);
            }
        });
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(ContextCompat.getColor(context, R.color.klein_blue));
        dataSet.setFillAlpha(80);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setHighLightColor(ContextCompat.getColor(context, R.color.holo_orange_bright));
        dataSet.setDrawIcons(false);

        LineData lineData = new LineData(dataSet);
        chartPtHistory.setData(lineData);
        chartPtHistory.animateX(500);
        chartPtHistory.invalidate();
    }

    private String formatDate(String isoDate) {
        if (isoDate == null) return "";
        try {
            String dateTime = isoDate.replace("T", " ").replace(".000Z", "");
            if (dateTime.length() >= 16) {
                return dateTime.substring(5, 16);
            }
            return dateTime;
        } catch (Exception e) {
            return isoDate;
        }
    }

    private void updatePageSizeButtonsState() {
        updateButtonState(btnHistory20, currentPageSize == 20);
        updateButtonState(btnHistory50, currentPageSize == 50);
        updateButtonState(btnHistory100, currentPageSize == 100);
    }

    private void updateButtonState(Button button, boolean isActive) {
        if (isActive) {
            button.setTextColor(ContextCompat.getColor(context, R.color.black));
            button.setBackgroundColor(ContextCompat.getColor(context, R.color.holo_blue_bright));
        } else {
            button.setTextColor(ContextCompat.getColor(context, R.color.grayLight));
            button.setBackgroundColor(ContextCompat.getColor(context, R.color.transparent));
        }
    }

    private void showMatchHistoryList(List<McHistoryResponse.HistoryItem> items) {
        if (items == null || items.isEmpty()) {
            matchHistoryAdapter.setNewData(null);
            tvShowAllMatches.setVisibility(View.GONE);
            tvMatchHistoryTitle.setText(R.string.duel_record);
            return;
        }
        matchHistoryAdapter.setNewData(items);
        tvShowAllMatches.setVisibility(View.GONE);
        tvMatchHistoryTitle.setText(YGOUtil.s(R.string.duel_record) + "(" + items.size() + ")");
    }

    private void filterMatchHistory(int index) {
        if (historyDataList == null || index < 0 || index >= historyDataList.size()) return;
        matchHistoryAdapter.setNewData(Collections.singletonList(historyDataList.get(index)));
        matchHistoryAdapter.setHighlightIndex(0);
        tvShowAllMatches.setVisibility(View.VISIBLE);
        tvMatchHistoryTitle.setText(YGOUtil.s(R.string.duel_record) + "(1/" + historyDataList.size() + ")");
    }

    public void setUsername(@NonNull String username) {
        if (matchHistoryAdapter != null) {
            matchHistoryAdapter.setUsername(username);
        }
    }
}
