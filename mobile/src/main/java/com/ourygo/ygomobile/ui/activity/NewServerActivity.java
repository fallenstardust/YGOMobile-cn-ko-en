package com.ourygo.ygomobile.ui.activity;

import android.os.Bundle;

import androidx.annotation.Nullable;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;

public class NewServerActivity extends BaseActivity {



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_server_activity);

        initView();

    }

    private void initView() {
        initToolbar("新建游戏设置");
    }
}
