package cn.garymb.ygomobile.ui.mycard.arena;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;

import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.adapter.DeckMatchupTableAdapter;
import cn.garymb.ygomobile.ui.mycard.bean.DeckMatchupAnalytics;
import cn.garymb.ygomobile.ui.mycard.bean.DeckMatchupStats;
import cn.garymb.ygomobile.utils.YGOUtil;

public class DeckMatchupFragment extends BaseFragemnt {

    private SwipeRefreshLayout srlRefresh;
    private RecyclerView rvMatchupTable;
    private TextView tvEmpty;
    private ProgressBar pbLoading;
    private Spinner spDataSource;
    private LinearLayout llTableHeader;
    private Button btnFirstGo;
    private Button btnSecondGo;
    private Button btnTotal;

    private DeckMatchupTableAdapter adapter;
    private String currentDataSource = "mycard-athletic";
    private List<DeckMatchupStats> allStatsList = new ArrayList<>();
    private List<String> allOpponentDecks = new ArrayList<>();
    private DeckMatchupTableAdapter.DisplayMode currentDisplayMode = DeckMatchupTableAdapter.DisplayMode.FIRST_GO;

    private static final String[] DATA_SOURCE_VALUES = {
            "mycard-athletic",
            "mycard-entertain"
    };
    private static final String[] DATA_SOURCE_LABELS = {
        YGOUtil.s(R.string.tag_mycard_athletic), 
        YGOUtil.s(R.string.tag_mycard_entertain)
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_deck_matchup_table, container, false);
        initView(view);
        setupSpinners();
        setupDisplayModeButtons();
        loadData();
        return view;
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

    private void initView(View view) {
        srlRefresh = view.findViewById(R.id.srl_refresh);
        rvMatchupTable = view.findViewById(R.id.rv_matchup_table);
        tvEmpty = view.findViewById(R.id.tv_empty);
        pbLoading = view.findViewById(R.id.pb_loading);
        spDataSource = view.findViewById(R.id.sp_data_source);
        llTableHeader = view.findViewById(R.id.ll_table_header);
        btnFirstGo = view.findViewById(R.id.btn_first_go);
        btnSecondGo = view.findViewById(R.id.btn_second_go);
        btnTotal = view.findViewById(R.id.btn_total);

        srlRefresh.setColorSchemeColors(YGOUtil.c(R.color.colorAccent));
        srlRefresh.setOnRefreshListener(() -> loadData());

        rvMatchupTable.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setupSpinners() {
        List<SimpleSpinnerItem> dataSourceItems = new ArrayList<>();
        for (int i = 0; i < DATA_SOURCE_VALUES.length; i++) {
            dataSourceItems.add(new SimpleSpinnerItem(i, DATA_SOURCE_LABELS[i]));
        }

        SimpleSpinnerAdapter dataSourceAdapter = new SimpleSpinnerAdapter(getContext());
        dataSourceAdapter.setColor(getResources().getColor(R.color.white));
        dataSourceAdapter.setTextSize(14f);
        dataSourceAdapter.set(dataSourceItems);
        spDataSource.setAdapter(dataSourceAdapter);
        spDataSource.setPopupBackgroundResource(R.color.colorNavy);
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

    private void setupDisplayModeButtons() {
        View.OnClickListener buttonClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetButtonStates();

                if (v == btnFirstGo) {
                    currentDisplayMode = DeckMatchupTableAdapter.DisplayMode.FIRST_GO;
                    btnFirstGo.setSelected(true);
                    btnFirstGo.setBackground(getResources().getDrawable(R.drawable.radius, null));
                } else if (v == btnSecondGo) {
                    currentDisplayMode = DeckMatchupTableAdapter.DisplayMode.SECOND_GO;
                    btnSecondGo.setSelected(true);
                    btnSecondGo.setBackground(getResources().getDrawable(R.drawable.radius, null));
                } else if (v == btnTotal) {
                    currentDisplayMode = DeckMatchupTableAdapter.DisplayMode.TOTAL;
                    btnTotal.setSelected(true);
                    btnTotal.setBackground(getResources().getDrawable(R.drawable.radius, null));
                }

                updateDisplayMode();
            }
        };

        btnFirstGo.setOnClickListener(buttonClickListener);
        btnSecondGo.setOnClickListener(buttonClickListener);
        btnTotal.setOnClickListener(buttonClickListener);

        btnFirstGo.setSelected(true);
        btnFirstGo.setBackground(getResources().getDrawable(R.drawable.radius, null));
    }

