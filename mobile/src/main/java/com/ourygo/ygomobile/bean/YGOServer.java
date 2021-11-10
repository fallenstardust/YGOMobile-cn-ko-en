package com.ourygo.ygomobile.bean;

import static cn.garymb.ygomobile.Constants.ARG_DECK;
import static cn.garymb.ygomobile.Constants.QUERY_YGO_TYPE;

import android.net.Uri;
import android.text.TextUtils;

import com.ourygo.assistant.util.Record;
import com.ourygo.assistant.util.UrlUtil;

import net.kk.xml.annotations.XmlElement;

import org.w3c.dom.Text;

import java.net.URL;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.ServerInfo;

@XmlElement("server")
public class YGOServer extends ServerInfo {

    public static final int MODE_ONE=0;
    public static final int MODE_MATCH=1;

    public static final int OPPONENT_TYPE_FRIEND=0;
    public static final int OPPONENT_TYPE_RANDOM=1;
    public static final int OPPONENT_TYPE_AI=2;

    @XmlElement("lflist_name")
    private String lflistName;
    @XmlElement("lflist_code")
    private int lflistCode;
    @XmlElement("mode")
    private int mode;
    @XmlElement("opponent_type")
    private int opponentType;

    private transient String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getOpponentType() {
        return opponentType;
    }

    public void setOpponentType(int opponentType) {
        this.opponentType = opponentType;
    }

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

    public static YGOServer toYGOServer(String serverName){
        YGOServer ygoServer=new YGOServer();
        ygoServer.setServerAddr("s1.ygo233.com");
        ygoServer.setPort(233);
        ygoServer.setPlayerName("武藤游戏");
        ygoServer.setName(serverName);
        ygoServer.setMode(MODE_ONE);
        ygoServer.setOpponentType(OPPONENT_TYPE_FRIEND);
        return ygoServer;
    }

    public String toUri(String password){
        Uri.Builder uri = Uri.parse(Record.HTTP_URL_PREFIX )
                .buildUpon()
                .authority(Record.URI_ROOM_HOST);
        uri.appendQueryParameter(QUERY_YGO_TYPE, Record.ARG_ROOM);
        uri.appendQueryParameter(Constants.QUERY_VERSION, "1");
        uri.appendQueryParameter(Record.ARG_HOST, UrlUtil.enURL(getServerAddr()));
        uri.appendQueryParameter(Record.ARG_PORT, UrlUtil.enURL(getPort()+""));
        uri.appendQueryParameter(Record.ARG_PASSWORD, UrlUtil.enURL(password));
        return uri.toString();
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
