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
import android.os.Process;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputQueue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.core.YGOCore;
import cn.garymb.ygomobile.interfaces.GameSize;
import cn.garymb.ygomobile.interfaces.IGameUI;
import cn.garymb.ygomobile.interfaces.GameHost;
import cn.garymb.ygomobile.interfaces.GameConfig;

import cn.garymb.ygomobile.interfaces.IGameActivity;
import cn.garymb.ygomobile.lib.R;
import cn.garymb.ygomobile.tool.GameSoundPlayer;
import cn.garymb.ygomobile.tool.ScreenKeeper;
import cn.garymb.ygomobile.widget.ComboBoxCompat;
import cn.garymb.ygomobile.widget.EditWindowCompat;
import cn.garymb.ygomobile.widget.overlay.OverlayOvalView;
import cn.garymb.ygomobile.widget.overlay.OverlayView;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;

public class YGOMobileActivity extends NativeActivity implements
        IGameActivity,
        View.OnClickListener,
        PopupWindow.OnDismissListener,
        TextView.OnEditorActionListener,
        OverlayOvalView.OnDuelOptionsSelectListener {
    private static final String TAG = YGOMobileActivity.class.getSimpleName();

    private static boolean DEBUG;

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
//                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION/* 隐藏导航栏 */
//                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION/* 隐藏状态栏 */
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

    private @NonNull
    YGOCore mCore;
    private @NonNull
    GameHost mHost;
    private @NonNull
    GameConfig mGameConfig;
    private volatile boolean mOverlayShowRequest = false;
    private volatile int mCompatGUIMode;
    private final GameSize mGameSize = new GameSize();
    private FrameLayout mLayout;
    //画面居中
    private SurfaceView mSurfaceView;
    private View mClickView;
    private boolean replaced = false;
    private boolean mInitView = false;
    private ScreenKeeper mScreenKeeper;
    private GameSoundPlayer mGameSoundPlayer;
    private static final boolean USE_INPUT_QUEEN = true;
    //点击修正
    private static final boolean RESIZE_WINDOW_LAYOUT = false;

    private GameApplication app() {
        return GameApplication.get();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DEBUG = GameApplication.isDebug();
        mGameConfig = getIntent().getParcelableExtra(GameConfig.EXTRA_CONFIG);
        mCore = YGOCore.getInstance();
        mHost = app().getGameHost();
        fullscreen();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            getWindow().setAttributes(lp);
        }
        if (mGameConfig != null) {
            mGameSoundPlayer = new GameSoundPlayer(mHost.getGameAsset());
            if (mGameConfig.isEnableSoundEffect()) {
                mGameSoundPlayer.initSoundEffectPool();
            }
        }
        mScreenKeeper = new ScreenKeeper(this);
        mHost.onBeforeCreate(this);
        super.onCreate(savedInstanceState);
        if (mGameConfig == null) {
            finish();
            return;
        }
        if (DEBUG) {
            Log.e("YGOStarter", "跳转完成" + System.currentTimeMillis());
        }
        if (mGameConfig.isLockScreenOrientation()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        if (DEBUG) {
            Log.i(TAG, "USE_INPUT_QUEEN:" + USE_INPUT_QUEEN + ",RESIZE_WINDOW_LAYOUT=" + RESIZE_WINDOW_LAYOUT);
        }
        initExtraView();

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
        mHost.initWindbot(mGameConfig.getNativeInitOptions(), mGameConfig);
        mHost.onAfterCreate(this);
        sendBroadcast(new Intent(YGOCore.ACTION_START).putExtra(YGOCore.EXTRA_PID, Process.myPid()).setPackage(getPackageName()));
    }

    @SuppressLint("WakelockTimeout")
    @Override
    protected void onResume() {
        super.onResume();
        if (DEBUG) {
            Log.e("YGOStarter", "ygo显示" + System.currentTimeMillis());
        }
        mScreenKeeper.keep();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mScreenKeeper.release();
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
        if (intent.hasExtra(GameConfig.EXTRA_CONFIG)) {
            GameConfig config = getIntent().getParcelableExtra(GameConfig.EXTRA_CONFIG);
            if (!mGameConfig.equals(config)) {
                mGameConfig = config;
                if (config.isEnableSoundEffect()) {
                    mGameSoundPlayer.initSoundEffectPool();
                } else {
                    mGameSoundPlayer.release();
                }
            }
        }
        handleExternalCommand(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void finish() {
        mCore.release();
        mHost.onGameExit(this);
        sendBroadcast(new Intent(YGOCore.ACTION_END).putExtra(YGOCore.EXTRA_PID, Process.myPid()).setPackage(getPackageName()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            super.finish();
        }
    }

    private void handleExternalCommand(Intent intent) {
        long time = intent.getLongExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_TIME, 0);
        if (System.currentTimeMillis() - time >= YGOGameOptions.TIME_OUT) {
            if (DEBUG)
                Log.i(TAG, "command timeout");
            return;
        }
        YGOGameOptions options = intent
                .getParcelableExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY);
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
        GameSize gameSize = mHost.getGameSize(this, mGameConfig);
        mGameSize.update(gameSize);
        int w = gameSize.getWidth();
        int h = gameSize.getHeight();
        if (RESIZE_WINDOW_LAYOUT || !USE_INPUT_QUEEN) {
            mGameSize.setTouch(0, 0);
        }
        if (DEBUG) {
            Log.i(TAG, "GameSize:" + mGameSize);
        }
        return new int[]{w, h};
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
        if (!USE_INPUT_QUEEN) {
            getWindow().takeInputQueue(null);
            mClickView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return mCore.sendTouchEvent(event.getAction(), (int) event.getX(), (int) event.getY(), event.getPointerId(0));
                }
            });
        } else if (RESIZE_WINDOW_LAYOUT) {
            getWindow().setLayout(w, h);
        }
        mSurfaceView.requestFocus();
        mInitView = true;
    }

    private void setGameSize(int w, int h) {
        if (mInitView) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mClickView.getLayoutParams();
            lp.width = w;
            lp.height = h;
            mClickView.setLayoutParams(lp);
            mSurfaceView.setLayoutParams(lp);
            mSurfaceView.getHolder().setFixedSize(w, h);
        }
        if (RESIZE_WINDOW_LAYOUT) {
            getWindow().setLayout(w, h);
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
            if (DEBUG) {
                Log.d(TAG, "showComboBoxCompat: receive selection: " + idx);
            }
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
                if (DEBUG) {
                    Log.d(TAG, "Constants.MODE_CANCEL_CHAIN_OPTIONS: " + action);
                }
                mCore.cancelChain();
                break;
            case OverlayView.MODE_REFRESH_OPTION:
                if (DEBUG) {
                    Log.d(TAG, "Constants.MODE_REFRESH_OPTION: " + action);
                }
                mCore.refreshTexture();
                break;
            case OverlayView.MODE_REACT_CHAIN_OPTION:
                if (DEBUG) {
                    Log.d(TAG, "Constants.MODE_REACT_CHAIN_OPTION: " + action);
                }
                mCore.reactChain(action);
                break;
            case OverlayView.MODE_IGNORE_CHAIN_OPTION:
                if (DEBUG) {
                    Log.d(TAG, "Constants.MODE_IGNORE_CHAIN_OPTION: " + action);
                }
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
    public ByteBuffer getJoinOptions() {
        Intent intent = getIntent();
        long time = intent.getLongExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_TIME, 0);
        if (System.currentTimeMillis() - time >= YGOGameOptions.TIME_OUT) {
            if (DEBUG)
                Log.i(TAG, "command timeout");
            return null;
        }
        if (intent.hasExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY)) {
            YGOGameOptions options = intent
                    .getParcelableExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY);
            intent.removeExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY);
            if (options != null) {
                return options.toByteBuffer();
            }
        }
        return null;
    }

    @Override
    public void playSoundEffect(String path) {
        if (mGameConfig.isEnableSoundEffect()) {
            mGameSoundPlayer.playSoundEffect(path);
        }
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
    public int getWindowLeft() {
        if (DEBUG) {
            Log.i(TAG, "getWindowLeft:" + mGameSize.getTouchX());
        }
        return mGameSize.getTouchX();
    }

    @Override
    public int getWindowTop() {
        if (DEBUG) {
            Log.i(TAG, "getWindowTop:" + mGameSize.getTouchY());
        }
        return mGameSize.getTouchY();
    }

    @Override
    public int getWindowWidth() {
        if (DEBUG) {
            Log.i(TAG, "getWindowWidth:" + mGameSize.getWidth());
        }
        return mGameSize.getWidth();
    }

    @Override
    public int getWindowHeight() {
        if (DEBUG) {
            Log.i(TAG, "getWindowHeight:" + mGameSize.getHeight());
        }
        return mGameSize.getHeight();
    }

    @Override
    public ByteBuffer getInitOptions() {
        return mGameConfig.getNativeInitOptions().toNativeBuffer();
    }

    @Override
    public void attachNativeDevice(int device) {
        mCore.setNativeAndroidDevice(device);
    }

    @Override
    public void onInputQueueCreated(InputQueue queue) {
        if (USE_INPUT_QUEEN) {
            super.onInputQueueCreated(queue);
        }
    }

    @Override
    public void onInputQueueDestroyed(InputQueue queue) {
        if (USE_INPUT_QUEEN) {
            super.onInputQueueDestroyed(queue);
        }
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

    @Override
    public String getSetting(String key) {
        return mHost.getSetting(key);
    }

    @Override
    public int getIntSetting(String key, int def) {
        return mHost.getIntSetting(key, def);
    }

    @Override
    public void saveIntSetting(String key, int value) {
        mHost.saveIntSetting(key, value);
    }

    @Override
    public void saveSetting(String key, String value) {
        mHost.saveSetting(key, value);
    }

    @Override
    public void runWindbot(String cmd) {
        mHost.runWindbot(cmd);
    }

    @Override
    public int getLocalAddr() {
        return mHost.getLocalAddr();
    }

    @Override
    public void onReportProblem() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mHost.onGameReport(YGOMobileActivity.this, mGameConfig);
            }
        });
    }
}
