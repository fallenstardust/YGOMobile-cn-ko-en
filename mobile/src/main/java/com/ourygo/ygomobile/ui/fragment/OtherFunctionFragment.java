package com.ourygo.ygomobile.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.feihua.dialogutils.util.DialogUtils;
import com.ourygo.ygomobile.adapter.SettingRecyclerViewAdapter1;
import com.ourygo.ygomobile.base.listener.OnMcUserListener;
import com.ourygo.ygomobile.bean.SettingItem;
import com.ourygo.ygomobile.ui.activity.AboutActivity;
import com.ourygo.ygomobile.ui.activity.DeckManagementActivity;
import com.ourygo.ygomobile.ui.activity.ExpansionsSettingActivity;
import com.ourygo.ygomobile.ui.activity.OYMainActivity;
import com.ourygo.ygomobile.ui.activity.OtherFunctionActivity;
import com.ourygo.ygomobile.ui.activity.UiSettingActivity;
import com.ourygo.ygomobile.util.IntentUtil;
import com.ourygo.ygomobile.util.McUserManagement;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.StatUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;

public class OtherFunctionFragment extends BaseFragemnt implements OnMcUserListener {

    public static final int TYPE_GAME = 0;
    public static final int TYPE_APP = 1;
    public static final int TYPE_ABOUT = 2;

    public static final int ID_EXPANSIONS = 0;
    public static final int ID_WATERFALL = 1;
    public static final int ID_OPENGL = 2;
    public static final int ID_DECK_MANAGEMENT = 3;
    public static final int ID_UI = 4;
    public static final int ID_OTHER_APP = 5;
    public static final int ID_AIFADIAN = 6;
    public static final int ID_ABOUT = 7;

    public static final int REQUEST_EXPANSIONS = 0;

    private RecyclerView rv_list;
    private SettingRecyclerViewAdapter1 settingAdp;

    private View headerView;

    private TextView tv_name;
    private ImageView iv_avatar;
    private McUserManagement userManagement;
    private AppsSettings appsSettings;

    private DialogUtils dialogUtils;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.other_function_fragment, null);

        initView(v);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        StatUtil.onResume(getClass().getName());
    }

    @Override
    public void onPause() {
        super.onPause();
        StatUtil.onPause(getClass().getName());
    }

    private void initView(View v) {
        rv_list = v.findViewById(R.id.rv_list);
        rv_list.setLayoutManager(new LinearLayoutManager(getActivity()));

        headerView = LayoutInflater.from(getActivity()).inflate(R.layout.setting_header, null);
        tv_name = headerView.findViewById(R.id.tv_name);
        iv_avatar = headerView.findViewById(R.id.iv_avatar);
        dialogUtils = DialogUtils.getInstance(getActivity());

        appsSettings = AppsSettings.get();
        userManagement = McUserManagement.getInstance();
        settingAdp = new SettingRecyclerViewAdapter1(getActivity(), new ArrayList<>());
        rv_list.setAdapter(settingAdp);

        headerView.setOnClickListener(v1 -> {
            if (userManagement.isLogin()) {
//                WebActivity.open(getActivity(),"", MyCard.mCommunityUrl);
                View[] views = dialogUtils.dialogt(null, "是否退出登录");
                Button b1, b2;
                b1 = (Button) views[0];
                b2 = (Button) views[1];

                b1.setText("取消");
                b2.setText("退出登录");

                b2.setTextColor(OYUtil.c(R.color.red));

                b1.setOnClickListener(v2 -> {
                    dialogUtils.dis();
                });
                b2.setOnClickListener(v2 -> {
                    userManagement.logout();
                    dialogUtils.dis();
                    OYUtil.snackShow(rv_list,"退出登录成功");
                    ((OYMainActivity)getActivity()).selectMycard();
                });
            } else {
                ((OYMainActivity)getActivity()).selectMycard();
            }
        });
    }


    private void initData() {
        List<SettingItem> settingItemList = new ArrayList<>();
        String stateMessage = "";
        SettingItem settingItem = SettingItem.toSettingItem(ID_EXPANSIONS, "扩展卡包", TYPE_GAME);
        if (appsSettings.isReadExpansions())
            stateMessage = "已启用";
        else
            stateMessage = "未启用";
        settingItem.setMessage(stateMessage);
        settingItem.setIcon(R.drawable.ic_expansions);
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_WATERFALL, "瀑布屏高度", TYPE_GAME);
        stateMessage = OYUtil.getArray(R.array.screen_top_bottom_desc)[SharedPreferenceUtil.getScreenPaddingPosition()];
        settingItem.setMessage(stateMessage);
        settingItem.setIcon(R.drawable.ic_waterfall);
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_OPENGL, "OpenGl", TYPE_GAME);
        stateMessage = OYUtil.getArray(R.array.opengl_version)[SharedPreferenceUtil.getOpenglVersionPosition()];
        settingItem.setMessage(stateMessage);
        settingItem.setIcon(R.drawable.ic_opengl);
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_DECK_MANAGEMENT, "卡组管理", TYPE_APP);
        settingItem.setIcon(R.drawable.ic_deck);
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_UI, "界面设置", TYPE_APP);
        settingItem.setIcon(R.drawable.ic_ui);
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_OTHER_APP, "其他功能", TYPE_APP);
        settingItem.setIcon(R.drawable.ic_other);
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_AIFADIAN, "支持我们", TYPE_ABOUT);
        settingItem.setIcon(R.drawable.ic_aifadian);
        settingItemList.add(settingItem);

        settingItem = SettingItem.toSettingItem(ID_ABOUT, "关于", TYPE_ABOUT);
        settingItem.setIcon(R.drawable.ic_about);
        settingItemList.add(settingItem);

        settingAdp.setNewInstance(settingItemList);

        if (userManagement.isLogin())
            onLogin(userManagement.getUser(), null);
        settingAdp.addHeaderView(headerView);

        userManagement.addListener(this);

        settingAdp.setOnItemClickListener((adapter, view, position) -> {
            switch (settingAdp.getItem(position).getId()) {
                case ID_EXPANSIONS:
                    startActivityForResult(new Intent(getActivity(), ExpansionsSettingActivity.class), REQUEST_EXPANSIONS);
                    break;
                case ID_WATERFALL:
                    setWaterFall();
                    break;
                case ID_OPENGL:
                    setOpenGl();
                    break;
                case ID_UI:
                    startActivity(new Intent(getActivity(), UiSettingActivity.class));
                    break;
                case ID_AIFADIAN:
                    startActivity(IntentUtil.getUrlIntent("https://afdian.net/@ourygo"));
                    break;
                case ID_ABOUT:
                    startActivity(new Intent(getActivity(), AboutActivity.class));
                    break;
                case ID_OTHER_APP:
                    startActivity(new Intent(getActivity(), OtherFunctionActivity.class));
                    break;
                case ID_DECK_MANAGEMENT:
                    startActivity(new Intent(getActivity(), DeckManagementActivity.class));
                    break;
            }
        });
