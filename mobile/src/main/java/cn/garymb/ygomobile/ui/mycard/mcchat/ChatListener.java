package cn.garymb.ygomobile.ui.mycard.mcchat;

public interface ChatListener {
    void addChatMessage(ChatMessage message);

    void removeChatMessage(ChatMessage message);

    void reChatLogin(boolean state);

    void reChatJoin(boolean state);

    boolean isListenerEffective();

}
