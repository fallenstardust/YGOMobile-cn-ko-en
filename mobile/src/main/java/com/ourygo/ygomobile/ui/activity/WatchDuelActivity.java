package com.ourygo.ygomobile.ui.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.ourygo.assistant.util.DuelAssistantManagement;
import com.ourygo.ygomobile.adapter.DuelRoomBQAdapter;
import com.ourygo.ygomobile.base.listener.OnDuelRoomListener;
import com.ourygo.ygomobile.bean.DuelRoom;
import com.ourygo.ygomobile.bean.WebSocketEvent;
import com.ourygo.ygomobile.util.McUserManagement;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.Record;
import com.ourygo.ygomobile.util.WatchDuelManagement;
import com.ourygo.ygomobile.util.YGOUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;

/**
 * Create By feihua  On 2021/11/3
 */
public class WatchDuelActivity extends ListAndUpdateActivity implements OnDuelRoomListener {

    private WatchDuelManagement duelManagement;
    private DuelRoomBQAdapter duelRoomBQAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
    }

    private void initView() {
        duelManagement=WatchDuelManagement.getInstance();
        duelManagement.addListener(this);


        duelRoomBQAdapter=new DuelRoomBQAdapter(this,new ArrayList<>());
        rv_list.setAdapter(duelRoomBQAdapter);

        duelRoomBQAdapter.setOnItemClickListener((adapter, view, position) -> {
            DuelRoom duelRoom=duelRoomBQAdapter.getItem(position);
            Log.e("WatchActivity","密码"+duelRoom.getId());
            Log.e("WatchActivity","用户id"+McUserManagement.getInstance().getUser().getExternal_id());
            String password=OYUtil.getWatchDuelPassword(duelRoom.getId(), McUserManagement.getInstance().getUser().getExternal_id());

            ServerInfo serverInfo=new ServerInfo();
            switch (duelRoom.getArenaType()){
                case DuelRoom.TYPE_ARENA_MATCH:
                    serverInfo.setServerAddr(Record.HOST_MC_MATCH);
                    serverInfo.setPort(Record.PORT_MC_MATCH);
                    break;
                case DuelRoom.TYPE_ARENA_FUN:
                case DuelRoom.TYPE_ARENA_AI:
                case DuelRoom.TYPE_ARENA_FUN_MATCH:
                case DuelRoom.TYPE_ARENA_FUN_SINGLE:
                case DuelRoom.TYPE_ARENA_FUN_TAG:
                    serverInfo.setServerAddr(Record.HOST_MC_OTHER);
                    serverInfo.setPort(Record.PORT_MC_OTHER);
                    break;
                default:
                    OYUtil.show("未知房间，请更新软件后进入");
                    return;
            }

            serverInfo.setPlayerName(McUserManagement.getInstance().getUser().getUsername());
            YGOUtil.joinGame(WatchDuelActivity.this,serverInfo,password);
        });

        initToolbar("观战");
        onRefresh();
    }

    @Override
    public void onRefresh() {
        super.onRefresh();
        duelManagement.start();
    }

    @Override
    protected void onDestroy() {
        duelManagement.closeConnect();
        super.onDestroy();
    }

    @Override
    public void onInit(List<DuelRoom> duelRoomList) {
        srl_update.setRefreshing(false);
        srl_update.setEnabled(false);
        duelRoomBQAdapter.addData(duelRoomList);
        initToolbar("观战（"+duelRoomBQAdapter.getData().size()+"）");
    }

    @Override
    public void onCreate(List<DuelRoom> duelRoomList) {
        duelRoomBQAdapter.addData(duelRoomList);
        initToolbar("观战（"+duelRoomBQAdapter.getData().size()+"）");
    }

    @Override
    public void onUpdate(List<DuelRoom> duelRoomList) {

    }


    @Override
    public void onDelete(List<DuelRoom> duelRoomList) {
        duelRoomBQAdapter.remove(duelRoomList);
        initToolbar("观战（"+duelRoomBQAdapter.getData().size()+"）");
    }

    @Override
    public boolean isListenerEffective() {
        return OYUtil.isContextExisted(this);
    }
}
