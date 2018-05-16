package cn.garymb.ygomobile.ui.online.mcchat;
import android.app.*;
import android.os.*;
import android.content.*;
import android.util.*;
import android.widget.*;
import android.view.*;
import android.view.View.*;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.online.MyCardActivity;
import cn.garymb.ygomobile.ui.online.mcchat.util.*;
import cn.garymb.ygomobile.ui.online.mcchat.management.*;

public class SplashActivity extends Activity
{

	ServiceManagement su;
	ProgressBar sp_jz;
	TextView sp_tv;
	LinearLayout sp_li;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// TODO: Implement this method
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash);
		
		initView();
		/*String name=getIntent().getStringExtra("name");
		String password=getIntent().getStringExtra("password");
		UserManagement.setUserName(name);
		UserManagement.setUserPassword(password);*/
		//UserManagement.setUserName("废话多");
		//UserManagement.setUserPassword("19709");

		SharedPreferences lastModified = getSharedPreferences("lastModified", Context.MODE_PRIVATE);
		UserManagement.setUserName(lastModified.getString("user_name",null));
		UserManagement.setUserPassword(lastModified.getString("user_external_id",null));

		su = ServiceManagement.getDx();
		join();

	}

	private void join()
	{
		if (!su.isListener())
		{
			new Thread(new Runnable(){

					@Override
					public void run()
					{
						if (!su.isConnected()){
							han.sendEmptyMessage(4);
							login();
						}
						if(su.isConnected()){
							han.sendEmptyMessage(3);
							try
							{
								su.joinChat();
								han.sendEmptyMessage(1);
							}
							catch (Exception e)
							{
								han.sendEmptyMessage(2);
							}
						}
						// TODO: Implement this method
					}
				}).start();
		}else{
			han.sendEmptyMessage(1);
		}
		// TODO: Implement this method
	}

	private void initView()
	{
		sp_jz=findViewById(R.id.sp_jz);
		sp_tv=findViewById(R.id.sp_tv);
		sp_li=findViewById(R.id.sp_li);
		
		sp_li.setOnClickListener(new OnClickListener(){

				@Override
				public void onClick(View p1)
				{
					join();
					// TODO: Implement this method
				}
			});
		
		// TODO: Implement this method
	}

	private void login()
	{
		
		String name=UserManagement.getUserName();
		String password =UserManagement.getUserPassword();
		if (name != null && password != null)
		{
			try
			{			
				su.login(name, password);			
			}catch (Exception e){
				Log.e("登录失败","登录失败"+e);
				Message me=new Message();
				me.obj=e;
				me.what=0;
				han.sendMessage(me);
			}
		}else{
			han.sendEmptyMessage(5);
		}
		
		// TODO: Implement this method
	}
	
	Handler han=new Handler(){

		@Override
		public void handleMessage(Message msg)
		{
			// TODO: Implement this method
			super.handleMessage(msg);
			switch(msg.what){
				case 0:
					su.setIsConnected(false);
					sp_jz.setVisibility(View.GONE);
					sp_tv.setText("登录失败,点击重新登录");
					Util.show("登录失败,原因为"+msg.obj);
					break;
				case 1:
					startActivity(new Intent(SplashActivity.this,McchatActivity.class));
					finish();
					break;
				case 2:
					su.setIsListener(false);
					sp_jz.setVisibility(View.GONE);
					sp_tv.setText("加入聊天室失败,点击重新加入");
					break;
				case 3:
					sp_jz.setVisibility(View.VISIBLE);
					sp_tv.setText("正在加入聊天室,请稍等");
					break;
				case 4:
					sp_jz.setVisibility(View.VISIBLE);
					sp_tv.setText("登录中,请稍等");
					break;
				case 5:
					/*sp_jz.setVisibility(View.GONE);
					sp_tv.setText("用户名或密码为空");*/
					startActivity(new Intent(SplashActivity.this,MyCardActivity.class));
					finish();
					
					break;
			}
		}
		
		
	};
}
