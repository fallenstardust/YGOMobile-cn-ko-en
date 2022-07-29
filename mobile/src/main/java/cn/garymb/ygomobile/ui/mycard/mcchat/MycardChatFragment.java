package cn.garymb.ygomobile.ui.mycard.mcchat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourygo.assistant.util.Util;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.mcchat.adapter.ChatAdapter;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.utils.YGOUtil;

public class MycardChatFragment extends BaseFragemnt implements ChatListener {

    private EditText main_send_message;
    private ImageButton main_send;
    private RecyclerView main_rec;
    private TextView main_title;
    private LinearLayout main_bottom_bar;
    private ChatAdapter cadp;
    private ServiceManagement su;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view;
        view = inflater.inflate(R.layout.fragment_mycard_chating_room, container, false);
        initView(view);
        return view;
    }

    @Override
    public void reChatLogin(boolean state) {
        main_bottom_bar.setVisibility(View.GONE);
        if (state) {
            main_title.setText(R.string.logining_in);
        } else {
            main_title.setText("连接断开,重新登录中……");
        }
        // TODO: Implement this method
    }

    @Override
    public void reChatJoin(boolean state) {
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
    public boolean isListenerEffective() {
        return Util.isContextExisted(getActivity());
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


    private void initView(View view) {
        main_rec = view.findViewById(R.id.main_rec);
        main_send = view.findViewById(R.id.main_send);
        main_send_message = view.findViewById(R.id.main_send_message);
        main_title = view.findViewById(R.id.main_title);
        main_bottom_bar = view.findViewById(R.id.main_bottom_bar);

        su = ServiceManagement.getDx();
        cadp = new ChatAdapter(getContext(), su.getData());
        su.addListener(this);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
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
    public void onDestroy() {
        // TODO: Implement this method
        super.onDestroy();
//        su.disClass();
//        UserManagement.setUserName(null);
//        UserManagement.setUserPassword(null);
    }


    @Override
    public void onFirstUserVisible() {

    }

    @Override
    public void onUserVisible() {

    }

    @Override
    public void onFirstUserInvisible() {

    }

    @Override
    public void onUserInvisible() {

    }

    @Override
    public void onBackHome() {

    }

    @Override
    public void onBackPressed() {

    }
}
