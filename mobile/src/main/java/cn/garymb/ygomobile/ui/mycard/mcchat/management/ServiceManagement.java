package cn.garymb.ygomobile.ui.mycard.mcchat.management;

import android.os.Handler;
import android.util.Log;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatException;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.ui.mycard.mcchat.ChatListener;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatMessage;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.TaxiConnectionListener;

public class ServiceManagement {
    public static final String GROUP_ADDRESS = "ygopro_china_north@conference.mycard.moe";

    private static ServiceManagement su = new ServiceManagement();
    private XMPPTCPConnection con;
    private MultiUserChat muc;
    private boolean isConnected = false;
    private boolean isListener = false;
    private List<ChatMessage> data = new ArrayList<ChatMessage>();
    private List<ChatListener> cl = new ArrayList<ChatListener>();
    Handler han = new Handler() {

        @Override
        public void handleMessage(android.os.Message msg) {
            // TODO: Implement this method
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    for (ChatListener c : cl) {
                        if (c != null) {
                            c.addMessage((Message) msg.obj);
                        } else {
                            cl.remove(c);
                        }
                    }
                    break;
                case 1:
                    for (ChatListener c : cl) {
                        if (c != null) {
                            c.reLogin((boolean) msg.obj);
                        } else {
                            cl.remove(c);
                        }
                    }
                    break;
                case 2:
                    for (ChatListener c : cl) {
                        if (c != null) {
                            c.reJoin((boolean) msg.obj);
                        } else {
                            cl.remove(c);
                        }
                    }
                    break;
            }
        }


    };

    private ServiceManagement() {

    }

    public static ServiceManagement getDx() {
        return su;
    }

    public void addListener(ChatListener c) {
        cl.add(c);
    }

    public List<ChatMessage> getData() {
        return data;
    }

    public void setIsListener(boolean isListener) {
        this.isListener = isListener;
    }

    public boolean isListener() {
        return isListener;
    }

    public void setIsConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public XMPPTCPConnection getCon() {
        return con;
    }

    private XMPPTCPConnection getConnextion(String name, String password) throws XmppStringprepException {
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setUsernameAndPassword(name, password)
                .setXmppDomain("mycard.moe")
                .setKeystoreType(null)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .setHost("chat.mycard.moe")
                .build();
        con = new XMPPTCPConnection(config);
        return con;
    }

    public boolean login(String name, String password) throws IOException, SmackException, XMPPException, InterruptedException {

        XMPPTCPConnection con = getConnextion(name, password);
        con.connect();

        if (con.isConnected()) {
            con.login();
            con.addConnectionListener(new TaxiConnectionListener());
            setIsConnected(true);
            return true;
        }
        setIsConnected(false);
        return false;
    }

    public void sendMessage(String message) throws SmackException.NotConnectedException, InterruptedException {
        muc.sendMessage(message);
    }

    public void joinChat() throws SmackException.NoResponseException, XMPPException.XMPPErrorException, MultiUserChatException.NotAMucServiceException, SmackException.NotConnectedException, XmppStringprepException, MultiUserChatException.MucAlreadyJoinedException, InterruptedException {
        if (!isListener) {
            MultiUserChatManager multiUserChatManager = MultiUserChatManager.getInstanceFor(getCon());
            muc = multiUserChatManager.getMultiUserChat(JidCreate.entityBareFrom(GROUP_ADDRESS));
            muc.createOrJoin(Resourcepart.from(UserManagement.getUserName()));
            data.clear();
            muc.addMessageListener(new MessageListener() {
                @Override
                public void processMessage(Message message) {

                    Log.e("接收消息", "接收" + message);
                    ChatMessage cm = ChatMessage.toChatMessage(message);
                    if (cm != null) {
                        data.add(cm);
                        han.sendEmptyMessage(0);
                    }
                }
            });
            setIsListener(true);
        }
    }

    public void setreLogin(boolean state) {
        android.os.Message me = new android.os.Message();
        me.what = 1;
        me.obj = state;
        han.sendMessage(me);
    }

    public void setreJoin(boolean state) {
        android.os.Message me = new android.os.Message();
        me.what = 2;
        me.obj = state;
        han.sendMessage(me);
    }

    public void disSerVice() {
        con.disconnect();
        setIsConnected(false);
    }

    public void disClass() {
        disSerVice();
        setIsConnected(false);
        setIsListener(false);
        data.clear();
        cl.clear();
    }

}
