package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import ocgcore.enums.CardLocation;
import ocgcore.enums.CardType;

/**
 * === Data parsing helpers ===
 * 自 GameEngine 拆分而来：MSG_UPDATE_DATA / MSG_UPDATE_CARD / MSG_SELECT_BATTLE_CMD /
 * MSG_SELECT_IDLE_CMD 的缓冲解析。命令状态数据（卡片列表、cmdFlag、show* 与 selectField*）
 * 为多方共享状态，保留在 GameEngine 门面上，本类经 engine 读写。
 */
public class CommandDataParser {

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
        List<GameField.ClientCard> list = engine.field.players[player].getLocationList(location);
        if (list == null) return;
        boolean fixedSlots = (location == 0x04 || location == 0x08);
        for (int i = 0; i < list.size(); i++) {
            GameField.ClientCard card = list.get(i);
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
     * MSG_UPDATE_CARD（client_field.cpp UpdateCard 真值格式）：
     * int32 len 前缀，len>8 时才解析 query（对现有卡对象更新，绝不整卡替换）
     */
    public void parseUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        if (data.remaining() < 4) return;
        int len = data.getInt();
        if (len <= 8) return;
        GameField.ClientCard card = engine.field.getCard(player, location, sequence);
        if (card == null) {
            card = new GameField.ClientCard();
            card.controler = player;
            card.location = location;
            card.sequence = sequence;
            engine.field.addCard(player, location, sequence, card);
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
            GameField.ClientCard card = engine.field.getCard(lpl, loc, seq);
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
            }
        }
        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int diratt = data.get() & 0xFF;
            GameField.ClientCard card = engine.field.getCard(engine.localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= GameEngine.COMMAND_ATTACK;
                engine.attackableCards.add(new GameEngine.CmdCardInfo(card, code, 0, 0, i));
            }
        }
        engine.showM2 = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        engine.showEP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
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
            GameField.ClientCard card = engine.field.getCard(engine.localPlayer(con & 1), loc, seq);
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
            GameField.ClientCard card = engine.field.getCard(lpl, loc, seq);
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
            GameField.ClientCard card = engine.field.getCard(engine.localPlayer(con & 1), loc, seq);
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
            GameField.ClientCard card = engine.field.getCard(engine.localPlayer(con & 1), loc, seq);
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
            GameField.ClientCard card = engine.field.getCard(engine.localPlayer(con & 1), loc, seq);
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
            GameField.ClientCard card = engine.field.getCard(lpl, loc, seq);
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
            }
        }

        engine.showBP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        engine.showEP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        engine.showShuffle = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
    }
}
