package cn.garymb.ygomobile.ui.mycard.mcchat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ourygo.ygomobile.util.OYUtil;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.base.OnJoinChatListener;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.utils.YGOUtil;

public class SplashActivity extends Activity implements OnJoinChatListener {

    public static final int CHAT_LOGIN_EXCEPTION_RE = 0;
    public static final int CHAT_LOGIN_OK = 1;
    public static final int CHAT_LOGIN_EXCEPTION = 2;
    public static final int CHAT_LOGIN_LOADING = 3;
    public static final int CHAT_JOIN_ROOM_LOADING = 4;
    public static final int CHAT_USER_NULL = 5;


    ServiceManagement su;
    ProgressBar sp_jz;
    TextView sp_tv;
    LinearLayout sp_li;


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
        su.addJoinRoomListener(this);
        su.start();

    }


    private void initView() {
        sp_jz = findViewById(R.id.sp_jz);
        sp_tv = findViewById(R.id.sp_tv);
        sp_li = findViewById(R.id.sp_li);

        sp_li.setOnClickListener(p1 -> {
            su.start();
            // TODO: Implement this method
        });

        // TODO: Implement this method
    }

//    @Override
//    public void onLoginExceptionClickRe() {
//        su.setIsConnected(false);
//        sp_jz.setVisibility(View.GONE);
//        sp_tv.setText(getString(R.string.logining_failed));
//        YGOUtil.show(getString(R.string.failed_reason) + msg.obj);
//    }

    @Override
    public void onChatLogin(String exception) {
        Log.e("SplashActivity", "登录情况" + exception);
        sp_jz.setVisibility(View.GONE);
        if (TextUtils.isEmpty(exception)) {
            startActivity(new Intent(SplashActivity.this, McchatActivity.class));
            finish();
        } else {
            su.setIsListener(false);

            sp_tv.setText(getString(R.string.logining_failed));
            YGOUtil.show(getString(R.string.failed_reason) + exception);
        }
    }

    @Override
    public void onChatLoginLoading() {
        Log.e("SplashActivity", "登录中");
        sp_jz.setVisibility(View.VISIBLE);
        sp_tv.setText(getString(R.string.logining_in));
    }

    @Override
    public void onJoinRoomLoading() {
        Log.e("SplashActivity", "加入房间中");
        sp_jz.setVisibility(View.VISIBLE);
        sp_tv.setText(getString(R.string.logining_in));
    }

    @Override
    public void onChatUserNull() {
        Log.e("SplashActivity", "用户为空");
        finish();
    }

    @Override
    public boolean isListenerEffective() {
        return OYUtil.isContextExisted(this);
    }
}
