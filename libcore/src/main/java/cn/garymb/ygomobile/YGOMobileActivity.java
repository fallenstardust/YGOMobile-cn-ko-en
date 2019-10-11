/*
 * YGOMobileActivity.java
 *
 *  Created on: 2014年2月24日
 *      Author: mabin
 */
package cn.garymb.ygomobile;

import android.app.NativeActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.controller.NetworkController;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lib.R;
import cn.garymb.ygomobile.utils.SignUtils;
import cn.garymb.ygomobile.widget.ComboBoxCompat;
import cn.garymb.ygomobile.widget.EditWindowCompat;
import cn.garymb.ygomobile.widget.overlay.OverlayOvalView;
import cn.garymb.ygomobile.widget.overlay.OverlayView;

import static cn.garymb.ygomobile.core.IrrlichtBridge.ACTION_START;
import static cn.garymb.ygomobile.core.IrrlichtBridge.ACTION_STOP;

/**
 * @author mabin
 */
public class YGOMobileActivity extends NativeActivity implements
        IrrlichtBridge.IrrlichtHost,
        View.OnClickListener,
        PopupWindow.OnDismissListener,
        TextView.OnEditorActionListener,
        OverlayOvalView.OnDuelOptionsSelectListener {
    private static final String TAG = YGOMobileActivity.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final int CHAIN_CONTROL_PANEL_X_POSITION_LEFT_EDGE = 205;
    private static final int CHAIN_CONTROL_PANEL_Y_REVERT_POSITION = 100;
    private static final int MAX_REFRESH = 30 * 1000;
    /**
     * 沉浸全屏模式
     */
    private static final int windowsFlags;
    /**
     * 非沉浸全屏模式
     */
    private static final int windowsFlags2;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            windowsFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE/* 系统UI变化不触发relayout */
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION/* 导航栏悬浮在布局上面 */
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN/* 状态栏悬浮在布局上面 */
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION/* 隐藏导航栏 */
                    | View.SYSTEM_UI_FLAG_FULLSCREEN/* 隐藏状态栏 */
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY/* 沉浸模式 */;
            windowsFlags2 = View.SYSTEM_UI_FLAG_LAYOUT_STABLE/* 系统UI变化不触发relayout */
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION/* 隐藏导航栏 */
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION/* 隐藏状态栏 */
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
        } else {
            windowsFlags = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LOW_PROFILE;
            windowsFlags2 = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LOW_PROFILE;
        }
    }

    protected View mContentView;
    protected ComboBoxCompat mGlobalComboBox;
    protected EditWindowCompat mGlobalEditText;
    private volatile long lastRefresh;
    //    private OverlayRectView mChainOverlayView;
//    private OverlayOvalView mOverlayView;
    private NetworkController mNetController;
    private volatile boolean mOverlayShowRequest = false;
    private volatile int mCompatGUIMode;
    private static int sChainControlXPostion = -1;
    private static int sChainControlYPostion = -1;
    private GameApplication mApp;
    private Handler handler = new Handler();
    private volatile int mPositionX, mPositionY;
    private FrameLayout mLayout;
    private SurfaceView mSurfaceView;
    private boolean replaced = false;
    private static boolean USE_SURFACE = true;
    private static boolean RESIZE_WINDOW = true;

