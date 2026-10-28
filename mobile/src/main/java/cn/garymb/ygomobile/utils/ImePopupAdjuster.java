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
public final class ImePopupAdjuster extends ImePopupAdjusterMid {

    private ImePopupAdjuster() {
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
}
