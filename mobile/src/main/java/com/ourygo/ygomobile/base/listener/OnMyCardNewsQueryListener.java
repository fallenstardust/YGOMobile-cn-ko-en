package com.ourygo.ygomobile.base.listener;

import com.ourygo.ygomobile.bean.McNews;

import java.util.List;

public interface OnMyCardNewsQueryListener {
    void onMyCardNewsQuery(List<McNews> mcNewsList, String exception);
}