//    public static int notchHeight;

    private GameApplication app() {
        if (mApp == null) {
            synchronized (this) {
                if (mApp == null) {
                    if (GameApplication.get() != null) {
                        mApp = GameApplication.get();
                    } else {
                        mApp = (GameApplication) getApplication();
                    }
                }
            }
        }
        return mApp;
    }

    @SuppressWarnings("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(USE_SURFACE) {
            mSurfaceView = new SurfaceView(this);
        }
        fullscreen();
        super.onCreate(savedInstanceState);
        Log.e("YGOStarter","跳转完成"+System.currentTimeMillis());
        if (sChainControlXPostion < 0) {
            initPostion();
        }
        if (app().isLockSreenOrientation()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        initExtraView();
        mPM = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mNetController = new NetworkController(getApplicationContext());
        handleExternalCommand(getIntent());
        sendBroadcast(new Intent(ACTION_START)
                .putExtra(IrrlichtBridge.EXTRA_PID, android.os.Process.myPid())
                .setPackage(getPackageName()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        fullscreen();
                    }
                }
            });
        }
    }

    //电池管理
    private PowerManager mPM;
    private PowerManager.WakeLock mLock;

    @Override
    protected void onResume() {
        super.onResume();
        Log.e("YGOStarter","ygo显示"+System.currentTimeMillis());
        if (mLock == null) {
            if (mPM == null) {
                mPM = (PowerManager) getSystemService(POWER_SERVICE);
            }
            mLock = mPM.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        }
        mLock.acquire();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mLock != null) {
            if (mLock.isHeld()) {
                mLock.release();
            }
        }
    }

    private void refreshTextures() {
        if (System.currentTimeMillis() - lastRefresh >= MAX_REFRESH) {
            lastRefresh = System.currentTimeMillis();
            Toast.makeText(this, R.string.refresh_textures, Toast.LENGTH_SHORT).show();
            IrrlichtBridge.refreshTexture();
        }
    }

    private void initPostion() {
        final Resources res = getResources();
        sChainControlXPostion = (int) (CHAIN_CONTROL_PANEL_X_POSITION_LEFT_EDGE * app()
                .getXScale());
        sChainControlYPostion = (int) (app().getSmallerSize()
                - CHAIN_CONTROL_PANEL_Y_REVERT_POSITION
                * app().getYScale() - (res
                .getDimensionPixelSize(R.dimen.chain_control_button_height) * 2 + res
                .getDimensionPixelSize(R.dimen.chain_control_margin)));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleExternalCommand(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        sendBroadcast(new Intent(ACTION_STOP)
                .putExtra(IrrlichtBridge.EXTRA_PID, android.os.Process.myPid())
                .setPackage(getPackageName()));
    }

    private void handleExternalCommand(Intent intent) {
        YGOGameOptions options = intent
                .getParcelableExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY);
        long time = intent.getLongExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_TIME, 0);
        if (System.currentTimeMillis() - time >= YGOGameOptions.TIME_OUT) {
            if (DEBUG)
                Log.i(TAG, "command timeout");
            return;
        }
        if (options != null) {
            if (DEBUG)
                Log.i(TAG, "receive:" + time + ":" + options.toString());
            ByteBuffer buffer = options.toByteBuffer();
            IrrlichtBridge.joinGame(buffer, buffer.position());
        } else {
            if (DEBUG)
                Log.i(TAG, "receive :null");
        }
    }

    private void fullscreen() {
        if (app().isImmerSiveMode()) {
            //沉浸模式
            getWindow().getDecorView().setSystemUiVisibility(windowsFlags);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(windowsFlags2);
        }
        app().attachGame(this);
        if (USE_SURFACE) {
            changeGameSize();
        } else {
            int[] size = getGameSize();
            if(RESIZE_WINDOW) {
                if (app().isKeepScale()) {
                    getWindow().setLayout(size[0], size[1]);
                }
            }
        }
    }

    private int[] getGameSize(){
        //调整padding
        float xScale = app().getXScale();
        float yScale = app().getYScale();
        int w = (int) (app().getGameWidth() * xScale);
        int h = (int) (app().getGameHeight() * yScale);
        Log.i("kk", "w1=" + app().getGameWidth() + ",h1=" + app().getGameHeight() + ",w2=" + w + ",h2=" + h + ",xScale=" + xScale + ",yScale=" + yScale);
        return new int[]{w, h};
    }

    @Override
    public int getPositionX() {
        synchronized (this) {
            return mPositionX;
        }
    }

    @Override
    public int getPositionY() {
        synchronized (this) {
            return mPositionY;
        }
    }

    @Override
    public void setContentView(View view) {
        int[] size = getGameSize();
        int w = size[0];
        int h = size[1];
        mLayout = new FrameLayout(this);
//        mLayout.setFitsSystemWindows(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.gravity = Gravity.CENTER;
        if (USE_SURFACE) {
            mLayout.addView(mSurfaceView, lp);
            mLayout.addView(view, lp);
            super.setContentView(mLayout);
            app().attachGame(this);
            getWindow().takeSurface(null);
            replaced = true;
            mSurfaceView.getHolder().addCallback(this);
            mSurfaceView.requestFocus();
            getWindow().setGravity(Gravity.CENTER);
            changeGameSize();
        } else {
            mLayout.addView(view, lp);
            if (RESIZE_WINDOW) {
                getWindow().setLayout(w, h);
                getWindow().setGravity(Gravity.CENTER);
            }
            super.setContentView(mLayout);
        }
    }

    private void changeGameSize(){
        //游戏大小
        int[] size = getGameSize();
        int w = (int) app().getScreenHeight();
        int h = (int) app().getScreenWidth();
        int spX = (int) ((w - size[0]) / 2.0f);
        int spY = (int) ((h - size[1]) / 2.0f);
//        Log.i("ygo", "Android command 1:posX=" + spX + ",posY=" + spY);
        boolean update = false;
        synchronized (this) {
            if (spX != mPositionX || spY != mPositionY) {
                mPositionX = spX;
                mPositionY = spY;
                update = true;
            }
        }
        if (update) {
//            Log.i("ygo", "Android command setInputFix2:posX=" + spX + ",posY=" + spY);
            IrrlichtBridge.setInputFix(mPositionX, mPositionY);
        }
        if(RESIZE_WINDOW) {
            if (app().isKeepScale()) {
                //设置为屏幕宽高
                getWindow().setLayout(w, h);
            } else {
                //拉伸，画布设置为游戏宽高
                getWindow().setLayout(size[0], size[1]);
            }
        }
    }

    private void initExtraView() {
        mContentView = getWindow().getDecorView().findViewById(android.R.id.content);
        mGlobalComboBox = new ComboBoxCompat(this);
        mGlobalComboBox.setButtonListener(this);
        mGlobalEditText = new EditWindowCompat(this);
        mGlobalEditText.setEditActionListener(this);
        mGlobalEditText.setOnDismissListener(this);

//        mChainOverlayView = new OverlayRectView(this);
//        mOverlayView = new OverlayOvalView(this);
//        mChainOverlayView.setDuelOpsListener(this);
//        mOverlayView.setDuelOpsListener(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
//        Log.e("YGOMobileActivity","窗口变化"+hasFocus);
        if (hasFocus) {
            fullscreen();
            mContentView.setHapticFeedbackEnabled(true);
        } else {
            mContentView.setHapticFeedbackEnabled(false);
        }
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public void onDismiss() {
        if (mOverlayShowRequest) {
//            mOverlayView.show();
//            mChainOverlayView.show();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.cancel) {
        } else if (v.getId() == R.id.submit) {
            int idx = mGlobalComboBox.getCurrentSelection();
            if (DEBUG)
                Log.d(TAG, "showComboBoxCompat: receive selection: " + idx);
            if (mCompatGUIMode == ComboBoxCompat.COMPAT_GUI_MODE_COMBOBOX) {
                IrrlichtBridge.setComboBoxSelection(idx);
            } else if (mCompatGUIMode == ComboBoxCompat.COMPAT_GUI_MODE_CHECKBOXES_PANEL) {
                IrrlichtBridge.setCheckBoxesSelection(idx);
            }
        }
        mGlobalComboBox.dismiss();
    }

    @Override
    public void onDuelOptionsSelected(int mode, boolean action) {
        switch (mode) {
            case OverlayView.MODE_CANCEL_CHAIN_OPTIONS:
                if (DEBUG)
                    Log.d(TAG, "Constants.MODE_CANCEL_CHAIN_OPTIONS: " + action);
                IrrlichtBridge.cancelChain();
                break;
            case OverlayView.MODE_REFRESH_OPTION:
                if (DEBUG)
                    Log.d(TAG, "Constants.MODE_REFRESH_OPTION: " + action);
                IrrlichtBridge.refreshTexture();
                break;
            case OverlayView.MODE_REACT_CHAIN_OPTION:
                if (DEBUG)
                    Log.d(TAG, "Constants.MODE_REACT_CHAIN_OPTION: " + action);
                IrrlichtBridge.reactChain(action);
                break;
            case OverlayView.MODE_IGNORE_CHAIN_OPTION:
                if (DEBUG)
                    Log.d(TAG, "Constants.MODE_IGNORE_CHAIN_OPTION: " + action);
                IrrlichtBridge.ignoreChain(action);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        final String text = v.getText().toString();
        IrrlichtBridge.insertText(text);
        mGlobalEditText.dismiss();
        return false;
    }

    ///////////////////C++

    @Override
    public void toggleOverlayView(final boolean isShow) {
        if (mOverlayShowRequest != isShow) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mOverlayShowRequest = isShow;
                }
            });
        }
    }

    @Override
    public ByteBuffer getInitOptions() {
        return getNativeInitOptions();
    }

    @Override
    public ByteBuffer getNativeInitOptions() {
        NativeInitOptions options = app().getNativeInitOptions();
        return options.toNativeBuffer();
    }

    @Override
    public void toggleIME(final String hint, final boolean isShow) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isShow) {
//                if (mOverlayShowRequest) {
//                    mOverlayView.hide();
//                    mChainOverlayView.hide();
//                }
                    mGlobalEditText.fillContent(hint);
                    mGlobalEditText.showAtLocation(mContentView,
                            Gravity.BOTTOM, 0, 0);
                } else {
                    mGlobalEditText.dismiss();
                }
            }
        });
    }

    @Override
    public void showComboBoxCompat(final String[] items, final boolean isShow, final int mode) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                mCompatGUIMode = mode;
                if (DEBUG)
                    Log.i(TAG, "showComboBoxCompat： isShow = " + isShow);
                if (isShow) {
                    mGlobalComboBox.fillContent(items);
                    mGlobalComboBox.showAtLocation(mContentView,
                            Gravity.BOTTOM, 0, 0);
                }
            }
        });
    }

    @Override
    public void performHapticFeedback() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                mContentView.performHapticFeedback(
                        HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
        });
    }

    @Override
    public byte[] performTrick() {
        return SignUtils.getSignInfo(this);
    }

    @Override
    public int getLocalAddress() {
        return mNetController.getIPAddress();
    }

    @Override
    public void setNativeHandle(int nativeHandle) {
        IrrlichtBridge.sNativeHandle = nativeHandle;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if(USE_SURFACE) {
            if (!replaced) {
                return;
            }
        }
        super.surfaceCreated(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if(USE_SURFACE) {
            if (!replaced) {
                return;
            }
        }
        super.surfaceChanged(holder, format, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if(USE_SURFACE) {
            if (!replaced) {
                return;
            }
        }
        super.surfaceDestroyed(holder);
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
        if(USE_SURFACE) {
            if (!replaced) {
                return;
            }
        }
        super.surfaceRedrawNeeded(holder);
    }
}
