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
import android.view.InputQueue;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
import cn.garymb.ygomobile.utils.FullScreenUtils;
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
    protected final int windowsFlags =
            Build.VERSION.SDK_INT >= 19 ? (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) :
                    View.SYSTEM_UI_FLAG_LOW_PROFILE;

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
    private FullScreenUtils mFullScreenUtils;
    private volatile int mPositionX, mPositionY;
    private boolean mPaused;
    private SurfaceView mSurfaceView;


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
        mSurfaceView = new SurfaceView(this);
        mSurfaceView.getHolder().addCallback(this);
        super.onCreate(savedInstanceState);
        Log.e("YGOStarter","跳转完成"+System.currentTimeMillis());
        mFullScreenUtils = new FullScreenUtils(this, app().isImmerSiveMode());
        mFullScreenUtils.fullscreen();
        mFullScreenUtils.onCreate();
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
    }

    //电池管理
    private PowerManager mPM;
    private PowerManager.WakeLock mLock;

    @Override
    protected void onResume() {
        mPaused = false;
        super.onResume();
        Log.e("YGOStarter","ygo显示"+System.currentTimeMillis());
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
        mPaused = true;
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

    private int[] getGameSize(){
        //调整padding
        float screenW = app().getScreenWidth();
        float screenH = app().getScreenHeight();
        float sH = screenH / 1024.0f;
        float sW = screenW / 640.0f;

        float xScale,yScale;
        //取最小值
        if(sH < sW){
            xScale = sH;
            yScale = sH;
        } else {
            xScale = sW;
            yScale = sW;
        }
        int w = (int)(1024.0*xScale);
        int h = (int)(640.0*yScale);
        mPositionX = (int)((sH - w)/2.0f);
        mPositionY = (int)((sW - h)/2.0f);
        return new int[]{w, h};
    }

    private void fullscreen() {

        //如果是沉浸模式
        if (app().isImmerSiveMode()) {
            mFullScreenUtils.fullscreen();
            app().attachGame(this);
            //游戏大小
//            int[] size = getGameSize();
//            getWindow().setLayout(size[0], size[1]);
        }
    }

    @Override
    public void setContentView(View view) {
        FrameLayout layout = new FrameLayout(this);
        int[] size = getGameSize();
        int w = size[0];
        int h = size[1];
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);

        lp.gravity = Gravity.CENTER;
        layout.addView(mSurfaceView, lp);
        layout.addView(view, lp);
        view.setLongClickable(true);
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v,final MotionEvent event) {
                if(mPaused){
                    return false;
                }
                int eventType = event.getAction() & MotionEvent.ACTION_MASK;
                switch (eventType) {
                    case MotionEvent.ACTION_DOWN:
//                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_MOVE:
                    case MotionEvent.ACTION_UP:
//                    case MotionEvent.ACTION_POINTER_UP:
                    case MotionEvent.ACTION_CANCEL:
                        break;
                    default:
                       return false;
                }
                IrrlichtBridge.sendTouch(event.getAction(), event.getX(), event.getY(), 0);
                return true;
            }
        });

//        getWindow().setLayout(w, h);
//        getWindow().setGravity(Gravity.CENTER);
        super.setContentView(layout);
        getWindow().takeSurface(null);
        getWindow().takeInputQueue(null);
    }

    @Override
    public void onInputQueueCreated(InputQueue queue) {
//        super.onInputQueueCreated(queue);
    }

    @Override
    public void onInputQueueDestroyed(InputQueue queue) {
//        super.onInputQueueDestroyed(queue);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode != KeyEvent.KEYCODE_BACK){
            IrrlichtBridge.sendKey(keyCode, true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if(keyCode != KeyEvent.KEYCODE_BACK){
            IrrlichtBridge.sendKey(keyCode, false);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
//        Toast.makeText(this, "请在游戏里面退出", Toast.LENGTH_SHORT).show();
//        super.onBackPressed();
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
        IrrlichtBridge.setHandle(nativeHandle);
    }
}
