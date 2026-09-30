package cn.garymb.ygomobile.game;

import java.util.List;

/**
 * 回放区域计数与卡面回填（自 ReplayPlayer 拆分，逻辑零改）：
 * 持 ReplayPlayer 反向引用读取视角映射与显示卡码，经 engine.localPlayer 做协议侧↔本地视角换算。
 */
class ReplayZoneCounter {

    private final ReplayPlayer player;

    ReplayZoneCounter(ReplayPlayer player) {
        // 仅持反向引用：engine/field 在使用时延迟读取，避免本对象在 ReplayPlayer 字段初始化阶段
        // 构造时其 engine 尚未在构造器体内赋值而读到 null（否则 GameEngine 构造链上 NPE）。
        this.player = player;
    }

    /**
     * 某区域当前卡片数（UPDATE_DATA 的 query 块数），口径同实况
     * {@code CommandDataParser.parseUpdateData}：固定槽位区域（怪兽区/魔法区）整列计数，
     * 动态列表只计实际存在的卡。
     */
    int zoneBlockCount(int playerIdx, int location) {
        GameEngine engine = player.engine;
        List<GameField.ClientCard> list = engine.field.players[engine.localPlayer(playerIdx)]
                .getLocationList(location);
        if (list == null) return 0;
        if (location == 0x04 || location == 0x08) return list.size();
        int count = 0;
        for (GameField.ClientCard card : list) {
            if (card != null) count++;
        }
        return count;
    }

    /**
     * 录像头部卡组/额外卡码回填：MSG_START 与 {@code field.initial} 只按数量建背面卡
     * （code=0），此处把已归一的卡码按索引同序写进 ClientCard，使双方卡组/额外可直接点开
     * 查看正面（修复手卡/卡组 unknown）；初始场建立后及每次快进落点后均需调用（重排会重建卡对象）
     */
    void applyReplayDeckCodes() {
        if (player.replayData == null) return;
        GameEngine engine = player.engine;
        GameField field = engine.field;
        // 回填与 field.initial 同一套视角映射：录制者（引擎 P0）卡组写 localPlayer(0) 容器
        int l0 = engine.localPlayer(0);
        int l1 = engine.localPlayer(1);
        writeZoneCodes(field.players[l0].deck, player.displayMain0);
        writeZoneCodes(field.players[l0].extra, player.displayExtra0);
        writeZoneCodes(field.players[l1].deck, player.displayMain1);
        writeZoneCodes(field.players[l1].extra, player.displayExtra1);
        field.refreshAllCards();
    }

    private void writeZoneCodes(List<GameField.ClientCard> list, List<Integer> codes) {
        if (list == null || codes == null) return;
        int n = Math.min(list.size(), codes.size());
        for (int i = 0; i < n; i++) {
            GameField.ClientCard card = list.get(i);
            if (card == null) continue;
            card.code = codes.get(i);
        }
    }

    /**
     * 录像头部（ReplayReader 自 .yrp 解出的双方卡组/额外卡组全部卡码，已按本机卡表归一）
     * 权威取数入口：viewPlayer 为本地视角容器索引（0=我方/1=对方，与 field.players[] 同索引），
     * 经 engine.localPlayer 对合映射换算回录制侧下标，视角互换后自动跟随。
     * 回放态堆叠区查看直接以该列表展开全部卡面，不依赖实况 ClientCard 是否已被回填——
     * 修复回填时序竞争/抽卡换位导致的卡组、额外里侧卡「时灵时不灵」；
     * 非卡组(0x01)/额外(0x40)区域或头部无数据时返回 null（调用方回落实况列表）。
     */
    List<Integer> getReplayZoneCodes(int viewPlayer, int location) {
        if (player.replayData == null || viewPlayer < 0 || viewPlayer > 1) return null;
        if (location != 0x01 && location != 0x40) return null;
        GameEngine engine = player.engine;
        int proto = engine.localPlayer(viewPlayer);
        if (location == 0x01) return proto == 0 ? player.displayMain0 : player.displayMain1;
        return proto == 0 ? player.displayExtra0 : player.displayExtra1;
    }
}
