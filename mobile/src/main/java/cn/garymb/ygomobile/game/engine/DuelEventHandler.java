package cn.garymb.ygomobile.game.engine;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.gamefield.MoveEventApplier;
import cn.garymb.ygomobile.game.gamefield.ReloadFieldBuilder;
import cn.garymb.ygomobile.game.gamefield.TagSwapApplier;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.dialogs.duel.DuelLogDialog;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.enums.CardLocation;

/**
 * === 场地事件消息处理（MSG_* 场地/连锁/LP/攻击等事件，对齐 gframe duelclient.cpp ClientAnalyze） ===
 * 自 GameEngine 拆分而来。实现 GameMessageParser.MessageHandler：
 * - 场地事件类方法在此直接实现；
 * - 选择/提示类方法（实现已合并至 GameMessageParser）转发 engine.messageParser；
 * - 召唤动画类方法（拆入 SummonAnimationManager）转发 engine.summonAnim。
 * - 卡组/手卡堆动画方法（拆入 DeckHandMotionManager）转发 engine.deckMotion。
 */
public class DuelEventHandler extends DuelEventHandlerBase {

    /**
     * Constructor (public entry of the split chain; the assignment lives
     * in the base layer).
     */
    public DuelEventHandler(GameEngine engine) {
        super(engine);
    }

