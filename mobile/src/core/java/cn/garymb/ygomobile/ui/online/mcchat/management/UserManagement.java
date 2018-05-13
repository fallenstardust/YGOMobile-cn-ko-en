package cn.garymb.ygomobile.ui.online.mcchat.management;
import android.content.*;
import cn.garymb.ygomobile.ui.online.mcchat.*;

public class UserManagement
{
	private static UserManagement um=new UserManagement();
	private static String userName,userPassword;
	
	private UserManagement(){
		
		
	}
	
	
	public static String getUserName(){
		return userName;
	}
	
	public static String getUserPassword(){
		return userPassword;
	}
	
	public static void setUserName(String name){
		
			userName=name;

	}
	
	public static  void setUserPassword(String password){
		
			userPassword=password;
	}
	
	public static UserManagement getDx(){
		return um;
	}
}
