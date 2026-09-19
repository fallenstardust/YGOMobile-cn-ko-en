package cn.garymb.ygomobile.network.server;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import ocgcore.DataManager;
import ocgcore.data.Card;

/**
 * 服务器侧一副已解析卡组，等价 {@code Classes/gframe/deck.h} 的 {@code Deck}（main/extra/side）。
 *
 * <p>载入逻辑移植 {@code deck_manager.cpp::LoadDeck}：客户端 {@code CTOS_UPDATE_DECK} 的
 * {@code mainc} 已是主卡组 + 额外卡组的合计，逐项按卡片类型是否为额外卡组拆分到
 * {@link #main}/{@link #extra}，{@code sidec} 项进 {@link #side}；未知卡或衍生物卡记为错误码并跳过。
 */
final class PlayerDeck {

    final List<Integer> main = new ArrayList<>();
    final List<Integer> extra = new ArrayList<>();
    final List<Integer> side = new ArrayList<>();

    private static final long TYPE_TOKEN = 0x4000L;

    /** 清空并载入，返回首个未知/衍生物卡代码（0 表示无错误），对齐 LoadDeck 的 errorcode。 */
    int load(int[] buf, int mainc, int sidec) {
        main.clear();
        extra.clear();
        side.clear();
        int errorcode = 0;
        for (int i = 0; i < mainc; i++) {
            int code = buf[i];
            Card card = DataManager.get().getCardManager().getCard(code);
            if (card == null || card.Code == 0) {
                errorcode = code;
                continue;
            }
            if ((card.Type & TYPE_TOKEN) != 0) {
                errorcode = code;
                continue;
            }
            if (card.isExtraCard()) {
                if (extra.size() < Constants.DECK_EXTRA_MAX) {
                    extra.add(code);
                }
            } else {
                if (main.size() < Constants.DECK_MAIN_MAX) {
                    main.add(code);
                }
            }
        }
        for (int i = 0; i < sidec; i++) {
            int code = buf[mainc + i];
            Card card = DataManager.get().getCardManager().getCard(code);
            if (card == null || card.Code == 0) {
                errorcode = code;
                continue;
            }
            if ((card.Type & TYPE_TOKEN) != 0) {
                errorcode = code;
                continue;
            }
            if (side.size() < Constants.DECK_SIDE_MAX) {
                side.add(code);
            }
        }
        return errorcode;
    }

    /** match 换边校验：新卡组主/额外/副数量须与原卡组一致（对齐 LoadSide 的数量一致判断）。 */
    boolean canSwapTo(PlayerDeck old) {
        return main.size() == old.main.size()
                && extra.size() == old.extra.size()
                && side.size() == old.side.size();
    }

    int mainCount() {
        return main.size();
    }

    int extraCount() {
        return extra.size();
    }

    int sideCount() {
        return side.size();
    }

    /**
     * 生成 STOC_DECK_COUNT（12 字节 int16[6]）。{@code which==0} 输出 [m0,e0,s0,m1,e1,s1]，
     * {@code which==1} 交换前后半使己方卡组在前（对齐 single_duel.cpp::StartDuel）。
     */
    static byte[] deckCountPayload(PlayerDeck d0, PlayerDeck d1, int which) {
        int[] vals;
        if (which == 0) {
            vals = new int[]{d0.mainCount(), d0.extraCount(), d0.sideCount(),
                    d1.mainCount(), d1.extraCount(), d1.sideCount()};
        } else {
            vals = new int[]{d1.mainCount(), d1.extraCount(), d1.sideCount(),
                    d0.mainCount(), d0.extraCount(), d0.sideCount()};
        }
        ByteBuffer b = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        for (int v : vals) {
            b.putShort((short) v);
        }
        return b.array();
    }
}
