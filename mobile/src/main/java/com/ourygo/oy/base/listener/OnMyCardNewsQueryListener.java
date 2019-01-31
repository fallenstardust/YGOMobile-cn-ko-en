package com.ourygo.oy.base.listener;

import com.ourygo.oy.bean.MyCardNews;

import java.util.List;

public interface OnMyCardNewsQueryListener {
    void onMyCardNewsQuery(List<MyCardNews> myCardNewsList,String exception);
}
