package cn.garymb.ygomobile.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.mycard.MycardFragment;
import cn.garymb.ygomobile.ui.mycard.base.OnMcUserListener;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.McUserManagement;

public class PersonalFragment extends BaseFragemnt implements OnMcUserListener {
    private RelativeLayout rl_user;
    private TextView tv_name;
    private ImageView iv_avatar;
    private McUserManagement userManagement;
    private RecyclerView rv_list;

    private MycardFragment fragment_mycard;
    private PersonalFragment fragment_personal;

    //private SettingRecyclerViewAdapter settingAdpter;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View layoutView;
        if (isHorizontal)
            layoutView = inflater.inflate(R.layout.main_horizontal_fragment, container, false);
        else
            layoutView = inflater.inflate(R.layout.fragment_personal, container, false);
        initView(layoutView);
        //event
        return layoutView;
    }

    public void initView(View layoutView) {
        fragment_mycard = new MycardFragment();
        fragment_personal = new PersonalFragment();
        //登录萌卡
        rl_user = layoutView.findViewById(R.id.rl_user);
        tv_name = layoutView.findViewById(R.id.tv_name);
        iv_avatar = layoutView.findViewById(R.id.iv_avatar);
        userManagement = McUserManagement.getInstance();
        rl_user.setOnClickListener(v1 -> {
            if (userManagement.isLogin()) {
                DialogPlus dialog = new DialogPlus(getContext());
                dialog.setMessage(R.string.logout_mycard);
                dialog.setLeftButtonText(R.string.cancel);
                dialog.setLeftButtonListener((dlg, i) -> {
                    dialog.dismiss();
                });
                dialog.setRightButtonText(R.string.quit);
                dialog.setRightButtonListener((dlg, i) -> {
                    userManagement.logout();
                    Toast.makeText(getContext(), R.string.done, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            } else {
               getParentFragmentManager().beginTransaction().hide(fragment_personal).show(fragment_mycard).commit();
            }
        });
        //设置列表
        rv_list = layoutView.findViewById(R.id.rv_list);
        rv_list.setLayoutManager(new LinearLayoutManager(getActivity()));

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
        tv_name.setText(R.string.login_mycard);
        iv_avatar.setImageResource(R.drawable.avatar);
    }

    @Override
    public boolean isListenerEffective() {
        return false;
    }

    /**
     * 第一次fragment可见（进行初始化工作）
     */
    @Override
    public void onFirstUserVisible() {

    }

    /**
     * fragment可见（切换回来或者onResume）
     */
    @Override
    public void onUserVisible() {

    }

    /**
     * 第一次fragment不可见（不建议在此处理事件）
     */
    @Override
    public void onFirstUserInvisible() {

    }

    /**
     * fragment不可见（切换掉或者onPause）
     */
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
