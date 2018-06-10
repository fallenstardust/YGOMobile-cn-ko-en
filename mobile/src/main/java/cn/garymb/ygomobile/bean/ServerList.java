package cn.garymb.ygomobile.bean;

import net.kk.xml.annotations.XmlElement;

import java.util.List;

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
        mServerInfoList = serverInfoList;
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
