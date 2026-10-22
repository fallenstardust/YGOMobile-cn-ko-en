package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import ocgcore.enums.CardLocation;

/**
 * MSG_TAG_SWAP（tag 换人）三区重建实现（自 DuelEventHandler 平移，逻辑零改；
 * 对齐 duelclient.cpp L4175-4287）。
 */
class TagSwapApplier {

    private final GameEngine engine;

    TagSwapApplier(GameEngine engine) {
        this.engine = engine;
    }

    void apply(ByteBuffer data) {
        // 对齐 duelclient.cpp MSG_TAG_SWAP L4175-4287：tag 换人核心——服务端下发新行动选手的
        // 卡组/额外/手卡数量与手卡/额外卡码，客户端重建该侧三个堆区并重播离场-归位动画
        //（C++ 两段 MoveCard(5)：先把现存卡甩向场外，重建后从场内前方/后方偏移飞回）；
        // 旧实现只 invalidate 不重建列表，导致对手换队友后手卡矩形不显示
        if (data == null || data.remaining() < 9) return;
        data.order(ByteOrder.LITTLE_ENDIAN);
        final int p = engine.localPlayer(data.get() & 0xFF);
        final int mcount = data.get() & 0xFF;
        final int ecount = data.get() & 0xFF;
        final int pcount = data.get() & 0xFF;
        final int hcount = data.get() & 0xFF;
        final int topcode = data.getInt();
        // C++ 读序：topcode 后依次 hcount 个手卡码、ecount 个额外码（&0x7fffffff 去隐藏位）
        final int[] handCodes = new int[hcount];
        for (int i = 0; i < hcount && data.remaining() >= 4; i++) handCodes[i] = data.getInt();
        final int[] extraCodes = new int[ecount];
        for (int i = 0; i < ecount && data.remaining() >= 4; i++) extraCodes[i] = data.getInt() & 0x7fffffff;
        final GameField field = engine.field;
        // 回放快进：同步即时落位（无飞入动画、不持闸），与 onDraw 的 instantPlace 分支同构
        if (field.instantPlace) {
            applyTagSwapPiles(field, p, mcount, ecount, pcount, handCodes, extraCodes, topcode);
            engine.mainHandler.post(() -> {
                if (engine.listener != null) {
                    engine.listener.onFieldChanged();
                    engine.listener.onPlayerInfoUpdated(p);
                }
            });
            return;
        }
        // 第一段（C++ L4182-4205）：现存 deck/hand/extra 卡标记移动目标并持闸 5 帧（甩离）；
        // 第二段（L4209-4286）：重建数量/卡码后从 Y 偏移处 MoveCard(5) 飞回。Java 以
        // delay=5 排布飞回动画等价两段时序：旧超额卡淡出、全部保留/新建卡延迟 5 帧后飞入
        applyTagSwapPiles(field, p, mcount, ecount, pcount, handCodes, extraCodes, topcode);
        // 动画持闸：5 帧离场等待 + 5 帧飞回 + 尾帧余量（对齐两段 WaitFrameSignal(5)）
        engine.animHoldUntilMs = Math.max(engine.animHoldUntilMs, System.currentTimeMillis() + 13L * 17L);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) {
                engine.listener.onFieldChanged();
                engine.listener.onPlayerInfoUpdated(p);
            }
        });
    }

    /** MSG_TAG_SWAP 三区重建（对齐 duelclient.cpp L4209-4286 的 deck/hand/extra 循环）：
     *  超出新数量的尾部卡移除并淡出（C++ DestroyCard），不足则补背面占位卡（C++ CreateCard），
     *  手卡/额外逐张回填卡码，卡组顶回填 topcode，最后逐张从 Y 偏移飞回正位（delay=5
     *  对应第一段甩离节拍）；extra_p_count 按消息值直接覆盖（C++ L4254） */
    private void applyTagSwapPiles(GameField field, int p, int mcount, int ecount, int pcount,
                                   int[] handCodes, int[] extraCodes, int topcode) {
        resizeTagPile(field, p, CardLocation.Deck.value(), mcount, null);
        resizeTagPile(field, p, CardLocation.Hand.value(), handCodes.length, handCodes);
        resizeTagPile(field, p, CardLocation.Extra.value(), extraCodes.length, extraCodes);
        field.extraPCount[p] = pcount;
        List<GameField.ClientCard> deck = field.players[p].deck;
        if (!deck.isEmpty() && topcode != 0) {
            GameField.ClientCard top = deck.get(deck.size() - 1);
            // C++ L4266-4267 直接赋值 code（不走 SetCode）：卡组顶卡码仅供堆叠区查看，
            // 无旧卡码进 chain_code 的飞行展示需求
            if (top != null) top.code = topcode;
        }
        // 手卡数量变化后双方手卡重排（对齐 C++ 换人后 GetCardLocation 全量重摆）
        field.updateHandLayout(0, 8);
        field.updateHandLayout(1, 8);
        field.refreshCardCountDisplay();
    }

    private void resizeTagPile(GameField field, int p, int loc, int count, int[] codes) {
        List<GameField.ClientCard> list = field.players[p].getLocationList(loc);
        if (list == null) return;
        // Java 堆区列表为 null 填充定长（removeCard 置尾 null 不缩表），C++ 为 vector
        // pop_back/push_back：先压实尾部空位，再以实际长度语义增删
        while (!list.isEmpty() && list.get(list.size() - 1) == null) list.remove(list.size() - 1);
        while (list.size() > count) {
            GameField.ClientCard c = list.remove(list.size() - 1);
            if (c == null) continue;
            if (loc == CardLocation.Extra.value() && c.isFaceUp()) field.extraPCount[p]--;
            c.location = 0;
            c.clearTarget();
            c.is_hovered = false;
            // 离场淡出（等价 C++ 第一段甩向场外后 DestroyCard）：与 MSG_MOVE 离场分支同构
            field.fadeCard(c, 5, GameField.APPEAR_FRAME);
            field.fadingCards.add(c);
        }
        while (list.size() < count) {
            GameField.ClientCard c = new GameField.ClientCard();
            c.owner = p;
            c.controler = p;
            c.location = loc;
            c.sequence = list.size();
            list.add(c);
        }
        for (int i = 0; i < list.size(); i++) {
            GameField.ClientCard c = list.get(i);
            if (c == null) continue;
            // C++ L4270/L4278 直接赋值（不走 SetCode）：整堆卡码全部按新行动选手回填
            if (codes != null && i < codes.length) c.code = codes[i];
            field.setCardPos(c);
            // 飞回起点：我方向屏幕外（+Y）/对方向场地深处（-Y）偏移（C++ L4262-4263
            // curPos.Y += 2.0f / -= 3.0f），5 帧延迟后 moveCardAnimated 归位
            c.curY += (p == 0 ? 2.0f : -3.0f);
            field.moveCardAnimated(c, 5, 5);
        }
    }
}
