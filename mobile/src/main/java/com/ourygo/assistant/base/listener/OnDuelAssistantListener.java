package com.ourygo.assistant.base.listener;

public interface OnDuelAssistantListener {
    void onJoinRoom(String password,int id);
    void onCardSearch(String key,int id);
    void onSaveDeck(String message,boolean isUrl,int id);
    boolean isListenerEffective();
}