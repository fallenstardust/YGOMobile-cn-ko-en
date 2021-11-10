package com.ourygo.ygomobile.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.ourygo.ygomobile.adapter.OtherAppBQAdapter;
import com.ourygo.ygomobile.bean.OtherApp;
import com.ourygo.ygomobile.util.IntentUtil;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;

/**
 * Create By feihua  On 2021/11/2
 */
public class OtherFunctionActivity extends ListAndUpdateActivity {

    private OtherAppBQAdapter otherAppAdp;
    private View headView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
    }

    private void initView() {
        headView= LayoutInflater.from(OtherFunctionActivity.this).inflate(R.layout.other_function_app_header,null);

        List<OtherApp> otherAppList=new ArrayList<>();
        OtherApp otherApp;

        otherApp=new OtherApp();
        otherApp.setName("YGOMOBILE");
        otherApp.setMessage("YGOMobile原版软件");
        otherApp.setAppUrl("https://www.pgyer.com/ygomobilecn");
        otherApp.setIcon(R.drawable.ic_icon_round);
        otherAppList.add(otherApp);

        otherApp=new OtherApp();
        otherApp.setName("OURYGO");
        otherApp.setMessage("游戏王交流社区，更新先行卡资源，下载ygo主题等等");
        otherApp.setAppUrl("http://oy.ourygo.top/");
        otherApp.setIcon(R.drawable.ic_oy);
        otherAppList.add(otherApp);

        otherApp=new OtherApp();
        otherApp.setName("OURYGO EZ");
        otherApp.setMessage("ygo卡组管理");
        otherApp.setAppUrl("http://ez.ourygo.top/");
        otherApp.setIcon(R.drawable.ic_ez);
        otherAppList.add(otherApp);

        otherApp=new OtherApp();
        otherApp.setName("OURYGO OC");
        otherApp.setMessage("ygo数据处理工具，可编辑建立数据库、残局、脚本、卡图包等");
        otherApp.setAppUrl("http://oc.ourygo.top/");
        otherApp.setIcon(R.drawable.ic_oc);
        otherAppList.add(otherApp);

        otherApp=new OtherApp();
        otherApp.setName("OURYGO SD");
        otherApp.setMessage("ygo卡图制作");
        otherApp.setAppUrl("http://sd.ourygo.top/");
        otherApp.setIcon(R.drawable.ic_sd);
        otherAppList.add(otherApp);

        otherApp=new OtherApp();
        otherApp.setName("OURYGO DA");
        otherApp.setMessage("ygo辅助工具，卡查、排表、生命值计算等");
        otherApp.setAppUrl("http://da.ourygo.top/");
        otherApp.setIcon(R.drawable.ic_da);
        otherAppList.add(otherApp);

        otherApp=new OtherApp();
        otherApp.setName("OURYGO FS");
        otherApp.setMessage("OURYGO系列软件文件选择器");
        otherApp.setAppUrl("http://fs.ourygo.top/");
        otherApp.setIcon(R.drawable.ic_fs);
        otherAppList.add(otherApp);

        otherAppAdp=new OtherAppBQAdapter(otherAppList);
        otherAppAdp.addHeaderView(headView);
        rv_list.setAdapter(otherAppAdp);

        initToolbar("其他功能");

        otherAppAdp.addChildClickViewIds(R.id.tv_download);
        otherAppAdp.setOnItemChildClickListener((adapter, view, position) -> {
            switch (view.getId()){
                case R.id.tv_download:
                    OtherApp otherApp1=otherAppAdp.getItem(position);
                    startActivity(IntentUtil.getWebIntent(OtherFunctionActivity.this,otherApp1.getAppUrl()));
                    break;
            }
        });

        srl_update.setRefreshing(false);
        srl_update.setEnabled(false);
    }
}
