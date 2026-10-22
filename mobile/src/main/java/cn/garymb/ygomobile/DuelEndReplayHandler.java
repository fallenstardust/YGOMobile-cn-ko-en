package cn.garymb.ygomobile;

import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.replay.ReplayReader;
import cn.garymb.ygomobile.ui.dialogs.replay.ReplaySaveDialog;
import cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog;

/** 决斗结束与录像保存子系统（自 EngineCallbackDelegate 拆出）：待处理录像队列、观战结算动画排空等待、逐个录像保存/自动保存对话框与落盘、决斗结束提示与对外复位/断开抑制标志。 */
class DuelEndReplayHandler {

    private static final String TAG = "YGONativeGame";

    private final YGOProActivity activity;

    DuelEndReplayHandler(YGOProActivity activity) {
        this.activity = activity;
    }

    // === 决斗结束 / 录像处理状态 ===
    final List<byte[]> pendingReplays = new ArrayList<>();
    boolean duelEndHandling = false;
    // 显式退出等待界面时置位：本次 DISCONNECTED 回调跳过 returnToLanMain，返回导航由退出入口独占（bot→SingleModeDialog / LAN→LanModeDialog），避免 LanModeDialog 与 MainMenuDialog 连带弹出
    boolean suppressDisconnectedReturn = false;
    YesOrNoDialog resultDialog;
    ReplaySaveDialog replaySaveDialog;
    // STOC_REPLAY 紧随 STOC_DUEL_END 下发：录像处理延迟到该窗口内无新数据到达再启动，避免在录像数据尚未收齐时就走到「决斗结束」弹窗
    static final long REPLAY_ARRIVAL_WAIT_MS = 800;
    /** 观战结算前动画排空等待：轮询间隔与超时兜底（异常/卡死时不再无限等待） */
    static final long ANIM_SETTLE_POLL_MS = 100L;
    static final long ANIM_SETTLE_TIMEOUT_MS = 20000L;
    /** 本次决斗结束等待动画排空的截止时间戳（DUEL_END 置位，processPendingReplays 轮询至空或超时） */
    long animSettleDeadlineMs = 0L;
    /** 决斗结束时本端是否观战（在 disconnect 前捕获，避免断开后身份丢失）：观战结算前才需等待动画排空（需求4），对战玩家不等待 */
    boolean duelEndedAsSpectator = false;
    final Runnable duelEndReplayProcessor = new Runnable() {
        @Override
        public void run() {
            processPendingReplays();
        }
    };

    /** 抑制下一次 DISCONNECTED 自动 returnToLanMain（由退出等待界面入口在 disconnect 前置位） */
    void suppressNextDisconnectedReturn() {
        suppressDisconnectedReturn = true;
    }

    /** 是否正处于「决斗结束 → 待点确定返回主界面」的收尾流程（弹窗期间旋转须保持决斗 UI 显示） */
    boolean isDuelEndHandling() {
        return duelEndHandling;
    }

