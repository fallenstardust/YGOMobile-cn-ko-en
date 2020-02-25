package com.ourygo.assistant.util;


import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.ourygo.assistant.base.listener.OnClipChangedListener;
import com.ourygo.assistant.base.listener.OnDuelAssistantListener;

import java.util.ArrayList;
import java.util.List;

public class DuelAssistantManagement implements OnClipChangedListener {
    private static final DuelAssistantManagement ourInstance = new DuelAssistantManagement();
    private Context context;
    private List<OnDuelAssistantListener> onDuelAssistantListenerList;
    private ClipManagement clipManagement;
    //卡查内容
    private String cardSearchMessage = "";
    private String lastMessage = "";

    private DuelAssistantManagement() {
        onDuelAssistantListenerList = new ArrayList<>();
    }

    public static DuelAssistantManagement getInstance() {
        return ourInstance;
    }

    public void init(Context context) {
        this.context = context;
        //初始化剪贴板监听
        initClipListener();
    }

    public void addDuelAssistantListener(OnDuelAssistantListener onDuelAssistantListener) {
        if (!onDuelAssistantListenerList.contains(onDuelAssistantListener))
            onDuelAssistantListenerList.add(onDuelAssistantListener);
    }

    public void removeDuelAssistantListener(OnDuelAssistantListener onDuelAssistantListener) {
        onDuelAssistantListenerList.remove(onDuelAssistantListener);
    }

    public List<OnDuelAssistantListener> getOnDuelAssistantListenerList() {
        return onDuelAssistantListenerList;
    }

    private void initClipListener() {
        clipManagement = ClipManagement.getInstance();
        clipManagement.startClipboardListener(context);
        clipManagement.setOnClipChangedListener(this);
    }


    public void clear() {
        context = null;
        lastMessage = "";
        cardSearchMessage = "";
        onDuelAssistantListenerList.clear();
    }

    public String getCardSearchMessage() {
        return cardSearchMessage;
    }

    public boolean deckCheck(String message, int id) {
        //如果复制的内容是多行作为卡组去判断
        if (message.contains("\n")) {
            for (String s : Record.DeckTextKey) {
                //只要包含其中一个关键字就视为卡组
                if (message.contains(s)) {
                    onSaveDeck(message, false, id);
                    return true;
                }
            }
            return true;
        }

        //如果是卡组url
        int deckStart = message.indexOf(Record.DECK_URL_PREFIX);
        if (deckStart != -1) {
            onSaveDeck(message.substring(deckStart + Record.DECK_URL_PREFIX.length()), true, id);
            return true;
        }
        return false;
    }

    public boolean roomCheck(String message, int id) {
        int start = -1;
        int end = -1;
        String passwordPrefixKey = null;
        for (String s : Record.PASSWORD_PREFIX) {
            start = message.indexOf(s);
            passwordPrefixKey = s;
            if (start != -1) {
                break;
            }
        }

        if (start != -1) {
            //如果密码含有空格，则以空格结尾
            end = message.indexOf(" ", start);
            //如果不含有空格则取片尾所有
            if (end == -1) {
                end = message.length();
            } else {
                //如果只有密码前缀而没有密码内容则不跳转
                if (end - start == passwordPrefixKey.length())
                    return false;
            }
                onJoinRoom(message.substring(start, end), id);
                return true;
        }
        return false;
    }

    public boolean cardSearchCheck(String message, int id) {
        for (String s : Record.CARD_SEARCH_KEY) {
            int cardSearchStart = message.indexOf(s);
            if (cardSearchStart != -1) {
                //卡查内容
                cardSearchMessage = message.substring(cardSearchStart + s.length(), message.length());
                //如果复制的文本里带？号后面没有内容则不跳转
                if (TextUtils.isEmpty(cardSearchMessage)) {
                    return false;
                }
                //如果卡查内容包含“=”并且复制的内容包含“.”不卡查
                if (cardSearchMessage.contains("=") && message.contains(".")) {
                    return false;
                }
                onCardSearch(cardSearchMessage, id);
                return true;


            }
        }
        return false;
    }

    private void onJoinRoom(String password, int id) {
        int i = 0;
        while (i < onDuelAssistantListenerList.size()) {
            OnDuelAssistantListener onDuelAssistantListener = onDuelAssistantListenerList.get(i);
            if (onDuelAssistantListener.isListenerEffective()) {
                onDuelAssistantListener.onJoinRoom(password, id);
                i++;
            } else {
                onDuelAssistantListenerList.remove(i);
            }
        }
    }

    private void onCardSearch(String key, int id) {
        int i = 0;
        while (i < onDuelAssistantListenerList.size()) {
            OnDuelAssistantListener onDuelAssistantListener = onDuelAssistantListenerList.get(i);
            if (onDuelAssistantListener.isListenerEffective()) {
                onDuelAssistantListener.onCardSearch(key, id);
                i++;
            } else {
                onDuelAssistantListenerList.remove(i);
            }
        }
    }

    public void onSaveDeck(String message, boolean isUrl, int id) {
        int i = 0;
        while (i < onDuelAssistantListenerList.size()) {
            OnDuelAssistantListener onDuelAssistantListener = onDuelAssistantListenerList.get(i);
            if (onDuelAssistantListener.isListenerEffective()) {
                onDuelAssistantListener.onSaveDeck(message, isUrl, id);
                i++;
            } else {
                onDuelAssistantListenerList.remove(i);
            }
        }
    }

    @Override
    public void onClipChanged(String clipMessage, boolean isCheck, int id) {
        if (isCheck)
            if (clipMessage.equals(lastMessage))
                return;
        if (deckCheck(clipMessage, id)) {
            lastMessage = clipMessage;
        } else if (roomCheck(clipMessage, id)) {
            lastMessage = clipMessage;
        } else if (cardSearchCheck(clipMessage, id))
            lastMessage = clipMessage;
    }

    public void checkClip(int id) {
        clipManagement.onPrimaryClipChanged(true, id);
    }

}
