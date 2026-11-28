package cn.garymb.ygomobile.render.card;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.Map;
import java.util.TreeMap;

import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardLocation;
import ocgcore.enums.CardType;

/**
 * 卡片列表弹窗（CardSelectDialog / CardDisplayDialog）的「点击即显示详情与通讯状态标签、标签常显」辅助器。
 * <p>
 * 交互（点击即常显，用户规格：不再需要长按）：
 * - ACTION_DOWN：立即回调 onCardPressed（宿主据此在 CardDetailPanel 显示该卡详情），同时把气泡
 *   锚点换成本卡并<b>立刻</b>显示其通讯状态标签 —— 换锚的这一拍先收起上一张卡片的气泡，
 *   因此「点击其他卡片」就是上一张 cardtip 唯一的自动隐藏时机；另起 {@link #HOLD_THRESHOLD_MS}
 *   计时，只用于抬手时判定「按满阈值不视为点击」，与气泡显隐无关；
 * - ACTION_MOVE：手指保持按下滑到另一张卡上时（用户规格），按下动画、详情面板与状态气泡
 *   一并切到新卡（见 {@link #onMove}）；离开卡片（卡间间隙/空白）不切换，抬手按最后
 *   停留的卡结算。卡片上的按下手势由卡片独占、包装层不拖窗（见
 *   {@code DraggablePopupHelper#registerGestureExclusiveView}），否则滑动超过 8px 就会被
 *   拖窗抢占并给卡片补发 CANCEL，MOVE 根本到不了本类；
 * - ACTION_UP：清定时器，未超过阈值才回调 onSlotClick 视为选中（目标为最后按下的卡）；
 *   <b>不收起气泡</b> —— 点击列表中某张卡后其标签一直显示、不自动消失（旧语义的
 *   「抬手即 EGET_ELEMENT_LEFT 隐藏」已废弃）；
 * - ACTION_CANCEL（含拖动弹窗时包装层补发的 CANCEL）：清定时器、不触发选中，气泡按其锚定卡片
 *   继续保留；
 * - 宿主翻页 / 刷新槽位 / 关闭弹窗（{@link #hide()}）解除锚点并收起 —— 槽位与卡片的对应关系
 *   已变，旧标签成为残影。
 * <p>
 * 标签容器（列表弹窗）：标签不再挂到 DraggablePopupHelper 的包装层，而是由 {@link #createTipHost}
 * 建一个包裹对话框内容根的宿主（FrameLayout，尺寸取内容、clipChildren=false），标签作为宿主内
 * 第 2 个子 View 与卡片同处「内容层」坐标系 —— 拖动弹窗时包装层只把平移量施加在宿主上
 * （applyDragOffsetToChildren），标签与卡片相对位置不变、随宿主整体平移，因此移动过程中时刻
 * 精准贴住卡片，不再依赖布局回调补落位（旧外挂方式拖动期间标签停在原位、抬手才被重算）。
 * 宿主的测量忽略标签（见 TipHostLayout），标签不会把自身高度并入弹窗高度。
 * <p>
 * 位置一律由 LayoutParams margin 承载，绝不用 setX/setY（见 {@link #layoutTip}）：宿主/包装层
 * 会把拖拽与区域重锚的平移量写进子视图的 translation（applyDragOffsetToChildren /
 * clearDragOffset，由 centerPopupInRegion、onSizeChanged、收缩、旋转重排触发），translation 里
 * 承载的位置会被这些路径清零，标签随即贴到父层原点，即屏幕左上角（用户反馈的「飞到左上角」）。
 * margin 与平移正交后仍会残留一类错位：重锚只改内容子视图的 margin，标签的 margin 不在其列，
 * 标签常显期间恰好重锚（输入法升降、区域重新居中、旋转后重解宽度）就会让标签停在旧位置，
 * 故 {@link #requestReposition()} 在宿主/内容子视图 bounds 变化时补一次落位（仅限标签仍
 * 有锚点且可见；锚卡随收缩/翻页变为不可见时 {@link #showTip()} 就地收起，不会被布局回调再变出来）。
 * <p>
 * 标签文字 = 列表提示 ClientField::ShowCardInfoInList（event_handler.cpp L2919-2964）叠加
 * 场上悬停 stTip 的通讯当前值段（event_handler.cpp L1676-1737）：卡名/别名之外，即时显示
 * 通讯查询给出的当前攻/守、星数/LINK、种族/属性、灵摆刻度与计数器。
 */
public class CardStatusTipHelper {

    /**
     * 按下超过该时长不再触发选中（纯「看」的手势）；气泡显隐与本时长无关，按下即显示
     */
    public static final long HOLD_THRESHOLD_MS = 300L;

    /**
     * 标签最大宽度：gframe SetStaticText(stCardListTip, 320 * xScale, ...)
     */
    private static final int TIP_MAX_WIDTH_DP = 320;
    /**
     * 标签背景：game.cpp L1409 stCardListTip 背景 0x6011113d（CARD_LIST_SELECTED_BACKGROUND_COLOR）
     */
    private static final int TIP_BACKGROUND = YGOUtil.c(R.color.card_list_selected_bg);
    private static final float TIP_TEXT_SIZE_SP = 11f;
    /** 场上/手卡悬浮气泡字体：比列表内 cardtip 小一号 */
    private static final float FIELD_TIP_TEXT_SIZE_SP = TIP_TEXT_SIZE_SP - 1f;
    private static final int TIP_PADDING_DP = 4;
    /**
     * 左右留白：对应 gframe 钳制用的 10 * xScale
     */
    private static final int TIP_EDGE_MARGIN_DP = 4;
    private static final int TIP_ANCHOR_GAP_DP = 2;
    private static final int MAX_SLOTS = 16;

