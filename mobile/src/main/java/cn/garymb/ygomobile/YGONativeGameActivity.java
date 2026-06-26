package cn.garymb.ygomobile;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.network.DuelClient;
import cn.garymb.ygomobile.render.GameFieldView;
import cn.garymb.ygomobile.render.TextureLoader;
import cn.garymb.ygodata.YGOGameOptions;
import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.enums.DuelPhase;

public class YGONativeGameActivity extends AppCompatActivity implements
        GameEngine.EngineListener,
        GameFieldView.OnCardClickListener {

    private static final String TAG = "YGONativeGame";

    private GameEngine engine;
    private SoundManager soundManager;
    private ImageLoader imageLoader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private GameFieldView gameFieldView;
    private TextView tvPlayerLp, tvPlayerName, tvOpponentLp, tvOpponentName;
    private TextView tvPhaseInfo, tvTurnInfo;
    private TextView tvPlayerTime, tvOpponentTime;
    private TextView tvHintMessage;
    private TextView tvChatLog;
    private TextView tvPlayerHandCount, tvOpponentHandCount;
    private ImageView ivPlayerAvatar, ivOpponentAvatar;
    private LinearLayout layoutActionButtons;
    private Button btnM2, btnEp, btnBp, btnChain, btnCancel;
    private LinearLayout layoutChat;
    private EditText etChatInput;
    private FrameLayout dialogContainer;
    private LinearLayout layoutLobby;
    private TextView tvLobbyStatus;
    private Button btnLobbyReady, btnLobbyLeave;

    private String chatHistory = "";
    private boolean isMyTurn = false;
    private volatile boolean isGameStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setupFullScreen();
        setContentView(R.layout.activity_ygo_game);

        initViews();
        initEngine();
        loadData();
        handleIntent(getIntent());
        setupBackPressedHandler();

        soundManager.playBGM(SoundManager.BGM.DUEL);
    }

    private void setupFullScreen() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void initViews() {
        gameFieldView = findViewById(R.id.game_field_view);
        gameFieldView.setCardClickListener(this);

        tvPlayerLp = findViewById(R.id.tv_player_lp);
        tvPlayerName = findViewById(R.id.tv_player_name);
        tvOpponentLp = findViewById(R.id.tv_opponent_lp);
        tvOpponentName = findViewById(R.id.tv_opponent_name);
        tvPhaseInfo = findViewById(R.id.tv_phase_info);
        tvTurnInfo = findViewById(R.id.tv_turn_info);
        tvPlayerTime = findViewById(R.id.tv_player_time);
        tvOpponentTime = findViewById(R.id.tv_opponent_time);
        tvHintMessage = findViewById(R.id.tv_hint_message);
        tvChatLog = findViewById(R.id.tv_chat_log);
        tvPlayerHandCount = findViewById(R.id.tv_opponent_hand_count);
        ivPlayerAvatar = findViewById(R.id.iv_player_avatar);
        ivOpponentAvatar = findViewById(R.id.iv_opponent_avatar);

        layoutActionButtons = findViewById(R.id.layout_action_buttons);
        btnM2 = findViewById(R.id.btn_m2);
        btnEp = findViewById(R.id.btn_ep);
        btnBp = findViewById(R.id.btn_bp);
        btnChain = findViewById(R.id.btn_chain);
        btnCancel = findViewById(R.id.btn_cancel);

        layoutChat = findViewById(R.id.layout_chat);
        etChatInput = findViewById(R.id.et_chat_input);
        Button btnChatSend = findViewById(R.id.btn_chat_send);

        dialogContainer = findViewById(R.id.dialog_container);
        layoutLobby = findViewById(R.id.layout_lobby);
        tvLobbyStatus = findViewById(R.id.tv_lobby_status);
        btnLobbyReady = findViewById(R.id.btn_lobby_ready);
        btnLobbyLeave = findViewById(R.id.btn_lobby_leave);

        setupButtonListeners(btnChatSend);
        setupAvatarImages();
    }

    private void setupAvatarImages() {
        Bitmap myAvatar = TextureLoader.get().getAvatar(true);
        if (myAvatar != null) ivPlayerAvatar.setImageBitmap(myAvatar);
        Bitmap opAvatar = TextureLoader.get().getAvatar(false);
        if (opAvatar != null) ivOpponentAvatar.setImageBitmap(opAvatar);
    }

    private void setupButtonListeners(Button btnChatSend) {
        btnM2.setOnClickListener(v -> {
            sendActionResponse(2);
        });
        btnEp.setOnClickListener(v -> {
            sendActionResponse(3);
        });
        btnBp.setOnClickListener(v -> {
            sendActionResponse(1);
        });
        btnChain.setOnClickListener(v -> {
            sendActionResponse(4);
        });
        btnCancel.setOnClickListener(v -> {
            sendActionResponse(-1);
        });

        btnLobbyReady.setOnClickListener(v -> {
            if (engine != null) engine.sendReady();
        });
        btnLobbyLeave.setOnClickListener(v -> {
            if (engine != null) engine.disconnect();
            finish();
        });

        btnChatSend.setOnClickListener(v -> {
            String msg = etChatInput.getText().toString().trim();
            if (!TextUtils.isEmpty(msg) && engine != null) {
                engine.sendChat(msg);
                appendChat("Me", msg);
                etChatInput.setText("");
            }
        });
    }

    private void initEngine() {
        soundManager = new SoundManager(this);
        soundManager.init(0.8, 0.6, true, true);

        imageLoader = new ImageLoader(true);

        engine = new GameEngine(soundManager);
        engine.setListener(this);
        engine.setPlayerName(Constants.PlayerName);

        gameFieldView.setField(engine.getField());
        gameFieldView.setImageLoader(imageLoader);

        TextureLoader.get().init();
    }

    private void loadData() {
        new Thread(() -> {
            DataManager.get().load(false);
            Log.i(TAG, "DataManager loaded");
        }, "DataLoad").start();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        YGOGameOptions options = intent.getParcelableExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY);
        if (options != null) {
            long time = intent.getLongExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_TIME, 0);
            if (System.currentTimeMillis() - time < YGOGameOptions.TIME_OUT) {
                joinFromOptions(options);
                return;
            }
        }

        String host = intent.getStringExtra("host");
        int port = intent.getIntExtra("port", 7911);
        String room = intent.getStringExtra("room");

        if (!TextUtils.isEmpty(host)) {
            engine.connectToServer(host, port, false,
                    room != null ? room : "", "",
                    0, 0, 5, 8000, 5, 1, 0, false, false);
        } else if (intent.getBooleanExtra("botMode", false)) {
            engine.setBotMode(true);
            engine.connectToServer("127.0.0.1", 7911, true,
                    "Bot Game", "",
                    5, 0, 5, 8000, 5, 1, 0, true, false);
            engine.startBotDuel("127.0.0.1", 7911,
                    "WindBot", "", "Normal");
        }
    }

    private void joinFromOptions(YGOGameOptions options) {
        String host = options.mServerAddr;
        int port = options.mPort;
        String room = options.mRoomName != null ? options.mRoomName : "";
        String user = options.mUserName != null ? options.mUserName : Constants.PlayerName;
        engine.setPlayerName(user);
        engine.connectToServer(host, port, false, room, "",
                0, 0, 5, 8000, 5, 1, 0, false, false);
    }

    // === EngineListener ===

    @Override
    public void onStateChanged(GameEngine.GameState newState) {
        Log.i(TAG, "State: " + newState);
        switch (newState) {
            case LOBBY:
                layoutLobby.setVisibility(View.VISIBLE);
                tvLobbyStatus.setText("已连接 - 等待玩家准备");
                break;
            case DECK_SELECT:
                layoutLobby.setVisibility(View.GONE);
                showDeckSelectDialog();
                break;
            case HAND_SELECT:
                showHandSelectDialog();
                break;
            case TP_SELECT:
                showTPSelectDialog();
                break;
            case DUELING:
                layoutLobby.setVisibility(View.GONE);
                layoutActionButtons.setVisibility(View.GONE);
                layoutChat.setVisibility(View.VISIBLE);
                isGameStarted = true;
                break;
            case SIDING:
                showSideSelectDialog();
                break;
            case DUEL_END:
                showDuelEndDialog();
                break;
            case DISCONNECTED:
                if (!isFinishing()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "连接已断开", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                break;
        }
    }

    @Override
    public void onFieldChanged() {
        runOnUiThread(() -> {
            gameFieldView.invalidate();
        });
    }

    @Override
    public void onPlayerInfoUpdated(int player) {
        runOnUiThread(() -> {
            GameEngine.PlayerInfo info = engine.playerInfos[player];
            GameField.PlayerField pf = engine.getField().players[player];
            if (player == 0) {
                tvPlayerLp.setText("LP: " + pf.lp);
                tvPlayerName.setText(info.name.isEmpty() ? Constants.PlayerName : info.name);
            } else {
                tvOpponentLp.setText("LP: " + pf.lp);
                tvOpponentName.setText(info.name.isEmpty() ? "Opponent" : info.name);
                int handCount = engine.getField().getCardCount(player, ocgcore.enums.CardLocation.Hand.value());
                tvPlayerHandCount.setText("Hand: " + handCount);
            }
        });
    }

    @Override
    public void onPhaseChanged(int phase) {
        runOnUiThread(() -> {
            DuelPhase dp = DuelPhase.valueOf(phase);
            String phaseName = dp != null ? dp.name() : "Unknown";
            tvPhaseInfo.setText(phaseName + " Phase");
            tvTurnInfo.setText("Turn " + engine.getField().turnCount);
            isMyTurn = (engine.getField().currentPlayer == engine.getClient().selfType);
            layoutActionButtons.setVisibility(isMyTurn ? View.VISIBLE : View.GONE);
            updateActionButtonsForPhase(phase);
        });
    }

    private void updateActionButtonsForPhase(int phase) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return;
        switch (dp) {
            case Main1:
                btnBp.setVisibility(View.VISIBLE);
                btnEp.setVisibility(View.GONE);
                btnM2.setVisibility(View.GONE);
                break;
            case Main2:
                btnBp.setVisibility(View.GONE);
                btnEp.setVisibility(View.VISIBLE);
                btnM2.setVisibility(View.GONE);
                break;
            case BattleStep:
                btnBp.setVisibility(View.GONE);
                btnEp.setVisibility(View.VISIBLE);
                btnM2.setVisibility(View.VISIBLE);
                break;
            default:
                btnBp.setVisibility(View.GONE);
                btnEp.setVisibility(View.VISIBLE);
                btnM2.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public void onChatReceived(String player, String message) {
        runOnUiThread(() -> appendChat(player, message));
    }

    private void appendChat(String player, String message) {
        chatHistory += "[" + player + "] " + message + "\n";
        tvChatLog.setText(chatHistory);
        ScrollView scrollChat = findViewById(R.id.scroll_chat);
        scrollChat.post(() -> scrollChat.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    public void onSelectRequired(int selectType, ByteBuffer data) {
        runOnUiThread(() -> {
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
                case 20:
                case 23:
                    showCardSelectDialog(data);
                    break;
                case 18:
                case 24:
                    break;
                case 19:
                    showPositionSelectDialog();
                    break;
                default:
                    Log.w(TAG, "Unhandled select type: " + selectType);
                    break;
            }
        });
    }

    @Override
    public void onDuelResult(int winner, int reason) {
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
        runOnUiThread(() -> {
            tvHintMessage.setText(hint);
            tvHintMessage.setVisibility(View.VISIBLE);
            mainHandler.postDelayed(() -> tvHintMessage.setVisibility(View.GONE), 2000);
        });
    }

    @Override
    public void onReplayData(byte[] data) {
        Log.i(TAG, "Replay data received, size=" + data.length);
    }

    @Override
    public void onTimeLimitUpdate(int player, int leftTime) {
        runOnUiThread(() -> {
            String timeStr = (leftTime / 60) + ":" + String.format("%02d", leftTime % 60);
            if (player == 0) {
                tvPlayerTime.setText(timeStr);
                tvPlayerTime.setVisibility(View.VISIBLE);
            } else {
                tvOpponentTime.setText(timeStr);
                tvOpponentTime.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onChainAnimation(int code, int controler, int location, int sequence) {
        runOnUiThread(() -> {
            gameFieldView.setSelectedCard(controler, location, sequence);
            mainHandler.postDelayed(() -> gameFieldView.clearSelection(), 1500);
        });
    }

    // === GameFieldView.OnCardClickListener ===

    @Override
    public void onCardClick(int player, int location, int sequence) {
        Log.d(TAG, "Card click: p=" + player + " loc=" + location + " seq=" + sequence);
        GameField.ClientCard card = engine.getField().getCard(player, location, sequence);
        if (card != null && card.isFaceUp() && card.code > 0) {
            showCardInfoDialog(card.code);
        }
    }

    @Override
    public void onZoneClick(int player, int location, int sequence) {
        Log.d(TAG, "Zone click: p=" + player + " loc=" + location);
    }

    @Override
    public void onFieldLongPress(int player, int location, int sequence) {
        Log.d(TAG, "Long press: p=" + player + " loc=" + location + " seq=" + sequence);
    }

    // === Dialog Methods ===

    private void showHandSelectDialog() {
        new AlertDialog.Builder(this)
                .setTitle("猜拳")
                .setMessage("请选择石头、剪刀或布")
                .setPositiveButton("石头", (d, w) -> {
                    engine.sendHandResult(1);
                    d.dismiss();
                })
                .setNeutralButton("剪刀", (d, w) -> {
                    engine.sendHandResult(2);
                    d.dismiss();
                })
                .setNegativeButton("布", (d, w) -> {
                    engine.sendHandResult(3);
                    d.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void showTPSelectDialog() {
        new AlertDialog.Builder(this)
                .setTitle("先攻选择")
                .setMessage("是否选择先攻？")
                .setPositiveButton("先攻", (d, w) -> {
                    engine.sendTPResult(true);
                    d.dismiss();
                })
                .setNegativeButton("后攻", (d, w) -> {
                    engine.sendTPResult(false);
                    d.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void showYesNoDialog(ByteBuffer data) {
        int descId = 0;
        if (data != null && data.remaining() >= 4) {
            descId = data.getInt();
        }
        String desc = descId > 0
                ? DataManager.get().getStringManager().getSystemString(descId, "是否发动效果？")
                : "是否发动效果？";

        new AlertDialog.Builder(this)
                .setTitle("确认")
                .setMessage(desc)
                .setPositiveButton("是", (d, w) -> {
                    sendResponseInt(1);
                    d.dismiss();
                })
                .setNegativeButton("否", (d, w) -> {
                    sendResponseInt(0);
                    d.dismiss();
                })
                .setCancelable(false)
                .show();
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
        String[] items = options.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("请选择")
                .setItems(items, (d, which) -> {
                    sendResponseInt(which);
                    d.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void showEffectYnDialog(ByteBuffer data) {
        showYesNoDialog(data);
    }

    private void showBattleCmdDialog(ByteBuffer data) {
        new AlertDialog.Builder(this)
                .setTitle("战斗阶段")
                .setItems(new String[]{"攻击", "切换为守备表示", "结束战斗阶段"}, (d, which) -> {
                    sendResponseInt(which);
                    d.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void showIdleCmdDialog(ByteBuffer data) {
        new AlertDialog.Builder(this)
                .setTitle("主要阶段")
                .setItems(new String[]{
                        "召唤", "特殊召唤", "放置", "发动", "切换表示",
                        "设置", "进入战斗阶段", "进入结束阶段"
                }, (d, which) -> {
                    sendResponseInt(which);
                    d.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void showCardSelectDialog(ByteBuffer data) {
        showHintMessage("请在场地中选择卡片");
    }

    private void showPositionSelectDialog() {
        new AlertDialog.Builder(this)
                .setTitle("选择表示形式")
                .setItems(new String[]{
                        "表侧攻击表示", "里侧攻击表示",
                        "表侧守备表示", "里侧守备表示"
                }, (d, which) -> {
                    int pos;
                    switch (which) {
                        case 0: pos = 0x1; break;
                        case 1: pos = 0x2; break;
                        case 2: pos = 0x4; break;
                        case 3: pos = 0x8; break;
                        default: pos = 0x1; break;
                    }
                    sendResponseInt(pos);
                    d.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void showDeckSelectDialog() {
        showHintMessage("请选择你的卡组");
    }

    private void showSideSelectDialog() {
        showHintMessage("请替换副卡组");
    }

    private void showCardInfoDialog(int cardCode) {
        Card card = DataManager.get().getCardManager().getCard(cardCode);
        String name = card != null ? card.Name : "Unknown Card";
        if (name == null) name = "Unknown Card";
        new AlertDialog.Builder(this)
                .setTitle(name + " (" + cardCode + ")")
                .setPositiveButton("关闭", (d, w) -> d.dismiss())
                .show();
    }

    private void showResultDialog(String result) {
        new AlertDialog.Builder(this)
                .setTitle("决斗结果")
                .setMessage(result)
                .setPositiveButton("确定", (d, w) -> {
                    d.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void showDuelEndDialog() {
        new AlertDialog.Builder(this)
                .setTitle("决斗结束")
                .setMessage("本次决斗已结束")
                .setPositiveButton("确定", (d, w) -> {
                    d.dismiss();
                    finish();
                })
                .setNegativeButton("继续等待", (d, w) -> {
                    d.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void showHintMessage(String msg) {
        tvHintMessage.setText(msg);
        tvHintMessage.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(() -> tvHintMessage.setVisibility(View.GONE), 3000);
    }

    // === Response helpers ===

    private void sendResponseInt(int value) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putInt(value);
        engine.sendResponse(buf.array());
    }

    private void sendActionResponse(int action) {
        sendResponseInt(action);
        layoutActionButtons.setVisibility(View.GONE);
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
                new AlertDialog.Builder(YGONativeGameActivity.this)
                        .setTitle("退出决斗")
                        .setMessage("确定要退出当前决斗吗？")
                        .setPositiveButton("确定", (d, w) -> {
                            if (engine != null) {
                                engine.sendSurrender();
                            }
                            d.dismiss();
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                        })
                        .setNegativeButton("取消", (d, w) -> d.dismiss())
                        .show();
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
