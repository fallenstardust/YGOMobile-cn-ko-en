package cn.garymb.ygomobile.game.engine;

import android.util.Log;

import cn.garymb.ygomobile.render.card.CardDetailPanel;

/** 对局视角管理（自 GameEngine 拆出）：本地视角显示名/座位昵称绑定、观战判定与切视角；状态（playerInfos/seatNames/tagPlayer/duelIsFirst/pendingSpectatorSwap）仍留在引擎，构造注入引擎引用。 */
public class DuelViewpointManager {
    private static final String TAG = "GameEngine";

    private final GameEngine engine;

    DuelViewpointManager(GameEngine engine) {
        this.engine = engine;
    }

    /** 本地视角（0=我方/1=对方，或观战视角的左右侧）显示名：playerInfos 已按视角绑定（bindViewNames / 回放 startSession），tag 模式该队 tag_player 在打时取队友名 nameTag（对齐 drawing.cpp 的 hostname/hostname_tag 分支） */
    public String displayName(int localIdx) {
        if (localIdx < 0 || localIdx >= engine.playerInfos.length) return "";
        // Solo 模式下优先取等待面板中房主为席位代选的卡组名（slot0=我方、slot1=对方）；未选卡时回落到协议回显的玩家昵称，避免空列
        if (engine.soloMode && localIdx < engine.soloSeatDeckNames.length
                && engine.soloSeatDeckNames[localIdx] != null && !engine.soloSeatDeckNames[localIdx].isEmpty()) {
            return engine.soloSeatDeckNames[localIdx];
        }
        GameEngine.PlayerInfo info = engine.playerInfos[localIdx];
        if (engine.field.isTag && localIdx < engine.tagPlayer.length && engine.tagPlayer[localIdx]
                && info.nameTag != null && !info.nameTag.isEmpty()) {
            return info.nameTag;
        }
        return info.name == null ? "" : info.name;
    }

    /** 是否观战位（对齐 duelclient.cpp STOC_DUEL_START 观战判定：非 tag selftype>1、tag selftype>3；与 gframe player_type=7 等价的本地视角参照） */
    public boolean isSpectator() {
        if (engine.replayMode || engine.isSingleMode) return false;
        int st = engine.client.selfType;
        return engine.field.isTag ? st > 3 : st > 1;
    }

    /** 进决斗前按大厅座位（seatNames）绑定本地视角昵称（duelclient.cpp STOC_HS_PLAYER_ENTER 语义） */
    public void bindViewNames() {
        if (engine.replayMode || engine.isSingleMode) return; // 回放/残局由各自 runner 按视角直写 playerInfos
        int st = engine.client.selfType;
        boolean tag = engine.field.isTag;
        int myMain;
        if (tag) {
            myMain = (st > 3) ? 0 : (st & ~1);   // 观战以 A 队为左；玩家以自己队伍主位为左
        } else {
            myMain = (st > 1) ? 0 : st;          // 观战固定座位 0 侧在左；玩家取自己座位
        }
        int oppMain = tag ? (myMain ^ 2) : (myMain ^ 1);
        engine.playerInfos[0].name = seatAt(myMain);
        engine.playerInfos[1].name = seatAt(oppMain);
        engine.playerInfos[0].nameTag = tag ? seatAt(myMain | 1) : "";
        engine.playerInfos[1].nameTag = tag ? seatAt(oppMain | 1) : "";
    }

    private String seatAt(int seat) {
        return (seat >= 0 && seat < engine.seatNames.length) ? engine.seatNames[seat] : "";
    }

