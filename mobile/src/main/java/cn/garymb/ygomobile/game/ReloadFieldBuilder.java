package cn.garymb.ygomobile.game;

import android.util.Log;

import java.nio.ByteBuffer;

import ocgcore.DataManager;
import ocgcore.enums.CardLocation;

/**
 * MSG_RELOAD_FIELD 全场快照重建实现（自 DuelEventHandler 平移，逻辑零改；
 * 对齐 duelclient.cpp L4287-4441）。日志沿用门面 TAG 便于与旧版比对。
 */
class ReloadFieldBuilder {

    private static final String TAG = "GameEngine";

    private final GameEngine engine;

    ReloadFieldBuilder(GameEngine engine) {
        this.engine = engine;
    }

    void apply(ByteBuffer data) {
        // duelclient.cpp MSG_RELOAD_FIELD L4287-4441：断线重连时服务端下发全场快照，
        // 重建双方 场/手/卡组/墓地/除外/额外(含里侧与表侧个数) 全部卡片与连锁信息。
        // 隐藏卡不下发卡码（新建 ClientCard code=0 背面占位，与 C++ CreateCard 一致）。
        if (data == null) return;
        final GameField field = engine.field;
        field.clear();
        if (data.remaining() < 1) {
            Log.w(TAG, "onReloadField: truncated payload");
            return;
        }
        field.dInfo.duelRule = data.get() & 0xFF;
        for (int i = 0; i < 2; i++) {
            int p = engine.localPlayer(i);
            if (data.remaining() < 4) {
                Log.w(TAG, "onReloadField: truncated at player " + i);
                break;
            }
            int lp = data.getInt();
            field.dInfo.lp[p] = lp;
            field.players[p].lp = lp;
            engine.playerInfos[p].lp = lp;
            // 怪兽区 7 格：present(1) → [position(1) + overlayCnt(1) + 素材占位]
            for (int seq = 0; seq < GameField.MAX_MONSTER_ZONE; seq++) {
                if (data.remaining() < 1) return;
                int present = data.get() & 0xFF;
                if (present == 0) continue;
                if (data.remaining() < 2) return;
                GameField.ClientCard card = new GameField.ClientCard();
                field.addCard(p, CardLocation.MonsterZone.value(), seq, card);
                card.position = data.get() & 0xFF;
                int ovc = data.get() & 0xFF;
                for (int xyz = 0; xyz < ovc; xyz++) {
                    // C++ L4316-4328：素材仅作占位（reload 载荷不携卡码），sequence 为 overlayed 内索引；
                    // 卡码由紧随其后的 refreshMzone（服务端携 QUERY_OVERLAY_CARD）填入同一占位对象
                    GameField.ClientCard x = new GameField.ClientCard();
                    card.overlayed.add(x);
                    field.overlayCards.add(x);
                    x.overlayTarget = card;
                    x.location = CardLocation.Overlay.value();
                    x.sequence = card.overlayed.size() - 1;
                    x.owner = p;
                    x.controler = p;
                    // 超量素材恒为表侧公开信息，而 query_field_info 不写素材姿态：留 position=0 会被
                    // 渲染层 isFaceUp() 判为里侧而统一画成卡背（同 onMove 叠放分支对新建素材的
                    // position==0 兼容处理）；叠放层序与旋转由 getCardLocation 的 OVERLAY 分支单独求出，
                    // 此处只影响正/背面贴图的选择
                    x.position = GameField.POS_FACEUP;
                }
            }
            // 魔法陷阱区 8 格：present(1) → [position(1)]（无叠放字段）
            for (int seq = 0; seq < GameField.MAX_SPELL_ZONE; seq++) {
                if (data.remaining() < 1) return;
                int present = data.get() & 0xFF;
                if (present == 0) continue;
                if (data.remaining() < 1) return;
                GameField.ClientCard card = new GameField.ClientCard();
                field.addCard(p, CardLocation.SpellZone.value(), seq, card);
                card.position = data.get() & 0xFF;
            }
            // deck/hand/grave/removed/extra 五区：各 cnt(1) + cnt 张占位卡（C++ 不读卡详情与 position）
            int[] zoneLocs = {CardLocation.Deck.value(), CardLocation.Hand.value(),
                    CardLocation.Grave.value(), CardLocation.Removed.value(), CardLocation.Extra.value()};
            for (int zone = 0; zone < zoneLocs.length; zone++) {
                if (data.remaining() < 1) return;
                int cnt = data.get() & 0xFF;
                for (int seq = 0; seq < cnt; seq++) {
                    GameField.ClientCard card = new GameField.ClientCard();
                    card.owner = p;
                    card.controler = p;
                    field.addCard(p, zoneLocs[zone], seq, card);
                }
            }
            // 额外卡组表侧张数（在上方 extra 占位卡全部入堆后赋值，避免 addCard 自增干扰）
            if (data.remaining() < 1) return;
            field.extraPCount[p] = data.get() & 0xFF;
        }
        // C++ L4378：RefreshAllCards 重算全场落点（场上/手卡/墓地/除外/额外摆放位置即此恢复）
        field.refreshAllCards();
        // 链信息：cnt(1) + cnt×[code(4) pcc pcl pcs subs cc cl cs(各1) desc(4)]
        int chainCnt = 0;
        if (data.remaining() >= 1) {
            chainCnt = data.get() & 0xFF;
        }
        GameField.ChainInfo lastChain = null;
        for (int i = 0; i < chainCnt; i++) {
            if (data.remaining() < 15) {
                Log.w(TAG, "onReloadField: truncated chain " + i);
                break;
            }
            int code = data.getInt();
            int pcc = engine.localPlayer(data.get() & 0xFF);
            int pcl = data.get() & 0xFF;
            int pcs = data.get() & 0xFF;
            int subs = data.get() & 0xFF;
            int cc = engine.localPlayer(data.get() & 0xFF);
            int cl = data.get() & 0xFF;
            int cs = data.get() & 0xFF;
            int desc = data.getInt();
            GameField.ChainInfo ci = new GameField.ChainInfo();
            ci.chainCard = field.getCard(pcc, pcl, pcs, subs);
            ci.code = code;
            ci.desc = desc;
            ci.controler = cc;
            ci.location = cl;
            ci.sequence = cs;
            ci.solved = false;
            field.chains.add(ci);
            lastChain = ci;
        }
        // C++ L4433-4436：存在链时写入事件提示串（sys 1609 + 最后一条链卡名）
        if (lastChain != null) {
            field.eventString = DataManager.get().formatSystemString(1609,
                    "【%s】的连锁发动", DataManager.get().getName(lastChain.code));
        }
        // 撤回（CTOS_UNDO）重同步：field.clear() 已把回合数/阶段归零，而 MSG_RELOAD_FIELD 本身
        // 不携这两项，故从 STOC_UNDO_ACK 暂存值回填（非撤回触发的 reload 不会有 latch，行为不变）
        final boolean undoResync = engine.applyUndoResync(field);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) {
                engine.listener.onFieldChanged();
                engine.listener.onPlayerInfoUpdated(0);
                engine.listener.onPlayerInfoUpdated(1);
                if (undoResync) {
                    // 回合方高亮与阶段文本随回退后的值重刷（同 performSpectatorSwap 的收尾三连）
                    engine.listener.onTurnStarted(field.currentPlayer);
                    engine.listener.onPhaseChanged(field.currentPhase);
                }
            }
        });
    }
}
