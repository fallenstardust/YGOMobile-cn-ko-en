package cn.garymb.ygomobile.ui.mycard.base;

import cn.garymb.ygomobile.ui.mycard.bean.McUser;

/**
 * Create By feihua  On 2021/10/26
 */
public interface OnJoinChatListener {
    void onChatLogin(String exception);
    void onChatLoginLoading();
    void onJoinRoomLoading();
    void onChatUserNull();
    void onLoginNoInactiveEmail();
    boolean isListenerEffective();
}
