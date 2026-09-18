package cn.garymb.ygomobile.game;

import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * === Lobby Actions ===
 * 自 GameEngine 拆分而来：大厅（房间等待页）操作——准备/开始/踢人/聊天/投降/观战切换。
 * tag 投降发起标志 tagSurrenderInitiated 为共享状态，保留在 GameEngine
 * （StocHandler 的 onTeammateSurrender 与对局开始清理也会读写）。
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
        if (isTagMode()) engine.tagSurrenderInitiated = true;
        engine.client.sendSurrender();
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
