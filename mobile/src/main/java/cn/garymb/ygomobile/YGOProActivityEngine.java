package cn.garymb.ygomobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.game.chat.ChatInputUI;
import cn.garymb.ygomobile.game.deck.DeckEditorManager;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.GameFieldController;
import cn.garymb.ygomobile.game.gamefield.GameTopInfoManager;
import cn.garymb.ygomobile.game.replay.ReplayPlayer;
import cn.garymb.ygomobile.game.select.ShowDialogUtil;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.card.CardDetailPanel;
import cn.garymb.ygomobile.render.gamefieldview.GameFieldView;
import cn.garymb.ygomobile.render.spec.SpecEffectOverlay;
import cn.garymb.ygomobile.render.field.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.lobby.CreateHostDialog;
import cn.garymb.ygomobile.ui.dialogs.duel.DuelLogDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.EmotionDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.LanModeDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.MainMenuDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.PlayerWaitingDialog;
import cn.garymb.ygomobile.ui.dialogs.replay.ReplayModeDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.SettingsDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.SingleModeDialog;
import cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog;
import cn.garymb.ygomobile.utils.CrashHandler;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.FullScreenUtils;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import cn.garymb.ygomobile.utils.RightAlignedTiledDrawable;
import ocgcore.DataManager;
import ocgcore.StringManager;


/**
 * 决斗主界面门面的引擎中间层（继承链第三层）：引擎/音频/图像资源初始化
 * （initEngine/loadData/startWindbotListener/warmUpDuelEngine）、外部直达启动
 * 参数处理（handleDirectIntent/handleArgs/joinFromOptions）与位置名/应答等
 * 工具方法；实现基类声明的部分 abstract 钩子，场景导航钩子由叶子层实现。
 */
abstract class YGOProActivityEngine extends YGOProActivityUi {
    protected void initEngine() {
        AppsSettings appsSettings = AppsSettings.get();
        soundManager = new SoundManager(this);
        // 初始化即按保存的音频设置应用（对齐 gframe game.cpp LoadConfig：
        // enable_sound/sound_volume/enable_music/music_volume/chkSwitchBGM）
        soundManager.init(
                appsSettings.getIntSettings("soundVolume", 50) / 100.0,
                appsSettings.getIntSettings("musicVolume", 50) / 100.0,
                appsSettings.getIntSettings("chkEnableSound", 1) == 1,
                appsSettings.getIntSettings("chkEnableMusic", 1) == 1);
        soundManager.setMusicMode(appsSettings.getIntSettings("chkSwitchBGM", 1) == 1);
        // 召唤主题歌（chants）播完：按当前场景重新选曲恢复 BGM
        soundManager.setOnChantFinishListener(this::updateBGM);

        imageLoader = new ImageLoader(true);

        // 引擎回调与对话框监听分别下沉到同包协作类，经包级私有直连本门面的共享状态/UI 编排
        engineCallback = new EngineCallbackDelegate((YGOProActivity) this);
        menuNav = new MainMenuNavigator((YGOProActivity) this);

        engine = new GameEngine(soundManager);
        engine.setListener(engineCallback);
        engine.setPlayerName(Constants.PlayerName);

        TextureLoader.get().init();

        cardDetailPanel.setImageLoader(imageLoader);
        cardDetailPanel.bindSideButtonIcons();
        fieldCtl.init(engine, imageLoader);

        // 初始化时通过 getIntSettings 统一应用全部已保存设置到对应功能
        applySettingsToEngine();
    }

    protected void loadData() {
        Thread t = new Thread(() -> {
            DataManager.get().load(false);
            Log.i(TAG, "DataManager loaded");
        }, "DataLoad");
        CrashHandler.getInstance().hookThread(t, "游戏-卡片数据加载");
        t.start();
    }

    /**
     * 初始化即后台启动 windbot：提前完成 WindBot.initAndroid 与 RUN_WINDBOT 监听注册，
     * 使人机对战不必等到进入 PlayerWaitingDialog 才初始化（原 ResCheckTask 路径在
     * isOnlyGame 直达决斗界面时会被跳过），节约启动等待时间。
     * initAndroid 在 WindBotService 内做了进程级去重：MainActivity 已初始化过时
     * 此处仅确保监听已注册，不会重复进入 Mono 运行时（重复 init 会导致 libmonosgen 崩溃）
     */
    protected void startWindbotListener() {
        Thread t = new Thread(() -> {
            WindBotService.startListening(getApplicationContext());
            Log.i(TAG, "WindBot listener ready");
        }, "WindBotInit");
        CrashHandler.getInstance().hookThread(t, "游戏-WindBot初始化");
        t.start();
    }

