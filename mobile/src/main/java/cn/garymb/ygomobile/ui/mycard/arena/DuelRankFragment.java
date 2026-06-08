package cn.garymb.ygomobile.ui.mycard.arena;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.adapter.UserDuelRankAdapter;
import cn.garymb.ygomobile.ui.mycard.bean.McDuelInfo;
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
    private EditText etSearchUsername;
    private Switch swByExp;
    private UserDuelRankAdapter adapter;
    private List<UserDuelRank> originalData = new ArrayList<>();
    private boolean isSortByExp = false;

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
            if (filteredList.size() == 1) {
                showUserDetailInDialog(filteredList.get(0));
            }
        } else {
            searchFromServer(keyword);
        }
    }

    private void performSearch() {
        String keyword = etSearchUsername.getText().toString().trim();
        
        if (TextUtils.isEmpty(keyword)) {
            Toast.makeText(getContext(), "请输入搜索关键词", Toast.LENGTH_SHORT).show();
            restoreFullList();
            return;
        }

        filterListByKeyword(keyword);
    }

    private void searchFromServer(String username) {
        tvEmpty.setVisibility(View.GONE);

        Map<String, Object> params = new HashMap<>();
        params.put("username", username);

        OkhttpUtil.get(MyCard.MYCARD_USER_DUEL_URL, params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "搜索用户失败: " + e.getMessage(), e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
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
                            showDuelInfoInDialog(duelInfo, username);
                        });
                    } else {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "未找到该用户", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析数据失败: " + e.getMessage(), e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "解析数据失败", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    private void showUserDetailInDialog(UserDuelRank rank) {
        DialogPlus dialog = new DialogPlus(getContext());
        dialog.setTitle("玩家详情");
        dialog.setContentView(R.layout.item_user_duel_detail);
        
        TextView tvUsername = dialog.bind(R.id.tv_detail_username);
        TextView tvPt = dialog.bind(R.id.tv_detail_pt);
        TextView tvAthleticWin = dialog.bind(R.id.tv_detail_athletic_win);
        TextView tvAthleticLose = dialog.bind(R.id.tv_detail_athletic_lose);
        TextView tvAthleticDraw = dialog.bind(R.id.tv_detail_athletic_draw);
        TextView tvAthleticAll = dialog.bind(R.id.tv_detail_athletic_all);
        TextView tvEntertainWin = dialog.bind(R.id.tv_detail_entertain_win);
        TextView tvEntertainLose = dialog.bind(R.id.tv_detail_entertain_lose);
        TextView tvEntertainDraw = dialog.bind(R.id.tv_detail_entertain_draw);
        TextView tvEntertainAll = dialog.bind(R.id.tv_detail_entertain_all);

        tvUsername.setText(rank.getUsername());
        tvPt.setText(String.format("%.2f", rank.getPt()));
        tvAthleticWin.setText(String.valueOf(rank.getAthleticWin()));
        tvAthleticLose.setText(String.valueOf(rank.getAthleticLose()));
        tvAthleticDraw.setText(String.valueOf(rank.getAthleticDraw()));
        tvAthleticAll.setText(String.valueOf(rank.getAthleticAll()));
        tvEntertainWin.setText(String.valueOf(rank.getEntertainWin()));
        tvEntertainLose.setText(String.valueOf(rank.getEntertainLose()));
        tvEntertainDraw.setText(String.valueOf(rank.getEntertainDraw()));
        tvEntertainAll.setText(String.valueOf(rank.getEntertainAll()));

        dialog.show();
    }

    private void showDuelInfoInDialog(McDuelInfo duelInfo, String username) {
        DialogPlus dialog = new DialogPlus(getContext());
        dialog.setTitle("玩家详情");
        dialog.setContentView(R.layout.item_user_duel_detail);
        
        TextView tvUsername = dialog.bind(R.id.tv_detail_username);
        TextView tvPt = dialog.bind(R.id.tv_detail_pt);
        TextView tvAthleticWin = dialog.bind(R.id.tv_detail_athletic_win);
        TextView tvAthleticLose = dialog.bind(R.id.tv_detail_athletic_lose);
        TextView tvAthleticDraw = dialog.bind(R.id.tv_detail_athletic_draw);
        TextView tvAthleticAll = dialog.bind(R.id.tv_detail_athletic_all);
        TextView tvEntertainWin = dialog.bind(R.id.tv_detail_entertain_win);
        TextView tvEntertainLose = dialog.bind(R.id.tv_detail_entertain_lose);
        TextView tvEntertainDraw = dialog.bind(R.id.tv_detail_entertain_draw);
        TextView tvEntertainAll = dialog.bind(R.id.tv_detail_entertain_all);

        tvUsername.setText(username);
        tvPt.setText(String.valueOf(duelInfo.getDp() != null ? duelInfo.getDp() : 0));
        tvAthleticWin.setText(String.valueOf(duelInfo.getMatchWin() != null ? duelInfo.getMatchWin() : 0));
        tvAthleticLose.setText(String.valueOf(duelInfo.getMatchLose() != null ? duelInfo.getMatchLose() : 0));
        tvAthleticDraw.setText(String.valueOf(duelInfo.getMatchDraw() != null ? duelInfo.getMatchDraw() : 0));
        tvAthleticAll.setText(String.valueOf(duelInfo.getMatchAll() != null ? duelInfo.getMatchAll() : 0));
        tvEntertainWin.setText(String.valueOf(duelInfo.getFunWin() != null ? duelInfo.getFunWin() : 0));
        tvEntertainLose.setText(String.valueOf(duelInfo.getFunLose() != null ? duelInfo.getFunLose() : 0));
        tvEntertainDraw.setText(String.valueOf(duelInfo.getFunDraw() != null ? duelInfo.getFunDraw() : 0));
        tvEntertainAll.setText(String.valueOf(duelInfo.getFunAll() != null ? duelInfo.getFunAll() : 0));

        dialog.show();
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
