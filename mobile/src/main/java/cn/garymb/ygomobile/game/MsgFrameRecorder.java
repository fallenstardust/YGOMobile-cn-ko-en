package cn.garymb.ygomobile.game;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ocgcore.enums.GameMessage;

/**
 * 逐局 MSG 帧录制（自 GameEngine 平移，逻辑零改）。
 * STOC_REPLAY 保存时合并出双兼容 V2 文件（见 ReplayMsgMerger）。
 */
class MsgFrameRecorder {

    /** 回放聊天伪帧消息号（ocgcore 0xF1，本工程专用） */
    static final int REPLAY_CHAT_FRAME = 0xF1;

    /** 当前局已录帧（每帧 = [消息号字节]+payload，与服务端→players[0] 的完整引擎消息同构） */
    private final List<byte[]> msgSegFrames = new ArrayList<>();
    /** 已完结的分局段（match 三局每局一段），与 STOC_REPLAY 到达顺序 FIFO 配对 */
    private final List<List<byte[]>> msgSegDone = new ArrayList<>();
    private final Object msgRecLock = new Object();
    /** 本局消息帧流是否已因撤回而作废 */
    private boolean msgSegInvalid;

    private final GameEngine engine;

    MsgFrameRecorder(GameEngine engine) {
        this.engine = engine;
    }

    /**
     * 录制一条引擎消息（网络线程在 enqueueGameMsg 快照处调用，与响应流同序）：
     * MSG_START 分局，MSG_WIN/MATCH_KILL/DUEL_WINNER 完结本段；MSG_RETRY 不入帧。
     */
    void record(int msgType, byte[] body) {
        synchronized (msgRecLock) {
            if (msgSegInvalid) {
                msgSegFrames.clear();
                if (msgType != GameMessage.Start.value()) {
                    return;
                }
                msgSegInvalid = false;
            }
            if (msgType == GameMessage.Start.value() && !msgSegFrames.isEmpty()) {
                msgSegDone.add(new ArrayList<>(msgSegFrames));
                msgSegFrames.clear();
            }
            byte[] frame = new byte[body.length + 1];
            frame[0] = (byte) msgType;
            System.arraycopy(body, 0, frame, 1, body.length);
            msgSegFrames.add(frame);
            if (msgType == GameMessage.Win.value() || msgType == GameMessage.MatchKill.value()
                    || msgType == GameMessage.DuelWinner.value()) {
                msgSegDone.add(new ArrayList<>(msgSegFrames));
                msgSegFrames.clear();
            }
        }
    }

    /** 取最早已完结的 MSG 段；无完结段时退而取当前段 */
    List<byte[]> takeRecordedMsgSegment() {
        synchronized (msgRecLock) {
            if (!msgSegDone.isEmpty()) return msgSegDone.remove(0);
            if (!msgSegFrames.isEmpty()) {
                List<byte[]> cur = new ArrayList<>(msgSegFrames);
                msgSegFrames.clear();
                return cur;
            }
        }
        return Collections.emptyList();
    }

    /** 新一场决斗开始：丢弃上一场残留段，防串局 */
    void resetMsgRecording() {
        synchronized (msgRecLock) {
            msgSegFrames.clear();
            msgSegDone.clear();
            msgSegInvalid = false;
        }
    }

    /** 撤回成功后作废本局的 MSG 尾段录制 */
    void invalidateCurrentMsgSegment() {
        synchronized (msgRecLock) {
            msgSegInvalid = true;
            msgSegFrames.clear();
        }
    }

    /**
     * 录制一条聊天/观战发言为 0xF1 伪帧（实况唯一聊天派发入口，覆盖玩家/队友/观战/系统）：
     * 回放模式丢弃；当前段在录则追加段尾；最近完结段未取走时追加；决斗外丢弃。
     */
    void recordChatFrame(int playerType, String message) {
        if (engine.replayMode || message == null || message.isEmpty()) return;
        byte[] text = message.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[text.length + 2];
        frame[0] = (byte) REPLAY_CHAT_FRAME;
        frame[1] = (byte) playerType;
        System.arraycopy(text, 0, frame, 2, text.length);
        synchronized (msgRecLock) {
            if (!msgSegFrames.isEmpty()) {
                msgSegFrames.add(frame);
            } else if (!msgSegDone.isEmpty()) {
                msgSegDone.get(msgSegDone.size() - 1).add(frame);
            }
        }
    }
}
