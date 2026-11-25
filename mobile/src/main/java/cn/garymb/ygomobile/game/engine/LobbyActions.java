package cn.garymb.ygomobile.game.engine;

import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * === Lobby Actions ===
 * 自 GameEngine 拆分而来：大厅（房间等待页）操作——准备/开始/踢人/聊天/投降/观战切换。
 * tag 投降发起标志 tagSurrenderInitiated 为共享状态，保留在 GameEngine
 * （DuelStocHandler 的 onTeammateSurrender 与对局开始清理也会读写）。
 */
public class LobbyActions {

    private final GameEngine engine;

    public LobbyActions(GameEngine engine) {
        this.engine = engine;
    }

    public void sendReady() {
        engine.client.sendReady();
    }

    public void sendNotReady() {
        engine.client.sendNotReady();
    }

    public void sendStart() {
        engine.client.sendStart();
    }

    public void sendKick(int pos) {
        engine.client.sendKick(pos);
    }

    public void sendChat(String message) {
        engine.client.sendChat(message);
    }

    public void sendSurrender() {
        // 残局：无服务器可发投降包，等价于判定本方负并收尾（对齐 gframe 残局LeaveGame）
        if (engine.isSingleMode) {
            engine.singleRunner.quit();
            return;
        }
        if (isTagMode()) {
            // tag：本席只是发起投降，须队友也确认才判负；期间对局照常推进，
            // 后续询问仍要作答，故不挂应答闸门
            engine.tagSurrenderInitiated = true;
        } else {
            // 非 tag：本包即为最终答复，服务端随即按投降判负并结束本局；从此刻起本端
            // 不再发出任何应答（见 GameEngine.surrenderSettling），避免界面上抢在胜负
            // 通讯落地前点掉的旧询问窗把一条已作废的应答打进已结束的对局
            engine.surrenderSettling = true;
        }
        engine.client.sendSurrender();
    }

    public void sendUndo() {
        // 撤回只对“服务端引擎跑局”的局域网房间有意义：残局（本地引擎直驱）与回放无此概念，
        // 具体能否回退、是否有权限由服务端校验（不合法时回 STOC_UNDO_ACK = DENIED）
        if (engine.isSingleMode || engine.replayMode) return;
        engine.client.sendUndo();
    }

    /** 是否 tag 双人模式（gameMode == MODE_TAG） */
    public boolean isTagMode() {
        return engine.gameMode == YGOProtocol.MODE_TAG;
    }

    /** tag 模式：本方已发起投降、正在等待队友回应 */
    public boolean isSurrenderPending() {
        return engine.tagSurrenderInitiated;
    }

    public void sendToDuelist() {
        engine.client.sendToDuelist();
    }

    public void sendToObserver() {
        engine.client.sendToObserver();
    }
}