    // 按下动画（参考 CGUIButton::draw 按下偏移语义：无 PressedImage 时整图按
    // EGDS_BUTTON_PRESSED_IMAGE_OFFSET 下沉，形成"陷下去"的反馈）：
    // Android 侧用 轻微下沉 + 微缩放 + 变暗 近似，抬手复原
    private static final float PRESS_SCALE = 0.95f;
    private static final float PRESS_ALPHA = 0.82f;
    private static final float PRESS_OFFSET_DP = 2f;
    private static final long PRESS_DURATION_MS = 90L;
    private static final long RELEASE_DURATION_MS = 150L;

    /**
     * TYPE_RITUAL|TYPE_FUSION|TYPE_SYNCHRO|TYPE_XYZ|TYPE_LINK|TYPE_SPSUMMON
     */
    private static final long SP_SUMMON_TYPE_MASK =
            CardType.Ritual.getId() | CardType.Fusion.getId() | CardType.Synchro.getId()
                    | CardType.Xyz.getId() | CardType.Sp_Summon.getId() | CardType.Link.getId();

    public interface OnCardPressed {
        void onCardPressed(int slot);
    }

    public interface OnSlotClick {
        void onSlotClick(int slot);
    }

    public interface TipProvider {
        String getTipText(int slot);
    }

    private final Context context;
    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ViewGroup overlay;
    private final View[] anchors = new View[MAX_SLOTS];

    private TextView tipView;
    private OnCardPressed pressedListener;
    private OnSlotClick clickListener;
    private TipProvider tipProvider;

    /** 气泡当前锚定的槽位：点击即换锚，抬手/取消都不改，直到下一次点击其他卡片或宿主 {@link #hide()} */
    private int pinnedSlot = -1;
    /**
     * 本次手势是否已按满 {@link #HOLD_THRESHOLD_MS}：仅作为「长按不视为点击」的判据，
     * 与气泡是否显示无关（气泡在按下当拍就已显示）
     */
    private boolean holdFired;

    /**
     * 本次手势当前按下的槽位：DOWN 起手记录，ACTION_MOVE 滑到另一张卡时切换
     * （按下动画、详情与气泡随之换卡），UP 以它为点击目标；手势结束/宿主刷新后为 -1
     */
    private int pressedSlot = -1;

    private final Runnable holdTask = new Runnable() {
        @Override
        public void run() {
            holdFired = true;
        }
    };

    /** 已排队一次「重锚后补落位」：一次布局过程可能连发多个 bounds 变化回调，只留一份 */
    private boolean repositionPending;

    /**
     * 补落位任务：到执行时再查一次「气泡是否仍在显示」，翻页/刷新/关闭已由 {@link #hide()}
     * 解除锚点，不重新显示；抬手与取消都不解除锚点，气泡跟着卡片重落位
     */
    private final Runnable repositionTask = new Runnable() {
        @Override
        public void run() {
            repositionPending = false;
            if (!isTipShowing()) return;
            showTip();
        }
    };

    public CardStatusTipHelper(Context context, View contentRoot) {
        this(context, contentRoot, null);
    }

    /**
     * @param tipHost 标签宿主（见 {@link #createTipHost}）：非空时标签直接作为它的子视图，
     *                与卡片同在对话框内容层，拖动弹窗时随内容一起平移；为空时沿用旧行为
     *                （自 contentRoot 向上找 DraggablePopupHelper 的全窗口包装层）。
     */
    public CardStatusTipHelper(Context context, View contentRoot, ViewGroup tipHost) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
        this.overlay = tipHost != null ? tipHost : findTipOverlay(contentRoot);
        // 区域重锚/窗口尺寸变化都是改内容子视图的 margin（或重解其宽/弹窗高），只会引发对应
        // bounds 变化回调：监听包装层本身（窗口 resize、旋转）与其第 0 个子视图（区域重新居中、
        // 自定义旋转重解宽度），变化后在气泡仍显示时重算一次 margin，使气泡跟回它的卡片
        if (overlay != null) {
            View.OnLayoutChangeListener anchorChanged =
                    (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> requestReposition();
            overlay.addOnLayoutChangeListener(anchorChanged);
            if (overlay.getChildCount() > 0) {
                overlay.getChildAt(0).addOnLayoutChangeListener(anchorChanged);
            }
        }
    }

