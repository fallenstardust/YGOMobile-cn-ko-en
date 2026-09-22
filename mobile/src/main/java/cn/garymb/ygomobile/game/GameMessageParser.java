package cn.garymb.ygomobile.game;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.ui.dialogs.DuelLogDialog;
import ocgcore.DataManager;
import ocgcore.enums.CardLocation;
import ocgcore.enums.DuelPhase;
import ocgcore.enums.GameMessage;
import ocgcore.enums.Query;

public class GameMessageParser {
    private static final String TAG = "GameMsgParser";

    public interface MessageHandler {
        void onRetry();
        void onHint(int type, int player, int data);
        void onWaiting();
        void onStart(int playerType, int duelRule, int lp0, int lp1,
                     int deck0, int extra0, int deck1, int extra1);
        void onWin(int player, int reason);
        void onUpdateData(int player, int location, ByteBuffer data);
        void onUpdateCard(int player, int location, int sequence, ByteBuffer data);
        void onRequestDeck(int player);
        void onSelectBattleCmd(ByteBuffer data);
        void onSelectIdleCmd(ByteBuffer data);
        void onSelectEffectYn(ByteBuffer data);
        void onSelectYesNo(ByteBuffer data);
        void onSelectOption(ByteBuffer data);
        void onSelectCard(ByteBuffer data);
        void onSelectChain(ByteBuffer data);
        void onSelectPlace(int player, int count, int fieldMask);
        void onSelectPosition(int player, int code, int positions);
        void onSelectTribute(ByteBuffer data);
        void onSortChain(ByteBuffer data);
        void onSelectCounter(ByteBuffer data);
        void onSelectSum(ByteBuffer data);
        void onSelectDisfield(int player, int count, int fieldMask);
        void onSortCard(ByteBuffer data);
        void onSelectUnselectCard(ByteBuffer data);
        void onConfirmDecktop(int player, int count, ByteBuffer data);
        void onConfirmCards(int player, int skipPanel, int count, ByteBuffer data);
        /** duelclient.cpp MSG_SHUFFLE_DECK L2620-2657：player(1)，handler 内自读 */
        void onShuffleDeck(ByteBuffer data);
        /** duelclient.cpp MSG_SHUFFLE_HAND L2659-2701：player(1) count(1) + count×code(4)，
         *  新卡面在聚拢动画停留段才换入（L2689-2692），故 handler 内自行读字段 */
        void onShuffleHand(ByteBuffer data);
        void onRefreshDeck(int player);
        void onSwapGraveDeck(int player);
        void onShuffleSetCard(int player, int count, ByteBuffer data);
        void onReverseDeck(int player);
        void onDeckTop(int player, int code);
        void onNewTurn(int player);
        void onNewPhase(int phase);
        void onMove(int code, int oldControler, int oldLocation, int oldSequence, int oldPosition,
                    int newControler, int newLocation, int newSequence, int position, int reason);
        void onPosChange(int code, int controler, int location, int sequence,
                         int oldPos, int newPos);
        void onSet(int code, int controler, int location, int sequence);
        void onSwap(int c1_ctrl, int c1_loc, int c1_seq, int c2_ctrl, int c2_loc, int c2_seq);
        void onFieldDisabled(int disabledMask);
        void onSummoning(int code, int controler, int location, int sequence);
        void onSummoned();
        void onSpSummoning(int code, int controler, int location, int sequence);
        void onSpSummoned();
        void onFlipSummoning(int code, int controler, int location, int sequence);
        void onFlipSummoned();
        void onChaining(int code, int pcc, int pcl, int pcs, int subs, int cc, int cl, int cs, int desc);
        void onChained(int chainCount);
        void onChainSolving(int chainCount);
        void onChainSolved(int chainCount);
        void onChainEnd();
        void onChainNegated(int chainCount);
        void onChainDisabled(int chainCount);
        void onDraw(int player, int count, int[] codes);
        void onDamage(int player, int amount);
        void onRecover(int player, int amount);
        void onEquip(int equip_code, int equip_ctrl, int equip_loc, int equip_seq,
                     int target_ctrl, int target_loc, int target_seq);
        void onLpUpdate(int player, int lp);
        void onUnequip(int controler, int location, int sequence);
        void onCardTarget(int c1_ctrl, int c1_loc, int c1_seq, int c2_ctrl, int c2_loc, int c2_seq);
        void onCancelTarget(int c1_ctrl, int c1_loc, int c1_seq, int c2_ctrl, int c2_loc, int c2_seq);
        void onPayLpCost(int player, int cost);
        void onAddCounter(int type, int controler, int location, int sequence, int count);
        void onRemoveCounter(int type, int controler, int location, int sequence, int count);
        void onAttack(int attacker_ctrl, int attacker_loc, int attacker_seq,
                       int defender_ctrl, int defender_loc, int defender_seq);
        void onBattle(int attacker_atk, boolean attacker_atk_pos,
                       int defender_atk, boolean defender_atk_pos);
        void onAttackDisabled();
        void onDamageStepStart();
        void onDamageStepEnd();
        void onMissedEffect(int code, int controler, int location, int sequence, int effectId);
        void onTossCoin(int player, int count, ByteBuffer results);
        void onTossDice(int player, int count, ByteBuffer results);
        void onAnnounceRace(int player, int count, int availableRaces);
        void onAnnounceAttrib(int player, int count, int availableAttribs);
        void onAnnounceCard(int player, ByteBuffer data);
        void onAnnounceNumber(int player, ByteBuffer data);
        /**
         * duelclient.cpp MSG_CARD_HINT L4094-4100：
         * controler(1) location(1) sequence(1) subseq(1，C++ 读入即弃) hintType(1) value(4)
         */
        void onCardHint(int player, int location, int sequence, int hintType, int value);
        /** duelclient.cpp MSG_BECOME_TARGET L3486-3497：count(1) + count×[ctrl1 loc1 seq1 ss1(忽略)] */
        void onBecomeTarget(int count, ByteBuffer data);
        void onTagSwap(int player);
        /** duelclient.cpp MSG_RELOAD_FIELD L4287-4441：duel_rule(1) + 双方[lp(4) + MZone7×(present+position+ovc) + SZone8×(present+position) + deck/hand/grave/removed/extra各[cnt+cnt×无详情] + extra_p_count(1)] + refreshAll + chains[cnt + cnt×15字节]，载荷解析全部在 handler 内做 */
        void onReloadField(ByteBuffer data);
        void onAiName(String name);
        void onShowHint(String hint);
        void onMatchKill(int code);
        void onCustomMsg(String msg);
        void onDuelWinner(int player, int reason);
    }