    /** 决斗结束提示框：仅显示「确定」按钮（TYPE_MESSAGE），弹窗时机在通讯录像全部确认保存或取消之后（见 processPendingReplays），点确定后隐藏决斗 UI 并重新显示 LanModeDialog */
    private void showDuelEndDialog() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setMessage(activity.mStringManager.getSystemString(1500, "決斗结束。"))
                .setType(YesOrNoDialog.TYPE_MESSAGE)
                .setPositiveButtonText(activity.mStringManager.getSystemString(1211, "确定"))
                .setPositiveButton(v -> activity.returnToLanMain(null))
                .setCenterInView(activity.layoutGameRight)
                .setCancelable(false);
        dialog.show();
    }

    /** 延迟启动录像处理：先取消上一次调度，窗口内若有新录像到达（onReplayData）会再次重置，直到通讯不再发送录像才真正开始逐个弹出录像保存对话框 */
    void scheduleReplayProcessing() {
        activity.mainHandler.removeCallbacks(duelEndReplayProcessor);
        activity.mainHandler.postDelayed(duelEndReplayProcessor, REPLAY_ARRIVAL_WAIT_MS);
    }

    /** 逐个处理通讯发来的录像：有待保存项则弹出录像保存对话框，全部处理完毕（保存或取消跳过）后才弹出「决斗结束」对话框 */
    private void processPendingReplays() {
        // 防止延迟调度与队列内递归调用重叠执行
        activity.mainHandler.removeCallbacks(duelEndReplayProcessor);
        if (activity.isFinishing() || activity.isDestroyed()) {
            pendingReplays.clear();
            return;
        }
        // 观战（需求1）：已收的全部通讯 msg 可能仍在按动画序列逐个播放（统一闸门串行化），需等消息队列排空且无任何动画在播再弹保存录像 dialog；实况观战会「积压」（连锁/快速效果下发速度远超动画播放速度），故队列仍有存量（backlog）时无条件继续等待并每轮刷新兜底截止（backlog 被持续消化不是卡死，动画闸门另有 8s failsafe 强制推进，队列必然最终排空）；早前只取 DUEL_END 一次性 20s 截止、不随 backlog 刷新，导致观战积压超 20s 时 dialog 提前弹出打断动画，正是本次要修复的根因，队列已空仅剩收尾动画（胜负文字等）时才用超时兜底。
        GameEngine eng = activity.engine;
        if (eng != null && duelEndedAsSpectator) {
            if (eng.hasPendingMsgs() || !eng.isMsgQueueIdle()) {
                animSettleDeadlineMs = System.currentTimeMillis() + ANIM_SETTLE_TIMEOUT_MS;
                activity.mainHandler.postDelayed(duelEndReplayProcessor, ANIM_SETTLE_POLL_MS);
                return;
            }
            if (eng.isAnyAnimationBusy() && System.currentTimeMillis() < animSettleDeadlineMs) {
                activity.mainHandler.postDelayed(duelEndReplayProcessor, ANIM_SETTLE_POLL_MS);
                return;
            }
        }
        if (pendingReplays.isEmpty()) {
            showDuelEndDialog();
            return;
        }
        final byte[] replayData = pendingReplays.remove(0);
        // 对齐 gframe duelclient.cpp STOC_REPLAY：勾选自动保存录像时不弹窗，直接以录像开始时间命名自动保存（对应提示 1367）
        if (AppsSettings.get().getIntSettings("chkAutoSaveReplay", 0) == 1) {
            saveReplayFile(replayData, getReplayDefaultName(replayData), true);
            activity.mainHandler.post(this::processPendingReplays);
            return;
        }
        replaySaveDialog = new ReplaySaveDialog(activity);
        replaySaveDialog.setDefaultName(getReplayDefaultName(replayData))
                .setCenterInView(activity.layoutGameRight)
                .setOnReplayActionListener(new ReplaySaveDialog.OnReplayActionListener() {
                    @Override
                    public void onSave(String fileName) {
                        saveReplayFile(replayData, fileName, false);
                        activity.mainHandler.post(() -> processPendingReplays());
                    }

                    @Override
                    public void onCancel() {
                        // 对齐 gframe duelclient.cpp STOC_REPLAY L1080-1082：点「否」(actionParam==0) 时仍将最近一局静默保存为 _LastReplay.yrp
                        saveLastReplay(replayData);
                        // 继续检查通讯是否还发来了其它录像文件
                        activity.mainHandler.post(() -> processPendingReplays());
                    }
                });
        replaySaveDialog.show();
    }

    /** 从通讯发来的录像数据中解析默认文件名，与 gframe duelclient.cpp STOC_REPLAY 一致：录像开始时间 %Y-%m-%d %H-%M-%S */
    private String getReplayDefaultName(byte[] data) {
        try {
            if (data == null || data.length < 24) return "_LastReplay";
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int id = buf.getInt();
            if (id != ReplayReader.REPLAY_ID_YRP1 && id != ReplayReader.REPLAY_ID_YRP2) {
                return "_LastReplay";
            }
            buf.getInt(); // version
            int flag = buf.getInt();
            int seed = buf.getInt();
            buf.getInt(); // datasize
            int startTime = buf.getInt();
            long ts = ((flag & ReplayReader.REPLAY_UNIFORM) != 0)
                    ? Integer.toUnsignedLong(startTime)
                    : Integer.toUnsignedLong(seed);
            return new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US)
                    .format(new Date(ts * 1000L));
        } catch (Exception e) {
            return "_LastReplay";
        }
    }

    private void saveReplayFile(byte[] data, String fileName, boolean autoSave) {
        String safeName = sanitizeReplayName(fileName);
        try {
            File dir = new File(AppsSettings.get().getReplayDir());
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, safeName + Constants.YRP_FILE_EX);
            FileOutputStream fos = new FileOutputStream(file);
            try {
                fos.write(data);
                fos.flush();
            } finally {
                fos.close();
            }
            Log.i(TAG, "Replay saved: " + file.getAbsolutePath());
            if (autoSave) {
                // 对齐 gframe 自動保存提示（系统字符串 1367「リプレイ自動保存 %ls.yrp」）：将 %ls 占位替换为实际保存的录像文件名
                String template = activity.mStringManager
                        .getSystemString(1367, "リプレイ自動保存 %ls.yrp");
                Toast.makeText(activity, template.replace("%ls", safeName), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, activity.mStringManager
                        .getSystemString(1335, "保存成功"), Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to save replay", e);
            Toast.makeText(activity, "录像保存失败: " + safeName, Toast.LENGTH_SHORT).show();
            // 对齐 gframe duelclient.cpp STOC_REPLAY L1078-1079：保存失败时兜底存为 _LastReplay.yrp
            saveLastReplay(data);
        }
    }

    /** 静默保存最近一局为 _LastReplay.yrp（不弹任何提示）：对应 gframe duelclient.cpp STOC_REPLAY 中点「否」与保存失败两条 new_replay.SaveReplay(L"_LastReplay") 路径 */
    private void saveLastReplay(byte[] data) {
        try {
            File dir = new File(AppsSettings.get().getReplayDir());
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "_LastReplay" + Constants.YRP_FILE_EX);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
                fos.flush();
            }
            Log.i(TAG, "Last replay saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to save last replay", e);
        }
    }

    private String sanitizeReplayName(String name) {
        String n = (name == null) ? "" : name.trim();
        if (n.toLowerCase(Locale.US).endsWith(Constants.YRP_FILE_EX)) {
            n = n.substring(0, n.length() - Constants.YRP_FILE_EX.length());
        }
        n = n.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        if (n.isEmpty()) n = "_LastReplay";
        return n;
    }

    /** returnToLanMain：断线/决斗结束返回时清空待处理录像与相关标志 */
    void resetDuelEndState() {
        duelEndHandling = false;
        duelEndedAsSpectator = false;
        animSettleDeadlineMs = 0L;
        pendingReplays.clear();
        if (replaySaveDialog != null) {
            replaySaveDialog.dismiss();
            replaySaveDialog = null;
        }
    }

    /** onDestroy：取消尚未触发的录像处理调度 */
    void cancelReplayProcessing() {
        activity.mainHandler.removeCallbacks(duelEndReplayProcessor);
    }
}
