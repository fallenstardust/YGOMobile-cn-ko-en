package cn.garymb.ygomobile.ui.mycard.mcchat.management;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
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

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.ui.mycard.base.OnJoinChatListener;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatListener;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatMessage;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.TaxiConnectionListener;
import cn.garymb.ygomobile.utils.HandlerUtil;

public class ServiceManagement {
    public static final String GROUP_ADDRESS = "ygopro_china_north@conference.mycard.moe";

    public static final int TYPE_ADD_MESSAGE = 0;
    public static final int TYPE_RE_LOGIN = 1;
    public static final int TYPE_RE_JOIN = 2;


    public static final int CHAT_LOGIN_EXCEPTION_RE = 3;
    public static final int CHAT_LOGIN_OK = 4;
    public static final int CHAT_LOGIN_EXCEPTION = 5;
    public static final int CHAT_LOGIN_LOADING = 6;
    public static final int CHAT_JOIN_ROOM_LOADING = 7;
    public static final int CHAT_USER_NULL = 8;

    private static ServiceManagement su = new ServiceManagement();
    private XMPPTCPConnection con;
    private MultiUserChat muc;
    private boolean isConnected = false;
    private boolean isListener = false;
    private boolean isStartLoading=false;

    private List<ChatMessage> chatMessageList;
    private List<ChatListener> chatListenerList;
    private List<OnJoinChatListener> joinChatListenerList;


    @SuppressLint("HandlerLeak")
    Handler han = new Handler() {

        @Override
        public void handleMessage(android.os.Message msg) {
            // TODO: Implement this method
            super.handleMessage(msg);
            int i = 0;
            switch (msg.what) {
                case TYPE_ADD_MESSAGE:
                    while (i < chatListenerList.size()) {
                        ChatListener chatListener = chatListenerList.get(i);
                        if (chatListener.isListenerEffective()) {
                            chatListener.addChatMessage((ChatMessage) msg.obj);
                            i++;
                        } else {
                            chatListenerList.remove(i);
                        }
                    }
                    break;
                case TYPE_RE_LOGIN:
                    while (i < chatListenerList.size()) {
                        ChatListener chatListener = chatListenerList.get(i);
                        if (chatListener.isListenerEffective()) {
                            chatListener.reChatLogin((boolean) msg.obj);
                            i++;
                        } else {
                            chatListenerList.remove(i);
                        }
                    }
                    break;
                case TYPE_RE_JOIN:

                    while (i < chatListenerList.size()) {
                        ChatListener chatListener = chatListenerList.get(i);
                        if (chatListener.isListenerEffective()) {
                            chatListener.reChatJoin((boolean) msg.obj);
                            i++;
                        } else {
                            chatListenerList.remove(i);
                        }
                    }
                    break;
                case CHAT_LOGIN_EXCEPTION_RE:
//                    while (i < joinChatListenerList.size()) {
//                        OnJoinChatListener ou = joinChatListenerList.get(i);
//                        if (ou.isListenerEffective()) {
//                            ou.onLoginExceptionClickRe();
//                            i++;
//                        } else {
//                            joinChatListenerList.remove(i);
//                        }
//                    }
//                    break;
                case CHAT_LOGIN_OK:
                    while (i < joinChatListenerList.size()) {
                        OnJoinChatListener onJoinChatListener = joinChatListenerList.get(i);
                        if (onJoinChatListener.isListenerEffective()) {
                            onJoinChatListener.onChatLogin(null);
                            i++;
                        } else {
                            joinChatListenerList.remove(i);
                        }
                    }
                    break;
                case CHAT_LOGIN_EXCEPTION:
                    while (i < joinChatListenerList.size()) {
                        OnJoinChatListener onJoinChatListener = joinChatListenerList.get(i);
                        if (onJoinChatListener.isListenerEffective()) {
                            onJoinChatListener.onChatLogin(msg.obj + "");
                            i++;
                        } else {
                            joinChatListenerList.remove(i);
                        }
                    }
                    break;
                case CHAT_LOGIN_LOADING:
                    while (i < joinChatListenerList.size()) {
                        OnJoinChatListener onJoinChatListener = joinChatListenerList.get(i);
                        if (onJoinChatListener.isListenerEffective()) {
                            onJoinChatListener.onChatLoginLoading();
                            i++;
                        } else {
                            joinChatListenerList.remove(i);
                        }
                    }
                    break;
                case CHAT_JOIN_ROOM_LOADING:
                    while (i < joinChatListenerList.size()) {
                        OnJoinChatListener onJoinChatListener = joinChatListenerList.get(i);
                        if (onJoinChatListener.isListenerEffective()) {
                            onJoinChatListener.onJoinRoomLoading();
                            i++;
                        } else {
                            joinChatListenerList.remove(i);
                        }
                    }
                    break;
                case CHAT_USER_NULL:
                    while (i < joinChatListenerList.size()) {
                        OnJoinChatListener onJoinChatListener = joinChatListenerList.get(i);
                        if (onJoinChatListener.isListenerEffective()) {
                            onJoinChatListener.onChatUserNull();
                            i++;
                        } else {
                            joinChatListenerList.remove(i);
                        }
                    }
                    break;
            }
        }
    };

