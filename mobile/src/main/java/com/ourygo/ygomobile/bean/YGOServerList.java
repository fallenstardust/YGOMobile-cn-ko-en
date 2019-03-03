package com.ourygo.ygomobile.bean;

import net.kk.xml.annotations.XmlElement;

import java.util.List;

import cn.garymb.ygomobile.bean.ServerInfo;

@XmlElement("servers")
public class YGOServerList {
    @XmlElement("version")
    private int vercode = 0;
    @XmlElement("server")
    private List<YGOServer> mServerInfoList;

    public YGOServerList() {

    }


    public int getVercode() {
        return vercode;
    }

    public YGOServerList(int version, List<YGOServer> serverInfoList) {
        mServerInfoList = serverInfoList;
        this.vercode = version;
    }

    public List<YGOServer> getServerInfoList() {
        return mServerInfoList;
    }

    public void setServerInfoList(List<YGOServer> serverInfoList) {
        mServerInfoList = serverInfoList;
    }

    @Override
    public String toString() {
        return "YGOServerList{" +
                "mServerInfoList=" + mServerInfoList +
                '}';
    }
}
