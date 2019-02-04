package com.ourygo.ygomobile.base.listener;

import com.ourygo.ygomobile.bean.MyCardNews;

import java.util.List;

public interface OnMyCardNewsQueryListener {
    void onMyCardNewsQuery(List<MyCardNews> myCardNewsList,String exception);
}
