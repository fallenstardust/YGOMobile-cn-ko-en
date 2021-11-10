package cn.garymb.ygomobile.ui.mycard.base;

/**
 * Create By feihua  On 2021/10/26
 */
public interface OnJoinChatListener {
    void onChatLogin(String exception);
    void onChatLoginLoading();
    void onJoinRoomLoading();
    void onChatUserNull();
    boolean isListenerEffective();
}
