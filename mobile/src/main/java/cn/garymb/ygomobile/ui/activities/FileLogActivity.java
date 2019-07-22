package cn.garymb.ygomobile.ui.activities;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import java.io.IOException;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
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

        tv_log.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                DialogPlus dialogPlus=new DialogPlus(FileLogActivity.this);
                dialogPlus.setMessage("确认清空日志？");
                dialogPlus.setLeftButtonText("清空");
                dialogPlus.setRightButtonText("取消");
                dialogPlus.setLeftButtonListener(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            FileLogUtil.clear();
                            showToast("清空完毕");
                            tv_log.setText("");
                        } catch (IOException e) {
                            showToast("清空失败，原因为"+e.getMessage());
                        }
                        dialog.dismiss();
                    }
                });
                dialogPlus.setRightButtonListener(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                dialogPlus.show();
                return true;
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
