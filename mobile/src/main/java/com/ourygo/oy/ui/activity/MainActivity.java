package com.ourygo.oy.ui.activity;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.ourygo.oy.ui.fragment.YGOServerFragemnt;
import com.ourygo.oy.util.OYUtil;

import cn.garymb.ygomobile.lite.R;

public class MainActivity extends AppCompatActivity {

    private Toolbar toolbar;

    private static final String TAG="TIME-MainActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mian_oy);

        Log.e(TAG,"1");
        initView();
        Log.e(TAG,"2");
    }

    private void initView() {
        toolbar=findViewById(R.id.toolbar);
        OYUtil.initToolbar(this,toolbar,"YGOMobile",false);

        YGOServerFragemnt ygoFragemnt=new YGOServerFragemnt();
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager. beginTransaction();
        transaction.replace(R.id.fragment, ygoFragemnt);
        transaction.commit();
    }


}
