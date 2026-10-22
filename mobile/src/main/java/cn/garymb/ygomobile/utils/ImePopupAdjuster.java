package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 输入法抬升联动工具（用户规格：决斗中出现的对话框——先后攻、是/否询问、宣言类、
 * 位置选择、卡片选择/显示、关键词列表、决斗记录、录像保存、玩家等待等——显示期间点击
 * 聊天输入框抬高输入框与输入法时，对话框底边要紧贴输入框上沿，双方互不遮挡；输入法收起
 * 后回到 layout_game_right 中央，横竖屏及横竖屏切换后一律回中央）。
 * <p>
 * 原为 {@code PlayerWaitingDialog} 私有的 repositionForIme/restoreFromIme 实现，现抽取为
 * 本工具类供所有相关 dialog 调用：dialog 只需在显示成功后 {@link #attach(PopupWindow)}，
 * 其余由 Activity 把聊天输入框的输入法显隐事件转发给
 * {@link #notifyImeShown(View)} / {@link #notifyImeHidden()} 统一驱动，登记表在弹窗窗口
 * 销毁时自动注销，dialog 自身无需处理还原。
 * <p>
 * <b>解算一律「测量—比对—纠正」循环</b>（{@link #TICK_MS} 一轮）：键盘升降是带动画的连续
 * resize，单次延后解算取到的输入框上沿可能滞后于动画终值（表现为弹窗仍遮挡输入框一点），
 * 还原下发时窗口也可能尚未回到原尺寸（表现为输入法收起后弹窗停在抬升位）。故每次状态切换
 * 后连续解算若干轮：每轮<em>实测</em>当前底边与输入框上沿的差值并按差值改正目标矩形，
 * 直到实测与目标一致——不依赖任何一次测量的绝对正确，也不依赖 gravity/窗口尺寸如何被系统
 * 重排，因而抬升后严格「底边贴输入框上沿、零遮挡」，还原后严格回到登记时的基准矩形。
 * <p>
 * <b>基准几何只在干净状态下采集一次</b>：抬升期间与还原在途时窗口尺寸/内容布局都是临时值，
 * 若把它们当作基准反复刷新，还原目标会被污染成「收缩态」，弹窗就再也回不到区域中央
 * （上一版用户反馈的问题）。故 {@code baseSaved} 一旦采集便冻结，只有还原被实测确认收敛后
 * 才作废重采（重采用于吸收旋转后的新屏幕尺寸），修复尝试另有上限防止永远收敛不了时空转。
 * <p>
 * 两种抬升方式按弹窗结构自动选择：
 * <ul>
 *   <li><b>收缩窗口 + 内容贴底</b>（{@link DraggablePopupHelper#setupDraggablePopup} 包装成
 *   铺满窗口的弹窗，绝大多数 dialog）：窗口顶保持原位、窗口底缩到「输入框上沿 − 安全间隙」，
 *   内容（包装层的第一个子 view，即区域锚定 margin 所在的那个 view）重力改为底部对齐并清零
 *   纵向 margin，于是内容底边正好落在输入框上沿之上。必须真的缩小窗口而不只是移动内容：
 *   全窗口铺满时弹窗窗口会盖住软键盘，键盘按键的触摸落在弹窗窗口上而到不了输入法。内容高
 *   装不进收缩后的窗口时上沿被窗口顶裁掉（与 PlayerWaitingDialog 原实现一致）。</li>
 *   <li><b>整窗上移</b>（先后攻/位置选择这类以 {@code showAtLocation} 绝对坐标定位、自带尺寸的
 *   弹窗）：尺寸不动，整窗上移「弹窗底边 − 输入框上沿」的差值，使底边贴输入框上沿，且不裁剪
 *   内容；本就位于输入框上方则不抬升。本方式直接改写窗口 {@code x/y}，故只适用于绝对坐标定位
 *   的弹窗；以 {@code showAsDropDown} 锚定的弹窗（猜拳 RPSDialog）改 {@code x/y} 会破坏锚点偏移，
 *   它已按锚视图（layout_game_right）尺寸变化自行重算「底边贴输入框上沿」并回到底部中央，
 *   故不登记本工具。</li>
 * </ul>
 * 抬升期间 {@link DraggablePopupHelper} 的区域锚定被本类 {@link #isAdjusted(PopupWindow)}
 * 推迟（否则锚定 margin 会把内容拉回区域中央而与抬升后的输入框重叠）。
 * <p>
 * <b>还原 = 参数下发 + 实测核对 + 必要时重建窗口</b>：真机结论是 {@code PopupWindow.update}
 * 能把窗口缩小、却不能把它撑回原尺寸（原 PlayerWaitingDialog 私有实现据此只能 dismiss 后重显），
 * 参数直写 {@code updateViewLayout} 同样可能被忽略——弹窗因此会停在收缩态、跟着落到聊天输入框
 * 上方而不回区域中央。故本类先按登记时冻结的参数下发（像素撑回 + 原样写回 + 复位
 * {@code PopupWindow} 自身宽高为 {@code MATCH_PARENT}），下一轮<em>实测核对</em>；仍撑不回就
 * 以原始参数重建窗口（{@code removeViewImmediate + addView}，与多层弹窗置顶同手法，不经
 * {@code dismiss}，故不会触发任何 dialog 的应答语义）。最后连续补做区域锚定——补做必须晚于
 * 窗口撑回，否则锚定按收缩中的窗口高度解算 margin 会把内容留在偏下位置。
 */
public final class ImePopupAdjuster {

    private ImePopupAdjuster() {
    }

    /** adjustResize 抬升输入框伴随动画，延后待其上沿稳定再开始解算（与 PlayerWaitingDialog 原实现同值） */
    private static final long SETTLE_DELAY_MS = 220L;

    /** 单轮解算间隔：键盘升降动画期间的两次窗口重排至少隔这么久 */
    private static final long TICK_MS = 60L;

    /** 连续无纠正的最大轮数：达到即认为几何已稳定，停止循环（避免空转） */
    private static final int MAX_IDLE_TICKS = 8;

    /** 抬升底边允许的误差（像素）：底边低于输入框上沿（遮挡）一律纠正，高于则容许此间隙 */
    private static final int LIFT_GAP_TOL_PX = 2;

    /**
     * 抬升时对话框底边相对输入框上沿预留的安全间隙（dp）：卡片背景贴图的投影会向布局盒
     * 之外延伸若干像素，按盒底边正好贴齐输入框上沿在真机上仍会「压住输入框一点」。故目标
     * 底边一律上提这道窄缝——视觉上仍是紧贴，但保证零遮挡（用户规格：不要有一点遮挡）。
     */
    private static final int LIFT_SAFETY_DP = 2;

    /** 还原后核对窗口矩形与基准矩形的允许误差（像素） */
    private static final int VERIFY_TOL_PX = 2;

    /** 还原收敛后补做区域锚定的轮数：窗口撑回与 margin 解算分帧完成，多补几轮确保回到区域中央 */
    private static final int RESTORE_REANCHOR_PASSES = 4;

    /** 静置态最多连续修复次数：超过即接受当前几何并重采基准，杜绝永远收敛不了时空转 */
    private static final int MAX_REPAIR_TRIES = 6;

    /** 抬升态最多连续下发次数：个别 ROM 不认窗口尺寸下发，收敛不了就停手，避免每轮空转 */
    private static final int MAX_LIFT_TRIES = 25;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 已登记的弹窗（弱引用：弹窗与视图销毁后不影响回收，条目在遍历顺带清理） */
    private static final List<Entry> ENTRIES = new ArrayList<>();

    /** 当前判定的输入法态：键盘抬起期间新显示的 dialog 立即按同态抬升，不必等下一次显隐翻转 */
    private static boolean sImeUp = false;
    /** 聊天输入框弱引用：抬升位置以它的实时屏幕上沿为准（旋转重建后由 Activity 重新传入新实例） */
    private static WeakReference<View> sChatInput;

    /** 本轮状态切换已连续无纠正的轮数 */
    private static int sIdleTicks = 0;

    /** {@link #LIFT_SAFETY_DP} 换算成的像素（按登记时所在屏幕密度，attach 与键盘弹起时刷新） */
    private static int sLiftSafetyPx = 0;

    /** 按参照视图的屏幕密度刷新抬升安全间隙 */
    private static void refreshLiftSafety(View reference) {
        if (reference == null || reference.getContext() == null) return;
        float density = reference.getContext().getResources().getDisplayMetrics().density;
        sLiftSafetyPx = Math.max(1, Math.round(LIFT_SAFETY_DP * density));
    }

    /** 状态切换后的首轮解算任务：{@link #schedule(long)} 的延时即键盘动画等待时间，到点直接进 tick 循环 */
    private static final Runnable SETTLE = () -> startTicks(0L);
    private static final Runnable TICK = ImePopupAdjuster::tick;

    /** 单个弹窗的登记项：抬升方式 + 干净状态下采集的基准矩形与内容布局快照 */
    private static class Entry {
        final WeakReference<PopupWindow> popupRef;
        /** true = 自定位弹窗整窗上移（尺寸与内容布局都不改）；false = 铺满窗口的包装层收缩 */
        final boolean liftMode;
        /** 参与抬升的内容根（包装层弹窗为包装层的第一个子 view） */
        final WeakReference<View> contentRef;

        /** 期望态：true = 底边贴输入框上沿；false = 回到基准位（区域中央） */
        boolean wantLift;
        /** 当前实际是否处于抬升态 */
        boolean lifted;
        /** 本次登记周期内是否抬升过：抬升过的条目在静置态才需要核对与修复 */
        boolean liftedEver;

        /** 基准几何是否已采集（采集后冻结，只在还原收敛后作废重采） */
        boolean baseSaved;
        /** 未抬升时窗口的实测矩形（屏幕坐标）与窗口参数原值 */
        int baseTop;
        int baseH;
        int savedGravity;
        int savedX;
        int savedY;
        int savedWidth;
        int savedHeight;

        boolean contentSnapshotSaved;
        int savedContentGravity;
        int savedLeftMargin;
        int savedTopMargin;
        int savedRightMargin;
        int savedBottomMargin;

        /** 还原后剩余补做区域锚定的轮数 */
        int reanchorPasses;
        /** 静置态已用修复次数 */
        int repairTries;
        /** 本轮抬升已连续下发次数（达上限仍未收敛即停手） */
        int liftTries;
        /** 上一次抬升下发的目标矩形：目标由冻结基准幂等反推，与上次一致却仍不收敛
         *  说明该 ROM 没有按请求改窗口尺寸，重复下发无意义 */
        int lastWantTop;
        int lastWantH;
        /** 内容视图上的注销监听（注销时摘除，避免 dialog 反复显示/关闭在同一视图上堆叠监听） */
        View.OnAttachStateChangeListener attachListener;

        Entry(PopupWindow popup, boolean liftMode, View content) {
            this.popupRef = new WeakReference<>(popup);
            this.liftMode = liftMode;
            this.contentRef = new WeakReference<>(content);
        }

        PopupWindow popup() {
            return popupRef.get();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // dialog 侧接口：显示成功后登记
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 登记弹窗参与输入法抬升联动（在 {@code showAtLocation}/{@code showPopup} 之后调用）：
     * 重复登记同一弹窗无副作用；基准几何在弹窗完成布局后的第一个静置轮次采集一次并冻结，
     * 抬升与还原都以它为准（还原期间不重采，避免把收缩中的临时尺寸当成基准）。弹窗窗口
     * 销毁时自动注销并撤销抬升（内容视图会被 dialog 复用，必须还原其布局参数）。
     */
    public static void attach(PopupWindow popup) {
        if (popup == null) return;
        prune();
        for (Entry e : ENTRIES) {
            if (e.popup() == popup) return;
        }
        View content = DraggablePopupHelper.liftedContentView(popup);
        if (content == null) return;
        refreshLiftSafety(content);
        final Entry entry = new Entry(popup,
                !DraggablePopupHelper.isWrappedFullscreenPopup(popup), content);
        entry.wantLift = sImeUp;
        ENTRIES.add(entry);
        entry.attachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                PopupWindow live = entry.popup();
                if (live != null && live.isShowing()) {
                    // 窗口被原地重排（多层弹窗置顶、还原失败时的窗口重建都走 removeView+addView）
                    // 造成的短暂 detach：弹窗仍在显，登记与抬升布局一律原样保留，重排完成后
                    // 由后续 tick 继续按同一条目解算，不做任何还原（否则会打断正在进行的抬升）
                    return;
                }
                unregister(entry, live);
            }
        };
        content.addOnAttachStateChangeListener(entry.attachListener);
        // 键盘已弹起时新显示的 dialog：立即按同态抬升，避免先全屏再跳变
        schedule(0L);
    }

    /**
     * 条目注销：弹窗真正关闭（含抬升中被应答后关闭）时把内容布局参数还原给下次复用——视图树
     * 被 dialog 反复复用，残留的贴底重力会让下次显示错位；同时把弹窗自身宽高还原（抬升下发过
     * 像素高度，不复位则重显时窗口停在收缩态尺寸），最后摘除本条目挂的 attach 监听，避免
     * 反复显示/关闭在同一内容视图上堆叠监听（旧监听会按过期快照覆盖新条目的布局）。
     */
    private static void unregister(Entry entry, PopupWindow live) {
        restoreContent(entry);
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

    /** 主动注销（dialog 主动释放复用弹窗时可选调用；不调用也会随窗口销毁自动注销） */
    public static void detach(PopupWindow popup) {
        if (popup == null) return;
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry e = ENTRIES.get(i);
            if (e.popup() == popup) {
                unregister(e, popup);
            }
        }
    }

    /** 该弹窗当前是否应处于「底边贴输入框上沿」的抬升态（供区域锚定推迟判断） */
    public static boolean isAdjusted(PopupWindow popup) {
        if (popup == null) return false;
        for (Entry e : ENTRIES) {
            if (e.popup() == popup) return e.wantLift;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Activity / 聊天输入框侧接口：输入法显隐驱动
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 输入法抬起：把所有在显且已登记的弹窗抬升到「底边贴聊天输入框上沿」。
     *
     * @param chatInput 当前视图树中的聊天输入框（旋转重建后须传新实例）
     */
    public static void notifyImeShown(View chatInput) {
        sImeUp = true;
        sChatInput = chatInput == null ? null : new WeakReference<>(chatInput);
        refreshLiftSafety(chatInput);
        setWantLift(true);
        // 键盘动画结束后首轮解算，其后由 tick 逐轮纠正到实测底边与输入框上沿一致
        schedule(SETTLE_DELAY_MS);
    }

    /** 输入法收起：所有已抬升的弹窗回到基准矩形（区域居中的重新贴回区域中央） */
    public static void notifyImeHidden() {
        sImeUp = false;
        setWantLift(false);
        schedule(0L);
    }

    /**
     * 还原全部弹窗：屏幕旋转重建视图树时由 Activity 在弹窗重排之前调用——
     * 旋转时系统已收起键盘，用户规格要求横竖屏切换后弹窗一律回到区域中央，
     * 故此刻必须先解除抬升态，后续的旋转重排才不会被锚定推迟守卫挡住。
     */
    public static void restoreAll() {
        setWantLift(false);
        schedule(0L);
    }

    /** 当前输入法态（dialog 需要在显示时判断是否立即抬升时可用） */
    public static boolean isImeUp() {
        return sImeUp;
    }

    private static void setWantLift(boolean lift) {
        for (Entry e : ENTRIES) {
            e.wantLift = lift;
        }
    }

    private static void schedule(long delay) {
        MAIN.removeCallbacks(SETTLE);
        MAIN.removeCallbacks(TICK);
        sIdleTicks = 0;
        MAIN.postDelayed(SETTLE, delay);
    }

    private static void startTicks(long delay) {
        MAIN.removeCallbacks(TICK);
        MAIN.postDelayed(TICK, delay);
    }

    /** 一轮解算：仍有纠正则继续下一轮，连续 {@link #MAX_IDLE_TICKS} 轮无纠正即停 */
    private static void tick() {
        int chatTop = measureChatTop();
        boolean corrected = false;
        for (Entry e : new ArrayList<>(ENTRIES)) {
            if (step(e, chatTop)) corrected = true;
        }
        prune();
        if (corrected) {
            sIdleTicks = 0;
        } else {
            sIdleTicks++;
        }
        if (sIdleTicks < MAX_IDLE_TICKS && hasWork()) {
            MAIN.removeCallbacks(TICK);
            MAIN.postDelayed(TICK, TICK_MS);
        }
    }

    /** 是否仍有待办：期望态与实际态不一致、基准未采集、还原修复与补做锚定未收敛 */
    private static boolean hasWork() {
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
    private static boolean step(Entry entry, int chatTop) {
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

        // 收缩成底部横条的弹窗不参与：横条几何（贴区域底部、与聊天输入框同高同位）由 dialog
        // 自身按布局变化解算并随窗口 resize 自动跟随抬升后的输入框，本工具介入反而会把它推离
        boolean lift = entry.wantLift && chatTop > 0 && !isCollapsed(entry);

        if (lift) {
            return stepLift(entry, popup, window, top, height, chatTop);
        }
        return stepIdle(entry, popup, window, p, top, height);
    }

    /**
     * 抬升解算（收缩窗口与整窗上移共用一套「幂等目标 + 遮挡微调」）：目标底边取
     * {@code 输入框上沿 − 安全间隙}，目标矩形一律由<em>冻结的基准</em>反推而不是在上一轮
     * 实测值上累加，故重复下发不会累积误差；实测底边仍低于输入框上沿（真遮挡）时按超出量
     * 再收缩一次，直到实测不低于 chatTop 才停手——即用户要求的「下边缘与输入框上边缘紧贴、
     * 不要有一点遮挡」。
     */
    private static boolean stepLift(Entry entry, PopupWindow popup, View window,
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
    private static boolean stepIdle(Entry entry, PopupWindow popup, View window,
                                    WindowManager.LayoutParams p, int top, int height) {
        if (entry.lifted) {
            restoreToBase(entry, popup, window, p, top, height);
            entry.lifted = false;
            entry.liftTries = 0;
            entry.lastWantTop = Integer.MIN_VALUE;
            entry.lastWantH = Integer.MIN_VALUE;
            entry.reanchorPasses = RESTORE_REANCHOR_PASSES;
            return true;
        }
        if (!entry.liftedEver) return false;

        boolean winOk = Math.abs(height - entry.baseH) <= VERIFY_TOL_PX
                && Math.abs(top - entry.baseTop) <= VERIFY_TOL_PX;
        boolean contentOk = !contentNeedsRestore(entry);
        if (winOk && contentOk && entry.reanchorPasses == 0) {
            // 实测确认已回到基准：作废基准，下一轮按当前（可能已旋转过的）干净几何重采
            entry.liftedEver = false;
            entry.repairTries = 0;
            entry.baseSaved = false;
            return false;
        }
        if (entry.repairTries >= MAX_REPAIR_TRIES) {
            // 修复到上限仍不收敛（该 ROM 拒绝把窗口撑回）：接受当前几何并停手，但<em>保留</em>
            // 原基准——收缩态绝不能被当成基准，否则下一次抬升/还原都以偏矮的窗口为准
            entry.liftedEver = false;
            entry.repairTries = 0;
            entry.reanchorPasses = 0;
            return false;
        }
        entry.repairTries++;
        boolean corrected = false;
        if (!winOk) {
            if (entry.repairTries == 1) {
                // 先按登记的原始参数再下发一次「像素撑回 + 参数直写」（与收缩同一条路径）
                restoreToBase(entry, popup, window, p, top, height);
            } else {
                // 参数下发没能把窗口撑回：以原始参数重建窗口（不经 dismiss，不触发各 dialog
                // 的应答语义），这是真机验证过的唯一能把窗口恢复成 MATCH_PARENT 的手段
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
    private static void captureBase(Entry entry, View window, WindowManager.LayoutParams p) {
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
    private static void applyWindowRect(Entry entry, PopupWindow popup, int wantTop, int wantH) {
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
    private static void restoreToBase(Entry entry, PopupWindow popup, View window,
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

    private static int windowLocationTop(View window) {
        int[] loc = new int[2];
        window.getLocationOnScreen(loc);
        return loc[1];
    }

    private static void updateViewLayout(View window, WindowManager.LayoutParams p) {
        try {
            Context ctx = window.getContext();
            if (ctx == null) return;
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) wm.updateViewLayout(window, p);
        } catch (Exception ignored) {
            // 窗口已移除/无权限时忽略：像素撑回已生效，视觉位置不受影响
        }
    }

    /**
     * 以登记的原始窗口参数重建弹窗窗口：{@code removeViewImmediate + addView} 让窗口管理
     * 按原始 {@code width/height}（MATCH_PARENT）重新建立窗口，是「update 系列下发都撑不回
     * 原尺寸」时的最后手段。不经 {@code PopupWindow.dismiss}，故不会触发任何 dialog 的
     * OnDismissListener 与应答语义；视图实例原样保留，内容与交互状态不丢失。
     */
    private static void rebuildWindow(Entry entry, View window, WindowManager.LayoutParams p) {
        try {
            Context ctx = window.getContext();
            if (ctx == null) return;
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;
            p.gravity = entry.savedGravity;
            p.width = entry.savedWidth;
            p.height = entry.savedHeight;
            p.x = entry.savedX;
            p.y = entry.savedY;
            int animations = p.windowAnimations;
            // 重排期间抑制进出场动画，避免窗口重建时弹窗闪烁（与 DraggablePopupHelper 置顶同语义）
            p.windowAnimations = 0;
            wm.removeViewImmediate(window);
            wm.addView(window, p);
            p.windowAnimations = animations;
        } catch (Exception ignored) {
            // token 失效或窗口正在销毁时放弃重建，条目随视图销毁自动注销
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 内容布局：抬升时贴底，还原时回到基准重力与 margin
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 收缩模式下把弹窗内容贴到窗口底缘（= 聊天输入框上沿）：纵向重力改 BOTTOM 并清零纵向
     * margin；横向重力与 margin 原样保留，故贴区域左缘的弹窗抬升后仍留在区域内。
     * 已是贴底态时不重复下发（去抖）。
     */
    private static void applyContentLift(Entry entry) {
        View content = entry.contentRef.get();
        if (content == null || !(content.getLayoutParams() instanceof FrameLayout.LayoutParams))
                return;
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) content.getLayoutParams();
        int vertical = flp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (vertical == Gravity.BOTTOM && flp.topMargin == 0 && flp.bottomMargin == 0) return;
        // 改布局之前确保基准快照已在手：条目万一在快照缺失的情况下被抬升，就没有可靠的还原目标
        if (!entry.contentSnapshotSaved) saveContentSnapshot(entry);
        flp.gravity = (flp.gravity & ~Gravity.VERTICAL_GRAVITY_MASK) | Gravity.BOTTOM;
        flp.topMargin = 0;
        flp.bottomMargin = 0;
        content.setLayoutParams(flp);
    }

    /** 内容当前布局是否仍与基准快照不一致（抬升期改过重力/margin 即为不一致） */
    private static boolean contentNeedsRestore(Entry entry) {
        View content = entry.contentRef.get();
        if (content == null || !(content.getLayoutParams() instanceof FrameLayout.LayoutParams))
                return false;
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) content.getLayoutParams();
        return flp.gravity != entry.savedContentGravity
                || flp.leftMargin != entry.savedLeftMargin
                || flp.topMargin != entry.savedTopMargin
                || flp.rightMargin != entry.savedRightMargin
                || flp.bottomMargin != entry.savedBottomMargin;
    }

    /**
     * 保存内容布局基准快照（与窗口矩形同生命周期）。已有快照时<em>拒绝</em>用贴底态覆盖：
     * 「BOTTOM 重力 + 清零纵向 margin」是抬升的临时布局，一旦把它当成基准，还原就会把弹窗
     * 永久钉在输入框上方而回不到区域中央。
     */
    private static void saveContentSnapshot(Entry entry) {
        View content = entry.contentRef.get();
        if (content == null || !(content.getLayoutParams() instanceof FrameLayout.LayoutParams))
                return;
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) content.getLayoutParams();
        boolean liftedLayout = (flp.gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM
                && flp.topMargin == 0 && flp.bottomMargin == 0;
        if (liftedLayout && entry.contentSnapshotSaved) return;
        entry.savedContentGravity = flp.gravity;
        entry.savedLeftMargin = flp.leftMargin;
        entry.savedTopMargin = flp.topMargin;
        entry.savedRightMargin = flp.rightMargin;
        entry.savedBottomMargin = flp.bottomMargin;
        entry.contentSnapshotSaved = true;
    }

    /** 把内容布局写回基准快照（无条件下发：还原不依赖比对结果，避免判定失真时残留贴底态） */
    private static void restoreContent(Entry entry) {
        if (!entry.contentSnapshotSaved) return;
        View content = entry.contentRef.get();
        if (content == null) return;
        if (!(content.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) content.getLayoutParams();
        flp.gravity = entry.savedContentGravity;
        flp.leftMargin = entry.savedLeftMargin;
        flp.topMargin = entry.savedTopMargin;
        flp.rightMargin = entry.savedRightMargin;
        flp.bottomMargin = entry.savedBottomMargin;
        content.setLayoutParams(flp);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 几何测量辅助
    // ══════════════════════════════════════════════════════════════════════

    /** 聊天输入框的实时屏幕上沿（尚未布局/已隐藏返回 -1，本轮不抬升） */
    private static int measureChatTop() {
        View chat = sChatInput != null ? sChatInput.get() : null;
        if (chat == null || !chat.isShown() || chat.getHeight() <= 0) return -1;
        int[] loc = new int[2];
        chat.getLocationOnScreen(loc);
        return loc[1] > 0 ? loc[1] : -1;
    }

    /**
     * 抬升时被贴到输入框上沿的那条边：收缩模式是内容（对话框根）的底边，整窗上移模式是窗口
     * 底边。内容底边按缩放后的可视边界解算（旋转重排会给内容整体缩放，pivot 在中心），
     * 与 {@link DraggablePopupHelper} 里把手/横条贴位同款算法，保证「看到的对话框底边」
     * 而不是「布局盒底边」贴住输入框上沿。
     */
    private static int measureLiftBottom(Entry entry, View window) {
        View content = entry.liftMode ? null : entry.contentRef.get();
        if (content != null && content.isShown() && content.getHeight() > 0) {
            int[] loc = new int[2];
            content.getLocationOnScreen(loc);
            float sy = content.getScaleY();
            int visualH = Math.round(content.getHeight() * sy);
            int visTopOffset = Math.round((content.getHeight() - visualH) / 2f);
            return loc[1] + visTopOffset + visualH;
        }
        return windowLocationTop(window) + window.getHeight();
    }

    /**
     * 该弹窗是否已收缩成底部横条：优先问 {@link DraggablePopupHelper} 包装层的收缩态，
     * 其余弹窗退回「内容根被隐藏」判定（收缩时内容 setVisibility(GONE)）。
     * 收缩态的窗口几何已由 dialog 自身按聊天输入框解算并随布局变化重锚，本工具不再介入
     * （否则横条会被推离输入框、或被撑回整块窗口）。
     */
    private static boolean isCollapsed(Entry entry) {
        PopupWindow popup = entry.popup();
        if (popup != null && DraggablePopupHelper.isPopupCollapsed(popup)) return true;
        View content = entry.contentRef.get();
        return content != null && content.getVisibility() != View.VISIBLE;
    }

    /** 自弹窗内容向上找到其窗口 decor（承载 WindowManager.LayoutParams 的那个视图） */
    private static View windowViewOf(PopupWindow popup) {
        View v = popup.getContentView();
        while (v != null) {
            if (v.getLayoutParams() instanceof WindowManager.LayoutParams) return v;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return null;
    }

    private static WindowManager.LayoutParams paramsOf(View window) {
        ViewGroup.LayoutParams lp = window.getLayoutParams();
        return lp instanceof WindowManager.LayoutParams ? (WindowManager.LayoutParams) lp : null;
    }

    /** 清理已失效条目（弹窗被回收且来不及走 detach 回调的兜底） */
    private static void prune() {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            if (ENTRIES.get(i).popup() == null) ENTRIES.remove(i);
        }
    }
}
