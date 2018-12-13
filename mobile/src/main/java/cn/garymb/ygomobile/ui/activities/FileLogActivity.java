package cn.garymb.ygomobile.ui.activities;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.FileLogUtil;

public class FileLogActivity extends BaseActivity {

    private TextView tv_log;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.file_log_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        tv_log = $(R.id.tv_log);

        setSupportActionBar(toolbar);
        enableBackHome();
        setTitle("本地Log输出");

        read();

        tv_log.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                read();
            }
        });

    }

    private void read() {
        try {
            tv_log.setText(FileLogUtil.read());
        } catch (IOException e) {
            tv_log.setText("读取日志失败，点击重新读取");
        }
    }

}
