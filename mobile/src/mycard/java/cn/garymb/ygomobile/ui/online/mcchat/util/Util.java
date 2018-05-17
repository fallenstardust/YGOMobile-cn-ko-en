package cn.garymb.ygomobile.ui.online.mcchat.util;
import android.app.*;
import android.content.*;
import android.view.inputmethod.*;
import android.widget.*;

public class Util
{
	//提示
	public static void show(Context context,String message){
		Toast.makeText(context,message,Toast.LENGTH_SHORT).show();
	}
	//关闭输入法
	public static void closeKeyboard(Activity activity){
		InputMethodManager inputMethodManager = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		inputMethodManager.hideSoftInputFromWindow((activity).getCurrentFocus().getWindowToken()
												   ,InputMethodManager.HIDE_NOT_ALWAYS);
	}
	


	//复制字符串到剪贴板
	public static void fzMessage(Context context,String message){
		ClipboardManager cmb = (ClipboardManager) context.getSystemService(context.CLIPBOARD_SERVICE);
		cmb.setText(message);//复制命令
	}
	
	
	
}
