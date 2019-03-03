package com.ourygo.ygomobile.base.listener;

import com.ourygo.ygomobile.bean.Lflist;

import java.util.List;

public interface OnLfListQueryListener {
    void onLflistQuery(List<Lflist> lflistNameList, String exception);
}
