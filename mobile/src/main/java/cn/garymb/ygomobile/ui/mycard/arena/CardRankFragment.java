package cn.garymb.ygomobile.ui.mycard.arena;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.adapter.CardRankAdapter;
import cn.garymb.ygomobile.ui.mycard.bean.CardTypeAnalytics;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class CardRankFragment extends BaseFragemnt {

    private SwipeRefreshLayout srlRefresh;
    private RecyclerView rvCardList;
    private TextView tvEmpty;
    private ProgressBar pbLoading;
    private Spinner spTimeRange;
    private Spinner spDataSource;
    private Button btnFilterAll;
    private Button btnFilterMonster;
    private Button btnFilterSpell;
    private Button btnFilterTrap;
    private Button btnFilterEx;
    private Button btnFilterSide;
    private CardRankAdapter adapter;

    private String currentTimeRange = "day";
    private String currentDataSource = "mycard-athletic";
    private String currentFilter = "all";
    private List<CardTypeAnalytics.CardItem> allCardList = new ArrayList<>();
    private List<CardTypeAnalytics.CardItem> filteredCardList = new ArrayList<>();

    private static final String[] TIME_RANGE_VALUES = {"day", "week", "halfmonth", "month", "season"};
    private static final String[] TIME_RANGE_LABELS = {YGOUtil.s(R.string.time_today), YGOUtil.s(R.string.time_7day), YGOUtil.s(R.string.time_15day), YGOUtil.s(R.string.time_1month), YGOUtil.s(R.string.current_banlist)};

    private static final String[] DATA_SOURCE_VALUES = {"mycard-athletic", "mycard-entertain", "233-athletic", "233-entertain"};
    private static final String[] DATA_SOURCE_LABELS = {YGOUtil.s(R.string.tag_mycard_athletic), YGOUtil.s(R.string.tag_mycard_entertain), YGOUtil.s(R.string.tag_233_athletic), YGOUtil.s(R.string.tag_233_entertain)};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_card_rank, container, false);
        initView(view);
        setupSpinners();
        setupFilterButtons();
        loadData();
        return view;
    }

    private void initView(View view) {
        srlRefresh = view.findViewById(R.id.srl_refresh);
        rvCardList = view.findViewById(R.id.rv_card_list);
        tvEmpty = view.findViewById(R.id.tv_empty);
        pbLoading = view.findViewById(R.id.pb_loading);

        spTimeRange = view.findViewById(R.id.sp_time_range);
        spDataSource = view.findViewById(R.id.sp_data_source);

        btnFilterAll = view.findViewById(R.id.btn_filter_all);
        btnFilterMonster = view.findViewById(R.id.btn_filter_monster);
        btnFilterSpell = view.findViewById(R.id.btn_filter_spell);
        btnFilterTrap = view.findViewById(R.id.btn_filter_trap);
        btnFilterEx = view.findViewById(R.id.btn_filter_ex);
        btnFilterSide = view.findViewById(R.id.btn_filter_side);

        srlRefresh.setColorSchemeColors(YGOUtil.c(R.color.colorAccent));
        srlRefresh.setOnRefreshListener(() -> loadData());

        rvCardList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CardRankAdapter();
        rvCardList.setAdapter(adapter);
    }

    private void setupSpinners() {
        List<SimpleSpinnerItem> timeRangeItems = new ArrayList<>();
        for (int i = 0; i < TIME_RANGE_VALUES.length; i++) {
            timeRangeItems.add(new SimpleSpinnerItem(i, TIME_RANGE_LABELS[i]));
        }

        SimpleSpinnerAdapter timeRangeAdapter = new SimpleSpinnerAdapter(getContext());
        timeRangeAdapter.setColor(getResources().getColor(R.color.item_title));
        timeRangeAdapter.setTextSize(14f);
        timeRangeAdapter.set(timeRangeItems);
        spTimeRange.setAdapter(timeRangeAdapter);
        spTimeRange.setPopupBackgroundResource(R.color.colorPrimaryDark);
        spTimeRange.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentTimeRange = TIME_RANGE_VALUES[position];
                loadData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        List<SimpleSpinnerItem> dataSourceItems = new ArrayList<>();
        for (int i = 0; i < DATA_SOURCE_VALUES.length; i++) {
            dataSourceItems.add(new SimpleSpinnerItem(i, DATA_SOURCE_LABELS[i]));
        }

        SimpleSpinnerAdapter dataSourceAdapter = new SimpleSpinnerAdapter(getContext());
        dataSourceAdapter.setColor(getResources().getColor(R.color.item_title));
        dataSourceAdapter.setTextSize(14f);
        dataSourceAdapter.set(dataSourceItems);
        spDataSource.setAdapter(dataSourceAdapter);
        spDataSource.setPopupBackgroundResource(R.color.colorPrimaryDark);
        spDataSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentDataSource = DATA_SOURCE_VALUES[position];
                loadData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupFilterButtons() {
        View.OnClickListener filterClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetFilterButtons();

                if (v == btnFilterAll) {
                    currentFilter = "all";
                    btnFilterAll.setSelected(true);
                    btnFilterAll.setBackground(getResources().getDrawable(R.drawable.radius, null));
                } else if (v == btnFilterMonster) {
                    currentFilter = "monster";
                    btnFilterMonster.setSelected(true);
                    btnFilterMonster.setBackground(getResources().getDrawable(R.drawable.radius, null));
                } else if (v == btnFilterSpell) {
                    currentFilter = "spell";
                    btnFilterSpell.setSelected(true);
                    btnFilterSpell.setBackground(getResources().getDrawable(R.drawable.radius, null));
                } else if (v == btnFilterTrap) {
                    currentFilter = "trap";
                    btnFilterTrap.setSelected(true);
                    btnFilterTrap.setBackground(getResources().getDrawable(R.drawable.radius, null));
                } else if (v == btnFilterEx) {
                    currentFilter = "ex";
                    btnFilterEx.setSelected(true);
                    btnFilterEx.setBackground(getResources().getDrawable(R.drawable.radius, null));
                } else if (v == btnFilterSide) {
                    currentFilter = "side";
                    btnFilterSide.setSelected(true);
                    btnFilterSide.setBackground(getResources().getDrawable(R.drawable.radius, null));
                }

                applyFilter();
            }
        };

        btnFilterAll.setOnClickListener(filterClickListener);
        btnFilterMonster.setOnClickListener(filterClickListener);
        btnFilterSpell.setOnClickListener(filterClickListener);
        btnFilterTrap.setOnClickListener(filterClickListener);
        btnFilterEx.setOnClickListener(filterClickListener);
        btnFilterSide.setOnClickListener(filterClickListener);

        btnFilterAll.setSelected(true);
        btnFilterAll.setBackground(getResources().getDrawable(R.drawable.radius, null));
    }

    private void resetFilterButtons() {
        btnFilterAll.setSelected(false);
        btnFilterAll.setBackground(getResources().getDrawable(R.drawable.button_radius_black_transparents, null));

        btnFilterMonster.setSelected(false);
        btnFilterMonster.setBackground(getResources().getDrawable(R.drawable.button_radius_black_transparents, null));

        btnFilterSpell.setSelected(false);
        btnFilterSpell.setBackground(getResources().getDrawable(R.drawable.button_radius_black_transparents, null));

        btnFilterTrap.setSelected(false);
        btnFilterTrap.setBackground(getResources().getDrawable(R.drawable.button_radius_black_transparents, null));

        btnFilterEx.setSelected(false);
        btnFilterEx.setBackground(getResources().getDrawable(R.drawable.button_radius_black_transparents, null));

        btnFilterSide.setSelected(false);
        btnFilterSide.setBackground(getResources().getDrawable(R.drawable.button_radius_black_transparents, null));
    }

    private void applyFilter() {
        filteredCardList.clear();

        if ("all".equals(currentFilter)) {
            filteredCardList.addAll(allCardList);
        } else {
            for (CardTypeAnalytics.CardItem item : allCardList) {
                if (currentFilter.equals(item.getCategory())) {
                    filteredCardList.add(item);
                }
            }
        }

        adapter.setData(filteredCardList);

        if (filteredCardList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("暂无数据");
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void loadData() {
        srlRefresh.setRefreshing(true);
        tvEmpty.setVisibility(View.GONE);
        pbLoading.setVisibility(View.VISIBLE);

        Map<String, Object> params = new HashMap<>();
        params.put("type", currentTimeRange);
        params.put("source", currentDataSource);

        OkhttpUtil.get(MyCard.URL_CARD_TYPE_ANALYTICS, params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        srlRefresh.setRefreshing(false);
                        pbLoading.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("加载失败: " + e.getMessage());
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            srlRefresh.setRefreshing(false);
                            pbLoading.setVisibility(View.GONE);
                            tvEmpty.setVisibility(View.VISIBLE);
                            tvEmpty.setText("请求失败: " + response.code());
                        });
                    }
                    return;
                }

                String json = response.body().string();

                try {
                    CardTypeAnalytics analytics = new Gson().fromJson(json, CardTypeAnalytics.class);

                    List<CardTypeAnalytics.CardItem> mergedList = new ArrayList<>();
                    if (analytics != null) {
                        if (analytics.getMonster() != null) mergedList.addAll(analytics.getMonster());
                        if (analytics.getSpell() != null) mergedList.addAll(analytics.getSpell());
                        if (analytics.getTrap() != null) mergedList.addAll(analytics.getTrap());
                        if (analytics.getEx() != null) mergedList.addAll(analytics.getEx());
                        if (analytics.getSide() != null) mergedList.addAll(analytics.getSide());
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            srlRefresh.setRefreshing(false);
                            pbLoading.setVisibility(View.GONE);

                            allCardList = mergedList;
                            applyFilter();
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            srlRefresh.setRefreshing(false);
                            pbLoading.setVisibility(View.GONE);
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
