package cn.garymb.ygomobile;

import static cn.garymb.ygomobile.Constants.PRO_VERSION;
import static cn.garymb.ygomobile.utils.BotUtil.parseBotConfig;
import static cn.garymb.ygomobile.utils.PuzzleUtil.loadPuzzleFiles;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.DeckEditorManager;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.GameFieldController;
import cn.garymb.ygomobile.game.ReplayEngine;
import cn.garymb.ygomobile.game.ReplayReader;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.render.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.LanModeDialog;
import cn.garymb.ygomobile.ui.dialogs.MainMenuDialog;
import cn.garymb.ygomobile.ui.dialogs.ReplayModeDialog;
import cn.garymb.ygomobile.ui.dialogs.SettingsDialog;
import cn.garymb.ygomobile.ui.dialogs.SingleModeDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.BotUtil;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.FullScreenUtils;
import cn.garymb.ygomobile.utils.PuzzleUtil;
import ocgcore.DataManager;
import ocgcore.data.Card;

public class YGOProActivity extends AppCompatActivity implements
        GameEngine.EngineListener,
        LanModeDialog.OnLanModeListener {

    private static final String TAG = "YGONativeGame";

    private GameEngine engine;
    private SoundManager soundManager;
    private ImageLoader imageLoader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private DeckEditorManager deckEditorManager;
    private View layoutDeckEditor;

    private LinearLayout layoutDeckControl;
    private LinearLayout layoutGameRight;
    private View layoutGameContent;
    private int currentSelectType = -1;
    private YesOrNoDialog currentDialog;

    private FrameLayout dialogContainer;
    private MainMenuDialog mainMenuDialog;
    private LanModeDialog lanModeDialog;

    private EditText etChatInput;

    private boolean isMyTurn = false;
    private volatile boolean isGameStarted = false;

    private ReplayEngine currentReplayEngine;
    private CardDetailPanel cardDetailPanel;
    private GameFieldController fieldCtl;

    private boolean isSelectingSum = false;
    private int sumSelectValue = 0;
    private int sumSelectMin = 0;
    private int sumSelectMax = 0;
    private List<SumCardInfo> sumCardInfos;
    private boolean[] sumSelected;
    private boolean exitOnReturn = true;
    private int directEnterMode = 0; // 0=normal, 1=replay dialog, 2=single dialog
    private FullScreenUtils mFullScreenUtils;
    private String currentBgPath;

    private static class SumCardInfo {
        int code, controler, location, sequence, opParam, value, index;

        SumCardInfo(int code, int ctrl, int loc, int seq, int opParam, int value, int index) {
            this.code = code;
            this.controler = ctrl;
            this.location = loc;
            this.sequence = seq;
            this.opParam = opParam;
            this.value = value;
            this.index = index;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setupFullScreen();
        setContentView(R.layout.activity_ygo_game);

        initViews();
        initEngine();
        loadData();
        setupBackPressedHandler();

        if (!handleDirectIntent(getIntent())) {
            getMainMenuDialog().showMainMenu();
        }
    }

    @Override
    public void onPlayerEnter(String name, int pos) {
        runOnUiThread(() -> {
            if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                lanModeDialog.removeObserver(name);
                lanModeDialog.setPlayerName(pos, name);
                lanModeDialog.refreshPlayerDisplay();
            }
        });
    }

    @Override
    public void onPlayerChange(int status) {
        runOnUiThread(() -> {
            if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                int pos = (status >> 4) & 0x0F;
                int state = status & 0x0F;

                if (state < 8) {
                    String oldName = "";
                    switch (pos) {
                        case 0:
                            oldName = lanModeDialog.getPlayerName(0);
                            break;
                        case 1:
                            oldName = lanModeDialog.getPlayerName(1);
                            break;
                        case 2:
                            oldName = lanModeDialog.getPlayerName(2);
                            break;
                        case 3:
                            oldName = lanModeDialog.getPlayerName(3);
                            break;
                    }

                    lanModeDialog.movePlayer(pos, state);

                    if (!oldName.isEmpty() && state >= 4) {
                        lanModeDialog.addObserver(oldName);
                    }
                    if (pos >= 4 && !oldName.isEmpty() && state < 4) {
                        lanModeDialog.removeObserver(oldName);
                    }
                } else if (state == 0x8) {
                    String observerName = "";
                    switch (pos) {
                        case 0:
                            observerName = lanModeDialog.getPlayerName(0);
                            break;
                        case 1:
                            observerName = lanModeDialog.getPlayerName(1);
                            break;
                        case 2:
                            observerName = lanModeDialog.getPlayerName(2);
                            break;
                        case 3:
                            observerName = lanModeDialog.getPlayerName(3);
                            break;
                    }
                    lanModeDialog.clearPlayerPos(pos);
                    if (!observerName.isEmpty()) {
                        lanModeDialog.addObserver(observerName);
                    }
                } else if (state == 0x9) {
                    lanModeDialog.setPlayerReady(pos, true);
                } else if (state == 0xa) {
                    lanModeDialog.setPlayerReady(pos, false);
                } else if (state == 0xb) {
                    String leavingName = "";
                    switch (pos) {
                        case 0:
                            leavingName = lanModeDialog.getPlayerName(0);
                            break;
                        case 1:
                            leavingName = lanModeDialog.getPlayerName(1);
                            break;
                        case 2:
                            leavingName = lanModeDialog.getPlayerName(2);
                            break;
                        case 3:
                            leavingName = lanModeDialog.getPlayerName(3);
                            break;
                    }
                    lanModeDialog.clearPlayerPos(pos);
                    if (!leavingName.isEmpty()) {
                        lanModeDialog.removeObserver(leavingName);
                    }
                }
                lanModeDialog.refreshPlayerDisplay();
            }
        });
    }

    @Override
    public void onWatchChange(int watchCount) {
        runOnUiThread(() -> {
            if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                lanModeDialog.updateWatchCount(watchCount);
                lanModeDialog.refreshPlayerDisplay();
            }
        });
    }

    @Override
    public void onJoinGame(int lflist, int rule, int mode, int duelRule,
                           int noCheckDeck, int noShuffleDeck,
                           int startLp, int startHand, int drawCount, int timeLimit) {
        runOnUiThread(() -> {
            if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                lanModeDialog.updateRoomInfo(lflist, rule, mode, duelRule,
                        noCheckDeck, noShuffleDeck,
                        startLp, startHand, drawCount, timeLimit);
            }
        });
    }

    @Override
    public void onTypeChange(int type) {
        runOnUiThread(() -> {
            if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                int selfType = type & 0x0F;
                boolean isHost = ((type >> 4) & 0x0F) != 0;
                boolean isTag = engine.getGameMode() == 2;
                lanModeDialog.updateTypeChange(selfType, isTag, isHost);
                lanModeDialog.refreshPlayerDisplay();
            }
        });
    }

    @Override
    public void onDeckError(int errorType, int cardCode) {
        String errorDesc;
        switch (errorType) {
            case YGOProtocol.DECKERROR_LFLIST:
                errorDesc = "禁限卡表违规";
                break;
            case YGOProtocol.DECKERROR_OCGONLY:
                errorDesc = "仅限OCG卡片";
                break;
            case YGOProtocol.DECKERROR_TCGONLY:
                errorDesc = "仅限TCG卡片";
                break;
            case YGOProtocol.DECKERROR_UNKNOWNCARD:
                errorDesc = "未知卡片";
                break;
            case YGOProtocol.DECKERROR_CARDCOUNT:
                errorDesc = "卡片数量超限";
                break;
            case YGOProtocol.DECKERROR_MAINCOUNT:
                errorDesc = "主卡组数量不符(" + cardCode + "张)";
                break;
            case YGOProtocol.DECKERROR_EXTRACOUNT:
                errorDesc = "额外卡组数量超限(" + cardCode + "张)";
                break;
            case YGOProtocol.DECKERROR_SIDECOUNT:
                errorDesc = "副卡组数量超限(" + cardCode + "张)";
                break;
            case YGOProtocol.DECKERROR_NOTAVAIL:
                errorDesc = "卡片不可用";
                break;
            default:
                errorDesc = "未知卡组错误(type=" + errorType + ")";
                break;
        }

        String cardName = "";
        if (cardCode > 0 && errorType != YGOProtocol.DECKERROR_MAINCOUNT
                && errorType != YGOProtocol.DECKERROR_EXTRACOUNT
                && errorType != YGOProtocol.DECKERROR_SIDECOUNT) {
            cardName = getCardDisplayName(cardCode);
        }

        String title = "卡组验证失败";
        String message = errorDesc;
        if (!cardName.isEmpty()) {
            message += "\n卡片: " + cardName + " (" + cardCode + ")";
        }

        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle(title).setMessage(message);
        dialog.show();
    }

    private void setupFullScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        if (mFullScreenUtils == null) {
            mFullScreenUtils = new FullScreenUtils(this, AppsSettings.get().isImmerSiveMode());
            mFullScreenUtils.onCreate();
        }
        mFullScreenUtils.fullscreen();
    }

    private void initViews() {
        dialogContainer = findViewById(R.id.dialog_container);
        layoutDeckControl = findViewById(R.id.layout_deck_control);
        layoutGameRight = findViewById(R.id.layout_game_right);
        layoutGameContent = findViewById(R.id.layout_game_content);
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.GONE);
        etChatInput = findViewById(R.id.et_chat_input);
        setupChatInput();

        cardDetailPanel = new CardDetailPanel(this);
        cardDetailPanel.bindViews();
        fieldCtl = new GameFieldController(this, mainHandler);
        fieldCtl.create();

        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_MENU);
    }

    private void setupChatInput() {
        if (etChatInput == null) return;
        etChatInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSend = actionId == EditorInfo.IME_ACTION_SEND
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (isSend) {
                sendChatMessage();
                return true;
            }
            return false;
        });
    }

    private void sendChatMessage() {
        String message = etChatInput.getText().toString().trim();
        if (message.isEmpty()) return;
        if (engine != null && engine.getClient() != null) {
            engine.sendChat(message);
        }
        etChatInput.setText("");
    }

    private void initEngine() {
        soundManager = new SoundManager(this);
        soundManager.init(0.8, 0.6, true, true);

        imageLoader = new ImageLoader(true);

        engine = new GameEngine(soundManager);
        engine.setListener(this);
        engine.setPlayerName(Constants.PlayerName);

        TextureLoader.get().init();

        cardDetailPanel.setImageLoader(imageLoader);
        fieldCtl.init(engine, imageLoader);
    }

    private void loadData() {
        new Thread(() -> {
            DataManager.get().load(false);
            Log.i(TAG, "DataManager loaded");
        }, "DataLoad").start();
    }

    // === 供三个UI管理类回调的桥接方法 ===

    public GameEngine getEngine() {
        return engine;
    }

    public int getCurrentSelectType() {
        return currentSelectType;
    }

    public ReplayEngine getCurrentReplayEngine() {
        return currentReplayEngine;
    }

    public void quitReplay() {
        ReplayModeDialog.quitReplay(this);
    }

    public void toggleSoundMute() {
        if (soundManager != null) {
            SharedPreferences prefs = getSharedPreferences(getPackageName() + ".settings", Context.MODE_PRIVATE);
            boolean currentSound = prefs.getBoolean("chkEnableSound", true);
            boolean currentMusic = prefs.getBoolean("chkEnableMusic", true);
            boolean newMuted = !currentSound && !currentMusic;
            soundManager.enableSounds(newMuted);
            soundManager.enableMusic(newMuted);
            prefs.edit()
                    .putBoolean("chkEnableSound", newMuted)
                    .putBoolean("chkEnableMusic", newMuted)
                    .apply();
        }
    }

    private boolean handleDirectIntent(Intent intent) {
        if (intent == null) return false;

        YGOGameOptions options = intent.getParcelableExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY);
        if (options != null) {
            long time = intent.getLongExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_TIME, 0);
            if (System.currentTimeMillis() - time < YGOGameOptions.TIME_OUT) {
                joinFromOptions(options);
                LanModeDialog.showPlayerWaitingForDirectJoin(this, options);
                return true;
            }
        }

        String[] args = cn.garymb.ygomobile.core.IrrlichtBridge.getArgs(intent);
        if (args != null && args.length > 0) {
            return handleArgs(args);
        }

        String host = intent.getStringExtra("host");
        if (!TextUtils.isEmpty(host)) {
            int port = intent.getIntExtra("port", 7911);
            String room = intent.getStringExtra("room");
            engine.connectToServer(host, port, false,
                    room != null ? room : "", "",
                    0, 0, 5, 8000, 5, 1, 0, false, false);
            LanModeDialog.showPlayerWaitingForDirectJoin(this, null);
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

    private boolean handleArgs(String[] args) {
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
                        ReplayModeDialog.startReplayPlayback(this, replayFile.getAbsolutePath(), 1);
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
                        engine.startSingleMode(singleFile.getAbsolutePath());
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
            dialogContainer.post(() -> ReplayModeDialog.showReplayModeDialog(this));
            return true;
        }

        if (showSingleDialog) {
            directEnterMode = 2;
            dialogContainer.post(() -> SingleModeDialog.showSingleModeDialog(this));
            return true;
        }

        return false;
    }

    private void joinFromOptions(YGOGameOptions options) {
        String host = options.mServerAddr;
        int port = options.mPort;
        String room = options.mRoomName != null ? options.mRoomName : "";
        String user = options.mUserName != null ? options.mUserName : Constants.PlayerName;
        String password = options.mRoomName != null ? options.mRoomName : "";
        engine.setPlayerName(user);
        engine.connectToServer(host, port, false, room, password,
                0, 0, 5, 8000, 5, 1, 0, false, false);
    }

    // === Main Menu ===

    public MainMenuDialog getMainMenuDialog() {
        if (mainMenuDialog == null) {
            mainMenuDialog = new MainMenuDialog(this);
        }
        return mainMenuDialog;
    }

    public GameFieldController getFieldCtl() {
        return fieldCtl;
    }

    public CardDetailPanel getCardDetailPanel() {
        return cardDetailPanel;
    }

    public void setCurrentReplayEngine(ReplayEngine engine) {
        currentReplayEngine = engine;
    }

    public View getDialogContainer() {
        return dialogContainer;
    }

    public void setLanModeDialog(LanModeDialog dialog) {
        lanModeDialog = dialog;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public void hideGameUI() {
        fieldCtl.hide();
        cardDetailPanel.onGameUIHidden();
        if (dialogContainer != null) dialogContainer.setVisibility(View.GONE);
        if (layoutGameRight != null) layoutGameRight.setVisibility(View.GONE);
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.GONE);
    }

    private void showGameUI() {
        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG);
        getMainMenuDialog().hideMainMenu();
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.VISIBLE);
        if (layoutGameRight != null) layoutGameRight.setVisibility(View.VISIBLE);

        fieldCtl.show();
        cardDetailPanel.onGameUIShown();
        if (dialogContainer != null) dialogContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onCreateHostConfirmed(int lflist, int ruleIdx, int modeIdx, int duelRule,
                                      int startLP, int startHand, int drawCount, int timeLimit,
                                      boolean noCheckDeck, boolean noShuffleDeck,
                                      String hostName, String password, String nickname) {
        String roomName = (hostName != null && !hostName.isEmpty()) ? hostName : "Local Game";
        String userName = (nickname != null && !nickname.isEmpty()) ? nickname : Constants.PlayerName;

        engine.setPlayerName(userName);
        engine.startLocalServerWithSettings(lflist, ruleIdx, modeIdx, duelRule,
                noCheckDeck, noShuffleDeck,
                startLP, startHand, drawCount, timeLimit,
                roomName, password != null ? password : "");
    }

    @Override
    public void onJoinGameRequested(String ip, String port, String password, String nickname) {
        int portNum;
        try {
            portNum = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            portNum = 7911;
        }
        String userName = (nickname != null && !nickname.isEmpty()) ? nickname : Constants.PlayerName;
        engine.setPlayerName(userName);
        engine.connectToServer(ip, portNum, false, "", password,
                0, 0, 5, 8000, 5, 1, 0, false, false);
    }

    @Override
    public void onExitLan() {
    }

    @Override
    public void onPlayerWaitingReady() {
        if (engine != null) engine.sendReady();
    }

    @Override
    public void onPlayerWaitingNotReady() {
        if (engine != null) engine.sendNotReady();
    }

    @Override
    public void onPlayerWaitingToDuelist() {
        if (engine != null) engine.sendToDuelist();
    }

    @Override
    public void onPlayerWaitingToObserver() {
        if (engine != null) engine.sendToObserver();
    }

    @Override
    public void onPlayerWaitingExit() {
        if (engine != null) engine.disconnect();
        getMainMenuDialog().restoreMainMenu();
    }

    @Override
    public void onPlayerWaitingDeckUpdate(List<Integer> main, List<Integer> extra, List<Integer> side) {
        if (engine != null) {
            engine.sendDeckUpdate(main, extra, side);
        }
    }

    @Override
    public void onStartGameRequested() {
        if (engine != null) {
            engine.sendStart();
        }
    }

    @Override
    public void onKickPlayerRequested(int pos) {
        if (engine != null) {
            engine.sendKick(pos);
        }
    }

    private void hideReplayControls() {
        cardDetailPanel.hideReplayControls();
        currentReplayEngine = null;
    }

    public void showDeckEditDialog() {
        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_DECK);
        getMainMenuDialog().hideMainMenu();
        hideGameUI();
        showDeckEditorView();
    }

    private void showDeckEditorView() {
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.VISIBLE);
        if (layoutDeckEditor == null) {
            layoutDeckEditor = findViewById(R.id.layout_deck_editor);
        }
        if (layoutDeckEditor != null) {
            layoutDeckEditor.setVisibility(View.VISIBLE);
        }

        // 隐藏右侧决斗场区，让卡组编辑器占据其空间
        if (layoutGameRight != null) layoutGameRight.setVisibility(View.GONE);

        if (layoutDeckControl == null) layoutDeckControl = findViewById(R.id.layout_deck_control);
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.VISIBLE);

        // 立刻显示左侧卡片详情面板（默认内容），并切换为卡组编辑器模式
        cardDetailPanel.enterDeckEditorMode();

        if (deckEditorManager == null) {
            deckEditorManager = new DeckEditorManager(this, imageLoader, cardDetailPanel);
            deckEditorManager.setListener(new DeckEditorManager.DeckEditorListener() {
                @Override
                public void onDeckModified() {
                }

                @Override
                public void onDeckSaved() {
                }

                @Override
                public void onExitEditor() {
                    hideDeckEditorView();
                    getMainMenuDialog().restoreMainMenu();
                }

                @Override
                public void onCardSelected(Card card) {
                }

                @Override
                public void onSearchResultsUpdated(int count) {
                }

                @Override
                public void onSideDeckFinished(List<Integer> main, List<Integer> extra, List<Integer> side) {
                    if (engine != null) {
                        engine.sendDeckUpdate(main, extra, side);
                    }
                }
            });
        }
        if (layoutDeckEditor != null) {
            deckEditorManager.initialize(layoutDeckEditor);
        }
    }

    private void hideDeckEditorView() {
        if (layoutDeckEditor != null) {
            layoutDeckEditor.setVisibility(View.GONE);
        }
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.GONE);
        cardDetailPanel.exitDeckEditorMode();
    }

    public void setWindowBackground(String relativePath) {
        String path = AppsSettings.get().getResourcePath() + "/" + relativePath;
        if (TextUtils.equals(path, currentBgPath)) {
            return;
        }
        File file = new File(path);
        if (file.exists()) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(path);
                if (bitmap != null) {
                    getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), bitmap));
                    currentBgPath = path;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load background: " + relativePath, e);
            }
        }
    }


    public void showSettingsDialog() {
        getMainMenuDialog().hideMainMenu();
        SettingsDialog dialog = new SettingsDialog(this, () -> applySettingsToEngine());
        dialog.show(dialogContainer);
        dialog.setOnDismissListener(() -> {
            boolean deckEditorShowing = layoutDeckEditor != null
                    && layoutDeckEditor.getVisibility() == View.VISIBLE;
            boolean gameRightShowing = layoutGameRight != null
                    && layoutGameRight.getVisibility() == View.VISIBLE;
            if (!deckEditorShowing && !gameRightShowing) {
                getMainMenuDialog().restoreMainMenu();
            }
        });
    }

    public void applySettingsToEngine() {
        AppsSettings appsSettings = AppsSettings.get();
        boolean enableSound = appsSettings.getIntSettings("chkEnableSound", 1) == 1;
        boolean enableMusic = appsSettings.getIntSettings("chkEnableMusic", 1) == 1;
        if (soundManager != null) {
            soundManager.enableSounds(enableSound);
            soundManager.enableMusic(enableMusic);
        }
        if (deckEditorManager != null) {
            deckEditorManager.refreshLimitList();
        }
    }

    // === EngineListener ===

    @Override
    public void onStateChanged(GameEngine.GameState newState) {
        Log.i(TAG, "State: " + newState);
        switch (newState) {
            case LOBBY:
                if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                    // Already showing player waiting via LanModeDialog, do nothing
                } else {
                    getMainMenuDialog().hideMainMenu();
                }
                break;
            case DECK_SELECT:
                showDeckSelectDialog();
                break;
            case HAND_SELECT:
                showHandSelectDialog();
                break;
            case TP_SELECT:
                showTPSelectDialog();
                break;
            case DUELING:
                getMainMenuDialog().hideMainMenu();
                showGameUI();
                if (lanModeDialog != null) lanModeDialog.dismiss();
                cardDetailPanel.showBottomActions();
                isGameStarted = true;
                break;
            case SIDING:
                showDeckEditorView();              // 打开卡组编辑器
                if (deckEditorManager != null) {
                    deckEditorManager.enterSideMode();  // 记录替换前张数并允许编辑
                }
                break;
            case DUEL_END:
                closeGameButtons();
                showDuelEndDialog();
                break;
            case DISCONNECTED:
                if (!isFinishing()) {
                    runOnUiThread(() -> {
                        if (lanModeDialog != null) {
                            lanModeDialog.showLanMain();
                        }
                    });
                }
                break;
        }
    }

    @Override
    public void onFieldChanged() {
        fieldCtl.invalidate();
        runOnUiThread(() -> fieldCtl.updateCardCountDisplay(engine.getField()));
    }

    @Override
    public void onPlayerInfoUpdated(int player) {
        runOnUiThread(() -> {
            GameEngine.PlayerInfo info = engine.playerInfos[player];
            GameField.PlayerField pf = engine.getField().players[player];
            String defaultName = (player == 0) ? Constants.PlayerName : "Opponent";
            String name = info.name.isEmpty() ? defaultName : info.name;
            fieldCtl.setPlayerDisplay(player, name, String.valueOf(pf.lp));
            fieldCtl.updateCardCountDisplay(engine.getField());
        });
    }

    @Override
    public void onPhaseChanged(int phase) {
        runOnUiThread(() -> {
            fieldCtl.setTurnText(String.valueOf(engine.getField().turnCount));
            isMyTurn = (engine.getField().currentPlayer == engine.getClient().selfType);
            fieldCtl.updateActionButtonsForPhase(phase, isMyTurn);
        });
    }

    @Override
    public void onChatReceived(String player, String message) {
        runOnUiThread(() -> fieldCtl.appendChat(player, message));
    }

    public void toggleChatInput() {
        if (etChatInput == null) return;
        etChatInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etChatInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public void onSelectRequired(int selectType, ByteBuffer data) {
        runOnUiThread(() -> {
            currentSelectType = selectType;
            switch (selectType) {
                case 0:
                    showHandSelectDialog();
                    break;
                case 1:
                    showTPSelectDialog();
                    break;
                case 10:
                    showBattleCmdDialog(data);
                    break;
                case 11:
                    showIdleCmdDialog(data);
                    break;
                case 12:
                    showEffectYnDialog(data);
                    break;
                case 13:
                    showYesNoDialog(data);
                    break;
                case 14:
                    showOptionDialog(data);
                    break;
                case 15:
                    showCardSelectDialog(data);
                    break;
                case 16:
                    showChainSelectDialog(data);
                    break;
                case 18:
                    showPlaceSelectDialog(false);
                    break;
                case 19:
                    showPositionSelectDialog();
                    break;
                case 20:
                    showTributeSelectDialog(data);
                    break;
                case 21:
                    showSortChainDialog(data);
                    break;
                case 22:
                    showCounterSelectDialog(data);
                    break;
                case 23:
                    showSumSelectDialog(data);
                    break;
                case 24:
                    showPlaceSelectDialog(true);
                    break;
                case 25:
                    showSortCardDialog(data);
                    break;
                case 140:
                    showAnnounceRaceDialog();
                    break;
                case 141:
                    showAnnounceAttribDialog();
                    break;
                case 142:
                    showAnnounceCardDialog(data);
                    break;
                case 143:
                    showAnnounceNumberDialog(data);
                    break;
                default:
                    Log.w(TAG, "Unhandled select type: " + selectType);
                    break;
            }
        });
    }

    @Override
    public void onDuelResult(int winner, int reason) {
        fieldCtl.stopTimer();
        runOnUiThread(() -> {
            String result;
            if (winner == 2) {
                result = "平局";
            } else if (winner == engine.getClient().selfType) {
                result = "🎉 你赢了！";
            } else {
                result = "😢 你输了";
            }
            showResultDialog(result);
        });
    }

    @Override
    public void onHintMessage(String hint) {
        runOnUiThread(() -> fieldCtl.showHint(hint, 2000));
    }

    @Override
    public void onReplayData(byte[] data) {
        Log.i(TAG, "Replay data received, size=" + data.length);
    }

    @Override
    public void onTimeLimitUpdate(int player, int leftTime) {
        runOnUiThread(() -> fieldCtl.onTimeLimitUpdate(player, leftTime, engine.getGameTimeLimit()));
    }

    @Override
    public void onChainAnimation(int code, int controler, int location, int sequence) {
        runOnUiThread(() -> fieldCtl.selectCardWithAutoClear(controler, location, sequence, 1500));
    }

    // === Dialog Methods ===

    private int getResId(String name, String type) {
        return getResources().getIdentifier(name, type, getPackageName());
    }

    private View inflateSelectLayout() {
        View contentView = getLayoutInflater().inflate(R.layout.dialog_game_select, null);
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        return contentView;
    }

    private void showHandSelectDialog() {
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("猜拳");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));
        String[] choices = {"石头", "剪刀", "布"};
        for (int i = 0; i < choices.length; i++) {
            Button btn = new Button(this);
            btn.setText(choices[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF006688);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 8;
            btn.setLayoutParams(lp);
            final int result = i + 1;
            btn.setOnClickListener(v -> {
                engine.sendHandResult(result);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showTPSelectDialog() {
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("先攻选择")
                .setMessage("是否选择先攻？")
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("先攻")
                .setNegativeButtonText("后攻")
                .setPositiveButton(v -> engine.sendTPResult(true))
                .setNegativeButton(v -> engine.sendTPResult(false))
                .setCancelable(false);
        dialog.show();
    }


    private void showYesNoDialog(ByteBuffer data) {
        int descId = 0;
        if (data != null && data.remaining() >= 4) {
            descId = data.getInt();
        }
        String desc = descId > 0
                ? DataManager.get().getStringManager().getSystemString(descId, "是否发动效果？")
                : "是否发动效果？";

        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("确认")
                .setMessage(desc)
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("是")
                .setNegativeButtonText("否")
                .setPositiveButton(v -> {
                    sendResponseInt(1);
                    hideCancelOrFinishButton();
                    dialog.dismiss();
                })
                .setNegativeButton(v -> {
                    sendResponseInt(0);
                    hideCancelOrFinishButton();
                    dialog.dismiss();
                })
                .setCancelable(false)
                .setOnDismissListener(() -> {
                    hideCancelOrFinishButton();
                    currentDialog = null;  // 若需要保留引用可调整
                });
        showCancelOrFinishButton("否");
        dialog.show();
    }

    private void showOptionDialog(ByteBuffer data) {
        if (data == null) return;
        int count = data.get() & 0xFF;
        List<String> options = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int descId = data.getInt();
            String str = DataManager.get().getStringManager().getSystemString(descId, "Option " + (i + 1));
            options.add(str);
        }

        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("请选择");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));
        for (int i = 0; i < options.size(); i++) {
            Button btn = new Button(this);
            btn.setText(options.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF006688);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 8;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                sendResponseInt(idx);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showEffectYnDialog(ByteBuffer data) {
        showYesNoDialog(data);
    }

    private void showBattleCmdDialog(ByteBuffer data) {
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        for (int i = 0; i < engine.attackableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.attackableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("⚔ " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 1));
        }

        for (int i = 0; i < engine.activatableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.activatableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            String descStr = info.desc > 0
                    ? DataManager.get().getStringManager().getSystemString(info.desc, "效果")
                    : "发动";
            options.add("✦ " + cardName + " - " + descStr);
            final int idx = i;
            actions.add(() -> sendResponseInt(idx << 16));
        }

        if (engine.showM2) {
            options.add("▶ 进入Main2");
            actions.add(() -> sendResponseInt(2));
        }
        if (engine.showEP) {
            options.add("▶ 结束阶段");
            actions.add(() -> sendResponseInt(3));
        }

        if (options.isEmpty()) {
            sendResponseInt(3);
            return;
        }

        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("战斗阶段");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < options.size(); i++) {
            Button btn = new Button(this);
            btn.setText(options.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(12);
            btn.setSingleLine(false);
            String opt = options.get(i);
            if (opt.startsWith("⚔")) {
                btn.setBackgroundColor(0xFF883333);
            } else if (opt.startsWith("✦")) {
                btn.setBackgroundColor(0xFF886633);
            } else {
                btn.setBackgroundColor(0xFF335577);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                actions.get(idx).run();
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showIdleCmdDialog(ByteBuffer data) {
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        for (int i = 0; i < engine.summonableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.summonableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("召唤: " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt(idx << 16));
        }

        for (int i = 0; i < engine.spsummonableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.spsummonableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("特殊召唤: " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 1));
        }

        for (int i = 0; i < engine.reposableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.reposableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("切换表示: " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 2));
        }

        for (int i = 0; i < engine.msetableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.msetableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("盖放(怪兽): " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 3));
        }

        for (int i = 0; i < engine.ssetableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.ssetableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("设置(魔陷): " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 4));
        }

        for (int i = 0; i < engine.activatableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.activatableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            String descStr = info.desc > 0
                    ? DataManager.get().getStringManager().getSystemString(info.desc, "效果")
                    : "发动";
            options.add("发动: " + cardName + " - " + descStr);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 5));
        }

        if (engine.showBP) {
            options.add("▶ 进入战斗阶段");
            actions.add(() -> sendResponseInt(6));
        }
        if (engine.showEP) {
            options.add("▶ 结束阶段");
            actions.add(() -> sendResponseInt(7));
        }
        if (engine.showShuffle) {
            options.add("🔀 洗牌");
            actions.add(() -> sendResponseInt(8));
        }

        if (options.isEmpty()) {
            sendResponseInt(7);
            return;
        }

        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("主要阶段");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < options.size(); i++) {
            Button btn = new Button(this);
            btn.setText(options.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(12);
            btn.setSingleLine(false);
            String opt = options.get(i);
            if (opt.startsWith("召唤")) {
                btn.setBackgroundColor(0xFF338833);
            } else if (opt.startsWith("特殊召唤")) {
                btn.setBackgroundColor(0xFF338888);
            } else if (opt.startsWith("切换表示")) {
                btn.setBackgroundColor(0xFF555588);
            } else if (opt.startsWith("盖放") || opt.startsWith("设置")) {
                btn.setBackgroundColor(0xFF555555);
            } else if (opt.startsWith("发动")) {
                btn.setBackgroundColor(0xFF886633);
            } else {
                btn.setBackgroundColor(0xFF335577);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                actions.get(idx).run();
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showPlaceSelectDialog(boolean isDisfield) {
        fieldCtl.beginPlaceSelect(isDisfield);
    }

    private void sendCardSelectResponse(List<CardSelectInfo> cardInfos, boolean[] selected, int count) {
        ByteBuffer buf = ByteBuffer.allocate(1 + count);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) count);
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                buf.put((byte) cardInfos.get(i).selectSeq);
            }
        }
        engine.sendResponse(buf.array());
    }

    public String getCardDisplayName(int code) {
        if (code <= 0) return "???";
        ocgcore.data.Card card = DataManager.get().getCardManager().getCard(code);
        if (card != null && card.Name != null) return card.Name;
        return "Card#" + code;
    }

    private String getLocationName(int location) {
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

    private static class CardSelectInfo {
        int code, controler, location, sequence, subSeq, selectSeq;

        CardSelectInfo(int code, int ctrl, int loc, int seq, int sub, int selSeq) {
            this.code = code;
            this.controler = ctrl;
            this.location = loc;
            this.sequence = seq;
            this.subSeq = sub;
            this.selectSeq = selSeq;
        }
    }

    private void showPositionSelectDialog() {
        showListDialog("选择表示形式", new String[]{
                "表侧攻击表示", "里侧攻击表示",
                "表侧守备表示", "里侧守备表示"
        }, which -> {
            int pos;
            switch (which) {
                case 0:
                    pos = 0x1;
                    break;
                case 1:
                    pos = 0x2;
                    break;
                case 2:
                    pos = 0x4;
                    break;
                case 3:
                    pos = 0x8;
                    break;
                default:
                    pos = 0x1;
                    break;
            }
            sendResponseInt(pos);
        });
    }

    private void showDeckSelectDialog() {
        File deckDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_DECK_PATH);
        File[] deckFiles = deckDir.exists()
                ? deckDir.listFiles((dir, name) -> name.endsWith(Constants.YDK_FILE_EX))
                : null;

        List<String> deckNames = new ArrayList<>();
        if (deckFiles != null && deckFiles.length > 0) {
            for (File f : deckFiles) {
                deckNames.add(f.getName().replace(Constants.YDK_FILE_EX, ""));
            }
        } else {
            deckNames.add("（暂无卡组文件）");
        }

        final File[] finalDeckFiles = deckFiles;
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("选择卡组");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < deckNames.size(); i++) {
            Button btn = new Button(this);
            btn.setText(deckNames.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int pos = i;
            btn.setOnClickListener(v -> {
                if (finalDeckFiles != null && pos < finalDeckFiles.length) {
                    loadAndSendDeck(finalDeckFiles[pos]);
                }
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }

        dialog.setType(YesOrNoDialog.TYPE_MESSAGE)
                .setPositiveButtonText("取消")
                .setPositiveButton(v -> engine.disconnect())
                .setCancelable(false);
        dialog.show();
    }

    private void loadAndSendDeck(File ydkFile) {
        new Thread(() -> {
            List<Integer> main = new ArrayList<>();
            List<Integer> extra = new ArrayList<>();
            List<Integer> side = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new FileReader(ydkFile))) {
                String line;
                int section = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.equalsIgnoreCase("#main")) {
                        section = 1;
                        continue;
                    }
                    if (line.equalsIgnoreCase("#extra")) {
                        section = 2;
                        continue;
                    }
                    if (line.equalsIgnoreCase("!side")) {
                        section = 3;
                        continue;
                    }
                    try {
                        int code = Integer.parseInt(line);
                        switch (section) {
                            case 1:
                                main.add(code);
                                break;
                            case 2:
                                extra.add(code);
                                break;
                            case 3:
                                side.add(code);
                                break;
                        }
                    } catch (NumberFormatException e) { /* skip */ }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load deck: " + ydkFile.getName(), e);
                mainHandler.post(() -> showHintMessage("卡组加载失败"));
                return;
            }

            engine.sendDeckUpdate(main, extra, side);
            mainHandler.post(() -> {
                showHintMessage("卡组已发送: " + main.size() + "+" + extra.size() + "+" + side.size());
            });
        }, "DeckLoad").start();
    }

    private void showSideSelectDialog() {
        showHintMessage("副卡组替换 - 请在大厅中选择卡组");
    }

    private void showChainSelectDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 2) {
            sendResponseInt(-1);
            return;
        }
        int count = data.get() & 0xFF;
        int specount = data.get() & 0xFF;
        if (data.remaining() < 8) {
            sendResponseInt(-1);
            return;
        }
        int hint0 = data.getInt();
        int hint1 = data.getInt();

        List<String> chainOptions = new ArrayList<>();
        List<Integer> chainFlags = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 14; i++) {
            int flag = data.get() & 0xFF;
            int forced = data.get() & 0xFF;
            flag |= forced << 8;
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int desc = data.getInt();

            String cardName = getCardDisplayName(code);
            String descStr = desc > 0 ? DataManager.get().getStringManager().getSystemString(desc, "效果") : "效果";
            chainOptions.add(cardName + " - " + descStr);
            chainFlags.add(flag);
        }

        if (chainOptions.isEmpty()) {
            sendResponseInt(-1);
            return;
        }

        boolean hasForced = false;
        for (int f : chainFlags) {
            if ((f & 0x100) != 0) {
                hasForced = true;
                break;
            }
        }

        if (hasForced) {
            for (int i = 0; i < chainFlags.size(); i++) {
                if ((chainFlags.get(i) & 0x100) != 0) {
                    sendResponseInt(i);
                    return;
                }
            }
        }

        YesOrNoDialog dialog = new YesOrNoDialog(this);
        currentDialog = dialog;
        dialog.setTitle("连锁选择");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < chainOptions.size(); i++) {
            Button btn = new Button(this);
            btn.setText(chainOptions.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor((chainFlags.get(i) & 0x100) != 0 ? 0xFFAA3333 : 0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                sendResponseInt(idx);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }

        dialog.setType(YesOrNoDialog.TYPE_MESSAGE)
                .setPositiveButtonText("不连锁")
                .setPositiveButton(v -> {
                    sendResponseInt(-1);
                    hideCancelOrFinishButton();
                })
                .setCancelable(false)
                .setOnDismissListener(() -> {
                    hideCancelOrFinishButton();
                    currentDialog = null;
                });
        if (!hasForced) {
            showCancelOrFinishButton("不连锁");
        }
        dialog.show();
    }

    private void showCardSelectDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 4) {
            sendResponseInt(0);
            return;
        }
        int selectType = data.get() & 0xFF;
        int selectPlayer = data.get() & 0xFF;
        int min = data.get() & 0xFF;
        int max = data.get() & 0xFF;

        boolean cancelable = (min == 0);

        List<CardSelectInfo> cardInfos = new ArrayList<>();
        int count = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
            cardInfos.add(new CardSelectInfo(code, ctrl, loc, seq, subSeq, i));
        }

        if (cardInfos.isEmpty()) {
            sendResponseInt(0);
            return;
        }

        boolean[] selected = new boolean[cardInfos.size()];

        YesOrNoDialog dialog = new YesOrNoDialog(this);
        currentDialog = dialog;
        dialog.setTitle("选择卡片 (" + min + "-" + max + ")");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        TextView tvCount = new TextView(this);
        tvCount.setTextColor(0xFFFFFF00);
        tvCount.setTextSize(14);
        tvCount.setText("已选: 0/" + max);
        layoutOptions.addView(tvCount);

        for (int i = 0; i < cardInfos.size(); i++) {
            CardSelectInfo info = cardInfos.get(i);
            Button btn = new Button(this);
            String cardName = getCardDisplayName(info.code);
            String locName = getLocationName(info.location);
            btn.setText(cardName + " [" + locName + "]");
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(12);
            btn.setSingleLine(false);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);

            final int idx = i;
            btn.setOnClickListener(v -> {
                selected[idx] = !selected[idx];
                int selCount = 0;
                for (boolean b : selected) if (b) selCount++;
                tvCount.setText("已选: " + selCount + "/" + max);
                btn.setBackgroundColor(selected[idx] ? 0xFF00AA44 : 0xFF335577);

                if (selCount >= min && selCount <= max) {
                    sendCardSelectResponse(cardInfos, selected, selCount);
                    hideCancelOrFinishButton();
                    dialog.dismiss();
                }
                updateCancelOrFinishButton(selCount >= min, cancelable, selCount > 0);
            });
            layoutOptions.addView(btn);
        }

        if (cancelable) {
            showCancelOrFinishButton("取消");
            dialog.setType(YesOrNoDialog.TYPE_MESSAGE)
                    .setPositiveButtonText("取消")
                    .setPositiveButton(v -> {
                        ByteBuffer buf = ByteBuffer.allocate(1);
                        buf.put((byte) 0);
                        engine.sendResponse(buf.array());
                        hideCancelOrFinishButton();
                    });
        } else if (min <= max) {
            showCancelOrFinishButton("完成选择");
        }
        dialog.setCancelable(false)
                .setOnDismissListener(() -> {
                    hideCancelOrFinishButton();
                    currentDialog = null;
                });
        dialog.show();
    }

    private void showTributeSelectDialog(ByteBuffer data) {
        showCardSelectDialog(data);
    }

    private void showSortChainDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 1) {
            sendResponseInt(0);
            return;
        }
        int count = data.get() & 0xFF;
        if (count <= 1) {
            sendResponseInt(0);
            return;
        }
        showHintMessage("连锁排序: 自动排序");
        sendResponseInt(0);
    }

    private void showCounterSelectDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 5) {
            sendResponseInt(0);
            return;
        }
        int player = data.get() & 0xFF;
        int counterType = data.getShort() & 0xFFFF;
        int count = data.get() & 0xFF;
        int descId = data.remaining() >= 4 ? data.getInt() : 0;

        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("选择指示物数量");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 1; i <= count; i++) {
            Button btn = new Button(this);
            btn.setText(String.valueOf(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int val = i;
            btn.setOnClickListener(v -> {
                sendResponseInt(val);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }


    private void showSumSelectDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 7) {
            sendResponseInt(0);
            return;
        }

        int selectMode = data.get() & 0xFF;
        int selectingPlayer = data.get() & 0xFF;
        int sumVal = data.get() & 0xFF;
        int minCount = data.get() & 0xFF;
        int maxCount = data.get() & 0xFF;
        int mustCount = data.get() & 0xFF;

        sumSelectValue = sumVal;
        sumSelectMin = minCount;
        sumSelectMax = maxCount;

        List<SumCardInfo> mustCards = new ArrayList<>();
        for (int i = 0; i < mustCount && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int opParam = data.remaining() >= 4 ? data.getInt() : 0;
            int value = (selectMode == 0) ? opParam : (opParam >> 16);
            if (value <= 0) value = 1;
            mustCards.add(new SumCardInfo(code, ctrl, loc, seq, opParam, value, i));
        }

        int count = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        sumCardInfos = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int opParam = data.remaining() >= 4 ? data.getInt() : 0;
            int value = (selectMode == 0) ? opParam : (opParam >> 16);
            if (value <= 0) value = 1;
            sumCardInfos.add(new SumCardInfo(code, ctrl, loc, seq, opParam, value, i));
        }

        sumSelected = new boolean[sumCardInfos.size()];
        for (SumCardInfo must : mustCards) {
            for (int i = 0; i < sumCardInfos.size(); i++) {
                SumCardInfo sc = sumCardInfos.get(i);
                if (sc.code == must.code && sc.controler == must.controler
                        && sc.location == must.location && sc.sequence == must.sequence) {
                    sumSelected[i] = true;
                    break;
                }
            }
        }

        isSelectingSum = true;
        showSumSelectUI(mustCards.size());
    }

    private void showSumSelectUI(int mustCount) {
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("选择卡片 (总和=" + sumSelectValue + ")");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        TextView tvSum = new TextView(this);
        tvSum.setTextColor(0xFFFFFF00);
        tvSum.setTextSize(14);
        layoutOptions.addView(tvSum);

        int currentSum = 0;
        int selectedCount = 0;
        for (int i = 0; i < sumCardInfos.size(); i++) {
            if (sumSelected[i]) {
                currentSum += sumCardInfos.get(i).value;
                selectedCount++;
            }
        }
        tvSum.setText("当前: " + currentSum + " / " + sumSelectValue + " (已选" + selectedCount + "张)");

        for (int i = 0; i < sumCardInfos.size(); i++) {
            SumCardInfo info = sumCardInfos.get(i);
            Button btn = new Button(this);
            String cardName = getCardDisplayName(info.code);
            btn.setText(cardName + " [值:" + info.value + "]");
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(12);
            btn.setSingleLine(false);
            btn.setBackgroundColor(sumSelected[i] ? 0xFF00AA44 : 0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);

            final int idx = i;
            btn.setOnClickListener(v -> {
                sumSelected[idx] = !sumSelected[idx];
                int newSum = 0;
                int newCount = 0;
                for (int j = 0; j < sumCardInfos.size(); j++) {
                    if (sumSelected[j]) {
                        newSum += sumCardInfos.get(j).value;
                        newCount++;
                    }
                }
                tvSum.setText("当前: " + newSum + " / " + sumSelectValue + " (已选" + newCount + "张)");
                btn.setBackgroundColor(sumSelected[idx] ? 0xFF00AA44 : 0xFF335577);

                if (newSum == sumSelectValue && newCount >= sumSelectMin && newCount <= sumSelectMax) {
                    sendSumResponse();
                    dialog.dismiss();
                }
            });
            layoutOptions.addView(btn);
        }

        if (sumSelectMin == 0) {
            dialog.setType(YesOrNoDialog.TYPE_MESSAGE)
                    .setPositiveButtonText("取消")
                    .setPositiveButton(v -> sendResponseInt(0));
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void sendSumResponse() {
        int selCount = 0;
        for (boolean b : sumSelected) if (b) selCount++;
        ByteBuffer buf = ByteBuffer.allocate(1 + selCount);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) selCount);
        for (int i = 0; i < sumSelected.length; i++) {
            if (sumSelected[i]) {
                buf.put((byte) sumCardInfos.get(i).index);
            }
        }
        engine.sendResponse(buf.array());
    }

    private void showAnnounceRaceDialog() {
        String[] races = {"战士", "魔法师", "炎", "水", "雷", "岩石", "植物", "兽",
                "兽战士", "恐龙", "昆虫", "爬虫", "海龙", "鱼", "机械", "超能",
                "幻神兽", "创造神", "龙"};
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("选择种族");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < races.length; i++) {
            Button btn = new Button(this);
            btn.setText(races[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int raceBit = 1 << i;
            btn.setOnClickListener(v -> {
                sendResponseInt(raceBit);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showAnnounceAttribDialog() {
        String[] attribs = {"光", "暗", "水", "炎", "地", "风", "神"};
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("选择属性");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < attribs.length; i++) {
            Button btn = new Button(this);
            btn.setText(attribs[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int attrBit = 1 << i;
            btn.setOnClickListener(v -> {
                sendResponseInt(attrBit);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showAnnounceCardDialog(ByteBuffer data) {
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("宣言卡片");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (10 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        EditText input = new EditText(this);
        input.setHint("输入卡片名称搜索...");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF888888);
        input.setSingleLine(true);
        root.addView(input);

        LinearLayout resultLayout = new LinearLayout(this);
        resultLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultLayout);

        final int[] selectedCode = {0};
        final TextView tvSelected = new TextView(this);
        tvSelected.setTextColor(0xFFFFFF00);
        tvSelected.setTextSize(13);
        tvSelected.setText("未选择卡片");
        root.addView(tvSelected);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                resultLayout.removeAllViews();
                String query = s.toString().trim();
                if (query.length() < 2) return;
                SparseArray<Card> allCards = DataManager.get().getCardManager().getAllCards();
                List<Card> results = new ArrayList<>();
                for (int i = 0; i < allCards.size(); i++) {
                    Card c = allCards.valueAt(i);
                    if (c.containsName(query)) {
                        results.add(c);
                        if (results.size() >= 10) break;
                    }
                }
                int limit = Math.min(results.size(), 10);
                for (int i = 0; i < limit; i++) {
                    Card c = results.get(i);
                    Button btn = new Button(YGOProActivity.this);
                    btn.setText(c.Name + " [" + c.Code + "]");
                    btn.setTextColor(0xFFFFFFFF);
                    btn.setBackgroundColor(0xFF335577);
                    btn.setTextSize(12);
                    btn.setSingleLine(true);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = 2;
                    btn.setLayoutParams(lp);
                    final int code = c.Code;
                    btn.setOnClickListener(v -> {
                        selectedCode[0] = code;
                        tvSelected.setText("已选择: " + c.Name);
                    });
                    resultLayout.addView(btn);
                }
            }
        });

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);
        dialog.setContentView(scrollView);
        dialog.setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("确认")
                .setNegativeButtonText("取消")
                .setPositiveButton(v -> {
                    if (selectedCode[0] > 0) {
                        sendResponseInt(selectedCode[0]);
                    }
                })
                .setNegativeButton(v -> sendResponseInt(0))
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                .setCancelable(false);
        dialog.show();
    }

    private void showAnnounceNumberDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 1) {
            sendResponseInt(0);
            return;
        }
        int count = data.get() & 0xFF;
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            numbers.add(data.getInt());
        }
        if (numbers.isEmpty()) {
            sendResponseInt(0);
            return;
        }

        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("选择数字");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < numbers.size(); i++) {
            Button btn = new Button(this);
            btn.setText(String.valueOf(numbers.get(i)));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int val = numbers.get(i);
            btn.setOnClickListener(v -> {
                sendResponseInt(val);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    public void showCardInfoPanel(GameField.ClientCard card) {
        cardDetailPanel.showCardInfo(card);
    }

    private void showSortCardDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 1) {
            sendResponseInt(0);
            return;
        }
        int player = data.get() & 0xFF;
        int count = data.get() & 0xFF;
        if (count <= 1) {
            byte[] resp = new byte[count];
            for (int i = 0; i < count; i++) resp[i] = (byte) i;
            engine.sendResponse(resp);
            return;
        }

        List<SortCardInfo> sortCards = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            sortCards.add(new SortCardInfo(code, ctrl, loc, seq, i));
        }

        int[] sortList = new int[count];
        int[] currentOrder = {0};

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("卡片排序 (按顺序点击)");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        TextView tvOrder = new TextView(this);
        tvOrder.setTextColor(0xFFFFFF00);
        tvOrder.setTextSize(13);
        tvOrder.setText("点击顺序: 0/" + count);
        layoutOptions.addView(tvOrder);

        for (int i = 0; i < sortCards.size(); i++) {
            SortCardInfo info = sortCards.get(i);
            Button btn = new Button(this);
            String cardName = getCardDisplayName(info.code);
            btn.setText(cardName);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            btn.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);

            final int idx = i;
            btn.setOnClickListener(v -> {
                if (sortList[idx] != 0) return;
                currentOrder[0]++;
                sortList[idx] = currentOrder[0];
                btn.setText(currentOrder[0] + ". " + cardName);
                btn.setBackgroundColor(0xFF00AA44);
                tvOrder.setText("点击顺序: " + currentOrder[0] + "/" + count);

                if (currentOrder[0] == count) {
                    byte[] resp = new byte[count];
                    for (int j = 0; j < count; j++) {
                        resp[j] = (byte) (sortList[j] - 1);
                    }
                    engine.sendResponse(resp);
                    dialog.dismiss();
                }
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private static class SortCardInfo {
        int code, controler, location, sequence, index;

        SortCardInfo(int code, int ctrl, int loc, int seq, int idx) {
            this.code = code;
            this.controler = ctrl;
            this.location = loc;
            this.sequence = seq;
            this.index = idx;
        }
    }

    public void showResultDialog(String result) {
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("决斗结果")
                .setMessage(result)
                .setPositiveButton(v -> finish())
                .setCancelable(false);
        dialog.show();
    }

    private void showDuelEndDialog() {
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle("决斗结束")
                .setMessage("本次决斗已结束")
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("确定")
                .setNegativeButtonText("继续等待")
                .setPositiveButton(v -> finish())
                .setCancelable(false);
        dialog.show();
    }

    public void showHintMessage(String msg) {
        fieldCtl.showHint(msg, 3000);
    }

    private interface OnItemPickedListener {
        void onPicked(int which);
    }

    private void showListDialog(String title, String[] items, OnItemPickedListener listener) {
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle(title);
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));
        for (int i = 0; i < items.length; i++) {
            Button btn = new Button(this);
            btn.setText(items[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF006688);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 8;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onPicked(idx);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    // === Response helpers ===

    public void sendResponseInt(int value) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(value);
        engine.sendResponse(buf.array());
    }

    private void sendActionResponse(int action) {
        sendResponseInt(action);
    }

    private void closeGameButtons() {
        cardDetailPanel.closeGameButtons();
    }

    // === CancelOrFinish (mirrors C++ ClientField::CancelOrFinish) ===

    private void showCancelOrFinishButton(String text) {
        cardDetailPanel.showCancelOrFinishButton(text);
    }

    private void hideCancelOrFinishButton() {
        cardDetailPanel.hideCancelOrFinishButton();
    }

    private void updateCancelOrFinishButton(boolean ready, boolean cancelable, boolean hasSelection) {
        cardDetailPanel.updateCancelOrFinishButton(ready, cancelable, hasSelection);
    }

    public void cancelOrFinish() {
        switch (currentSelectType) {
            case 13:
            case 12: {
                sendResponseInt(0);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            case 15:
            case 20: {
                GameField field = engine.getField();
                if (field.selectedCards.isEmpty()) {
                    if (field.selectCancelable) {
                        sendResponseInt(-1);
                        hideCancelOrFinishButton();
                        if (currentDialog != null) currentDialog.dismiss();
                    }
                } else if (field.selectReady) {
                    sendSelectedCardsResponse();
                    hideCancelOrFinishButton();
                    if (currentDialog != null) currentDialog.dismiss();
                }
                break;
            }
            case 23: {
                GameField field = engine.getField();
                if (field.selectReady) {
                    sendSelectedCardsResponse();
                    hideCancelOrFinishButton();
                    if (currentDialog != null) currentDialog.dismiss();
                }
                break;
            }
            case 16:
            case 25: {
                sendResponseInt(-1);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            case 18:
            case 24: {
                if (fieldCtl.cancelPlaceSelect()) {
                    hideCancelOrFinishButton();
                }
                break;
            }
            default: {
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
        }
        currentSelectType = -1;
    }

    private void sendSelectedCardsResponse() {
        GameField field = engine.getField();
        int len = field.selectedCards.size();
        if (len > 255) len = 255;
        byte[] respbuf = new byte[len + 1];
        respbuf[0] = (byte) len;
        for (int i = 0; i < len; i++) {
            respbuf[i + 1] = (byte) field.selectedCards.get(i).select_seq;
        }
        engine.sendResponse(respbuf);
    }


    // === Lifecycle ===

    @Override
    protected void onResume() {
        super.onResume();
        setupFullScreen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DraggablePopupHelper.resetAllPositions(this);
        if (engine != null) {
            engine.release();
        }
        if (soundManager != null) {
            soundManager.release();
        }
        if (imageLoader != null) {
            imageLoader.close();
        }
        TextureLoader.get().release();
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mainMenuDialog != null && mainMenuDialog.isShowing()) {
                    soundManager.stopBGM();
                    finish();
                    return;
                }
                if (!isGameStarted) {
                    getMainMenuDialog().restoreMainMenu();
                    return;
                }
                YesOrNoDialog dialog = new YesOrNoDialog(YGOProActivity.this);
                dialog.setTitle("退出决斗")
                        .setMessage("确定要退出当前决斗吗？")
                        .setType(YesOrNoDialog.TYPE_YES_NO)
                        .setPositiveButtonText("确定")
                        .setNegativeButtonText("取消")
                        .setPositiveButton(v -> {
                            if (engine != null) {
                                if (engine.getState() == GameEngine.GameState.DUELING) {
                                    engine.sendSurrender();
                                } else {
                                    engine.disconnect();
                                }
                            }
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                        });
                dialog.show();
            }
        });
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupFullScreen();
        }
    }
}