    /**
     * 自内容层往上找第一个 FrameLayout 祖先（即 DraggablePopupHelper 的全窗口包装层）
     */
    private static ViewGroup findTipOverlay(View contentRoot) {
        if (contentRoot == null) return null;
        ViewParent p = contentRoot.getParent();
        while (p != null) {
            if (p instanceof FrameLayout) return (ViewGroup) p;
            if (!(p instanceof View)) break;
            p = ((View) p).getParent();
        }
        // 找不到 FrameLayout 时：如果 contentRoot 本身就是 ViewGroup，取它的第一个子 View 继续尝试
        if (contentRoot instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) contentRoot;
            if (vg.getChildCount() > 0) {
                View child = vg.getChildAt(0);
                return findTipOverlay(child);
            }
        }
        return null;
    }

    public void setOnCardPressedListener(OnCardPressed l) {
        this.pressedListener = l;
    }

    public void setOnSlotClickListener(OnSlotClick l) {
        this.clickListener = l;
    }

    public void setTipProvider(TipProvider p) {
        this.tipProvider = p;
    }

    /**
     * 接管槽位触摸。listener 对全部事件返回 true，View 自身的点击/长按机制完全不参与，
     * 按下态由 setPressed 手动维护，避免 onTouchEvent 在缺少 DOWN 的情况下误触发 performClick。
     * slotView 显式置为可点击，保证 ACTION_DOWN 一定投递到本 View（"按下卡片图"的直接命中面），
     * 并对 anchor（卡片图）施加简单的按下/抬起动画。slotView 同时登记为手势独占视图：
     * 按住后滑到另一张卡时由 {@link #onMove} 换卡换详情，包装层不把这次手势抢去拖窗。
     */
    public void attach(final int slot, View slotView, View anchor) {
        if (slotView == null || slot < 0 || slot >= MAX_SLOTS) return;
        anchors[slot] = anchor != null ? anchor : slotView;
        slotView.setClickable(true);
        slotView.setFocusable(true);
        // 卡片按下期间手势独占：按住滑动可跨卡切换详情，包装层不得把这次手势抢去拖窗
        //（否则滑动超过拖窗阈值即被补发 CANCEL，卡片收不到 ACTION_MOVE）
        DraggablePopupHelper.registerGestureExclusiveView(slotView);
        slotView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        applyPressEffect(anchors[slot], true);
                        onDown(slot);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        onMove(event.getRawX(), event.getRawY());
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        onUp(slot);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        releasePressedAnchor();
                        onCancel();
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
    }

    /**
     * 简单按下动画：按下下沉若干 dp + 微缩放 + 变暗，抬手/取消复原
     */
    private void applyPressEffect(View v, boolean pressed) {
        if (v == null) return;
        v.animate().cancel();
        if (pressed) {
            v.animate()
                    .scaleX(PRESS_SCALE).scaleY(PRESS_SCALE)
                    .translationY(PRESS_OFFSET_DP * density)
                    .alpha(PRESS_ALPHA)
                    .setDuration(PRESS_DURATION_MS)
                    .start();
        } else {
            v.animate()
                    .scaleX(1f).scaleY(1f)
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(RELEASE_DURATION_MS)
                    .start();
        }
    }

    /**
     * 按下即换锚：先收起上一张卡片的气泡，再立刻显示本卡片的通讯状态标签（不再等长按），
     * 详情面板同样在本拍显示
     */
    private void onDown(int slot) {
        handler.removeCallbacks(holdTask);
        holdFired = false;
        pressedSlot = slot;
        pinnedSlot = slot;
        hideBubble();
        if (pressedListener != null) pressedListener.onCardPressed(slot);
        showTip();
        handler.postDelayed(holdTask, HOLD_THRESHOLD_MS);
    }

    /**
     * 按住滑动换卡（用户规格）：手指保持按下从一张卡滑到另一张卡时，不必抬起即可查看新卡详情。
     * 触点进入另一张已附着的卡片才切换；落在卡间间隙/空白处不切换（保持最后一张，避免
     * 滑过间隙时反复闪断），抬手时按最后停留的卡结算点击
     */
    private void onMove(float rawX, float rawY) {
        if (pressedSlot < 0) return;
        int slot = findSlotAt(rawX, rawY);
        if (slot < 0 || slot == pressedSlot) return;
        switchPressedSlot(slot);
    }

    /** 换卡：旧卡复原按下动画，新卡进入按下态，详情面板与通讯状态标签一并切到新卡 */
    private void switchPressedSlot(int newSlot) {
        int old = pressedSlot;
        pressedSlot = newSlot;
        if (old >= 0 && old < MAX_SLOTS) {
            applyPressEffect(anchors[old], false);
        }
        applyPressEffect(anchors[newSlot], true);
        pinnedSlot = newSlot;
        hideBubble();
        if (pressedListener != null) pressedListener.onCardPressed(newSlot);
        showTip();
    }

    /** 屏幕坐标命中的已附着卡片槽位（含可见性与布局有效性校验），都不命中返回 -1 */
    private int findSlotAt(float rawX, float rawY) {
        int[] loc = new int[2];
        for (int i = 0; i < MAX_SLOTS; i++) {
            View view = anchors[i];
            if (view == null || !view.isShown()
                    || view.getWidth() <= 0 || view.getHeight() <= 0) {
                continue;
            }
            view.getLocationOnScreen(loc);
            if (rawX >= loc[0] && rawX < loc[0] + view.getWidth()
                    && rawY >= loc[1] && rawY < loc[1] + view.getHeight()) {
                return i;
            }
        }
        return -1;
    }

    /** 复原当前按下卡的按下动画并结束本次手势的按下跟踪（换卡时旧卡已在 {@link #switchPressedSlot} 复原） */
    private void releasePressedAnchor() {
        if (pressedSlot >= 0 && pressedSlot < MAX_SLOTS) {
            applyPressEffect(anchors[pressedSlot], false);
        }
        pressedSlot = -1;
    }

    /**
     * 抬手只判定「是否视为点击」，不收起气泡：本次点击卡片的标签一直显示，
     * 到下一次点击其他卡片（{@link #onDown} 换锚）或宿主刷新/翻页/关闭（{@link #hide()}）为止。
     * 点击目标取最后按下的卡（按住滑到另一张卡再抬手即选中该卡）；按下动画在本拍复原。
     * 位置由 margin 承载 + {@link #requestReposition()} 跟锚，常显不会再触发旧版
     * 「抬手后标签被父层重锚清零平移而停在屏幕左上角」的问题。
     */
    private void onUp(int slot) {
        handler.removeCallbacks(holdTask);
        boolean held = holdFired;
        holdFired = false;
        int clickSlot = pressedSlot >= 0 ? pressedSlot : slot;
        releasePressedAnchor();
        if (!held && clickListener != null) clickListener.onSlotClick(clickSlot);
    }

    /** 手势被打断（拖拽弹窗、手指移出）：不触发选中，已显示的气泡仍随其锚定卡片保留 */
    private void onCancel() {
        handler.removeCallbacks(holdTask);
        holdFired = false;
    }

    /**
     * 翻页 / 刷新槽位 / 关闭弹窗时调用：收起气泡并<b>解除锚点</b>。
     * 与 {@link #hideBubble()} 的区别是不再参与后续重锚 —— 槽位对应的卡片已换，旧锚点无意义
     */
    public void hide() {
        handler.removeCallbacks(holdTask);
        if (overlay != null) overlay.removeCallbacks(repositionTask);
        repositionPending = false;
        holdFired = false;
        pinnedSlot = -1;
        // 宿主刷新时手指可能仍按住卡片：一并复原按下动画并终止按下跟踪（槽位对应的卡已换）
        releasePressedAnchor();
        hideBubble();
    }

    /** 仅收起气泡视图、保留锚点（换锚前清场，及锚卡不可见时的收起） */
    private void hideBubble() {
        if (tipView != null) tipView.setVisibility(View.GONE);
    }

    /** 气泡当前是否有有效锚点且视图可见 —— 补落位的唯一前提 */
    private boolean isTipShowing() {
        return pinnedSlot >= 0 && tipView != null
                && tipView.getVisibility() == View.VISIBLE;
    }

    /**
     * 标签宿主/内容 bounds 变化后延后补一次落位。绝不能在 onLayout 回调里直接改
     * {@link #layoutTip}：那是 layout 过程中 setLayoutParams/requestLayout，Android 会记
     * 「layout 中请求布局」并再走一遍 traversal，极端情况下反复；post 出本次 traversal 后执行，
     * 且执行时重新判定「气泡仍有锚点且仍可见」（宿主 hide 已解除锚点，锚卡不可见已在
     * {@link #showTip()} 内收起），所以不会把已隐藏的气泡再变出来，也不影响 margin 定位的既有结论。
     * 拖动弹窗不改 bounds、不触发本方法 —— 标签随宿主平移，无需逐帧重排（用户规格）。
     */
    private void requestReposition() {
        if (repositionPending || overlay == null) return;
        if (!isTipShowing()) return;
        repositionPending = true;
        overlay.post(repositionTask);
    }

    private void showTip() {
        int slot = pinnedSlot;
        if (overlay == null || tipProvider == null || slot < 0 || slot >= MAX_SLOTS) return;
        View anchor = anchors[slot];
        if (anchor == null || !anchor.isShown()) {
            // 锚卡已不可见（翻页、收缩、槽位 GONE）：就地收起，不让旧气泡成为没有锚点的残影
            // （补落位路径依赖此判定：重锚后卡片已经不在原处时宁可没有气泡）
            hideBubble();
            return;
        }
        // 包装层已脱离窗口或尚未完成布局时几何不可解：本次不显示，以免按退化度量把标签贴到
        // 包装层原点（屏幕左上角）
        if (!overlay.isAttachedToWindow() || overlay.getWidth() <= 0 || overlay.getHeight() <= 0) {
            return;
        }
        String text = tipProvider.getTipText(slot);
        // 对应 C++ if(str.length() > 0)：无内容时不显示标签（上一张的气泡已在换锚时收起，
        // 此处直接返回即保持空白），但锚点保留，下次点击/重锚再解
        if (text == null || text.isEmpty()) return;

        TextView tv = ensureTipView();
        tv.setText(text);
        tv.setVisibility(View.VISIBLE);
        int maxW = (int) (TIP_MAX_WIDTH_DP * density + 0.5f);
        tv.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int w = tv.getMeasuredWidth();
        int h = tv.getMeasuredHeight();

        int[] aLoc = new int[2];
        anchor.getLocationOnScreen(aLoc);
        int[] oLoc = new int[2];
        overlay.getLocationOnScreen(oLoc);
        // 限幅基准取弹窗窗口而非标签宿主：宿主只有对话框那么大，按宿主限幅会把卡片下方的
        // 标签顶上卡片、也会把比对话框宽的标签压窄；窗口范围才是标签的实际可见边界。
        // 旧的外挂方式宿主即全窗口包装层，窗口基准与其原行为一致（偏移为 0）。
        View rootView = overlay.getRootView();
        int[] rLoc = new int[2];
        rootView.getLocationOnScreen(rLoc);
        float hostOffX = oLoc[0] - rLoc[0];
        float hostOffY = oLoc[1] - rLoc[1];
        int winW = rootView.getWidth() > 0 ? rootView.getWidth() : overlay.getWidth();
        int winH = rootView.getHeight() > 0 ? rootView.getHeight() : overlay.getHeight();
        float margin = TIP_EDGE_MARGIN_DP * density;
        // x = 锚点水平中心、y = 锚点下边缘（gframe L2952-2953），坐标相对标签宿主
        float cx = aLoc[0] + anchor.getWidth() / 2f - oLoc[0];
        float left = cx - w / 2f;
        float minLeft = margin - hostOffX;
        float maxLeft = winW - w - margin - hostOffX;
        if (maxLeft < minLeft) maxLeft = minLeft;
        if (left < minLeft) left = minLeft;
        if (left > maxLeft) left = maxLeft;
        float top = aLoc[1] + anchor.getHeight() - oLoc[1] + TIP_ANCHOR_GAP_DP * density;
        // 纵向同样限幅到窗口：宿主 clipChildren=false，标签可以越出宿主绘制到窗口内（旧写法
        // 按宿主高限幅，靠底行卡片不限住就会把多行状态文字直接截掉或顶到卡片上）
        float maxTop = winH - h - margin - hostOffY;
        if (maxTop < margin) maxTop = margin;
        if (top > maxTop) top = maxTop;
        layoutTip(tv, Math.round(left), Math.round(top));
    }

    /**
     * 以 LayoutParams margin 落位标签：不用 setX/setY —— translation 正是 DragFrameLayout 施加拖拽/
     * 区域重锚平移的字段（applyDragOffsetToChildren 会把「直接子视图」的 translation 覆写为拖拽量，
     * clearDragOffset 则覆写为 0）：内部宿主模式下被平移的是宿主（标签的父层），标签自身 translation
     * 必须保持 0；旧包装层模式下标签自身就是被覆写的子视图，位置放在 translation 里会在重锚时
     * 被清零而飞回屏幕左上角。margin 与平移正交，落位时再把标签自身 translation 归零，免历史
     * 拖拽量叠加到 margin 位置上（拖拽起手即发 CANCEL，但标签常显不因此收起，靠父层统一施加的
     * 平移跟着弹窗一起移动）。
     */
    private void layoutTip(TextView tv, int left, int top) {
        tv.setTranslationX(0f);
        tv.setTranslationY(0f);
        ViewGroup.LayoutParams raw = tv.getLayoutParams();
        FrameLayout.LayoutParams lp;
        if (raw instanceof FrameLayout.LayoutParams) {
            lp = (FrameLayout.LayoutParams) raw;
            // 已在该位就收尾：不再 setLayoutParams（免多走一轮 traversal），也给
            // 「bounds 变化 → 补落位」一个幂等终点，不会因父层度量而来回自我重排
            if (lp.gravity == (Gravity.TOP | Gravity.LEFT)
                    && lp.leftMargin == left && lp.topMargin == top) {
                return;
            }
        } else {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        // 用 LEFT 而非 START：位置是按屏幕坐标向左解算的，START 在 RTL 下会改成贴右缘取
        // rightMargin 语义，使 margin 与实际绘制边不符
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = left;
        lp.topMargin = top;
        tv.setLayoutParams(lp);
    }

    /**
     * 为列表弹窗创建「卡片状态标签宿主」：宿主包裹对话框内容根，并以内容尺寸作为自身尺寸
     * （标签不参与测量，见 {@link TipHostLayout}），标签作为宿主最后添加的子视图绘制在内容
     * 之上、可越出宿主边界（clipChildren=false，位置仍限幅在弹窗窗口内）。
     * <p>
     * 关键收益：拖动弹窗时 DraggablePopupHelper 只把平移量施加在包装层第 0 个子视图（即本宿主）
     * 上，标签与卡片同在宿主坐标系内、相对位置不变，移动过程中标签始终精准贴着卡片，不再依赖
     * 布局回调补落位（旧的外挂方式拖动期间标签停在旧位置，抬手才被重算）。
     * <p>
     * 内容根背景上移到宿主：收缩横条等位置通过「包装层第 0 个子视图」读取对话框背景贴图
     * （styleBarWithDialogContent），直接包一层会让那里读不到原背景。
     */
    public static FrameLayout createTipHost(Context context, View content) {
        TipHostLayout host = new TipHostLayout(context);
        host.setClipChildren(false);
        host.setClipToPadding(false);
        Drawable bg = content.getBackground();
        if (bg != null) {
            content.setBackground(null);
            host.setBackground(bg);
        }
        host.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return host;
    }

    /**
     * 标签宿主容器：测量时只按内容子视图（第 0 个子视图）决定宿主尺寸，标签（后加子视图）
     * 不参与 —— FrameLayout 以 WRAP_CONTENT 测量时会把标签高度/底部 margin 并入弹窗高度，
     * 标签随卡片位置变化时弹窗尺寸就会跟着变。内容尺寸以 EXACT 规格交给常规测量，标签仍是
     * 真实宽高可供落位；标签自身的测量不受宿主（对话框）宽度钳制，保持既有 maxWidth 语义。
     */
    static final class TipHostLayout extends FrameLayout {
        TipHostLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (getChildCount() == 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            View content = getChildAt(0);
            measureChildWithMargins(content, widthMeasureSpec, 0, heightMeasureSpec, 0);
            int w = content.getMeasuredWidth();
            int h = content.getMeasuredHeight();
            ViewGroup.LayoutParams lp = content.getLayoutParams();
            if (lp instanceof MarginLayoutParams) {
                w += ((MarginLayoutParams) lp).leftMargin + ((MarginLayoutParams) lp).rightMargin;
                h += ((MarginLayoutParams) lp).topMargin + ((MarginLayoutParams) lp).bottomMargin;
            }
            int cw = resolveSize(Math.max(w, getSuggestedMinimumWidth()), widthMeasureSpec);
            int ch = resolveSize(Math.max(h, getSuggestedMinimumHeight()), heightMeasureSpec);
            super.onMeasure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }

        @Override
        protected void measureChildWithMargins(View child, int parentWidthMeasureSpec,
                                               int widthUsed, int parentHeightMeasureSpec,
                                               int heightUsed) {
            if (getChildCount() > 0 && child != getChildAt(0)) {
                // 标签：按自身 maxWidth 测量，不被宿主宽度钳制（位置由 margin 承载，可越出宿主绘制）
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                child.measure(
                        getChildMeasureSpec(
                                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0, lp.width),
                        getChildMeasureSpec(
                                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0, lp.height));
                return;
            }
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed,
                    parentHeightMeasureSpec, heightUsed);
        }
    }

    private TextView ensureTipView() {
        if (tipView != null) return tipView;
        int maxW = (int) (TIP_MAX_WIDTH_DP * density + 0.5f);
        TextView tv = new TextView(context);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TIP_TEXT_SIZE_SP);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(TIP_BACKGROUND);
        int p = (int) (TIP_PADDING_DP * density + 0.5f);
        tv.setPadding(p, p, p, p);
        // maxWidth 保证父层复核测量时也不会超过 320dp（宿主宽度较小时由 TipHostLayout 放宽约束），位置保持
        tv.setMaxWidth(maxW);
        tv.setVisibility(View.GONE);
        tv.setClickable(false);
        tv.setLongClickable(false);
        tv.setFocusable(false);
        overlay.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT));
        tipView = tv;
        return tv;
    }

    /**
     * 列表项 → 场上实时卡片。location 含 LOCATION_OVERLAY 时按「超量怪兽格 + 素材序号」解析
     * （协议与 CmdMenuDialog 两种写法都覆盖：0x84 之类带基址的标志，以及单独的 0x80）。
     */
    public static ClientCard resolveLiveCard(GameField field, int viewControler,
                                                       int location, int sequence, int subSeq) {
        if (field == null) return null;
        int overlayBit = CardLocation.Overlay.value();
        if ((location & overlayBit) != 0) {
            int base = location & 0x7f;
            if (base == 0) base = CardLocation.MonsterZone.value();
            ClientCard xyz = field.getCard(viewControler, base, sequence);
            if (xyz != null && subSeq >= 0 && subSeq < xyz.overlayed.size()) {
                return xyz.overlayed.get(subSeq);
            }
            return null;
        }
        return field.getCard(viewControler, location, sequence);
    }

    /**
     * 卡名 → 通讯当前值段（攻/守、星数/LINK+种族/属性、灵摆刻度、计数器） → 225 叠放素材 →
     * 224 正规特殊召唤 → *desc_hints → 216 在连锁%d发动 → 217 被连锁选择为对象。
     * 卡组内的卡只保留卡名（C++ location != LOCATION_DECK 才继续，与悬停 stTip L1625 同）。
     */
    public static String buildStatusText(GameField field, ClientCard card) {
        if (card == null) return null;
        DataManager dm = DataManager.get();
        StringBuilder sb = new StringBuilder();
        // 卡名用 displayCode：场上里侧卡一经公开（泡影类 POS_CHANGE 携码 / POS_REVEAL 解遮蔽）
        // 即可报出通讯卡名，遮蔽刷新洗回 code 后仍有 chain_code 备份
        //（对齐 event_handler.cpp L1673 mcard->code 恒可 hover 的语义）
        int cardCode = card.displayCode();
        if (cardCode != 0) {
            String name0 = dm.getName(cardCode);
            sb.append(name0);
            // 通讯中该卡当前被视为的卡名（QUERY_ALIAS）：与卡表本名不同才补一行「(别名)」，
            // 镜像 event_handler.cpp L1680-1683 / L1704-1707 的 GetName(alias) 括号显示
            if (card.alias != 0) {
                String aliasName = dm.getName(card.alias);
                if (aliasName != null && !aliasName.isEmpty() && !aliasName.equals(name0)) {
                    sb.append("\n(").append(aliasName).append(")");
                }
            }
        }
        if (card.location != CardLocation.Deck.value()) {
            appendLiveStatLines(sb, dm, card);
            if (card.overlayTarget != null) {
                appendLine(sb, dm.formatSystemString(225, "叠放于[%s](%d)下",
                        dm.getName(card.overlayTarget.displayCode()), card.overlayTarget.sequence + 1));
            }
            if ((card.status & GameField.STATUS_PROC_COMPLETE) != 0
                    && (card.type & SP_SUMMON_TYPE_MASK) != 0) {
                appendLine(sb, dm.getStringManager()
                        .getSystemString(224, "已用正规方法特殊召唤"));
            }
            if (card.descHints != null) {
                for (Map.Entry<Integer, Integer> e : card.descHints.entrySet()) {
                    appendLine(sb, "*" + dm.getDesc(e.getKey(), ""));
                }
            }
            if (field != null && field.chains != null) {
                for (int i = 0; i < field.chains.size(); i++) {
                    GameField.ChainInfo chit = field.chains.get(i);
                    if (chit == null) continue;
                    if (card == chit.chainCard) {
                        appendLine(sb, dm.formatSystemString(216, "在连锁%d发动", i + 1));
                    }
                    if (chit.chainCard != null && chit.targets != null
                            && chit.targets.contains(card)) {
                        appendLine(sb, dm.formatSystemString(217, "被连锁%d的[%s]选择为对象",
                                i + 1, dm.getName(chit.chainCard.displayCode())));
                    }
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 通讯中该卡的当前值行（镜像场上悬停 stTip，event_handler.cpp L1679-1716：atkstring/defstring、
     * ★/☆或LINK、race/attribute 同行、手卡灵摆刻度、计数器 [名]: 个数），数据全部取查询块
     * 写入的字段（updateQuery），不读 cards.db；详情面板只显卡表原值，故当前值≠原值时在该值
     * 后内联「(原…↑/↓)」注记（保留原差异行语义，对应 drawing.cpp DrawStatus 的变色文字信息量）。
     * type==0 表示未收到该卡查询（暗卡等），不输出任何当前值行；卡表查不到 cd（衍生物等）只显当前值。
     */
    private static void appendLiveStatLines(StringBuilder sb, DataManager dm, ClientCard card) {
        if (card.type == 0) return;
        StringManager sm = dm.getStringManager();
        int cardCode = card.displayCode();
        Card cd = cardCode != 0 ? dm.getCardManager().getCard(cardCode) : null;
        boolean monster = (card.type & CardType.Monster.getId()) != 0;
        boolean link = card.isLink();
        if (monster) {
            // 攻/守：优先查询拼好的 atkString/defString（<0→"?"、连接怪守→"-"，同 updateQuery）
            String atk = !card.atkString.isEmpty() ? card.atkString : statValue(card.attack);
            String def = !card.defString.isEmpty() ? card.defString : statValue(card.defense);
            StringBuilder ad = new StringBuilder(atk).append('/').append(def);
            if (cd != null) {
                if (card.attack != cd.Attack) {
                    ad.append("(攻原").append(statValue(cd.Attack))
                            .append(statDelta(card.attack, cd.Attack)).append(')');
                }
                if (!link && card.defense != cd.Defense) {
                    ad.append("(守原").append(statValue(cd.Defense))
                            .append(statDelta(card.defense, cd.Defense)).append(')');
                }
            }
            appendLine(sb, ad.toString());
            // 星数行（C++ L1686-1694：非连接★，有阶级则☆）与种族/属性同行（L1695-1698 " 种族/属性"）
            StringBuilder lv = new StringBuilder();
            if (link) {
                if (card.link > 0) lv.append("LINK-").append(card.link);
            } else {
                int star = card.level != 0 ? card.level : card.rank;
                if (star > 0) lv.append(card.rank != 0 ? '\u2606' : '\u2605').append(star);
            }
            if (cd != null && lv.length() > 0) {
                if (link) {
                    if (card.link != cd.getLinkNumber()) {
                        lv.append("(原LINK-").append(cd.getLinkNumber()).append(')');
                    }
                } else {
                    int star = card.level != 0 ? card.level : card.rank;
                    if (star != cd.getStar()) {
                        lv.append("(原").append(cd.getStar()).append(')');
                    }
                }
            }
            String raceStr = card.race != 0 ? sm.getRaceString(card.race) : "";
            String attrStr = card.attribute != 0 ? sm.getAttributeString(card.attribute) : "";
            if (!raceStr.isEmpty() || !attrStr.isEmpty()) {
                lv.append(' ').append(raceStr).append('/').append(attrStr);
                if (cd != null) {
                    if (card.race != 0 && card.race != cd.Race) {
                        lv.append("(原").append(sm.getRaceString(cd.Race)).append(')');
                    }
                    if (card.attribute != 0 && card.attribute != cd.Attribute) {
                        lv.append("(原").append(sm.getAttributeString(cd.Attribute)).append(')');
                    }
                }
            }
            if (lv.length() > 0) appendLine(sb, lv.toString());
        }
        // 灵摆刻度：C++ 仅两处输出——手卡（怪/灵摆怪 L1699-1702）与魔陷区非怪卡（L1708-1711）
        boolean pendulum = (card.type & CardType.Pendulum.getId()) != 0;
        boolean scalePlace = card.location == CardLocation.Hand.value()
                || (!monster && card.location == CardLocation.SpellZone.value());
        if (pendulum && scalePlace && (card.lScale > 0 || card.rScale > 0)) {
            boolean lsDiff = cd != null && card.lScale > 0 && card.lScale != cd.LeftScale;
            boolean rsDiff = cd != null && card.rScale > 0 && card.rScale != cd.RightScale;
            StringBuilder sc = new StringBuilder().append(card.lScale).append('/').append(card.rScale);
            if (lsDiff || rsDiff) {
                sc.append("(原").append(lsDiff ? cd.LeftScale : card.lScale)
                        .append('/').append(rsDiff ? cd.RightScale : card.rScale).append(')');
            }
            appendLine(sb, sc.toString());
        }
        // 计数器（C++ L1713-1716 "[名]: 个数"，std::map 升序——HashMap 按类型 id 排序对齐）
        if (!card.counters.isEmpty()) {
            TreeMap<Integer, Integer> sorted = new TreeMap<>(card.counters);
            for (Map.Entry<Integer, Integer> e : sorted.entrySet()) {
                appendLine(sb, "[" + dm.getCounterName(e.getKey()) + "]: " + e.getValue());
            }
        }
    }

    /**
     * 数值显示：负数按「?」处理（cards.db 中 -2/-3 表示 ?），同 CardDetailPanel 惯例
     */
    private static String statValue(int v) {
        return v < 0 ? "?" : String.valueOf(v);
    }

    private static String statDelta(int cur, int base) {
        if (cur < 0 || base < 0) return "";
        return cur > base ? "↑" : "↓";
    }

    private static void appendLine(StringBuilder sb, String text) {
        if (text == null || text.isEmpty()) return;
        if (sb.length() > 0) sb.append('\n');
        sb.append(text);
    }

    /**
     * 场上 / 手卡长按的同款悬浮标签。
     * <p>
     * GameFieldView 是 setZOrderOnTop(true) 的 GLSurfaceView，GL 曲面合成在 Activity 窗口之上，
     * 挂在 content 层的普通 View（即便抬高 elevation / bringToFront）仍会被卡片遮挡。故与
     * SpecEffectOverlay 采用同一图层约定：用全屏透明、不拦截触摸的 PopupWindow（独立窗口，位于
     * GL 曲面之上）承载气泡标签，稳定显示在卡片前面，不再被遮挡。
     * <p>
     * 已在屏时<b>就地复用同一窗口</b>（只换文字与落位），见 {@link #show}：决斗场按住浏览逐卡
     * 跟随必须走这条路——逐张销毁重建窗口每跳一张卡就空一帧（闪断），且窗口增删拖慢 UI 线程。
     * <p>
     * 锚定：我方卡片把气泡放在卡片「上边缘」之上；对方卡片对其视角是倒置 180° 的，其卡名/信息
     * 落在视觉下缘，故把气泡放在「下边缘上方」。卡片上下边缘由 GameFieldView.getCardScreenBounds
     * 投影得到的实际绘制四边形给出（bounds 为空时回退到触点）。字体比列表内 cardtip 小一号。
     * 两个对话框内的 cardtip 走 showTip() 挂弹窗包装层的路径，与此无关，位置与字号均保持不变。
     */
    public static final class FieldTip {
        private static PopupWindow sWindow;
        private static FrameLayout sRoot;
        private static TextView sTip;
        /** 气泡当前锚定的决斗场视图：只有同一视图上的在屏气泡才允许就地复用 */
        private static View sAnchor;

        public static void show(Activity activity, View anchor, String text,
                                float tapX, float tapY, float[] bounds, boolean mine) {
            if (activity == null || anchor == null || text == null || text.isEmpty()) {
                hide();
                return;
            }
            final float density = activity.getResources().getDisplayMetrics().density;
            if (isReusable(anchor)) {
                sTip.setText(text);
                // 显式测量后立刻重落位：不等下一轮 traversal，气泡与卡片同帧切换（无闪断）。
                // 规格与 FrameLayout 复测一致（WRAP_CONTENT + TextView maxWidth），故实测宽高不会偏离
                int maxW = (int) (TIP_MAX_WIDTH_DP * density + 0.5f);
                sTip.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                positionTip(sTip, sRoot, anchor, tapX, tapY, bounds, mine, density,
                        sTip.getMeasuredWidth(), sTip.getMeasuredHeight());
                return;
            }
            hide();
            View decor = activity.getWindow().getDecorView();
            if (decor == null || decor.getWindowToken() == null) return;

            final TextView tv = new TextView(activity);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, FIELD_TIP_TEXT_SIZE_SP);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.card_status_tip_bg);
            int pad = (int) (TIP_PADDING_DP * density + 0.5f);
            tv.setPadding(pad, pad, pad, pad);
            tv.setMaxWidth((int) (TIP_MAX_WIDTH_DP * density + 0.5f));
            tv.setText(text);

            final FrameLayout root = new FrameLayout(activity);
            // 落位在 showAtLocation 之后的 post 回调里完成：先置 INVISIBLE（仍参与测量布局），
            // 避免未定位的首帧把气泡画在窗口原点＝屏幕左上角，再由 positionTip 收尾可见
            root.setVisibility(View.INVISIBLE);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.START | Gravity.TOP;
            root.addView(tv, lp);

            PopupWindow window = new PopupWindow(root,
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // 纯展示层：不抢焦点、不拦截触摸，事件穿透到下层决斗场
            window.setFocusable(false);
            window.setOutsideTouchable(false);
            window.setTouchable(false);
            try {
                window.showAtLocation(decor, Gravity.NO_GRAVITY, 0, 0);
            } catch (Exception ignored) {
                return;
            }
            sWindow = window;
            sRoot = root;
            sTip = tv;
            sAnchor = anchor;

            // 待 PopupWindow 完成测量布局后再定位（此时 root 有真实宽高可做越界翻转钳制）
            final float[] fb = bounds;
            final boolean fm = mine;
            root.post(new Runnable() {
                @Override
                public void run() {
                    positionTip(tv, root, anchor, tapX, tapY, fb, fm, density,
                            tv.getWidth() > 0 ? tv.getWidth() : tv.getMeasuredWidth(),
                            tv.getHeight() > 0 ? tv.getHeight() : tv.getMeasuredHeight());
                }
            });
        }

        /** 在屏气泡可否就地换内容：同一决斗场视图、窗口仍在显示且已完成首帧落位 */
        private static boolean isReusable(View anchor) {
            return sWindow != null && sWindow.isShowing() && sTip != null && sRoot != null
                    && sAnchor == anchor && anchor.isAttachedToWindow()
                    // 首帧落位前 root 还是 INVISIBLE：此时换内容会被随后排队的一次旧触点定位覆盖，
                    // 宁可走重建路径
                    && sRoot.getVisibility() == View.VISIBLE;
        }

        private static void positionTip(TextView tv, FrameLayout root, View anchor,
                                        float tapX, float tapY, float[] bounds, boolean mine,
                                        float density, int w, int h) {
            // 已 hide()（含排队期间被隐藏）或本回调属于已被替换的旧气泡：不再落位
            if (sWindow == null || sTip != tv) return;
            int[] aLoc = new int[2];
            int[] rLoc = new int[2];
            anchor.getLocationInWindow(aLoc);
            root.getLocationInWindow(rLoc);
            float margin = TIP_EDGE_MARGIN_DP * density;
            float gap = 8f * density;
            int rootW = root.getWidth();
            int rootH = root.getHeight();

            // anchor(GameFieldView) 视图内坐标 + anchor 窗口偏移 = 窗口坐标；
            // root 铺满窗口，减去 root 窗口偏移即得 root 内坐标
            float cxWin;
            float edgeWin;
            if (bounds != null && bounds.length >= 3) {
                cxWin = aLoc[0] + bounds[0];
                // bounds[1]=卡片视觉顶边, bounds[2]=卡片视觉底边
                // 我方锚上边缘、对方锚下边缘（其卡面对我方倒置 180°，信息在视觉下缘）
                edgeWin = aLoc[1] + (mine ? bounds[1] : bounds[2]);
            } else {
                cxWin = aLoc[0] + tapX;
                edgeWin = aLoc[1] + tapY;
            }
            float cx = cxWin - rLoc[0];
            float edgeY = edgeWin - rLoc[1];

            float left = cx - w / 2f;
            float maxLeft = rootW - w - margin;
            if (maxLeft < margin) maxLeft = margin;
            if (left < margin) left = margin;
            if (left > maxLeft) left = maxLeft;

            // 默认把气泡放在参考边缘「之上」（气泡底边贴边缘上方 gap）；
            // 上方放不下则翻到边缘下方，再越界则贴边钳制
            float top = edgeY - gap - h;
            if (top < margin) top = edgeY + gap;
            if (top + h > rootH - margin) top = rootH - margin - h;
            if (top < margin) top = margin;

            tv.setX(left);
            tv.setY(top);
            // 已按卡片实际四边形落位，此前为避开左上角而隐藏的层次现在放开
            root.setVisibility(View.VISIBLE);
        }

        public static void hide() {
            if (sWindow != null) {
                try {
                    if (sWindow.isShowing()) sWindow.dismiss();
                } catch (Exception ignored) {
                }
                sWindow = null;
            }
            sRoot = null;
            sTip = null;
            sAnchor = null;
        }
    }
}