package com.ourygo.oy.ui.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.feihua.dialogutils.util.DialogUtils;
import com.ourygo.oy.adapter.YGOServerBQAdapter;
import com.ourygo.oy.base.listener.OnYGOServerListQueryListener;
import com.ourygo.oy.util.OYUtil;
import com.ourygo.oy.util.YGOUtil;

import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.ServerList;
import cn.garymb.ygomobile.lite.R;

public class YGOServerFragemnt extends Fragment {

    private RecyclerView rv_service_list;
    private YGOServerBQAdapter ygoServerAdp;
    private DialogUtils du;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v=inflater.inflate(R.layout.ygo_server_fragment,null);

        initView(v);
        initServiceList();

        return v;
    }


    private void initServiceList() {
        YGOUtil.getYGOServerList(new OnYGOServerListQueryListener() {
            @Override
            public void onYGOServerListQuery(ServerList serverList) {
                ygoServerAdp=new YGOServerBQAdapter(serverList.getServerInfoList());
                rv_service_list.setAdapter(ygoServerAdp);
                ygoServerAdp.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
                    @Override
                    public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                        ServerInfo serverInfo= (ServerInfo) adapter.getItem(position);
                        joinRoom(serverInfo);


                    }
                });
            }
        });

    }

    private void joinRoom(ServerInfo serverInfo) {
        View[] v=du.dialoge(null,OYUtil.s(R.string.intput_room_name));
        EditText et= (EditText) v[0];
        Button bt= (Button) v[1];
        et.setHintTextColor(Color.GRAY);
        et.setTextColor(Color.BLACK);
        et.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    du.dis();
                    YGOUtil.joinGame(getActivity(),serverInfo,et.getText().toString().trim());
                    return true;
                }
                return false;
            }
        });
        bt.setText(OYUtil.s(R.string.join_game));
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                du.dis();
                YGOUtil.joinGame(getActivity(),serverInfo,et.getText().toString().trim());
            }
        });
    }


    private void initView(View v) {
        rv_service_list=v.findViewById(R.id.rv_service_list);

        du=DialogUtils.getdx(getActivity());

        rv_service_list.setLayoutManager(new LinearLayoutManager(getActivity()));

    }
}
