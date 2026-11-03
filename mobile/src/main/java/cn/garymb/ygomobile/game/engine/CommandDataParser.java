package cn.garymb.ygomobile.game.engine;
import android.util.Log;

import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.EffectActivatableHints;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.render.card.CardDetailPanel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import ocgcore.enums.CardLocation;
import ocgcore.enums.CardPosition;
import ocgcore.enums.CardType;

/**
 * === Data parsing helpers ===
 * 自 GameEngine 拆分而来：MSG_UPDATE_DATA / MSG_UPDATE_CARD / MSG_SELECT_BATTLE_CMD /
 * MSG_SELECT_IDLE_CMD 的缓冲解析。命令状态数据（卡片列表、cmdFlag、show* 与 selectField*）
 * 为多方共享状态，保留在 GameEngine 门面上，本类经 engine 读写。
 */
public class CommandDataParser {

    // 保持拆分前日志标识，便于与旧版日志比对
    private static final String TAG = "GameEngine";

    private final GameEngine engine;

    public CommandDataParser(GameEngine engine) {
        this.engine = engine;
    }

    /**
     * MSG_UPDATE_DATA（client_field.cpp UpdateFieldCard真值格式）：
     * 无 count 字段，按列表条目遍历；每条先读 int32 len（含自身 4 字节），
     * len>8 才有 query 数据（query 内首个 int32 是 flag），随后跳到 len-4 处。
     * 固定槽位列表（怪兽区/魔法区）空位也有len=4 条目；动态列表只发实际卡。
     */
    public void parseUpdateData(int player, int location, ByteBuffer data) {
        List<ClientCard> list = engine.field.players[player].getLocationList(location);
        if (list == null) return;
        boolean fixedSlots = (location == 0x04 || location == 0x08);
        if (location == CardLocation.Hand.value()) {
            // 手卡区先把张数对平再按位写入（观战补建占位卡与真实卡混排时不分先后必错位）
            reconcileHandCount(player, list, data);
        }
        for (int i = 0; i < list.size(); i++) {
            ClientCard card = list.get(i);
            if (card == null && !fixedSlots) continue;
            if (data.remaining() < 4) break;
            int len = data.getInt();
            int next = data.position() + (len - 4);
            if (next < data.position() || next > data.limit()) break;
            if (len > 8 && card != null) {
                ByteBuffer sub = data.slice().order(ByteOrder.LITTLE_ENDIAN);
                sub.limit(Math.min(sub.limit(), len - 4));
                card.updateQuery(sub);
            }
            data.position(next);
        }
    }

    /**
     * 手卡区的张数对平：MSG_UPDATE_DATA(LOCATION_HAND) 载荷里每个非空槽一个 len 前缀块，
     * <b>块数就是服务端此刻该席位的手卡张数</b>（引擎按区域逐卡查询，遮蔽只清块内容不减块数），
     * 所以它是本端唯一可用的手卡权威值。
     * <p>
     * 为何必须对平：观战端手卡是按 MSG_START 起手数补建的背面占位卡，占位数与真实张数
     * 只要差一张（房间配置与引擎实际起手不同、遮蔽版 MSG_MOVE 来源序号在本端落空而
     * 伪造卡片送墓、发动后离场的卡与占位卡彼此错位），本端手卡列表就长期多出一张；而下面
     * 那个循环是“逐块对非空槽按位写入”，于是那张多出的占位卡会接到本应写给别人的卡码，
     * 表现为用户反馈的「从手卡发动效果后那张卡一直留在手卡并保持公开」——真正该走的那张卡
     * 反而被当成没走。这里先把张数拉平：多了就从尾部摘掉仍是背面（code==0）的卡，
     * 少了就补背面占位，再把块按位写下去，码自然落在对的卡上。
     * <p>
     * 决斗位自己的手卡张数本来就与块数一致，本对平不产生任何增删；带真实卡码的卡也不强摘，
     * 避免把已揭示的卡抹成不存在（宁可多显一张，不可谎报少一张）。
     */
    private void reconcileHandCount(int player, List<ClientCard> hand, ByteBuffer data) {
        int blocks = 0;
        ByteBuffer scan = data.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        while (scan.remaining() >= 4) {
            int len = scan.getInt();
            int next = scan.position() + (len - 4);
            if (len < 4 || next < scan.position() || next > scan.limit()) break;
            scan.position(next);
            blocks++;
        }
        if (blocks <= 0) return;
        int owned = engine.field.getCardCount(player, CardLocation.Hand.value());
        boolean changed = false;
        while (owned > blocks) {
            int victim = -1;
            for (int i = hand.size() - 1; i >= 0; i--) {
                ClientCard c = hand.get(i);
                if (c != null && c.code == 0) { victim = i; break; }
            }
            if (victim < 0) break;
            hand.set(victim, null);
            owned--;
            changed = true;
        }
        while (owned < blocks) {
            int slot = -1;
            for (int i = 0; i < hand.size(); i++) {
                if (hand.get(i) == null) { slot = i; break; }
            }
            if (slot < 0) {
                if (hand.size() >= GameField.MAX_HAND) break;//已到上限不再撑长
                hand.add(null);
                slot = hand.size() - 1;
            }
            ClientCard c = new ClientCard();
            c.owner = player;
            c.controler = player;
            c.location = CardLocation.Hand.value();
            c.position = CardPosition.FaceDownDefence.value();
            hand.set(slot, c);
            owned++;
            changed = true;
        }
        if (!changed) return;
        // 序号与列表下标对齐（getCard 与后续 MSG_MOVE 的来源序号都依赖它），并按新张数重排整行
        engine.field.resetSequence(hand, false);
        engine.field.updateHandLayout(player, 10);
    }

