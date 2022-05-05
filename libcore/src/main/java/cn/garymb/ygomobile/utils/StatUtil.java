package cn.garymb.ygomobile.utils;


import android.content.Context;
import android.util.Log;

import com.umeng.analytics.MobclickAgent;

import java.util.Map;

public class StatUtil
{
	public static void onResume(String name) {
//		SdkInitManagement.getInstance().initUmengSdk();
////		MobclickAgent.onPageStart(context.getClass().getName());
//		MobclickAgent.onResume(context);
		MobclickAgent.onPageStart(name);
		Log.e("UMLog","Fragment参数"+name);
	}


	public static void onPause(String name) {
//		SdkInitManagement.getInstance().initUmengSdk();
////		MobclickAgent.onPageEnd(context.getClass().getName());
//		MobclickAgent.onPause(context);
		MobclickAgent.onPageEnd(name);
		Log.e("UMLog","Fragment关闭参数"+name);
	}

	public static void onResume(Context context) {
		onResume(context,false);
	}

	public static void onResume(Context context,boolean isFragmentActivity) {
		if (!isFragmentActivity) {
			MobclickAgent.onPageStart(context.getClass().getName());
			Log.e("UMLog","调用状态"+context.getClass().getName());
		}
//		SdkInitManagement.getInstance().initUmengSdk();
		MobclickAgent.onResume(context);

//		MobclickAgent.onResume(context);
	}

	public static void onPause(Context context) {
		onPause(context,false);
	}

	public static void onPause(Context context,boolean isFragmentActivity) {
		if (!isFragmentActivity){
			MobclickAgent.onPageEnd(context.getClass().getName());
			Log.e("UMLog","关闭状态"+context.getClass().getName());
		}
//		SdkInitManagement.getInstance().initUmengSdk();
		MobclickAgent.onPause(context);

//		MobclickAgent.onPause(context);
	}


	public static void login(String userID){
//		SdkInitManagement.getInstance().initUmengSdk();
//		MobclickAgent.onProfileSignIn(userID);
	}

	public static void logout(){
//		SdkInitManagement.getInstance().initUmengSdk();
//		MobclickAgent.onProfileSignOff();
	}

	public static void onEvent(Context context, String eventID, Map<String,String> map){
//		SdkInitManagement.getInstance().initUmengSdk();
//		MobclickAgent.onEvent(context, eventID, map);
	}

	public static void onKillProcess(Context context){

		MobclickAgent.onKillProcess(context);
//		SdkInitManagement.getInstance().initUmengSdk();
//		MobclickAgent.onKillProcess(context);
	}

	public static void onLogin(String nameID){
		MobclickAgent.onProfileSignIn(nameID);
	}

	public static void onLogout(){
		MobclickAgent.onProfileSignOff();
	}

}
