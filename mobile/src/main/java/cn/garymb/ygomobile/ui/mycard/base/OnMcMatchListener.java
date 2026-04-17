package cn.garymb.ygomobile.ui.mycard.base;


import cn.garymb.ygomobile.ui.mycard.bean.YGOServer;

/**
 * Create By feihua  On 2021/11/8
 */
public interface OnMcMatchListener {
    void onMcMatch(YGOServer ygoServer, String password, String exception);
}
