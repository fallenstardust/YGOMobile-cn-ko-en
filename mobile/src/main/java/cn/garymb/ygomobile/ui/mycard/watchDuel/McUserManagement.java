package cn.garymb.ygomobile.ui.mycard.watchDuel;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;

import cn.garymb.ygomobile.ui.mycard.base.OnMcUserListener;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.utils.HandlerUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;

/**
 * Create By feihua  On 2021/10/21
 * Converted from Kotlin to Java
 */
public class McUserManagement {

    private static final String TAG = "McUserManagement";
    private static final int HANDLE_USER_LOGIN = 0;
    private static final int HANDLE_USER_UPDATE = 1;
    private static final int HANDLE_USER_LOGOUT = 2;

    private static volatile McUserManagement instance;

    private McUser user;
    private ArrayList<OnMcUserListener> userListenerList;

    private Handler handler;

    private McUserManagement() {
        userListenerList = new ArrayList<>();
        Log.e(TAG, "初始化  " + (user != null));

        initHandler();
    }

    public static McUserManagement getInstance() {
        if (instance == null) {
            synchronized (McUserManagement.class) {
                if (instance == null) {
                    instance = new McUserManagement();
                }
            }
        }
        return instance;
    }

    private void initHandler() {
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                switch (msg.what) {
                    case HANDLE_USER_LOGIN:
                        SharedPreferenceUtil.setMyCardUserName(user != null ? user.getUsername() : null);

                        boolean isUpdate = (Boolean) msg.obj;
                        int i = 0;
                        while (i < userListenerList.size()) {
                            OnMcUserListener ul = userListenerList.get(i);
                            if (ul.isListenerEffective()) {
                                if (isUpdate) {
                                    ul.onUpdate(user);
                                } else {
                                    ul.onLogin(user, null);
                                }
                            } else {
                                userListenerList.remove(i);
                                i--;
                            }
                            i++;
                        }
                        break;

                    case HANDLE_USER_LOGOUT:
                        String message = (String) msg.obj;
                        i = 0;
                        while (i < userListenerList.size()) {
                            OnMcUserListener ul = userListenerList.get(i);
                            if (ul.isListenerEffective()) {
                                ul.onLogout(message);
                            } else {
                                userListenerList.remove(i);
                                i--;
                            }
                            i++;
                        }
                        break;

                    case HANDLE_USER_UPDATE:
                        break;
                }
            }
        };
    }

    public void addListener(OnMcUserListener onMcUserListener) {
        userListenerList.add(onMcUserListener);
    }

    public void removeListener(OnMcUserListener onMcUserListener) {
        userListenerList.remove(onMcUserListener);
    }

    public void login(McUser mUser, boolean isUpdate) {
        boolean updateFlag = isUpdate;

        if (updateFlag && user != null) {
            if (!TextUtils.isEmpty(mUser.getUsername())) {
                user.setUsername(mUser.getUsername());
            }
            if (mUser.getExternal_id() > 0) {
                Log.e(TAG, "重设" + mUser.getExternal_id());
                user.setExternal_id(mUser.getExternal_id());
            }
            if (!TextUtils.isEmpty(mUser.getUsername())) {
                user.setUsername(mUser.getUsername());
            }
            if (!TextUtils.isEmpty(mUser.getEmail())) {
                user.setEmail(mUser.getEmail());
            }
            if (!TextUtils.isEmpty(mUser.getAvatar_url())) {
                user.setAvatar_url(mUser.getAvatar_url());
            }
            updateFlag = true;
        } else {
            user = mUser;
            Log.e(TAG, "保存前" + user.getId());
            Log.e(TAG, "保存后" + user.getId());
            updateFlag = false;
        }

        HandlerUtil.sendMessage(handler, HANDLE_USER_LOGIN, updateFlag);
    }

    public boolean isLogin() {
        return user != null;
    }

    public void logout(String message) {
        user = null;
        Log.e(TAG, "退出登录");

        ServiceManagement.getDx().disSerVice();
        HandlerUtil.sendMessage(handler, HANDLE_USER_LOGOUT, message);
    }

    public McUser getUser() {
        return user;
    }
}

