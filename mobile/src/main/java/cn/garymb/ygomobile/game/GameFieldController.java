package cn.garymb.ygomobile.game;

import android.graphics.Bitmap;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.GameFieldView;
import cn.garymb.ygomobile.render.GameFieldViewController;
import cn.garymb.ygomobile.render.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.CmdMenuDialog;
import ocgcore.enums.DuelPhase;

/**
 * 决斗场管理类：卡片/区域点击与长按、卡片命令菜单、放置区域选择、
 * 高亮、连锁动画、cmdContext 状态；
 * 提示信息 + 聊天气泡；
 * 以及阶段按钮（DP/SP/M1/BP/M2/EP 切换与响应）
 * 以及 GameFieldView 的 Canvas 3D 渲染（场地/区域/场上卡/堆叠区/双方手卡）
 * layout_top_info 区域（双方 LP/名字/手卡数/计时/头像 + 回合数）已迁移至 GameTopInfoManager
 */
public class GameFieldController implements GameFieldView.OnCardClickListener {

    private static final String TAG = "YGONativeGame";
    static final int CMD_CONTEXT_IDLE = 1;
    static final int CMD_CONTEXT_BATTLE = 2;

    private final YGOProActivity activity;
    private final Handler mainHandler;
    private final GameTopInfoManager topInfoManager;
    private GameFieldViewController viewController;
    private GameEngine engine;
    private int cmdContext = 0;
    private boolean isPlaceSelecting = false;
    private CmdMenuDialog cmdMenuDialog;
    private final Random random = new Random();

    private TextView tvHintMessage;
    private FrameLayout layoutChatMessages;
    private TextView tvChatMessage1, tvChatMessage2;
    /** 系统/观战弹幕层：叠加在 layout_top_info（LPbar 区域）上一层，XML 中位于其后的子视图 */
    private FrameLayout layoutDanmaku;
    // 表情气泡：显示在发送方头像下方（对齐 gframe drawing.cpp DrawEmoticon），超时自动隐藏
    private static final long EMOTE_BUBBLE_DURATION_MS = 3000;
    private ImageView ivPlayerEmoteBubble, ivOpponentEmoteBubble;
    private final Runnable hidePlayerEmoteBubble = () -> {
        if (ivPlayerEmoteBubble != null) ivPlayerEmoteBubble.setVisibility(View.GONE);
    };
    private final Runnable hideOpponentEmoteBubble = () -> {
        if (ivOpponentEmoteBubble != null) ivOpponentEmoteBubble.setVisibility(View.GONE);
    };

    // 阶段按钮状态：按钮本体由 GameFieldView 场内绘制（双方怪兽区之间、平行屏幕），
    // 控制器仅维护显示状态并按通讯协议应答点击
    private boolean phaseCurrentVisible = false;
    private String phaseCurrentLabel = "";
    private String phaseNextLabel = "";
    private boolean phaseEpVisible = false;

