package com.ourygo.ygomobile.ui.activity;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.feihua.dialogutils.util.DialogUtils;
import com.ourygo.ygomobile.adapter.OYSelectBQAdapter;
import com.ourygo.ygomobile.bean.Lflist;
import com.ourygo.ygomobile.bean.OYSelect;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.StatUtil;
import com.ourygo.ygomobile.util.YGOUtil;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;

public class NewServerActivity extends BaseActivity implements OnItemClickListener, View.OnClickListener {

    private static final int TYPE_QUERY_LFLIST_OK = 0;
    private static final int TYPE_QUERY_LFLIST_EXCEPTION = 1;

    private RecyclerView rv_server, rv_opponent, rv_duel_mode, rv_lflist;
    private ProgressBar pb_lflist_loading;

    private OYSelectBQAdapter serverAdp, opponentAdp, modeAdp, lflistAdp;
    private View server_add_layout;

    private List<OYSelect> lflistNameList;
    private YGOServer currentYGOServer;
    private TextView tv_lflist_exception,tv_mode_s;
    private ImageView iv_del;

    @Override
    protected void onResume() {
        super.onResume();
        StatUtil.onResume(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        StatUtil.onPause(this);
    }

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_QUERY_LFLIST_OK:
                    pb_lflist_loading.setVisibility(View.GONE);
                    tv_lflist_exception.setVisibility(View.GONE);
                    lflistAdp = new OYSelectBQAdapter(lflistNameList);
                    lflistAdp.setSelectPosition(0);
                    lflistAdp.setMessageColor(OYUtil.c(R.color.white));
                    lflistAdp.setMessageSize(20);
                    lflistAdp.setMessageBold(true);
                    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(NewServerActivity.this);
                    linearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                    rv_lflist.setAdapter(lflistAdp);
                    rv_lflist.setLayoutManager(linearLayoutManager);
                    lflistAdp.setOnItemClickListener(NewServerActivity.this);
                    break;
                case TYPE_QUERY_LFLIST_EXCEPTION:
                    pb_lflist_loading.setVisibility(View.GONE);
                    tv_lflist_exception.setVisibility(View.VISIBLE);
                    break;
            }
        }
    };
    private TextView tv_set_ok;
    private LinearLayout ll_host, ll_port, ll_user_name;
    private TextView tv_host, tv_port, tv_user_name;
    private DialogUtils dialogUtils;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_server_activity);

        initView();

    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ll_user_name:
                setUserName();
                break;
            case R.id.ll_host:
                setHost();
                break;
            case R.id.ll_port:
                setPort();
                break;
            case R.id.tv_set_ok:
                List<OYSelect> oySelectList = serverAdp.getData();
                List<YGOServer> ygoServerList = new ArrayList<>();
                for (OYSelect oySelect : oySelectList) {
                    ygoServerList.add((YGOServer) oySelect.getObject());
                }
                YGOUtil.setYGOServer(ygoServerList);
                OYUtil.show("保存成功");
                setResult(RESULT_OK);
                finish();
                break;
            case R.id.iv_del:
                serverAdp.remove(serverAdp.getSelectPosttion());
                serverAdp.setSelectPosition(-1,0);
                break;
        }
    }

    private void setPort() {
        View[] views = dialogUtils.dialoge("设置端口", "请输入端口");
        dialogUtils.getDialog().setOnDismissListener(dialog -> {
            OYUtil.closeKeyboard(dialogUtils.getDialog());
        });
        EditText editText = (EditText) views[0];
        Button bt_ok = (Button) views[1];
        int host = currentYGOServer.getPort();
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (host != 0) {
            editText.setText(host + "");
            editText.setSelection(editText.getText().length());
        }
        bt_ok.setOnClickListener(v -> {
            String message = editText.getText().toString().trim();
            if (TextUtils.isEmpty(message)) {
                OYUtil.show("端口不能为空");
                return;
            }
            int port = 0;
            try {
                port = Integer.parseInt(message);
            } catch (Exception e) {
                OYUtil.show("端口只能为数字");
                return;
            }
            if (port == 0) {
                OYUtil.show("端口不能为0");
                return;
            }
            tv_port.setText(message);
            currentYGOServer.setPort(port);
            OYUtil.closeKeyboard(dialogUtils.getDialog());
            dialogUtils.dis();
        });
    }

    private void setHost() {
        View[] views = dialogUtils.dialoge("设置服务器地址", "请输入服务器ip");
        dialogUtils.getDialog().setOnDismissListener(dialog -> {
            OYUtil.closeKeyboard(dialogUtils.getDialog());
        });
        EditText editText = (EditText) views[0];
        Button bt_ok = (Button) views[1];
        String host = currentYGOServer.getServerAddr();
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        if (!TextUtils.isEmpty(host)){
            editText.setText(host);
            editText.setSelection(editText.getText().length());
        }
        bt_ok.setOnClickListener(v -> {
            String message = editText.getText().toString().trim();
            if (TextUtils.isEmpty(message)) {
                OYUtil.show("服务器地址不能为空");
                return;
            }
            tv_host.setText(message);
            currentYGOServer.setServerAddr(message);
            OYUtil.closeKeyboard(dialogUtils.getDialog());
            dialogUtils.dis();
        });
    }

    private void setUserName() {
        View[] views = dialogUtils.dialoge("设置用户名", "请输入用户名");
        dialogUtils.getDialog().setOnDismissListener(dialog -> {
            OYUtil.closeKeyboard(dialogUtils.getDialog());
        });
        EditText editText = (EditText) views[0];
        Button bt_ok = (Button) views[1];
        String host = currentYGOServer.getPlayerName();
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        if (!TextUtils.isEmpty(host)){
            editText.setText(host);
            editText.setSelection(editText.getText().length());
        }
        bt_ok.setOnClickListener(v -> {
            String message = editText.getText().toString().trim();
            if (TextUtils.isEmpty(message)) {
                OYUtil.show("用户名不能为空");
                return;
            }
            tv_user_name.setText(message);
            currentYGOServer.setPlayerName(message);
            OYUtil.closeKeyboard(dialogUtils.getDialog());
            dialogUtils.dis();
        });
    }

    @Override
    public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
        OYSelectBQAdapter adp = (OYSelectBQAdapter) adapter;
        OYSelect oySelect = adp.getItem(position);
        int lastPosition = adp.getSelectPosttion();
        //选中——选中
