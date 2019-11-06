package cn.garymb.ygomobile.ourygo.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.ServerList;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ourygo.base.OnDuelClipBoardListener;
import cn.garymb.ygomobile.ourygo.util.DuelAssistantManagement;
import cn.garymb.ygomobile.ui.adapters.ServerListAdapter;
import cn.garymb.ygomobile.ui.cards.CardSearchAcitivity;
import cn.garymb.ygomobile.ui.home.MainActivity;
import cn.garymb.ygomobile.ui.home.ServerListManager;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.PermissionUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;


public class DuelAssistantService extends Service implements OnDuelClipBoardListener {

    private final static String TAG = "DuelAssistantService";
    private static final String CHANNEL_ID = "YGOMobile";
    private static final String CHANNEL_NAME = "Duel_Assistant";
    private final static String DUEL_ASSISTANT_SERVICE_ACTION = "YGOMOBILE:ACTION_DUEL_ASSISTANT_SERVICE";
    private final static String CMD_NAME = "CMD";
    private final static String CMD_START_GAME = "CMD : START GAME";
    private final static String CMD_STOP_SERVICE = "CMD : STOP SERVICE";

    //悬浮窗显示的时间
    private static final int TIME_DIS_WINDOW = 3000;

    //悬浮窗布局View
    private View mFloatLayout;
    private TextView tv_message;
    private Button bt_join, bt_close;

    //是否可以移除悬浮窗上面的视图
    private boolean isdis = false;

    private WindowManager.LayoutParams wmParams;
    private WindowManager mWindowManager;
    private DuelAssistantManagement duelAssistantManagement;

    @Override
    public IBinder onBind(Intent p1) {
        // TODO: Implement this method
        return null;
    }

    @Override
    public void onCreate() {
        // TODO: Implement this method
        super.onCreate();
        Log.e("DuelAssistantService","开启服务");
        duelAssistantManagement = DuelAssistantManagement.getInstance();
        //开启服务
        startForeground();
        //初始化加房布局
        createFloatView();
        duelAssistantManagement.addListener(this);


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
      duelAssistantManagement.removeListener(this);
        //关闭悬浮窗时的声明
        stopForeground(true);
        duelAssistantManagement.setStart(false);
    }

    private void startForeground() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            if (PermissionUtil.isNotificationListenerEnabled(this)) {
                RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.notification_view_duel_assistant);
                Intent intent = new Intent(this, this.getClass());
                intent.setAction(DUEL_ASSISTANT_SERVICE_ACTION);
                PendingIntent pendingIntent;

                intent.putExtra(CMD_NAME, CMD_START_GAME);
                pendingIntent = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                remoteViews.setOnClickPendingIntent(R.id.notification_view_duel_assistant, pendingIntent);

