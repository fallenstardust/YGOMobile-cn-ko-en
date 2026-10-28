package cn.garymb.ygomobile.game.replay;
import cn.garymb.ygomobile.game.engine.GameEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 回放聊天时间线：扫描逐帧流 0xF1 伪帧并按「前置可见步计数」编排，供正常节奏、快进落点、undo 后按步号补齐派发。 */
final class ReplayChatTimeline {

    /** 聊天时间线条目：0xF1 帧的帧体（不含帧号）与其前置可见步计数。 */
    private static final class Entry {
        final int step;
        final byte[] body;

        Entry(int step, byte[] body) {
            this.step = step;
            this.body = body;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    /** 聊天时间线游标（单调前移，回卷时复位）。 */
    private int cursor;

    /** 消息流内 0xF1 聊天伪帧条数（开场信息展示用）：V1 原始流/引擎重跑源无逐帧时返回 -1。 */
    static int countChatFrames(List<byte[]> frames) {
        if (frames == null) return -1;
        int n = 0;
        for (byte[] f : frames) {
            if (f != null && f.length >= 2 && (f[0] & 0xFF) == GameEngine.REPLAY_CHAT_FRAME) n++;
        }
        return n;
    }

    /** 构建时间线：0xF1 帧记「前置可见步计数+帧体」，其余帧按 isVisibleStep 推进计数（计步口径与 computeTotalSteps/pump 一致）。 */
    void build(List<byte[]> frames) {
        entries.clear();
        cursor = 0;
        if (frames == null) return;
        int step = 0;
        for (byte[] f : frames) {
            if (f == null || f.length == 0) continue;
            int type = f[0] & 0xFF;
            if (type == GameEngine.REPLAY_CHAT_FRAME) {
                if (f.length >= 2) {
                    byte[] body = new byte[f.length - 1];
                    System.arraycopy(f, 1, body, 0, body.length);
                    entries.add(new Entry(step, body));
                }
            } else if (ReplayMessageSlicer.isVisibleStep(type)) {
                step++;
            }
        }
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 回卷时复位游标（保留条目，落点后按步号批量补齐）。 */
    void resetCursor() {
        cursor = 0;
    }

    /** 派发步号 ≤ uptoStep 的全部聊天（游标单调前移，重复调用幂等）。 */
    void dispatchUpTo(int uptoStep, Consumer<byte[]> sink) {
        while (cursor < entries.size() && entries.get(cursor).step <= uptoStep) {
            sink.accept(entries.get(cursor).body);
            cursor++;
        }
    }

    /** 全部聊天帧体（帧序即时间序，不动游标）：录像打开时预载「消息记录·聊天」一次性取出。 */
    List<byte[]> collectChatBodies() {
        List<byte[]> out = new ArrayList<>(entries.size());
        for (Entry e : entries) out.add(e.body);
        return out;
    }

    /** 转码产物帧：把时间线聊天帧按步号交错并回重跑 captured 帧（否则遮蔽文件转码固化后 0xF1 被抹掉、丢聊天）。 */
    List<byte[]> buildTranscodeFrames(List<byte[]> captured) {
        List<byte[]> out = new ArrayList<>(captured.size() + entries.size());
        if (entries.isEmpty()) {
            out.addAll(captured);
            return out;
        }
        int step = 0, ci = 0;
        for (byte[] f : captured) {
            while (ci < entries.size() && entries.get(ci).step <= step) {
                out.add(chatFrameToWire(entries.get(ci++).body));
            }
            out.add(f);
            if (f != null && f.length > 0 && ReplayMessageSlicer.isVisibleStep(f[0] & 0xFF)) step++;
        }
        while (ci < entries.size()) out.add(chatFrameToWire(entries.get(ci++).body));
        return out;
    }

    /** 帧体 → 完整 0xF1 帧（帧号+playerType+UTF-8 文本），与录制侧 writeMessage 落盘布局一致。 */
    static byte[] chatFrameToWire(byte[] body) {
        byte[] wire = new byte[body.length + 1];
        wire[0] = (byte) GameEngine.REPLAY_CHAT_FRAME;
        System.arraycopy(body, 0, wire, 1, body.length);
        return wire;
    }
}
