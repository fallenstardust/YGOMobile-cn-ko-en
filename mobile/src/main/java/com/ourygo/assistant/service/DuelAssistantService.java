package com.ourygo.assistant.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import cn.garymb.ygomobile.ui.adapters.ServerListAdapter;
import cn.garymb.ygomobile.ui.cards.CardSearchAcitivity;
import cn.garymb.ygomobile.ui.cards.DeckManagerActivity;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.ui.home.MainActivity;
import cn.garymb.ygomobile.ui.home.ServerListManager;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.PermissionUtil;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;


public class DuelAssistantService extends Service {

    private final static String TAG = "DuelAssistantService";
    private static final String CHANNEL_ID = "YGOMobile";
    private static final String CHANNEL_NAME = "Duel_Assistant";
    private final static String DUEL_ASSISTANT_SERVICE_ACTION = "YGOMOBILE:ACTION_DUEL_ASSISTANT_SERVICE";
    private final static String CMD_NAME = "CMD";
    private final static String CMD_START_GAME = "CMD : START GAME";
    private final static String CMD_STOP_SERVICE = "CMD : STOP SERVICE";
    private final static String DECK_URL_PREFIX = Constants.SCHEME_APP+"://"+Constants.URI_HOST;
    //悬浮窗显示的时间
    private static final int TIME_DIS_WINDOW = 3000;

    //卡查关键字
    public static final String[] cardSearchKey = new String[]{"?", "？"};
    //加房关键字
    public static final String[] passwordPrefix = {
            "M,", "m,",
            "T,",
            "PR,", "pr,",
            "AI,", "ai,",
            "LF2,", "lf2,",
            "M#", "m#",
            "T#", "t#",
            "PR#", "pr#",
            "NS#", "ns#",
            "S#", "s#",
            "AI#", "ai#",
            "LF2#", "lf2#",
            "R#", "r#"
    };

    //卡查内容
    public static String cardSearchMessage = "";
    //卡组复制
    public static final String[] DeckTextKey = new String[]{"#main"};
    public static String DeckText = "";

    //悬浮窗布局View
    private View mFloatLayout;
    private TextView tv_message;
    private Button bt_join, bt_close;

    //是否可以移除悬浮窗上面的视图
    private boolean isdis = false;

    private WindowManager.LayoutParams wmParams;
    private WindowManager mWindowManager;
    private ClipboardManager cm;

    @Override
    public IBinder onBind(Intent p1) {
        // TODO: Implement this method
        return null;
    }

    @Override
    public void onCreate() {
        // TODO: Implement this method
        super.onCreate();
        //开启服务
        startForeground();
        //初始化加房布局
        createFloatView();
        //开启剪贴板监听
        startClipboardListener();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //移除剪贴板监听
        cm.removePrimaryClipChangedListener(onPrimaryClipChangedListener);
        //关闭悬浮窗时的声明
        stopForeground(true);
    }