    private ServiceManagement() {
        chatMessageList = new ArrayList<>();
        chatListenerList = new ArrayList<>();
        joinChatListenerList = new ArrayList<>();
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
                .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
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

    public int getMemberNum(){
        if (!isListener)
            return 0;
        return muc.getOccupantsCount();
    }

    public void joinChat() throws SmackException.NoResponseException, XMPPException.XMPPErrorException, MultiUserChatException.NotAMucServiceException, SmackException.NotConnectedException, XmppStringprepException, MultiUserChatException.MucAlreadyJoinedException, InterruptedException {
        if (!isListener) {
            MultiUserChatManager multiUserChatManager = MultiUserChatManager.getInstanceFor(getCon());
            muc = multiUserChatManager.getMultiUserChat(JidCreate.entityBareFrom(GROUP_ADDRESS));
            muc.createOrJoin(Resourcepart.from(UserManagement.getUserName()));
            chatMessageList.clear();
            muc.addMessageListener(message -> {

                Log.e("接收消息", "接收" + message);
                ChatMessage cm = ChatMessage.toChatMessage(message);
                if (cm != null) {
                    chatMessageList.add(cm);
                    HandlerUtil.sendMessage(han,TYPE_ADD_MESSAGE,cm);
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
        if (con != null) {
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
        joinChatListenerList.clear();
    }

    public void addJoinRoomListener(OnJoinChatListener onJoinChatListener) {
        joinChatListenerList.add(onJoinChatListener);
    }

    public void removeJoinRoomListener(OnJoinChatListener onJoinChatListener) {
        joinChatListenerList.remove(onJoinChatListener);
    }

    public void start() {
        if (isStartLoading)
            return;
        isStartLoading=true;
        String name, password;
        SharedPreferences lastModified = App.get().getSharedPreferences("lastModified", Context.MODE_PRIVATE);
        UserManagement.setUserName(lastModified.getString("user_name", null));
        UserManagement.setUserPassword(lastModified.getString("user_external_id", null));

        name = UserManagement.getUserName();
        password = UserManagement.getUserPassword();
        Log.i(BuildConfig.VERSION_NAME +"看看",name+"+"+password);
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(password)) {
            isStartLoading=false;
            han.sendEmptyMessage(CHAT_USER_NULL);
            return;
        }

        if (su.isListener()) {
            isStartLoading=false;
            han.sendEmptyMessage(CHAT_LOGIN_OK);
            return;
        }

        new Thread(() -> {
            if (!su.isConnected()) {
                han.sendEmptyMessage(CHAT_LOGIN_LOADING);
                android.os.Message me = new android.os.Message();
                me.what = CHAT_LOGIN_EXCEPTION;

                try {
                    su.login(name, password);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    isStartLoading=false;
                    me.obj = "InterruptedException：" + e;
                    han.sendMessage(me);
                } catch (IOException e) {
                    isStartLoading=false;
                    me.obj = "IOException：" + e;
                    e.printStackTrace();
                    han.sendMessage(me);
                } catch (SmackException e) {
                    isStartLoading=false;
                    me.obj = "SmackException：" + e;
                    e.printStackTrace();
                    han.sendMessage(me);
                } catch (XMPPException e) {
                    isStartLoading=false;
                    me.obj = "XMPPException：" + e;
                    e.printStackTrace();
                    han.sendMessage(me);
                } catch (Exception e) {
                    isStartLoading=false;
                    me.obj = "其他错误：" + e;
                    e.printStackTrace();
                    han.sendMessage(me);
                }
            }
            if (su.isConnected()) {
                han.sendEmptyMessage(CHAT_JOIN_ROOM_LOADING);
                try {
                    su.joinChat();
                    isStartLoading=false;
                    han.sendEmptyMessage(CHAT_LOGIN_OK);
                } catch (Exception e) {
                    isStartLoading=false;
                    HandlerUtil.sendMessage(han, CHAT_LOGIN_EXCEPTION, e);
                }
            }
            // TODO: Implement this method
        }).start();

    }

}
