package cn.garymb.ygomobile.ui.plus;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import java.util.*;
import android.support.v7.widget.*;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.ServerListAdapter;


public class ServiceDuelAssistant extends Service
{
	private LinearLayout mFloatLayout;
	private TextView ds_text;
	private Button ds_join,ds_qx;
	
	//是否可以移除悬浮窗上面的视图
	private boolean isdis=false;
	String[] passwordPrefix={"M,","T,","PR,","M#","T#","PR#","S#"};
	
	//private List<Card> lc;
	
	private WindowManager.LayoutParams wmParams;
	private WindowManager mWindowManager;
	
	//private CardListRecyclerViewAdapter cladp;
	//private DialogUtils du;
	
	@Override
	public IBinder onBind(Intent p1)
	{
		// TODO: Implement this method
		return null;
	}

	@Override
	public void onCreate() {
		// TODO: Implement this method
		super.onCreate();

		//lc = new ArrayList<Card>();
		//cladp = new CardListRecyclerViewAdapter(this, lc);
		//	du = DialogUtils.getdx(this);

		final ClipboardManager cm=(ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
		createFloatView();
		cm.addPrimaryClipChangedListener(new  ClipboardManager.OnPrimaryClipChangedListener() {


				@Override  
				public void onPrimaryClipChanged() {  
					final String ss=cm.getText().toString().trim();
					/*final int ssi=ss.indexOf("卡查");
					if (ssi != -1) {
						cxCard(ss, ssi);
					} else {*/
						int start=-1;
								
						for (String st:passwordPrefix) {
							start = ss.indexOf(st);
							if (start != -1) {				
								break;			
							}
						}		

						if (start != -1) {
							joinRoom(ss, start);
						}
					//}
				}


			});  


	}
	
	private void joinRoom(String ss, int start) {
			final String password = ss.substring(start, ss.length());
			ds_text.setText("加入房间\"" + password + "\"");
			ds_join.setText("加入");
			ds_qx.setText("关闭");
			disJoinDialog();
			showJoinDialog();
			new Handler().postDelayed(new Runnable() {

					@Override
					public void run() {
						if (isdis) {
							isdis = false;
							mWindowManager.removeView(mFloatLayout);
						}
					}
				}, 3000);

			ds_qx.setOnClickListener(new OnClickListener(){

					@Override
					public void onClick(View p1) {
						disJoinDialog();
					}
				});
			ds_join.setOnClickListener(new OnClickListener(){

					@Override
					public void onClick(View p1) {
						if (isdis) {
							isdis = false;
							mWindowManager.removeView(mFloatLayout);				
						}
						ServerInfo serverInfo=new ServerListAdapter(ServiceDuelAssistant.this).getItem(0);

						duelIntent(ServiceDuelAssistant.this,serverInfo.getServerAddr(),serverInfo.getPort(),serverInfo.getPlayerName(),password);
					}
				});

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
	
	private void disJoinDialog() {
		if (isdis) {
			isdis = false;
			mWindowManager.removeView(mFloatLayout);
		}
	}
	
	private void showJoinDialog(){
		if(!isdis){
		mWindowManager.addView(mFloatLayout, wmParams);		
		isdis = true;
		}
	}
	
	private void createFloatView() {
		wmParams = new WindowManager.LayoutParams();
		//获取的是WindowManagerImpl.CompatModeWrapper
		mWindowManager = (WindowManager)getApplication().getSystemService(getApplication().WINDOW_SERVICE);
		//设置window type
		wmParams.type = android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
		//设置背景透明
		wmParams.format = PixelFormat.TRANSLUCENT;
		//设置浮动窗口不可聚焦（实现操作除浮动窗口外的其他可见窗口的操作）
		//实现悬浮窗到状态栏
		wmParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
			WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
		//调整悬浮窗显示的停靠位置为左侧置顶
		wmParams.gravity = Gravity.LEFT | Gravity.TOP;
		// 以屏幕左上角为原点，设置x、y初始值，相对于gravity
		wmParams.x = 0;
		wmParams.y = 0;
		//设置悬浮窗口长宽数据
		wmParams.width = WindowManager.LayoutParams.MATCH_PARENT;
		wmParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

		LayoutInflater inflater = LayoutInflater.from(this);
		//获取浮动窗口视图所在布局
		mFloatLayout = (LinearLayout) inflater.inflate(R.layout.duel_assistant_service, null);
		//添加mFloatLayout
		ds_join = mFloatLayout.findViewById(R.id.ds_join);
		ds_text = mFloatLayout.findViewById(R.id.ds_text);
		ds_qx = mFloatLayout.findViewById(R.id.ds_qx);
	}
	
	/*private void cxCard(String ss, int ssi) {
	 final String card=ss.substring(ssi + 2, ss.length());

	 if (isdis) {
	 mWindowManager.removeView(mFloatLayout);	
	 }
	 ds_join.setText("查询");
	 ds_text.setText("查询卡片\"" + card + "\"");
	 ds_join.setOnClickListener(new OnClickListener(){

	 @Override
	 public void onClick(View p1) {

	 lc.clear();
	 for (Card c:DACardUtils.cardQuery(card)) {
	 lc.add(c);
	 }
	 cladp.notifyDataSetChanged();
	 LinearLayoutManager llm=new LinearLayoutManager(ServiceDuelAssistant.this);
	 du.dialogRec("\"" + card + "\"的搜索结果", cladp, llm);
	 // TODO: Implement this method
	 }
	 });
	 mWindowManager.addView(mFloatLayout, wmParams);		
	 isdis = true;
	 new Handler().postDelayed(new Runnable() {

	 @Override
	 public void run() {
	 // TODO Auto-generated method stub
	 if (isdis) {
	 isdis = false;
	 mWindowManager.removeView(mFloatLayout);
	 }
	 }
	 }, 2000);
	 // TODO: Implement this method
	 }*/
}
