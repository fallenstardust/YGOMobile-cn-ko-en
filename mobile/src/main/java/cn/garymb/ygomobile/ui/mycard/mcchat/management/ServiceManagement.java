package cn.garymb.ygomobile.ui.mycard.mcchat.management;

import android.annotation.SuppressLint;
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
import cn.garymb.ygomobile.utils.FileLogUtil;

public class ServiceManagement {
    public static final String GROUP_ADDRESS = "ygopro_china_north@conference.mycard.moe";

    public static final int TYPE_ADD_MESSAGE=0;
    public static final int TYPE_RE_LOGIN=1;
    public static final int TYPE_RE_JOIN=2;

    private static ServiceManagement su = new ServiceManagement();
    private XMPPTCPConnection con;
    private MultiUserChat muc;
    private boolean isConnected = false;
    private boolean isListener = false;
    private List<ChatMessage> chatMessageList;
    private List<ChatListener> chatListenerList;
    @SuppressLint("HandlerLeak")
    Handler han = new Handler() {

        @Override
        public void handleMessage(android.os.Message msg) {
            // TODO: Implement this method
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_ADD_MESSAGE:
                    for (ChatListener c : chatListenerList) {
                        if (c != null) {
                            c.addMessage((Message) msg.obj);
                        } else {
                            chatListenerList.remove(c);
                        }
                    }
                    break;
                case TYPE_RE_LOGIN:
                    for (ChatListener c : chatListenerList) {
                        if (c != null) {
                            c.reLogin((boolean) msg.obj);
                        } else {
                            chatListenerList.remove(c);
                        }
                    }
                    break;
                case TYPE_RE_JOIN:
                    for (ChatListener c : chatListenerList) {
                        if (c != null) {
                            c.reJoin((boolean) msg.obj);
                        } else {
                            chatListenerList.remove(c);
                        }
                    }
                    break;
            }
        }
    };

    private ServiceManagement() {
        chatMessageList=new ArrayList<>();
        chatListenerList=new ArrayList<>();
    }

    public static ServiceManagement getDx() {
        return su;
    }

    public void addListener(ChatListener c) {
        chatListenerList.add(c);
    }

    public List<ChatMessage> getData() {
        return chatMessageList;
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

    private XMPPTCPConnection getConnextion(String name, String password) throws IOException {
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setUsernameAndPassword(name, password)
                .setXmppDomain("mycard.moe")
                .setKeystoreType(null)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .setHost("chat.mycard.moe")
                .build();
        FileLogUtil.writeAndTime("初始化配置");
        con = new XMPPTCPConnection(config);
        FileLogUtil.writeAndTime("建立新配置");
        return con;
    }

    public boolean login(String name, String password) throws IOException, SmackException, XMPPException, InterruptedException {

        FileLogUtil.writeAndTime("获取配置之前");
        XMPPTCPConnection con = getConnextion(name, password);
        FileLogUtil.writeAndTime("获取配置完毕");
        con.connect();
        FileLogUtil.writeAndTime("连接完毕");
        if (con.isConnected()) {
            con.login();
            FileLogUtil.writeAndTime("登陆完毕");
            con.addConnectionListener(new TaxiConnectionListener());
            FileLogUtil.writeAndTime("设置监听完毕");
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
            chatMessageList.clear();
            muc.addMessageListener(new MessageListener() {
                @Override
                public void processMessage(Message message) {

                    Log.e("接收消息", "接收" + message);
                    ChatMessage cm = ChatMessage.toChatMessage(message);
                    if (cm != null) {
                        chatMessageList.add(cm);
                        han.sendEmptyMessage(TYPE_ADD_MESSAGE);
                    }
                }
            });
            setIsListener(true);
        }
    }

    public void setReLogin(boolean state) {
        android.os.Message me = new android.os.Message();
        me.what = TYPE_RE_LOGIN;
        me.obj = state;
        han.sendMessage(me);
    }

    public void setReJoin(boolean state) {
        android.os.Message me = new android.os.Message();
        me.what = TYPE_RE_JOIN;
        me.obj = state;
        han.sendMessage(me);
    }

    public void disSerVice() {
        if(con!=null) {
            con.disconnect();
        }
        setIsConnected(false);
    }

    public void disClass() {
        disSerVice();
        setIsConnected(false);
        setIsListener(false);
        chatMessageList.clear();
        chatListenerList.clear();
    }

}
