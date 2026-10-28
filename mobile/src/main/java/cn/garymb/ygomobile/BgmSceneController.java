package cn.garymb.ygomobile;

import android.view.View;

import cn.garymb.ygomobile.audio.SoundManager;

/**
 * === 场景 BGM 集中决策（对齐 game.cpp Game::playBGM）===
 * 自 YGOProActivity 拆出：依据布局可见性与对局状态计算场景，交
 * {@link SoundManager#playBGM} 播放（同场景由 SoundManager 内部去重不重复切歌）。
 * 对外入口仍是 {@link YGOProActivity#updateBGM()} / {@link YGOProActivity#setBgmDuelResult(boolean)}，
 * 场景复位（离开决斗场 / 新开一局）由门面在 hideGameUI / showGameUI 时调用 {@link #resetDuelResult()}。
 * 必须在主线程调用。
 */
class BgmSceneController {

    // 场景 BGM 胜负覆盖（对齐 game.cpp Game::playBGM 的 dInfo.isFinished && showcardcode 判定）：
    // 决斗场显示时若已判定胜负则优先播放 WIN/LOSE，否则按 LP 差判定 ADVANTAGE/DISADVANTAGE/DUEL
    private static final int BGM_RESULT_NONE = 0;
    private static final int BGM_RESULT_WIN = 1;
    private static final int BGM_RESULT_LOSE = 2;
    private int bgmDuelResult = BGM_RESULT_NONE;
    /** 优势/劣势 LP 差阈值 */
    private static final int BGM_LP_DIFF_THRESHOLD = 4000;
    /**
     * 决斗是否真正进行中（对齐 C++ Game::playBGM 的 dInfo.isStarted 闸门）：
     * 只在进入决斗场 UI（{@link #enterDuel()}，含实况/观战/录像/残局）时置真、
     * 离开（{@link #leaveDuel()}）时置假。以此而非 layout_game_right 可见性判定场景，
     * 因为玩家等待/大厅聊天（enterLobbyChatUI）也会让 game_right 可见，若据此判定
     * 会把等待大厅误当作决斗场景；而等待/建主/各模式 dialog 应走 MENU。
     */
    private boolean duelActive = false;

    private final YGOProActivity activity;

    BgmSceneController(YGOProActivity activity) {
        this.activity = activity;
    }

    /**
     * 依据当前布局与对局状态选择 BGM 场景：
     * - 决斗真正进行中（duelActive，含观战/录像/残局）：胜负已判定 → WIN/LOSE；否则 LP 差≥阈值时
     *   对方血多 → DISADVANTAGE、我方血多 → ADVANTAGE，其余（含刚开局）→ DUEL
     * - 卡组编辑器 layout_deck_editor 显示（含副卡组替换）→ DECK
     * - 其他（主菜单 / 建主 / 各模式 dialog / 玩家等待大厅）→ MENU
     */
    void update() {
        if (activity.soundManager == null) return;
        SoundManager.BGM scene;
        boolean deckEditorShowing = activity.layoutDeckEditor != null
                && activity.layoutDeckEditor.getVisibility() == View.VISIBLE;
        if (duelActive) {
            if (bgmDuelResult == BGM_RESULT_WIN) {
                scene = SoundManager.BGM.WIN;
            } else if (bgmDuelResult == BGM_RESULT_LOSE) {
                scene = SoundManager.BGM.LOSE;
            } else {
                int myLp = activity.engine != null ? activity.engine.field.players[0].lp : 0;
                int oppLp = activity.engine != null ? activity.engine.field.players[1].lp : 0;
                if (Math.abs(myLp - oppLp) >= BGM_LP_DIFF_THRESHOLD) {
                    scene = oppLp > myLp ? SoundManager.BGM.DISADVANTAGE
                            : SoundManager.BGM.ADVANTAGE;
                } else {
                    scene = SoundManager.BGM.DUEL;
                }
            }
        } else if (deckEditorShowing) {
            scene = SoundManager.BGM.DECK;
        } else {
            scene = SoundManager.BGM.MENU;
        }
        activity.soundManager.playBGM(scene);
    }

    /**
     * 进入决斗场 UI（实况/观战/录像/残局开局）：置决斗进行标志并清除上一局残留的胜负覆盖，
     * 使刚开局按 LP 初始差（通常为 0）走 DUEL。调用方随后 updateBGM 生效。
     */
    void enterDuel() {
        duelActive = true;
        bgmDuelResult = BGM_RESULT_NONE;
    }

    /** 离开决斗场 / 进入等待大厅：清除决斗进行标志与胜负覆盖（下一帧按 MENU/DECK 重算） */
    void leaveDuel() {
        duelActive = false;
        bgmDuelResult = BGM_RESULT_NONE;
    }

    /**
     * 决斗判定胜负时设置 BGM 胜负覆盖并刷新场景
     *（对齐 Game::playBGM 的 dInfo.isFinished && showcardcode==1/2/3 分支）
     */
    void setDuelResult(boolean selfWon) {
        bgmDuelResult = selfWon ? BGM_RESULT_WIN : BGM_RESULT_LOSE;
        update();
    }

    /** 离开决斗场 / 新开一局：清除上一局残留的胜负覆盖（对齐 dInfo.isFinished 复位） */
    void resetDuelResult() {
        bgmDuelResult = BGM_RESULT_NONE;
    }
}
