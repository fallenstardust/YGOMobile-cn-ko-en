package com.ourygo.ygomobile.base.listener;

import com.ourygo.ygomobile.bean.DuelRoom;

import java.util.List;

/**
 * Create By feihua  On 2021/11/4
 */
public interface OnDuelRoomListener {
    void onInit(List<DuelRoom> duelRoomList);
    void onCreate(List<DuelRoom> duelRoomList);
    void onUpdate(List<DuelRoom> duelRoomList);
    void onDelete(List<DuelRoom> duelRoomList);

    boolean isListenerEffective();
}
