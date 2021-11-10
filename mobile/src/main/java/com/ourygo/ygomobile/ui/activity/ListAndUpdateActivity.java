package com.ourygo.ygomobile.ui.activity;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ourygo.ygomobile.util.OYUtil;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;

public class ListAndUpdateActivity extends BaseActivity implements SwipeRefreshLayout.OnRefreshListener, View.OnClickListener {

    protected SwipeRefreshLayout srl_update;
    protected RecyclerView rv_list;
    protected FloatingActionButton fab_add;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_and_update_activity);

        init();
    }

    private void init() {
        srl_update=findViewById(R.id.srl_update);
        rv_list=findViewById(R.id.rv_list);
        fab_add=findViewById(R.id.fab_add);

        rv_list.setLayoutManager(new LinearLayoutManager(this));

        srl_update.setColorSchemeColors(OYUtil.c(R.color.colorAccent));
        srl_update.setOnRefreshListener(this);
        srl_update.setRefreshing(true);

        fab_add.setOnClickListener(this);
    }

    @Override
    public void onRefresh() {
        
    }

    @Override
    public void onClick(View v) {

    }

}