    @Override
    public void onCancelTarget(int c1ctrl, int c1loc, int c1seq, int c2ctrl, int c2loc, int c2seq) {
        // duelclient.cpp MSG_CANCEL_TARGET L3718-3743：双向摘除，并对位清理悬停态图标标记
        ClientCard c1 = engine.field.getCard(engine.localPlayer(c1ctrl & 1), c1loc, c1seq);
        ClientCard c2 = engine.field.getCard(engine.localPlayer(c2ctrl & 1), c2loc, c2seq);
        if (c1 != null && c2 != null) {
            c1.targetCards.remove(c2);
            c2.ownerTarget.remove(c1);
            c2.is_showtarget = false;
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onPayLpCost(int player, int cost) {
        int p = engine.localPlayer(player & 1);
        int fin = Math.max(0, engine.field.players[p].lp - cost);
        engine.field.players[p].lp = fin;
        // 对齐 duelclient.cpp MSG_PAY_LPCOST L3755-3763：SFX.DAMAGE + lpccolor=0xff0000ff（蓝）、lpcstring="-cost"、
        // WaitFrameSignal(30)+lpframe=10+WaitFrameSignal(11)——支付基本分同样先浮字再扣减；startLpChange(showText=true)
        // 同步置 lpPending，统一动画屏障据此暂缓后续消息（如随后场上怪兽被破坏离场的 MSG_MOVE）
        engine.soundManager.playSoundEffect(SoundManager.SFX.DAMAGE);
        engine.field.startLpChange(p, fin, LP_COST_BLUE, "-" + cost, true);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onAddCounter(int type, int ctrl, int loc, int seq, int count) {
        ClientCard card = engine.field.getCard(ctrl, loc, seq);
        if (card != null) {
            card.counters.put(type, count);
        }
        engine.soundManager.playSoundEffect(SoundManager.SFX.COUNTER_ADD);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onRemoveCounter(int type, int ctrl, int loc, int seq, int count) {
        ClientCard card = engine.field.getCard(ctrl, loc, seq);
        if (card != null) {
            card.counters.remove(type);
        }
        engine.soundManager.playSoundEffect(SoundManager.SFX.COUNTER_REMOVE);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onAttack(int aCtrl, int aLoc, int aSeq, int dCtrl, int dLoc, int dSeq) {
        // 对齐 duelclient.cpp MSG_ATTACK L3830-3860：有目标攻怪音效 ATTACK，直接攻击音效 DIRECT_ATTACK
        ClientCard atkCard = engine.field.getCard(engine.localPlayer(aCtrl & 1), aLoc & 0x7f, aSeq);
        if (atkCard == null) {
            // Fallback: try raw controler (in case localPlayer mapping edge case)
            atkCard = engine.field.getCard(aCtrl & 1, aLoc & 0x7f, aSeq);
        }
        if (atkCard == null) {
            Log.w(TAG, "onAttack: attacker card not found ctrl=" + aCtrl + " loc=" + aLoc + " seq=" + aSeq);
        }
        String atkName = atkCard != null ? DataManager.get().getName(atkCard.code) : "";
        boolean skipArc = engine.field.instantPlace; // 回放快进重排：不写弧线、不持闸
        /** 战斗阶段点击「攻击宣言」选中的怪兽：cmdFlag 已清空但尚未进入 arcAttacker 前需隐藏其 attack 箭头；
         *   该卡通讯中还有剩余攻击次数时会重新显示（见 CardOverlayRenderer.drawFieldCardOverlays 对 selectAttacking 的判断）。 */
        engine.field.selectAttacking = atkCard;
        if (dLoc != 0) {
            engine.soundManager.playSoundEffect(SoundManager.SFX.ATTACK);
            ClientCard defCard = engine.field.getCard(engine.localPlayer(dCtrl & 1), dLoc & 0x7f, dSeq);
            String defName = defCard != null ? DataManager.get().getName(defCard.code) : "";
            engine.hintManager.setEventString(1619, "[%s]攻击[%s]", atkName, defName);
            // 记录绿色攻击弧端点（攻击者→目标），GameFieldView 在约 0.9s 内绘制流动弧
            if (!skipArc) {
                engine.field.arcAttacker = atkCard;
                engine.field.arcTarget = defCard;
                engine.field.arcStartMs = System.currentTimeMillis();
                // selectAttacking 已移入 arcAttacker，立即清零
                engine.field.selectAttacking = null;
            }
        } else {
            engine.soundManager.playSoundEffect(SoundManager.SFX.DIRECT_ATTACK);
            engine.hintManager.setEventString(1620, "[%s]直接攻击", atkName);
            // 直接攻击：无目标卡，弧落到对方手牌行一侧（duelclient.cpp L3850-3853）
            if (!skipArc) {
                engine.field.arcAttacker = atkCard;
                engine.field.arcTarget = null;
                engine.field.arcStartMs = System.currentTimeMillis();
                // selectAttacking 已移入 arcAttacker，立即清零
                engine.field.selectAttacking = null;
            }
        }
        // 对齐 duelclient.cpp MSG_ATTACK L3864 WaitFrameSignal(40)：弧光展示期内关闭闸门，
        // 后续伤害步骤/询问弹窗不得抢先于弧线展示（直接攻击无卡片动画屏障，旧实现弧被弹窗
        // 遮挡几乎不可见；攻怪因伴随 MSG_MOVE 动画而受影响较小）
        if (!skipArc) {
            engine.animHoldUntilMs = System.currentTimeMillis() + GameEngine.ATTACK_HOLD_MS;
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onBattle(int atkAtk, boolean atkPos, int defAtk, boolean defPos) {
        Log.d(TAG, "Battle: " + atkAtk + " vs " + defAtk);
    }

    @Override
    public void onAttackDisabled() {
        // 对齐 duelclient.cpp MSG_ATTACK_DISABLED L3910：sys1621「攻击被无效」（无占位符，C++ 传入的卡名参数被忽略）
        engine.hintManager.setEventString(1621, "攻击被无效");
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onDamageStepStart() {
        Log.d(TAG, "Damage step start");
    }

    @Override
    public void onDamageStepEnd() {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onMissedEffect(int code, int ctrl, int loc, int seq, int effectId) {
        Log.w(TAG, "Missed effect: code=" + code + " effectId=" + effectId);
        // 对齐 duelclient.cpp MSG_MISSED_EFFECT L3919-3924：sys1622「[%ls]错过时点」用 GetName(code) 填充，携带卡代码
        DuelLogDialog.addLog(DuelLogDialog.sysFormat(1622, "[%s]错过时点", DataManager.get().getName(code)), code);
    }

    @Override
    public void onTossCoin(int player, int count, ByteBuffer results) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.COIN);
        // 对齐 duelclient.cpp MSG_TOSS_COIN L3926-3946：掷硬币结果记入日志
        StringManager sm = DataManager.get().getStringManager();
        StringBuilder sb = new StringBuilder(sm.getSystemString(1623, "掷硬币："));
        for (int i = 0; i < count && results.remaining() >= 1; i++) {
            int res = results.get() & 0xFF;
            sb.append('[')
                    .append(res != 0 ? sm.getSystemString(60, "正面") : sm.getSystemString(61, "反面"))
                    .append(']');
        }
        DuelLogDialog.addLog(sb.toString());
    }

    @Override
    public void onTossDice(int player, int count, ByteBuffer results) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.DICE);
        // 对齐 duelclient.cpp MSG_TOSS_DICE L3948-3968：掷骰子结果记入日志
        StringBuilder sb = new StringBuilder(
                DataManager.get().getStringManager().getSystemString(1624, "掷骰子："));
        for (int i = 0; i < count && results.remaining() >= 1; i++) {
            sb.append('[').append(results.get() & 0xFF).append(']');
        }
        DuelLogDialog.addLog(sb.toString());
    }

    @Override
    public void onAnnounceRace(int player, int count, int availableRaces) {
        // duelclient.cpp MSG_ANNOUNCE_RACE L3996-4014：player 已消费，打包 count+available 转发
        ByteBuffer buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) count);
        buf.putInt(availableRaces);
        buf.flip();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(140, buf);
        });
    }

    @Override
    public void onAnnounceAttrib(int player, int count, int availableAttribs) {
        // duelclient.cpp MSG_ANNOUNCE_ATTRIB L4015-4033：同上，打包 count+available 转发
        ByteBuffer buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) count);
        buf.putInt(availableAttribs);
        buf.flip();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(141, buf);
        });
    }

    @Override
    public void onAnnounceCard(int player, ByteBuffer data) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(142, data);
        });
    }

    @Override
    public void onAnnounceNumber(int player, ByteBuffer data) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(143, data);
        });
    }

    @Override
    public void onCardHint(int player, int location, int sequence, int hintType, int value) {
        // duelclient.cpp MSG_CARD_HINT L4094-4109：CHINT_DESC_ADD/REMOVE 维护卡片 desc_hints
        engine.field.applyCardHint(engine.localPlayer(player & 1), location, sequence, hintType, value);
    }

    @Override
    public void onPlayerHint(int player, int hintType, int value) {
        // duelclient.cpp MSG_PLAYER_HINT L4135-4166：本地座位的 CARD_QUESTION(谜题)描述提示
        // 置/清「不可确认墓地」态，使谜题发动到效果处理完毕期间本端不能点击墓地弹 cmdmenudialog
        engine.field.applyPlayerHint(engine.localPlayer(player & 1), hintType, value);
    }

    @Override
    public void onBecomeTarget(int count, ByteBuffer data) {
        if (data == null || count <= 0) return;
        // duelclient.cpp MSG_BECOME_TARGET L3493-3500：每条 4 字节，第 4 字节（subseq）读入即弃，
        // 三参 GetCard 后写入 current_chain.target
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            data.get();
            int localCtrl = engine.localPlayer(ctrl & 1);
            engine.field.addChainTarget(localCtrl, loc, seq);
            // 对齐 duelclient.cpp MSG_BECOME_TARGET L3511-3519：墓地/除外（location & 0x30）的卡被选为效果
            // 对象时卡片同样不移动、核心只发本消息，故滑出动画必须挂在这里（与 onChaining 同源，上一版误挂
            // onMove 永不触发）。场上目标（L & 0xc）C++ 做 FadeCard 闪烁，已由蚂蚁线高亮承担，此处只补堆叠区
            // 滑出；±0.15/帧两阶段轨迹、方向随控制方（同 C++ 的 c==1 取 +0.15）。
            ClientCard tcard = engine.field.getCard(localCtrl, loc, seq);
            if (tcard != null && (tcard.location & 0x30) != 0) {
                float shift = (localCtrl == 1) ? 0.15f : -0.15f;
                engine.field.moveCardAnimated(tcard, 10, 0, shift);
            }
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onTagSwap(ByteBuffer data) {
        tagSwapApplier.apply(data);
    }

    @Override
    public void onReloadField(ByteBuffer data) {
        reloadBuilder.apply(data);
    }

    @Override
    public void onAiName(String name) {
        Log.i(TAG, "AI name: " + name);
    }

    @Override
    public void onShowHint(String hint) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onNoticeMessage(hint);
        });
    }

    @Override
    public void onMatchKill(int code) {
        engine.matchResult = 3;
    }

    @Override
    public void onCustomMsg(String msg) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onNoticeMessage(msg);
        });
    }

    @Override
    public void onDuelWinner(int player, int reason) {
        engine.messageParser.onWin(player, reason);
    }
}
