package com.ourygo.ygomobile.ui.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.feihua.dialogutils.util.DialogUtils;
import com.ourygo.ygomobile.adapter.YGOServerBQAdapter;
import com.ourygo.ygomobile.base.listener.OnYGOServerListQueryListener;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.bean.YGOServerList;
import com.ourygo.ygomobile.util.OYDialogUtil;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.StatUtil;
import com.ourygo.ygomobile.util.YGOUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
        View v = inflater.inflate(R.layout.ygo_server_fragment, null);

        initView(v);
        initServiceList();

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        StatUtil.onResume(getClass().getName());
    }

    @Override
    public void onPause() {
        super.onPause();
        StatUtil.onPause(getClass().getName());
    }

    private void initServiceList() {
        YGOUtil.getYGOServerList(serverList -> {
            ygoServerAdp = new YGOServerBQAdapter(serverList.getServerInfoList());
            rv_service_list.setAdapter(ygoServerAdp);

            ygoServerAdp.addChildClickViewIds(R.id.tv_create_and_share);
            ygoServerAdp.setOnItemChildClickListener((adapter, view, position) -> {
                switch (view.getId()) {
                    case R.id.tv_create_and_share:
                        joinRoom((YGOServer) adapter.getItem(position),true);
                        break;
                }
            });
            ygoServerAdp.setOnItemClickListener((adapter, view, position) -> {
                YGOServer serverInfo = (YGOServer) adapter.getItem(position);
                joinRoom(serverInfo,false);
            });
        });
    }

    private void joinRoom(YGOServer serverInfo, boolean isShare) {
        if (isShare) {
            OYDialogUtil.dialogcreateRoom(getActivity(), serverInfo);
        } else {
            View[] v = du.dialoge(null, OYUtil.s(R.string.intput_room_name));
            EditText et = (EditText) v[0];
            Button bt = (Button) v[1];
            et.setHintTextColor(Color.GRAY);
            et.setTextColor(Color.BLACK);
            et.setOnKeyListener((v1, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    du.dis();
                    YGOUtil.joinGame(getActivity(), serverInfo, et.getText().toString().trim());
                    return true;
                }
                return false;
            });
            bt.setText(OYUtil.s(R.string.join_game));
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    du.dis();
                    YGOUtil.joinGame(getActivity(), serverInfo, et.getText().toString().trim());
                }
            });
        }
    }


    private void initView(View v) {
        rv_service_list = v.findViewById(R.id.rv_service_list);

        du = DialogUtils.getInstance(getActivity());
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        linearLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        rv_service_list.setLayoutManager(linearLayoutManager);

    }
}
