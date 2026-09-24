package cn.garymb.ygomobile.game;

import ocgcore.DataManager;

import cn.garymb.ygomobile.audio.SoundManager;

/**
 * === 召唤动画类型（onSummonAnimation 的 summonType 参数，对齐 duelclient.cpp showcard=5/7） ===
 * 自 GameEngine 拆分而来：MSG_SUMMONING/MSG_SPSUMMONING/MSG_FLIPSUMMONING 的居中卡片动画，
 * 以及 MSG_CHAIN_NEGATED/MSG_CHAIN_DISABLED 的效果无效动画（showcard=3）。
 */
public class SummonAnimationManager {

    public static final int SUMMON_NORMAL = 0;   // 通常召唤（MSG_SUMMONING，case 7 翻面）
    public static final int SUMMON_SPECIAL = 1;  // 特殊召唤（MSG_SPSUMMONING，case 5 放大淡入）
    public static final int SUMMON_FLIP = 2;     // 反转召唤（MSG_FLIPSUMMONING，case 7 翻面）

    private final GameEngine engine;

    public SummonAnimationManager(GameEngine engine) {
        this.engine = engine;
    }

    /** 召唤类卡片居中动画（对齐 duelclient.cpp showcard=5/7）：同步派发即入队特效，
     *  闸门由 drainPendingMsgs 的统一动画屏障检测关闭，无需在此显式关闭 */
    public void postSummonAnimation(int code, int summonType) {
        if (engine.listener == null) return;
        engine.listener.onSummonAnimation(code, summonType);
    }

    /**
     * 效果无效卡片居中动画（对齐 duelclient.cpp MSG_CHAIN_NEGATED/DISABLED L3450-3452：
     * showcardcode = chains[ct-1].code, showcarddif=0, showcard=3）。
     * ct 为 1 基连锁序号；取不到卡码时不播放。
     */
    public void postNegatedAnimation(int chainCount) {
        final int code = (chainCount >= 1 && chainCount <= engine.duelEvents.chainCodes.size())
                ? engine.duelEvents.chainCodes.get(chainCount - 1) : 0;
        if (code == 0 || engine.listener == null) return;
        // 无效动画入队即由统一动画屏障关闭闸门，播完再处理后续消息
        // （对齐 C++ MSG_CHAIN_NEGATED/DISABLED 的 WaitFrameSignal(30)）
        engine.listener.onNegatedAnimation(code);
    }

    // ==== 召唤类消息（MSG_SUMMONING/MSG_SPSUMMONING/MSG_FLIPSUMMONING）====

    public void onSummoning(int code, int ctrl, int loc, int seq) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.SUMMON);
        // duelclient.cpp MSG_SUMMONING L3250/3252-3258：event_string=sys1603「[%ls]召唤中」+ showcard=7（翻面进入）
        engine.hintManager.setEventString(1603, "[%s]召唤中", DataManager.get().getName(code));
        postSummonAnimation(code, SUMMON_NORMAL);
    }

    public void onSpSummoning(int code, int ctrl, int loc, int seq) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.SPECIAL_SUMMON);
        // duelclient.cpp MSG_SPSUMMONING L3286-3290：event_string=sys1605「[%ls]特殊召唤中」+ if(code) showcard=5（放大淡入）
        engine.hintManager.setEventString(1605, "[%s]特殊召唤中", DataManager.get().getName(code));
        if (code != 0) postSummonAnimation(code, SUMMON_SPECIAL);
    }

    public void onFlipSummoning(int code, int ctrl, int loc, int seq) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.FLIP);
        // duelclient.cpp MSG_FLIPSUMMONING L3314-3320：event_string=sys1607「[%ls]反转召唤中」+ showcard=7（翻面进入）
        engine.hintManager.setEventString(1607, "[%s]反转召唤中", DataManager.get().getName(code));
        postSummonAnimation(code, SUMMON_FLIP);
    }
}
