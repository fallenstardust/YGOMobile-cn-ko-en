package cn.garymb.ygomobile.bean;

import net.kk.xml.annotations.XmlElement;

import java.util.List;

/**
 * 将服务器列表写入xml文件时利用的PO类
 */
@XmlElement("servers")
public class ServerList {
    @XmlElement("version")
    private int vercode = 0;
    @XmlElement("server")
    private List<ServerInfo> mServerInfoList;

    public ServerList() {

    }

    public int getVercode() {
        return vercode;
    }

    public ServerList(int version, List<ServerInfo> serverInfoList) {
        this.mServerInfoList = serverInfoList;
        this.vercode = version;
    }

    public List<ServerInfo> getServerInfoList() {
        return mServerInfoList;
    }

    public void setServerInfoList(List<ServerInfo> serverInfoList) {
        mServerInfoList = serverInfoList;
    }

    @Override
    public String toString() {
        return "ServerList{" +
                "mServerInfoList=" + mServerInfoList +
                '}';
    }
}