//        if (position == lastPosition)

    }

    private void initView() {
        rv_duel_mode = findViewById(R.id.rv_duel_mode);
        rv_lflist = findViewById(R.id.rv_lflist);
        rv_opponent = findViewById(R.id.rv_opponent);
        rv_server = findViewById(R.id.rv_server);
        pb_lflist_loading = findViewById(R.id.pb_lflist_loading);
        tv_lflist_exception = findViewById(R.id.tv_lflist_exception);
        tv_set_ok = findViewById(R.id.tv_set_ok);
        ll_user_name = findViewById(R.id.ll_user_name);
        ll_host = findViewById(R.id.ll_host);
        ll_port = findViewById(R.id.ll_port);
        tv_host = findViewById(R.id.tv_host);
        tv_port = findViewById(R.id.tv_port);
        tv_user_name = findViewById(R.id.tv_user_name);
        iv_del = findViewById(R.id.iv_del);
        tv_mode_s = findViewById(R.id.tv_mode_s);

        dialogUtils = DialogUtils.getInstance(this);

        server_add_layout = LayoutInflater.from(this).inflate(R.layout.server_add_layout, null);
        lflistNameList = new ArrayList<>();
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        rv_server.setLayoutManager(linearLayoutManager);
        initAdapter();
        YGOUtil.getYGOServerList(serverList -> {
            List<OYSelect> oySelectList = new ArrayList<>();

            for (YGOServer serverInfo : serverList.getServerInfoList()) {
                oySelectList.add(OYSelect.tOYSelect(serverInfo.getName(), "", serverInfo));
            }

            serverAdp = new OYSelectBQAdapter(oySelectList);
            //隐藏内容
            serverAdp.hideMessage();
            //设置高宽
            serverAdp.setLayoutSize(OYUtil.dp2px(75), OYUtil.dp2px(86));
            serverAdp.setFooterView(server_add_layout);

            rv_server.setAdapter(serverAdp);

            setAdapterClickListener();
            serverAdp.setOnSelectListener((oySelect, lastPosition, position) -> {
                Log.e("NewSer",lastPosition+" 服务器回调 "+position);
                if (lastPosition != position) {
                    setCurrentServer((YGOServer) oySelect.getObject());
                } else {
                    View[] views = dialogUtils.dialoge("设置服务器名称", "请输入服务器名称");
                    dialogUtils.getDialog().setOnDismissListener(dialog -> {
                        OYUtil.closeKeyboard(dialogUtils.getDialog());
                    });
                    EditText editText = (EditText) views[0];
                    Button bt_ok = (Button) views[1];
                    String name = currentYGOServer.getName();
                    editText.setInputType(InputType.TYPE_CLASS_TEXT);
                    if (!TextUtils.isEmpty(name)){
                        editText.setText(name);
                        editText.setSelection(editText.getText().length());
                    }
                    bt_ok.setOnClickListener(v -> {
                        String message = editText.getText().toString().trim();
                        if (TextUtils.isEmpty(message)) {
                            OYUtil.show("名称不能为空");
                            return;
                        }
                        currentYGOServer.setName(message);
                        oySelect.setName(message);
                        serverAdp.notifyItemChanged(position);
                        OYUtil.closeKeyboard(dialogUtils.getDialog());
                        dialogUtils.dis();
                    });
                }
            });
            serverAdp.setSelectPosition(0);

        });


        tv_set_ok.setOnClickListener(this);
        ll_user_name.setOnClickListener(this);
        ll_host.setOnClickListener(this);
        ll_port.setOnClickListener(this);
        iv_del.setOnClickListener(this);

        server_add_layout.setOnClickListener(v -> {
            View[] views = dialogUtils.dialoge("新建服务器", "请输入服务器名称");
            dialogUtils.getDialog().setOnDismissListener(dialog -> {
                OYUtil.closeKeyboard(dialogUtils.getDialog());
            });
            EditText editText = (EditText) views[0];
            Button bt_ok = (Button) views[1];
            String host = currentYGOServer.getServerAddr();
            editText.setInputType(InputType.TYPE_CLASS_TEXT);
            bt_ok.setOnClickListener(v1 -> {
                String message = editText.getText().toString().trim();
                if (TextUtils.isEmpty(message)) {
                    OYUtil.show("服务器名称不能为空");
                    return;
                }
                YGOServer ygoServer = YGOServer.toYGOServer(message);
                serverAdp.addData(OYSelect.tOYSelect(ygoServer.getName(), "", ygoServer));
                int position = serverAdp.getItemCount() - 1 - serverAdp.getFooterLayoutCount();
                serverAdp.setSelectPosition(position);
//                setCurrentServer(ygoServer);
                dialogUtils.dis();
            });
        });


        initToolbar("新建游戏设置");
        toolbar.setOnClickListener(v -> onBackPressed());
    }

    private void initAdapter() {
        List<OYSelect> oySelectList = new ArrayList<>();
        oySelectList.add(OYSelect.tOYSelect("约战", "新建一个带密码的房间，发送到QQ或微信好友中开始对战", YGOServer.OPPONENT_TYPE_FRIEND));
        oySelectList.add(OYSelect.tOYSelect("随机", "", YGOServer.OPPONENT_TYPE_RANDOM));
        oySelectList.add(OYSelect.tOYSelect("线上AI", "", YGOServer.OPPONENT_TYPE_AI));
        opponentAdp = new OYSelectBQAdapter(oySelectList);
        opponentAdp.setSelectPosition(0);
        opponentAdp.setTitleSize(15);
        opponentAdp.setLayoutGravity(Gravity.LEFT);
        opponentAdp.setLayoutSize(0, OYUtil.dp2px(107));
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(RecyclerView.HORIZONTAL);
        rv_opponent.setLayoutManager(linearLayoutManager);
        rv_opponent.setAdapter(opponentAdp);

        List<OYSelect> oySelectList1 = new ArrayList<>();
        oySelectList1.add(OYSelect.tOYSelect("单局", "", YGOServer.MODE_ONE));
        oySelectList1.add(OYSelect.tOYSelect("比赛", "", YGOServer.MODE_MATCH));
        oySelectList1.add(OYSelect.tOYSelect(OYUtil.s(R.string.duel_mode_tag), "", YGOServer.MODE_TAG));
        modeAdp = new OYSelectBQAdapter(oySelectList1);
        modeAdp.setSelectPosition(0);
        modeAdp.hideMessage();
        modeAdp.setLayoutSize(OYUtil.dp2px(75), 0);
        LinearLayoutManager linearLayoutManager1 = new LinearLayoutManager(this);
        linearLayoutManager1.setOrientation(RecyclerView.HORIZONTAL);
        rv_duel_mode.setLayoutManager(linearLayoutManager1);

        rv_duel_mode.setAdapter(modeAdp);
//        initLflist();

        tv_lflist_exception.setOnClickListener(v -> {
            initLflist();
        });

        opponentAdp.setOnSelectListener((oySelect, lastPosition, position) -> {
            if (lastPosition != position) {
                currentYGOServer.setOpponentType((int) oySelect.getObject());
               if (currentYGOServer.getOpponentType()==YGOServer.OPPONENT_TYPE_AI){
                   tv_mode_s.setVisibility(View.GONE);
                   rv_duel_mode.setVisibility(View.GONE);
               }else {
                   tv_mode_s.setVisibility(View.VISIBLE);
                   rv_duel_mode.setVisibility(View.VISIBLE);
               }
            }
        });

        modeAdp.setOnSelectListener((oySelect, lastPosition, position) -> {
            if (lastPosition != position)
                currentYGOServer.setMode((int) oySelect.getObject());
        });


    }

    private void initLflist() {
        pb_lflist_loading.setVisibility(View.VISIBLE);
        tv_lflist_exception.setVisibility(View.GONE);
        YGOUtil.findLfListListener((lflistNameList, exception) -> {
            Message message = new Message();
            if (TextUtils.isEmpty(exception)) {
                message.what = TYPE_QUERY_LFLIST_OK;
                for (Lflist lflist : lflistNameList) {
                    NewServerActivity.this.lflistNameList.add(OYSelect.tOYSelect(lflist.getTypeName(), lflist.getName(), null));
                }
            } else {
                message.what = TYPE_QUERY_LFLIST_EXCEPTION;
                message.obj = exception;
                Log.e("NewServer", "错误" + exception);
            }
            handler.sendMessage(message);
        });
    }

    private void setCurrentServer(YGOServer ygoServer) {
        currentYGOServer = ygoServer;
        String userName = currentYGOServer.getPlayerName();
        String port = currentYGOServer.getPort() + "";
        String host = currentYGOServer.getServerAddr();

        int num=serverAdp.getData().size();

        if (num>1)
            iv_del.setVisibility(View.VISIBLE);
        else
            iv_del.setVisibility(View.GONE);

        if (TextUtils.isEmpty(userName))
            userName = "点击设置";
        if (TextUtils.isEmpty(port))
            port = "点击设置";
        if (TextUtils.isEmpty(host))
            host = "点击设置";

        tv_user_name.setText(userName);
        tv_host.setText(host);
        tv_port.setText(port);
        switch (ygoServer.getMode()) {
            case YGOServer.MODE_ONE:
                modeAdp.setSelectPosition(0);
                break;
            case YGOServer.MODE_MATCH:
                modeAdp.setSelectPosition(1);
                break;
            case YGOServer.MODE_TAG:
                modeAdp.setSelectPosition(2);
                break;
        }


        switch (ygoServer.getOpponentType()) {
            case YGOServer.OPPONENT_TYPE_FRIEND:
                opponentAdp.setSelectPosition(0);
                break;
            case YGOServer.OPPONENT_TYPE_RANDOM:
                opponentAdp.setSelectPosition(1);
                break;
            case YGOServer.OPPONENT_TYPE_AI:
                opponentAdp.setSelectPosition(2);
                break;
        }
    }

    private void setAdapterClickListener() {
//        serverAdp.setOnItemClickListener(this);
//        modeAdp.setOnItemClickListener(this);
//        opponentAdp.setOnItemClickListener(this);
    }

}
