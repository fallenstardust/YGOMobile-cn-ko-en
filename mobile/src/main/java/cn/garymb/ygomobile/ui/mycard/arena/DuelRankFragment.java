package cn.garymb.ygomobile.ui.mycard.arena;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
import com.google.gson.reflect.TypeToken;
import com.king.view.circleprogressview.CircleProgressView;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.adapter.UserDuelRankAdapter;
import cn.garymb.ygomobile.ui.mycard.bean.McDuelInfo;
import cn.garymb.ygomobile.ui.mycard.bean.McHistoryResponse;
import cn.garymb.ygomobile.ui.mycard.bean.UserDuelRank;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class DuelRankFragment extends BaseFragemnt {

    private static final String TAG = "DuelRankFragment";

    private SwipeRefreshLayout srlRefresh;
    private RecyclerView rvDuelList;
    private TextView tvEmpty;
    private ProgressBar pbLoading;
    private EditText etSearchUsername;
    private Switch swByExp;
    private UserDuelRankAdapter adapter;
    private List<UserDuelRank> originalData = new ArrayList<>();
    private boolean isSortByExp = false;
    private boolean isDialogShowing = false;
    private long lastSearchTime = 0;
    private static final long SEARCH_DEBOUNCE_DELAY = 500;

    private LineChart chartPtHistory;
    private Button btnHistory20, btnHistory50, btnHistory100;
    private ProgressBar pbChartLoading;
    private String currentHistoryUsername;
    private int currentPageSize = 20;
    private List<McHistoryResponse.HistoryItem> historyDataList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_duel_rank, container, false);
        initView(view);
        loadData();
        return view;
    }

    private void initView(View view) {
        srlRefresh = view.findViewById(R.id.srl_refresh);
        rvDuelList = view.findViewById(R.id.rv_duel_list);
        tvEmpty = view.findViewById(R.id.tv_empty);
        pbLoading = view.findViewById(R.id.pb_loading);
        etSearchUsername = view.findViewById(R.id.et_search_username);
        swByExp = view.findViewById(R.id.sw_by_exp);

        srlRefresh.setColorSchemeColors(YGOUtil.c(R.color.colorAccent));
        srlRefresh.setOnRefreshListener(() -> loadData());

        rvDuelList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new UserDuelRankAdapter();
        rvDuelList.setAdapter(adapter);

        setupSearchFunction();
        setupSortSwitch();
    }

    private void setupSortSwitch() {
        swByExp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isSortByExp = isChecked;
            loadData();
        });
    }

    private void setupSearchFunction() {
        etSearchUsername.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        etSearchUsername.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String keyword = s.toString().trim();
                if (TextUtils.isEmpty(keyword)) {
                    restoreFullList();
                } else {
                    filterListByKeyword(keyword);
                }
            }
        });
    }

    private void filterListByKeyword(String keyword) {
        List<UserDuelRank> filteredList = new ArrayList<>();
        for (UserDuelRank rank : originalData) {
            if (rank.getUsername() != null && rank.getUsername().toLowerCase().contains(keyword.toLowerCase())) {
                filteredList.add(rank);
            }
        }

        if (!filteredList.isEmpty()) {
            adapter.setNewData(filteredList);
        }
    }

    private void performSearch() {
        String keyword = etSearchUsername.getText().toString().trim();
        
        if (TextUtils.isEmpty(keyword)) {
            Toast.makeText(getContext(), "请输入搜索关键词", Toast.LENGTH_SHORT).show();
            restoreFullList();
            return;
        }

        List<UserDuelRank> filteredList = new ArrayList<>();
        for (UserDuelRank rank : originalData) {
            if (rank.getUsername() != null && rank.getUsername().toLowerCase().contains(keyword.toLowerCase())) {
                filteredList.add(rank);
            }
        }

        if (!filteredList.isEmpty()) {
            adapter.setNewData(filteredList);
        } else {
            searchFromServer(keyword);
        }
    }

    private void searchFromServer(String username) {
        if (isDialogShowing) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSearchTime < SEARCH_DEBOUNCE_DELAY) {
            Log.d(TAG, "搜索请求被防抖拦截，距离上次请求不足0.5秒");
            return;
        }
        lastSearchTime = currentTime;
        
        tvEmpty.setVisibility(View.GONE);
        pbLoading.setVisibility(View.VISIBLE);

        Map<String, Object> params = new HashMap<>();
        params.put("username", username);

        OkhttpUtil.get(MyCard.MYCARD_USER_DUEL_URL, params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "搜索用户失败: " + e.getMessage(), e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "搜索失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "请求失败，状态码: " + response.code());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            pbLoading.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "请求失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        });
                    }
                    return;
                }

                String json = response.body().string();
                Log.d(TAG, "搜索用户JSON: " + json);

                try {
                    McDuelInfo duelInfo = new Gson().fromJson(json, McDuelInfo.class);
                    
                    if (duelInfo != null && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            pbLoading.setVisibility(View.GONE);
                            showDuelInfoInDialog(duelInfo, username);
                        });
                    } else {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                pbLoading.setVisibility(View.GONE);
                                Toast.makeText(getContext(), "未找到该用户", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析数据失败: " + e.getMessage(), e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            pbLoading.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "解析数据失败", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    private void showDuelInfoInDialog(McDuelInfo duelInfo, String username) {
        if (isDialogShowing) {
            return;
        }

        isDialogShowing = true;
        DialogPlus dialog = new DialogPlus(getContext());
        dialog.setTitle("玩家详情");
        dialog.setContentView(R.layout.item_user_duel_detail);
        
        TextView tvUsername = dialog.bind(R.id.tv_detail_username);
        TextView tvPt = dialog.bind(R.id.tv_detail_pt);
        TextView tvExp = dialog.bind(R.id.tv_detail_exp);
        TextView tvArenaRank = dialog.bind(R.id.tv_detail_arena_rank);
        TextView tvExpRank = dialog.bind(R.id.tv_detail_exp_rank);
        TextView tvAthleticWin = dialog.bind(R.id.tv_detail_athletic_win);
        TextView tvAthleticLose = dialog.bind(R.id.tv_detail_athletic_lose);
        TextView tvAthleticDraw = dialog.bind(R.id.tv_detail_athletic_draw);
        TextView tvAthleticAll = dialog.bind(R.id.tv_detail_athletic_all);
        CircleProgressView cpvAthleticWlRatio = dialog.bind(R.id.cpv_detail_athletic_wl_ratio);
        TextView tvEntertainWin = dialog.bind(R.id.tv_detail_entertain_win);
        TextView tvEntertainLose = dialog.bind(R.id.tv_detail_entertain_lose);
        TextView tvEntertainDraw = dialog.bind(R.id.tv_detail_entertain_draw);
        TextView tvEntertainAll = dialog.bind(R.id.tv_detail_entertain_all);

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

        chartPtHistory = dialog.bind(R.id.chart_pt_history);
        btnHistory20 = dialog.bind(R.id.btn_history_20);
        btnHistory50 = dialog.bind(R.id.btn_history_50);
        btnHistory100 = dialog.bind(R.id.btn_history_100);
        pbChartLoading = dialog.bind(R.id.pb_chart_loading);

        currentPageSize = 20;
        updatePageSizeButtonsState();
        setupPageSizeButtons(username);
        loadHistoryData(username, false);

        dialog.setOnDismissListener(dialogInterface -> {
            isDialogShowing = false;
        });
        
        dialog.show();
    }

    private void loadHistoryData(String username, boolean isPageSizeChange) {
        currentHistoryUsername = username;
        pbChartLoading.setVisibility(View.VISIBLE);
        if (!isPageSizeChange) {
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
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        pbChartLoading.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "获取历史数据失败", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> pbChartLoading.setVisibility(View.GONE));
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
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            pbChartLoading.setVisibility(View.GONE);
                            if (finalItems != null && !finalItems.isEmpty()) {
                                historyDataList = finalItems;
                                setupPtChart(finalItems, username);
                            } else {
                                historyDataList = null;
                                chartPtHistory.clear();
                                chartPtHistory.setNoDataText("暂无对局历史");
                                chartPtHistory.invalidate();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析历史数据失败: " + e.getMessage());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> pbChartLoading.setVisibility(View.GONE));
                    }
                }
            }
        });
    }

    private void setupPtChart(List<McHistoryResponse.HistoryItem> items, String username) {
        chartPtHistory.getDescription().setEnabled(false);
        chartPtHistory.setNoDataText("加载中...");

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
            chartPtHistory.setNoDataText("暂无PT数据");
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
        xAxis.setTextColor(ContextCompat.getColor(getContext(), R.color.grayLight));
        xAxis.setValueFormatter(new IndexAxisValueFormatter(dateLabels));

        chartPtHistory.getAxisLeft().setTextColor(ContextCompat.getColor(getContext(), R.color.grayLight));
        chartPtHistory.getAxisLeft().setGridColor(ContextCompat.getColor(getContext(), R.color.klein_blue));
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
                    showMatchDetailDialog(historyDataList.get(index));
                }
            }

            @Override
            public void onNothingSelected() {}
        });

        LineDataSet dataSet = new LineDataSet(entries, "PT");
        dataSet.setColor(ContextCompat.getColor(getContext(), R.color.holo_blue_bright));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3.5f);
        dataSet.setCircleColor(ContextCompat.getColor(getContext(), R.color.holo_blue_bright));
        dataSet.setValueTextColor(ContextCompat.getColor(getContext(), R.color.grayLight));
        dataSet.setValueTextSize(9f);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(ContextCompat.getColor(getContext(), R.color.klein_blue));
        dataSet.setFillAlpha(80);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setHighLightColor(ContextCompat.getColor(getContext(), R.color.holo_orange_bright));
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

    private void setupPageSizeButtons(String username) {
        btnHistory20.setOnClickListener(v -> {
            currentPageSize = 20;
            updatePageSizeButtonsState();
            loadHistoryData(username, true);
        });
        btnHistory50.setOnClickListener(v -> {
            currentPageSize = 50;
            updatePageSizeButtonsState();
            loadHistoryData(username, true);
        });
        btnHistory100.setOnClickListener(v -> {
            currentPageSize = 100;
            updatePageSizeButtonsState();
            loadHistoryData(username, true);
        });
    }

    private void updatePageSizeButtonsState() {
        updateButtonState(btnHistory20, currentPageSize == 20);
        updateButtonState(btnHistory50, currentPageSize == 50);
        updateButtonState(btnHistory100, currentPageSize == 100);
    }

    private void updateButtonState(Button button, boolean isActive) {
        if (isActive) {
            button.setTextColor(ContextCompat.getColor(getContext(), R.color.black));
            button.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.holo_blue_bright));
        } else {
            button.setTextColor(ContextCompat.getColor(getContext(), R.color.grayLight));
            button.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent));
        }
    }

    private String formatPtDelta(Double before, Double after) {
        if (before == null || after == null) return "N/A";
        double delta = after - before;
        if (delta >= 0) {
            return String.format(Locale.getDefault(), "+%.1f", delta);
        } else {
            return String.format(Locale.getDefault(), "%.1f", delta);
        }
    }

    private void showMatchDetailDialog(McHistoryResponse.HistoryItem item) {
        if (getContext() == null) return;

        boolean isUserA = currentHistoryUsername.equals(item.getUsernamea());
        String userPlayer = isUserA ? item.getUsernamea() : item.getUsernameb();
        String opponent = isUserA ? item.getUsernameb() : item.getUsernamea();
        int userScore = isUserA ? (item.getUserscorea() != null ? item.getUserscorea() : 0)
                : (item.getUserscoreb() != null ? item.getUserscoreb() : 0);
        int oppScore = isUserA ? (item.getUserscoreb() != null ? item.getUserscoreb() : 0)
                : (item.getUserscorea() != null ? item.getUserscorea() : 0);
        Double userPtBefore = isUserA ? item.getPta() : item.getPtb();
        Double userPtAfter = isUserA ? item.getPta() : item.getPtb();
        Double oppPtBefore = isUserA ? item.getPtb() : item.getPta();
        Double oppPtAfter = isUserA ? item.getPtb() : item.getPta();

        int dp = 16;
        int density = getContext().getResources().getDisplayMetrics().densityDpi;
        int dpPx = (int) (dp * (density / 160f));

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpPx, dpPx, dpPx, dpPx);

        addDialogRow(layout, "⚔ " + userPlayer + "  vs  " + opponent);
        addDialogDivider(layout);
        addDialogRow(layout, "比分:  " + userScore + " : " + oppScore);
        addDialogRow(layout, "胜者:  " + (item.getWinner() != null ? item.getWinner() : "N/A"));
        addDialogRow(layout, "时间:  " + formatDate(item.getStartTime()));
        addDialogDivider(layout);
        addDialogRow(layout, userPlayer + "  PT: "
                + String.format(Locale.getDefault(), "%.1f", userPtBefore != null ? userPtBefore : 0.0)
                + " → "
                + String.format(Locale.getDefault(), "%.1f", userPtAfter != null ? userPtAfter : 0.0)
                + "  (" + formatPtDelta(userPtBefore, userPtAfter) + ")");
        addDialogRow(layout, opponent + "  PT: "
                + String.format(Locale.getDefault(), "%.1f", oppPtBefore != null ? oppPtBefore : 0.0)
                + " → "
                + String.format(Locale.getDefault(), "%.1f", oppPtAfter != null ? oppPtAfter : 0.0)
                + "  (" + formatPtDelta(oppPtBefore, oppPtAfter) + ")");

        DialogPlus matchDialog = new DialogPlus(getContext());
        matchDialog.setTitle("对局详情");
        matchDialog.setContentView(layout);
        matchDialog.setLeftButtonText("关闭");
        matchDialog.setLeftButtonListener((d, which) -> d.dismiss());
        matchDialog.show();
    }

    private void addDialogRow(LinearLayout parent, String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(ContextCompat.getColor(getContext(), R.color.grayLight));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 8;
        tv.setLayoutParams(params);
        parent.addView(tv);
    }

    private void addDialogDivider(LinearLayout parent) {
        View divider = new View(getContext());
        divider.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.klein_blue));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.topMargin = 16;
        params.bottomMargin = 8;
        divider.setLayoutParams(params);
        parent.addView(divider);
    }

    private void restoreFullList() {
        adapter.setNewData(originalData);
    }

    private void loadData() {
        srlRefresh.setRefreshing(true);
        tvEmpty.setVisibility(View.GONE);

        Map<String, Object> params = new HashMap<>();
        params.put("o", isSortByExp ? "exp" : "pt");

        OkhttpUtil.get(MyCard.MYCARD_USERS_DUEL_URL, params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "加载玩家排名失败: " + e.getMessage(), e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        srlRefresh.setRefreshing(false);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("加载失败: " + e.getMessage());
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "请求失败，状态码: " + response.code() + ", 消息: " + response.message());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            srlRefresh.setRefreshing(false);
                            tvEmpty.setVisibility(View.VISIBLE);
                            tvEmpty.setText("请求失败: " + response.code());
                        });
                    }
                    return;
                }

                String json = response.body().string();
                Log.d(TAG, "玩家排名JSON: " + json);

                try {
                    List<UserDuelRank> rankList;

                    Type listType = new TypeToken<List<UserDuelRank>>() {}.getType();
                    rankList = new Gson().fromJson(json, listType);
                    Log.d(TAG, "直接解析为数组，数据条数: " + (rankList != null ? rankList.size() : "null"));


                    Log.d(TAG, "最终数据条数: " + (rankList != null ? rankList.size() : "null"));
                    if (rankList != null && !rankList.isEmpty()) {
                        Log.d(TAG, "第一条数据: " + rankList.get(0).toString());
                    }

                    if (getActivity() != null) {
                        List<UserDuelRank> finalRankList = rankList;
                        getActivity().runOnUiThread(() -> {
                            srlRefresh.setRefreshing(false);

                            if (finalRankList != null && !finalRankList.isEmpty()) {
                                originalData.clear();
                                originalData.addAll(finalRankList);
                                adapter.setNewData(finalRankList);
                                tvEmpty.setVisibility(View.GONE);
                            } else {
                                tvEmpty.setVisibility(View.VISIBLE);
                                tvEmpty.setText("暂无数据");
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析数据失败: " + e.getMessage(), e);
                    Log.e(TAG, "JSON内容: " + json);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            srlRefresh.setRefreshing(false);
                            tvEmpty.setVisibility(View.VISIBLE);
                            tvEmpty.setText("解析数据失败: " + e.getMessage());
                        });
                    }
                }
            }
        });
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
        return false;
    }
}
