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
 * IME popup lift adjuster, base layer of the split inheritance chain
 * (ImePopupAdjusterBase <- ImePopupAdjusterMid <- ImePopupAdjuster).
 * Owns all tunable constants and static registry state, the per-popup
 * Entry type, and the pure helpers: EditText detection, window layout
 * write/freeze/rebuild, content snapshot apply/restore, geometry
 * measurement and stale-entry pruning.
 */
abstract class ImePopupAdjusterBase {

    /** adjustResize 抬升输入框伴随动画，延后待其上沿稳定再开始解算（与 PlayerWaitingDialog 原实现同值） */
    protected static final long SETTLE_DELAY_MS = 220L;

    /** 单轮解算间隔：键盘升降动画期间的两次窗口重排至少隔这么久 */
    protected static final long TICK_MS = 60L;

    /** 连续无纠正的最大轮数：达到即认为几何已稳定，停止循环（避免空转） */
    protected static final int MAX_IDLE_TICKS = 8;

    /** 抬升底边允许的误差（像素）：底边低于输入框上沿（遮挡）一律纠正，高于则容许此间隙 */
    protected static final int LIFT_GAP_TOL_PX = 2;

    /**
     * 抬升时对话框底边相对输入框上沿预留的安全间隙（dp）：卡片背景贴图的投影会向布局盒
     * 之外延伸若干像素，按盒底边正好贴齐输入框上沿在真机上仍会「压住输入框一点」。故目标
     * 底边一律上提这道窄缝——视觉上仍是紧贴，但保证零遮挡（用户规格：不要有一点遮挡）。
     */
    protected static final int LIFT_SAFETY_DP = 2;

    /** 还原后核对窗口矩形与基准矩形的允许误差（像素） */
    protected static final int VERIFY_TOL_PX = 2;

    /**
     * 聊天输入框「被抬高」的判定阈值（dp，玩家等待弹窗几何判定专用）：实测上沿比
     * 静置基准上移超过此距离才算抬高——键盘弹起但输入框未动时弹窗保持原处，不抬升
     * （用户规格），也吸收掉事件抖动引起的快速上下闪动。
     */
    protected static final int CHAT_LIFT_TRIGGER_DP = 24;

    /**
     * 抬升解算前要求的「落定」轮数：连续这么多轮实测输入框上沿一致才算最终高度
     * （60ms/轮 → 约 120ms 静止窗口，覆盖键盘动画尾帧抖动）。
     */
    protected static final int CHAT_STABLE_TICKS = 2;

    /** 还原收敛后补做区域锚定的轮数：窗口撑回与 margin 解算分帧完成，多补几轮确保回到区域中央 */
    protected static final int RESTORE_REANCHOR_PASSES = 4;

    /** 静置态最多连续修复次数：超过即接受当前几何并重采基准，杜绝永远收敛不了时空转 */
    protected static final int MAX_REPAIR_TRIES = 6;

    /** 抬升态最多连续下发次数：个别 ROM 不认窗口尺寸下发，收敛不了就停手，避免每轮空转 */
    protected static final int MAX_LIFT_TRIES = 25;

    protected static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 已登记的弹窗（弱引用：弹窗与视图销毁后不影响回收，条目在遍历顺带清理） */
    protected static final List<Entry> ENTRIES = new ArrayList<>();

    /** 当前判定的输入法态：键盘抬起期间新显示的 dialog 立即按同态抬升，不必等下一次显隐翻转 */
    protected static boolean sImeUp = false;
    /** 聊天输入框弱引用：抬升位置以它的实时屏幕上沿为准（旋转重建后由 Activity 重新传入新实例） */
    protected static WeakReference<View> sChatInput;

    /** 聊天输入框静置上沿基准（屏幕 px）：只在落定且未处于抬高态时重采，抬高判定以它为准 */
    protected static int sChatRestTop = -1;
    /** 抬高态锁存（玩家等待弹窗专用）：输入框落定后被抬高才置 true，落定降回基准高度才
     *  置 false；动画途中的中间值不参与翻转，杜绝逐帧追逐引起的闪动（用户规格） */
    protected static boolean sChatLiftState = false;
    /** {@link #CHAT_LIFT_TRIGGER_DP} 换算成的像素（随 {@link #refreshLiftSafety} 同密度刷新） */
    protected static int sChatLiftTriggerPx = 0;
    /** 聊天输入框视图根的全局布局监听：非键盘期实时更新静置基准；重绑时先摘旧再挂新 */
    protected static ViewTreeObserver.OnGlobalLayoutListener sRestSampler;