    public static void parse(int msgType, ByteBuffer buf, MessageHandler handler) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
        GameMessage msg = GameMessage.valueOf(msgType);
        if (msg == null) {
            Log.w(TAG, "Unknown message: " + msgType);
            return;
        }
        switch (msg) {
            case Retry:
                handler.onRetry();
                break;
            case Hint:
                handler.onHint(buf.get() & 0xFF, buf.get() & 0xFF, buf.getInt());
                break;
            case Waiting:
                handler.onWaiting();
                break;
            case Start: {
                // duelclient.cpp L1622-1642：playertype+duel_rule+lp0+lp1+deckc/extrac×2
                int playerType = buf.get() & 0xFF;
                int duelRule = buf.get() & 0xFF;
                int lp0 = buf.getInt();
                int lp1 = buf.getInt();
                int deck0 = buf.getShort() & 0xFFFF;
                int extra0 = buf.getShort() & 0xFFFF;
                int deck1 = buf.getShort() & 0xFFFF;
                int extra1 = buf.getShort() & 0xFFFF;
                handler.onStart(playerType, duelRule, lp0, lp1, deck0, extra0, deck1, extra1);
                break;
            }
            case Win:
                handler.onWin(buf.get() & 0xFF, buf.get() & 0xFF);
                break;
            case UpdateData:
            case UpdateCard:
                handleUpdate(msg, buf, handler);
                break;
            case RequestDeck:
                handler.onRequestDeck(buf.get() & 0xFF);
                break;
            case SelectBattleCmd:
                handler.onSelectBattleCmd(buf);
                break;
            case SelectIdleCmd:
                handler.onSelectIdleCmd(buf);
                break;
            case SelectEffectYn:
                handler.onSelectEffectYn(buf);
                break;
            case SelectYesNo:
                handler.onSelectYesNo(buf);
                break;
            case SelectOption:
                handler.onSelectOption(buf);
                break;
            case SelectCard:
                handler.onSelectCard(buf);
                break;
            case SelectChain:
                handler.onSelectChain(buf);
                break;
            case SortChain:
                handler.onSortChain(buf);
                break;
            case SelectTribute:
                handler.onSelectTribute(buf);
                break;
            case SelectSum:
                handler.onSelectSum(buf);
                break;
            case SelectCounter:
                handler.onSelectCounter(buf);
                break;
            case SortCard:
                handler.onSortCard(buf);
                break;
            case SelectUnselectCard:
                handler.onSelectUnselectCard(buf);
                break;
            case SelectPlace:
            case SelectDisfield: {
                int player = buf.get() & 0xFF;
                int count = buf.get() & 0xFF;
                int fieldMask = buf.getInt();
                if (msg == GameMessage.SelectPlace)
                    handler.onSelectPlace(player, count, fieldMask);
                else
                    handler.onSelectDisfield(player, count, fieldMask);
                break;
            }
            case SelectPosition: {
                int player = buf.get() & 0xFF;
                int code = buf.getInt();
                int positions = buf.get() & 0xFF;
                handler.onSelectPosition(player, code, positions);
                break;
            }
            case ConfirmDecktop: {
                int player = buf.get() & 0xFF;
                int count = buf.get() & 0xFF;
                handler.onConfirmDecktop(player, count, buf);
                break;
            }
            case ConfirmCards: {
                // duelclient.cpp L2519-2522：player(1) skip_panel(1) count(1) + n×[code4 ctrl1 loc1 seq1]
                int player = buf.get() & 0xFF;
                int skipPanel = buf.get() & 0xFF;
                int count = buf.get() & 0xFF;
                handler.onConfirmCards(player, skipPanel, count, buf);
                break;
            }
            case ShuffleDeck:
                handler.onShuffleDeck(buf);
                break;
            case ShuffleHand:
                handler.onShuffleHand(buf);
                break;
            case RefreshDeck:
                handler.onRefreshDeck(buf.get() & 0xFF);
                break;
            case SwapGraveDeck:
                handler.onSwapGraveDeck(buf.get() & 0xFF);
                break;
            case ShuffleSetCard: {
                int player = buf.get() & 0xFF;
                int count = buf.get() & 0xFF;
                handler.onShuffleSetCard(player, count, buf);
                break;
            }
            case ReverseDeck:
                handler.onReverseDeck(buf.get() & 0xFF);
                break;
            case DeckTop: {
                int player = buf.get() & 0xFF;
                buf.get(); // seq
                int code = buf.getInt();
                handler.onDeckTop(player, code);
                break;
            }
            case NewTurn:
                handler.onNewTurn(buf.get() & 0xFF);
                break;
            case NewPhase:
                handler.onNewPhase(buf.getShort() & 0xFFFF);
                break;
            case Move: {
                int code = buf.getInt();
                int oldCtrl = buf.get() & 0xFF;
                int oldLoc = buf.get() & 0xFF;
                int oldSeq = buf.get() & 0xFF;
                int oldPos = buf.get() & 0xFF; // 超量素材离场时该字节为素材在 overlayed 中的序号
                int newCtrl = buf.get() & 0xFF;
                int newLoc = buf.get() & 0xFF;
                int newSeq = buf.get() & 0xFF;
                int pos = buf.get() & 0xFF;
                int reason = buf.getInt();
                handler.onMove(code, oldCtrl, oldLoc, oldSeq, oldPos, newCtrl, newLoc, newSeq, pos, reason);
                break;
            }
            case PosChange: {
                int code = buf.getInt();
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                int oldPos = buf.get() & 0xFF;
                int newPos = buf.get() & 0xFF;
                handler.onPosChange(code, ctrl, loc, seq, oldPos, newPos);
                break;
            }
            case Set: {
                int code = buf.getInt();
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                buf.get(); // position
                handler.onSet(code, ctrl, loc, seq);
                break;
            }
            case Swap: {
                int c1code = buf.getInt();
                int c1ctrl = buf.get() & 0xFF;
                int c1loc = buf.get() & 0xFF;
                int c1seq = buf.get() & 0xFF;
                buf.get(); // position
                int c2code = buf.getInt();
                int c2ctrl = buf.get() & 0xFF;
                int c2loc = buf.get() & 0xFF;
                int c2seq = buf.get() & 0xFF;
                buf.get(); // position
                handler.onSwap(c1ctrl, c1loc, c1seq, c2ctrl, c2loc, c2seq);
                break;
            }
            case FieldDisabled: {
                int disabledMask = buf.getInt();
                handler.onFieldDisabled(disabledMask);
                break;
            }
            case Summoning: {
                int code = buf.getInt();
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                buf.get(); // position
                handler.onSummoning(code, ctrl, loc, seq);
                break;
            }
            case SpSummoning: {
                int code = buf.getInt();
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                buf.get(); // position
                handler.onSpSummoning(code, ctrl, loc, seq);
                break;
            }
            case FlipSummoning: {
                int code = buf.getInt();
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                buf.get(); // position
                handler.onFlipSummoning(code, ctrl, loc, seq);
                break;
            }
            case FlipSummoned:
                handler.onFlipSummoned();
                break;
            case Chaining: {
                int code = buf.getInt();
                int pcc = buf.get() & 0xFF;
                int pcl = buf.get() & 0xFF;
                int pcs = buf.get() & 0xFF;
                int subs = buf.get() & 0xFF;
                int cc = buf.get() & 0xFF;
                int cl = buf.get() & 0xFF;
                int cs = buf.get() & 0xFF;
                int desc = buf.getInt();
                // ct is read but not used in C++, so we skip it
                int ct = buf.get() & 0xFF;
                handler.onChaining(code, pcc, pcl, pcs, subs, cc, cl, cs, desc);
                break;
            }
            case Chained:
                handler.onChained(buf.get() & 0xFF);
                break;
            case ChainSolving:
                handler.onChainSolving(buf.get() & 0xFF);
                break;
            case ChainSolved:
                handler.onChainSolved(buf.get() & 0xFF);
                break;
            case ChainEnd:
                handler.onChainEnd();
                break;
            case ChainNegated:
                handler.onChainNegated(buf.get() & 0xFF);
                break;
            case ChainDisabled:
                handler.onChainDisabled(buf.get() & 0xFF);
                break;
            case BecomeTarget: {
                // duelclient.cpp MSG_BECOME_TARGET L3488-3497：目标写入 current_chain.target
                int btCount = buf.get() & 0xFF;
                handler.onBecomeTarget(btCount, buf);
                break;
            }
            case Draw: {
                int player = buf.get() & 0xFF;
                int count = buf.get() & 0xFF;
                int[] codes = new int[count];
                for (int i = 0; i < count && buf.remaining() >= 4; i++) {
                    codes[i] = buf.getInt();
                }
                handler.onDraw(player, count, codes);
                break;
            }
            case Damage: {
                int player = buf.get() & 0xFF;
                int amount = buf.getInt();
                handler.onDamage(player, amount);
                break;
            }
            case Recover: {
                int player = buf.get() & 0xFF;
                int amount = buf.getInt();
                handler.onRecover(player, amount);
                break;
            }
            case Equip: {
                int eqCtrl = buf.get() & 0xFF;
                int eqLoc = buf.get() & 0xFF;
                int eqSeq = buf.get() & 0xFF;
                buf.get(); // position
                int tCtrl = buf.get() & 0xFF;
                int tLoc = buf.get() & 0xFF;
                int tSeq = buf.get() & 0xFF;
                buf.get(); // position
                handler.onEquip(0, eqCtrl, eqLoc, eqSeq, tCtrl, tLoc, tSeq);
                break;
            }
            case Unequip: {
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                buf.get(); // position
                handler.onUnequip(ctrl, loc, seq);
                break;
            }
            case CardTarget:
            case CancelTarget: {
                int c1ctrl = buf.get() & 0xFF;
                int c1loc = buf.get() & 0xFF;
                int c1seq = buf.get() & 0xFF;
                buf.get(); // position
                int c2ctrl = buf.get() & 0xFF;
                int c2loc = buf.get() & 0xFF;
                int c2seq = buf.get() & 0xFF;
                buf.get(); // position
                if (msg == GameMessage.CardTarget)
                    handler.onCardTarget(c1ctrl, c1loc, c1seq, c2ctrl, c2loc, c2seq);
                else
                    handler.onCancelTarget(c1ctrl, c1loc, c1seq, c2ctrl, c2loc, c2seq);
                break;
            }
            case PayLpCost: {
                int player = buf.get() & 0xFF;
                int cost = buf.getInt();
                handler.onPayLpCost(player, cost);
                break;
            }
            case AddCounter: {
                int type = buf.getShort() & 0xFFFF;
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                int count = buf.getShort() & 0xFFFF;
                handler.onAddCounter(type, ctrl, loc, seq, count);
                break;
            }
            case RemoveCounter: {
                int type = buf.getShort() & 0xFFFF;
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                int count = buf.getShort() & 0xFFFF;
                handler.onRemoveCounter(type, ctrl, loc, seq, count);
                break;
            }
            case Attack: {
                int aCtrl = buf.get() & 0xFF;
                int aLoc = buf.get() & 0xFF;
                int aSeq = buf.get() & 0xFF;
                buf.get(); // position
                int dCtrl = buf.get() & 0xFF;
                int dLoc = buf.get() & 0xFF;
                int dSeq = buf.get() & 0xFF;
                buf.get(); // position
                handler.onAttack(aCtrl, aLoc, aSeq, dCtrl, dLoc, dSeq);
                break;
            }
            case Battle: {
                buf.get(); buf.get(); buf.get(); buf.get(); // attacker ctrl/loc/seq/pos
                int atkAtk = buf.getInt();
                buf.getInt(); // attacker defense
                int atkPos = buf.get() & 0xFF;
                buf.get(); buf.get(); buf.get(); buf.get(); // defender ctrl/loc/seq/pos
                int defAtk = buf.getInt();
                buf.getInt(); // defender defense
                int defPos = buf.get() & 0xFF;
                handler.onBattle(atkAtk, (atkPos & 0x9) != 0, defAtk, (defPos & 0x9) != 0);
                break;
            }
            case MissedEffect: {
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                buf.get(); // position
                int code = buf.getInt();
                handler.onMissedEffect(code, ctrl, loc, seq, 0);
                break;
            }
            case AttackDisabled:
                handler.onAttackDisabled();
                break;
            case DamageStepStart:
                handler.onDamageStepStart();
                break;
            case DamageStepEnd:
                handler.onDamageStepEnd();
                break;
            case TossCoin: {
                int player = buf.get() & 0xFF;
                int count = buf.get() & 0xFF;
                handler.onTossCoin(player, count, buf);
                break;
            }
            case TossDice: {
                int player = buf.get() & 0xFF;
                int count = buf.get() & 0xFF;
                handler.onTossDice(player, count, buf);
                break;
            }
            case AnnounceRace: {
                int player = buf.get() & 0xFF;
                int count = buf.get() & 0xFF;
                int races = buf.getInt();
                handler.onAnnounceRace(player, count, races);
                break;
            }
            case AnnounceAttrib: {
                int player = buf.get() & 0xFF;
                int count = buf.get() & 0xFF;
                int attribs = buf.getInt();
                handler.onAnnounceAttrib(player, count, attribs);
                break;
            }
            case AnnounceCard:
                handler.onAnnounceCard(buf.get() & 0xFF, buf);
                break;
            case AnnounceNumber:
                handler.onAnnounceNumber(buf.get() & 0xFF, buf);
                break;
            case CardHint: {
                // duelclient.cpp MSG_CARD_HINT L4095-4100：c(1) l(1) s(1) 占位(1) chtype(1) value(4) 共 9 字节
                int chPlayer = buf.get() & 0xFF;
                int chLocation = buf.get() & 0xFF;
                int chSequence = buf.get() & 0xFF;
                buf.get(); // subseq：C++ 读取但忽略（GetCard 三参，超量素材不加提示）
                int chType = buf.get() & 0xFF;
                int chValue = buf.getInt();
                handler.onCardHint(chPlayer, chLocation, chSequence, chType, chValue);
                break;
            }
            case TagSwap:
                handler.onTagSwap(buf.get() & 0xFF);
                break;
            case ReloadField:
                handler.onReloadField(buf);
                break;
            case AiName: {
                int len = buf.getShort() & 0xFFFF;
                byte[] nameBytes = new byte[len];
                buf.get(nameBytes);
                handler.onAiName(new String(nameBytes, StandardCharsets.UTF_16LE));
                break;
            }
            case ShowHint: {
                int len = buf.getShort() & 0xFFFF;
                byte[] hintBytes = new byte[len];
                buf.get(hintBytes);
                handler.onShowHint(new String(hintBytes, StandardCharsets.UTF_16LE));
                break;
            }
            case MatchKill:
                handler.onMatchKill(buf.getInt());
                break;
            case CustomMsg: {
                int len = buf.getShort() & 0xFFFF;
                byte[] msgBytes = new byte[len];
                buf.get(msgBytes);
                handler.onCustomMsg(new String(msgBytes, StandardCharsets.UTF_16LE));
                break;
            }
            case DuelWinner:
                handler.onDuelWinner(buf.get() & 0xFF, buf.get() & 0xFF);
                break;
            default:
                Log.w(TAG, "Unhandled message: " + msg);
                break;
        }
    }

    private static void handleUpdate(GameMessage msg, ByteBuffer buf, MessageHandler handler) {
        int player = buf.get() & 0xFF;
        int location = buf.get() & 0xFF;
        if (msg == GameMessage.UpdateData) {
            handler.onUpdateData(player, location, buf);
        } else {
            int sequence = buf.get() & 0xFF;
            handler.onUpdateCard(player, location, sequence, buf);
        }
    }

    // === GameMessageParser.MessageHandler ===
    // 网络对局消息处理实现（自 GameEngine 合并，对齐 gframe duelclient.cpp ClientAnalyze）：
    // 共享状态经 engine 访问；场地事件类 handler 在 DuelEventHandler 实现并转发回本类方法。

    private final GameEngine engine;

    /** 网络对局解析器构造（GameEngine 装配）；回放等场景仍使用静态 parse(msg, buf, handler) */
    public GameMessageParser(GameEngine engine) {
        this.engine = engine;
    }

    public void onRetry() {
        if (engine.replayMode) return; // 回放：应答已录制在文件里，重放缓存消息无意义
        Log.w(TAG, "Retry message received");
        // 对齐 duelclient.cpp L1320-1404：无效应答后服务端只回 1 字节 MSG_RETRY，
        // 不重发原 SELECT（single_duel.cpp L583-591）——C++ 弹提示后重放缓存的上一条
        // 消息重建选择 UI 供玩家重新应答；此前仅打日志导致选择弹窗永久丢失、对局冻结
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onHintMessage("操作无效，请重新选择");
        });
        engine.replayLastGameMsg();
    }

    public void onHint(int type, int player, int data) {
        String hintText = "";
        switch (type) {
            // HINT_EVENT（对齐 duelclient.cpp L1445-1447）：静默写入 event_string=GetDesc(data)，不弹提示
            case 1:
                engine.field.eventString = DataManager.get().getDesc(data, "");
                return;
            case 2:
                hintText = "请选择";
                break;
            case 3:
                // HINT_SELECTMSG：保存下一条选择对话框标题的 sys 字符串索引，
                // 消费语义与 gframe select_hint 一致（duelclient.cpp L1458-1461）
                engine.field.selectHint = data;
                return;
            // HINT_OPSELECTED（对齐 duelclient.cpp L1463-1472）：记录"已选择"日志
            case 4:
                DuelLogDialog.addOpSelectedLog(data);
                return;
            case 5:
                hintText = "当前连锁: " + data;
                break;
            // HINT_RACE（对齐 duelclient.cpp L1480-1490）：宣告种族选择记入日志
            case 6:
                DuelLogDialog.addSelectedRaceLog(data);
                return;
            // HINT_ATTRIB（对齐 duelclient.cpp L1491-1501）：宣告属性选择记入日志
            case 7:
                DuelLogDialog.addSelectedAttributeLog(data);
                return;
            // HINT_CODE（对齐 duelclient.cpp L1502-1511）：宣言卡名记入日志（sys1511「玩家宣言了」），携带卡代码供点击查看
            case 8:
                DuelLogDialog.addLog(DuelLogDialog.formatDeclared(DataManager.get().getName(data)), data);
                return;
            // HINT_NUMBER（对齐 duelclient.cpp L1512-1521）：宣告数字记入日志
            case 9:
                DuelLogDialog.addLog(DuelLogDialog.sysFormat(1512, "已选择数字：%d", data));
                engine.soundManager.playSoundEffect(SoundManager.SFX.NEGATE);
                return;
            default:
                hintText = "Hint type=" + type + " data=" + data;
                break;
        }
        final String finalHint = hintText;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onHintMessage(finalHint);
        });
    }

    public void onWaiting() {
        if (engine.replayMode) return; // 回放：无真实等待方，启动等待轮换提示会悬挂
        Log.d(TAG, "Waiting...");
        // 对齐 duelclient.cpp L1610-1616 + game.cpp L1624-1633：waitFrame=0，显示"等待行动中..."并轮换
        engine.hintManager.startWaitHint();
    }

    public void onStart(int playerType, int duelRule, int lp0, int lp1, int deck0, int extra0, int deck1, int extra1) {
        engine.field.clear();
        engine.inDuel = true;
        engine.siding = false;
        engine.field.dInfo.duelRule = duelRule;
        // 回放视角恒以录制者为参照（ReplayPlayer.startSession 置恒等映射/切换视角翻转），
        // 不得由 MSG_START 的先攻方重新推断：undo/从头重放重投本消息时会把用户已切换的
        // 视角静默翻回（C++ gframe 也仅在 ReplayThread 开始设 isFirst=true，ReplaySwap 翻转）
        if (!engine.replayMode) {
            engine.duelIsFirst = (playerType & 1) == 0;
        }
        int p0 = engine.localPlayer(0);
        int p1 = engine.localPlayer(1);
        engine.playerInfos[p0].lp = lp0;
        engine.playerInfos[p1].lp = lp1;
        engine.playerInfos[p0].startLp = lp0;
        engine.playerInfos[p1].startLp = lp1;
        engine.field.players[p0].lp = lp0;
        engine.field.players[p1].lp = lp1;
        engine.field.dInfo.startLp = Math.max(lp0, lp1);
        engine.field.dInfo.lp[p0] = lp0;
        engine.field.dInfo.lp[p1] = lp1;
        // ClientField::Initial：为双方卡组/额外创建全部 ClientCard（背面朝下、带堆叠高度）
        engine.field.initial(p0, deck0, extra0, 0);
        engine.field.initial(p1, deck1, extra1, 0);
        // 回放（含带消息流录像的 MSG_START）不切 DUELING 状态：该状态会经
        // EngineCallbackDelegate 弹底部行动区/初始化时点按钮，回放左侧面板只应显示
        // 录像控制条（旧格式无 MSG_START 重跑天然不触发，带流文件经实况管线建场后必须同样拦截）
        if (!engine.replayMode) {
            engine.setState(GameEngine.GameState.DUELING);
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) {
                engine.listener.onFieldChanged();
                engine.listener.onPlayerInfoUpdated(0);
                engine.listener.onPlayerInfoUpdated(1);
            }
        });
    }

    public void onWin(int player, int reason) {
        // 回放结算由 ReplayPlayer 的 MSG_WIN → listener.onReplayFinished 路径负责，
        // 不走现网比分累计与结算弹窗
        if (engine.replayMode) return;
        // 场景 BGM 的胜负切换统一由 UI 层集中决策（对齐 game.cpp Game::playBGM）：
        // 经 onDuelResult 回调 → YGOProActivity.setBgmDuelResult/updateBGM 处理，此处不再直接切歌
        engine.currentMatch++;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onDuelResult(player, reason);
        });
    }

    public void onUpdateData(int player, int location, ByteBuffer data) {
        engine.dataParser.parseUpdateData(engine.localPlayer(player), location, data);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    public void onUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        engine.dataParser.parseUpdateCard(engine.localPlayer(player), location, sequence, data);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    public void onRequestDeck(int player) {
        if (engine.replayMode) return; // 回放：卡组公开由消息流自身保证，不进入 DECK_SELECT 状态
        engine.setState(GameEngine.GameState.DECK_SELECT);
    }

    public void onSelectBattleCmd(ByteBuffer data) {
        if (engine.replayMode) return; // 回放：SELECT 询问已录制应答，不弹选择窗不发提示
        engine.dataParser.parseBattleCmd(data);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(10, null);
        });
    }

    public void onSelectIdleCmd(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.dataParser.parseIdleCmd(data);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(11, null);
        });
    }

    public void onSelectEffectYn(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(12, data);
        });
    }

    public void onSelectYesNo(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(13, data);
        });
    }

    public void onSelectOption(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(14, data);
        });
    }

    public void onSelectCard(ByteBuffer data) {
        if (engine.replayMode) return;
        // 对齐 duelclient.cpp L1964-1974：非 panelmode 时 stHintMsg 显示"提示(min-max)"
        String hint = engine.hintManager.selectRangeHint(data, 560, "选择卡片");
        if (hint != null) engine.hintManager.postDuelHint(hint);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(15, data);
        });
    }

    public void onSelectChain(ByteBuffer data) {
        if (engine.replayMode) return;
        // 对齐 duelclient.cpp L2158-2163：存在"发动并作为连锁"项(EDESC_OPERATION=1)时用 556，否则 550
        boolean contiExist = false;
        try {
            ByteBuffer dup = data.duplicate();
            dup.order(ByteOrder.LITTLE_ENDIAN);
            dup.get(); // selecting_player
            int count = dup.get() & 0xFF;
            dup.get(); // specount
            dup.getInt(); // hint0
            dup.getInt(); // hint1
            for (int i = 0; i < count && dup.remaining() >= 14; i++) {
                int flag = dup.get() & 0xFF;
                dup.get(); // forced
                dup.getInt(); // code
                dup.position(dup.position() + 4); // c l s ss
                dup.getInt(); // desc
                if ((flag & 0x1) != 0) contiExist = true;
            }
        } catch (Exception ignored) {
        }
        engine.hintManager.postDuelHint(engine.hintManager.sysString(contiExist ? 556 : 550,
                contiExist ? "选择发动效果并作为连锁" : "选择发动效果"));
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(16, data);
        });
    }

    public void onSelectPlace(int player, int count, int fieldMask) {
        if (engine.replayMode) return;
        engine.clearCommandFlags();
        engine.selectFieldPlayer = player;
        engine.selectFieldCount = count;
        engine.selectFieldMask = ~fieldMask;
        // fieldMask 相对选择方：低 16 位 = 选择方自己的半场。
        // 只有选择方与我不同半场时才交换高低 16 位，保证低 16 位始终是我方半场（下半区）。
        // 用 localPlayer(与卡牌渲染同一套映射)判断"选择方是否为对方"，player&1 取边以兼容 tag(0/2 先攻,1/3 后攻)。
        if (engine.localPlayer(player & 1) == 1) {
            engine.selectFieldMask = (engine.selectFieldMask >>> 16) | (engine.selectFieldMask << 16);
        }
        // 对齐 duelclient.cpp L2199-2208：MSG_SELECT_PLACE 提示 sys569「请选择[%ls]的位置」（select_hint 此时是卡号）/ sys560
        if (engine.field.selectHint > 0) {
            engine.hintManager.postDuelHint(DataManager.get().formatSystemString(569, "请选择[%s]的位置",
                    DataManager.get().getName(engine.field.selectHint)));
        } else {
            engine.hintManager.postDuelHint(engine.hintManager.sysString(560, "请选择放置位置"));
        }
        engine.field.selectHint = 0;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(18, null);
        });
    }

    public void onSelectPosition(int player, int code, int positions) {
        if (engine.replayMode) return; // 单一形式时的自动应答分支也必须拦截，否则会向服务器发 Response
        positions &= 0x0F;
        // duelclient.cpp L2275-2278：仅一种表示形式可选时直接以该形式应答，不弹窗
        if (positions == 0x1 || positions == 0x2 || positions == 0x4 || positions == 0x8) {
            ByteBuffer resp = ByteBuffer.allocate(4);
            resp.order(ByteOrder.LITTLE_ENDIAN);
            resp.putInt(positions);
            engine.client.sendResponse(resp.array());
            return;
        }
        // 打包 code(4) + positions(4) 传给 UI 层：用于显示卡图与按位掩码显示形式按钮
        ByteBuffer data = ByteBuffer.allocate(8);
        data.order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(code);
        data.putInt(positions);
        data.flip();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(19, data);
        });
    }

    public void onSelectTribute(ByteBuffer data) {
        if (engine.replayMode) return;
        // 对齐 duelclient.cpp L2330-2335：stHintMsg 显示"提示(min-max)"（hint 优先 selectHint，默认 531）
        String hint = engine.hintManager.selectRangeHint(data, 531, "解放选择");
        if (hint != null) engine.hintManager.postDuelHint(hint);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(20, data);
        });
    }

    public void onSortChain(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(21, data);
        });
    }

    public void onSelectCounter(ByteBuffer data) {
        if (engine.replayMode) return;
        // 对齐 duelclient.cpp L2362-2365：stHintMsg 显示 GetSysString(204)（移除 N 个指示物）
        String hint = engine.hintManager.counterHint(data);
        if (hint != null) engine.hintManager.postDuelHint(hint);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(22, data);
        });
    }

    public void onSelectSum(ByteBuffer data) {
        if (engine.replayMode) return;
        // 对齐 client_field.cpp L1090-1115 ShowSelectSum：display_hint = GetDesc(select_hint) 或 GetSysString(560)
        int hint = engine.field.selectHint;
        engine.hintManager.postDuelHint(hint > 0 ? DataManager.get().getDesc(hint, "选择卡片") : engine.hintManager.sysString(560, "选择卡片"));
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(23, data);
        });
    }

    public void onSelectDisfield(int player, int count, int fieldMask) {
        if (engine.replayMode) return;
        engine.clearCommandFlags();
        engine.selectFieldPlayer = player;
        engine.selectFieldCount = count;
        engine.selectFieldMask = ~fieldMask;
        if (engine.localPlayer(player & 1) == 1) {
            engine.selectFieldMask = (engine.selectFieldMask >>> 16) | (engine.selectFieldMask << 16);
        }
        // 对齐 duelclient.cpp L2205-2210：MSG_SELECT_DISFIELD 提示 GetDesc(select_hint ?: 570)
        int hint = engine.field.selectHint > 0 ? engine.field.selectHint : 570;
        engine.hintManager.postDuelHint(DataManager.get().getDesc(hint, "请选择要禁用的区域"));
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(24, null);
        });
    }

    public void onSortCard(ByteBuffer data) {
        if (engine.replayMode) return;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(25, data);
        });
    }

    public void onSelectUnselectCard(ByteBuffer data) {
        if (engine.replayMode) return;
        // 对齐 duelclient.cpp L2053-2065：stHintMsg 显示"提示(min-max)"
        String hint = engine.hintManager.selectRangeHint(data, 560, "选择卡片");
        if (hint != null) engine.hintManager.postDuelHint(hint);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(26, data);
        });
    }
}
