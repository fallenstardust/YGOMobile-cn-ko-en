package cn.garymb.ygomobile.ui.mycard.mcchat.management;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatException;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import cn.garymb.ygomobile.ui.mycard.base.OnJoinChatListener;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
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

    private static final ServiceManagement su = new ServiceManagement();
    private final List<ChatMessage> chatMessageList;
    private final ConcurrentHashMap<Integer, ChatListener> chatListenerList;
    private final ConcurrentHashMap<Integer, OnJoinChatListener> joinChatListenerList;
    private int chatListenerId = 0;
    private int joinChatListenerId = 0;

    @SuppressLint("HandlerLeak")
    private final Handler han = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TYPE_ADD_MESSAGE:
                    broadcastChatMessage((ChatMessage) msg.obj);
                    break;
                case TYPE_RE_LOGIN:
                    broadcastReLogin((boolean) msg.obj);
                    break;
                case TYPE_RE_JOIN:
                    broadcastReJoin((boolean) msg.obj);
                    break;
                case CHAT_LOGIN_EXCEPTION_RE:
                    // 处理重新登录异常的逻辑
                    //for (OnJoinChatListener listener : joinChatListenerList.values()) {
                    //    if (listener != null && listener.isListenerEffective()) {
                    //        listener.onLoginExceptionClickRe();
                    //    }
                    //}
                    //break;
                case CHAT_LOGIN_OK:
                    for (OnJoinChatListener listener : joinChatListenerList.values()) {
                        if (listener != null && listener.isListenerEffective()) {
                            listener.onChatLogin(null);
                        }
                    }
                    break;
                case CHAT_LOGIN_EXCEPTION:
                    for (OnJoinChatListener listener : joinChatListenerList.values()) {
                        if (listener != null && listener.isListenerEffective()) {
                            listener.onChatLogin(msg.obj + "");
                        }
                    }
                    break;
                case CHAT_LOGIN_LOADING:
                    for (OnJoinChatListener listener : joinChatListenerList.values()) {
                        if (listener != null && listener.isListenerEffective()) {
                            listener.onChatLoginLoading();
                        }
                    }
                    break;
                case CHAT_JOIN_ROOM_LOADING:
                    for (OnJoinChatListener listener : joinChatListenerList.values()) {
                        if (listener != null && listener.isListenerEffective()) {
                            listener.onJoinRoomLoading();
                        }
                    }
                    break;
                case CHAT_USER_NULL:
                    for (OnJoinChatListener listener : joinChatListenerList.values()) {
                        if (listener != null && listener.isListenerEffective()) {
                            listener.onChatUserNull();
                        }
                    }
                    break;
            }
        }

        private void broadcastChatMessage(ChatMessage message) {
            for (ChatListener listener : chatListenerList.values()) {
                if (listener != null && listener.isListenerEffective()) {
                    listener.addChatMessage(message);
                }
            }
        }

        private void broadcastReLogin(boolean state) {
            for (ChatListener listener : chatListenerList.values()) {
                if (listener != null && listener.isListenerEffective()) {
                    listener.reChatLogin(state);
                }
            }
        }

        private void broadcastReJoin(boolean state) {
            for (ChatListener listener : chatListenerList.values()) {
                if (listener != null && listener.isListenerEffective()) {
                    listener.reChatJoin(state);
                }
            }
        }
    };

    private XMPPTCPConnection con;
    private MultiUserChat muc;
    private boolean isConnected = false;
    private boolean isListener = false;
    private boolean isStartLoading = false;
    private final Object connectionLock = new Object();

    private ServiceManagement() {
        chatMessageList = Collections.synchronizedList(new ArrayList<>());
        chatListenerList = new ConcurrentHashMap<>();
        joinChatListenerList = new ConcurrentHashMap<>();
    }

    public static ServiceManagement getDx() {
        return su;
    }

    public int addListener(ChatListener c) {
        int id = ++chatListenerId;
        chatListenerList.put(id, c);
        return id;
    }

    public void removeListener(int id) {
        chatListenerList.remove(id);
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
        try {
            XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                    .setUsernameAndPassword(name, password)
                    .setXmppDomain("mycard.moe")
                    .setKeystoreType(null)
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
                    .setHostAddress(InetAddress.getByName("mchat.moecube.com"))
                    .setPort(5222)
                    .setConnectTimeout(10000)
                    .setCompressionEnabled(false)
                    .build();
            
            synchronized (connectionLock) {
                if (con != null && con.isConnected()) {
                    try {
                        con.disconnect();
                    } catch (Exception e) {
                        Log.w("ServiceManagement", "Error disconnecting old connection", e);
                    }
                }
                con = new XMPPTCPConnection(config);
            }
            return con;
        } catch (Exception e) {
            Log.e("ServiceManagement", "Failed to create XMPP connection configuration", e);
            throw new IOException("Failed to create connection configuration: " + e.getMessage(), e);
        }
    }

    public boolean login(String name, String password) throws IOException, SmackException, XMPPException, InterruptedException {
        synchronized (connectionLock) {
            try {
                XMPPTCPConnection connection = getConnextion(name, password);
                
                connection.connect();
                
                if (!connection.isConnected()) {
                    Log.e("ServiceManagement", "Connection failed - not connected after connect()");
                    setIsConnected(false);
                    return false;
                }
                
                connection.login();
                
                if (connection.isAuthenticated()) {
                    connection.addConnectionListener(new TaxiConnectionListener());
                    setIsConnected(true);
                    Log.d("ServiceManagement", "XMPP login successful for user: " + name);
                    return true;
                } else {
                    Log.e("ServiceManagement", "Authentication failed");
                    setIsConnected(false);
                    try {
                        connection.disconnect();
                    } catch (Exception e) {
                        Log.w("ServiceManagement", "Error disconnecting after auth failure", e);
                    }
                    return false;
                }
            } catch (XMPPException.StreamErrorException | XMPPException.XMPPErrorException e) {
                Log.e("ServiceManagement", "XMPP authentication error", e);
                setIsConnected(false);
                throw e;
            } catch (SmackException.NotConnectedException | SmackException.NoResponseException e) {
                Log.e("ServiceManagement", "Connection error during login", e);
                setIsConnected(false);
                throw e;
            } catch (Exception e) {
                Log.e("ServiceManagement", "Unexpected error during login", e);
                setIsConnected(false);
                throw e;
            }
        }
    }

    public void sendMessage(String message) throws SmackException.NotConnectedException, InterruptedException {
        if (muc != null && isListener) {
            muc.sendMessage(message);
        }
    }

    public int getMemberNum() {
        if (!isListener || muc == null)
            return 0;
        return muc.getOccupantsCount();
    }

    public void joinChat() throws SmackException.NoResponseException, XMPPException.XMPPErrorException,
            MultiUserChatException.NotAMucServiceException, SmackException.NotConnectedException,
            XmppStringprepException, MultiUserChatException.MucAlreadyJoinedException, InterruptedException {
        if (!isListener) {
            McUser mcUser = UserManagement.getDx().getMcUser();
            if (mcUser == null || TextUtils.isEmpty(mcUser.getUsername())) {
                throw new IllegalStateException("User not logged in");
            }

            MultiUserChatManager multiUserChatManager = MultiUserChatManager.getInstanceFor(getCon());
            muc = multiUserChatManager.getMultiUserChat(JidCreate.entityBareFrom(GROUP_ADDRESS));
            muc.createOrJoin(Resourcepart.from(mcUser.getUsername()));
            chatMessageList.clear();
            muc.addMessageListener(message -> {
                Log.e("接收消息", "接收" + message);
                ChatMessage cm = ChatMessage.toChatMessage(message);
                if (cm != null) {
                    chatMessageList.add(cm);
                    HandlerUtil.sendMessage(han, TYPE_ADD_MESSAGE, cm);
                }
            });
            setIsListener(true);
        }
    }

    public void setReLogin(boolean state) {
        Message me = new Message();
        me.what = TYPE_RE_LOGIN;
        me.obj = state;
        han.sendMessage(me);
    }

    public void setReJoin(boolean state) {
        Message me = new Message();
        me.what = TYPE_RE_JOIN;
        me.obj = state;
        han.sendMessage(me);
    }

    public void disSerVice() {
        synchronized (connectionLock) {
            if (muc != null) {
                try {
                    if (isListener) {
                        muc.leave();
                    }
                } catch (SmackException.NotConnectedException | InterruptedException e) {
                    Log.e("ServiceManagement", "Error leaving MUC", e);
                }
                muc = null;
            }

            if (con != null) {
                try {
                    if (con.isConnected()) {
                        con.disconnect();
                    }
                } catch (Exception e) {
                    Log.e("ServiceManagement", "Error disconnecting XMPP connection", e);
                }
                con = null;
            }

            setIsConnected(false);
            setIsListener(false);
        }
    }

    public void disClass() {
        disSerVice();
        chatMessageList.clear();
        chatListenerList.clear();
        joinChatListenerList.clear();
    }

    public int addJoinRoomListener(OnJoinChatListener onJoinChatListener) {
        int id = ++joinChatListenerId;
        joinChatListenerList.put(id, onJoinChatListener);
        return id;
    }

    public void removeJoinRoomListener(int id) {
        joinChatListenerList.remove(id);
    }

    public void start() {
        if (isStartLoading)
            return;
        isStartLoading = true;
        String name, password;
        McUser mcUser = UserManagement.getDx().getMcUser();

        if (mcUser == null || TextUtils.isEmpty(mcUser.getUsername())) {
            isStartLoading = false;
            han.sendEmptyMessage(CHAT_USER_NULL);
            return;
        }

        name = mcUser.getUsername();
        password = mcUser.getPassword();
        
        if (TextUtils.isEmpty(password)) {
            Log.e("ServiceManagement", "Password is empty for user: " + name);
            isStartLoading = false;
            han.sendEmptyMessage(CHAT_USER_NULL);
            return;
        }

        if (su.isListener()) {
            isStartLoading = false;
            han.sendEmptyMessage(CHAT_LOGIN_OK);
            return;
        }

        new Thread(() -> {
            if (!su.isConnected()) {
                han.sendEmptyMessage(CHAT_LOGIN_LOADING);
                Message me = new Message();
                me.what = CHAT_LOGIN_EXCEPTION;

                try {
                    su.login(name, password);
                } catch (InterruptedException e) {
                    Log.e("ServiceManagement", "Login interrupted", e);
                    isStartLoading = false;
                    me.obj = "登录被中断";
                    han.sendMessage(me);
                } catch (IOException e) {
                    Log.e("ServiceManagement", "IO error during login", e);
                    isStartLoading = false;
                    me.obj = "网络错误：" + e.getMessage();
                    han.sendMessage(me);
                } catch (XMPPException.StreamErrorException | XMPPException.XMPPErrorException e) {
                    Log.e("ServiceManagement", "XMPP authentication error", e);
                    isStartLoading = false;
                    me.obj = "认证失败，请检查账号状态";
                    han.sendMessage(me);
                } catch (SmackException.NotConnectedException | SmackException.NoResponseException e) {
                    Log.e("ServiceManagement", "Connection error during login", e);
                    isStartLoading = false;
                    me.obj = "连接超时，请检查网络";
                    han.sendMessage(me);
                } catch (SmackException e) {
                    Log.e("ServiceManagement", "Smack error during login", e);
                    isStartLoading = false;
                    me.obj = "通信错误：" + e.getMessage();
                    han.sendMessage(me);
                } catch (Exception e) {
                    Log.e("ServiceManagement", "Other error during login", e);
                    isStartLoading = false;
                    me.obj = "登录失败：" + e.getMessage();
                    han.sendMessage(me);
                }
            }

            if (su.isConnected()) {
                han.sendEmptyMessage(CHAT_JOIN_ROOM_LOADING);
                try {
                    su.joinChat();
                    isStartLoading = false;
                    han.sendEmptyMessage(CHAT_LOGIN_OK);
                } catch (Exception e) {
                    Log.e("ServiceManagement", "Error joining chat", e);
                    isStartLoading = false;
                    HandlerUtil.sendMessage(han, CHAT_LOGIN_EXCEPTION, "加入聊天室失败: " + e.getMessage());
                }
            }
        }).start();
    }
}