    public GameFieldController(YGOProActivity activity, Handler mainHandler, GameTopInfoManager topInfoManager) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.topInfoManager = topInfoManager;
    }

    public void create() {
        viewController = new GameFieldViewController(activity);
        bindChatViews();
        setupPhaseButtons();
    }

    private void bindChatViews() {
        tvHintMessage = activity.findViewById(R.id.tv_hint_message);
        layoutChatMessages = activity.findViewById(R.id.layout_chat_messages);
        tvChatMessage1 = activity.findViewById(R.id.tv_chat_message_1);
        tvChatMessage2 = activity.findViewById(R.id.tv_chat_message_2);
        layoutDanmaku = activity.findViewById(R.id.layout_danmaku);
        ivPlayerEmoteBubble = activity.findViewById(R.id.iv_player_emote_bubble);
        ivOpponentEmoteBubble = activity.findViewById(R.id.iv_opponent_emote_bubble);
    }

    /**
     * 阶段按钮由 GameFieldView 场内绘制：控制器只监听点击并按协议应答。
     * 下一阶段：idle 命令(selectType=11)下 BP→6，battle 命令(selectType=10)下 M2→2；
     * 结束阶段：selectType=10→3，selectType=11→7
     */
    private void setupPhaseButtons() {
        if (viewController == null) return;
        viewController.setPhaseButtonListener(new GameFieldView.OnPhaseButtonListener() {
            @Override
            public void onPhaseNextClicked() {
                if (activity.getEngine() == null || activity.getEngine().getClient() == null)
                    return;
                if ("BP".equals(phaseNextLabel) && activity.getCurrentSelectType() == 11) {
                    activity.sendResponseInt(6);
                } else if ("M2".equals(phaseNextLabel) && activity.getCurrentSelectType() == 10) {
                    activity.sendResponseInt(2);
                }
            }

            @Override
            public void onPhaseEpClicked() {
                if (activity.getEngine() == null || activity.getEngine().getClient() == null)
                    return;
                if (activity.getCurrentSelectType() == 10) {
                    activity.sendResponseInt(3);
                } else if (activity.getCurrentSelectType() == 11) {
                    activity.sendResponseInt(7);
                }
            }
        });
    }

    private void pushPhaseDisplay() {
        if (viewController != null) {
            viewController.setPhaseDisplay(phaseCurrentVisible, phaseCurrentLabel,
                    phaseNextLabel, phaseEpVisible);
        }
    }

    public void init(GameEngine engine, ImageLoader imageLoader) {
        this.engine = engine;
        viewController.init(engine.getField(), imageLoader, this);
    }

    public void show() {
        if (viewController != null) viewController.show();
        if (topInfoManager != null) topInfoManager.show();
    }

    public void hide() {
        if (viewController != null) viewController.hide();
        if (topInfoManager != null) topInfoManager.hide();
        if (layoutChatMessages != null) layoutChatMessages.setVisibility(View.GONE);
        if (cmdMenuDialog != null) cmdMenuDialog.dismiss();
        hideDuelHint();
        // 清场时清空双方聊天记录与进行中的弹幕
        myChatLines.clear();
        opChatLines.clear();
        clearDanmaku();
        // 清场时兜底退出大厅聊天模式（下次 showPlayerWaiting 会重新进入）
        lobbyChatMode = false;
        if (lobbyChatContainer != null) lobbyChatContainer.removeAllViews();
        // 清场时隐藏表情气泡并撤销延时隐藏任务
        if (ivPlayerEmoteBubble != null) ivPlayerEmoteBubble.setVisibility(View.GONE);
        if (ivOpponentEmoteBubble != null) ivOpponentEmoteBubble.setVisibility(View.GONE);
        mainHandler.removeCallbacks(hidePlayerEmoteBubble);
        mainHandler.removeCallbacks(hideOpponentEmoteBubble);
        phaseCurrentVisible = false;
        phaseNextLabel = "";
        phaseEpVisible = false;
        pushPhaseDisplay();
    }

    public void invalidate() {
        viewController.invalidate();
    }

    public void selectCardWithAutoClear(int controler, int location, int sequence, int durationMs) {
        viewController.selectCardWithAutoClear(controler, location, sequence, durationMs);
    }

    void setCmdContext(int context) {
        cmdContext = context;
    }

    // === 场上命令模式（主阶/战斗阶段：直接点击场上卡片操作，不弹模态对话框） ===

    public void beginIdleCommand() {
        setCmdContext(CMD_CONTEXT_IDLE);
        if (engine == null) return;
        // 与原弹窗逻辑一致：无任何可执行操作时直接结束阶段
        if (!engine.hasIdleCommands()) {
            activity.sendResponseInt(7);
            return;
        }
        // 通讯（MSG_SELECT_IDLE_CMD）允许进入结束阶段
        setEpButtonAllowed(true);
        showHint("点击手牌或场上卡片进行操作", 2500);
    }

    public void beginBattleCommand() {
        setCmdContext(CMD_CONTEXT_BATTLE);
        if (engine == null) return;
        if (!engine.hasBattleCommands()) {
            activity.sendResponseInt(3);
            return;
        }
        // 通讯（MSG_SELECT_BATTLE_CMD）允许进入结束阶段
        setEpButtonAllowed(true);
        showHint("点击卡片进行攻击或发动", 2500);
    }

    // === 放置区域选择 ===

    public void beginPlaceSelect(boolean isDisfield) {
        isPlaceSelecting = true;
        int mask = engine.selectFieldMask;
        viewController.highlightField(mask);
        String msg = isDisfield ? "请选择要禁用的区域" : "请选择放置位置";
        showHint(msg, 3000);
    }

    /**
     * 自动放置（复刻 gframe duelclient.cpp MSG_SELECT_PLACE 自动放置 L2211-2264）：
     * chkMAutoPos/chkSTAutoPos 勾选且可选区域含怪兽区/魔陷区时，按优先级
     *（我方怪兽→我方魔陷→我方灵摆→对方怪兽→对方魔陷→对方灵摆）选区自动应答
     * byte[3]{player, location, sequence}；chkRandomPos 决定普通区是否随机取位
     *
     * @return true=已自动放置并应答，false=需弹窗手动选择
     */
    public boolean tryAutoPlaceSelect() {
        if (engine == null) return false;
        AppsSettings settings = AppsSettings.get();
        int mask = engine.selectFieldMask;
        // 对齐 gframe 条件：怪兽区可选(0x7f007f)看 chkMAutoPos，否则看 chkSTAutoPos
        if ((mask & 0x7f007f) != 0) {
            if (settings.getIntSettings("chkMAutoPos", 0) != 1) return false;
        } else {
            if (settings.getIntSettings("chkSTAutoPos", 0) != 1) return false;
        }

        int filter;
        int respLocation;
        int respPlayer;
        boolean pzone;
        if ((mask & 0x7f) != 0) {
            respPlayer = engine.localPlayer(0);
            respLocation = 0x04;
            filter = mask & 0x7f;
            pzone = false;
        } else if ((mask & 0x3f00) != 0) {
            respPlayer = engine.localPlayer(0);
            respLocation = 0x08;
            filter = (mask >> 8) & 0x3f;
            pzone = false;
        } else if ((mask & 0xc000) != 0) {
            respPlayer = engine.localPlayer(0);
            respLocation = 0x08;
            filter = (mask >> 14) & 0x3;
            pzone = true;
        } else if ((mask & 0x7f0000) != 0) {
            respPlayer = engine.localPlayer(1);
            respLocation = 0x04;
            filter = (mask >> 16) & 0x7f;
            pzone = false;
        } else if ((mask & 0x3f000000) != 0) {
            respPlayer = engine.localPlayer(1);
            respLocation = 0x08;
            filter = (mask >> 24) & 0x3f;
            pzone = false;
        } else if ((mask & 0xc0000000) != 0) {
            respPlayer = engine.localPlayer(1);
            respLocation = 0x08;
            filter = (mask >>> 30) & 0x3;
            pzone = true;
        } else {
            return false;
        }

        int seq;
        if (!pzone) {
            if (settings.getIntSettings("chkRandomPos", 0) == 1) {
                // 随机取位（对齐 gframe chkRandomPos：dist(0,6) 直到命中可选位）
                do {
                    seq = random.nextInt(7);
                } while ((filter & (1 << seq)) == 0);
            } else {
                // 固定次序（对齐 gframe：0x40→6, 0x20→5, 0x4→2, 0x2→1, 0x8→3, 0x1→0, 0x10→4）
                if ((filter & 0x40) != 0) seq = 6;
                else if ((filter & 0x20) != 0) seq = 5;
                else if ((filter & 0x4) != 0) seq = 2;
                else if ((filter & 0x2) != 0) seq = 1;
                else if ((filter & 0x8) != 0) seq = 3;
                else if ((filter & 0x1) != 0) seq = 0;
                else seq = 4;
            }
        } else {
            // 灵摆区：序列固定 6(左)/7(右)，gframe 不对灵摆区随机取位
            seq = (filter & 0x1) != 0 ? 6 : 7;
        }

        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.put((byte) respPlayer);
        buf.put((byte) respLocation);
        buf.put((byte) seq);
        engine.sendResponse(buf.array());
        return true;
    }

    public boolean cancelPlaceSelect() {
        if (!isPlaceSelecting) return false;
        // 协议应答需要协议侧玩家索引：localPlayer 为对合映射，
        // localPlayer(0) 即我方对应的协议索引（先攻=0/后攻=1），
        // selfType 是座位号，换座场景下不能直接使用
        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.put((byte) engine.localPlayer(0));
        buf.put((byte) 0);
        buf.put((byte) 0);
        engine.sendResponse(buf.array());
        isPlaceSelecting = false;
        viewController.clearHighlight();
        return true;
    }

    // === 区域点击处理（来自 DuelFieldManager） ===

    @Override
    public void onZoneClick(int player, int location, int sequence, float tapX, float tapY) {
        if (engine == null) return;
        GameField field = engine.getField();
        if (isPlaceSelecting) {
            handlePlaceSelection(player, location, sequence);
            return;
        }
        GameField.ClientCard card = field.getCard(player, location, sequence);
        if (card != null && card.cmdFlag != 0) {
            showCardCommandMenu(card, tapX, tapY);
            return;
        }
        // 无可执行命令：关闭残留的命令菜单，只显示卡片信息
        dismissCmdMenu();
        // 堆叠区点击：查看卡片信息
        boolean isPile = (location == 0x01 || location == 0x10
                || location == 0x20 || location == 0x40);
        if (isPile && card != null && card.code > 0) {
            activity.showCardInfoPanel(card);
            return;
        }
        // 场上卡片点击：查看信息
        if (card != null && card.code > 0) {
            activity.showCardInfoPanel(card);
        }
    }

    private void handlePlaceSelection(int player, int location, int sequence) {
        int bitPos = getZoneBitPos(player, location, sequence);
        if (bitPos < 0 || (engine.selectFieldMask & (1 << bitPos)) == 0) {
            showHint("该区域不可选择", 3000);
            return;
        }
        isPlaceSelecting = false;
        viewController.clearHighlight();

        // player 为本地方位索引(0=我方,1=对方)，协议响应需转换为协议侧玩家索引
        //（localPlayer 为对合映射：本地索引 → 协议索引）
        int respPlayer = engine.localPlayer(player & 1);
        int respLocation;
        if (location == 0x04) {
            respLocation = 0x04;
        } else if (location == 0x08) {
            respLocation = 0x08;
        } else {
            respLocation = location;
        }
        int respSeq = sequence;

        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.put((byte) respPlayer);
        buf.put((byte) respLocation);
        buf.put((byte) respSeq);
        engine.sendResponse(buf.array());
    }

    private int getZoneBitPos(int player, int location, int sequence) {
        // player 为本地方位索引(0=我方,1=对方)，mask 已归一化：0-15=我方, 16-31=对方
        int base = (player == 0) ? 0 : 16;
        if (location == 0x04) return base + sequence;
        if (location == 0x08) {
            if (sequence < 6) return base + 8 + sequence;
            if (sequence == 6) return base + 14;
            if (sequence == 7) return base + 15;
        }
        return -1;
    }

    // === 卡片命令菜单 ===

    /**
     * 菜单构建逻辑已迁移至 CmdMenuDialog.showCardCommandMenu：
     * 此处仅转发触点与当前命令上下文（idle/battle）
     */
    private void showCardCommandMenu(GameField.ClientCard card, float tapX, float tapY) {
        if (cmdMenuDialog == null) {
            cmdMenuDialog = new CmdMenuDialog(activity);
        }
        cmdMenuDialog.showCardCommandMenu(card, engine, cmdContext,
                viewController != null ? viewController.getView() : null, tapX, tapY);
    }

    /** 本次点击无可执行命令时，关闭残留的旧菜单 */
    private void dismissCmdMenu() {
        if (cmdMenuDialog != null) cmdMenuDialog.dismiss();
    }

    // === 提示信息 ===

    /** 提示定时隐藏任务：showHint 与 showDuelHint 共用，避免多次延时任务叠加导致提前隐藏 */
    private final Runnable hideHintRunnable = () -> {
        if (tvHintMessage != null) tvHintMessage.setVisibility(View.GONE);
    };

    public void showHint(String msg, int durationMs) {
        mainHandler.removeCallbacks(hideHintRunnable);
        tvHintMessage.setText(msg);
        tvHintMessage.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(hideHintRunnable, durationMs);
    }

    /**
     * 通讯提示（对齐 gframe stHintMsg）：显示后持续，由下一条通讯消息或显式隐藏
     * （调用方 GameEngine.onGameMsg / onWaiting / onSelectXxx，见 stHintMsg 调用点）
     */
    public void showDuelHint(String text) {
        mainHandler.removeCallbacks(hideHintRunnable);
        tvHintMessage.setText(text);
        tvHintMessage.setVisibility(View.VISIBLE);
    }

    /** 隐藏通讯提示（对齐 duelclient.cpp ClientAnalyze 开头 stHintMsg->setVisible(false)） */
    public void hideDuelHint() {
        mainHandler.removeCallbacks(hideHintRunnable);
        if (tvHintMessage != null) tvHintMessage.setVisibility(View.GONE);
    }

    // === 聊天消息（对齐 gframe game.cpp AddChatMsg + drawing.cpp DrawChatMsg） ===

    /** 每侧玩家聊天最大行数：超过 5 行向上滚动（清除第一条，最新一条落在最下行） */
    private static final int MAX_CHAT_LINES = 5;

    private final LinkedList<String> myChatLines = new LinkedList<>();
    private final LinkedList<String> opChatLines = new LinkedList<>();

    public void appendChat(int playerType, String message) {
        // player waiting 大厅模式：所有系统消息与玩家聊天进入 layout_danmaku 静态列表，
        // 不走决斗内的分侧聊天/弹幕逻辑（决斗开始时由 exitLobbyChatMode 切回）
        if (lobbyChatMode) {
            appendLobbyChat(playerType, message);
            return;
        }
        AppsSettings settings = AppsSettings.get();
        // 对齐 gframe duelclient.cpp STOC_CHAT：停用聊天（chkDisableChatting，对应 chkIgnore1）时丢弃全部消息
        if (settings.getIntSettings("chkDisableChatting", 0) == 1) return;
        if (message == null) message = "";
        if (playerType >= 0 && playerType < 4) {
            // 玩家消息（座位号：0/1 我方队首+tag，2/3 对方队首+tag）：
            // 表情编码不走文字行，在发送方头像下方显示图片气泡（对齐 gframe DrawEmoticon）
            if (isEmoticonCode(message)) {
                showEmoteBubble(playerType, message);
                return;
            }
            // 我方（含我方 tag 同伴）→ tv_chat_message_1；对方（含对方 tag）→ tv_chat_message_2。
            boolean selfSide = isChatSelfSide(playerType);
            appendSideChat(selfSide, chatNickname(playerType) + ": " + message);
        } else {
            // 系统/脚本错误/观战消息：对齐 chkIgnore2，观战者（11-19）可屏蔽
            if (playerType >= 11 && playerType <= 19
                    && settings.getIntSettings("chkMuteSpectators", 0) == 1) return;
            showChatDanmaku(playerType, message);
        }
    }

    /** 聊天昵称：优先 STOC_HS_PLAYER_ENTER 记录的座位名（对齐 game.cpp AddChatMsg 的昵称前缀） */
    private String chatNickname(int seat) {
        if (engine != null && seat >= 0 && seat < engine.seatNames.length) {
            String name = engine.seatNames[seat];
            if (name != null && !name.isEmpty()) return name;
        }
        if (engine != null && engine.getClient() != null
                && seat == engine.getClient().selfType) {
            return engine.getPlayerName();
        }
        return "Player" + (seat + 1);
    }

    /**
     * 聊天消息是否我方发送（对齐 gframe game.cpp ChatLocalPlayer 的边判定）：
     * STOC_CHAT 的 playerType 是大厅座位号——
     * 1v1：座位 0/1 分属双方，座位号本身即协议侧边索引（不能 >>1，否则座位 1 会被误判为队伍 0）；
     * tag：座位 0/1 属我方队（队首+tag 同伴）、2/3 属对方队，座位 >>1 才是协议侧队伍索引
     * （ChatLocalPlayer 中 tag 座位 1<->2 互换后 0/2 同侧、1/3 同侧即此对应）。
     * isSelfSide 与牌局渲染同一套 localPlayer 映射（换先攻自动翻边）
     */
    private boolean isChatSelfSide(int playerType) {
        if (engine == null) return false;
        boolean isTag = engine.getGameMode() == 2;
        int team = isTag ? (playerType >> 1) : playerType;
        return engine.isSelfSide(team);
    }

    /** 我方/对方聊天各占一个 TextView：每条换行，超过 5 行清除第一条（向上滚动），宽度不超过上方 LPbar */
    private void appendSideChat(boolean selfSide, String line) {
        TextView tv = selfSide ? tvChatMessage1 : tvChatMessage2;
        if (tv == null) return;
        LinkedList<String> lines = selfSide ? myChatLines : opChatLines;
        lines.addLast(line);
        while (lines.size() > MAX_CHAT_LINES) {
            lines.removeFirst();
        }
        // 对齐 drawing.cpp 玩家聊天 maxwidth：最大长度不超过上方 LPbar
        int maxW = selfSide ? topInfoManager.getPlayerLpBarWidth()
                : topInfoManager.getOpponentLpBarWidth();
        if (maxW > 0) tv.setMaxWidth(maxW);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        tv.setText(sb.toString());
        tv.setVisibility(View.VISIBLE);
        if (layoutChatMessages != null) layoutChatMessages.setVisibility(View.VISIBLE);
    }

    // === 系统/观战消息弹幕（对齐 drawing.cpp DrawChatMsg chatType>=4 分支） ===

    /** 弹幕最大行数：从上到下最多 5 行，超过后循环回第 1 行 */
    private static final int DANMAKU_MAX_ROWS = 5;
    /** 弹幕匀速（dp/ms）：时长 = 总路程 / 速度，所有消息速度一致 */
    private static final float DANMAKU_SPEED_DP_PER_MS = 0.08f;
    /** 弹幕行高（dp）：5 行总高约 80dp，与 layout_top_info 高度相当 */
    private static final float DANMAKU_ROW_HEIGHT_DP = 16f;
    /** 观战弹幕颜色，逐一对齐 drawing.cpp chatColor[11..19]（11=红 12=绿 13=蓝 14=青 15=品红 16=黄 17=白 18=灰 19=深灰） */
    private static final int[] DANMAKU_OBS_COLORS = {
            0xFFFF4040, 0xFF40FF40, 0xFF4040FF, 0xFF40FFFF, 0xFFFF40FF,
            0xFFFFFF40, 0xFFFFFFFF, 0xFF808080, 0xFF404040
    };
    /** 聊天消息半透明黑底（对齐 drawing.cpp L1597 draw2DRectangle 0xa0000000） */
    private static final int CHAT_BG_COLOR = 0xA0000000;

    private int danmakuRowIndex = 0;
    private final List<TextView> danmakuViews = new ArrayList<>();

    /** 大厅（player waiting）聊天模式：全部消息在 layout_danmaku 静态列表显示 */
    private boolean lobbyChatMode = false;
    /** 大厅聊天最大条数：超过时移除最上方最旧的一条 */
    private static final int MAX_LOBBY_CHAT_LINES = 10;
    /** 大厅聊天列表容器：每条消息一个 TextView（独立半透明黑底），旧→新从上往下排列 */
    private LinearLayout lobbyChatContainer;

    /** 清除全部进行中的弹幕（停止聊天/离开决斗界面时调用） */
    private void clearDanmaku() {
        for (TextView tv : danmakuViews) {
            tv.animate().cancel();
            if (layoutDanmaku != null) layoutDanmaku.removeView(tv);
        }
        danmakuViews.clear();
        danmakuRowIndex = 0;
    }

    // === player waiting 大厅聊天模式 ===

    /**
     * 进入大厅聊天模式：所有系统消息与玩家聊天在 layout_danmaku 中按
     * 从上往下、旧到新的静态列表显示（每条一个 TextView、半透明黑底），
     * 最多 10 条，超出移除最上方最旧的一条；
     * 决斗内分侧聊天（tv_chat_message_1/2）与弹幕滚动在此期间停用
     */
    public void enterLobbyChatMode() {
        lobbyChatMode = true;
        clearDanmaku();
        if (layoutChatMessages != null) layoutChatMessages.setVisibility(View.GONE);
        if (layoutDanmaku == null) return;
        if (lobbyChatContainer == null) {
            lobbyChatContainer = new LinearLayout(activity);
            lobbyChatContainer.setOrientation(LinearLayout.VERTICAL);
            float density = activity.getResources().getDisplayMetrics().density;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            lp.leftMargin = (int) (8 * density);
            lp.rightMargin = (int) (8 * density);
            layoutDanmaku.addView(lobbyChatContainer, lp);
        }
        lobbyChatContainer.removeAllViews();
        layoutDanmaku.setVisibility(View.VISIBLE);
    }

    /** 决斗开始：退出大厅聊天模式，恢复决斗内玩家分侧聊天 + 系统/观战弹幕 */
    public void exitLobbyChatMode() {
        if (!lobbyChatMode) return;
        lobbyChatMode = false;
        if (lobbyChatContainer != null) lobbyChatContainer.removeAllViews();
        clearDanmaku();
    }

    /** 大厅聊天列表追加：旧→新从上往下排列，每条独立半透明黑底；超过 10 条移除最上方旧消息；颜色对齐 drawing.cpp chatColor */
    private void appendLobbyChat(int playerType, String message) {
        if (lobbyChatContainer == null) return;
        if (message == null) message = "";
        // 表情编码在大厅没有头像气泡可依附，直接忽略
        if (isEmoticonCode(message)) return;
        String text;
        int color;
        if (playerType >= 0 && playerType < 4) {
            // 玩家消息：昵称: 内容（颜色对齐 chatColor[0..3] 白色）
            text = chatNickname(playerType) + ": " + message;
            color = 0xFFFFFFFF;
        } else if (playerType == 8) {
            text = "[System]: " + message;
            color = 0xFF8080FF;                       // chatColor[8]
        } else if (playerType == 9) {
            text = "[Script Error]: " + message;
            color = 0xFFFF4040;                       // chatColor[9]
        } else if (playerType == 10) {
            text = "[********]: " + message;
            color = 0xFFFF4040;                       // chatColor[10]
        } else {
            // 观战者 11-19（无前缀）与其他未知类型
            text = message;
            color = (playerType >= 11 && playerType <= 19)
                    ? DANMAKU_OBS_COLORS[playerType - 11] : 0xFFFFFFFF;
        }
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);  // 与 tv_chat_message 统一 9sp
        tv.setTextColor(color);
        tv.setBackgroundColor(CHAT_BG_COLOR);               // 对齐 drawing.cpp draw2DRectangle 0xa0000000
        tv.setShadowLayer(1f, 1f, 1f, 0xFF000000);
        float density = activity.getResources().getDisplayMetrics().density;
        int hPadding = (int) (3 * density);
        tv.setPadding(hPadding, 0, hPadding, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (2 * density);          // 每条之间留间隙，黑底不连成整块
        lobbyChatContainer.addView(tv, lp);
        // 超过 10 条：移除最上方最旧的一条
        while (lobbyChatContainer.getChildCount() > MAX_LOBBY_CHAT_LINES) {
            lobbyChatContainer.removeViewAt(0);
        }
    }

    /**
     * 系统/脚本错误/观战消息以弹幕形式横向滚动显示：
     * 对齐 drawing.cpp L1587-1593：chatType>=4 时 offsetX = (1200 - chatTiming[i]) * 4，
     * 消息自右向左匀速移动直至离场消失；颜色对齐 chatColor[chatType]：
     * 8 系统=0xFF8080FF，9 脚本错误/10 隐藏名=0xFFFF4040，11-19 观战=chatColor[11..19] 轮换。
     * 前缀对齐 game.cpp AddChatMsg：8→"[System]: "、9→"[Script Error]: "、10→"[********]: "、
     * 观战 11-19 无前缀（default 分支不追加）。
     * 弹幕层叠加在 layout_top_info 上一层，行位从上到下循环（最多 5 行）
     */
    private void showChatDanmaku(int playerType, String message) {
        if (message == null || message.isEmpty()) return;
        if (layoutDanmaku == null) return;
        if (layoutDanmaku.getWidth() <= 0) {
            // 首帧尚未布局完成：延后到布局后再入场
            layoutDanmaku.post(() -> showChatDanmaku(playerType, message));
            return;
        }
        String text;
        int color;
        if (playerType == 8) {
            text = "[System]: " + message;
            color = 0xFF8080FF;                       // chatColor[8]
        } else if (playerType == 9) {
            text = "[Script Error]: " + message;
            color = 0xFFFF4040;                       // chatColor[9]
        } else if (playerType == 10) {
            text = "[********]: " + message;
            color = 0xFFFF4040;                       // chatColor[10]
        } else {
            text = message;
            color = (playerType >= 11 && playerType <= 19)
                    ? DANMAKU_OBS_COLORS[playerType - 11] : 0xFFFFFFFF;
        }
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);   // 与 tv_chat_message 统一 9sp
        tv.setTextColor(color);
        tv.setSingleLine(true);
        tv.setBackgroundColor(CHAT_BG_COLOR); // 对齐 drawing.cpp draw2DRectangle 0xa0000000
        int hPadding = (int) (3 * activity.getResources().getDisplayMetrics().density);
        tv.setPadding(hPadding, 0, hPadding, 0);
        // 对齐 drawing.cpp shadowloc：黑色 1px 偏移阴影，保证血条背景上可读
        tv.setShadowLayer(1f, 1f, 1f, 0xFF000000);
        int row = danmakuRowIndex % DANMAKU_MAX_ROWS; // 超过 5 行循环回第 1 行
        danmakuRowIndex++;
        int rowHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                DANMAKU_ROW_HEIGHT_DP, activity.getResources().getDisplayMetrics());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        lp.topMargin = row * rowHeight;               // LPbar 区域上一层，从上到下分行
        lp.leftMargin = layoutDanmaku.getWidth();     // 起点：顶部信息区右缘之外
        layoutDanmaku.addView(tv, lp);
        danmakuViews.add(tv);
        float density = activity.getResources().getDisplayMetrics().density;
        tv.post(() -> {
            // 匀速：总路程 = 弹幕层宽度 + 自身宽度（一直移动到 activity 最左侧消失）
            int distance = layoutDanmaku.getWidth() + tv.getWidth();
            long duration = Math.max(1, (long) (distance / (DANMAKU_SPEED_DP_PER_MS * density)));
            tv.animate().translationX(-distance).setDuration(duration)
                    .withEndAction(() -> {
                        danmakuViews.remove(tv);
                        layoutDanmaku.removeView(tv);
                    }).start();
        });
    }

    /** 隐藏聊天消息文本（对齐 gframe BUTTON_CHATTING 切换关闭时的 ClearChatMsg：清空聊天显示） */
    public void clearChatMessages() {
        myChatLines.clear();
        opChatLines.clear();
        clearDanmaku();   // clearChatMessages() 末尾：停止聊天时清空弹幕
        if (tvChatMessage1 != null) {
            tvChatMessage1.setText("");
            tvChatMessage1.setVisibility(View.GONE);
        }
        if (tvChatMessage2 != null) {
            tvChatMessage2.setText("");
            tvChatMessage2.setVisibility(View.GONE);
        }
    }

    private boolean isEmoticonCode(String message) {
        if (message == null || message.isEmpty()) return false;
        for (String code : TextureLoader.EMOTICON_KEYS) {
            if (code.equals(message)) return true;
        }
        return false;
    }

    /** 将表情图片气泡显示到发送方头像下方，并刷新自动隐藏计时 */
    private void showEmoteBubble(int playerType, String code) {
        if (engine == null) return;
        // STOC_CHAT 座位号：与文字聊天同一套分边规则（1v1 用座位、tag 用座位>>1）
        boolean selfSide = isChatSelfSide(playerType);
        ImageView bubble = selfSide ? ivPlayerEmoteBubble : ivOpponentEmoteBubble;
        if (bubble == null) return;
        Bitmap bmp = TextureLoader.get().getEmoticon(code);
        if (bmp == null || bmp.isRecycled()) return;
        bubble.setImageBitmap(bmp);
        bubble.setVisibility(View.VISIBLE);
        Runnable hide = selfSide ? hidePlayerEmoteBubble : hideOpponentEmoteBubble;
        mainHandler.removeCallbacks(hide);
        mainHandler.postDelayed(hide, EMOTE_BUBBLE_DURATION_MS);
    }

    // === 阶段按钮 ===

    /**
     * 按通讯显示/隐藏结束阶段按钮：仅当服务端下发
     * MSG_SELECT_IDLE_CMD(selectType=11) / MSG_SELECT_BATTLE_CMD(selectType=10)
     * 请求本地行动时，才允许进入结束阶段
     */
    public void setEpButtonAllowed(boolean allowed) {
        phaseEpVisible = allowed;
        pushPhaseDisplay();
    }

    public void updateActionButtonsForPhase(int phase, boolean isMyTurn) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return;

        phaseCurrentVisible = isMyTurn;
        // 阶段切换即重置：下一阶段/结束阶段按钮由通讯（阶段与指令请求）重新驱动
        phaseNextLabel = "";
        phaseEpVisible = false;

        switch (dp) {
            case Draw:
                phaseCurrentLabel = "DP";
                break;
            case Standby:
                phaseCurrentLabel = "SP";
                break;
            case Main1:
                phaseCurrentLabel = "M1";
                if (isMyTurn) phaseNextLabel = "BP";
                break;
            case BattleStart:
            case BattleStep:
            case Damage:
            case DamageCal:
            case Battle:
                phaseCurrentLabel = "BP";
                if (isMyTurn) phaseNextLabel = "M2";
                break;
            case Main2:
                phaseCurrentLabel = "M2";
                break;
            case End:
                phaseCurrentLabel = "EP";
                break;
            default:
                phaseCurrentLabel = dp.name();
                break;
        }
        pushPhaseDisplay();
    }

    /**
     * 录像回放时仅更新阶段文字，不处理可见性
     */
    public void setPhaseByValue(int phase) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return;
        switch (dp) {
            case Draw:
                phaseCurrentLabel = "DP";
                break;
            case Standby:
                phaseCurrentLabel = "SP";
                break;
            case Main1:
                phaseCurrentLabel = "M1";
                break;
            case BattleStart:
            case BattleStep:
            case Battle:
            case Damage:
            case DamageCal:
                phaseCurrentLabel = "BP";
                break;
            case Main2:
                phaseCurrentLabel = "M2";
                break;
            case End:
                phaseCurrentLabel = "EP";
                break;
            default:
                phaseCurrentLabel = dp.name();
                break;
        }
        pushPhaseDisplay();
    }

    public void setPhaseText(String text) {
        phaseCurrentLabel = text == null ? "" : text;
        pushPhaseDisplay();
    }

    /**
     * 对局结束时清空并隐藏阶段按钮（配合 CardDetailPanel.closeGameButtons）
     */
    public void closePhaseButtons() {
        phaseCurrentVisible = false;
        phaseCurrentLabel = "";
        phaseNextLabel = "";
        phaseEpVisible = false;
        pushPhaseDisplay();
    }

    // === GameFieldView.OnCardClickListener ===

    @Override
    public void onCardClick(int player, int location, int sequence, float tapX, float tapY) {
        Log.d(TAG, "Card click: p=" + player + " loc=" + location + " seq=" + sequence);
        if (engine == null) return;
        GameField.ClientCard card = engine.getField().getCard(player, location, sequence);
        if (card != null && card.cmdFlag != 0) {
            showCardCommandMenu(card, tapX, tapY);
            return;
        }
        // 无可执行命令：关闭残留的命令菜单
        dismissCmdMenu();
        if (card != null) {
            // 手卡确认由 GameFieldView 场内动画完成（抬高/翻面+虚线框），不弹卡面展示
            if (location == 0x02) return;
            activity.showCardInfoPanel(card);
        }
    }

    @Override
    public void onFieldLongPress(int player, int location, int sequence) {
        Log.d(TAG, "Long press: p=" + player + " loc=" + location + " seq=" + sequence);
        GameField.ClientCard card = engine.getField().getCard(player, location, sequence);
        if (card != null && card.code > 0) {
            activity.showCardInfoPanel(card);
        }
    }
}