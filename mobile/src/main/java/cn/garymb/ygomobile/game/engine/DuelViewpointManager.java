package cn.garymb.ygomobile.game.engine;

/** 对局视角管理（自 GameEngine 拆出）：本地视角显示名/座位昵称绑定、观战判定与切视角；状态（playerInfos/seatNames/tagPlayer/duelIsFirst/pendingSpectatorSwap）仍留在引擎，构造注入引擎引用。 */
public class DuelViewpointManager {
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

    /** 观战切视角请求（event_handler.cpp BUTTON_REPLAY_SWAP 观战分支 → is_swapping，实际交换延迟到消息消费点） */
    public void requestSpectatorSwap() {
        if (engine.replayMode || !engine.inDuel) return;
        engine.pendingSpectatorSwap = true;
        engine.mainHandler.post(engine::drainPendingMsgs);
    }

    /** 观战视角交换（client_field.cpp ReplaySwap 实况版，与 ReplayPlayer.performSwapField 同构）：duelIsFirst 翻转 + field.swapField() + 昵称/LP 对调；仅在主线程派发链内调用 */
    void performSpectatorSwap() {
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
