package cn.garymb.ygomobile.ui.mycard.base;


import java.util.List;

import cn.garymb.ygomobile.ui.mycard.bean.DuelRoom;

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
