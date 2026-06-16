package cn.garymb.ygomobile.ui.mycard.arena;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

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
import cn.garymb.ygomobile.ui.mycard.adapter.UserDuelDetailAdapter;
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
    private ProgressBar pbLoading;
    private EditText etSearchUsername;
    private Switch swByExp;
    private UserDuelRankAdapter adapter;
    private List<UserDuelRank> originalData = new ArrayList<>();
    private boolean isSortByExp = false;
    private boolean isDialogShowing = false;
    private long lastSearchTime = 0;
    private static final long SEARCH_DEBOUNCE_DELAY = 500;

    private UserDuelDetailAdapter duelDetailAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_mycard_duel_rank, container, false);
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
        adapter.setOnItemClickListener(rank -> {
            String username = rank.getUsername();
            if (!TextUtils.isEmpty(username)) {
                searchFromServer(username);
            }
        });
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
            YGOUtil.show(R.string.input_user_name);
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
                        YGOUtil.show(YGOUtil.s(R.string.loading_failed) + ": " + e.getMessage());
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
                            YGOUtil.show(YGOUtil.s(R.string.loading_failed) + ": " + response.code());
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
                                YGOUtil.show(YGOUtil.s(R.string.loading_failed));
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析数据失败: " + e.getMessage(), e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            pbLoading.setVisibility(View.GONE);
                            YGOUtil.show(R.string.loading_failed);
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
        dialog.setTitle(username);
        dialog.setContentView(R.layout.item_mycard_user_duel_detail);

        duelDetailAdapter = new UserDuelDetailAdapter(getContext(), dialog.getContentView());
        duelDetailAdapter.setUsername(username);
        duelDetailAdapter.bindDuelInfo(duelInfo, username);

        dialog.setOnDismissListener(dialogInterface -> {
            isDialogShowing = false;
        });

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
                        tvEmpty.setText(YGOUtil.s(R.string.loading_failed) + ": " + e.getMessage());
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
                            tvEmpty.setText(YGOUtil.s(R.string.loading_failed) + ": " + response.code());
                        });
                    }
                    return;
                }

                String json = response.body().string();
                Log.d(TAG, "玩家排名JSON: " + json);

                try {
                    List<UserDuelRank> rankList;

                    Type listType = new TypeToken<List<UserDuelRank>>() {
                    }.getType();
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
                                tvEmpty.setText(R.string.loading_failed);
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
                            tvEmpty.setText(YGOUtil.s(R.string.loading_failed) + e.getMessage());
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
