package cn.garymb.ygomobile.game;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.ui.dialogs.DuelLogDialog;
import ocgcore.DataManager;

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

    // ==== Batch accumulator for simultaneous hand-card reveals ====
    private final List<GameField.ClientCard> handRevealBatch = new ArrayList<>();
    private int handRevealBatchPlayer = -1;
    private Runnable handRevealFlushRunnable = null;

    // ==== MSG_CONFIRM_DECKTOP / MSG_CONFIRM_CARDS ====

    public void applyConfirmDecktop(int player, int count, ByteBuffer data) {
        Log.d(TAG, "ConfirmDecktop: player=" + player + " count=" + count);
        // 对齐 duelclient.cpp MSG_CONFIRM_DECKTOP L2456-2458：非回放快进时播 reveal 音效 + 记日志
        if (!engine.replaySkip)
            engine.soundManager.playSoundEffect(SoundManager.SFX.REVEAL);
        DuelLogDialog.addLog(DuelLogDialog.sysFormat(207, "翻开卡组上方%d张卡：", count));

        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt() & 0x7fffffff;
            data.position(data.position() + 3);
            DuelLogDialog.addLog("*[" + DataManager.get().getName(code) + "]", code);
        }
    }

    public void applyConfirmCards(int player, int skipPanel, int count, ByteBuffer data) {
        // 对齐 duelclient.cpp MSG_CONFIRM_CARDS L2527-2532：非回放快进时播 reveal 音效 + 记日志
        if (!engine.replaySkip)
            engine.soundManager.playSoundEffect(SoundManager.SFX.REVEAL);
        DuelLogDialog.addLog(DuelLogDialog.sysFormat(208, "确认%d张卡：", count));

        int start = data.position();
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt() & 0x7fffffff;
            data.position(data.position() + 3);
            DuelLogDialog.addLog("*[" + DataManager.get().getName(code) + "]", code);
        }
        // 日志读取后回退缓冲位置，供下方确认面板复用条目数据
        data.position(start);
        // 前置 1 字节 skipPanel 转发 UI（duelclient.cpp L2607：skip_panel 时不弹面板）
        ByteBuffer packed = ByteBuffer.allocate(1 + data.remaining()).order(ByteOrder.LITTLE_ENDIAN);
        packed.put((byte) skipPanel);
        packed.put(data);
        packed.flip();
        engine.mainHandler.post(() -> {
            if (engine.replaySkip) return; // 回放快进重排：不叠加确认面板（落点后无需回补，gframe 回放同款面板非必需）
            if (engine.listener != null) engine.listener.onSelectRequired(27, packed);
        });
    }

    // ==== MSG_SHUFFLE_DECK / MSG_SHUFFLE_HAND ====

    public void applyShuffleDeck(ByteBuffer data) {
        // duelclient.cpp MSG_SHUFFLE_DECK L2620-2657：卡组不足 2 张直接返回（无声无动画）
        final int p = engine.localPlayer(data.get() & 0xFF);
        final List<GameField.ClientCard> deck = engine.field.players[p].deck;
        if (deck.size() < 2) return;
        final boolean rev = engine.field.deckReversed;
        if (rev) {
            // L2626-2631：先置 deck_reversed=false 使正向排布为新落点，逐张 10 帧移过去，
            // WaitFrameSignal(10)（is_moving 占住闸门 10 帧）
            engine.field.deckReversed = false;
            for (GameField.ClientCard c : deck) {
                if (c != null) engine.field.moveCardAnimated(c, 10);
            }
        }
        // L2633-2636：抖动前逐张清卡码与 is_reversed（卡组以背面呈现）；5 轮抖动延迟到
        // 正向排布到位后启动（30 帧单动画，期间 aniFrame>0 持续占住统一动画闸门）
        final long preDelay = rev ? 170L : 0L;
        Runnable shuffleWork = () -> {
            for (GameField.ClientCard c : deck) {
                if (c != null) {
                    c.setCode(0);
                    c.is_reversed = false;
                }
            }
            engine.soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE); // L2638
            for (GameField.ClientCard c : deck) {
                if (c != null) engine.field.startDeckShake(c);
            }
            // L2651-2655：抖完 5 轮后恢复倒转标记并 10 帧移回倒转排布（延迟 30 帧接在抖动后）
            if (rev) {
                engine.field.deckReversed = true;
                for (GameField.ClientCard c : deck) {
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

    public void applyShuffleHand(ByteBuffer data) {
        // duelclient.cpp MSG_SHUFFLE_HAND L2659-2701：服务端已按洗后顺序重排并重发全部手卡，
        // 此刻列表已是新布局；动画为 翻面(对手)→聚拢→停留→回新布局 的单条关键帧动画
        final int p = engine.localPlayer(data.get() & 0xFF);
        final int count = data.get() & 0xFF;
        final List<GameField.ClientCard> hand = engine.field.players[p].hand;
        // 读取新卡面（L2689-2692 在聚拢停留段才 SetCode），停留段后延迟换入，
        // 避免对手视角背面卡在聚拢前因 code 非 0 而提前亮出正面
        final int[] newCodes = new int[count];
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            newCodes[i] = data.getInt();
        }
        if (engine.field.instantPlace) {
            // 回放快进重排：不播聚拢/翻面动画，同步换入洗后新卡面（延迟换面会逃逸到排空后）
            int sidx = 0;
            for (GameField.ClientCard c : hand) {
                if (c == null) continue;
                if (sidx < count) c.setCode(newCodes[sidx] & 0x7fffffff);
                c.clearDescHints();
                sidx++;
            }
            postFieldChanged();
            return;
        }
        if (count > 1) {
            engine.soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE); // L2663-2664
        }
        // L2666：player==1 且非回放非单机时，背面展示的对手手卡先做 5 帧翻面揭示
        // （回放对齐 C++ is_replay_need_flip=false：不做对手手卡翻面揭示）
        final boolean flip = p == 1 && !engine.replayMode;
        int maxTotal = 0;
        for (GameField.ClientCard c : hand) {
            if (c == null) continue;
            engine.field.startHandShuffle(c, flip);
            maxTotal = Math.max(maxTotal, c.animTotalFrame);
        }
        if (maxTotal > 0) {
            // 停留段末（回位前 1 帧，对应 C++ gather+Wait(11) 后的 SetCode）主线程换入新卡面
            int returnStart = maxTotal >= 31 ? 26 : 21;
            final long revealDelay = (returnStart - 1L) * 17L;
            engine.mainHandler.postDelayed(() -> {
                int idx = 0;
                for (GameField.ClientCard c : hand) {
                    if (c == null) continue;
                    if (idx < count) c.setCode(newCodes[idx] & 0x7fffffff);
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
            for (GameField.ClientCard c : hand) {
                if (c == null) continue;
                if (idx < count) c.setCode(newCodes[idx] & 0x7fffffff);
                c.clearDescHints();
                idx++;
            }
        }
        postFieldChanged();
    }

    /**
     * 卡片从卡组 / 墓地 / 除外区 / 额外卡组经效果加入手卡时，
     * 把同时入手的**全部**卡片作为一组统一揭示并施加行进蚂蚁线高亮，展示结束后再洗切。
     *
     * 批量机制：同一轮 drainPendingMsgs 中连续多张 MSG_MOVE 入手时，每张只加入 batch
     * 而不设 animHoldUntilMs（避免闸门阻塞后续消息导致串行），50ms 去抖后 flush
     * 整组统一播放。对齐 C++ MSG_SHUFFLE_HAND L2659-2701 count 张卡片整体动画：
     * 聚拢→停留→换面→回新布局，而非旧版的"揭示→洗切→再聚拢→换面→回位"两次流程。
     */
    public void applyMoveToHandShuffle(int localPlayer, GameField.ClientCard arrivingCard) {
        if (engine.field.instantPlace) return;
        if (arrivingCard == null) return;
        final List<GameField.ClientCard> hand = engine.field.players[localPlayer].hand;
        if (hand == null || hand.isEmpty()) return;
        // Different player? Flush previous batch first
        if (handRevealBatchPlayer != localPlayer && !handRevealBatch.isEmpty()) {
            flushHandRevealBatch();
        }
        handRevealBatchPlayer = localPlayer;
        if (!handRevealBatch.contains(arrivingCard)) {
            handRevealBatch.add(arrivingCard);
        }
        // Debounce: 50ms after last card arrives, flush the batch as one group
        if (handRevealFlushRunnable != null) {
            engine.mainHandler.removeCallbacks(handRevealFlushRunnable);
        }
        handRevealFlushRunnable = this::flushHandRevealBatch;
        engine.mainHandler.postDelayed(handRevealFlushRunnable, 50L);
        postFieldChanged();
    }

    /**
     * Flush accumulated batch: reveal all cards together with marching ants, then single shuffle.
     * Animation gate is set HERE (not per-card) so all cards in the batch process in one
     * drain cycle without the gate blocking subsequent MSG_MOVEs.
     *
     * 严格对齐 C++ MSG_SHUFFLE_HAND L2662-2702 时序（仅一次完整流程）:
     *   ① WaitFrameSignal(5) [停顿 5 帧]
     *   ② (对手)Flip 5 帧
     *   ③ Gather 5 帧向中线聚拢
     *   ④ WaitFrameSignal(11) [停留 11 帧]——此处换入新卡面 (SetCode)
     *   ⑤ MoveCard(5) [回新布局]
     *   ⑥ WaitFrameSignal(5)
     *
     * 关键改动：新卡面在「聚拢停留段」就设置，展开时直接按新顺序落位；
     * 视觉上消除"先收拢→展开→重排"的误导感，实现"收拢即确定顺序→展开即新布局"。
     */
    private void flushHandRevealBatch() {
        handRevealFlushRunnable = null;
        if (handRevealBatch.isEmpty()) return;
        final List<GameField.ClientCard> batch = new ArrayList<>(handRevealBatch);
        handRevealBatch.clear();
        final int localPlayer = handRevealBatchPlayer;
        handRevealBatchPlayer = -1;
        final List<GameField.ClientCard> hand = engine.field.players[localPlayer].hand;
        if (hand == null || hand.isEmpty()) return;
        final boolean flip = localPlayer == 1 && !engine.replayMode;
        
        // 统一动画闸门持有时长 = 揭示 + 洗切全流程
        engine.animHoldUntilMs = System.currentTimeMillis() + (5L + 5L + 5L + 11L + 5L + 5L) * 17L;
        
        // Phase 1: 揭示新入手卡片（蚂蚁线高亮）+ 启动洗切动画
        // 对齐 C++ L2666-2668: 停顿 5 帧
        final long delay1 = 5L * 17L;
        engine.mainHandler.postDelayed(() -> {
            engine.field.revealHighlightCards.clear();
            for (GameField.ClientCard card : batch) {
                if (card != null && !card.is_hand_shuffle && !card.is_hand_reveal) {
                    engine.field.startHandReveal(card, flip);
                    engine.field.revealHighlightCards.add(card);
                }
            }
            // 揭示同时启动整列手卡的洗切动画（避免两次洗切）
            if (hand.size() > 1) {
                engine.soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE);
                for (GameField.ClientCard c : hand) {
                    if (c == null) continue;
                    c.is_hand_reveal = false;
                    engine.field.startHandShuffle(c, false);
                }
            }
            if (engine.listener != null) engine.listener.onFieldChanged();
        }, delay1);
        
        // Phase 2: 聚拢停留段末设置新卡码（对齐 C++ L2692-2695 SetCode 时机）
        // 停顿 5 + 翻面 5+ 聚拢 5 = 15 帧后延迟 1 帧（对应 WaitFrameSignal(11) 的最后 1 帧）
        final long codesetDelay = (5L + 5L + 5L + 10L - 1L) * 17L; // WaitFrameSignal(11) 的倒数第 1 帧
        engine.mainHandler.postDelayed(() -> {
            for (GameField.ClientCard c : hand) {
                if (c == null) continue;
                c.clearDescHints();
            }
            if (engine.listener != null) engine.listener.onFieldChanged();
        }, codesetDelay);
        
        // Phase 3: 回新布局（C++ L2697-2700 MoveCard(5)），再持闸 5 帧（L2701 WaitFrameSignal(5)）
        final long returnDelay = (5L + 5L + 5L + 11L) * 17L;
        engine.mainHandler.postDelayed(() -> {
            // 揭示展示结束（停留段完毕、卡片回新布局）：清除入手的行进蚂蚁线高亮，
            // 否则 revealHighlightCards 永不清空 → 蚂蚁线一直跟在该卡上（含其后续移动）。
            engine.field.revealHighlightCards.clear();
            for (GameField.ClientCard c : hand) {
                if (c == null) continue;
                engine.field.moveCardAnimated(c, 5);
            }
            if (engine.listener != null) engine.listener.onFieldChanged();
        }, returnDelay);
    }
}