    private void resetButtonStates() {
        btnFirstGo.setSelected(false);
        btnFirstGo.setBackground(getResources().getDrawable(R.drawable.button_radius_black_transparents, null));

        btnSecondGo.setSelected(false);
        btnSecondGo.setBackground(getResources().getDrawable(R.drawable.button_radius_black_transparents, null));

        btnTotal.setSelected(false);
        btnTotal.setBackground(getResources().getDrawable(R.drawable.button_radius_black_transparents, null));
    }

    private void updateDisplayMode() {
        if (adapter != null) {
            adapter.setDisplayMode(currentDisplayMode);
        }
        setupTableHeaders();
    }

    private void loadData() {
        srlRefresh.setRefreshing(true);
        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        rvMatchupTable.setVisibility(View.GONE);

        DeckMatchupAnalytics.getDeckMatchupAnalytics(currentDataSource, new DeckMatchupAnalytics.OnDeckMatchupListener() {
            @Override
            public void onDeckMatchupQuery(DeckMatchupAnalytics analytics, String exception) {
                getActivity().runOnUiThread(() -> {
                    srlRefresh.setRefreshing(false);
                    pbLoading.setVisibility(View.GONE);

                    if (exception != null || analytics == null) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText(exception != null ? "加载失败: " + exception : "暂无数据");
                        return;
                    }

                    processAnalyticsData(analytics);
                    
                    if (allStatsList.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("暂无数据");
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        rvMatchupTable.setVisibility(View.VISIBLE);
                        setupTableHeaders();
                        adapter = new DeckMatchupTableAdapter(allStatsList, allOpponentDecks, currentDisplayMode);
                        rvMatchupTable.setAdapter(adapter);
                    }
                });
            }
        });
    }

    private void setupTableHeaders() {
        llTableHeader.removeAllViews();

        TextView deckNameHeader = createHeaderCell("卡组名称");
        deckNameHeader.setLayoutParams(new LinearLayout.LayoutParams(
                YGOUtil.dp2px(120),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        llTableHeader.addView(deckNameHeader);

        for (String opponentDeck : allOpponentDecks) {
            TextView headerCell = createHeaderCell(opponentDeck);
            llTableHeader.addView(headerCell);
        }
    }

    private TextView createHeaderCell(String text) {
        TextView header = (TextView) LayoutInflater.from(getContext())
                .inflate(R.layout.item_table_header_cell, null);
        header.setText(text);
        return header;
    }

    private void processAnalyticsData(DeckMatchupAnalytics analytics) {
        Map<String, DeckMatchupStats> statsMap = new HashMap<>();
        Set<String> allDecksSet = new TreeSet<>();

        for (DeckMatchupAnalytics.Item item : analytics) {
            String deckA = item.getDecka();
            String deckB = item.getDeckb();
            int win = item.getWin();
            int draw = item.getDraw();
            int lose = item.getLose();

            if (deckA != null && !deckA.isEmpty() && deckB != null && !deckB.isEmpty()) {
                allDecksSet.add(deckA);
                allDecksSet.add(deckB);

                DeckMatchupStats statsA = statsMap.computeIfAbsent(deckA, k -> new DeckMatchupStats(deckA));
                statsA.addFirstGoMatchup(deckB, win, draw, lose);

                DeckMatchupStats statsB = statsMap.computeIfAbsent(deckB, k -> new DeckMatchupStats(deckB));
                statsB.addSecondGoMatchup(deckA, win, draw, lose);
            }
        }

        allOpponentDecks.clear();
        allOpponentDecks.addAll(allDecksSet);

        allStatsList.clear();
        allStatsList.addAll(statsMap.values());
        
        allStatsList.sort((a, b) -> Integer.compare(b.getTotalMatches(), a.getTotalMatches()));
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
