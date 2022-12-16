/*
 * YGOMobileActivity.java
 *
 *  Created on: 2014年2月24日
 *      Author: mabin
 */
package cn.garymb.ygomobile;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Arrays;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.controller.NetworkController;
import cn.garymb.ygomobile.core.GameActivity;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lib.R;
import cn.garymb.ygomobile.utils.FullScreenUtils;
import cn.garymb.ygomobile.utils.SignUtils;
import cn.garymb.ygomobile.widget.ComboBoxCompat;
import cn.garymb.ygomobile.widget.EditWindowCompat;
import cn.garymb.ygomobile.widget.overlay.OverlayOvalView;
import cn.garymb.ygomobile.widget.overlay.OverlayView;

import static cn.garymb.ygomobile.core.IrrlichtBridge.ACTION_SHARE_FILE;

/**
 * @author mabin
 */
public class YGOMobileActivity extends GameActivity implements
        IrrlichtBridge.IrrlichtHost,
        View.OnClickListener,
        PopupWindow.OnDismissListener,
        TextView.OnEditorActionListener,
        OverlayOvalView.OnDuelOptionsSelectListener {
    private static final String TAG = YGOMobileActivity.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final int CHAIN_CONTROL_PANEL_X_POSITION_LEFT_EDGE = 205;
    private static final int CHAIN_CONTROL_PANEL_Y_REVERT_POSITION = 100;
    private static final int MAX_REFRESH = 30 * 1000;
    protected final int windowsFlags =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    protected View mContentView;
    protected ComboBoxCompat mGlobalComboBox;
    protected EditWindowCompat mGlobalEditText;
    private volatile long lastRefresh;
    //    private OverlayRectView mChainOverlayView;
//    private OverlayOvalView mOverlayView;
    private NetworkController mNetController;
    private volatile boolean mOverlayShowRequest = false;
    private volatile int mCompatGUIMode;
    //    private static int sChainControlXPostion = -1;
//    private static int sChainControlYPostion = -1;
    private GameApplication mApp;
    private FullScreenUtils mFullScreenUtils;
    private volatile int mPositionX, mPositionY;
    private String[] mArgV;
    private boolean onGameExiting;
    private static final boolean blockKey = false;


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

    @Override
    protected void initBeforeOnCreate() {
        mPositionX = 0;
        mPositionY = 0;
        mFullScreenUtils = new FullScreenUtils(this, app().isImmerSiveMode());
        mFullScreenUtils.fullscreen();
        mFullScreenUtils.onCreate();
        //argv
        mArgV = IrrlichtBridge.getArgs(getIntent());
        //
    }

    @Override
    protected void initAfterOnCreate() {
        Log.e("YGOStarter", "跳转完成" + System.currentTimeMillis());
//        if (sChainControlXPostion < 0) {
//            initPostion();
//        }
        if (app().isLockSreenOrientation()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        initExtraView();
        mPM = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mNetController = new NetworkController(getApplicationContext());
        handleExternalCommand(getIntent());
    }

    //电池管理
    private PowerManager mPM;
    private PowerManager.WakeLock mLock;

    @Override
    protected void onResume() {
        super.onResume();
        Log.e("YGOStarter", "ygo显示" + System.currentTimeMillis());
        if (mLock == null) {
            if (mPM == null) {
                mPM = (PowerManager) getSystemService(POWER_SERVICE);
            }
            mLock = mPM.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        }
        mLock.acquire();
        //注册
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleExternalCommand(intent);
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
        //如果是沉浸模式
        if (app().isImmerSiveMode()) {
            mFullScreenUtils.fullscreen();
            app().attachGame(this);
            if (USE_SURFACE) {
                changeGameSize();
            }
        }
    }

    @Override
    protected Size getGameWindowSize() {
        //调整padding
        float xScale = app().getXScale();
        float yScale = app().getYScale();
        float sw = app().getScreenWidth();
        float sh = app().getScreenHeight();
        int w = (int) (app().getGameWidth() * xScale);
        int h = (int) (app().getGameHeight() * yScale);
        Log.i(IrrlichtBridge.TAG, "game size=" + app().getGameWidth() + "x" + app().getGameHeight()
                + ", surface=" + w + "x" + h
                + ", screen=" + sw + "x" + sh
                + ", xScale=" + xScale + ",yScale=" + yScale);
        return new Size(w, h);
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
        super.setContentView(view);
        app().attachGame(this);
        changeGameSize();
        //可以通过mLayout.addView添加view，增加功能
        //test code
//        int size = (int) (getResources().getDisplayMetrics().density * 100);
//        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
//        lp.gravity = Gravity.RIGHT|Gravity.BOTTOM;
//        ImageView imageView = new ImageView(this);
//        imageView.setImageResource(android.R.drawable.sym_def_app_icon);
//        imageView.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                refreshTextures();
//            }
//        });
//        mLayout.addView(imageView, lp);
    }

    private void changeGameSize() {
        if (USE_MY_INPUT) {
            return;
        }
        //游戏大小
        Size size = getGameWindowSize();
        int w = (int) app().getScreenHeight();
        int h = (int) app().getScreenWidth();
        int spX = (int) ((w - size.getWidth()) / 2.0f);
        int spY = (int) ((h - size.getHeight()) / 2.0f);
        boolean update = false;
        synchronized (this) {
            if (spX != mPositionX || spY != mPositionY) {
                mPositionX = spX;
                mPositionY = spY;
                update = true;
            }
        }
        if (update) {
            IrrlichtBridge.setInputFix(mPositionX, mPositionY);
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
            runOnUiThread(new Runnable() {
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
        options.mArgvList.clear();
        if (mArgV != null) {
            options.mArgvList.addAll(Arrays.asList(mArgV));
            mArgV = null;
        }
        return options.toNativeBuffer();
    }

    @Override
    public void toggleIME(final String hint, final boolean isShow) {
        runOnUiThread(new Runnable() {
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
        runOnUiThread(new Runnable() {
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
        runOnUiThread(new Runnable() {
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
    public void setNativeHandle(long nativeHandle) {
        IrrlichtBridge.sNativeHandle = nativeHandle;
    }

    @Override
    public void shareFile(final String type, final String name) {
        //TODO 分享文件
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(ACTION_SHARE_FILE);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.putExtra(IrrlichtBridge.EXTRA_SHARE_TYPE, type);
                intent.putExtra(IrrlichtBridge.EXTRA_SHARE_FILE, name);
                intent.setPackage(getPackageName());
                try {
                    startActivity(intent);
                } catch (Throwable e) {
                    //ignore
                    Toast.makeText(YGOMobileActivity.this, "dev error:not found activity.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private long lasttime;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (blockKey && USE_MY_INPUT) {
            if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
                    && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
                sendInputEvent(event, false);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (blockKey && USE_MY_INPUT) {
            if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
                    && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
                sendInputEvent(event, false);
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (mGlobalComboBox != null && mGlobalComboBox.isShowing()) {
            mGlobalComboBox.dismiss();
            return;
        }
        if (mGlobalEditText != null && mGlobalEditText.isShowing()) {
            mGlobalEditText.dismiss();
            return;
        }
        if (lasttime == 0 || (System.currentTimeMillis() - lasttime) > 1000) {
            lasttime = System.currentTimeMillis();
            return;
        }
//        super.onWindowFocusChanged(false);
//        onGameExit();
    }

    @Override
    protected void onSurfaceTouch(View v, MotionEvent event) {
        if (event.getPointerCount() > 1) {
            //多指操作不处理
            return;
        }
        super.onSurfaceTouch(v, event);
    }

    @Override
    public void onGameExit() {
        if (onGameExiting) {
            return;
        }
        onGameExiting = true;
        Log.e(IrrlichtBridge.TAG, "game exit");
        final Intent intent = new Intent(IrrlichtBridge.ACTION_OPEN_GAME_HOME);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
//        intent.putExtra(IrrlichtBridge.EXTRA_PID, Process.myPid());
//        intent.putExtra(IrrlichtBridge.EXTRA_TASK_ID, getTaskId());
//        intent.putExtra(IrrlichtBridge.EXTRA_GAME_EXIT_TIME, System.currentTimeMillis());
        intent.setPackage(getPackageName());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    startActivity(intent);
                    Log.d(IrrlichtBridge.TAG, "open home ok");
                } catch (Throwable e) {
                    Log.w(IrrlichtBridge.TAG, "open home", e);
                }
                boolean isRoot = isTaskRoot();
                Log.d(IrrlichtBridge.TAG, "isRoot=" + isRoot + ",kill:" + Process.myPid());
                if (isRoot) {
                    finishAndRemoveTask();
                } else {
                    finish();
                }
                Process.killProcess(Process.myPid());
            }
        });
    }
}
