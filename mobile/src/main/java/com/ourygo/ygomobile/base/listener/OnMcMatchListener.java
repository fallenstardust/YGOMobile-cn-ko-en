package com.ourygo.ygomobile.base.listener;

import com.ourygo.ygomobile.bean.YGOServer;

/**
 * Create By feihua  On 2021/11/8
 */
public interface OnMcMatchListener {
    void onMcMatch(YGOServer ygoServer,String password,String exception);
}
