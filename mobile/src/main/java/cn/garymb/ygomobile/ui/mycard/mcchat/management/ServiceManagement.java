package cn.garymb.ygomobile.ui.mycard.mcchat.management;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

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
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setUsernameAndPassword(name, password)
                .setXmppDomain("mycard.moe")
                .setKeystoreType(null)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
                .setHostAddress(InetAddress.getByName("mchat.moecube.com"))
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
            con.disconnect();
            con = null;
        }

        setIsConnected(false);
        setIsListener(false);
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

        if (mcUser == null || TextUtils.isEmpty(mcUser.getUsername()) || TextUtils.isEmpty(mcUser.getPassword())) {
            isStartLoading = false;
            han.sendEmptyMessage(CHAT_USER_NULL);
            return;
        }

        name = mcUser.getUsername();
        password = mcUser.getPassword();

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
                    me.obj = "InterruptedException：" + e;
                    han.sendMessage(me);
                } catch (IOException e) {
                    Log.e("ServiceManagement", "IO error during login", e);
                    isStartLoading = false;
                    me.obj = "IOException：" + e;
                    han.sendMessage(me);
                } catch (SmackException e) {
                    Log.e("ServiceManagement", "Smack error during login", e);
                    isStartLoading = false;
                    me.obj = "SmackException：" + e;
                    han.sendMessage(me);
                } catch (XMPPException e) {
                    Log.e("ServiceManagement", "XMPP error during login", e);
                    isStartLoading = false;
                    me.obj = "XMPPException：" + e;
                    han.sendMessage(me);
                } catch (Exception e) {
                    Log.e("ServiceManagement", "Other error during login", e);
                    isStartLoading = false;
                    me.obj = "otherException：" + e;
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
                    HandlerUtil.sendMessage(han, CHAT_LOGIN_EXCEPTION, e);
                }
            }
        }).start();
    }
}