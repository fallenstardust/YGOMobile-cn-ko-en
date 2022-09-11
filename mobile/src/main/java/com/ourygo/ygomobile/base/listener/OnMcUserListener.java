package com.ourygo.ygomobile.base.listener;

import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;

/**
 * Create By feihua  On 2021/10/21
 */
public interface OnMcUserListener {
    void onLogin(McUser user, String exception);
    void onLogout(String message);
    void onUpdate(McUser mcUser);


    //是否有效
    boolean isListenerEffective();
}