//        settingItemList.add(SettingItem.toSettingItem(0, "扩展卡包", 0, "已安装"));
//        settingItemList.add(SettingItem.toSettingItem(0, "扩展卡包", 0, "已安装"));
    }

    private void setWaterFall() {
        List<String> data = Arrays.asList(OYUtil.getArray(R.array.screen_top_bottom_desc));
        dialogUtils.dialogRadio("瀑布屏高度", data, SharedPreferenceUtil.getScreenPaddingPosition()).setOnRadioListener((data1, position) -> {
            SharedPreferenceUtil.setScreenPadding(position);
            settingAdp.getItem2Id(ID_WATERFALL).setMessage(data1.get(position));
            settingAdp.notifyItemChanged(settingAdp.getItem2IdPosition(ID_WATERFALL));
            dialogUtils.dis();
        });
    }

    private void setOpenGl() {
        List<String> data = Arrays.asList(OYUtil.getArray(R.array.opengl_version));
        dialogUtils.dialogRadio("OpenGl", data, SharedPreferenceUtil.getOpenglVersionPosition()).setOnRadioListener((data1, position) -> {
            SharedPreferenceUtil.setOpenglVersion(position);
            settingAdp.getItem2Id(ID_OPENGL).setMessage(data1.get(position));
            settingAdp.notifyItemChanged(settingAdp.getItem2IdPosition(ID_OPENGL));
            dialogUtils.dis();
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_EXPANSIONS:
                    boolean isEx = appsSettings.isReadExpansions();
                    settingAdp.getItem2Id(ID_EXPANSIONS).setMessage(isEx ? "已启用" : "未启用");
                    settingAdp.notifyItemChanged(settingAdp.getItem2IdPosition(ID_EXPANSIONS));
                    break;
            }
        }
    }

    @Override
    public void onFirstUserVisible() {
        initData();
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
    public void onLogin(McUser user, String exception) {
        if (TextUtils.isEmpty(exception)) {
            tv_name.setText(user.getUsername());
            ImageUtil.setImage(getActivity(), user.getAvatar_url(), iv_avatar);
        }
    }

    @Override
    public void onLogout() {
        tv_name.setText("登录MyCard");
        iv_avatar.setImageResource(R.drawable.avatar);
    }

    @Override
    public boolean isListenerEffective() {
        return OYUtil.isContextExisted(getActivity());
    }
}
