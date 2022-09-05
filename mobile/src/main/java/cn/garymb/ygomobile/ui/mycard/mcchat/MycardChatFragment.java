package cn.garymb.ygomobile.ui.mycard.mcchat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourygo.assistant.util.Util;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.mycard.mcchat.adapter.ChatAdapter;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.utils.YGOUtil;

public class MycardChatFragment extends BaseFragemnt implements ChatListener {
    private HomeActivity homeActivity;
    private EditText main_send_message;
    private Button main_send;
    private RecyclerView main_rec;
    private TextView main_title;
    private Button btn_hide;
    private LinearLayout main_bottom_bar;
    private ChatAdapter cadp;
    private ServiceManagement serviceManagement;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        homeActivity = (HomeActivity) getActivity();
        View view;
        view = inflater.inflate(R.layout.fragment_mycard_chating_room, container, false);
        initView(view);
        return view;
    }

    private void initView(View view) {
        serviceManagement = ServiceManagement.getDx();
        cadp = new ChatAdapter(getContext(), serviceManagement.getData());
        serviceManagement.addListener(this);

        main_rec = view.findViewById(R.id.main_rec);
        main_send = view.findViewById(R.id.main_send);
        main_send_message = view.findViewById(R.id.main_send_message);
        main_title = view.findViewById(R.id.main_title);
        main_title.setText(getString(R.string.mc_chat) + "(" + serviceManagement.getMemberNum() + ")");
        btn_hide = view.findViewById(R.id.btn_hide);
        main_bottom_bar = view.findViewById(R.id.main_bottom_bar);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        linearLayoutManager.setStackFromEnd(true); //关键 设置此项，当软键盘弹出时，布局会自动顶上去，在结合AndroidManifest.xml设置属性
        main_rec.setLayoutManager(linearLayoutManager);
        main_rec.setAdapter(cadp);
        initListener();
    }

    @Override
    public void reChatLogin(boolean state) {
        main_bottom_bar.setVisibility(View.GONE);
        if (state) {
            main_title.setText(R.string.logining_in);
        } else {
            main_title.setText(R.string.reChatJoining);
        }
    }

    @Override
    public void reChatJoin(boolean state) {
        if (state) {
            main_bottom_bar.setVisibility(View.VISIBLE);
            main_title.setText(getString(R.string.mc_chat) + "(" + serviceManagement.getMemberNum() + ")");
        } else {
            main_bottom_bar.setVisibility(View.GONE);
            main_title.setText(R.string.reChatJoining);
        }
    }

    @Override
    public boolean isListenerEffective() {
        return Util.isContextExisted(getActivity());
    }

    @Override
    public void addChatMessage(ChatMessage message) {
        boolean isSmooth = YGOUtil.isVisBottom(main_rec) || message.getName().equals(UserManagement.getDx().getMcUser().getUsername());
        cadp.sx();
        //如果在底部新消息来了或者消息是自己发送才滑到最下面，最后一个item有显示才算在底部
        if (isSmooth)
            main_rec.smoothScrollToPosition(serviceManagement.getData().size() - 1);
    }

    @Override
    public void removeChatMessage(ChatMessage message) {
    }

    private void initListener() {
        main_send.setOnClickListener(p1 -> {
            String message = main_send_message.getText().toString().trim();
            if (message.equals("")) {
                YGOUtil.show(getString(R.string.noting_to_send));
            } else {
                try {
                    serviceManagement.sendMessage(message);
                    main_send_message.setText("");
                } catch (Exception e) {
                    YGOUtil.show(getString(R.string.sending_failed));
                }
            }
        });
        btn_hide.setOnClickListener(p1 -> {
            getParentFragmentManager().beginTransaction().hide(homeActivity.fragment_mycard_chatting_room).commit();
            homeActivity.fragment_mycard.mWebViewPlus.setVisibility(View.VISIBLE);
            homeActivity.fragment_mycard.rl_chat.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onDestroy() {
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
    public boolean onBackPressed() {
        return true;
    }
}
