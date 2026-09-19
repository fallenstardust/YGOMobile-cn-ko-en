package cn.garymb.ygomobile.network.server;

import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.network.YGOProtocol;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

/**
 * 卡组合法性校验，移植 {@code Classes/gframe/deck_manager.cpp::CheckDeck} 与
 * {@code checkAvail}。返回值为 gframe 风格的编码错误：{@code (DECKERROR_* << 28) | code}，
 * 0 表示合法。
 *
 * <p>禁限表按 {@code hostInfo.lflist} 哈希从 {@link LimitManager} 反查；哈希 0（N/A）或
 * 未找到时视为无禁限（返回 0），与 C++ {@code GetLFList} 返回空时的行为一致。
 */
final class DeckChecker implements YGOProtocol {

    private DeckChecker() {
    }

    private static final int AVAIL_OCG = 0x1;
    private static final int AVAIL_TCG = 0x2;
    private static final int AVAIL_SC = 0x4;
    private static final int AVAIL_CUSTOM = 0x8;
    private static final int AVAIL_OCGTCG = AVAIL_OCG | AVAIL_TCG;
    private static final int[] RULE_MAP = {AVAIL_OCG, AVAIL_TCG, AVAIL_SC, AVAIL_CUSTOM, AVAIL_OCGTCG, 0};

    private static final int DECK_MIN_SIZE = 40;
    private static final long TYPES_EXTRA_DECK = 0x40L | 0x2000L | 0x800000L | 0x4000000L;
    private static final long TYPE_TOKEN = 0x4000L;

    static int checkDeck(PlayerDeck deck, int lflist, int rule) {
        if (deck.main.size() < DECK_MIN_SIZE || deck.main.size() > Constants.DECK_MAIN_MAX) {
            return (DECKERROR_MAINCOUNT << 28) | deck.main.size();
        }
        if (deck.extra.size() > Constants.DECK_EXTRA_MAX) {
            return (DECKERROR_EXTRACOUNT << 28) | deck.extra.size();
        }
        if (deck.side.size() > Constants.DECK_SIDE_MAX) {
            return (DECKERROR_SIDECOUNT << 28) | deck.side.size();
        }
        LimitList lf = resolveLimitList(lflist);
        int avail = (rule >= 0 && rule < RULE_MAP.length) ? RULE_MAP[rule] : 0;
        Map<Integer, Integer> ccount = new HashMap<>();

        for (int code : deck.main) {
            int err = checkCard(code, avail, TYPES_EXTRA_DECK | TYPE_TOKEN, true, ccount, lf);
            if (err != 0) {
                return err;
            }
        }
        for (int code : deck.extra) {
            int err = checkCard(code, avail, TYPE_TOKEN, false, ccount, lf);
            if (err != 0) {
                return err;
            }
        }
        for (int code : deck.side) {
            int err = checkSideCard(code, avail, ccount, lf);
            if (err != 0) {
                return err;
            }
        }
        return 0;
    }

    private static int checkCard(int code, int avail, long forbidTypeMask, boolean mustNotBeExtra,
                                 Map<Integer, Integer> ccount, LimitList lf) {
        Card card = DataManager.get().getCardManager().getCard(code);
        if (card == null || card.Code == 0) {
            return (DECKERROR_UNKNOWNCARD << 28) | code;
        }
        int ga = checkAvail(card.Ot, avail);
        if (ga != 0) {
            return (ga << 28) | code;
        }
        if (mustNotBeExtra) {
            if ((card.Type & (TYPES_EXTRA_DECK | TYPE_TOKEN)) != 0) {
                return DECKERROR_MAINCOUNT << 28;
            }
        } else {
            if ((card.Type & TYPES_EXTRA_DECK) == 0 || (card.Type & TYPE_TOKEN) != 0) {
                return DECKERROR_EXTRACOUNT << 28;
            }
        }
        return checkCount(card, ccount, lf, code);
    }

    private static int checkSideCard(int code, int avail, Map<Integer, Integer> ccount, LimitList lf) {
        Card card = DataManager.get().getCardManager().getCard(code);
        if (card == null || card.Code == 0) {
            return (DECKERROR_UNKNOWNCARD << 28) | code;
        }
        int ga = checkAvail(card.Ot, avail);
        if (ga != 0) {
            return (ga << 28) | code;
        }
        if ((card.Type & TYPE_TOKEN) != 0) {
            return DECKERROR_SIDECOUNT << 28;
        }
        return checkCount(card, ccount, lf, code);
    }

    private static int checkCount(Card card, Map<Integer, Integer> ccount, LimitList lf, int code) {
        int ruleCode = card.getGameCode();
        int dc = ccount.merge(ruleCode, 1, Integer::sum);
        if (dc > Constants.CARD_MAX_COUNT) {
            return (DECKERROR_CARDCOUNT << 28) | code;
        }
        if (lf != null) {
            int allow = maxAllowed(card, lf);
            if (dc > allow) {
                return (DECKERROR_LFLIST << 28) | code;
            }
        }
        return 0;
    }

    private static int maxAllowed(Card card, LimitList lf) {
        if (lf.check(card, LimitType.Forbidden)) {
            return 0;
        }
        if (lf.check(card, LimitType.Limit)) {
            return 1;
        }
        if (lf.check(card, LimitType.SemiLimit)) {
            return 2;
        }
        return Constants.CARD_MAX_COUNT;
    }

    private static int checkAvail(int ot, int avail) {
        if ((ot & avail) == avail) {
            return 0;
        }
        if ((ot & AVAIL_OCG) != 0 && avail != AVAIL_OCG) {
            return DECKERROR_OCGONLY;
        }
        if ((ot & AVAIL_TCG) != 0 && avail != AVAIL_TCG) {
            return DECKERROR_TCGONLY;
        }
        return DECKERROR_NOTAVAIL;
    }

    /** 按 lflist 哈希解析禁限表；0/未找到返回 null（表示无禁限）。 */
    private static LimitList resolveLimitList(int lflist) {
        if (lflist == 0) {
            return null;
        }
        LimitManager lm = DataManager.get().getLimitManager();
        if (lm == null) {
            return null;
        }
        String name = lm.getLimitNameByHash(lflist);
        if (name == null) {
            name = lm.getGenesysLimitNameByHash(lflist);
            return name == null ? null : lm.getGenesysLimit(name);
        }
        LimitList list = lm.getLimit(name);
        // N/A（无禁限）哈希为 0，不会到这里；内容为空的表按无禁限处理
        if (list != null && list.getCodeList().isEmpty()) {
            return null;
        }
        return list;
    }
}
