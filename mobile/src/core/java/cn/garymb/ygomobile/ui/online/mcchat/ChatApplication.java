package cn.garymb.ygomobile.ui.online.mcchat;
import android.app.*;
import android.content.*;

public class ChatApplication extends Application
{

	private static Context context;
	@Override
	public void onCreate()
	{
		// TODO: Implement this method
		super.onCreate();
		context=getApplicationContext();
		
	}
	
	public static Context getContext(){
		return context;
	}
	
}
