package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import ocgcore.DataManager;
import ocgcore.data.Card;

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

    /** 等待提示轮换间隔（game.cpp L1624-1633：waitFrame 每 90 帧≈1.5s 轮换 1390/1391/1392） */
    private static final long WAIT_HINT_TICK_MS = 1500;
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
     */
    public String selectRangeHint(ByteBuffer data, int defIndex, String defText) {
        try {
            ByteBuffer dup = data.duplicate();
            dup.order(ByteOrder.LITTLE_ENDIAN);
            dup.get(); // selecting_player
            dup.get(); // cancelable（UNSELECT 为 finishable 位，同位置）
            int min = dup.get() & 0xFF;
            int max = dup.get() & 0xFF;
            int hint = engine.field.selectHint;
            String title = hint > 0 ? DataManager.get().getDesc(hint, defText) : sysString(defIndex, defText);
            return title + "(" + min + "-" + max + ")";
        } catch (Exception e) {
            return null;
        }
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
