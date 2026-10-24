package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Middle layer of the IME popup adjuster chain: the settle/tick
 * scheduling loop and the per-entry resolution engine (step,
 * stepMinimize, stepLift, stepIdle) plus entry unregistration,
 * base capture and window rect apply/restore-to-base涓嬪彂.
 */
abstract class ImePopupAdjusterMid extends ImePopupAdjusterBase {

    /** 状态切换后的首轮解算任务：{@link #schedule(long)} 的延时即键盘动画等待时间，到点直接进 tick 循环 */
    protected static final Runnable SETTLE = () -> startTicks(0L);
    protected static final Runnable TICK = ImePopupAdjuster::tick;
    /**
     * 条目注销：弹窗真正关闭（含抬升中被应答后关闭）时把内容布局参数还原给下次复用——视图树
     * 被 dialog 反复复用，残留的贴底重力会让下次显示错位；同时把弹窗自身宽高还原（抬升下发过
     * 像素高度，不复位则重显时窗口停在收缩态尺寸），最后摘除本条目挂的 attach 监听，避免
     * 反复显示/关闭在同一内容视图上堆叠监听（旧监听会按过期快照覆盖新条目的布局）。
     */
    protected static void unregister(Entry entry, PopupWindow live) {
        restoreContent(entry);
        entry.minimizedForIme = false;
        entry.userExpandedFlag = false;
        entry.softFrozen = false;
        if (live != null) {
            DraggablePopupHelper.expandPopupFromIme(live);
        }
        if (live != null && entry.baseSaved) {
            live.setWidth(entry.savedWidth);
            live.setHeight(entry.savedHeight);
        }
        entry.lifted = false;
        entry.baseSaved = false;
        View content = entry.contentRef.get();
        if (content != null && entry.attachListener != null) {
            content.removeOnAttachStateChangeListener(entry.attachListener);
            entry.attachListener = null;
        }
        ENTRIES.remove(entry);
    }
    protected static void setWantLift(boolean lift) {
        for (Entry e : ENTRIES) {
            e.wantLift = lift;
            if (lift) {
                // 新一轮键盘抬起：重置「用户手动展开」豁免、横条锚定重试计数与重建限额
                e.userExpandedFlag = false;
                e.barTries = 0;
                e.rebuiltOnce = false;
            }
        }
    }

    protected static void schedule(long delay) {
        MAIN.removeCallbacks(SETTLE);
        MAIN.removeCallbacks(TICK);
        sIdleTicks = 0;
        MAIN.postDelayed(SETTLE, delay);
    }

    protected static void startTicks(long delay) {
        MAIN.removeCallbacks(TICK);
        MAIN.postDelayed(TICK, delay);
    }

    /** 一轮解算：仍有纠正则继续下一轮，连续 {@link #MAX_IDLE_TICKS} 轮无纠正即停 */
    protected static void tick() {
        int chatTop = measureChatTop();
        // 落定判定：连续多轮实测输入框上沿一致 = 键盘动画已结束（用户要求：拿到
        // 最终高度后再移动，不随动画过程持续变化）；实测变动轮直接继续解算，
        // 不受无纠正轮数上限提前停轮
        if (chatTop == sStableChatTop) {
            if (sStableCount < CHAT_STABLE_TICKS) sStableCount++;
        } else {
            sStableChatTop = chatTop;
            sStableCount = 0;
        }
        sChatStable = sStableCount >= CHAT_STABLE_TICKS;
        // 抬高状态机（玩家等待弹窗判据）：只在输入框落定后翻转，不参考输入法事件——
        // 抬高后不管输入法是否仍显示都保持贴输入框上沿，落定降回基准高度才回位（用户规格）；
        // 静置态重采基准使旋转/HUD 缩放后的新静置位自动跟上，动画途中的中间值一律不污染基准
        boolean liftedNow = chatTop > 0 && sChatRestTop > 0
                && chatTop < sChatRestTop - sChatLiftTriggerPx;
        if (sChatStable && chatTop > 0) {
            if (sChatLiftState) {
                if (!liftedNow) sChatLiftState = false;
            } else {
                sChatRestTop = chatTop;
                if (liftedNow) sChatLiftState = true;
            }
        }
        // 「被抬高」= 落定态锁存结果：不论输入法是否显示，只看几何
        boolean chatLifted = sChatLiftState;
        boolean corrected = false;
        for (Entry e : new ArrayList<>(ENTRIES)) {
            // 全部弹窗统一几何驱动（用户规格：duel dialog 只在聊天输入框真被抬高时才
            // 最小化/抬升，输入法直接覆盖界面而未抬高输入框时不做处理），不再受显隐
            // 事件翻转影响；基准未建立前退回旧 IME 事件态
            e.wantLift = sChatRestTop > 0 ? chatLifted : sImeUp;
            if (step(e, chatTop)) corrected = true;
        }
        prune();
        if (corrected) {
            sIdleTicks = 0;
        } else {
            sIdleTicks++;
        }
        if (!sChatStable || (sIdleTicks < MAX_IDLE_TICKS && hasWork())) {
            MAIN.removeCallbacks(TICK);
            MAIN.postDelayed(TICK, TICK_MS);
        }
    }

