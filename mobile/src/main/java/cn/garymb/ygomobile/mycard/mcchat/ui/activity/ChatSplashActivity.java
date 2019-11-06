package cn.garymb.ygomobile.mycard.mcchat.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import java.io.IOException;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.mycard.MyCardActivity;
import cn.garymb.ygomobile.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.mycard.mcchat.management.UserManagement;

import cn.garymb.ygomobile.utils.YGOUtil;

public class ChatSplashActivity extends Activity {

    ServiceManagement su;
    ProgressBar sp_jz;
    TextView sp_tv;
    LinearLayout sp_li;
    @SuppressLint("HandlerLeak")
    Handler han = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            // TODO: Implement this method
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    su.setIsConnected(false);
                    sp_jz.setVisibility(View.GONE);
                    sp_tv.setText(getString(R.string.logining_failed));
                    YGOUtil.show(YGOUtil.s(R.string.failed_reason) + msg.obj);
                    break;
                case 1:
                    startActivity(new Intent(ChatSplashActivity.this, McChatActivity.class));
                    finish();
                    break;
                case 2:
                    su.setIsListener(false);
                    sp_jz.setVisibility(View.GONE);
                    sp_tv.setText(getString(R.string.logining_failed));
                    break;
                case 3:
                    sp_jz.setVisibility(View.VISIBLE);
                    sp_tv.setText(getString(R.string.logining_in));
                    break;
                case 4:
                    sp_jz.setVisibility(View.VISIBLE);
                    sp_tv.setText(getString(R.string.logining_in));
                    break;
                case 5:
					/*sp_jz.setVisibility(View.GONE);
					sp_tv.setText("用户名或密码为空");*/
                    startActivity(new Intent(ChatSplashActivity.this, MyCardActivity.class));
                    finish();

                    break;
            }
        }


    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: Implement this method
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        initView();
		/*String name=getIntent().getStringExtra("name");
		String password=getIntent().getStringExtra("password");
		UserManagement.setUserName(name);
		UserManagement.setUserPassword(password);*/
        //UserManagement.setUserName("废话多");
        //UserManagement.setUserPassword("19709");

        SharedPreferences lastModified = getSharedPreferences("lastModified", Context.MODE_PRIVATE);
        UserManagement.setUserName(lastModified.getString("user_name", null));
        UserManagement.setUserPassword(lastModified.getString("user_external_id", null));

        su = ServiceManagement.getDx();
        join();

    }

    private void join() {
        if (!su.isListener()) {
            new Thread(new Runnable() {

                @Override
                public void run() {
                    if (!su.isConnected()) {
                        han.sendEmptyMessage(4);
                        login();
                    }
                    if (su.isConnected()) {
                        han.sendEmptyMessage(3);
                        try {
                            su.joinChat();
                            han.sendEmptyMessage(1);
                        } catch (Exception e) {
                            han.sendEmptyMessage(2);
                        }
                    }
                    // TODO: Implement this method
                }
            }).start();
        } else {
            han.sendEmptyMessage(1);
        }
        // TODO: Implement this method
    }

    private void initView() {
        sp_jz = findViewById(R.id.sp_jz);
        sp_tv = findViewById(R.id.sp_tv);
        sp_li = findViewById(R.id.sp_li);

        sp_li.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View p1) {
                join();
                // TODO: Implement this method
            }
        });

        // TODO: Implement this method
    }

    private void login() {

        String name = UserManagement.getUserName();
        String password = UserManagement.getUserPassword();
        if (name != null && password != null) {
            Message me = new Message();
                me.what = 0;

            try {
                su.login(name, password);
            } catch (InterruptedException e) {
                e.printStackTrace();
                me.obj = "InterruptedException："+e;
                han.sendMessage(me);
            } catch (IOException e) {
                me.obj = "IOException："+e;
                e.printStackTrace();
                han.sendMessage(me);
            } catch (SmackException e) {
                me.obj = "SmackException："+e;
                e.printStackTrace();
                han.sendMessage(me);
            } catch (XMPPException e) {
                me.obj = "XMPPException："+e;
                e.printStackTrace();
                han.sendMessage(me);
            } catch (Exception e){
                me.obj = "其他错误："+e;
                e.printStackTrace();
                han.sendMessage(me);
            }
//            catch (Exception e) {
//                Message me = new Message();
//                me.obj = e;
//                me.what = 0;
//                han.sendMessage(me);
//            }
        } else {
            han.sendEmptyMessage(5);
        }

        // TODO: Implement this method
    }
}
