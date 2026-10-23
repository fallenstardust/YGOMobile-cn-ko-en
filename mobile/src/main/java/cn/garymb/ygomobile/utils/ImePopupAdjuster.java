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
 * 抬升方式按弹窗结构与登记选项自动选择：
 * <ul>
 *   <li><b>自动最小化为底部收缩横条</b>（{@link DraggablePopupHelper#setupDraggablePopup} 包装成
 *   铺满窗口的弹窗，绝大多数 dialog，用户规格）：键盘抬起时把 dialog 收缩成横条（无横条的
 *   动态补装），横条底边贴「输入框上沿 − 安全间隙」，并把窗口底边同步收缩到同一目标线——
 *   真机结论（原 PlayerWaitingDialog）：铺满全屏的弹窗窗口盖住键盘区域会吞掉输入法触摸，
 *   键盘按键落不回 IME；收缩后抬高后的输入框与键盘全部位于弹窗窗口之外，触摸恢复；
 *   高内容不再被裁剪（只剩横条可见），输入法收起后一步展开复原并由还原序列把窗口撑回基准。
 *   已是用户手动收缩态的同样接管：横条随键盘逐轮重锚贴住抬高后的输入框、窗口底边同步
 *   收缩，收起后保持收缩态随输入框降回原位仍紧贴其上方（用户规格），不自动展开；
 *   键盘抬起期间横条一律不可点（展开会再次吞掉输入法触摸，用户规格），复原统一由
 *   还原序列完成。仅聊天输入框持有焦点时介入；内容含 EditText 的弹窗（宣言卡搜索框、
 *   录像保存名称等）登记时自动识别，一律不自动最小化——收缩成横条会藏起输入框导致
 *   无法输入（用户反馈），改走下方「收缩窗口 + 内容贴底」整 dialog 抬高方式。</li>
 *   <li><b>收缩窗口 + 内容贴底（旧方式）</b>（{@link #attach(PopupWindow, boolean)} 传
 *   autoMinimize=false，或弹窗内容含 EditText 被强制置位）：
 *   窗口顶保持原位、窗口底缩到「输入框上沿 − 安全间隙」，内容重力改底部对齐并清零纵向
 *   margin，整个 dialog 视觉上被抬高贴住输入框。其中不最小化且自身无输入框的（玩家
 *   等待弹窗）在登记时即冻结本窗口 softInputMode，消除 WMS 二次 resize 与本类下发
 *   互相拉扯引起的快速上下闪动（用户反馈）。
 *   <b>抬升/最小化的触发对所有登记弹窗一律由几何判定驱动</b>：只凭「聊天输入框实测
 *   上沿相对静置基准落定后是否真被抬高」决定（不论输入法是否显示）——输入法直接
 *   覆盖界面而未抬高输入框时不做最小化/抬升（用户规格），不再受显隐事件翻转影响。</li>
 *   <li><b>整窗上移</b>（先后攻/位置选择这类以 {@code showAtLocation} 绝对坐标定位、自带尺寸的
 *   小弹窗）：尺寸不动，整窗上移「弹窗底边 − 输入框上沿」的差值，使底边贴输入框上沿，且不裁剪
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

    /**
     * 聊天输入框「被抬高」的判定阈值（dp，玩家等待弹窗几何判定专用）：实测上沿比
     * 静置基准上移超过此距离才算抬高——键盘弹起但输入框未动时弹窗保持原处，不抬升
     * （用户规格），也吸收掉事件抖动引起的快速上下闪动。
     */
    private static final int CHAT_LIFT_TRIGGER_DP = 24;

    /**
     * 抬升解算前要求的「落定」轮数：连续这么多轮实测输入框上沿一致才算最终高度
     * （60ms/轮 → 约 120ms 静止窗口，覆盖键盘动画尾帧抖动）。
     */
    private static final int CHAT_STABLE_TICKS = 2;

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

    /** 聊天输入框静置上沿基准（屏幕 px）：只在落定且未处于抬高态时重采，抬高判定以它为准 */
    private static int sChatRestTop = -1;
    /** 抬高态锁存（玩家等待弹窗专用）：输入框落定后被抬高才置 true，落定降回基准高度才
     *  置 false；动画途中的中间值不参与翻转，杜绝逐帧追逐引起的闪动（用户规格） */
    private static boolean sChatLiftState = false;
    /** {@link #CHAT_LIFT_TRIGGER_DP} 换算成的像素（随 {@link #refreshLiftSafety} 同密度刷新） */
    private static int sChatLiftTriggerPx = 0;
    /** 聊天输入框视图根的全局布局监听：非键盘期实时更新静置基准；重绑时先摘旧再挂新 */
    private static ViewTreeObserver.OnGlobalLayoutListener sRestSampler;

    /** 本轮状态切换已连续无纠正的轮数 */
    private static int sIdleTicks = 0;

    /** 落定判定：上一轮解算时聊天输入框的实测上沿（-2 = 尚未采样） */
    private static int sStableChatTop = -2;
    /** 输入框上沿与上一轮连续一致的轮数（达 {@link #CHAT_STABLE_TICKS} 视为键盘动画落定） */
    private static int sStableCount = 0;
    /** 当前是否已落定：玩家等待/含 EditText 弹窗只在此态下发移动，动画途中不动 */
    private static boolean sChatStable = true;

    /** {@link #LIFT_SAFETY_DP} 换算成的像素（按登记时所在屏幕密度，attach 与键盘弹起时刷新） */
    private static int sLiftSafetyPx = 0;

    /** 按参照视图的屏幕密度刷新抬升安全间隙 */
    private static void refreshLiftSafety(View reference) {
        if (reference == null || reference.getContext() == null) return;
        float density = reference.getContext().getResources().getDisplayMetrics().density;
        sLiftSafetyPx = Math.max(1, Math.round(LIFT_SAFETY_DP * density));
        sChatLiftTriggerPx = Math.round(CHAT_LIFT_TRIGGER_DP * density);
    }

    /** 状态切换后的首轮解算任务：{@link #schedule(long)} 的延时即键盘动画等待时间，到点直接进 tick 循环 */
    private static final Runnable SETTLE = () -> startTicks(0L);
    private static final Runnable TICK = ImePopupAdjuster::tick;

    /** 单个弹窗的登记项：抬升方式 + 干净状态下采集的基准矩形与内容布局快照 */
    private static class Entry {
        final WeakReference<PopupWindow> popupRef;
        /** true = 自定位弹窗整窗上移（尺寸与内容布局都不改）；false = 铺满窗口的包装层弹窗
         *  （抬升走 {@link #stepMinimize} 自动最小化，或 noAutoMinimize 时走旧收缩+贴底） */
        final boolean liftMode;
        /** true = 包装层弹窗不做自动最小化，维持旧的「收缩窗口 + 内容贴底」整 dialog 抬高
         *  （{@link #attach(PopupWindow, boolean)} 传 autoMinimize=false，或因含 EditText 被
         *  强制置位，用户规格：玩家等待 / 含输入框弹窗不加最小化） */
        final boolean noAutoMinimize;
        /** true = 弹窗内容子树含 EditText（宣言卡搜索框、录像保存名称等）：一律不缩小/
         *  不最小化；聊天输入框未持焦时（弹窗自身输入框唤起键盘）完全不介入，保留其
         *  原生 ADJUST_RESIZE 输入路径（用户规格） */
        final boolean hasEditText;
        /** true = 玩家等待弹窗方式（不最小化且自身无输入框）：抬升改由「聊天输入框实测
         *  上沿相对静置基准是否真被抬高」的几何判定驱动，不论输入法是否显示都不再受
         *  显隐事件翻转影响；键盘显示期冻结本弹窗 softInputMode，消除 WMS 二次 resize
         *  与本类还原下发的互相拉扯（输入框未抬高时快速上下闪动的根源，用户反馈） */
        final boolean followChatLift;
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
        /** 本弹窗是否已被输入法联动自动收缩成横条（横条底边贴抬高后的输入框上沿） */
        boolean minimizedForIme;
        /** 自动最小化期间用户点横条手动展开：本个抬升周期内尊重用户，不再强制收缩回去 */
        boolean userExpandedFlag;
        /** 自动最小化横条锚定的连续下发次数（复用抬升上限，收敛不了即停手） */
        int barTries;
        /** 本次还原周期内是否已重建过窗口：重建每做一次窗口就消失/重现一次，
         *  每个周期至多一次；否则键盘动画期实测高度反复变化会被当成「撑不回」
         *  连做多次重建，构成横屏还原时的快速显隐闪烁（用户反馈） */
        boolean rebuiltOnce;
        /** 抬升期间是否已把弹窗窗口 softInputMode 冻结为 ADJUST_NOTHING（阻止 WMS 对弹窗
         *  二次 resize 构成第二定位控制器——闪动与钳顶的根源）；仅聊天输入框持有焦点时冻结，
         *  宣言卡等自带搜索框依赖 ADJUST_RESIZE 的自身输入路径不受影响 */
        boolean softFrozen;
        int savedSoftInputMode;
        /** 内容视图上的注销监听（注销时摘除，避免 dialog 反复显示/关闭在同一视图上堆叠监听） */
        View.OnAttachStateChangeListener attachListener;

        Entry(PopupWindow popup, boolean liftMode, View content, boolean noAutoMinimize,
              boolean hasEditText) {
            this.popupRef = new WeakReference<>(popup);
            this.liftMode = liftMode;
            this.contentRef = new WeakReference<>(content);
            this.noAutoMinimize = noAutoMinimize;
            this.hasEditText = hasEditText;
            this.followChatLift = noAutoMinimize && !hasEditText;
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
        attach(popup, true);
    }

    /**
     * @param autoMinimize 铺满窗口的包装层弹窗抬升时是否自动最小化为底部收缩横条（默认 true）；
     *                     传 false 维持旧的「收缩窗口 + 内容贴底」整 dialog 抬高方式（用户规格：
     *                     玩家等待弹窗不加最小化功能）；自定位弹窗的整窗上移不受此参数影响。
     *                     弹窗内容含 EditText 时无论传值一律强制按不最小化处理（用户规格：
     *                     不得缩小包含 edittext 布局的 dialog）。
     */
    public static void attach(PopupWindow popup, boolean autoMinimize) {
        if (popup == null) return;
        prune();
        for (Entry e : ENTRIES) {
            if (e.popup() == popup) return;
        }
        View content = DraggablePopupHelper.liftedContentView(popup);
        if (content == null) return;
        refreshLiftSafety(content);
        // 含 EditText 的弹窗（宣言卡搜索框等）强制不自动最小化：收缩成横条会把输入框
        // 一并藏起，聊天抬键盘时用户无法继续输入（用户反馈）
        boolean hasEditText = containsEditText(content);
        final Entry entry = new Entry(popup,
                !DraggablePopupHelper.isWrappedFullscreenPopup(popup), content,
                !autoMinimize || hasEditText, hasEditText);
        // 期望态一律由输入框几何锁存决定（用户规格：只有聊天输入框真被抬高才抬升/
        // 最小化，输入法直接覆盖未抬高输入框时不处理），基准未建立前退回 IME 事件态
        entry.wantLift = sChatRestTop > 0 ? sChatLiftState : sImeUp;
        ENTRIES.add(entry);
        // 跟随输入框抬高的弹窗（玩家等待）登记即刻冻结本窗口的 IME 自适应：等首轮 tick
        // 再冻会留下 WMS 按键盘高度收缩铺满窗口的空窗，随后的还原下发与二次 resize 来回
        // 拉扯，即真机仍见残余快速上下闪动的根源（用户反馈）；冻结后窗口尺寸只由本类下发
        if (entry.followChatLift) {
            View w = windowViewOf(popup);
            WindowManager.LayoutParams lp = w == null ? null : paramsOf(w);
            if (w != null && lp != null) freezeImeMode(entry, w, lp);
        }
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

    /** 视图子树内是否含 EditText（含子类）：用于识别自带输入框、一律不得缩小的弹窗 */
    private static boolean containsEditText(View v) {
        if (v == null) return false;
        if (v instanceof EditText) return true;
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsEditText(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Activity / 聊天输入框侧接口：输入法显隐驱动
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 提早绑定聊天输入框（Activity 在 initViews 与旋转重建绑定新视图后调用）：登记弱引用
     * 并在视图根挂全局布局监听，非键盘期采样输入框静置上沿基准，使玩家等待弹窗的几何
     * 判定（只凭「输入框是否真被抬高」）从第一次抬键盘起就可用；不绑定时基准在首次
     * 键盘收起后才建立，期间退回旧的 IME 事件驱动。
     */
    public static void bindChatInput(View chat) {
        if (chat == null) return;
        sChatInput = new WeakReference<>(chat);
        refreshLiftSafety(chat);
        View root = chat.getRootView();
        if (root == null) return;
        ViewTreeObserver vto = root.getViewTreeObserver();
        if (!vto.isAlive()) return;
        if (sRestSampler != null) {
            try {
                vto.removeOnGlobalLayoutListener(sRestSampler);
            } catch (Exception ignored) {
            }
        }
        sRestSampler = () -> {
            int t = measureChatTop();
            if (t <= 0) return;
            // 布局变化不重采基准也不翻转状态：仅在「瞬时判定与当前抬高态不一致」
            // 或基准尚未建立时唤醒解算，由 tick 在落定后才真正切换抬高/回位，
            // 杜绝键盘动画中间帧污染基准引起的快速上下闪动（用户反馈）
            boolean liftedNow = sChatRestTop > 0
                    && t < sChatRestTop - sChatLiftTriggerPx;
            if (sChatRestTop > 0 && liftedNow == sChatLiftState) return;
            for (Entry e : ENTRIES) {
                if (e.followChatLift) {
                    schedule(0L);
                    return;
                }
            }
        };
        vto.addOnGlobalLayoutListener(sRestSampler);
        // 首帧布局完成后立即采一次基准（initViews 时输入框尚未布局）
        chat.post(() -> {
            if (sImeUp) return;
            int t = measureChatTop();
            if (t > 0) sChatRestTop = t;
        });
    }

    /**
     * 输入法抬起：不再事件驱动地把弹窗置为抬升态——抬升/最小化一律由 tick 按
     * 「聊天输入框实测上沿相对静置基准是否真被抬高」的锁存态决定（用户规格：输入法
     * 直接覆盖界面而未抬高输入框时不做最小化/抬升），此处仅记录事件态供基准未建立时
     * 退回，并延时启动解算等待键盘动画落定。
     *
     * @param chatInput 当前视图树中的聊天输入框（旋转重建后须传新实例）
     */
    public static void notifyImeShown(View chatInput) {
        sImeUp = true;
        sChatInput = chatInput == null ? null : new WeakReference<>(chatInput);
        refreshLiftSafety(chatInput);
        // 键盘动画结束后首轮解算，其后由 tick 逐轮纠正到实测底边与输入框上沿一致
        schedule(SETTLE_DELAY_MS);
    }

    /** 输入法收起：同样不事件翻转期望态（收起动画途中几何尚未落定，事件抢跑会把
     *  弹窗提前放回又抬起来一次——残余闪动），回位由 tick 在输入框落定降回后统一处理 */
    public static void notifyImeHidden() {
        sImeUp = false;
        schedule(0L);
    }

    /**
     * 还原全部弹窗：屏幕旋转重建视图树时由 Activity 在弹窗重排之前调用——
     * 旋转时系统已收起键盘，用户规格要求横竖屏切换后弹窗一律回到区域中央，
     * 故此刻必须先解除抬升态，后续的旋转重排才不会被锚定推迟守卫挡住。
     */
    public static void restoreAll() {
        setWantLift(false);
        // 旋转/重建后旧方向的静置基准与抬高态不再适用：作废基准，下一轮落定时重采新静置位
        sChatRestTop = -1;
        sChatLiftState = false;
        schedule(0L);
    }

    /** 当前输入法态（dialog 需要在显示时判断是否立即抬升时可用） */
    public static boolean isImeUp() {
        return sImeUp;
    }

    private static void setWantLift(boolean lift) {
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
    private static boolean stepMinimize(Entry entry, PopupWindow popup, View window,
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

    /**
     * 抬升期间冻结弹窗窗口的 IME 自适应：ADJUST_RESIZE 下 WMS 会同步压缩铺满窗口的弹窗
     * 窗口本身，与本工具的横条锚定构成两个定位控制器（弹窗随键盘动画反复重排、还原时
     * 又多次闪动的来源）。只在聊天输入框持有焦点时冻结——宣言卡等弹窗自带搜索框依赖
     * ADJUST_RESIZE 把键盘顶到自己上面，其自身输入路径绝不能冻结。
     */
    private static void freezeImeMode(Entry entry, View window, WindowManager.LayoutParams p) {
        if (entry.softFrozen || p.softInputMode == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) {
            return;
        }
        entry.savedSoftInputMode = p.softInputMode;
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        entry.softFrozen = true;
        updateViewLayout(window, p);
    }

    /** 解除抬升期的 IME 冻结，写回登记窗口自身的原 softInputMode */
    private static void releaseImeMode(Entry entry, View window, WindowManager.LayoutParams p) {
        if (!entry.softFrozen) return;
        entry.softFrozen = false;
        p.softInputMode = entry.savedSoftInputMode;
        updateViewLayout(window, p);
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
