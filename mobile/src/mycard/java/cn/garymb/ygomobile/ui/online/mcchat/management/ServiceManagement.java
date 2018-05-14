package cn.garymb.ygomobile.ui.online.mcchat.management;

import android.util.*;

import java.util.*;
import java.io.*;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.tcp.*;
import org.jivesoftware.smackx.muc.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.jid.parts.*;
import org.jxmpp.stringprep.*;

import android.os.Handler;
import cn.garymb.ygomobile.ui.online.mcchat.util.*;
import cn.garymb.ygomobile.ui.online.mcchat.*;

public class ServiceManagement
{
	public static final String GROUP_ADDRESS="ygopro_china_north@conference.mycard.moe";
	
	private static ServiceManagement su=new ServiceManagement();
	private XMPPTCPConnection con;
	private MultiUserChat muc;
	private boolean isConnected=false;
	private boolean isListener=false;
	private List<ChatMessage> data=new ArrayList<ChatMessage>();
	private List<ChatListener> cl=new ArrayList<ChatListener>();
	
	private ServiceManagement(){
		
	}

	public void addListener(ChatListener c){
		cl.add(c);
	}
	
	public List<ChatMessage> getData()
	{
		return data;
	}

	public void setIsListener(boolean isListener)
	{
		this.isListener = isListener;
	}

	public boolean isListener()
	{
		return isListener;
	}

	public void setIsConnected(boolean isConnected)
	{
		this.isConnected = isConnected;
	}

	public boolean isConnected()
	{
		return isConnected;
	}


	public XMPPTCPConnection getCon()
	{
		return con;
	}
	
	private  XMPPTCPConnection getConnextion(String name,String password) throws XmppStringprepException{
		XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
			.setUsernameAndPassword(name, password)
			.setXmppDomain("mycard.moe")
			.setKeystoreType(null)
			.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
			.build();
			con= new XMPPTCPConnection(config);
		return con;
	}
	
	
	public boolean login(String name,String password) throws IOException, SmackException, XMPPException, InterruptedException{
		
		XMPPTCPConnection con= getConnextion(name,password);
		con.connect();
		
		if(con.isConnected()){
		con.login();
		con.addConnectionListener(new TaxiConnectionListener());
		setIsConnected(true);
		return true;
		}
		setIsConnected(false);
		return false;
	}
	
	public void sendMessage(String message) throws SmackException.NotConnectedException, InterruptedException{
		muc.sendMessage(message);
	}
	
	public void joinChat() throws SmackException.NoResponseException, XMPPException.XMPPErrorException, MultiUserChatException.NotAMucServiceException, SmackException.NotConnectedException, XmppStringprepException, MultiUserChatException.MucAlreadyJoinedException, InterruptedException{
		if(!isListener){
			MultiUserChatManager multiUserChatManager = MultiUserChatManager.getInstanceFor(getCon());
			muc = multiUserChatManager.getMultiUserChat(JidCreate.entityBareFrom(GROUP_ADDRESS));
			muc.createOrJoin(Resourcepart.from(UserManagement.getUserName()));
			data.clear();
			muc.addMessageListener(new MessageListener() {
					@Override
					public void processMessage(Message message) {

						Log.e("接收消息","接收"+message);
						ChatMessage cm=ChatMessage.toChatMessage(message);
						if(cm!=null){
							data.add(cm);
							han.sendEmptyMessage(0);
						}
					}
				});
			setIsListener(true);
		}
	}
	
	Handler han=new Handler(){

		@Override
		public void handleMessage(android.os.Message msg)
		{
			// TODO: Implement this method
			super.handleMessage(msg);
			switch(msg.what){
				case 0:
					for(ChatListener c:cl){
						if(c!=null){
							c.addMessage((Message)msg.obj);
						}else{
							cl.remove(c);
						}
					}
					break;
				case 1:
					for(ChatListener c:cl){
						if(c!=null){
							c.reLogin((boolean)msg.obj);
						}else{
							cl.remove(c);
						}
					}
					break;
				case 2:
					for(ChatListener c:cl){
						if(c!=null){
							c.reJoin((boolean)msg.obj);
						}else{
							cl.remove(c);
						}
					}
					break;
			}
		}
		
		
	};
	
	
	public void setreLogin(boolean state){
		android.os.Message me=new android.os.Message();
		me.what=1;
		me.obj=state;
		han.sendMessage(me);
	}
	
	public void setreJoin(boolean state){
		android.os.Message me=new android.os.Message();
		me.what=2;
		me.obj=state;
		han.sendMessage(me);
	}
	
	public static ServiceManagement getDx(){
		return su;
	}
	
	public void disSerVice(){
		con.disconnect();
		setIsConnected(false);
	}
	
	public void disClass(){
		disSerVice();
		setIsConnected(false);
		setIsListener(false);
		data.clear();
		cl.clear();
	}
	
}