    /**
     * MSG_UPDATE_CARD（client_field.cpp UpdateCard 真值格式）：
     * int32 len 前缀，len>8 时才解析 query（对现有卡对象更新，绝不整卡替换）。
     * <p>卡对象不存在时按 C++（client_field.cpp UpdateCard L300-306 的 {@code if (pcard && ...)}）
     * 直接跳过：本方法只在既有卡上补写查询字段，从不建卡。旧写法在此 {@code new ClientCard()} 并
     * {@code addCard} 落位，会在观战席位（中途接入、没有对方开局发牌）的除外区/场上凭空立出
     * 一张卡码为 0、点不动也移不走的幽灵卡。
     */
    public void parseUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        if (data.remaining() < 4) return;
        int len = data.getInt();
        if (len <= 8) return;
        ClientCard card = engine.field.getCard(player, location, sequence);
        if (card == null) {
            Log.d(TAG, "update card skipped: no such card at " + player + "/0x"
                    + Integer.toHexString(location) + "/" + sequence);
            return;
        }
        ByteBuffer sub = data.slice().order(ByteOrder.LITTLE_ENDIAN);
        sub.limit(Math.min(sub.limit(), len - 4));
        card.updateQuery(sub);
    }

    public void parseBattleCmd(ByteBuffer data) {
        engine.clearCommandFlags();
        engine.resetFieldCommandHints();
        int selectingPlayer = data.get() & 0xFF;
        int count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 9; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int desc = data.getInt();
            int flag = 0;
            if ((code & 0x80000000) != 0) {
                flag = 1;
                code &= 0x7fffffff;
            }
            int lpl = engine.localPlayer(con & 1);
            ClientCard card = engine.field.getCard(lpl, loc, seq);
            if (card != null) {
                // 对齐 duelclient.cpp L1704-1716：EDESC_OPERATION → conti_act（回合结束待结算），
                // 否则 COMMAND_ACTIVATE 并按所在区域置墓地/除外/额外发动提示
                if (flag != 0) {
                    card.chain_code = code;
                    engine.field.contiCards.add(card);
                    engine.field.contiAct = true;
                } else {
                    card.cmdFlag |= GameEngine.COMMAND_ACTIVATE;
                    if (card.location == 0x10) {
                        engine.field.graveAct[lpl] = true;
                    } else if (card.location == 0x20) {
                        engine.field.removeAct[lpl] = true;
                    } else if (card.location == 0x40) {
                        engine.field.extraAct[lpl] = true;
                    }
                }
                engine.activatableCards.add(new GameEngine.CmdCardInfo(card, code, desc, flag, i));
                registerCommandEffect(card, code, desc, flag);
            }
        }
        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int diratt = data.get() & 0xFF;
            ClientCard card = engine.field.getCard(engine.localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= GameEngine.COMMAND_ATTACK;
                engine.attackableCards.add(new GameEngine.CmdCardInfo(card, code, 0, 0, i));
            }
        }
        engine.showM2 = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        engine.showEP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        // 用户新规格3：战斗指令登记完成，卡详正展示相关卡时即时刷新可发动背景呼吸段
        //（与开段 clearCommandFlags 的刷新经 40ms 防抖合并，不会以中间态重绑）
        CardDetailPanel.refreshCardDetailMarks();
    }

    public void parseIdleCmd(ByteBuffer data) {
        engine.clearCommandFlags();
        engine.resetFieldCommandHints();
        int selectingPlayer = data.get() & 0xFF;
        int count;

        // 第1段 summonable_cards（duelclient.cpp L1750-1760）：仅置 COMMAND_SUMMON 并入 summonableCards，
        // 应答编码 i<<16（event_handler.cpp BUTTON_CMD_SUMMON L595-605）。
        // 修复：此前误按特召段解析（置 COMMAND_SPSUMMON + 入 spsummonableCards），
        // 导致手卡可通常召唤的怪兽只显示「特殊召唤」且应答 op=1 越出 spsummon_list 而无效果。
        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            ClientCard card = engine.field.getCard(engine.localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= GameEngine.COMMAND_SUMMON;
                engine.summonableCards.add(new GameEngine.CmdCardInfo(card, code, 0, 0, i));
            }
        }

        // 第2段 spsummonable_cards（duelclient.cpp L1761-1785）：COMMAND_SPSUMMON + 按区域置发动提示，
        // 卡组项需补明卡码（对应 pcard->SetCode(code)），应答编码 (i<<16)+1。
        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int lpl = engine.localPlayer(con & 1);
            ClientCard card = engine.field.getCard(lpl, loc, seq);
            if (card != null) {
                card.cmdFlag |= GameEngine.COMMAND_SPSUMMON;
                if (card.location == CardLocation.Deck.value()) {
                    card.code = code;
                    engine.field.deckAct[lpl] = true;
                } else if (card.location == CardLocation.Grave.value()) {
                    engine.field.graveAct[lpl] = true;
                } else if (card.location == CardLocation.Removed.value()) {
                    engine.field.removeAct[lpl] = true;
                } else if (card.location == CardLocation.Extra.value()) {
                    engine.field.extraAct[lpl] = true;
                } else {
                    // duelclient.cpp L1780-1784：灵摆区（duel_rule>=4 为 seq0，否则 seq6）且未被装备占用
                    int leftSeq = engine.field.dInfo.duelRule >= 4 ? 0 : 6;
                    if (card.location == CardLocation.SpellZone.value() && card.sequence == leftSeq
                            && (card.type & CardType.Pendulum.getId()) != 0 && card.equipTarget == null) {
                        engine.field.pzoneAct[lpl] = true;
                    }
                }
                engine.spsummonableCards.add(new GameEngine.CmdCardInfo(card, code, 0, 0, i));
            }
        }

        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            ClientCard card = engine.field.getCard(engine.localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= GameEngine.COMMAND_REPOS;
                engine.reposableCards.add(new GameEngine.CmdCardInfo(card, code, 0, 0, i));
            }
        }

        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            ClientCard card = engine.field.getCard(engine.localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= GameEngine.COMMAND_MSET;
                engine.msetableCards.add(new GameEngine.CmdCardInfo(card, code, 0, 0, i));
            }
        }

        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            ClientCard card = engine.field.getCard(engine.localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= GameEngine.COMMAND_SSET;
                engine.ssetableCards.add(new GameEngine.CmdCardInfo(card, code, 0, 0, i));
            }
        }

        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 11; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int desc = data.getInt();
            int flag = 0;
            if ((code & 0x80000000) != 0) {
                flag = 1;
                code &= 0x7fffffff;
            }
            int lpl = engine.localPlayer(con & 1);
            ClientCard card = engine.field.getCard(lpl, loc, seq);
            if (card != null) {
                // 对齐 duelclient.cpp L1837-1849：EDESC_OPERATION → conti_act，否则按区域置提示
                if (flag != 0) {
                    card.chain_code = code;
                    engine.field.contiCards.add(card);
                    engine.field.contiAct = true;
                } else {
                    card.cmdFlag |= GameEngine.COMMAND_ACTIVATE;
                    if (card.location == 0x10) {
                        engine.field.graveAct[lpl] = true;
                    } else if (card.location == 0x20) {
                        engine.field.removeAct[lpl] = true;
                    } else if (card.location == 0x40) {
                        engine.field.extraAct[lpl] = true;
                    }
                }
                engine.activatableCards.add(new GameEngine.CmdCardInfo(card, code, desc, flag, i));
                registerCommandEffect(card, code, desc, flag);
            }
        }

        engine.showBP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        engine.showEP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        engine.showShuffle = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        // 用户新规格3：主阶指令登记完成，卡详正展示相关卡时即时刷新可发动背景呼吸段
        CardDetailPanel.refreshCardDetailMarks();
    }

    /** 可发动选项入列（即卡片出现呼吸绿点/conti_act 动画的同一时刻）登记效果段落序号：
     *  判据与 CardOverlayRenderer 角位呼吸点同源（COMMAND_ACTIVATE 置位或 conti 项），点击带点
     *  的卡即由卡详对可发动效果段落做背景色呼吸，无需等命令菜单出现「发动」按钮；
     *  conti 项（flag=EDESC_OPERATION）报文不 masking 卡码，用置入卡上的 chain_code 对齐卡详码 */
    private static void registerCommandEffect(ClientCard card, int maskedCode, int desc, int flag) {
        if ((flag & 0x1) != 0) {
            EffectActivatableHints.register(card.chain_code, card.controler == 1 ? 1 : 0, desc);
        } else {
            EffectActivatableHints.register(maskedCode, card.controler == 1 ? 1 : 0, desc);
        }
    }
}
