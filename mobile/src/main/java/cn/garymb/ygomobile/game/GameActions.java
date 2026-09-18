package cn.garymb.ygomobile.game;

import java.util.List;

import cn.garymb.ygomobile.render.TextureLoader;

/**
 * === Game Actions ===
 * 自 GameEngine 拆分而来：对局内主动操作——猜拳/先攻选择/卡组上报/应答/时间确认。
 */
public class GameActions {

    private final GameEngine engine;

    public GameActions(GameEngine engine) {
        this.engine = engine;
    }

    public void sendHandResult(int result) {
        engine.client.sendHandResult(result);
    }

    public void sendTPResult(boolean chooseFirst) {
        engine.client.sendTPResult(chooseFirst);
    }

    public void sendDeckUpdate(List<Integer> main, List<Integer> extra, List<Integer> side) {
        engine.client.sendUpdateDeck(main, extra, side);
        // 预读本方卡组全部卡图（TextureLoader 后台解码 + LRU 缓存、内部去重），
        // 对局开始后我方抽卡/召唤直接带图，消除灰色占位闪现
        try {
            TextureLoader tl = TextureLoader.get();
            for (List<Integer> deck : new List[]{main, extra, side}) {
                if (deck == null) continue;
                for (Integer code : deck) {
                    if (code != null && code > 0) tl.getCardBitmap(code & 0xFFFFFFFFL);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void sendResponse(byte[] responseData) {
        engine.client.sendResponse(responseData);
    }

    public void sendTimeConfirm() {
        engine.client.sendTimeConfirm();
    }

    public int getSelfType() {
        return engine.client.selfType;
    }
}
