package com.ourygo.ygomobile.bean;

import android.text.TextUtils;

import net.kk.xml.annotations.XmlElement;

import org.w3c.dom.Text;

import cn.garymb.ygomobile.bean.ServerInfo;

@XmlElement("server")
public class YGOServer extends ServerInfo {

    public static final int MODE_ONE=0;
    public static final int MODE_MATCH=1;

    @XmlElement("lflist_name")
    private String lflistName;
    @XmlElement("lflist_code")
    private int lflistCode;
    @XmlElement("mode")
    private int mode;

    public String getLflistName() {
        return lflistName;
    }

    public void setLflistName(String lflistName) {
        this.lflistName = lflistName;
    }

    public void setLflistCode(int lflistCode) {
        this.lflistCode = lflistCode;
    }

    public int getLflistCode() {
        return lflistCode;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public String passwordPrefix(){
        String passwordPrefix="";
        String sLflist = null,sMode = null;
        if (lflistCode>=2){
            sLflist="LF"+lflistCode;
        }
        if (mode==MODE_MATCH)
            sMode="M";

        if (!TextUtils.isEmpty(sMode)){
            passwordPrefix=sMode+"#"+passwordPrefix;
        }

        if (!TextUtils.isEmpty(sLflist)){
            if (TextUtils.isEmpty(passwordPrefix))
                passwordPrefix=sLflist+"#"+passwordPrefix;
            else
                passwordPrefix=sLflist+","+passwordPrefix;
        }

        return passwordPrefix;
    }

}
