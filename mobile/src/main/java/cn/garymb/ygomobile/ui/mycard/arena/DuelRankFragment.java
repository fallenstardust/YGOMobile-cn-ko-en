package cn.garymb.ygomobile.ui.mycard.arena;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
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
import cn.garymb.ygomobile.ui.mycard.adapter.UserDuelRankAdapter;
import cn.garymb.ygomobile.ui.mycard.bean.UserDuelRank;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class DuelRankFragment extends BaseFragemnt {

    private static final String TAG = "DuelRankFragment";

    private SwipeRefreshLayout srlRefresh;
    private RecyclerView rvDuelList;
    private ProgressBar pbLoading;
    private TextView tvEmpty;
    private UserDuelRankAdapter adapter;

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
        pbLoading = view.findViewById(R.id.pb_loading);
        tvEmpty = view.findViewById(R.id.tv_empty);

        srlRefresh.setColorSchemeColors(YGOUtil.c(R.color.colorAccent));
        srlRefresh.setOnRefreshListener(() -> loadData());

        rvDuelList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new UserDuelRankAdapter();
        rvDuelList.setAdapter(adapter);
    }

    private void loadData() {
        srlRefresh.setRefreshing(true);
        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        Map<String, Object> params = new HashMap<>();
        params.put("o", "pt");

        OkhttpUtil.get(MyCard.MYCARD_USERS_DUEL_URL, params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "加载玩家排名失败: " + e.getMessage(), e);
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
                    Log.e(TAG, "请求失败，状态码: " + response.code() + ", 消息: " + response.message());
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
                Log.d(TAG, "玩家排名JSON: " + json);

                try {
                    List<UserDuelRank> rankList;

                    // 直接是数组
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
                            pbLoading.setVisibility(View.GONE);

                            if (finalRankList != null && !finalRankList.isEmpty()) {
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
