package cn.garymb.ygomobile.network.server;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.engine.OcgDuelEngine;

/**
 * 区域刷新（Refresh*）域实现（自 DuelAnalyzer 平移，逻辑零改）：Mzone/Szone/Hand/Grave/Extra/Single
 * 各区的查询、暗置遮蔽与双视角发送。共享掩码常量与字节工具留在 {@link DuelAnalyzer}（同包 static 复用）。
 */
final class DuelZoneRefresh {

    private final DuelAnalyzer analyzer;

    DuelZoneRefresh(DuelAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    void refreshMzone(int player) {
        refreshMzone(player, DuelAnalyzer.REFRESH_MZONE_FLAG, 1);
    }

    void refreshMzone(int player, int flag, int useCache) {
        refreshMaskedZone(player, OcgDuelEngine.LOCATION_MZONE, flag, useCache);
    }

    void refreshSzone(int player) {
        refreshSzone(player, DuelAnalyzer.REFRESH_SZONE_FLAG, 1);
    }

    void refreshSzone(int player, int flag, int useCache) {
        refreshMaskedZone(player, OcgDuelEngine.LOCATION_SZONE, flag, useCache);
    }

    /** Mzone/Szone 通用：遍历收集暗置段→先发本人→清零暗置段→发对手+观战。 */
    private void refreshMaskedZone(int player, int location, int flag, int useCache) {
        byte[] blocks = OcgDuelEngine.queryFieldCard(analyzer.pduel(), player, location, flag, useCache);
        int len = blocks.length;
        List<int[]> hidden = new ArrayList<>();
        int qpos = 0;
        int qlen = 0;
        while (qlen < len && qpos + 4 <= len) {
            int clen = DuelAnalyzer.readInt32(blocks, qpos);
            qpos += 4;
            qlen += clen;
            if (clen <= EngineMessage.LEN_HEADER) {
                continue;
            }
            int position = EngineMessage.getPosition(blocks, qpos + 8);
            boolean hide = EngineMessage.shouldHideFacedownCode(position);
            EngineMessage.stripRevealFlag(blocks, qpos + 8);
            if (hide) {
                hidden.add(new int[]{qpos, clen});
            }
            qpos += clen - 4;
        }
        byte[] full = updateDataPayload(player, location, blocks, len);
        for (int[] seg : hidden) {
            DuelAnalyzer.zeroRange(blocks, seg[0], seg[1] - 4);
        }
        byte[] masked = updateDataPayload(player, location, blocks, len);
        analyzer.broadcaster.sendDualViewAndRecord(player, full, masked);
    }

    void refreshHand(int player) {
        refreshHand(player, DuelAnalyzer.REFRESH_HAND_FLAG, 1);
    }

    void refreshHand(int player, int flag, int useCache) {
        byte[] blocks = OcgDuelEngine.queryFieldCard(analyzer.pduel(), player, OcgDuelEngine.LOCATION_HAND, flag, useCache);
        int len = blocks.length;
        byte[] full = updateDataPayload(player, OcgDuelEngine.LOCATION_HAND, blocks, len);
        int qpos = 0;
        int qlen = 0;
        while (qlen < len && qpos + 4 <= len) {
            int slen = DuelAnalyzer.readInt32(blocks, qpos);
            qpos += 4;
            qlen += slen;
            if (slen <= EngineMessage.LEN_HEADER) {
                continue;
            }
            int position = EngineMessage.getPosition(blocks, qpos + 8);
            if ((position & EngineMessage.POS_FACEUP) == 0) {
                DuelAnalyzer.zeroRange(blocks, qpos, slen - 4);
            }
            qpos += slen - 4;
        }
        byte[] masked = updateDataPayload(player, OcgDuelEngine.LOCATION_HAND, blocks, len);
        analyzer.broadcaster.sendDualViewAndRecord(player, full, masked);
    }

    void refreshGrave(int player) {
        refreshGrave(player, DuelAnalyzer.REFRESH_GRAVE_FLAG, 1);
    }

    void refreshGrave(int player, int flag, int useCache) {
        byte[] blocks = OcgDuelEngine.queryFieldCard(analyzer.pduel(), player, OcgDuelEngine.LOCATION_GRAVE, flag, useCache);
        analyzer.broadcastMsg(updateDataPayload(player, OcgDuelEngine.LOCATION_GRAVE, blocks, blocks.length));
    }

    void refreshExtra(int player) {
        refreshExtra(player, DuelAnalyzer.REFRESH_EXTRA_FLAG, 1);
    }

    void refreshExtra(int player, int flag, int useCache) {
        byte[] blocks = OcgDuelEngine.queryFieldCard(analyzer.pduel(), player, OcgDuelEngine.LOCATION_EXTRA, flag, useCache);
        analyzer.sendToPlayer(analyzer.room.players[player],
                updateDataPayload(player, OcgDuelEngine.LOCATION_EXTRA, blocks, blocks.length));
    }

    void refreshSingle(int player, int location, int sequence) {
        refreshSingle(player, location, sequence, DuelAnalyzer.REFRESH_SINGLE_FLAG);
    }

    void refreshSingle(int player, int location, int sequence, int flag) {
        byte[] blocks = OcgDuelEngine.queryCard(analyzer.pduel(), player, location, sequence, flag, 0);
        int len = blocks.length;
        if (len <= EngineMessage.LEN_HEADER) {
            analyzer.sendToPlayer(analyzer.room.players[player], updateCardPayload(player, location, sequence, blocks, len));
            return;
        }
        int position = EngineMessage.getPosition(blocks, 12);
        // 手卡位置在本端口是里侧（POS_FACEDOWN），但“效果入手需向对手展示”已经由 MSG_MOVE
        // 分支公开了卡码；紧随其后的这次 refreshSingle 若仍按里侧清零，会把刚解除的卡码
        // 再次盖掉，对手侧永远拿不到入手卡的卡面 → 翻面确认无从渲染。故手卡不按里侧遮蔽。
        // （C++ single_duel.cpp RefreshSingle L1586-1616 对非场上位置一律 hide，此处为有意增强）
        boolean hide = (position & EngineMessage.POS_FACEDOWN) != 0
                && (location & EngineMessage.LOCATION_HAND) == 0;
        if ((location & EngineMessage.LOCATION_ONFIELD) != 0) {
            hide = EngineMessage.shouldHideFacedownCode(position);
            EngineMessage.stripRevealFlag(blocks, 12);
        }
        byte[] full = updateCardPayload(player, location, sequence, blocks, len);
        int sendLen = len;
        if (hide) {
            DuelAnalyzer.writeInt32(blocks, 0, 16);
            DuelAnalyzer.writeInt32(blocks, 4, DuelAnalyzer.QUERY_CODE_POSITION);
            DuelAnalyzer.writeInt32(blocks, 8, 0); // 清零 code，保留 12..15 姿态
            sendLen = 16;
        }
        byte[] masked = updateCardPayload(player, location, sequence, blocks, sendLen);
        analyzer.broadcaster.sendDualViewAndRecord(player, full, masked);
    }

    static byte[] updateDataPayload(int player, int location, byte[] blocks, int len) {
        byte[] d = new byte[len + DuelAnalyzer.MSG_HEADER_LEN];
        d[0] = (byte) EngineMessage.MSG_UPDATE_DATA;
        d[1] = (byte) player;
        d[2] = (byte) location;
        System.arraycopy(blocks, 0, d, DuelAnalyzer.MSG_HEADER_LEN, len);
        return d;
    }

    static byte[] updateCardPayload(int player, int location, int sequence, byte[] blocks, int len) {
        byte[] d = new byte[len + DuelAnalyzer.CARD_HEADER_LEN];
        d[0] = (byte) EngineMessage.MSG_UPDATE_CARD;
        d[1] = (byte) player;
        d[2] = (byte) location;
        d[3] = (byte) sequence;
        System.arraycopy(blocks, 0, d, DuelAnalyzer.CARD_HEADER_LEN, len);
        return d;
    }
}