    /**
     * 启动即在子线程引导 native 决斗引擎：scripts.zip 解压、卡包脚本/cdb 物化、
     * cards.cdb/扩展 cdb 加载（{@code NativeScriptBootstrap#ensureEngineReady} 的全部
     * 环节，即 {@code OcgDuelEngine.init} 的首次数秒一次性开销）在 Activity 创建的
     * 第一刻就开始后台进行，与主菜单/建房/猜拳/选先后攻全部并行，使首次建立
     * 局域网主机与开局发牌都无需再等待引擎引导。预热进程内最早锚点已在
     * {@code App#onCreate}（AppEngineWarmUp），此处是同幂等方法的重申入口：
     * App 阶段因资源目录尚未就绪等瞬态原因失败时，本处重试链（含延后重试）
     * 继续覆盖；已就绪时 ensureEngineReady 毫秒级返回，不重复解压/加载。
     */
    protected void warmUpDuelEngine() {
        NativeScriptBootstrap.warmUpAsync("EngineWarmUp", "游戏-引擎预热");
    }
    protected boolean handleDirectIntent(Intent intent) {
        if (intent == null) return false;

        YGOGameOptions options = intent.getParcelableExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY);
        if (options != null) {
            long time = intent.getLongExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_TIME, 0);
            if (System.currentTimeMillis() - time < YGOGameOptions.TIME_OUT) {
                joinFromOptions(options);
                PlayerWaitingDialog.showPlayerWaitingForDirectJoin((YGOProActivity) this, options);
                return true;
            }
        }

        String[] args = IrrlichtBridge.getArgs(intent);
        if (args != null && args.length > 0) {
            return handleArgs(args);
        }

        String host = intent.getStringExtra("host");
        if (!TextUtils.isEmpty(host)) {
            int port = intent.getIntExtra("port", 7911);
            String room = intent.getStringExtra("room");
            saveLastConnectionInfo(Constants.PlayerName, host, port, room, "");
            engine.connectToServer(host, port, false,
                    room != null ? room : "", "",
                    0, 0, 5, 8000, 5, 1, 0, false, false);
            PlayerWaitingDialog.showPlayerWaitingForDirectJoin((YGOProActivity) this, null);
            return true;
        }

        if (intent.getBooleanExtra("botMode", false)) {
            engine.setBotMode(true);
            engine.connectToServer("127.0.0.1", 7911, true,
                    "Bot Game", "",
                    5, 0, 5, 8000, 5, 1, 0, true, false);
            engine.startBotDuel("127.0.0.1", 7911, "WindBot", "");
            getMainMenuDialog().hideMainMenu();
            return true;
        }

        return false;
    }

    protected boolean handleArgs(String[] args) {
        boolean keepOnReturn = false;
        boolean showReplayDialog = false;
        boolean showSingleDialog = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-k".equals(arg)) {
                keepOnReturn = true;
                exitOnReturn = false;
            } else if ("-r".equals(arg)) {
                exitOnReturn = !keepOnReturn;
                String replayName = null;
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    replayName = args[i + 1];
                    i++;
                }
                if (replayName != null) {
                    File replayFile = new File(AppsSettings.get().getResourcePath() + "/" + Constants.CORE_REPLAY_PATH, replayName);
                    if (replayFile.exists()) {
                        getMainMenuDialog().hideMainMenu();
                        ReplayModeDialog.startReplayPlayback((YGOProActivity) this, replayFile.getAbsolutePath(), 1);
                        return true;
                    }
                } else {
                    showReplayDialog = true;
                }
            } else if ("-s".equals(arg)) {
                exitOnReturn = !keepOnReturn;
                String singleName = null;
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    singleName = args[i + 1];
                    i++;
                }
                if (singleName != null) {
                    File singleFile = new File(AppsSettings.get().getResourcePath() + "/" + Constants.CORE_SINGLE_PATH, singleName);
                    if (singleFile.exists()) {
                        getMainMenuDialog().hideMainMenu();
                        engine.startSingleMode(singleFile.getAbsolutePath(), false); // 默认不使用顶端返回
                        return true;
                    }
                } else {
                    showSingleDialog = true;
                }
            } else if ("-j".equals(arg) || "-c".equals(arg)) {
                exitOnReturn = !keepOnReturn;
            }
        }

        if (showReplayDialog) {
            directEnterMode = 1;
            dialogContainer.post(() -> ReplayModeDialog.showReplayModeDialog((YGOProActivity) YGOProActivityEngine.this));
            return true;
        }

        if (showSingleDialog) {
            directEnterMode = 2;
            dialogContainer.post(() -> SingleModeDialog.showSingleModeDialog((YGOProActivity) YGOProActivityEngine.this));
            return true;
        }

        return false;
    }

    protected void joinFromOptions(YGOGameOptions options) {
        String host = options.mServerAddr;
        int port = options.mPort;
        String room = options.mRoomName != null ? options.mRoomName : "";
        String user = options.mUserName != null ? options.mUserName : Constants.PlayerName;
        String password = options.mRoomName != null ? options.mRoomName : "";
        saveLastConnectionInfo(user, host, port, room, password);
        engine.setPlayerName(user);
        engine.connectToServer(host, port, false, room, password,
                0, 0, 5, 8000, 5, 1, 0, false, false);
    }
    protected String getLocationName(int location) {
        switch (location) {
            case 0x01:
                return "卡组";
            case 0x02:
                return "手牌";
            case 0x04:
                return "怪兽区";
            case 0x08:
                return "魔陷区";
            case 0x10:
                return "墓地";
            case 0x20:
                return "除外";
            case 0x40:
                return "额外";
            case 0x80:
                return "超量素材";
            default:
                return "区域" + location;
        }
    }

    public void showCardInfoPanel(ClientCard card) {
        cardDetailPanel.showCardInfo(card);
    }

    /**
     * 回放结束：用阶段文字（case 101）显示胜负 + 胜利原因，委托 EngineCallbackDelegate 的居中特效层。
     */
    public void showReplayResult(int winner, int reason, String winnerName) {
        if (engineCallback != null) engineCallback.showReplayResult(winner, reason, winnerName);
    }

    // 录像回放的召唤/连锁/无效大图与阶段文字已由实况管线（EngineCallbackDelegate 的消息回调）
    // 直接派发，回放不再需要一套专用转发入口

    // === Response helpers ===

    public void sendResponseInt(int value) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(value);
        engine.sendResponse(buf.array());
    }
}

