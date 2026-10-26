package cn.garymb.ygomobile.game.engine;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.GameField;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.ui.dialogs.duel.DuelLogDialog;
import ocgcore.DataManager;
import ocgcore.enums.CardLocation;

/**
 * === 卡组 / 手卡堆动画（对齐 gframe duelclient.cpp 的 CONFIRM_DECKTOP / CONFIRM_CARDS /
 * SHUFFLE_DECK / SHUFFLE_HAND 分支）===
 * 自 DuelEventHandler 拆分而来，与 {@link SummonAnimationManager} 同构：由 DuelEventHandler
 * 的消息回调转发实现。因联机对局、观战与录像回放都走 GameEngine 的同一条实况管线，
 * 回放的洗牌/换手卡/确认卡组顶动画与实况完全同源（旧的 ReplayEngine 自建设卡动画不再存在）。
 *
 * <p>统一动画闸门：本类动画一律用 {@code engine.field.moveCardAnimated/startDeckShake/
 * startHandShuffle} 驱动，并以 {@code engine.animHoldUntilMs} 持闸时长（回放快进
 * {@code field.instantPlace} 置位时改为同步落位、不持闸），故回放正常播放与观战节奏一致。
 *
 * <p>全部方法在消息派发线程（主线程）调用。
 */
public class DeckHandMotionManager {

    // 保持拆分前日志标识，便于与旧版日志比对
    private static final String TAG = "GameEngine";

    private final GameEngine engine;

    public DeckHandMotionManager(GameEngine engine) {
        this.engine = engine;
    }

    /** 区域列表内容被就地重排（洗切/反转/互换）后通知 UI 刷帧：与实况同一 onFieldChanged 入口 */
    public void postFieldChanged() {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    // ==== MSG_CONFIRM_DECKTOP / MSG_CONFIRM_CARDS ====

    public void applyConfirmDecktop(int player, int count, ByteBuffer data) {
        Log.d(TAG, "ConfirmDecktop: player=" + player + " count=" + count);
        final int p = engine.localPlayer(player);
        final int deckLoc = CardLocation.Deck.value();
        // 对齐 duelclient.cpp MSG_CONFIRM_DECKTOP L2456-2463：非回放快进时播 reveal 音效 + 记日志
        if (!engine.replaySkip)
            engine.soundManager.playSoundEffect(SoundManager.SFX.REVEAL);
        DuelLogDialog.addLog(DuelLogDialog.sysFormat(207, "翻开卡组上方%d张卡：", count));

        // L2452-2458：第一段循环——读 count 个卡码并写回卡组顶对应卡（rbegin+i → 顶部第 i 张，
        // 本端以 getCardCount-1-i 取顶卡）。旧实现只读码记日志、不 SetCode、无任何翻面动画，
        // 故「翻开卡组顶部卡片」只见日志不见卡面。
        final int top = engine.field.getCardCount(p, deckLoc) - 1;
        final ClientCard[] revealed = new ClientCard[count];
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt() & 0x7fffffff;
            data.position(data.position() + 3);
            ClientCard c = engine.field.getCard(p, deckLoc, top - i);
            revealed[i] = c;
            if (c != null && code != 0) c.setCode(code);
            DuelLogDialog.addLog("*[" + DataManager.get().getName(code) + "]", code);
        }
        // L2464-2481：第二段逐张翻开揭示动画（滑出堆叠→旋转到面朝相机亮出卡面→停留→转回盖回）
        if (count > 0 && !engine.replaySkip) {
            scheduleTopReveal(revealed, (p == 1) ? 0.15f : -0.15f, count);
        }
        postFieldChanged();
    }

