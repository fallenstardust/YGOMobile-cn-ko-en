package com.ourygo.ygomobile.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.ourygo.ygomobile.OYApplication;
import com.ourygo.ygomobile.base.listener.OnMcUserListener;

import org.litepal.LitePal;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;

/**
 * Create By feihua  On 2021/10/21
 */
public class McUserManagement {
    private static final McUserManagement ourInstance = new McUserManagement();
    private McUser user;
    private List<OnMcUserListener> userListenerList;

    private static final int HANDLE_USER_LOGIN=0;
    private static final int HANDLE_USER_UPDATE=1;
    private static final int HANDLE_USER_LOGOUT=2;

    private McUserManagement() {
        userListenerList = new ArrayList<>();
        user = LitePal.findFirst(McUser.class);
        Log.e("McUserManagement", "初始化  " + (user!=null));
    }

    public static McUserManagement getInstance() {
        return ourInstance;
    }

    public void addListener(OnMcUserListener onMcUserListener) {
        userListenerList.add(onMcUserListener);
    }

    public void removeListener(OnMcUserListener onMcUserListener) {
        userListenerList.remove(onMcUserListener);
    }

    public void login(McUser mUser, boolean isUpdate) {
        if (isUpdate && this.user != null) {
            if (!TextUtils.isEmpty(mUser.getName()))
                this.user.setName(mUser.getName());
            if (mUser.getExternal_id() > 0) {
                Log.e("McUserManagement", "重设" + mUser.getExternal_id());
                this.user.setExternal_id(mUser.getExternal_id());
            }
            if (!TextUtils.isEmpty(mUser.getUsername()))
                this.user.setUsername(mUser.getUsername());
            if (!TextUtils.isEmpty(mUser.getEmail()))
                this.user.setEmail(mUser.getEmail());
            if (!TextUtils.isEmpty(mUser.getAvatar_url()))
                this.user.setAvatar_url(mUser.getAvatar_url());
            boolean isSave=this.user.save();
            isUpdate=true;
        } else {
            this.user = mUser;

            LitePal.deleteAll(McUser.class);
            Log.e("McUserManagement","保存前"+user.getId());
            boolean isSave=this.user.save();
            Log.e("McUserManagement", (LitePal.findFirst(McUser.class)!=null)+"保存情况  " + isSave);
            Log.e("McUserManagement","保存后"+user.getId());
            isUpdate=false;
        }
        HandlerUtil.sendMessage(handler,HANDLE_USER_LOGIN,isUpdate);


    }

    Handler handler=new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case HANDLE_USER_LOGIN:
                    SharedPreferenceUtil.setMyCardUserName(user.getUsername());
                    for (int i = 0; i < userListenerList.size(); i++) {
                        OnMcUserListener ul = userListenerList.get(i);
                        if (ul != null && ul.isListenerEffective()) {
                            if((boolean)msg.obj) {
                                //                    Log.e("McUserManagement","回调更新"+ul.getClass().getName());
                                ul.onUpdate(user);
                            }else {
                                ul.onLogin(user, null);
                            }
                        } else {
                            userListenerList.remove(i);
                            i--;
                        }
                    }
                    break;
                case HANDLE_USER_LOGOUT:
                    for (int i = 0; i < userListenerList.size(); i++) {
                        OnMcUserListener ul = userListenerList.get(i);
                        if (ul != null && ul.isListenerEffective()) {
                            ul.onLogout((String) msg.obj);
                        } else {
                            userListenerList.remove(i);
                            i--;
                        }
                    }
                    break;
                case HANDLE_USER_UPDATE:

                    break;
            }
        }
    };

    public McUser getUser() {
        return user;
    }

    public boolean isLogin() {
        return user != null;
    }

    public void logout(String message) {
        this.user = null;
        LitePal.deleteAll(McUser.class);
        Log.e("McUserManagement", "退出登录");

//        SharedPreferences lastModified = OYApplication.get().getSharedPreferences("lastModified", Context.MODE_PRIVATE);
        ServiceManagement.getDx().disSerVice();
        HandlerUtil.sendMessage(handler,HANDLE_USER_LOGOUT,message);
    }

}
