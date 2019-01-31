package com.ourygo.oy.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.File;

public class IntentUtil
{

	/*
	 *根据包名和入口名应用跳转,返回跳转的intent,适用于无MainActivity的应用等
	 *packageName:应用包名
	 *activity:入口名
	 */
	public static Intent getAppIntent(String packageName, String activity){
		Intent intent=new Intent();
		intent.setComponent(new ComponentName(packageName,activity));
		return intent;
	}
	
	//应用跳转
	public static Intent getAppIntent(Context context, String s){
		Context c = context;
		PackageManager pm =  c.getPackageManager();
		return pm.getLaunchIntentForPackage(s);
	}

	//决斗跳转
	public static void duelIntent(Context context,String ip, int dk, String name, String password){
		Intent intent1=new Intent("ygomobile.intent.action.GAME");
		intent1.putExtra("host", ip);
		intent1.putExtra("port", dk);
		intent1.putExtra("user", name);
		intent1.putExtra("room", password);
		intent1.setPackage("cn.garymb.ygomobile");
		intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(intent1);
	}

	public static void deckEditIntent(Context context,String deckPath){
		Intent intent1=new Intent("ygomobile.intent.action.DECK");
		intent1.putExtra(Intent.EXTRA_TEXT, deckPath);
		context.startActivity(intent1);
	}
	

//Android获取一个用于打开APK文件的intent
	public static Intent getApkFileIntent(Context context, String param ) {

		Intent intent = new Intent();  
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);  
		intent.setAction(Intent.ACTION_VIEW);
		Uri uri = Uri.fromFile(new File(param ));
		intent.setDataAndType(uri,"application/vnd.android.package-archive"); 
		return intent;
	}
	
	public static Intent getUrlIntent(String url){
		return new Intent (Intent.ACTION_VIEW,Uri.parse(url));
	}

}
