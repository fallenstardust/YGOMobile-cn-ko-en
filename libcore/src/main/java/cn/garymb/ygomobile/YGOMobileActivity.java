/*
 * YGOMobileActivity.java
 *
 *  Created on: 2014年2月24日
 *      Author: mabin
 */
package cn.garymb.ygomobile;

import android.annotation.SuppressLint;
import android.app.NativeActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputQueue;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
import cn.garymb.ygomobile.core.GameConfig;
import cn.garymb.ygomobile.core.GameHost;
import cn.garymb.ygomobile.core.GameHostWrapper;
import cn.garymb.ygomobile.core.YGOCore;
import cn.garymb.ygomobile.lib.R;
import cn.garymb.ygomobile.widget.ComboBoxCompat;
import cn.garymb.ygomobile.widget.EditWindowCompat;
import cn.garymb.ygomobile.widget.overlay.OverlayOvalView;
import cn.garymb.ygomobile.widget.overlay.OverlayView;

import static cn.garymb.ygomobile.core.YGOCore.ACTION_START;
import static cn.garymb.ygomobile.core.YGOCore.ACTION_STOP;

public class YGOMobileActivity extends NativeActivity implements
        YGOCore.IActivityHost,
        View.OnClickListener,
        PopupWindow.OnDismissListener,
        TextView.OnEditorActionListener,
        OverlayOvalView.OnDuelOptionsSelectListener {
    private static final String TAG = YGOMobileActivity.class.getSimpleName();
    private static final float GAME_WIDTH = 1024.0f;
    private static final float GAME_HEIGHT = 640.0f;
    private static final boolean DEBUG = true;

    private static final int MAX_REFRESH = 30 * 1000;

    //region flag
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
    //endregion

    protected View mContentView;
    protected ComboBoxCompat mGlobalComboBox;
    protected EditWindowCompat mGlobalEditText;
    private volatile long lastRefresh;

    private YGOCore mCore;
    private GameHost mHost;
    private GameConfig mGameConfig;
    private NetworkController mNetController;
    private volatile boolean mOverlayShowRequest = false;
    private volatile int mCompatGUIMode;
    //电池管理
    private PowerManager mPM;
    private PowerManager.WakeLock mLock;
    private volatile int mGameWidth, mGameHeight;
    private volatile int mActivityWidth, mActivityHeight;
    private FrameLayout mLayout;
    private SurfaceView mSurfaceView;
    private View mClickView;
    private boolean replaced = false;
    private boolean mInitView = false;

    private GameApplication app() {
        return GameApplication.get();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mGameConfig = app().getConfig();
        try {
            mCore = new YGOCore(this, mGameConfig.getGameAsset());
        } catch (Exception e) {
            Log.e(TAG, "init core", e);
        }
        mHost = new DefaultGameHost(this, app().getGameHost());
        fullscreen();
        if(mGameConfig.isEnableSoundEffect()){
            mHost.initSoundEffectPool();
        }
        super.onCreate(savedInstanceState);
        Log.e("YGOStarter", "跳转完成" + System.currentTimeMillis());
        if (mGameConfig.isLockScreenOrientation()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        initExtraView();
        mPM = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mNetController = new NetworkController(getApplicationContext());
        handleExternalCommand(getIntent());
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
        sendBroadcast(new Intent(ACTION_START)
                .putExtra(YGOCore.EXTRA_PID, android.os.Process.myPid())
                .setPackage(getPackageName()));
        if(mCore == null){
            finish();
        }
    }

    @SuppressLint("WakelockTimeout")
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
            mCore.refreshTexture();
        }
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
                .putExtra(YGOCore.EXTRA_PID, android.os.Process.myPid())
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
            mCore.joinGame(buffer, buffer.position());
        } else {
            if (DEBUG)
                Log.i(TAG, "receive :null");
        }
    }

   private void fullscreen() {
        if (mGameConfig.isImmerSiveMode()) {
            //沉浸模式
            getWindow().getDecorView().setSystemUiVisibility(windowsFlags);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(windowsFlags2);
        }
        int[] size = getGameSize();
        setGameSize(size[0], size[1]);
   }

    private int[] getGameSize() {
        Display display = getWindowManager().getDefaultDisplay();
        int activityHeight = display.getHeight();
        int activityWidth = display.getWidth();
        int w = Math.max(activityHeight, activityWidth);
        int h = Math.min(activityHeight, activityWidth);
        mActivityWidth = w;
        mActivityHeight = h;
        if (mGameConfig.isKeepScale()) {
            float sx = (float) w / GAME_WIDTH;
            float sy = (float) h / GAME_HEIGHT;
            float scale = Math.min(sx, sy);
            Log.i(TAG, "getGameSize:sx=" + sx + ",sy=" + sy + ",w=" + w + ",h=" + h + ",gw=" + (int) (GAME_WIDTH * scale) + ",gh=" + (int) (GAME_HEIGHT * scale));
            return new int[]{(int) (GAME_WIDTH * scale), (int) (GAME_HEIGHT * scale)};
        } else {
            return new int[]{w, h};
        }
    }

    @Override
    public void onBackPressed() {
        //
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        mCore.sendKeyEvent(event);
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
//        mCore.sendKeyEvent(event);
        return super.onKeyUp(keyCode, event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void setContentView(View view) {
        int[] size = getGameSize();
        int w = size[0];
        int h = size[1];
        setGameSize(w, h);
        mLayout = new FrameLayout(this);
        mSurfaceView = new SurfaceView(this);
//        mLayout.setFitsSystemWindows(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.gravity = Gravity.CENTER;
        mLayout.addView(mSurfaceView, lp);
        mLayout.addView(view, lp);
        super.setContentView(mLayout);
        mClickView = view;
        getWindow().takeSurface(null);
        replaced = true;
        mSurfaceView.getHolder().addCallback(this);
        getWindow().takeInputQueue(null);
        mSurfaceView.requestFocus();
        mClickView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return mCore.sendTouchEvent(event.getAction(), (int)event.getX(), (int)event.getY(), event.getPointerId(0));
            }
        });
        mInitView = true;
    }

    private void setGameSize(int w, int h){
        mGameWidth = w;
        mGameHeight = h;
        if(mInitView) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mClickView.getLayoutParams();
            lp.width = w;
            lp.height = h;
            mClickView.setLayoutParams(lp);
            mSurfaceView.setLayoutParams(lp);
            mSurfaceView.getHolder().setFixedSize(w, h);
        }
    }

    @Override
    public void onGlobalLayout() {
//        super.onGlobalLayout();
    }

    //region popup window
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
                mCore.setComboBoxSelection(idx);
            } else if (mCompatGUIMode == ComboBoxCompat.COMPAT_GUI_MODE_CHECKBOXES_PANEL) {
                mCore.setCheckBoxesSelection(idx);
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
                mCore.cancelChain();
                break;
            case OverlayView.MODE_REFRESH_OPTION:
                if (DEBUG)
                    Log.d(TAG, "Constants.MODE_REFRESH_OPTION: " + action);
                mCore.refreshTexture();
                break;
            case OverlayView.MODE_REACT_CHAIN_OPTION:
                if (DEBUG)
                    Log.d(TAG, "Constants.MODE_REACT_CHAIN_OPTION: " + action);
                mCore.reactChain(action);
                break;
            case OverlayView.MODE_IGNORE_CHAIN_OPTION:
                if (DEBUG)
                    Log.d(TAG, "Constants.MODE_IGNORE_CHAIN_OPTION: " + action);
                mCore.ignoreChain(action);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        final String text = v.getText().toString();
        mCore.insertText(text);
        mGlobalEditText.dismiss();
        return false;
    }
    //endregion

    @Override
    public Object getNativeGameHost(){
        //jni call
        return mHost;
    }

    //region game host
    protected class DefaultGameHost extends GameHostWrapper {

        public DefaultGameHost(Context context, GameHost base) {
            super(context, base);
        }

        @Override
        public void playSoundEffect(String name) {
            if(mGameConfig.isEnableSoundEffect()) {
                super.playSoundEffect(name);
            }
        }

        @Override
        public int getLocalAddr() {
            return mNetController.getIPAddress();
        }

        @Override
        public void toggleIME(final boolean show, final String hint) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (show) {
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
        public void showComboBoxCompat(final String[] items, final boolean isShow, final int mode) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCompatGUIMode = mode;
                    if (isShow) {
                        mGlobalComboBox.fillContent(items);
                        mGlobalComboBox.showAtLocation(mContentView,
                                Gravity.BOTTOM, 0, 0);
                    }
                }
            });
        }

        @Override
        public ByteBuffer getInitOptions() {
            return mGameConfig.getNativeInitOptions().toNativeBuffer();
        }

        @Override
        public int getWindowWidth() {
            Log.i(TAG, "getWindowWidth:" + mGameWidth);
            return mGameWidth;
        }

        @Override
        public int getWindowHeight() {
            Log.i(TAG, "getWindowHeight:" + mGameHeight);
            return mGameHeight;
        }

        @Override
        public void attachNativeDevice(int device) {
            mCore.setNativeAndroidDevice(device);
        }
    }
    //endregion

    @Override
    public void onInputQueueCreated(InputQueue queue) {
//        super.onInputQueueCreated(mCore.getInputQueue());
    }

    @Override
    public void onInputQueueDestroyed(InputQueue queue) {
//        super.onInputQueueDestroyed(mCore.getInputQueue());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!replaced) {
            return;
        }
        super.surfaceCreated(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (!replaced) {
            return;
        }
        super.surfaceChanged(holder, format, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (!replaced) {
            return;
        }
        super.surfaceDestroyed(holder);
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
        if (!replaced) {
            return;
        }
        super.surfaceRedrawNeeded(holder);
    }
}