    /**
     * 观战「切换视角」（event_handler.cpp BUTTON_REPLAY_SWAP 观战分支 → DuelClient::SwapField）：
     * 玩家主动点击，必须即时见效。
     * <p>两道旧闸门都是「不跳到当前就切不动」的直接原因，一并拆掉：
     * <ul>
     * <li><b>调度</b>：原先只置 {@code pendingSpectatorSwap} 标志并 post 一次
     * {@code drainPendingMsgs()}，而该方法开头即 {@code if (dispatchingMsg || animGateClosed) return;}
     *——观战时动画闸门几乎常闭，那一次 post 直接早退，交换只能等闸门重开后的「循环顶部」；
     * 后一版改成 post 到主线程再执行，仍多一次排队的依赖。</li>
     * <li><b>前置条件</b>：{@code !engine.inDuel} 直接 return。{@code inDuel} 只在 <b>MSG_START 真正
     * 被派发时</b>才置真（{@code STOC_DUEL_START} 反而先把它写假，见 DuelStocHandler.onDuelStart），
     * 而观战端整条消息链常被动画闸门一段段持住、开局与局间队列里常存积压——这段窗口内
     * {@code inDuel} 就是假的，点击被静默吞掉；「跳到当前」入口只判 {@code isSpectator()}、
     * 不判 {@code inDuel}，所以它能把队列一次性落地、顺带把 {@code inDuel} 置真，之后切换视角
     * 才“有响应”。这与用户实测现象完全对应。</li>
     * </ul>
     * <p>现改为在点击所在的调用线程（STOC/MSG 派发同样在主线程，见 DuelClient 的 mainHandler.post
     * 派发链）**同步执行**交换：既不依赖闸门是否开着，也不依赖队列是否排空；前置条件放宽为
     * 「有当局语境」（inDuel / duelStarted / 观战席位 任一），本按钮也只在观战控制集里出现。
     * 自动翻转（solo 逐回合、撤回重同步）仍走 {@link #requestAutoSpectatorSwap()} 的「排到下一个
     * 消息消费点」语义，让上一回合的动画先播完。
     */
    public void requestSpectatorSwap() {
        if (engine.replayMode) {
            Log.i(TAG, "spectator swap ignored: replay mode (replay swaps by itself)");
            return;
        }
        if (!hasDuelContext()) {
            Log.i(TAG, "spectator swap ignored: no duel context (inDuel=" + engine.inDuel
                    + " started=" + engine.duelStarted + " spectator=" + isSpectator() + ")");
            return;
        }
        // 与排队中的自动翻转共用一次交换：先作废标志，避免一次点击叠出两次翻转（来回）
        engine.pendingSpectatorSwap = false;
        // 观战席位记下这笔视角选择：积压的 MSG_START 落地时不得再把它静默还原
        //（否则观战者看上去就是「点了切换什么也没发生」，见 GameEngine.spectatorViewSwapped）
        if (isSpectator()) engine.spectatorViewSwapped = !engine.spectatorViewSwapped;
        performSpectatorSwap();
        if (isSpectator()) {
            Log.i(TAG, "spectator view preference -> swapped=" + engine.spectatorViewSwapped
                    + " duelIsFirst=" + engine.duelIsFirst);
        }
    }

    /** 手动切视角的前置：有当局画面即可，不以 inDuel 为硬性条件（观战积压/中途进房时它可能仍为假） */
    private boolean hasDuelContext() {
        return engine.inDuel || engine.duelStarted || isSpectator();
    }

    /** 观战切视角请求（自动翻转专用）：挂到下一个消息消费点执行，让先前的在途动画先播完 */
    public void requestAutoSpectatorSwap() {
        if (engine.replayMode || !engine.inDuel) return;
        engine.pendingSpectatorSwap = true;
        engine.mainHandler.post(engine::drainPendingMsgs);
    }

    /**
     * 作废尚未消费的视角翻转请求（会话切换点调用：回放起停、新一局开场、断开）。
     * 标志跨会话残留会让下一段派发链在某个消费点把当时场地静默镜像一次
     *（观战/录像表现为双方手牌无由头互换）。
     */
    public void clearPendingSpectatorSwap() {
        engine.pendingSpectatorSwap = false;
    }

