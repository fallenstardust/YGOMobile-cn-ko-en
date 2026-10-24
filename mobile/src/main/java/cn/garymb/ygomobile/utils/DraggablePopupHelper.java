package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.view.ScrollingView;
import androidx.core.widget.NestedScrollView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;

/**
 * 可拖动弹窗辅助工具（拆分后的具体叶子类，保持原类名与全部调用方式）。
 *
 * <p>原单一文件共 2005 行，拆为 {@link DraggablePopupHelperBase} ← 本类；本层持有弹窗包装、
 * 显示/区域锚定与输入法联动的对外 API。各对话框仍经 {@code new DraggablePopupHelper(context, id)}
 * 实例化并调用，静态方法仍经 {@code DraggablePopupHelper.xxx} 限定调用（继承自基类）。</p>
 *
 * <p>原嵌套类 DragFrameLayout 拆至同包 {@link DragFrameLayoutBase} ← {@link DragFrameLayoutImpl}，
 * 壳声明在基类中，行为与实现细节与拆分前完全一致。</p>
 */
public class DraggablePopupHelper extends DraggablePopupHelperBase {
    public DraggablePopupHelper(Context context, String dialogId) {
        this.context = context;
        this.dialogId = dialogId;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadPosition();
    }
    public void setupDraggablePopup(PopupWindow popupWindow, View contentView,
                                     int designW, int designH) {
        setupDraggablePopup(popupWindow, contentView, designW, designH, false);
    }

    /**
     * @param swapForPortrait 竖屏宽高比重排（供建主/局域网/单机/录像/玩家等待这类横屏设计为宽大于高的弹窗使用）：
     *                        竖屏宽铺满屏宽（与 Activity 同宽）且高与宽相等（正方形），转回横屏
     *                        恢复设计宽高比（宽大于高）；平板/折叠屏大分辨率近方屏下正方形边长按
     *                        设计宽 420dp 封顶（见 {@link DialogScale#portraitSquareCapPx}）；其余弹窗传 false 行为不变。
     */
    public void setupDraggablePopup(PopupWindow popupWindow, View contentView,
                                     int designW, int designH, boolean swapForPortrait) {
        if (!ENABLE_DRAG) return;

        ViewGroup originalParent = (ViewGroup) contentView.getParent();
        int index = -1;
        ViewGroup.LayoutParams lp = contentView.getLayoutParams();
        if (originalParent != null) {
            index = originalParent.indexOfChild(contentView);
            originalParent.removeView(contentView);
        }

        DragFrameLayout wrapper = new DragFrameLayout(
                contentView.getContext(), prefs, dialogId);
        // 包装层铺满窗口，内部对话框以 Gravity.CENTER 居中，内容区之外的触摸需转发给下层弹窗或 Activity 窗口，
        // 否则弹窗显示期间决斗场、双方手卡公开面板等下层 UI 无法响应点击
        wrapper.setHostPopup(popupWindow);
        wrapper.setPassThroughTarget(resolveActivityDecorView(contentView.getContext()));
        // 记录设计尺寸与创建时的等比系数：屏幕旋转后按新屏宽重新解算弹窗显示宽度、并按
        // 当前方向系数相对创建系数的比例重缩放弹窗（见 relayoutActivePopupsForOrientation），
        // 故此处入参语义为“设计（未限宽）尺寸”，由本方法统一按当前屏宽解算实际显示尺寸
        wrapper.setDesignSize(designW, designH, DialogScale.factor(contentView.getContext()));
        wrapper.setSwapForPortrait(swapForPortrait);

        int[] fitted = orientedFitSize(contentView.getContext(), designW, designH, swapForPortrait);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(fitted[0], fitted[1]);
        centerLp.gravity = Gravity.CENTER;
        contentView.setLayoutParams(centerLp);
        wrapper.addView(contentView);

        if (originalParent != null) {
            originalParent.addView(wrapper, index, lp);
        } else {
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            wrapper.setClipChildren(false);
            popupWindow.setContentView(wrapper);
            popupWindow.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
            popupWindow.setHeight(ViewGroup.LayoutParams.MATCH_PARENT);
        }
        // 用户规格：所有 dialog 不响应返回键/返回手势（含输入框的弹窗除外，见方法注释）
        ignoreBackKey(popupWindow, contentView);
        // 用户规格：聊天输入框与输入法显示在所有弹窗前面——统一应用 Android 12+ 键盘分层修复
        applyImeLayeringFix(popupWindow);
    }
    /**
     * 按 dialog 单独启用/禁用拖拽（须在 {@link #setupDraggablePopup} 包装生效后调用）：
     * 用户规格——建主/局域网/单机/录像/玩家等待五个 dialog 对齐 C++ 定位弹窗不可
     * 拖动，禁用时一并清除其记忆的拖拽位置并把已有的整层平移归零；其他 dialog 不受影响仍可拖动。
     */
    public void setDragEnabled(PopupWindow popupWindow, boolean enabled) {
        DragFrameLayout layer = findLayerOfPopup(popupWindow);
        if (layer == null) return;
        layer.setDragEnabled(enabled);
        if (!enabled) clearSavedPosition();
    }

