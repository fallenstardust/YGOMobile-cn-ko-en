package com.ourygo.oy.util;

import android.content.SharedPreferences;


import cn.garymb.ygomobile.App;

public class SharedPreferenceUtil
{
	
	//获取存放路径的share
	public static SharedPreferences getSharePath(){
		return App.get().getSharedPreferences("path",App.get().MODE_PRIVATE);
	}
	//获取存放类型的share
	public static SharedPreferences getShareType(){
		return App.get().getSharedPreferences("type",App.get().MODE_PRIVATE);
	}
	//获取存放开关状态的share
	public static SharedPreferences getShareKaiguan(){
		return App.get().getSharedPreferences("kaiguan",App.get().MODE_PRIVATE);
	}
	//获取各种记录的share
	public static SharedPreferences getShareRecord(){
		return App.get().getSharedPreferences("record",App.get().MODE_PRIVATE);
	}
	
	public static boolean addAppStartTimes(){
		return getShareRecord().edit().putInt("StartTimes",getAppStartTimes()+1).commit();
	}
	//获取应用的启动次数
	public static int getAppStartTimes(){	
		return getShareRecord().getInt("StartTimes",0);
	}
	
	public static String getUserName(){
		return getShareRecord().getString("userName",null);
	}
	
	public static String getUserPassword(){
		return getShareRecord().getString("userPassword",null);
	}
	
	public static String getUserAccount(){
		return getShareRecord().getString("userAccount",null);
	}
	
	public static String getHttpSessionId() {
		return getShareRecord().getString("sessionId",null);
	}
	
	public static boolean setHttpSessionId(String sessionid) {
		return getShareRecord().edit().putString("sessionId",sessionid).commit();
		// TODO: Implement this method
	}
	
	public static boolean setUserName(String name){
		return getShareRecord().edit().putString("userName",name).commit();
	}
	
	public static boolean setUserAccount(String account){
		return getShareRecord().edit().putString("userAccount",account).commit();
	}
	
	public static boolean setUserPassword(String password){
		return getShareRecord().edit().putString("userPassword",password).commit();
	}
	
}