    /** 翻开额外卡组顶卡（对齐 duelclient.cpp MSG_CONFIRM_EXTRATOP L2484-2519，与卡组顶同类揭示动画）：
     *  此前该消息在 GameMessageParser 无 case、完全未处理，翻开额外顶卡既无动画也无揭示音效。 */
    public void applyConfirmExtratop(int player, int count, ByteBuffer data) {
        Log.d(TAG, "ConfirmExtratop: player=" + player + " count=" + count);
        final int p = engine.localPlayer(player);
        final int extraLoc = CardLocation.Extra.value();
        if (!engine.replaySkip)
            engine.soundManager.playSoundEffect(SoundManager.SFX.REVEAL);
        DuelLogDialog.addLog(DuelLogDialog.sysFormat(207, "翻开卡组上方%d张卡：", count));
        final int top = engine.field.getCardCount(p, extraLoc) - 1;
        final ClientCard[] revealed = new ClientCard[count];
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt() & 0x7fffffff;
            data.position(data.position() + 3);
            ClientCard c = engine.field.getCard(p, extraLoc, top - i);
            revealed[i] = c;
            if (c != null && code != 0) c.setCode(code);
            DuelLogDialog.addLog("*[" + DataManager.get().getName(code) + "]", code);
        }
        // L2502-2518：额外顶卡翻开揭示，与卡组顶同一套逐张动画（shift 随席位）
        if (count > 0 && !engine.replaySkip) {
            scheduleTopReveal(revealed, (p == 1) ? 0.15f : -0.15f, count);
        }
        postFieldChanged();
    }

    /**
     * 卡组 / 额外顶卡逐张翻开揭示动画的串行调度（对齐 duelclient.cpp MSG_CONFIRM_DECKTOP/
     * MSG_CONFIRM_EXTRATOP 逐张 WaitFrameSignal 的时序）：第 i 张在 i*step 帧后滑出翻开（5 帧），
     * 停留 holdFrames 帧后 5 帧转回原位盖回卡背，再清除揭示态；count>5 时停留缩短（同 C++
     * count>5?12:45）。整段以 {@code animHoldUntilMs} 持统一动画闸门，令后续消息串行等待揭示播完。
     * 回放快进（instantPlace）下 revealTopCardOut/moveCardAnimated 自动同步落位、不排程不持闸。
     */
    private void scheduleTopReveal(ClientCard[] revealed, float shift, int count) {
        if (engine.field.instantPlace) {
            // 快进重排：只同步落位（卡码已写回），不排程翻开动画、不持闸
            for (ClientCard c : revealed) {
                if (c != null) engine.field.revealTopCardOut(c, 0f, 1);
            }
            return;
        }
        final long holdFrames = count > 5 ? 12 : 45;
        final long stepMs = (holdFrames + 6) * 17L;
        long lastEndMs = 0L;
        for (int i = 0; i < revealed.length; i++) {
            final ClientCard c = revealed[i];
            if (c == null) continue;
            final long base = i * stepMs;
            engine.mainHandler.postDelayed(() -> {
                engine.field.revealTopCardOut(c, shift, 5);
                if (engine.listener != null) engine.listener.onFieldChanged();
            }, base);
            engine.mainHandler.postDelayed(() -> {
                engine.field.moveCardAnimated(c, 5);
                if (engine.listener != null) engine.listener.onFieldChanged();
            }, base + holdFrames * 17L);
            engine.mainHandler.postDelayed(() -> {
                c.top_reveal = false;
                if (engine.listener != null) engine.listener.onFieldChanged();
            }, base + (holdFrames + 6) * 17L);
            lastEndMs = base + (holdFrames + 6) * 17L;
        }
        engine.animHoldUntilMs = System.currentTimeMillis() + lastEndMs + 50L;
    }

    /**
     * 场上里侧卡「翻开确认→停留→盖回」整批动画（对齐 duelclient.cpp MSG_CONFIRM_CARDS
     * field_confirm 分支 L2571-2608）：Wait(5) 后逐卡启动 5 帧原地翻开（revealFieldCard，
     * 对应 C++ dRot=π/5 + is_moving）并在停留段置 is_highlighting（C++ L2579），持闸 90 帧
     *（C++ L2602 实况 WaitFrameSignal(90)）后整批 MoveCard(5) 转回里侧落位盖回卡背并取消
     * 高亮，末尾清除 top_reveal。整段以 {@code animHoldUntilMs} 持统一动画闸门，令后续消息
     * 串行等待确认播完（等价 C++ 屏障）。回放快进（replaySkip，同 C++ isReplaySkiping 开头
     * 直接 return）与快进落位（instantPlace）下不排程、不持闸。
     */
    private void scheduleFieldConfirmReveal(List<ClientCard> cards) {
        // 回放快进（同 C++ isReplaySkiping 在本消息开头直接 return 不播）与即时落位：不排程、不持闸
        if (engine.replaySkip || engine.field.instantPlace) return;
        final long holdFrames = 90L;
        final long flipStart = 5L * 17L;                    // C++ L2572 WaitFrameSignal(5)
        final long coverStart = flipStart + 5L * 17L + holdFrames * 17L; // 翻开 5 帧 + 停留
        final long endMs = coverStart + 5L * 17L;           // 盖回 5 帧
        engine.mainHandler.postDelayed(() -> {
            for (ClientCard c : cards) {
                engine.field.revealFieldCard(c, 5);
                c.is_highlighting = true; // C++ L2579：停留段高亮框
            }
            if (engine.listener != null) engine.listener.onFieldChanged();
        }, flipStart);
        engine.mainHandler.postDelayed(() -> {
            for (ClientCard c : cards) {
                engine.field.moveCardAnimated(c, 5);        // C++ L2605：转回里侧落位盖回
                c.is_highlighting = false;                  // C++ L2606
            }
            if (engine.listener != null) engine.listener.onFieldChanged();
        }, coverStart);
        engine.mainHandler.postDelayed(() -> {
            for (ClientCard c : cards) c.top_reveal = false;
            if (engine.listener != null) engine.listener.onFieldChanged();
        }, endMs);
        engine.animHoldUntilMs = System.currentTimeMillis() + endMs + 50L;
    }

    public void applyConfirmCards(int player, int skipPanel, int count, ByteBuffer data) {
        // 对齐 duelclient.cpp MSG_CONFIRM_CARDS L2527-2532：非回放快进时播 reveal 音效 + 记日志
        if (!engine.replaySkip)
            engine.soundManager.playSoundEffect(SoundManager.SFX.REVEAL);
        DuelLogDialog.addLog(DuelLogDialog.sysFormat(208, "确认%d张卡：", count));

        int start = data.position();
        int panelCount = 0; // 面板确认卡数（loc&0x41，对齐 duelclient.cpp L2546-2566 与 CardSelectController 过滤）
        // 场上确认卡（对齐 C++ L2527 field_confirm：非卡组/额外的 loc，含手卡/坟地等，但翻开动画
        // 仅对场上里侧卡触发，同 C++ L2581/2591 的 POS_FACEUP 跳过）
        final List<ClientCard> fieldConfirm = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt() & 0x7fffffff;
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            if ((loc & 0x41) != 0) panelCount++;
            DuelLogDialog.addLog("*[" + DataManager.get().getName(code) + "]", code);
            // 对齐 duelclient.cpp MSG_CONFIRM_CARDS L2542-2544：确认消息自带真实卡码，必须
            // 写回卡对象。客机加入遮蔽型外部服务器时「卡组→手卡」的 MSG_MOVE 卡码为 0，
            // 真实卡码只由本消息（入手后公开确认，loc=手卡，服务器向双方转发）送达；旧实现
            // 只记日志不写码，对方手卡因缺码不翻面，随后 MSG_SHUFFLE_HAND 直接洗切盖回卡背
            int lp = engine.localPlayer(ctrl);
            ClientCard pcard = engine.field.getCard(lp, loc, seq);
            if (pcard != null && code != 0) {
                boolean newlyKnown = pcard.code == 0;
                pcard.setCode(code);
                // 首次经确认拿到卡码的手卡补翻面揭示（对齐 C++ L2577-2578 手卡高亮展示）；
                // 卡码已在 MSG_MOVE 路径已知（本端主机解遮蔽/公开区来源）时揭示窗口已开，
                // 不重复播放，本机作为主机的既有观感保持不变
                if (newlyKnown && loc == CardLocation.Hand.value() && !engine.field.instantPlace) {
                    applyMoveToHandReveal(lp, pcard, false);
                } else if ((loc & CardLocation.Onfield.value()) != 0
                        && (pcard.position & GameField.POS_FACEDOWN) != 0) {
                    // 盖放到场上的里侧卡（来自卡组/墓地/除外/额外，确认消息把真实卡码送达本区）：
                    // 收集后统一播「原地翻开确认→停留→盖回」动画（对齐 C++ L2571-2608）
                    fieldConfirm.add(pcard);
                }
            }
        }
        // 场上里侧确认动画：整批同时翻开、持闸停留、同时盖回（C++ 对 field_confirm 即整批同起同落）
        if (!fieldConfirm.isEmpty()) {
            scheduleFieldConfirmReveal(fieldConfirm);
        }
        // 日志读取后回退缓冲位置，供下方确认面板复用条目数据
        data.position(start);
        // 前置 1 字节 skipPanel 转发 UI（duelclient.cpp L2607：skip_panel 时不弹面板）
        ByteBuffer packed = ByteBuffer.allocate(1 + data.remaining()).order(ByteOrder.LITTLE_ENDIAN);
        packed.put((byte) skipPanel);
        packed.put(data);
        packed.flip();
        // 对齐 duelclient.cpp L2610-2620：将弹确认面板（!skip_panel && panel 卡≥、且非观战/非快进）时，
        // 在派发链上同步持有消息闸门，使 drainPendingMsgs 处理完本条 MSG_CONFIRM_CARDS 后立即断链，
        // 后续 msg 挂起直到对话框确认释放（C++ actionSignal.Wait() 的 Java 等价）。
        // 判定条件与 CardSelectController.showConfirmCardsDialog 的显示条件一致；弹不出时由其释放。
        if (skipPanel == 0 && panelCount > 1
                && !engine.replaySkip && !engine.isSpectator()) {
            engine.holdMsgGateForDialog();
        }
        engine.mainHandler.post(() -> {
            if (engine.replaySkip) { // 回放快进重排：不叠加确认面板（落点后无需回补，gframe 回放同款面板非必需）
                engine.releaseMsgGateForDialog(); // 不会弹：释放上方可能已同步持有的闸门，避免永久挂起
                return;
            }
            if (engine.listener != null) engine.listener.onSelectRequired(27, packed);
        });
    }

    // ==== MSG_SHUFFLE_DECK / MSG_SHUFFLE_HAND ====

    public void applyShuffleDeck(ByteBuffer data) {
        // duelclient.cpp MSG_SHUFFLE_DECK L2620-2657：卡组不足 2 张直接返回（无声无动画）
        final int p = engine.localPlayer(data.get() & 0xFF);
        final List<ClientCard> deck = engine.field.players[p].deck;
        if (deck.size() < 2) return;
        final boolean rev = engine.field.deckReversed;
        if (rev) {
            // L2626-2631：先置 deck_reversed=false 使正向排布为新落点，逐张 10 帧移过去，
            // WaitFrameSignal(10)（is_moving 占住闸门 10 帧）
            engine.field.deckReversed = false;
            for (ClientCard c : deck) {
                if (c != null) engine.field.moveCardAnimated(c, 10);
            }
        }
        // L2633-2636：抖动前逐张清卡码与 is_reversed（卡组以背面呈现）；5 轮抖动延迟到
        // 正向排布到位后启动（30 帧单动画，期间 aniFrame>0 持续占住统一动画闸门）
        final long preDelay = rev ? 170L : 0L;
        Runnable shuffleWork = () -> {
            for (ClientCard c : deck) {
                if (c != null) {
                    c.setCode(0);
                    c.is_reversed = false;
                }
            }
            engine.soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE); // L2638
            for (ClientCard c : deck) {
                if (c != null) engine.field.startDeckShake(c);
            }
            // L2651-2655：抖完 5 轮后恢复倒转标记并 10 帧移回倒转排布（延迟 30 帧接在抖动后）
            if (rev) {
                engine.field.deckReversed = true;
                for (ClientCard c : deck) {
                    if (c != null) engine.field.moveCardAnimated(c, 10, 30);
                }
            }
            // 回放快进重排（即时落位）：不持闸，且整段直接同步执行（见下）
            if (!engine.field.instantPlace) {
                engine.animHoldUntilMs = System.currentTimeMillis()
                        + preDelay + 30L * 17L + 100L;
            }
        };
        if (engine.field.instantPlace) {
            shuffleWork.run();
        } else {
            engine.mainHandler.postDelayed(shuffleWork, preDelay);
        }
        postFieldChanged();
    }

    /**
     * 天变地异等「整个卡组翻转」效果（对齐 duelclient.cpp MSG_REVERSE_DECK L2782-2791）。
     * <p>卡组卡的朝向在本端不是 position 字段（ocgcore 无里/外侧概念，翻转后 position 仍是里侧），
     * 而由 {@code GameFieldGeometry} 的 DECK 分支按 {@code back = (deckReversed == is_reversed)}
     * 决定 rotY（同 client_field.cpp L826-851）。故本消息必须先把 {@code field.deckReversed} 取反，
     * 再把双方卡组逐张 {@code moveCardAnimated(c, 10)}：落点与 rotY 随之翻转，形成整叠卡组
     * 10 帧翻面动画（此前实现只 postFieldChanged、既不翻转标记也无动画，即用户反馈的
     * 「天变地异没有翻转动画」）。
     * <p>C++ 在回放快进下只翻标记不播动画；本端 {@code moveCardAnimated} 在 {@code instantPlace}
     * 时内部同步落位、不产生 aniFrame，语义等价，故只需按同一闸门决定是否持闸。
     * 本消息无载荷、无音效（同 C++）。
     */
    public void applyReverseDeck() {
        engine.field.deckReversed = !engine.field.deckReversed;
        for (int p = 0; p < 2; p++) {
            final List<ClientCard> deck = engine.field.players[p].deck;
            for (ClientCard c : deck) {
                if (c != null) engine.field.moveCardAnimated(c, 10);
            }
        }
        // 10 帧整叠翻面期间持统一动画闸门，令后续消息串行等待（快进落位时不持闸）
        if (!engine.field.instantPlace) {
            engine.animHoldUntilMs = System.currentTimeMillis() + 10L * 17L + 100L;
        }
        postFieldChanged();
    }

    /**
     * 卡组顶卡下发（对齐 duelclient.cpp MSG_DECK_TOP L2792-2804）：引擎在卡组翻转后为双方各发一条
     * 本消息，携顶卡卡码（bit31 = 该卡在翻转后仍应朝下，即 position 为 POS_FACEUP_DEFENSE）。
     * 此前实现丢弃 seq、且 handler 只 postFieldChanged 不写卡码，导致卡组翻成正面朝上后
     * 最上方那张卡既无卡图（code==0 走卡背兜底）、点击也看不了详情（卡详面板对 code&lt;=0 只显示默认）。
     * seq 自卡组顶往下计数，本端以 {@code getCardCount-1-seq} 定位（同 applyConfirmDecktop）。
     */
    public void applyDeckTop(int player, int seq, int code) {
        final int p = engine.localPlayer(player);
        final int deckLoc = CardLocation.Deck.value();
        final int top = engine.field.getCardCount(p, deckLoc) - 1;
        ClientCard c = engine.field.getCard(p, deckLoc, top - seq);
        if (c == null) return;
        c.setCode(code & 0x7fffffff);
        boolean rev = (code & 0x80000000) != 0;
        if (c.is_reversed != rev) {
            // 该卡相对整叠朝向需要反着摆（翻转卡组中仍朝下的一张）：置 is_reversed 并 5 帧翻过去
            c.is_reversed = rev;
            engine.field.moveCardAnimated(c, 5);
            if (!engine.field.instantPlace) {
                engine.animHoldUntilMs = System.currentTimeMillis() + 5L * 17L + 50L;
            }
        }
        postFieldChanged();
    }

    public void applyShuffleHand(ByteBuffer data) {
        // duelclient.cpp MSG_SHUFFLE_HAND L2659-2701：服务端已按洗后顺序重排并重发全部手卡，
        // 此刻列表已是新布局；动画为 聚拢→停留→换面→回新布局 的单条关键帧动画。
        // 本方法是洗切手卡动画的**唯一入口**：效果入手只揭示不洗切（见 applyMoveToHandReveal），
        // 洗切一律等引擎为「非抽卡入手」发出的 MSG_SHUFFLE_HAND 播放一次，避免重复洗切。
        final int p = engine.localPlayer(data.get() & 0xFF);
        final int count = data.get() & 0xFF;
        final List<ClientCard> hand = engine.field.players[p].hand;
        // 洗切接管该侧手牌：入手的揭示蚂蚁线到此为止
        engine.field.revealHighlightCards.clear();
        // 读取新卡面（L2689-2692 在聚拢停留段才 SetCode），停留段后延迟换入，
        // 避免对手视角背面卡在聚拢前因 code 非 0 而提前亮出正面
        final int[] newCodes = new int[count];
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            newCodes[i] = data.getInt();
        }
        if (engine.field.instantPlace) {
            // 回放快进重排：不播聚拢/翻面动画，同步换入洗后新卡面（延迟换面会逃逸到排空后）
            int sidx = 0;
            for (ClientCard c : hand) {
                if (c == null) continue;
                if (sidx < count) setHandCode(c, newCodes[sidx] & 0x7fffffff);
                c.clearDescHints();
                sidx++;
            }
            postFieldChanged();
            return;
        }
        if (count > 1) {
            engine.soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE); // L2663-2664
        }
        // 对齐 C++ L2669-2681：对方手卡先进行一次整体翻面（聚拢 + 卡背朝外）
        boolean needFlipForOpponent = (p == 1) && !engine.replayMode && !engine.isSingleMode;
        if (needFlipForOpponent) {
            for (ClientCard c : hand) {
                if (c == null || c.code == 0) continue;
                // 对齐 C++ L2673-2674：向玩家侧聚拢并旋转至卡背朝向观众
                // Java 用 curX/curY 移动，通过 updateHandFlip 控制翻转
                c.curX += (0.0f - c.curX) / 5.0f; // 向中心聚拢
                // 对手手卡的翻转由 handFlipT 自动推进，此处不直接修改旋转角度
                c.is_moving = true;
                c.aniFrame = 5;
            }
            if (!engine.field.instantPlace) {
                engine.animHoldUntilMs = System.currentTimeMillis() + 5L * 17L;
            }
        }
        int maxTotal = 0;
        for (ClientCard c : hand) {
            if (c == null) continue;
            // 洗切接管：仍处于冻结待码的入手卡（确认码未到达）释放冻结，随洗切动画
            // 从卡组坐标归位到手牌，不致卡在卡组顶
            if (c.pendingFaceupAdd) {
                c.pendingFaceupAdd = false;
                c.revealHeld = false;
            }
            engine.field.startHandShuffle(c);
            maxTotal = Math.max(maxTotal, c.animTotalFrame);
        }
        if (maxTotal > 0) {
            // 停留段末（回位前 1 帧，对应 C++ gather+Wait(11) 后的 SetCode）主线程换入新卡面；
            // 对方手卡在遮蔽视图里卡码为 0，换面后 handFlipT 自动从正面翻回卡背（洗切的盖回段）
            final long revealDelay = (maxTotal - 6L) * 17L;
            engine.mainHandler.postDelayed(() -> {
                int idx = 0;
                for (ClientCard c : hand) {
                    if (c == null) continue;
                    if (idx < count) setHandCode(c, newCodes[idx] & 0x7fffffff);
                    c.clearDescHints(); // L2691：desc_hints.clear()
                    idx++;
                }
                if (engine.listener != null) engine.listener.onFieldChanged();
            }, revealDelay);
            // 统一动画闸门持有时长 = 整段关键帧动画 + 回位尾帧余量
            engine.animHoldUntilMs = System.currentTimeMillis() + (maxTotal + 5L) * 17L;
        } else if (count > 0) {
            // 手卡列表为空（无卡可动画）：直接换面，保持与旧实现一致的同步语义
            int idx = 0;
            for (ClientCard c : hand) {
                if (c == null) continue;
                if (idx < count) setHandCode(c, newCodes[idx] & 0x7fffffff);
                c.clearDescHints();
                idx++;
            }
        }
        postFieldChanged();
    }

    /** 洗切额外卡组（对齐 duelclient.cpp MSG_SHUFFLE_EXTRA L2705-2733）：此前该消息在
     *  GameMessageParser 无 case、完全未处理，洗切额外卡组既无抖动动画也无 shuffle 音效。
     *  仅里侧（非表侧）额外卡参与抖动与换码；不足 2 张直接返回（同 C++ L2708，无声无动画）。 */
    public void applyShuffleExtra(int player, int count, ByteBuffer data) {
        final int p = engine.localPlayer(player);
        final List<ClientCard> extra = engine.field.players[p].extra;
        // 收集里侧额外卡（对齐 C++ !(position & POS_FACEUP)）
        final List<ClientCard> facedowns = new ArrayList<>();
        for (ClientCard c : extra) {
            if (c != null && (c.position & GameField.POS_FACEUP) == 0) facedowns.add(c);
        }
        if (facedowns.size() < 2) return;
        // L2710-2712：非回放快进、count>1 时播 shuffle 音效
        if (!engine.replaySkip && count > 1)
            engine.soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE);
        // L2713-2727：5 轮抖动（复用 startDeckShake 的 30 帧单动画，仅里侧卡；instantPlace 下其内部同步落位）
        for (ClientCard c : facedowns) engine.field.startDeckShake(c);
        if (!engine.field.instantPlace) {
            engine.animHoldUntilMs = System.currentTimeMillis() + 30L * 17L + 100L;
        }
        // L2729-2731：抖后逐张写入洗后卡码（里侧卡以背面呈现，换码不露正面）
        for (ClientCard c : facedowns) {
            if (data.remaining() >= 4) c.setCode(data.getInt() & 0x7fffffff);
        }
        postFieldChanged();
    }

    /** 洗切换面写码：回放遮蔽保护态、或入手揭示持闸窗口内的对方手卡，洗后码为遮蔽零时
     *  不用零码盖掉已揭示的真实卡码（实况遮蔽语义不变，窗口结束自动恢复）。
     *  <p>真正写入遮蔽零码时（走到 setCode(0)）连同 chain_code 一并清零：setCode(0) 会把此前
     *  MSG_CONFIRM_CARDS 揭示过的真实卡码备份进 chain_code，使 {@code displayCode()} 在洗切遮蔽后
     *  仍返回该码——点击背面手卡因此能看到详情与 tip。gframe 手卡直接取 code（duelclient.cpp
     *  MSG_SHUFFLE_HAND L2690 SetCode + L2691 desc_hints.clear），遮蔽即 code=0、无任何身份信息，
     *  chain_code 从不参与手卡展示；故此处遗忘揭示身份，直到该卡被正面放到场上才重新亮码。
     *  早退分支（遮蔽保护 / 持闸窗口）不动，保持已揭示卡面不受影响。 */
    private static void setHandCode(ClientCard c, int newCode) {
        if (newCode == 0 && (GameField.preserveMaskedHandCode || c.revealHeld) && c.code != 0) return;
        c.setCode(newCode);
        if (newCode == 0) c.chain_code = 0;
    }

    /**
     * 卡片从卡组 / 墓地 / 除外区 / 额外卡组经效果加入手卡（引擎对此类移动发 MSG_MOVE 而非
     * MSG_DRAW）：把入手的卡亮出到手牌并施加行进蚂蚁线高亮；<b>对方</b>卡额外做「卡背→正面」
     * 翻面以供对手确认（新版服务端已对 MSG_MOVE→HAND 解除遮蔽，对手拿得到真实卡码；
     * 旧版/第三方仍遮蔽时，公开区来源的卡码由本端自行保留，持闸窗口内遮蔽零刷新不再盖码），
     * <b>己方</b>卡本就正面、不翻给对方看，只高亮展示。
     *
     * <p>洗切不在这里合成：引擎随后会为「非抽卡入手」发出 MSG_SHUFFLE_HAND，由
     * {@link #applyShuffleHand} 唯一播放一次。旧实现在此对整列手卡补放一次 startHandShuffle、
     * 尾段又整列 moveCardAnimated，与引擎的洗切叠加造成「确认动画之后洗切手卡播多次」。
     *
     * <p>不逐张持闸也能工作：统一动画屏障（GameEngine.drainPendingMsgs）在任一消息产生动画后
     * 即关闭并 break，所以连续的 MSG_MOVE 天然串行，无需旧版的 50ms 去抖批次。
     */
    public void applyMoveToHandReveal(int localPlayer, ClientCard arrivingCard) {
        applyMoveToHandReveal(localPlayer, arrivingCard, true);
    }

    /** playSound=false 供 MSG_CONFIRM_CARDS 复用：确认消息入口已播过 REVEAL 音效，不再叠加 */
    public void applyMoveToHandReveal(int localPlayer, ClientCard arrivingCard, boolean playSound) {
        if (arrivingCard == null || engine.field.instantPlace) return;
        // 冻结待码释放：本卡自 MSG_MOVE 起停在卡组顶坐标等码，现卡码已由
        // MSG_CONFIRM_CARDS 写入——直接以正面从卡组位飞入手卡（翻面进度置满，不走
        // 卡背翻开），消除「背面入手→翻开」两步观感
        boolean faceupEntry = arrivingCard.pendingFaceupAdd;
        if (faceupEntry) {
            arrivingCard.pendingFaceupAdd = false;
            if (arrivingCard.code != 0) arrivingCard.handFlipT = 1f;
            engine.field.moveCardAnimated(arrivingCard, 10);
        }
        // 揭示持闸窗口开启：窗口内本卡免疫遮蔽零码刷新（见 ClientCard.revealHeld），
        // 确保无论服务端是否解除 MSG_MOVE→HAND 遮蔽，已拿到的卡码都能支撑完整翻面确认
        arrivingCard.revealHeld = true;
        // localPlayer 已由调用方经 engine.localPlayer 转为本地视角索引：1 即对方席位
        final boolean flip = localPlayer == 1 && !engine.replayMode;
        if (!engine.field.revealHighlightCards.contains(arrivingCard)) {
            engine.field.revealHighlightCards.add(arrivingCard);
        }
        // 揭示只把对方卡的翻面进度归零（卡背起手），5 帧内由 updateHandFlip 依卡码翻到正面；
        // 正面飞入（faceupEntry）的卡全程保持正面，不再归零翻面；
        // 不打断卡片飞入手牌的 is_moving 动画，于是观感为「飞进来 + 边落位边翻开」
        engine.field.startHandReveal(arrivingCard, flip && !faceupEntry);
        if (playSound && !engine.replaySkip)
            engine.soundManager.playSoundEffect(SoundManager.SFX.REVEAL);
        
        // 对齐 C++ L2669-2681：对方手卡先进行一次整体聚拢 + 卡背朝外的翻面动画
        if (flip) {
            arrivingCard.curX += (0.0f - arrivingCard.curX) / 5.0f; // 向中心聚拢
            arrivingCard.is_moving = true;
            arrivingCard.aniFrame = 5;
            if (!engine.field.instantPlace) {
                engine.animHoldUntilMs = System.currentTimeMillis() + 5L * 17L;
            }
        }
        
        // 揭示持闸：飞入 10 帧 + 亮出展示 14 帧（对齐 C++ 翻面后 WaitFrameSignal 的停留段）。
        // 持闸期间后续消息（含引擎的 MSG_SHUFFLE_HAND）不放行，洗切紧接在揭示之后播一次
        final long hold = 24L * 17L;
        engine.animHoldUntilMs = System.currentTimeMillis() + hold;
        final ClientCard card = arrivingCard;
        engine.mainHandler.postDelayed(() -> {
            // 展示结束：解除持闸保护，手卡遮蔽语义恢复正常；清除行进蚂蚁线，
            // 否则 revealHighlightCards 不清空 → 蚂蚁线一直跟着该卡
            card.revealHeld = false;
            engine.field.revealHighlightCards.remove(card);
            if (engine.listener != null) engine.listener.onFieldChanged();
        }, hold);
        postFieldChanged();
    }
}
