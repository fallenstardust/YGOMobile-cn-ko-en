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
    /** 决斗中双方 LP 差达到该阈值时切换优势/劣势 BGM（对齐需求「LP 相差大于等于 4000」） */
    private static final int BGM_LP_DIFF_THRESHOLD = 4000;

    private final YGOProActivity activity;

    BgmSceneController(YGOProActivity activity) {
        this.activity = activity;
    }

    /**
     * 依据当前布局与对局状态选择 BGM 场景：
     * - 决斗场 layout_game_right 显示：胜负已判定 → WIN/LOSE；否则 LP 差≥阈值时
     *   对方血多 → DISADVANTAGE、我方血多 → ADVANTAGE，其余 → DUEL
     * - 卡组编辑器 layout_deck_editor 显示（含副卡组替换）→ DECK
     * - 其他 → MENU
     */
    void update() {
        if (activity.soundManager == null) return;
        SoundManager.BGM scene;
        boolean gameRightShowing = activity.layoutGameRight != null
                && activity.layoutGameRight.getVisibility() == View.VISIBLE;
        boolean deckEditorShowing = activity.layoutDeckEditor != null
                && activity.layoutDeckEditor.getVisibility() == View.VISIBLE;
        if (gameRightShowing) {
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
