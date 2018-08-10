package cn.garymb.ygomobile.ui.mycard.mcchat;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jivesoftware.smack.packet.Message;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.mcchat.adapter.ChatAdapter;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.Util;

public class McchatActivity extends Activity implements ChatListener {

    @Override
    public void reLogin(boolean state) {
        main_bottom_bar.setVisibility(View.GONE);
        if (state) {
            main_title.setText("登录成功");
        } else {
            main_title.setText("连接断开,重新登录中……");
        }
        // TODO: Implement this method
    }

    @Override
    public void reJoin(boolean state) {
        if (state) {
            main_bottom_bar.setVisibility(View.VISIBLE);
            main_title.setText(getResources().getString(R.string.app_name));
        } else {
            main_bottom_bar.setVisibility(View.GONE);
            main_title.setText("重新加入聊天室中……");
        }
        // TODO: Implement this method
    }


    @Override
    public void addMessage(Message message) {
        cadp.sx();
        main_rec.smoothScrollToPosition(su.getData().size() - 1);

        // TODO: Implement this method
    }

    @Override
    public void removeMessage(Message message) {
        // TODO: Implement this method
    }


    private EditText main_send_message;
    private ImageButton main_send;
    private RecyclerView main_rec;
    private TextView main_title;
    private LinearLayout main_bottom_bar;

    private ChatAdapter cadp;
    private ServiceManagement su;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        initView();
    }


    private void initView() {
        main_rec = (RecyclerView) findViewById(R.id.main_rec);
        main_send = (ImageButton) findViewById(R.id.main_send);
        main_send_message = (EditText) findViewById(R.id.main_send_message);
        main_title = (TextView) findViewById(R.id.main_title);
        main_bottom_bar = (LinearLayout) findViewById(R.id.main_bottom_bar);

        su = ServiceManagement.getDx();
        cadp = new ChatAdapter(this, su.getData());
        su.addListener(this);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(McchatActivity.this, LinearLayoutManager.VERTICAL, false);
        linearLayoutManager.setStackFromEnd(true); //关键 设置此项，当软键盘弹出时，布局会自动顶上去，在结合AndroidManifest.xml设置属性
        main_rec.setLayoutManager(linearLayoutManager);
        main_rec.setAdapter(cadp);
        initListener();

        // TODO: Implement this method
    }

    private void initListener() {
        main_send.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View p1) {
                String message = main_send_message.getText().toString().trim();
                if (message.equals("")) {
                    Util.show(McchatActivity.this, getString(R.string.noting_to_send));
                } else {
                    try {
                        su.sendMessage(message);
                        main_send_message.setText("");
                    } catch (Exception e) {
                        Util.show(McchatActivity.this, getString(R.string.sending_failed));
                    }
                }
                // TODO: Implement this method
            }
        });
        // TODO: Implement this method
    }

    @Override
    protected void onDestroy() {
        // TODO: Implement this method
        super.onDestroy();
        su.disClass();
        UserManagement.setUserName(null);
        UserManagement.setUserPassword(null);
        finish();
    }


}
