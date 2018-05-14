package cn.garymb.ygomobile.ui.online.mcchat;
import org.jivesoftware.smack.packet.*;

public interface ChatListener
{
	void addMessage(Message message);
	void removeMessage(Message message);
	void reLogin(boolean state);
	void reJoin(boolean state);
	
}
