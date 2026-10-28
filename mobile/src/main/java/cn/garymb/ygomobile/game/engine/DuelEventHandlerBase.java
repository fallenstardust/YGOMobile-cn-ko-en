package cn.garymb.ygomobile.game.engine;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.EffectActivationLedger;
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
import cn.garymb.ygomobile.render.card.CardDetailPanel;
import cn.garymb.ygomobile.ui.dialogs.duel.DuelLogDialog;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.enums.CardLocation;


/**
 * Base layer of the split inheritance chain (DuelEventHandlerBase <- DuelEventHandler):
 * implements the MessageHandler forwards for selection / prompt / summon-animation
 * messages plus the field-event cluster up to the card-target handlers. Owns the
 * shared engine reference, the applier facades and the chain-code registry.
 * The remaining announce / attack / toss handlers live in the leaf class.
 */
abstract class DuelEventHandlerBase implements GameMessageParser.MessageHandler {

    // 保持拆分前日志标识，便于与旧版日志比对
    protected static final String TAG = "GameEngine";

    // LP 变化飘字色（gframe 红=扣血 绿=回复 蓝=代价）：硬编码集中到 colors.xml
    protected static final int LP_DAMAGE_RED = YGOUtil.c(R.color.pure_red);
    protected static final int LP_RECOVER_GREEN = YGOUtil.c(R.color.pure_green);
    protected static final int LP_COST_BLUE = YGOUtil.c(R.color.pure_blue);

    protected final GameEngine engine;
    // 重实现块已平移同包协作类（持 engine 反向引用），门面留一行委托
    protected final MoveEventApplier moveApplier;
    protected final TagSwapApplier tagSwapApplier;
    protected final ReloadFieldBuilder reloadBuilder;

    /**
     * 连锁卡码序列（对齐 gframe dField.chains[].code）：MSG_CHAINING 依次入列，
     * 供 MSG_CHAIN_NEGATED/DISABLED 按 ct-1 取被无效的卡码；MSG_CHAIN_END 清空。
     * 仅在通讯（网络）线程访问，无需同步。
     */
    public final List<Integer> chainCodes = new ArrayList<>();

    /**
     * Constructor body anchor kept verbatim so the final fields are
     * assigned exactly once from the leaf forwarding constructor.
     */
    protected DuelEventHandlerBase(GameEngine engine) {
        this.engine = engine;
        this.moveApplier = new MoveEventApplier(engine);
        this.tagSwapApplier = new TagSwapApplier(engine);
        this.reloadBuilder = new ReloadFieldBuilder(engine);
    }

    // ==== 选择/提示类转发（实现在 GameMessageParser） ====

    @Override
    public void onRetry() { engine.messageParser.onRetry(); }

    @Override
    public void onHint(int type, int player, int data) { engine.messageParser.onHint(type, player, data); }

    @Override
    public void onWaiting() { engine.messageParser.onWaiting(); }

    @Override
    public void onStart(int playerType, int duelRule, int lp0, int lp1, int deck0, int extra0, int deck1, int extra1) {
        // 新一局/回放从头重播：清空已发动效果账本并把当前回合号复位到 0（实况与回放同走本链路）；
        // 随后刷新正在展示的卡详，清除上一局/上一轮重播残留的标记文本
        EffectActivationLedger.clear();
        EffectActivationLedger.setCurrentTurn(0);
        CardDetailPanel.refreshCardDetailMarks();
        engine.messageParser.onStart(playerType, duelRule, lp0, lp1, deck0, extra0, deck1, extra1);
    }

    @Override
    public void onWin(int player, int reason) {
        // 用户规格：胜负（当前一局结束信息）出现即清空全部发动标记——「决斗中只能使用1次」
        // 类持续标记的寿命终点就是本局结束；新一局 MSG_START 会重发并再次复位
        EffectActivationLedger.clear();
        CardDetailPanel.refreshCardDetailMarks();
        engine.messageParser.onWin(player, reason);
    }

    @Override
    public void onUpdateData(int player, int location, ByteBuffer data) { engine.messageParser.onUpdateData(player, location, data); }