    private void startClipboardListener() {
        cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null)
            return;
        cm.addPrimaryClipChangedListener(onPrimaryClipChangedListener);
    }

    ClipboardManager.OnPrimaryClipChangedListener onPrimaryClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {

        @Override
        public void onPrimaryClipChanged() {
            ClipData clipData = cm.getPrimaryClip();
            if (clipData == null)
                return;
            CharSequence cs = clipData.getItemAt(0).getText();
            final String clipMessage;
            if (cs != null) {
                clipMessage = cs.toString();
            } else {
                clipMessage = null;
            }

            //如果复制的内容为空则不执行下面的代码
            if (TextUtils.isEmpty(clipMessage)) {
                return;
            }
            //如果复制的内容是多行作为卡组去判断
            if (clipMessage.contains("\n")) {
                for (String s : DeckTextKey) {
                    //只要包含其中一个关键字就视为卡组
                    if (clipMessage.contains(s)) {
                        saveDeck(clipMessage, false);
                        return;
                    }
                }
                return;
            }
            //如果是卡组url
            int deckStart = clipMessage.indexOf(DECK_URL_PREFIX);
            if (deckStart != -1) {
                saveDeck(clipMessage.substring(deckStart + DECK_URL_PREFIX.length(), clipMessage.length()), true);
                return;
            }

            int start = -1;
            int end = -1;
            String passwordPrefixKey = null;
            for (String s : passwordPrefix) {
                start = clipMessage.indexOf(s);
                passwordPrefixKey = s;
                if (start != -1) {
                    break;
                }
            }

            if (start != -1) {
                //如果密码含有空格，则以空格结尾
                end = clipMessage.indexOf(" ", start);
                //如果不含有空格则取片尾所有
                if (end == -1) {
                    end = clipMessage.length();
                } else {
                    //如果只有密码前缀而没有密码内容则不跳转
                    if (end - start == passwordPrefixKey.length())
                        return;
                }
                //如果有悬浮窗权限再显示
                if (PermissionUtil.isServicePermission(DuelAssistantService.this, false))
                    joinRoom(clipMessage, start, end);
            } else {
                for (String s : cardSearchKey) {
                    int cardSearchStart = clipMessage.indexOf(s);
                    if (cardSearchStart != -1) {
                        //卡查内容
                        cardSearchMessage = clipMessage.substring(cardSearchStart + s.length(), clipMessage.length());
                        //如果复制的文本里带？号后面没有内容则不跳转
                        if (TextUtils.isEmpty(cardSearchMessage)) {
                            return;
                        }
                        //如果卡查内容包含“=”并且复制的内容包含“.”不卡查
                        if (cardSearchMessage.contains("=") && clipMessage.contains(".")) {
                            return;
                        }
                        Intent intent = new Intent(DuelAssistantService.this, CardSearchAcitivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra(CardSearchAcitivity.SEARCH_MESSAGE, cardSearchMessage);
                        startActivity(intent);
                    }
                }
            }
        }
    };

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

                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT);
                channel.setSound(null, null);
                channel.enableLights(false);

                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                manager.createNotificationChannel(channel);

                Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
                builder.setSmallIcon(R.drawable.ic_icon);
                builder.setSound(null);
                builder.setCustomContentView(remoteViews);
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

        return super.onStartCommand(intent, flags, startId);
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
                if (isUrl) {
                    Deck deckInfo = new Deck(getString(R.string.rename_deck) + System.currentTimeMillis(), Uri.parse(deckMessage));
                    File file = deckInfo.saveTemp(AppsSettings.get().getDeckDir());
                    Intent startdeck = new Intent(DuelAssistantService.this, DeckManagerActivity.getDeckManager());
                    startdeck.putExtra(Intent.EXTRA_TEXT, file.getAbsolutePath());
                    startdeck.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(startdeck);
                } else {
                    //如果是卡组文本
                    try {
                        //以当前时间戳作为卡组名保存卡组
                        File file = DeckUtils.save(getString(R.string.rename_deck) + System.currentTimeMillis(), deckMessage);
                        Intent startdeck = new Intent(DuelAssistantService.this, DeckManagerActivity.getDeckManager());
                        startdeck.putExtra(Intent.EXTRA_TEXT, file.getAbsolutePath());
                        startdeck.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(startdeck);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(DuelAssistantService.this, getString(R.string.save_failed_bcos) + e, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

    }

    private void joinRoom(String ss, int start, int end) {
        final String password = ss.substring(start, ss.length());
        tv_message.setText(getString(R.string.quick_join) + password + "\"");
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

                        duelIntent(DuelAssistantService.this, serverInfo.getServerAddr(), serverInfo.getPort(), serverInfo.getPlayerName(), password);

                    }
                });

            }
        });

    }

    //决斗跳转
    public static void duelIntent(Context context, String ip, int dk, String name, String password) {
        Intent intent1 = new Intent("ygomobile.intent.action.GAME");
        intent1.putExtra("host", ip);
        intent1.putExtra("port", dk);
        intent1.putExtra("user", name);
        intent1.putExtra("room", password);
        //intent1.setPackage("cn.garymb.ygomobile");
        intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent1);
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

}