    /** 是否仍有待办：期望态与实际态不一致、基准未采集、还原修复与补做锚定未收敛 */
    protected static boolean hasWork() {
        for (Entry e : ENTRIES) {
            if (!e.baseSaved) return true;
            if (e.wantLift != e.lifted) return true;
            if (e.reanchorPasses > 0) return true;
            if (e.liftedEver && e.repairTries < MAX_REPAIR_TRIES) return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 单条目解算
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 解算单个弹窗一轮：几何一律实时测量（窗口与输入框都在随键盘动画变动），实测与目标
     * 不符即下发纠正。返回 true 表示本轮做了下发（需要再来一轮核对实际生效结果）。
     */
    protected static boolean step(Entry entry, int chatTop) {
        PopupWindow popup = entry.popup();
        if (popup == null || !popup.isShowing()) return false;
        View window = windowViewOf(popup);
        WindowManager.LayoutParams p = window == null ? null : paramsOf(window);
        if (p == null || window == null || window.getHeight() <= 0) return false;
        int[] loc = new int[2];
        window.getLocationOnScreen(loc);
        final int top = loc[1];
        final int height = window.getHeight();

        // 基准未采集：此刻弹窗处于干净静置态（抬升只在基准采集之后发生），采一次后冻结
        if (!entry.baseSaved) {
            captureBase(entry, window, p);
            return false;
        }

        // dialog 自己（非本工具）手动收缩成横条的：包装层弹窗抬升时同样接管——横条随键盘
        // 逐轮重锚、窗口底边同步收缩（收起后保持收缩态贴回输入框上方，用户规格）；
        // 旧的整 dialog 抬升路径不介入收缩态，避免把隐藏中的内容重新撑大
        boolean dialogCollapsed = DraggablePopupHelper.isPopupCollapsed(popup)
                && !DraggablePopupHelper.isPopupAutoCollapsed(popup);
        // 自动最小化期间用户点横条手动展开：尊重用户，标记豁免并解除窗口冻结
        if (entry.minimizedForIme && !DraggablePopupHelper.isPopupAutoCollapsed(popup)
                && !dialogCollapsed) {
            entry.minimizedForIme = false;
            entry.userExpandedFlag = true;
            releaseImeMode(entry, window, p);
        }

        // 铺满窗口的包装层弹窗（绝大多数 dialog）：抬升自动最小化为底部横条（已手动收缩的
        // 只接管横条重锚，collapseForIme 幂等），横条底边贴抬高后的输入框上沿，并把窗口
        // 底边同步收缩到目标线——铺满全屏的弹窗盖住键盘区会吞掉输入法触摸（真机结论，
        // 见类头注释）。仅聊天输入框持有焦点时介入：宣言卡等自带搜索框弹窗自己弹键盘时
        // （chat 无焦点）不最小化也不收缩，避免打断其自身输入。
        boolean chatFocused = sChatInput != null && sChatInput.get() != null
                && sChatInput.get().hasFocus();
        // 玩家等待弹窗（followChatLift）：登记起一律持续冻结本窗口的 IME 自适应，
        // 使抬升/还原只由本类下发——ADJUST_RESIZE 下 WMS 会同步收缩铺满窗口的弹窗，
        // 与还原下发构成两个互相拉扯的定位控制器（快速上下闪动的根源）；本弹窗
        // 自身无输入框，冻结不影响任何输入路径（用户规格：只看输入框几何，不管输入法）
        if (entry.followChatLift) {
            freezeImeMode(entry, window, p);
        }
        // 含 EditText 弹窗：聊天输入框持焦（聊天联动抬键盘）期间同样冻结——键盘动画
        // 途中 WMS 的连续 resize 会把弹窗跟着键盘一点点爬动（用户要求只在输入框
        // 落定后一次性移动）；焦点转到本弹窗输入框后立即解除，恢复 ADJUST_RESIZE
        // 原生输入路径
        if (entry.hasEditText) {
            if (chatFocused) freezeImeMode(entry, window, p);
            else releaseImeMode(entry, window, p);
        }
        // 含 EditText 弹窗且聊天未持焦（键盘由本弹窗自己的输入框唤起）：完全
        // 不介入，保留其原生 ADJUST_RESIZE 输入路径；聊天持焦抬键盘时才走整 dialog
        // 贴边抬高（不缩小、不最小化，用户规格）
        if (entry.hasEditText && entry.wantLift && !chatFocused) return false;
        // 动画途中不下发（用户规格：拿到聊天输入框最终高度后再移动）：玩家等待与
        // 含 EditText 弹窗都待输入框上沿落定后一次性移到/回位；其余弹窗维持逐轮跟随
        if ((entry.followChatLift || entry.hasEditText) && !sChatStable) return false;
        if (entry.wantLift && chatTop > 0 && !entry.liftMode && !entry.noAutoMinimize
                && !entry.userExpandedFlag) {
            return chatFocused
                    ? stepMinimize(entry, popup, window, p, top, height, chatTop)
                    : false;
        }

        boolean lift = entry.wantLift && chatTop > 0 && !dialogCollapsed
                && !entry.userExpandedFlag;
        if (lift) {
            return stepLift(entry, popup, window, top, height, chatTop);
        }
        return stepIdle(entry, popup, window, p, top, height);
    }

    /**
     * 自动最小化解算：目标线 = {@code 输入框上沿 − 安全间隙}。两件事同步下发：
     * ①收缩 dialog 为底部横条并把横条底边贴目标线（只改包装层子视图 margin，幂等重发）；
     * ②把弹窗窗口底边收缩到目标线（applyWindowRect，与旧收缩路径同一套幂等反推）——
     * 窗口不缩会盖住键盘区吞掉输入法触摸（真机结论）。收敛判定同时核对横条底边与
     * 窗口底边两条实测线，Z 字形键盘动画期间逐轮跟随。内容布局全程不动，还原时
     * 只需一步展开 + 一次窗口撑回（撑不回再走重建 fallback）。
     */
    protected static boolean stepMinimize(Entry entry, PopupWindow popup, View window,
                                        WindowManager.LayoutParams p, int top, int height,
                                        int chatTop) {
        freezeImeMode(entry, window, p);
        final int wantBottom = chatTop - sLiftSafetyPx;
        int wantH = wantBottom - entry.baseTop;
        if (wantH > entry.baseH) wantH = entry.baseH;
        if (wantH < 1) wantH = 1;
        View bar = DraggablePopupHelper.getCollapseBarView(popup);
        boolean barOk = false;
        if (bar != null && bar.isShown() && bar.getHeight() > 0) {
            int[] bLoc = new int[2];
            bar.getLocationOnScreen(bLoc);
            barOk = Math.abs(bLoc[1] + bar.getHeight() - wantBottom) <= LIFT_GAP_TOL_PX;
        }
        boolean winOk = Math.abs(top + height - wantBottom) <= LIFT_GAP_TOL_PX;
        if (barOk && winOk) {
            entry.barTries = 0;
            entry.lifted = true;
            entry.liftedEver = true;
            return false;
        }
        if (entry.barTries >= MAX_LIFT_TRIES) {
            entry.lifted = true;
            entry.liftedEver = true;
            return false;
        }
        entry.barTries++;
        if (!DraggablePopupHelper.collapsePopupForIme(popup, wantBottom)) {
            // 弹窗结构无法装横条（理论不应发生）：退回旧的收缩窗口+内容贴底路径作为 fallback
            entry.minimizedForIme = false;
            return stepLift(entry, popup, window, top, height, chatTop);
        }
        entry.minimizedForIme = true;
        if (!winOk && (entry.lastWantTop != entry.baseTop || entry.lastWantH != wantH)) {
            // 目标与上一轮一致却仍未收敛：该 ROM 不认尺寸下发，不重复空转，继续只重锚横条
            entry.lastWantTop = entry.baseTop;
            entry.lastWantH = wantH;
            applyWindowRect(entry, popup, entry.baseTop, wantH);
        }
        entry.lifted = true;
        entry.liftedEver = true;
        return true;
    }

    /**
     * 抬升解算（收缩窗口与整窗上移共用一套「幂等目标 + 遮挡微调」）：目标底边取
     * {@code 输入框上沿 − 安全间隙}，目标矩形一律由<em>冻结的基准</em>反推而不是在上一轮
     * 实测值上累加，故重复下发不会累积误差；实测底边仍低于输入框上沿（真遮挡）时按超出量
     * 再收缩一次，直到实测不低于 chatTop 才停手——即用户要求的「下边缘与输入框上边缘紧贴、
     * 不要有一点遮挡」。
     */
    protected static boolean stepLift(Entry entry, PopupWindow popup, View window,
                                    int top, int height, int chatTop) {
        if (!entry.lifted) {
            entry.liftTries = 0;
            entry.lastWantTop = Integer.MIN_VALUE;
            entry.lastWantH = Integer.MIN_VALUE;
        }
        if (entry.liftTries >= MAX_LIFT_TRIES) {
            // 个别 ROM 不认窗口尺寸下发：收敛不了就停手，保持抬升态等下一次状态翻转再试
            entry.lifted = true;
            entry.liftedEver = true;
            return false;
        }
        final int wantBottom = chatTop - sLiftSafetyPx;
        // 参与判定的底边：收缩模式取「窗口底」与「内容可视底」中更低的一条（贴底生效前内容
        // 仍居中原位而低于窗口底），整窗上移模式内容随窗移动只取窗口底
        int bottom = top + height;
        if (!entry.liftMode) bottom = Math.max(bottom, measureLiftBottom(entry, window));
        if (bottom <= chatTop) {
            // 实测底边已在输入框上沿之上：零遮挡达成。从未下发过的（本就高悬在输入框上方的
            // 自定位弹窗）保持未抬升态，还原时不必撑回；已抬升过的就地收敛（再撑高就会重新
            // 压住输入框），也不为「间隙略大」去追加撑回——抬升期间的撑回不可靠且会再次遮挡
            entry.liftTries = 0;
            entry.lifted = entry.liftedEver;
            return false;
        }
        // 需要把上面那条判定底边向上搬的量：目标线（输入框上沿 − 安全间隙）之下的位移一律
        // 不发放，故收缩量直接由实测底边与目标线之差得出——与「窗口底正好落在目标线」等价，
        // 且重复解算不会在上一轮结果上叠扣（早先写成先按目标算高、再减一次遮挡量，会把窗口
        // 多缩一截，表现为对话框与输入框之间出现明显空隙）
        final int shrink = bottom - wantBottom;
        int wantTop;
        int wantH;
        if (entry.liftMode) {
            // 整窗上移：尺寸不动，整窗上移 shrink 使窗口底落到目标线，内容不被裁剪
            wantH = height;
            wantTop = top - shrink;
        } else {
            // 收缩窗口：窗口顶保持基准顶缘，窗口底缩到目标线，内容（对话框根）贴窗口底
            applyContentLift(entry);
            wantH = entry.baseH - shrink;
            wantTop = entry.baseTop;
        }
        if (wantH > entry.baseH) wantH = entry.baseH;
        if (wantH < 1) wantH = 1;
        if (wantTop < 0) wantTop = 0;
        entry.liftTries++;
        if (wantTop == entry.lastWantTop && wantH == entry.lastWantH) {
            // 目标与上一轮完全一致却仍未收敛：说明窗口尺寸没按请求改变，重复下发不会有用，
            // 由 liftTries 上限收尾（保持抬升态，等下一次状态翻转重新起算）
            entry.lifted = true;
            entry.liftedEver = true;
            return false;
        }
        entry.lastWantTop = wantTop;
        entry.lastWantH = wantH;
        applyWindowRect(entry, popup, wantTop, wantH);
        entry.lifted = true;
        entry.liftedEver = true;
        return true;
    }

    /**
     * 静置解算：抬升过又解除的条目先回到基准矩形与基准内容布局，其后若干轮按实测核对——
     * 窗口未撑回、内容仍带贴底布局、区域锚定未补做都再次修复。
     * <p>
     * <b>撑回窗口不能只靠参数下发</b>：真机结论（原 PlayerWaitingDialog 私有实现已验证）是
     * {@code PopupWindow.update} 能把窗口<em>缩小</em>、却不能把它<em>撑回</em>原尺寸，参数
     * 直写 + {@code updateViewLayout} 同样可能被忽略，于是弹窗停在收缩态——表现为输入法收起后
     * 弹窗跟着落到聊天输入框上方而不回区域中央（用户反馈的问题）。故第一轮仍走参数下发核对，
     * 第二轮起直接以登记的原始参数重建窗口（{@link #rebuildWindow}），并连续补做区域锚定。
     * <p>
     * 核对收敛（窗口与内容都实测回到基准）后才作废基准重采，使随后的旋转能把新屏幕尺寸纳入
     * 基准；收敛前绝不重采，否则收缩中的临时尺寸会被当成「原位」，弹窗再也回不了中央。
     */
    protected static boolean stepIdle(Entry entry, PopupWindow popup, View window,
                                    WindowManager.LayoutParams p, int top, int height) {
        // 自动最小化的还原：一步展开横条→内容回到原区域锚定位（内容布局收缩期间从未被改），
        // 再把收缩过的窗口底边按基准矩形一次下发撑回；后续轮实测核对，撑不回时
        // 自动进入下方「重建窗口 + 补锚」的旧 fallback 路径
        if (entry.minimizedForIme || entry.userExpandedFlag) {
            entry.minimizedForIme = false;
            entry.userExpandedFlag = false;
            entry.barTries = 0;
            DraggablePopupHelper.expandPopupFromIme(popup);
            // 还原期保持 ADJUST_NOTHING 冻结：此刻键盘尚未完全降回，若提前解回
            // ADJUST_RESIZE，WMS 会按残留键盘高度再次压缩刚撑回的窗口，与撑回下发互相
            // 拉扯（横屏还原快速显隐闪烁的根源之一）；冻结的解除移到实测收敛/修复收尾处
            entry.lifted = false;
            entry.liftTries = 0;
            entry.lastWantTop = Integer.MIN_VALUE;
            entry.lastWantH = Integer.MIN_VALUE;
            if (Math.abs(height - entry.baseH) > VERIFY_TOL_PX
                    || Math.abs(top - entry.baseTop) > VERIFY_TOL_PX) {
                restoreToBase(entry, popup, window, p, top, height);
                entry.reanchorPasses = Math.max(entry.reanchorPasses, RESTORE_REANCHOR_PASSES);
            }
            return true;
        }
        if (entry.lifted) {
            restoreToBase(entry, popup, window, p, top, height);
            entry.lifted = false;
            entry.liftTries = 0;
            entry.lastWantTop = Integer.MIN_VALUE;
            entry.lastWantH = Integer.MIN_VALUE;
            entry.reanchorPasses = RESTORE_REANCHOR_PASSES;
            return true;
        }
        if (!entry.liftedEver) {
            // 从未抬升过但期间被冻结过：含 EditText 弹窗在此解除；followChatLift 弹窗
            // 永久保持冻结（位置只由本类几何状态机下发，解除会把窗口交回 WMS 二次 resize）
            if (!sImeUp && !entry.followChatLift) releaseImeMode(entry, window, p);
            return false;
        }

        boolean winOk = Math.abs(height - entry.baseH) <= VERIFY_TOL_PX
                && Math.abs(top - entry.baseTop) <= VERIFY_TOL_PX;
        boolean contentOk = !contentNeedsRestore(entry);
        if (winOk && contentOk && entry.reanchorPasses == 0) {
            // 实测确认已回到基准：作废基准重采，使随后的旋转能把新屏幕尺寸纳入基准；
            // 收敛前绝不重采，否则收缩中的临时尺寸会被当成「原位」。followChatLift 弹窗
            // 永不解除冻结（回到弹窗自身模式会再引入 WMS 二次 resize 拉扯，闪动根源）
            if (!entry.followChatLift) {
                releaseImeMode(entry, window, p);
            }
            entry.liftedEver = false;
            entry.repairTries = 0;
            entry.rebuiltOnce = false;
            entry.baseSaved = false;
            return false;
        }
        if (entry.repairTries >= MAX_REPAIR_TRIES) {
            // 修复到上限仍不收敛（该 ROM 拒绝把窗口撑回）：接受当前几何并停手，
            // 但<em>保留</em>原基准——收缩态绝不能被当成基准，否则下一次抬升/还原都以偏矮的
            // 窗口为准；followChatLift 弹窗永不解除冻结
            if (!entry.followChatLift) {
                releaseImeMode(entry, window, p);
            }
            entry.liftedEver = false;
            entry.repairTries = 0;
            entry.reanchorPasses = 0;
            entry.rebuiltOnce = false;
            return false;
        }
        entry.repairTries++;
        boolean corrected = false;
        if (!winOk) {
            if (entry.repairTries == 1) {
                // 先按登记的原始参数再下发一次「像素撑回 + 参数直写」（与收缩同一条路径）
                restoreToBase(entry, popup, window, p, top, height);
            } else if (!entry.rebuiltOnce) {
                // 参数下发没能把窗口撑回：以原始参数重建窗口（不经 dismiss，不触发各 dialog
                // 的应答语义），这是真机验证过的唯一能把窗口恢复成 MATCH_PARENT 的手段。
                // 重建每做一次窗口就消失/重现一次（用户反馈的横屏还原快速显隐闪烁），故每个
                // 还原周期至多一次，此后只补锚等待收敛或走上限收尾
                entry.rebuiltOnce = true;
                rebuildWindow(entry, window, p);
            }
            entry.reanchorPasses = Math.max(entry.reanchorPasses, RESTORE_REANCHOR_PASSES);
            corrected = true;
        }
        if (!contentOk) {
            // 内容仍带贴底重力/被清零的纵向 margin：强制写回基准布局（不等判定不一致才动）
            restoreContent(entry);
            corrected = true;
        }
        if (entry.reanchorPasses > 0) {
            entry.reanchorPasses--;
            DraggablePopupHelper.reanchorPopup(popup);
            corrected = true;
        }
        return corrected;
    }
    /** 采集基准几何：窗口实测矩形、窗口参数原值与内容布局快照（只在干净静置态调用） */
    protected static void captureBase(Entry entry, View window, WindowManager.LayoutParams p) {
        int[] loc = new int[2];
        window.getLocationOnScreen(loc);
        entry.baseTop = loc[1];
        entry.baseH = window.getHeight();
        entry.savedGravity = p.gravity;
        entry.savedX = p.x;
        entry.savedY = p.y;
        entry.savedWidth = p.width;
        entry.savedHeight = p.height;
        saveContentSnapshot(entry);
        entry.baseSaved = true;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 窗口矩形下发
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 把窗口矩形改为「顶缘 wantTop、高 wantH」，偏移量由<em>登记时冻结的基准</em>幂等反推：
     * 顶对齐时 y 与顶缘同向平移；垂直居中时改高度会连带顶缘位移，需反向补偿一半；底对齐时
     * 改高度全部作用在顶缘，y 反向补偿整段差值。一律从 {@code savedY/baseTop/baseH} 出发而
     * 不在上一轮的实时值上累加，故同一目标重复下发结果恒定，某轮下发被系统改写也不会把
     * 误差永久累积进窗口位置。
     */
    protected static void applyWindowRect(Entry entry, PopupWindow popup, int wantTop, int wantH) {
        int vert = entry.savedGravity & Gravity.VERTICAL_GRAVITY_MASK;
        float k = vert == Gravity.CENTER_VERTICAL ? 0.5f : (vert == Gravity.BOTTOM ? 1f : 0f);
        int y = entry.savedY + (wantTop - entry.baseTop) + Math.round(k * (wantH - entry.baseH));
        // 宽度形参传 -1（update 语义为“不改宽”）：抬升与还原都只动高度与纵向偏移
        try {
            popup.update(entry.savedX, y, -1, wantH, true);
        } catch (Exception ignored) {
            // 窗口 token 失效（Activity 正在销毁）时忽略，条目会随视图 detach 清理
        }
    }

    /**
     * 还原到基准：先按登记时实测到的像素高把窗口撑回原尺寸，再把登记时保存的窗口参数原样
     * 写回窗口并复位 {@code PopupWindow} 自身的宽高——后一步使窗口重新以 {@code MATCH_PARENT}
     * 承载，旋转与弹窗复用重显才不会卡在收缩时的旧尺寸；内容布局强制写回基准快照。
     * 本方法只下发、不核对：窗口能否真的撑回由调用方在下一轮按实测判定（撑不回则重建窗口）。
     */
    protected static void restoreToBase(Entry entry, PopupWindow popup, View window,
                                      WindowManager.LayoutParams p, int top, int height) {
        applyWindowRect(entry, popup, entry.baseTop, entry.baseH);
        p.gravity = entry.savedGravity;
        p.width = entry.savedWidth;
        p.height = entry.savedHeight;
        p.x = entry.savedX;
        p.y = entry.savedY;
        updateViewLayout(window, p);
        popup.setWidth(entry.savedWidth);
        popup.setHeight(entry.savedHeight);
        restoreContent(entry);
    }
}