    @Override
    public void onUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        engine.messageParser.onUpdateCard(player, location, sequence, data);
    }

    @Override
    public void onRequestDeck(int player) { engine.messageParser.onRequestDeck(player); }

    @Override
    public void onSelectBattleCmd(ByteBuffer data) { engine.messageParser.onSelectBattleCmd(data); }

    @Override
    public void onSelectIdleCmd(ByteBuffer data) { engine.messageParser.onSelectIdleCmd(data); }

    @Override
    public void onSelectEffectYn(ByteBuffer data) { engine.messageParser.onSelectEffectYn(data); }

    @Override
    public void onSelectYesNo(ByteBuffer data) { engine.messageParser.onSelectYesNo(data); }

    @Override
    public void onSelectOption(ByteBuffer data) { engine.messageParser.onSelectOption(data); }

    @Override
    public void onSelectCard(ByteBuffer data) { engine.messageParser.onSelectCard(data); }

    @Override
    public void onSelectChain(ByteBuffer data) { engine.messageParser.onSelectChain(data); }

    @Override
    public void onSelectPlace(int player, int count, int fieldMask) { engine.messageParser.onSelectPlace(player, count, fieldMask); }

    @Override
    public void onSelectPosition(int player, int code, int positions) { engine.messageParser.onSelectPosition(player, code, positions); }

    @Override
    public void onSelectTribute(ByteBuffer data) { engine.messageParser.onSelectTribute(data); }

    @Override
    public void onSortChain(ByteBuffer data) { engine.messageParser.onSortChain(data); }

    @Override
    public void onSelectCounter(ByteBuffer data) { engine.messageParser.onSelectCounter(data); }

    @Override
    public void onSelectSum(ByteBuffer data) { engine.messageParser.onSelectSum(data); }

    @Override
    public void onSelectDisfield(int player, int count, int fieldMask) { engine.messageParser.onSelectDisfield(player, count, fieldMask); }

    @Override
    public void onSortCard(ByteBuffer data) { engine.messageParser.onSortCard(data); }

    @Override
    public void onSelectUnselectCard(ByteBuffer data) { engine.messageParser.onSelectUnselectCard(data); }

    // ==== 召唤动画类转发（实现在 SummonAnimationManager） ====

    @Override
    public void onSummoning(int code, int controler, int location, int sequence) {
        engine.summonAnim.onSummoning(code, controler, location, sequence);
    }

    @Override
    public void onSpSummoning(int code, int controler, int location, int sequence) {
        engine.summonAnim.onSpSummoning(code, controler, location, sequence);
    }

    @Override
    public void onFlipSummoning(int code, int controler, int location, int sequence, int position) {
        engine.summonAnim.onFlipSummoning(code, controler, location, sequence, position);
    }

    // ==== 场地事件实现 ====

    @Override
    public void onConfirmDecktop(int player, int count, ByteBuffer data) {
        engine.deckMotion.applyConfirmDecktop(player, count, data);
    }

    @Override
    public void onConfirmExtratop(int player, int count, ByteBuffer data) {
        engine.deckMotion.applyConfirmExtratop(player, count, data);
    }

    @Override
    public void onConfirmCards(int player, int skipPanel, int count, ByteBuffer data) {
        engine.deckMotion.applyConfirmCards(player, skipPanel, count, data);
    }

    @Override
    public void onShuffleDeck(ByteBuffer data) {
        engine.deckMotion.applyShuffleDeck(data);
    }

    @Override
    public void onShuffleHand(ByteBuffer data) {
        engine.deckMotion.applyShuffleHand(data);
    }

    @Override
    public void onShuffleExtra(int player, int count, ByteBuffer data) {
        engine.deckMotion.applyShuffleExtra(player, count, data);
    }

    @Override
    public void onRefreshDeck(int player) {
        engine.deckMotion.postFieldChanged();
    }

    @Override
    public void onSwapGraveDeck(int player) {
        engine.deckMotion.postFieldChanged();
    }

    @Override
    public void onShuffleSetCard(int player, int count, ByteBuffer data) {
        engine.deckMotion.postFieldChanged();
    }

    @Override
    public void onReverseDeck() {
        // duelclient.cpp L2782-2791：翻转 deck_reversed 并逐张重摆双方卡组（整叠翻面动画）
        engine.deckMotion.applyReverseDeck();
    }

    @Override
    public void onDeckTop(int player, int seq, int code) {
        // duelclient.cpp L2792-2804：写入卡组第 seq 张的卡码并按需翻面
        engine.deckMotion.applyDeckTop(player, seq, code);
    }

    @Override
    public void onNewTurn(int player) {
        // Solo mode: the acting player must always sit at localPlayer==0 (bottom of screen).
        // If MSG_NEW_TURN targets the side currently shown as "opponent", request a perspective
        // flip. requestSpectatorSwap defers the flip to the next drainPendingMsgs boundary, so
        // pending animations of the previous turn finish before the view is swapped — same path
        // as the manual spectator "switch view" button.
        if (engine.soloMode && !engine.replayMode && engine.inDuel
                && engine.localPlayer(player & 1) == 1) {
            engine.requestSpectatorSwap();
        }
        engine.field.currentPlayer = engine.localPlayer(player);
        engine.field.turnCount++;
        // 卡详发动标记随回合轮转过期（用户规格：1回合1次类标记在回合结束轮转时清除，
        // 「决斗中只能使用1次」类由渲染方按描述文本判定后持续）：同步账本回合号并刷新正在展示的卡详
        EffectActivationLedger.setCurrentTurn(engine.field.turnCount);
        CardDetailPanel.refreshCardDetailMarks();
        // HINT_ZONE 纵列玫红标注清除：无限泡影类无效效果均携 RESET_PHASE+PHASE_END 于
        // 发动回合结束时失效，而客户端无显式失效通讯，下一个 MSG_NEW_TURN 即已跨过
        // 该 End Phase，此刻清除与效果寿命对齐（duelclient.cpp 仅瞬时高亮 40 帧，无持久态可照抄）
        engine.field.hintZoneMask = 0;
        // tag 队友切换（对齐 duelclient.cpp MSG_NEW_TURN L2879-2885：isTag 且非首回合时，
        // 该队每过一回合在主力/队友间轮换；player 为协议索引即队伍索引，不做 LocalPlayer 换算）
        if (engine.field.isTag && engine.field.turnCount != 1) {
            if (player == 0) {
                engine.tagPlayer[0] = !engine.tagPlayer[0];
            } else {
                engine.tagPlayer[1] = !engine.tagPlayer[1];
            }
        }
        engine.soundManager.playSoundEffect(SoundManager.SFX.NEXT_TURN);
        final int localCurrent = engine.field.currentPlayer;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) {
                engine.listener.onFieldChanged();
                engine.listener.onTurnStarted(localCurrent);
                // tag：回合方切换后名字按 tag_player 在主位/队友间切换
                //（对齐 drawing.cpp L1036-1049 每帧按 tag_player 选 hostname/hostname_tag）
                if (engine.field.isTag) {
                    engine.listener.onPlayerInfoUpdated(0);
                    engine.listener.onPlayerInfoUpdated(1);
                }
            }
        });
    }

    @Override
    public void onNewPhase(int phase) {
        engine.field.currentPhase = phase;
        engine.soundManager.playSoundEffect(SoundManager.SFX.PHASE);
        // 同步派发（不再 mainHandler.post）：使阶段文字（case 101）在本次消息派发期间即入队
        // SpecEffectOverlay（startCard→startLoop 立即置 running），统一动画屏障才能检测到「阶段文字在播」并关闭闸门，
        // 令当前阶段内的卡片移动、阶段按钮文字变化、可选择外框、连锁/conti_act 动画等后续消息排在阶段文字之后，
        // 对齐 duelclient.cpp MSG_NEW_PHASE 的 showcard=101 + WaitFrameSignal(40)（overlay 阶段文字恰 40 帧）。
        if (engine.listener != null) engine.listener.onPhaseChanged(phase);
    }

    @Override
    public void onMove(int code, int oldCtrl, int oldLoc, int oldSeq, int oldPos,
                       int newCtrl, int newLoc, int newSeq, int position, int reason) {
        moveApplier.applyMove(code, oldCtrl, oldLoc, oldSeq, oldPos,
                newCtrl, newLoc, newSeq, position, reason);
    }

    @Override
    public void onPosChange(int code, int ctrl, int loc, int seq, int oldPos, int newPos) {
        moveApplier.applyPosChange(code, ctrl, loc, seq, oldPos, newPos);
    }

    @Override
    public void onSet(int code, int ctrl, int loc, int seq) {
        moveApplier.applySet(code, ctrl, loc, seq);
    }

    @Override
    public void onSwap(int c1ctrl, int c1loc, int c1seq, int c2ctrl, int c2loc, int c2seq) {
        moveApplier.applySwap(c1ctrl, c1loc, c1seq, c2ctrl, c2loc, c2seq);
    }

    @Override
    public void onFieldDisabled(int disabledMask) {
        moveApplier.applyFieldDisabled(disabledMask);
    }

    @Override
    public void onSummoned() {
        engine.hintManager.setEventString(1604, "怪兽召唤成功");
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onSpSummoned() {
        engine.hintManager.setEventString(1606, "怪兽特殊召唤成功");
        // 整组特殊召唤结束：清空本次灵摆召唤的刻度闪动记录，使下一次灵摆召唤重新闪动
        //（同批逐张 MSG_SELECT_PLACE 之间只会收到每张的 MSG_SPSUMMONING，不会到这里）
        engine.field.pendulumPreludeL = null;
        engine.field.pendulumPreludeR = null;
        engine.field.pendulumSelectPending = false;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onFlipSummoned() {
        engine.hintManager.setEventString(1608, "怪兽反转召唤成功");
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onChaining(int code, int pcc, int pcl, int pcs, int subs, int cc, int cl, int cs, int desc) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.ACTIVATE);
        // 记录连锁卡码（对齐 gframe MSG_CHAINED 将 current_chain.code 压入 dField.chains），
        // 供 MSG_CHAIN_NEGATED/DISABLED 按 ct-1 取被无效的卡码
        chainCodes.add(code);
        // 协议侧 controler 转本地索引，保证连锁高亮落在正确的半场
        final int localCc = engine.localPlayer(cc & 1);
        // duelclient.cpp MSG_CHAINING L3345/L3366-3374：四参 GetCard 取发动卡并填充 current_chain，
        // 其后 MSG_BECOME_TARGET 写入 current_chain.target，MSG_CHAINED 再压入 chains；
        // 供 ClientField::ShowCardInfoInList（event_handler.cpp L2937-2947）生成连锁状态标签
        engine.field.currentChain = new GameField.ChainInfo();
        engine.field.currentChain.chainCard = engine.field.getCard(engine.localPlayer(pcc & 1), pcl, pcs, subs);
        // 对齐 duelclient.cpp MSG_CHAINING L3346-3349：发动卡卡码变化（手卡/场上里侧卡翻开揭示）
        // 时 SetCode + MoveCard(pcard, 10)，由 code 0→真实卡码驱动 curRotY 插值，播放反面到正面的转动动画。
        ClientCard chainCard = engine.field.currentChain.chainCard;
        // 卡详情「已发动效果」标记的数据源：普通发动效果不带 EFFECT_FLAG_CLIENT_HINT、
        // 不会发 CHINT_DESC_ADD（descHints 对绝大多数卡恒空，旧版只拿它标记故永远不显示），
        // 而每次效果发动必发的本消息才携 peffect->description=code*16+offset 与发动半场
        // triggering_controler；按 卡码×半场×回合 登记进决斗级账本（不依赖 chainCard 是否解析
        // 成功，墓地/卡组/额外等列表重建临时 ClientCard 场景也能按卡码命中），由
        // CardInfoBinder 映射到描述段落；登记后按卡码门控刷新正在展示的卡详（用户规格1/2：
        // 本回合发动即时反映无需重新点卡，但只有同码/同 alias 卡发动才打扰面板）
        EffectActivationLedger.record(code, desc, localCc, engine.field.turnCount);
        CardDetailPanel.refreshCardDetailMarks(code);
        if (chainCard != null && chainCard.code != code) {
            chainCard.setCode(code);
            engine.field.moveCardAnimated(chainCard, 10);
        }
        // 对齐 duelclient.cpp MSG_CHAINING L3357-3365：墓地/除外（location & 0x30）的卡在原地发动效果时
        // 并不离开堆叠区、核心也不下发 MSG_MOVE，其「向侧边滑出展示→马上滑回同一堆叠层」的动画只能挂在
        // 本消息处理器内。上一版误把滑出挂在 onMove 的同区分支，而该场景根本没有 MSG_MOVE，导致发动滑出
        // 动画完全不显示（即用户反馈的「第二点动画没出来」根因）。卡片位置不变使 animFrom==animTo，
        // 用 ±0.15/帧的两阶段 jitter 轨迹滑出再滑回，方向随控制方（同 C++ 的 cc==1 取 +0.15）。
        // C++ 此段与上方翻面分支相互独立（slide 会覆盖 flip 动画状态），墓地/除外卡通常已亮明故不冲突。
        if (chainCard != null && (chainCard.location & 0x30) != 0) {
            float shift = (localCc == 1) ? 0.15f : -0.15f;
            engine.field.moveCardAnimated(chainCard, 10, 0, shift);
        }
        // 用户规格：怪兽区域（含额外怪兽区，0x04）/魔法陷阱区域（0x08）的卡发动效果时，
        // 卡片矩形明显抬高并落回原高度的短暂小动画（幅度见 GameFieldMotion.BOUNCE_LIFT_Z，
        // 用户反馈 0.05 不明显后已加大），供玩家视觉确认是哪张卡发动；
        // 与上方翻面移动分支并存无冲突（抬跳仅叠加 Z，场上卡移动前后 Z 同权威位），
        // 墓地/除外原地发动的侧滑（位置非 0x04/0x08）不重复抬跳；实况与回放同走本链路
        if (chainCard != null && (chainCard.location & (0x04 | 0x08)) != 0) {
            engine.field.bounceCard(chainCard);
        }
        engine.field.currentChain.code = code;
        engine.field.currentChain.desc = desc;
        engine.field.currentChain.controler = localCc;
        engine.field.currentChain.location = cl;
        engine.field.currentChain.sequence = cs;
        if (engine.listener != null) {
            // 发动动画入队即由统一动画屏障关闭闸门，播完再处理后续消息（对齐 C++ MSG_CHAINING 的 WaitFrameSignal(30)）
            engine.listener.onChainAnimation(code, localCc, cl, cs);
        }
    }

    @Override
    public void onChained(int chainCount) {
        // 对齐 duelclient.cpp MSG_CHAINED L3406：event_string=sys1609「[%ls]的效果发动」，卡名取 current_chain.code
        //（即最近一次 onChaining 压入 chainCodes 的卡码）
        int curCode = chainCodes.isEmpty() ? 0 : chainCodes.get(chainCodes.size() - 1);
        engine.hintManager.setEventString(1609, "[%s]的效果发动", DataManager.get().getName(curCode));
        // duelclient.cpp MSG_CHAINED L3408：chains.push_back(current_chain)。
        // C++ 为值拷贝，Java 用引用语义（同一对象入列），使效果处理期追加的目标同样能体现在状态标签上
        if (engine.field.currentChain != null && !engine.field.chains.contains(engine.field.currentChain)) {
            // 连锁图标锚点（对齐 duelclient.cpp MSG_CHAINING L3375/L3389-3402）：不再从发动卡当前 xyz
            // 快照（手卡发动后卡因支付代价飞向墓地/除外/回卡组，卡片对象 curX 随之改变，图标会跟着跑），
            // 而按 C++ GetChainLocation 取区域/堆叠锚点：手卡固定于「发动方魔陷区后中央」，图标脱离卡片
            // 对象、卡离场也不移动；同位置多连锁按 chc 堆叠偏移避免重叠。
            GameField.ChainInfo cur = engine.field.currentChain;
            float[] anchor = engine.field.getChainLocation(cur.controler, cur.location, cur.sequence);
            if (anchor != null) {
                cur.iconX = anchor[0];
                cur.iconY = anchor[1];
                cur.iconZ = anchor[2];
                // 堆叠偏移（对齐 C++ L3389-3402 意图并对手卡作可读性改进）：
                // 手卡(0x02)按 (controler,location) 计数 chc——同侧所有手卡发动共享同一固定锚点，
                // 多张沿 Y 向外逐层错开（我方 +、对方 −），序号互不重叠且不越过魔陷行；
                // 墓地/除外(0x10/0x20)按 (controler,location) 计数；其余（怪兽/魔陷区等）按
                // (controler,location,sequence) 计数、同格才堆叠，方向与 C++ MSG_CHAINING L3402 一致。
                int cl = cur.location;
                boolean isHand = cl == 0x02;
                boolean byLocOnly = isHand || cl == 0x10 || cl == 0x20;
                int chc = 0;
                for (GameField.ChainInfo ch : engine.field.chains) {
                    if (byLocOnly) {
                        if (ch.controler == cur.controler && ch.location == cl) chc++;
                    } else {
                        if (ch.controler == cur.controler && ch.location == cl && ch.sequence == cur.sequence) chc++;
                    }
                }
                if (isHand) {
                    cur.iconY += (cur.controler == 0 ? chc * 0.25f : -chc * 0.25f);
                } else {
                    cur.iconY += chc * 0.25f;
                }
                cur.iconPosCaptured = true;
            } else {
                // 无锚点（overlay 等）：此刻卡片尚未因结算离开原位，图标固定在此处直到连锁消失
                ClientCard cc = cur.chainCard;
                if (cc != null) {
                    cur.iconX = cc.curX;
                    cur.iconY = cc.curY;
                    cur.iconZ = cc.curZ;
                    cur.iconPosCaptured = true;
                }
            }
            engine.field.chains.add(cur);
        }
    }

    @Override
    public void onChainSolving(int chainCount) {

    }

    @Override
    public void onChainSolved(int chainCount) {

    }

    @Override
    public void onChainEnd() {
        // 对齐 duelclient.cpp MSG_CHAIN_END L3436-3442：逐链清除对象卡与发动卡的
        // is_showchaintarget 后再 chains.clear()
        for (GameField.ChainInfo ch : engine.field.chains) {
            for (ClientCard t : ch.targets) t.is_showchaintarget = false;
            if (ch.chainCard != null) ch.chainCard.is_showchaintarget = false;
        }
        chainCodes.clear();
        engine.field.chains.clear();
        engine.field.currentChain = new GameField.ChainInfo();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onChainNegated(int chainCount) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.NEGATE);
        engine.summonAnim.postNegatedAnimation(chainCount);
    }

    @Override
    public void onChainDisabled(int chainCount) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.NEGATE);
        engine.summonAnim.postNegatedAnimation(chainCount);
    }

    @Override
    public void onDraw(int player, int count, int[] codes) {
        // duelclient.cpp MSG_DRAW L3519-3557
        final int p = engine.localPlayer(player);
        final int deckLoc = CardLocation.Deck.value();
        final int handLoc = CardLocation.Hand.value();
        // 1) 给被抽的卡组顶设卡码（对齐 L3529-3534：deck_reversed 且 code=0 时不揭码）
        int top = engine.field.getCardCount(p, deckLoc) - 1;
        for (int i = 0; i < count; i++) {
            ClientCard pcard = engine.field.getCard(p, deckLoc, top - i);
            if (pcard != null && (!engine.field.deckReversed || codes[i] != 0)) {
                applyDrawCode(pcard, localDrawCode(p, codes[i]));
            }
        }
        // 2) 起手 N 张 / 单独抽卡动画：严格对齐 C++ L3542-3552 的逐张时序——每次从卡组顶取一张、
        //    入手卡、让该手卡从卡组顶飞入并顺带把已在手中的卡重排让位，随后 WaitFrameSignal(5)
        //    再抽下一张。旧实现在同一同步循环里对每张重复 moveCardAnimated 并覆盖 animDelayFrame，
        //    令所有卡同时起步、抽卡逐张感被抹平（即“缺少抽卡动画”的根因）。这里把每张卡的入手
        //    按 5 帧节拍用 postDelayed 依次排布，主线程逐帧渲染即可看到一张张抽入的动画。
        final long stepMs = 5L * 17L;
        if (engine.field.instantPlace) {
            // 回放快进重排：同步逐张入手，不按 5 帧节拍排布调度、不持闸
            //（postDelayed 回调会逃逸到排空之后破坏快进落点一致性）
            for (int i = 0; i < count; i++) drawOneCard(p, deckLoc, handLoc, codes, i);
        } else {
            for (int i = 0; i < count; i++) {
                final int idx = i;
                engine.mainHandler.postDelayed(() -> drawOneCard(p, deckLoc, handLoc, codes, idx), idx * stepMs);
            }
            // 抽卡展示动画持闸：最后一张延迟 (count-1)*5 帧启动 + 10 帧飞行 + 尾帧余量
            engine.animHoldUntilMs = System.currentTimeMillis() + ((count - 1) * 5L + 15L) * 17L;
        }
        engine.hintManager.setEventString(p == 0 ? 1611 : 1612, p == 0 ? "我方抽了%d张卡" : "对方抽了%d张卡", count);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) {
                engine.listener.onFieldChanged();
                engine.listener.onPlayerInfoUpdated(p);
            }
        });
    }

    /**
     * 单张抽卡（onDraw 按 5 帧节拍调度调用，对齐 duelclient.cpp MSG_DRAW 循环体 L3543-3550）：
     * 从卡组顶移除 → 加入手卡 → 该卡从卡组位飞入 + 已有手卡重排让位（10 帧）→ 播放抽卡音效。
     * <p>
     * 对对手抽卡（p==1），务必将 handFlipT 显式置为 0f（背面朝下）。因对手抽卡的 code 已由服务器遮蔽为 0，
     * 但若复用之前作为手牌的旧对象残留非零 code，渲染层仍会绘制卡面。故通过强制翻面进度回退到背面终态（0f），
     * 即使 code 异常不为 0，handShowsFace 也会返回 false 而画卡背；同时确保代码读取的是本次收到的 masked code
     *（而非对象可能持有的链备份 chain_code/displayCode）。
     */
    protected void drawOneCard(int p, int deckLoc, int handLoc, int[] codes, int idx) {
        int t = engine.field.getCardCount(p, deckLoc) - 1;
        if (t < 0) return;
        ClientCard pcard = engine.field.removeCard(p, deckLoc, t);
        if (pcard == null) {
            pcard = new ClientCard();
            pcard.owner = p;
            pcard.controler = p;
        }
        // 关键：立即写入本次收到的卡码（已含服务器的遮蔽处理：对手抽卡时为 0），防止复用卡组顶
        // 旧对象时残留上一次的卡码、或经 chain_code 回退取值，造成信息泄露。必须在设置 handFlipT
        // 之前执行，使卡码与翻面态一致。
        if (idx < codes.length) applyDrawCode(pcard, localDrawCode(p, codes[idx]));
        
        // 我方抽卡入手恒为正面（对齐 C++ gframe 我方手卡只按卡码贴卡面、从不走卡背）：从卡组顶
        // 取回的 ClientCard 对象可能残留此前作为手卡 / 洗切被置 0 的 handFlipT，若放任不管，
        // updateHandFlip 会以 0→1 逐帧推进，出现「背面入手→短暂停留→翻开」与原版不一致的观感。
        // 入手前直接把翻面进度吸附到正面终态，渲染线程永不看到其卡背态；code==0 时渲染层仍按卡码
        // 画卡背不会误露。仅对 p==0（我方视角）生效，对方抽卡维持遮蔽背面语义不变
        if (p == 0) {
            pcard.handFlipT = 1f;
        } else {
            // 对手的抽卡必须保持卡背：强制 backface 终态（0f）+ 禁止显示 code=0 时的 cardback
            pcard.handFlipT = 0f;
        }
        engine.field.addCard(p, handLoc, 0, pcard);
        for (ClientCard hc : engine.field.players[p].hand) {
            // 冻结待码的入手卡不参与让位重排，避免被提前拽离卡组顶坐标
            if (hc != null && !hc.pendingFaceupAdd) engine.field.moveCardAnimated(hc, 10);
        }
        engine.soundManager.playSoundEffect(SoundManager.SFX.DRAW);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    /**
     * 抽卡写码：遮蔽零码时把 chain_code 一并清零。setCode(0) 会把被顶掉的旧卡码备份进
     * chain_code（ClientCard L186-192），而渲染层在卡飞行途中按 {@code code==0 ? chain_code}
     * 回退取卡面（FieldCardRenderer L185，对齐 drawing.cpp L619-620）——复用卡组顶旧对象时
     * 那条回退分支就能把别人的卡画出来。语义与 DeckHandMotionManager.setHandCode 一致：
     * 遮蔽即遗忘，chain_code 只服务「已公开卡飞行中继续绘旧卡面」，从不服务遮蔽零码。
     */
    private static void applyDrawCode(ClientCard c, int newCode) {
        c.setCode(newCode);
        if (newCode == 0) c.chain_code = 0;
    }

    /**
     * 本地视角下的抽卡卡码（对齐服务端 single_duel.cpp MSG_DRAW L1198-1204 的遮蔽语义、
     * operations.cpp draw_command L404 的表侧判定）：MSG_DRAW 卡码 bit31 表示该卡是否表侧被抽
     *（仅 EFFECT_PUBLIC 抽入的卡表侧）。里侧抽卡（bit31 未置位）属私有信息——实况局主机已在
     * DuelAnalyzer 侧对对手把码清零，但 solo / 主机全量视图（DuelViewBroadcaster.sendDualViewAndRecord
     * 的 solo 分支）会把对手抽卡真实码直接送达本地连接，而本端手卡渲染只按 {@code code != 0}
     * 翻成正面（GameFieldMotionBase.updateHandFlip L617 与 FieldCardRenderer.handShowsFace），
     * 于是出现「抽卡阶段常规抽卡 / 效果抽卡都把对方抽的卡亮出卡面」的泄露。故此处对对手（p==1）
     * 的私有抽卡把码遮蔽为 0、令手卡保持卡背；本方抽卡（p==0）与公开抽卡（bit31 置位，如天变
     * 地异的 public 抽卡）保留原码。检索类「卡组→手卡」不走本路径（经 MSG_CONFIRM_CARDS 揭示），
     * 其展示不受影响——即「抽卡不展示、非抽卡把卡组卡加入手卡才展示」的口径。
     */
    private static int localDrawCode(int p, int rawCode) {
        if (p == 1 && (rawCode & 0x80000000) == 0) return 0;
        return rawCode & 0x7fffffff;
    }

    @Override
    public void onDamage(int player, int amount) {
        // 协议侧玩家 → 本地视角索引（0=我方）：我方为后攻时伤害/回复正确落到对应半场
        // 与顶部信息栏左半边（我方）布局保持一致
        int p = engine.localPlayer(player & 1);
        int fin = Math.max(0, engine.field.players[p].lp - amount);
        engine.field.players[p].lp = fin;
        engine.field.startLpChange(p, fin, LP_DAMAGE_RED, "-" + amount, true);
        engine.soundManager.playSoundEffect(SoundManager.SFX.DAMAGE);
        engine.hintManager.setEventString(p == 0 ? 1613 : 1614, p == 0 ? "我方受到%d伤害" : "对方受到%d伤害", amount);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onRecover(int player, int amount) {
        int p = engine.localPlayer(player & 1);
        int fin = engine.field.players[p].lp + amount;
        engine.field.players[p].lp = fin;
        engine.field.startLpChange(p, fin, LP_RECOVER_GREEN, "+" + amount, true);
        engine.soundManager.playSoundEffect(SoundManager.SFX.RECOVER);
        engine.hintManager.setEventString(p == 0 ? 1615 : 1616, p == 0 ? "我方回复%d基本分" : "对方回复%d基本分", amount);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onEquip(int eqCode, int eqCtrl, int eqLoc, int eqSeq,
                        int tCtrl, int tLoc, int tSeq) {
        // duelclient.cpp MSG_EQUIP L3619-3652：旧装备关系解除（equipped 集合摘除、
        // 集合空时清 is_showequip），再登记新关系 pc1->equipTarget=pc2、pc2->equipped.add(pc1)
        ClientCard pc1 = engine.field.getCard(engine.localPlayer(eqCtrl & 1), eqLoc, eqSeq);
        ClientCard pc2 = engine.field.getCard(engine.localPlayer(tCtrl & 1), tLoc, tSeq);
        if (pc1 != null && pc2 != null) {
            if (pc1.equipTarget != null) {
                pc1.is_showequip = false;
                pc1.equipTarget.is_showequip = false;
                pc1.equipTarget.equipped.remove(pc1);
            }
            pc1.equipCard = pc2; // 兼容旧字段（HUD 图标的装备卡指向）
            pc1.equipTarget = pc2;
            if (!pc2.equipped.contains(pc1)) pc2.equipped.add(pc1);
        }
        engine.soundManager.playSoundEffect(SoundManager.SFX.EQUIP);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onLpUpdate(int player, int lp) {
        int p = engine.localPlayer(player & 1);
        engine.field.players[p].lp = lp;
        engine.field.startLpChange(p, lp, 0, null, false);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onUnequip(int ctrl, int loc, int seq) {
        // duelclient.cpp MSG_UNEQUIP L3671-3691：从被装备卡 equipped 集合摘除，集合空时清 is_showequip
        ClientCard card = engine.field.getCard(engine.localPlayer(ctrl & 1), loc, seq);
        if (card != null) {
            if (card.equipTarget != null) {
                card.equipTarget.equipped.remove(card);
                card.equipTarget.is_showequip = false;
                card.is_showequip = false;
                card.equipTarget = null;
            }
            card.equipCard = null;
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onCardTarget(int c1ctrl, int c1loc, int c1seq, int c2ctrl, int c2loc, int c2seq) {
        // duelclient.cpp MSG_CARD_TARGET L3692-3717：c1->cardTarget.insert(c2); c2->ownerTarget.insert(c1)
        ClientCard c1 = engine.field.getCard(engine.localPlayer(c1ctrl & 1), c1loc, c1seq);
        ClientCard c2 = engine.field.getCard(engine.localPlayer(c2ctrl & 1), c2loc, c2seq);
        if (c1 != null && c2 != null) {
            if (!c1.targetCards.contains(c2)) c1.targetCards.add(c2);
            if (!c2.ownerTarget.contains(c1)) c2.ownerTarget.add(c1);
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }
}
