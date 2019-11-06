package cn.garymb.ygomobile.mycard.mcchat.base;

import org.jivesoftware.smack.packet.Message;

public interface ChatListener {
    void addMessage(Message message);

    void removeMessage(Message message);

    void reLogin(boolean state);

    void reJoin(boolean state);

}
