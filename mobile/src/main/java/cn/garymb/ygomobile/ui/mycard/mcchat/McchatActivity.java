package cn.garymb.ygomobile.ui.mycard.mcchat;

import android.app.Activity;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ourygo.ygomobile.util.OYUtil;

import org.jivesoftware.smack.packet.Message;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.mycard.mcchat.adapter.ChatAdapter;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.utils.YGOUtil;

public class McchatActivity extends BaseActivity implements ChatListener {

    private EditText main_send_message;
    private ImageButton main_send;
    private RecyclerView main_rec;
//    private TextView main_title;
    private LinearLayout main_bottom_bar;
    private ChatAdapter cadp;
    private ServiceManagement su;

    @Override
    public void reChatLogin(boolean state) {
        main_bottom_bar.setVisibility(View.GONE);
        if (state) {
            setTitle("登录成功");
        } else {
            setTitle("连接断开,重新登录中……");
        }
        // TODO: Implement this method
    }

    @Override
    public void reChatJoin(boolean state) {
        if (state) {
            main_bottom_bar.setVisibility(View.VISIBLE);
            setTitle("Mc聊天室（"+su.getMemberNum()+"）");
        } else {
            main_bottom_bar.setVisibility(View.GONE);
            setTitle("重新加入聊天室中……");
        }
        // TODO: Implement this method
    }

    @Override
    public boolean isListenerEffective() {
        return OYUtil.isContextExisted(this);
    }

    @Override
    public void addChatMessage(ChatMessage message) {
        cadp.sx();
        main_rec.smoothScrollToPosition(su.getData().size() - 1);

        // TODO: Implement this method
    }

    @Override
    public void removeChatMessage(ChatMessage message) {
        // TODO: Implement this method
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        initView();
    }


    private void initView() {
        main_rec = findViewById(R.id.main_rec);
        main_send = findViewById(R.id.main_send);
        main_send_message = findViewById(R.id.main_send_message);
//        main_title = findViewById(R.id.main_title);
        main_bottom_bar = findViewById(R.id.main_bottom_bar);

        su = ServiceManagement.getDx();
        cadp = new ChatAdapter(this, su.getData());

        initToolbar("Mc聊天室（"+su.getMemberNum()+"）");

        su.addListener(this);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(McchatActivity.this, LinearLayoutManager.VERTICAL, false);
        linearLayoutManager.setStackFromEnd(true); //关键 设置此项，当软键盘弹出时，布局会自动顶上去，在结合AndroidManifest.xml设置属性
        main_rec.setLayoutManager(linearLayoutManager);
        main_rec.setAdapter(cadp);
        initListener();


        // TODO: Implement this method
    }

    private void initListener() {
        main_send.setOnClickListener(p1 -> {
            String message = main_send_message.getText().toString().trim();
            if (message.equals("")) {
                YGOUtil.show( getString(R.string.noting_to_send));
            } else {
                try {
                    su.sendMessage(message);
                    main_send_message.setText("");
                } catch (Exception e) {
                    YGOUtil.show( getString(R.string.sending_failed));
                }
            }
            // TODO: Implement this method
        });
        // TODO: Implement this method
    }

    @Override
    protected void onDestroy() {
        // TODO: Implement this method
        super.onDestroy();
//        su.disClass();
//        UserManagement.setUserName(null);
//        UserManagement.setUserPassword(null);
        finish();
    }


}
