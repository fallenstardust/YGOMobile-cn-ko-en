package com.ourygo.ygomobile.ui.activity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.ourygo.ygomobile.adapter.OYSelectBQAdapter;
import com.ourygo.ygomobile.base.listener.OnLfListQueryListener;
import com.ourygo.ygomobile.base.listener.OnYGOServerListQueryListener;
import com.ourygo.ygomobile.bean.Lflist;
import com.ourygo.ygomobile.bean.OYSelect;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.bean.YGOServerList;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.YGOUtil;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.adapters.ServerListAdapter;

public class NewServerActivity extends BaseActivity implements BaseQuickAdapter.OnItemClickListener {

    private static final int TYPE_QUERY_LFLIST_OK = 0;
    private static final int TYPE_QUERY_LFLIST_EXCEPTION = 1;

    private RecyclerView rv_server, rv_opponent, rv_duel_mode, rv_lflist;

    private OYSelectBQAdapter serverAdp, opponentAdp, modeAdp, lflistAdp;
    private View server_add_layout;

    private List<OYSelect> lflistNameList;
    private YGOServer currentYGOServer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_server_activity);

        initView();

    }

    @Override
    public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
        OYSelectBQAdapter adp= (OYSelectBQAdapter) adapter;
        OYSelect oySelect=adp.getItem(position);
        //未选中——选中
        if (position != adp.getSelectPosttion()) {
            adp.setSelectPosition(position);
            adp.notifyDataSetChanged();
            if (adapter.equals(serverAdp)){
                setCurrentServer((YGOServer) oySelect.getObject());
            }
        }

    }

    private void initView() {
        rv_duel_mode = findViewById(R.id.rv_duel_mode);
        rv_lflist = findViewById(R.id.rv_lflist);
        rv_opponent = findViewById(R.id.rv_opponent);
        rv_server = findViewById(R.id.rv_server);
        server_add_layout = LayoutInflater.from(this).inflate(R.layout.server_add_layout, null);
        lflistNameList = new ArrayList<>();
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        rv_server.setLayoutManager(linearLayoutManager);
        initAdapter();
        YGOUtil.getYGOServerList(new OnYGOServerListQueryListener() {
            @Override
            public void onYGOServerListQuery(YGOServerList serverList) {
                List<OYSelect> oySelectList = new ArrayList<>();

                for (YGOServer serverInfo : serverList.getServerInfoList()) {
                    oySelectList.add(OYSelect.tOYSelect(serverInfo.getName(), "", serverInfo));
                }

                serverAdp = new OYSelectBQAdapter(oySelectList);
                //隐藏内容
                serverAdp.hideMessage();
                //设置第一个为选项
                serverAdp.setSelectPosition(0);
                //设置高宽
                serverAdp.setLayoutSize(OYUtil.dp2px(75), OYUtil.dp2px(86));
                serverAdp.setFooterView(server_add_layout);

                rv_server.setAdapter(serverAdp);

                setAdapterClickListener();
                setCurrentServer(serverList.getServerInfoList().get(0));
            }
        });

        initToolbar("新建游戏设置");
    }

    private void initAdapter() {
        List<OYSelect> oySelectList = new ArrayList<>();
        oySelectList.add(OYSelect.tOYSelect("约战", "新建一个带密码的房间，发送到QQ或微信好友中开始对战", null));
        oySelectList.add(OYSelect.tOYSelect("随机", "", null));
        oySelectList.add(OYSelect.tOYSelect("线上AI", "", null));
        opponentAdp = new OYSelectBQAdapter(oySelectList);
        opponentAdp.setSelectPosition(0);
        opponentAdp.setTitleSize(15);
        opponentAdp.setLayoutGravity(Gravity.LEFT);
        opponentAdp.setLayoutSize(0, OYUtil.dp2px(107));
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        rv_opponent.setLayoutManager(linearLayoutManager);
        rv_opponent.setAdapter(opponentAdp);

        List<OYSelect> oySelectList1 = new ArrayList<>();
        oySelectList1.add(OYSelect.tOYSelect("单局", "", null));
        oySelectList1.add(OYSelect.tOYSelect("比赛", "", null));
        modeAdp = new OYSelectBQAdapter(oySelectList1);
        modeAdp.setSelectPosition(0);
        modeAdp.hideMessage();
        modeAdp.setLayoutSize(OYUtil.dp2px(75), 0);
        LinearLayoutManager linearLayoutManager1 = new LinearLayoutManager(this);
        linearLayoutManager1.setOrientation(RecyclerView.HORIZONTAL);
        rv_duel_mode.setLayoutManager(linearLayoutManager1);

        rv_duel_mode.setAdapter(modeAdp);

        YGOUtil.findLfListListener(new OnLfListQueryListener() {
            @Override
            public void onLflistQuery(List<Lflist> lflistNameList, String exception) {
                Message message = new Message();
                if (TextUtils.isEmpty(exception)) {
                    message.what = TYPE_QUERY_LFLIST_OK;
                    for (Lflist lflist : lflistNameList) {
                        NewServerActivity.this.lflistNameList.add(OYSelect.tOYSelect(lflist.getTypeName(), lflist.getName(), null));
                    }
                } else {
                    message.what = TYPE_QUERY_LFLIST_EXCEPTION;
                    message.obj = exception;
                }
                handler.sendMessage(message);
            }
        });
    }

    private void setCurrentServer(YGOServer ygoServer) {
        currentYGOServer = ygoServer;
        switch (ygoServer.getMode()) {
            case YGOServer.MODE_ONE:
                modeAdp.setSelectPosition(0);
                break;
            case YGOServer.MODE_MATCH:

                modeAdp.setSelectPosition(1);
                break;
        }
    }

    private void setAdapterClickListener() {
        serverAdp.setOnItemClickListener(this);
        modeAdp.setOnItemClickListener(this);
        opponentAdp.setOnItemClickListener(this);
    }

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_QUERY_LFLIST_OK:
                    lflistAdp = new OYSelectBQAdapter(lflistNameList);
                    lflistAdp.setSelectPosition(0);
                    lflistAdp.setMessageColor(OYUtil.c(R.color.white));
                    lflistAdp.setMessageSize(20);
                    lflistAdp.setMessageBold(true);
                    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(NewServerActivity.this);
                    linearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                    rv_lflist.setAdapter(lflistAdp);
                    rv_lflist.setLayoutManager(linearLayoutManager);
                    lflistAdp.setOnItemClickListener(NewServerActivity.this);
                    break;
                case TYPE_QUERY_LFLIST_EXCEPTION:

                    break;
            }
        }
    };

}