                intent.putExtra(CMD_NAME, CMD_STOP_SERVICE);
                pendingIntent = PendingIntent.getService(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                remoteViews.setOnClickPendingIntent(R.id.buttonStopService, pendingIntent);

                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
                if (channel != null) {
                    if (channel.getLockscreenVisibility() != Notification.VISIBILITY_SECRET) {
                        manager.deleteNotificationChannel(CHANNEL_ID);
                        channel = null;
                    }
                }
                if (channel == null) {
                    channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_LOW);
                    channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
                    channel.setSound(null, null);
                    channel.setShowBadge(false);
                    channel.enableLights(false);
                    manager.createNotificationChannel(channel);
                }

                Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
                builder.setSmallIcon(R.drawable.ic_icon);
                builder.setSound(null);
                builder.setCustomContentView(remoteViews);
                builder.setVisibility(Notification.VISIBILITY_SECRET);
                startForeground(1, builder.build());
            } else {
                //如果没有通知权限则关闭服务
                stopForeground(true);
                stopService(new Intent(DuelAssistantService.this, DuelAssistantService.class));
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return super.onStartCommand(intent, flags, startId);
        String action = intent.getAction();
        Log.d(TAG, "rev action:" + action);
        if (DUEL_ASSISTANT_SERVICE_ACTION.equals(action)) {
            String cmd = intent.getStringExtra(CMD_NAME);
            Log.d(TAG, "rev cmd:" + cmd);

            if (null == cmd) {
                Log.e(TAG, "cmd null");
            } else {
                switch (cmd) {
                    case CMD_STOP_SERVICE:
                        stopSelf();
                        break;

                    case CMD_START_GAME:
                        Intent intent2 = new Intent(DuelAssistantService.this, MainActivity.class);
                        intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent2);
                        break;

                    default:
                        Log.e(TAG, "unknown cmd:" + cmd);
                        break;

                }
                collapseStatusBar();
            }
        }

        return START_STICKY;//super.onStartCommand(intent, flags, startId);
    }

    private void collapseStatusBar() {
        try {
            @SuppressLint("WrongConstant") Object statusBarManager = getSystemService("statusbar");
            if (null == statusBarManager) {
                return;
            }
            Class<?> clazz = statusBarManager.getClass();
            if (null == clazz) {
                return;
            }

            Method methodCollapse;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
                methodCollapse = clazz.getMethod("collapse");
            } else {
                methodCollapse = clazz.getMethod("collapsePanels");
            }
            if (null == methodCollapse) {
                return;
            }
            methodCollapse.invoke(statusBarManager);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveDeck(String deckMessage, boolean isUrl) {
        tv_message.setText(R.string.find_deck_text);
        bt_close.setText(R.string.search_close);
        bt_join.setText(R.string.save_n_open);
        disJoinDialog();
        showJoinDialog();
        new Handler().postDelayed(() -> {
            if (isdis) {
                isdis = false;
                mWindowManager.removeView(mFloatLayout);
            }
        }, TIME_DIS_WINDOW);
        bt_close.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                disJoinDialog();
            }
        });
        bt_join.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                disJoinDialog();
                //如果是卡组url
                DeckUtil.saveDeck(DuelAssistantService.this,deckMessage,isUrl);
            }
        });

    }

    private void joinRoom(String password) {
        tv_message.setText(YGOUtil.s(R.string.quick_join) + password + "\"");
        bt_join.setText(R.string.join);
        bt_close.setText(R.string.search_close);
        disJoinDialog();
        showJoinDialog();
        new Handler().postDelayed(() -> {
            if (isdis) {
                isdis = false;
                mWindowManager.removeView(mFloatLayout);
            }
        }, TIME_DIS_WINDOW);

        bt_close.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View p1) {
                disJoinDialog();
            }
        });
        bt_join.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View p1) {
                if (isdis) {
                    isdis = false;
                    mWindowManager.removeView(mFloatLayout);
                }
                ServerListAdapter mServerListAdapter = new ServerListAdapter(DuelAssistantService.this);

                ServerListManager mServerListManager = new ServerListManager(DuelAssistantService.this, mServerListAdapter);
                mServerListManager.syncLoadData();

                File xmlFile = new File(getFilesDir(), Constants.SERVER_FILE);
                VUiKit.defer().when(() -> {
                    ServerList assetList = ServerListManager.readList(DuelAssistantService.this.getAssets().open(ASSET_SERVER_LIST));
                    ServerList fileList = xmlFile.exists() ? ServerListManager.readList(new FileInputStream(xmlFile)) : null;
                    if (fileList == null) {
                        return assetList;
                    }
                    if (fileList.getVercode() < assetList.getVercode()) {
                        xmlFile.delete();
                        return assetList;
                    }
                    return fileList;
                }).done((list) -> {
                    if (list != null) {
                        ServerInfo serverInfo = list.getServerInfoList().get(0);
                        YGOUtil.duelIntent(DuelAssistantService.this, serverInfo.getServerAddr(), serverInfo.getPort(), serverInfo.getPlayerName(), password);
                    }
                });
            }
        });

    }

    private void disJoinDialog() {
        if (isdis) {
            isdis = false;
            mWindowManager.removeView(mFloatLayout);
        }
    }

    private void showJoinDialog() {
        if (!isdis) {
            mWindowManager.addView(mFloatLayout, wmParams);
            isdis = true;
        }
    }

    private void createFloatView() {
        wmParams = new WindowManager.LayoutParams();
        //获取的是WindowManagerImpl.CompatModeWrapper
        mWindowManager = (WindowManager) getApplication().getSystemService(WINDOW_SERVICE);
        //设置window type
        wmParams.type = android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        //设置背景透明
        wmParams.format = PixelFormat.TRANSLUCENT;
        //设置浮动窗口不可聚焦（实现操作除浮动窗口外的其他可见窗口的操作）
        //实现悬浮窗到状态栏
        wmParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        //安卓7.0要求
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wmParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        //安卓8.0要求
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wmParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        //调整悬浮窗显示的停靠位置为左侧置顶
        wmParams.gravity = Gravity.START | Gravity.TOP;
        // 以屏幕左上角为原点，设置x、y初始值，相对于gravity
        wmParams.x = 0;
        wmParams.y = 0;
        //设置悬浮窗口长宽数据
        wmParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        wmParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        //获取浮动窗口视图所在布局
        mFloatLayout = LayoutInflater.from(this).inflate(R.layout.duel_assistant_service, null);
        //添加mFloatLayout
        bt_join = mFloatLayout.findViewById(R.id.bt_join);
        tv_message = mFloatLayout.findViewById(R.id.tv_message);
        bt_close = mFloatLayout.findViewById(R.id.bt_close);
    }

    @Override
    public void onDeckCode(String deckCode,boolean isDebounce) {
        saveDeck(deckCode, false);
    }

    @Override
    public void onDeckUrl(String deckUrl,boolean isDebounce) {
        saveDeck(deckUrl, true);
    }

    @Override
    public void onCardQuery(String cardSearchMessage,boolean isDebounce) {
        Log.e("DuelAssistantService","卡查监听"+cardSearchMessage);
        Intent intent = new Intent(DuelAssistantService.this, CardSearchAcitivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(CardSearchAcitivity.SEARCH_MESSAGE, cardSearchMessage);
        startActivity(intent);
    }

    @Override
    public void onDuelPassword(String password,boolean isDebounce) {
        //如果有悬浮窗权限再显示
        if (PermissionUtil.isServicePermission(DuelAssistantService.this, false))
            joinRoom(password);
    }

    @Override
    public boolean isEffective() {
        Log.e("DuelAssistantService","有效性"+YGOUtil.isContextExisted(this));
        return YGOUtil.isContextExisted(this);
    }
}
