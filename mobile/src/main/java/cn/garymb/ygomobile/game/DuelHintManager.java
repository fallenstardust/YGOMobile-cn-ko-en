package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.enums.CardLocation;

/**
 * === stHintMsg 提示栏（对齐 gframe：选择/等待类消息显示，下一条消息隐藏）
 *  + 提示栏文字生成（对齐 gframe stHintMsg 各调用点的格式化） ===
 * 自 GameEngine 拆分而来，逻辑与原实现逐行对齐（duelclient.cpp / game.cpp）。
 */
public class DuelHintManager {

    private final GameEngine engine;

    public DuelHintManager(GameEngine engine) {
        this.engine = engine;
    }

    /** 等待提示轮换间隔（game.cpp L1624-1633：waitFrame 每帧++，%90==30→1391、%90==60→1392、%90==0→1390，
     *  即每 30 帧≈ 0.5s(60fps) 轮换一次，周期 90 帧≈1.5s） */
    private static final long WAIT_HINT_TICK_MS = 500;
    private static final int[] WAIT_HINT_SYS = {1390, 1391, 1392};
    private int waitHintIndex;
    private final Runnable waitHintTicker = new Runnable() {
        @Override
        public void run() {
            waitHintIndex = (waitHintIndex + 1) % WAIT_HINT_SYS.length;
            postDuelHint(sysString(WAIT_HINT_SYS[waitHintIndex], "等待行动中..."));
            engine.mainHandler.postDelayed(this, WAIT_HINT_TICK_MS);
        }
    };

    public String sysString(int index, String def) {
        return DataManager.get().getStringManager().getSystemString(index, def);
    }

    /**
     * 写入 event_string（对齐 duelclient.cpp 各事件消息的 myswprintf(event_string, GetSysString(id), args...)）。
     * 无参数时直接取系统字符串；有参数时用 formatSystemString 填充其中的 %ls/%d。
     */
    public void setEventString(int index, String def, Object... args) {
        engine.field.eventString = (args == null || args.length == 0)
                ? sysString(index, def)
                : DataManager.get().formatSystemString(index, def, args);
    }

    public void postDuelHint(String text) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onDuelHint(text);
        });
    }

    public void postDuelHintHide() {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onDuelHintHide();
        });
    }

    /** 停止等待提示动画（对齐 duelclient.cpp L1309：waitFrame = -1） */
    public void stopWaitHint() {
        engine.mainHandler.removeCallbacks(waitHintTicker);
    }

    /**
     * 启动等待提示轮换（对齐 duelclient.cpp L1610-1616 + game.cpp L1624-1633：
     * waitFrame=0，显示"等待行动中..."并轮换），MSG_WAITING 时由 GameMessageParser.onWaiting 调用。
     */
    public void startWaitHint() {
        waitHintIndex = 0;
        postDuelHint(sysString(1390, "等待行动中..."));
        engine.mainHandler.removeCallbacks(waitHintTicker);
        engine.mainHandler.postDelayed(waitHintTicker, WAIT_HINT_TICK_MS);
    }

    /**
     * 生成选择类消息提示（duelclient.cpp L1965/L2056/L2331："%ls(%d-%d)"）。
     * 用 duplicate 解析不推进原缓冲（UI 侧仍需读取同一数据）；
     * selectHint 只读取不消费（消费仍由 UI 侧对话框标题承担）
     *
     * @param skipBeforeMinMax min/max 之前需跳过的字节数：MSG_SELECT_CARD/TRIBUTE 头为
     *                         player/cancelable（跳过 2），MSG_SELECT_UNSELECT 头为
     *                         player/finishable/cancelable（duelclient.cpp L1989-1994，跳过 3）
     */
    public String selectRangeHint(ByteBuffer data, int defIndex, String defText) {
        return selectRangeHint(data, defIndex, defText, 2);
    }

    public String selectRangeHint(ByteBuffer data, int defIndex, String defText, int skipBeforeMinMax) {
        try {
            ByteBuffer dup = data.duplicate();
            dup.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < skipBeforeMinMax; i++) dup.get();
            int min = dup.get() & 0xFF;
            int max = dup.get() & 0xFF;
            int hint = engine.field.selectHint;
            String title = hint > 0 ? DataManager.get().getDesc(hint, defText) : sysString(defIndex, defText);
            return title + "(" + min + "-" + max + ")";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 复刻 duelclient.cpp L1958-1964/L2022-2053 的 panelmode 判定：候选卡位于
     * 卡组/墓地/除外/额外/超量素材/衍生物（l & 0xF1），或选择方手牌数 ≥ 10
     * 且本条消息候选中的手卡 ≥ 2 张时为弹窗选择模式——C++ 此时文案进
     * stCardSelect 标题而非 stHintMsg，调用方应跳过提示栏显示。
     *
     * @param headerSkip  首个 count 字节前的头长度（SELECT_CARD 为 player/cancelable/min/max=4，
     *                    SELECT_UNSELECT 为 player/finishable/cancelable/min/max=5）
     * @param batchCount  候选批次：SELECT_CARD 为 1 批，SELECT_UNSELECT 为 count1/批1 + count2/批2
     */
    public boolean selectCardPanelMode(ByteBuffer data, int headerSkip, int batchCount) {
        if (data == null) return false;
        try {
            ByteBuffer dup = data.duplicate();
            dup.order(ByteOrder.LITTLE_ENDIAN);
            dup.position(dup.position() + headerSkip);
            GameField field = engine.getField();
            int[] handInMsg = new int[2];
            for (int b = 0; b < batchCount; b++) {
                int count = dup.get() & 0xFF;
                for (int i = 0; i < count; i++) {
                    dup.getInt(); // code
                    int c = engine.localPlayer(dup.get() & 0xFF);
                    int l = dup.get() & 0xFF;
                    dup.get(); // seq
                    dup.get(); // subseq
                    if ((l & 0xF1) != 0) return true;
                    if ((l & CardLocation.Hand.value()) != 0 && field != null && c >= 0 && c < 2) {
                        // 对齐 hand_count[c]：PlayerField.hand 为定长索引表（空位 null），取非空实卡数
                        List<GameField.ClientCard> hand = field.players[c].hand;
                        int handCount = 0;
                        for (GameField.ClientCard cc : hand) if (cc != null) handCount++;
                        if (handCount >= 10 && ++handInMsg[c] > 1) return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** 解析 player(1) counter_type(2) counter_count(2)，对齐 duelclient.cpp L2362：GetSysString(204) */
    public String counterHint(ByteBuffer data) {
        try {
            ByteBuffer dup = data.duplicate();
            dup.order(ByteOrder.LITTLE_ENDIAN);
            dup.get(); // selecting_player
            int counterType = dup.getShort() & 0xFFFF;
            int count = dup.getShort() & 0xFFFF;
            DataManager dm = DataManager.get();
            return dm.formatSystemString(204, "请取除%d个[%s]", count, dm.getCounterName(counterType));
        } catch (Exception e) {
            return null;
        }
    }

    /** 对齐 dataManager.GetName（duelclient.cpp L2201：GetName(select_hint)） */
    public String getCardDisplayName(int code) {
        if (code <= 0) return "?";
        Card card = DataManager.get().getCardManager().getCard(code);
        return card != null && card.Name != null ? card.Name : "?";
    }
}
