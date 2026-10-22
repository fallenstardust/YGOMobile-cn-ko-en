package cn.garymb.ygomobile.game;

import android.util.Log;

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
        // 撤回后的应答代次屏障：被撤销询问的迟到应答（弹窗关闭前的点击、未取消的自动应答、
        // 按旧命令列表编码的指令）一律不发。服务端已把待应答席位转到回退后的新局面，错拍应答
        // 轻则被引擎判为非法引发 MSG_RETRY 风暴（表现为通讯中断），重则旧列表里的合法指令凭空
        // 推进新局面（表现为直接跳到下个回合）；新询问到达后本出口自动恢复放行
        if (engine.isResponseBlocked()) {
            Log.w("GameActions", "response dropped by undo barrier, len="
                    + (responseData == null ? 0 : responseData.length));
            return;
        }
        // 残局：无网络，应答经引擎泵线程喂回本地决斗引擎（对齐 SingleMode::SetResponse）
        if (engine.isSingleMode) {
            engine.singleRunner.submitResponse(responseData);
            return;
        }
        engine.client.sendResponse(responseData);
        // 应答确实发出去了：从此刻起服务端应当要么把下一条询问挂给本席位、要么回一条 MSG_WAITING。
        // 两者都没到的看门狗会请服务端重发（询问丢失自愈，见 GameEngine#notifyResponseSent）
        engine.notifyResponseSent();
    }

    public void sendTimeConfirm() {
        engine.client.sendTimeConfirm();
    }

    public int getSelfType() {
        return engine.client.selfType;
    }
}