    /**
     * 新一局开局时按报文先攻方重算视角，并重新叠加观战者自己的视角选择：
     * 不叠加则积压到本局的 MSG_START 会把刚手动翻过的视角静默还原（观战表现为点击无反应）。
     * 仅观战席位叠加：对战玩家的视角就是自己那一侧，不允许被上一场的选择污染。
     */
    public void applyDuelFirstFromPlayerType(int playerType) {
        boolean first = (playerType & 1) == 0;
        if (isSpectator() && engine.spectatorViewSwapped) first = !first;
        engine.duelIsFirst = first;
    }

    /**
     * 观战已切换视角时，开局 {@link #bindViewNames()} 之后把左右昵称再对调一次：
     * bindViewNames 按大厅座位绑的是默认边（观战固定座位 0 侧在左），不对调则新一局只有场面
     * 按交换后的视角落位、昵称与卡组/LP 归属却跳回默认边（LP 在此之后按 localPlayer 写入，无需动）。
     */
    public void applySpectatorViewNames() {
        if (!isSpectator() || !engine.spectatorViewSwapped) return;
        GameEngine.PlayerInfo a = engine.playerInfos[0];
        GameEngine.PlayerInfo b = engine.playerInfos[1];
        String tmpName = a.name;
        a.name = b.name;
        b.name = tmpName;
        String tmpNameTag = a.nameTag;
        a.nameTag = b.nameTag;
        b.nameTag = tmpNameTag;
    }

    /** 观战视角交换（client_field.cpp ReplaySwap 实况版，与 ReplayPlayer.performSwapField 同构）：duelIsFirst 翻转 + field.swapField() + 昵称/LP 对调；仅在主线程派发链内调用 */
    void performSpectatorSwap() {
        // 回放态不接受实况侧的翻转：回放的场地/昵称/LP 由 ReplayPlayer.performSwapField 自己维护，
        // 上一段实况（solo逐回合翻转、观战手动切换）残留而未消费的 pendingSpectatorSwap 若在这里
        // 被回放派发链消费，会把回放的半场静默镜像一次（表现为双方手牌无由头互换）
        if (engine.replayMode) return;
        engine.duelIsFirst = !engine.duelIsFirst;
        engine.field.swapField();
        GameEngine.PlayerInfo a = engine.playerInfos[0];
        GameEngine.PlayerInfo b = engine.playerInfos[1];
        String tmpName = a.name;
        a.name = b.name;
        b.name = tmpName;
        // tag 队友名随视角一并左右对调（对齐 C++ ReplaySwap 的 hostname_tag↔clientname_tag swap）
        String tmpNameTag = a.nameTag;
        a.nameTag = b.nameTag;
        b.nameTag = tmpNameTag;
        int tmpLp = a.lp;
        a.lp = b.lp;
        b.lp = tmpLp;
        int tmpStart = a.startLp;
        a.startLp = b.startLp;
        b.startLp = tmpStart;
        engine.field.currentPlayer = 1 - engine.field.currentPlayer;
        // dInfo.lp 是视角索引的血条显示值：容器已对调，直接按 players[].lp 重新对齐
        engine.field.dInfo.lp[0] = engine.field.players[0].lp;
        engine.field.dInfo.lp[1] = engine.field.players[1].lp;
        engine.field.refreshAllCards();
        // 卡详已发动标记随视角翻转（用户规格3）：账本半场归属整体对调后，正在展示卡的
        // 绿/红勾按新视角重绑（观战手动切视角与单人模式逐回合自动翻转同走本链路）
        CardDetailPanel.notifyViewpointSwapped();
        if (engine.listener != null) {
            engine.listener.onFieldChanged();
            engine.listener.onPlayerInfoUpdated(0);
            engine.listener.onPlayerInfoUpdated(1);
            // 回合方高亮（LPBarFrame 彩色/灰色与名字色）随视角翻转重刷
            engine.listener.onTurnStarted(engine.field.currentPlayer);
            // 切视角同步左右对调双方聊天内容
            engine.listener.onViewpointSwapped();
        }
    }
}