    /** 本轮状态切换已连续无纠正的轮数 */
    protected static int sIdleTicks = 0;

    /** 落定判定：上一轮解算时聊天输入框的实测上沿（-2 = 尚未采样） */
    protected static int sStableChatTop = -2;
    /** 输入框上沿与上一轮连续一致的轮数（达 {@link #CHAT_STABLE_TICKS} 视为键盘动画落定） */
    protected static int sStableCount = 0;
    /** 当前是否已落定：玩家等待/含 EditText 弹窗只在此态下发移动，动画途中不动 */
    protected static boolean sChatStable = true;

    /** {@link #LIFT_SAFETY_DP} 换算成的像素（按登记时所在屏幕密度，attach 与键盘弹起时刷新） */
    protected static int sLiftSafetyPx = 0;

    /** 按参照视图的屏幕密度刷新抬升安全间隙 */
    protected static void refreshLiftSafety(View reference) {
        if (reference == null || reference.getContext() == null) return;
        float density = reference.getContext().getResources().getDisplayMetrics().density;
        sLiftSafetyPx = Math.max(1, Math.round(LIFT_SAFETY_DP * density));
        sChatLiftTriggerPx = Math.round(CHAT_LIFT_TRIGGER_DP * density);
    }

    /** 单个弹窗的登记项：抬升方式 + 干净状态下采集的基准矩形与内容布局快照 */
    protected static class Entry {
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
    /** 视图子树内是否含 EditText（含子类）：用于识别自带输入框、一律不得缩小的弹窗 */
    protected static boolean containsEditText(View v) {
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
    protected static int windowLocationTop(View window) {
        int[] loc = new int[2];
        window.getLocationOnScreen(loc);
        return loc[1];
    }

    protected static void updateViewLayout(View window, WindowManager.LayoutParams p) {
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
    protected static void rebuildWindow(Entry entry, View window, WindowManager.LayoutParams p) {
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
    protected static void freezeImeMode(Entry entry, View window, WindowManager.LayoutParams p) {
        if (entry.softFrozen || p.softInputMode == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) {
            return;
        }
        entry.savedSoftInputMode = p.softInputMode;
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        entry.softFrozen = true;
        updateViewLayout(window, p);
    }

    /** 解除抬升期的 IME 冻结，写回登记窗口自身的原 softInputMode */
    protected static void releaseImeMode(Entry entry, View window, WindowManager.LayoutParams p) {
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
    protected static void applyContentLift(Entry entry) {
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
    protected static boolean contentNeedsRestore(Entry entry) {
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
    protected static void saveContentSnapshot(Entry entry) {
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
    protected static void restoreContent(Entry entry) {
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
    protected static int measureChatTop() {
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
    protected static int measureLiftBottom(Entry entry, View window) {
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
    protected static boolean isCollapsed(Entry entry) {
        PopupWindow popup = entry.popup();
        if (popup != null && DraggablePopupHelper.isPopupCollapsed(popup)) return true;
        View content = entry.contentRef.get();
        return content != null && content.getVisibility() != View.VISIBLE;
    }

    /** 自弹窗内容向上找到其窗口 decor（承载 WindowManager.LayoutParams 的那个视图） */
    protected static View windowViewOf(PopupWindow popup) {
        View v = popup.getContentView();
        while (v != null) {
            if (v.getLayoutParams() instanceof WindowManager.LayoutParams) return v;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return null;
    }

    protected static WindowManager.LayoutParams paramsOf(View window) {
        ViewGroup.LayoutParams lp = window.getLayoutParams();
        return lp instanceof WindowManager.LayoutParams ? (WindowManager.LayoutParams) lp : null;
    }

    /** 清理已失效条目（弹窗被回收且来不及走 detach 回调的兜底） */
    protected static void prune() {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            if (ENTRIES.get(i).popup() == null) ENTRIES.remove(i);
        }
    }
}