    /**
     * 为经 {@link #setupDraggablePopup} 包装的弹窗注册自定义旋转重排逻辑：默认重排只按设计
     * 尺寸重解弹窗显示宽度，而选卡/卡片确认类弹窗宽度由 <code>layout_game_right</code> 区域
     * 实时解算并据此烘焙每张卡图尺寸，默认重排无法同步子视图尺寸，会导致横竖屏切换后卡片被裁剪。
     * 注册后旋转重排改由传入的 runnable 全权负责（重解宽度、重烘焙子视图、重新限宽与居中）。
     */
    public void registerOrientationRelayout(PopupWindow popupWindow, Runnable handler) {
        if (popupWindow == null || !(popupWindow.getContentView() instanceof DragFrameLayout)) {
            return;
        }
        ((DragFrameLayout) popupWindow.getContentView()).setCustomRelayout(handler);
    }

    public void applySavedPositionToView(View targetView) {
        if (hasSavedPosition) {
            targetView.post(() -> {
                targetView.setLeft(lastX);
                targetView.setTop(lastY);
                targetView.setRight(lastX + targetView.getWidth());
                targetView.setBottom(lastY + targetView.getHeight());
            });
        }
    }

    public void showPopup(PopupWindow popupWindow, View anchorView) {
        showPopup(popupWindow, anchorView, Gravity.CENTER, 0, 0);
    }
    /**
     * 将经 setupDraggablePopup 包装为全窗口的 popup 内容，从「整个窗口居中」改为
     * 「按指定区域宽高居中」（如 layout_game_right）：
     * 包装层（DragFrameLayout）铺满窗口，内部对话框以 Gravity.CENTER 居中，
     * 通过非对称 margin 把内容中心平移「区域中心 - 窗口中心」的偏移量即可。
     * 区域/窗口此刻未解算出有效几何（旋转重建瞬间视图树尚未布局、popup 自身窗口 resize
     * 滞后）时挂布局监听逐帧重试，直到几何就绪应用成功后才摘除——旧写法「先摘监听再
     * 应用、几何无效直接放弃」会让询问/宣言弹窗旋转后偶发地永不回区域中心（用户反馈）。
     */
    public static void centerPopupInRegion(PopupWindow popupWindow, View region) {
        if (popupWindow == null || region == null) return;
        // 记录居中区域到底层包装层，供屏幕旋转重建视图树后按同 id 重新解析并再次居中
        if (popupWindow.getContentView() instanceof DragFrameLayout) {
            ((DragFrameLayout) popupWindow.getContentView()).setCenterRegion(region);
        }
        if (!applyRegionAnchor(popupWindow, region, ANCHOR_CENTER)) {
            addRegionAnchorRetryListener(popupWindow, region, ANCHOR_CENTER);
        }
    }
    /**
     * 将经 {@link #setupDraggablePopup} 包装为全窗口的 popup 内容贴到指定区域（如 layout_game_right）
     * 的**左缘**、区域内垂直居中（区别于 {@link #centerPopupInRegion} 的水平居中）：
     * 包装层内对话框内容以 Gravity.CENTER 布局，左缘基准 = 窗口左 +（窗口宽 - 内容宽）/2，
     * 据此反推 leftMargin 使内容左缘 = 区域左缘；topMargin 与居中同式使内容垂直居中于区域。
     * 同时把区域与 dockLeft 模式记入包装层，旋转重建后由 {@link DragFrameLayout#relayoutForOrientation}
     * 按同 id 新实例重新贴回左缘。区域/窗口尚未布局时挂布局监听重试至应用成功（同 {@link #centerPopupInRegion}）。
     */
    public static void dockPopupInRegionLeft(PopupWindow popupWindow, View region) {
        if (popupWindow == null || region == null) return;
        if (popupWindow.getContentView() instanceof DragFrameLayout) {
            ((DragFrameLayout) popupWindow.getContentView()).setCenterRegion(region, true);
        }
        if (!applyRegionAnchor(popupWindow, region, ANCHOR_DOCK_LEFT)) {
            addRegionAnchorRetryListener(popupWindow, region, ANCHOR_DOCK_LEFT);
        }
    }
    /**
     * 将经 {@link #setupDraggablePopup} 包装为全窗口的 popup 内容贴到指定区域（如 layout_game_right）
     * 的**右上角**（区别于 {@link #centerPopupInRegion} 居中、{@link #dockPopupInRegionLeft} 贴左缘）：
     * 包装层（DragFrameLayout）铺满整屏，内容以 Gravity.TOP|END 布局，通过 rightMargin 把内容右缘
     * 从窗口右缘向左平移「窗口右 - 区域右」的距离，topMargin 把内容顶缘对齐到区域顶缘。
     * 区域/窗口尚未布局时挂布局监听重试至应用成功（同 {@link #centerPopupInRegion}）。供决斗记录这类
     * 固定在右上角、需铺满整屏参与跨窗口触摸转发与点击置顶的浮层使用。
     */
    public static void anchorPopupInRegionTopRight(PopupWindow popupWindow, View region) {
        if (popupWindow == null || region == null) return;
        if (!applyRegionAnchor(popupWindow, region, ANCHOR_TOP_RIGHT)) {
            addRegionAnchorRetryListener(popupWindow, region, ANCHOR_TOP_RIGHT);
        }
    }
    public void showPopup(PopupWindow popupWindow, View anchorView, int gravity, int xOffset, int yOffset) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
        }

        View effectiveAnchor = null;
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            View decorView = activity.getWindow().getDecorView();
            effectiveAnchor = decorView;
        }
        if (effectiveAnchor == null) {
            effectiveAnchor = anchorView;
        }
        if (effectiveAnchor == null || effectiveAnchor.getWindowToken() == null) {
            return;
        }

        try {
            if (hasSavedPosition && !isRegionAnchoredPopup(popupWindow)) {
                popupWindow.showAtLocation(effectiveAnchor, Gravity.NO_GRAVITY, lastX, lastY);
            } else {
                // 区域锚定的全窗口包装层不接受“整窗绝对偏移”式记忆位：那是上一方向的屏幕坐标，
                // 在新方向重放会把铺满整屏的层连同 margin 定位一起推出屏外，故忽略并清除
                if (hasSavedPosition) clearSavedPosition();
                popupWindow.showAtLocation(effectiveAnchor, gravity, xOffset, yOffset);
            }
        } catch (Exception e) {
            // Token may become invalid (e.g. OPPO ColorOS OplusViewRootImplHooks$ColorW)
        }
    }

    /**
     * 该弹窗是否为“内容按区域定位”的全窗口包装层（已由 {@link #centerPopupInRegion} /
     * {@link #dockPopupInRegionLeft} 记下过居中区）：这类弹窗每次显示/旋转都重算区域定位，
     * 拖拽位置仅作会话内平移，不应以窗口绝对偏移形式恢复。
     */
    protected static boolean isRegionAnchoredPopup(PopupWindow popupWindow) {
        if (popupWindow == null) return false;
        View content = popupWindow.getContentView();
        return content instanceof DragFrameLayout
                && ((DragFrameLayout) content).centerRegionId != View.NO_ID;
    }

    /**
     * 该弹窗是否为 {@link #setupDraggablePopup} 包装成「铺满窗口的包装层」的弹窗：
     * 供 {@link ImePopupAdjuster} 区分抬升方式——包装层弹窗收缩窗口并把内容贴底，
     * 而猜拳/先后攻/位置选择这类自定位弹窗改为整窗上移（尺寸不动，不裁剪内容）。
     */
    public static boolean isWrappedFullscreenPopup(PopupWindow popupWindow) {
        return popupWindow != null && popupWindow.getContentView() instanceof DragFrameLayout;
    }
    /**
     * 该弹窗当前是否收缩成底部横条（包装层的收缩态）：未包装、无包装层或处于展开态返回 false。
     * 供 {@link ImePopupAdjuster} 跳过收缩态弹窗——横条几何（贴区域底部、与聊天输入框同高同位）
     * 由各 dialog 自己按布局变化解算，输入法抬升联动介入会把横条推离输入框或撑回整块窗口。
     */
    public static boolean isPopupCollapsed(PopupWindow popupWindow) {
        if (popupWindow == null) return false;
        View content = popupWindow.getContentView();
        return content instanceof DragFrameLayout && ((DragFrameLayout) content).collapsed;
    }
    /**
     * 该弹窗当前是否处于 {@link ImePopupAdjuster} 驱动的输入法自动最小化态：
     * 供联动工具区分「自己收缩的」（需继续每帧重锚横条底边）与「dialog 自己手动收缩的」
     * （交由其自身锚定逻辑，本工具不介入）。
     */
    public static boolean isPopupAutoCollapsed(PopupWindow popupWindow) {
        if (popupWindow == null) return false;
        View content = popupWindow.getContentView();
        return content instanceof DragFrameLayout
                && ((DragFrameLayout) content).imeAutoCollapsed
                && ((DragFrameLayout) content).collapsed;
    }
    /**
     * 程序化把弹窗自动最小化为底部横条，并把横条底边贴到 {@code barBottomScreenY}
     * （屏幕坐标）。未启用收缩的弹窗会动态补装横条与把手；已是用户手动收缩态的只接管
     * 横条重锚不重复收缩。返回 false 表示弹窗结构不可收缩（调用方应跳过该弹窗）。
     */
    public static boolean collapsePopupForIme(PopupWindow popupWindow, int barBottomScreenY) {
        if (popupWindow == null) return false;
        View content = popupWindow.getContentView();
        return content instanceof DragFrameLayout
                && ((DragFrameLayout) content).collapseForIme(barBottomScreenY);
    }

    /** 撤销输入法自动最小化，恢复原始尺寸（非本工具收缩的弹窗为无操作） */
    public static boolean expandPopupFromIme(PopupWindow popupWindow) {
        if (popupWindow == null) return false;
        View content = popupWindow.getContentView();
        return content instanceof DragFrameLayout && ((DragFrameLayout) content).expandFromIme();
    }

    /** 收缩态横条视图（未收缩/未包装返回 null）：供联动工具实测横条底边与目标线是否收敛 */
    public static View getCollapseBarView(PopupWindow popupWindow) {
        if (popupWindow == null) return null;
        View content = popupWindow.getContentView();
        return content instanceof DragFrameLayout ? ((DragFrameLayout) content).collapseBar : null;
    }
    /**
     * 弹窗参与输入法抬升联动的「内容根」：{@code setupDraggablePopup} 包装成全窗口的弹窗，
     * 其区域锚定 margin 与贴底重力都作用在包装层的第一个子 view（真正的对话框根布局）上，
     * 故抬升/还原必须改这个子 view 而不是包装层本身；未包装的自定位弹窗返回值即其内容视图。
     */
    public static View liftedContentView(PopupWindow popupWindow) {
        if (popupWindow == null) return null;
        View root = popupWindow.getContentView();
        if (root instanceof DragFrameLayout && ((DragFrameLayout) root).getChildCount() > 0) {
            return ((DragFrameLayout) root).getChildAt(0);
        }
        return root;
    }
    /**
     * 让弹窗按已登记的区域重新定位（未登记过区域的弹窗为无操作）：
     * 供 {@link ImePopupAdjuster} 在输入法收起、窗口恢复全尺寸后补做被推迟的区域锚定，
     * 使弹窗回到 layout_game_right 中央（用户规格）。收缩态改为逐轮重锚横条，使其跟随
     * 降回原位的聊天输入框贴回其上方（用户规格：已最小化的 dialog 随输入法一起升降）。
     */
    public static void reanchorPopup(PopupWindow popupWindow) {
        if (popupWindow == null) return;
        View wrapper = popupWindow.getContentView();
        if (!(wrapper instanceof DragFrameLayout)) return;
        DragFrameLayout layer = (DragFrameLayout) wrapper;
        if (layer.collapsed) {
            layer.refreshCollapseAnchors();
        } else {
            layer.reanchorToRegion();
        }
    }

    /** 清除本弹窗持久化的拖拽位置，下次显示回到默认居中布局。 */
    public void clearSavedPosition() {
        lastX = 0;
        lastY = 0;
        hasSavedPosition = false;
        prefs.edit().remove(dialogId + KEY_X).remove(dialogId + KEY_Y).apply();
    }

    /** 按 dialogId 清除某个弹窗持久化的拖拽位置（供对话框释放自身布局时调用，不影响其他弹窗）。 */
    public static void clearPosition(Context context, String dialogId) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(dialogId + KEY_X)
            .remove(dialogId + KEY_Y)
            .apply();
    }

    public static void resetAllPositions(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
    }
    public void setupDraggableView(View targetView) {
        if (!ENABLE_DRAG) return;
        if (!(targetView instanceof ViewGroup)) {
            setupSimpleDraggableView(targetView);
            return;
        }

        final ViewGroup viewGroup = (ViewGroup) targetView;
        final float[] grabDX = new float[1];
        final float[] grabDY = new float[1];
        final boolean[] dragging = new boolean[]{false};

        View.OnTouchListener dragListener = (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    grabDX[0] = event.getRawX() - targetView.getLeft();
                    grabDY[0] = event.getRawY() - targetView.getTop();
                    dragging[0] = false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (!dragging[0]) {
                        float dx = event.getRawX() - (targetView.getLeft() + grabDX[0]);
                        float dy = event.getRawY() - (targetView.getTop() + grabDY[0]);
                        if (Math.abs(dx) < 8 && Math.abs(dy) < 8) {
                            return false;
                        }
                        dragging[0] = true;
                        targetView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    }
                    int newX = (int) (event.getRawX() - grabDX[0]);
                    int newY = (int) (event.getRawY() - grabDY[0]);
                    targetView.setLeft(newX);
                    targetView.setTop(newY);
                    targetView.setRight(newX + targetView.getWidth());
                    targetView.setBottom(newY + targetView.getHeight());
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        savePosition(targetView.getLeft(), targetView.getTop());
                    }
                    dragging[0] = false;
                    return false;
            }
            return false;
        };

        setTouchListenerRecursively(viewGroup, dragListener);
        targetView.setOnTouchListener(dragListener);
    }

    protected void setTouchListenerRecursively(ViewGroup parent, View.OnTouchListener listener) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            child.setOnTouchListener(listener);
            
            if (child instanceof ViewGroup) {
                setTouchListenerRecursively((ViewGroup) child, listener);
            }
        }
    }
    protected void setupSimpleDraggableView(View targetView) {
        if (!ENABLE_DRAG) return;
        final float[] grabDX = new float[1];
        final float[] grabDY = new float[1];
        final boolean[] dragging = new boolean[]{false};

        targetView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    grabDX[0] = event.getRawX() - targetView.getLeft();
                    grabDY[0] = event.getRawY() - targetView.getTop();
                    dragging[0] = false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (!dragging[0]) {
                        float dx = event.getRawX() - (targetView.getLeft() + grabDX[0]);
                        float dy = event.getRawY() - (targetView.getTop() + grabDY[0]);
                        if (Math.abs(dx) < 8 && Math.abs(dy) < 8) {
                            return false;
                        }
                        dragging[0] = true;
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    }
                    int newX = (int) (event.getRawX() - grabDX[0]);
                    int newY = (int) (event.getRawY() - grabDY[0]);
                    targetView.setLeft(newX);
                    targetView.setTop(newY);
                    targetView.setRight(newX + targetView.getWidth());
                    targetView.setBottom(newY + targetView.getHeight());
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        savePosition(targetView.getLeft(), targetView.getTop());
                    }
                    dragging[0] = false;
                    return false;
            }
            return false;
        });
    }
}

