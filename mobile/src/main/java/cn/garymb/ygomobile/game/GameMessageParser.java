package cn.garymb.ygomobile.game;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.network.YGOProtocol;
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
        void onStart(int lp, int startHand, int drawCount);
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
        void onConfirmDecktop(int player, int count, ByteBuffer data);
        void onConfirmCards(int player, int count, ByteBuffer data);
        void onShuffleDeck(int player);
        void onShuffleHand(int player);
        void onRefreshDeck(int player);
        void onSwapGraveDeck(int player);
        void onShuffleSetCard(int player, int count, ByteBuffer data);
        void onReverseDeck(int player);
        void onDeckTop(int player, int code);
        void onNewTurn(int player);
        void onNewPhase(int phase);
        void onMove(int code, int oldControler, int oldLocation, int oldSequence,
                    int newControler, int newLocation, int newSequence, int position, int reason);
        void onPosChange(int code, int controler, int location, int sequence,
                         int oldPos, int newPos);
        void onSet(int code, int controler, int location, int sequence);
        void onSwap(int c1_ctrl, int c1_loc, int c1_seq, int c2_ctrl, int c2_loc, int c2_seq);
        void onFieldDisabled(int controler, int location, int sequence);
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
        void onDraw(int player, int count);
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
        void onCardHint(int type, int data);
        void onTagSwap(int player);
        void onReloadField();
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
                int lp = buf.getInt();
                int startHand = buf.get() & 0xFF;
                int drawCount = buf.get() & 0xFF;
                handler.onStart(lp, startHand, drawCount);
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
            case SelectTribute:
                handler.onSelectTribute(buf);
                break;
            case SelectSum:
                handler.onSelectSum(buf);
                break;
            case SelectCounter:
                handler.onSelectCounter(buf);
                break;
            case SortChain:
                handler.onSortChain(buf);
                break;
            case SortCard:
                handler.onSortCard(buf);
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
                int player = buf.get() & 0xFF;
                int count = buf.get() & 0xFF;
                handler.onConfirmCards(player, count, buf);
                break;
            }
            case ShuffleDeck:
                handler.onShuffleDeck(buf.get() & 0xFF);
                break;
            case ShuffleHand:
                handler.onShuffleHand(buf.get() & 0xFF);
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
                buf.get(); // old position
                int newCtrl = buf.get() & 0xFF;
                int newLoc = buf.get() & 0xFF;
                int newSeq = buf.get() & 0xFF;
                int pos = buf.get() & 0xFF;
                int reason = buf.getInt();
                handler.onMove(code, oldCtrl, oldLoc, oldSeq, newCtrl, newLoc, newSeq, pos, reason);
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
                handler.onSet(code, ctrl, loc, seq);
                break;
            }
            case Swap: {
                int c1code = buf.getInt();
                int c1ctrl = buf.get() & 0xFF;
                int c1loc = buf.get() & 0xFF;
                int c1seq = buf.get() & 0xFF;
                int c2code = buf.getInt();
                int c2ctrl = buf.get() & 0xFF;
                int c2loc = buf.get() & 0xFF;
                int c2seq = buf.get() & 0xFF;
                handler.onSwap(c1ctrl, c1loc, c1seq, c2ctrl, c2loc, c2seq);
                break;
            }
            case FieldDisabled: {
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                handler.onFieldDisabled(ctrl, loc, seq);
                break;
            }
            case Summoning: {
                int code = buf.getInt();
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                handler.onSummoning(code, ctrl, loc, seq);
                break;
            }
            case Summoned:
                handler.onSummoned();
                break;
            case SpSummoning: {
                int code = buf.getInt();
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                handler.onSpSummoning(code, ctrl, loc, seq);
                break;
            }
            case SpSummoned:
                handler.onSpSummoned();
                break;
            case FlipSummoning: {
                int code = buf.getInt();
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
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
            case Draw: {
                int player = buf.get() & 0xFF;
                int count = buf.get() & 0xFF;
                handler.onDraw(player, count);
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
                int eqCode = buf.getInt();
                int eqCtrl = buf.get() & 0xFF;
                int eqLoc = buf.get() & 0xFF;
                int eqSeq = buf.get() & 0xFF;
                int tCtrl = buf.get() & 0xFF;
                int tLoc = buf.get() & 0xFF;
                int tSeq = buf.get() & 0xFF;
                handler.onEquip(eqCode, eqCtrl, eqLoc, eqSeq, tCtrl, tLoc, tSeq);
                break;
            }
            case LpUpdate: {
                int player = buf.get() & 0xFF;
                int lp = buf.getInt();
                handler.onLpUpdate(player, lp);
                break;
            }
            case Unequip: {
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                handler.onUnequip(ctrl, loc, seq);
                break;
            }
            case CardTarget:
            case CancelTarget: {
                int c1ctrl = buf.get() & 0xFF;
                int c1loc = buf.get() & 0xFF;
                int c1seq = buf.get() & 0xFF;
                int c2ctrl = buf.get() & 0xFF;
                int c2loc = buf.get() & 0xFF;
                int c2seq = buf.get() & 0xFF;
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
                int dCtrl = buf.get() & 0xFF;
                int dLoc = buf.get() & 0xFF;
                int dSeq = buf.get() & 0xFF;
                handler.onAttack(aCtrl, aLoc, aSeq, dCtrl, dLoc, dSeq);
                break;
            }
            case Battle: {
                int atkAtk = buf.getInt();
                boolean atkPos = (buf.get() & 0xFF) != 0;
                int defAtk = buf.getInt();
                boolean defPos = (buf.get() & 0xFF) != 0;
                handler.onBattle(atkAtk, atkPos, defAtk, defPos);
                break;
            }
            case AttackDiabled:
                handler.onAttackDisabled();
                break;
            case DamageStepStart:
                handler.onDamageStepStart();
                break;
            case DamageStepEnd:
                handler.onDamageStepEnd();
                break;
            case MissedEffect: {
                int code = buf.getInt();
                int ctrl = buf.get() & 0xFF;
                int loc = buf.get() & 0xFF;
                int seq = buf.get() & 0xFF;
                int effectId = buf.getInt();
                handler.onMissedEffect(code, ctrl, loc, seq, effectId);
                break;
            }
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
                int type = buf.get() & 0xFF;
                int data = buf.getInt();
                handler.onCardHint(type, data);
                break;
            }
            case TagSwap:
                handler.onTagSwap(buf.get() & 0xFF);
                break;
            case ReloadField:
                handler.onReloadField();
                break;
            case AiName: {
                int len = buf.getShort() & 0xFFFF;
                byte[] nameBytes = new byte[len];
                buf.get(nameBytes);
                handler.onAiName(new String(nameBytes, java.nio.charset.StandardCharsets.UTF_16LE));
                break;
            }
            case ShowHint: {
                int len = buf.getShort() & 0xFFFF;
                byte[] hintBytes = new byte[len];
                buf.get(hintBytes);
                handler.onShowHint(new String(hintBytes, java.nio.charset.StandardCharsets.UTF_16LE));
                break;
            }
            case MatchKill:
                handler.onMatchKill(buf.getInt());
                break;
            case CustomMsg: {
                int len = buf.getShort() & 0xFFFF;
                byte[] msgBytes = new byte[len];
                buf.get(msgBytes);
                handler.onCustomMsg(new String(msgBytes, java.nio.charset.StandardCharsets.UTF_16LE));
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
}
