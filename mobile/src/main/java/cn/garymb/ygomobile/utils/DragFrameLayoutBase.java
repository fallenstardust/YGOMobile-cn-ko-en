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

import static cn.garymb.ygomobile.utils.DraggablePopupHelperBase.DragFrameLayout;
import static cn.garymb.ygomobile.utils.DraggablePopupHelper.*;

/**
 * 弹窗全窗口包装层的顶部抽象实现（原 {@link DraggablePopupHelper} 嵌套类 DragFrameLayout 拆分而来）。
 *
 * <p>原嵌套类约 948 行超限，提升为同包顶层两级：本类 ← {@link DragFrameLayoutImpl}，
 * 并在 {@link DraggablePopupHelperBase} 中保留壳类型 {@code DragFrameLayout} 供全部原引用点使用。
 * 本层保持原嵌套类的 FrameLayout 继承不变，外层静态成员经 static-on-demand 导入、
 * 壳类型经 static 单类型导入，使方法体与原文件逐行一致；自身持有拖拽平移、
 * 收缩横条/把手、跨窗口触摸转发目标解析与旋转重排的前半部分实现。</p>
 *
 * <p>拆分仅做机械搬移与可见性放宽，行为与实现细节与拆分前完全一致。</p>
 */
abstract class DragFrameLayoutBase extends FrameLayout {
        protected static final String PREF_X = "_x";
        protected static final String PREF_Y = "_y";
        protected final SharedPreferences prefs;
        protected final String dialogId;
        protected float downX, downY;
        /** 拖拽累计平移量（施加到全部子视图的 translation 上）：包装层自身永不平移，
         *  否则整层拖离原位后腾出的带状区域不再命中本层，跨窗口触摸转发失效，
         *  场上/手卡点击不再响应抬高与卡片详情（用户反馈），拖回原位又恢复 */
        protected float dragTX, dragTY;
        protected float startDragTX, startDragTY;
        protected boolean dragging;
        /** 本弹窗是否可拖动（默认 true；经 {@link #setDragEnabled(PopupWindow, boolean)} 按 dialog 禁用，
         *  用户规格：建主/局域网/单机/录像/玩家等待对齐 C++ 定位弹窗不可拖） */
        protected boolean dragEnabled = true;
        protected boolean touchOnScrollable;
        protected boolean childConsumedDown;
        /** 本层所属 PopupWindow：点击置顶时重排其窗口层级 */
        protected PopupWindow hostPopup;
        /** 兜底转发目标（Activity decorView），null 表示不转发 */
        protected View passThroughTarget;
        /** 当前手势的转发目标：null 表示由本层自行处理 */
        protected View gestureTarget;
        /** 转发手势的 downTime 与最近落点（目标坐标），手势被打断时补发 ACTION_CANCEL 用 */
        protected long forwardedDownTime;
        protected float forwardedX, forwardedY;
        /** 正在处理由其他弹窗层转发进来的事件：跳过归属判定，避免二次转发 */
        protected boolean receivingForwarded;
        /** 弹窗设计（未限宽）尺寸，旋转后据此按新屏宽重新解算显示宽度 */
        protected int designW = 0;
        protected int designH = 0;
        /** 创建弹窗时的横屏等比系数（与内容 inflate 密度一致）：旋转重排时按
         *  当前方向系数/创建系数的比例重缩放弹窗可视尺寸（用户规格：竖屏转横屏后
         *  dialog 仍按横屏时的高度比例显示，幸存弹窗无法重 inflate 密度，整体缩放等效） */
        protected float createdFactor = 1f;
        /** 竖屏时交换宽高基准（与创建期一致），供建主/局域网/单机弹窗旋转后重解使用 */
        protected boolean swapForPortrait = false;
        /** 居中区域（如 layout_game_right）的视图 id 与弱引用，旋转重建后按 id 重新解析新实例 */
        protected int centerRegionId = View.NO_ID;
        protected java.lang.ref.WeakReference<View> centerRegionRef;
        /** 停靠模式：true=内容贴区域左缘+垂直居中（{@link #dockPopupInRegionLeft}），
         *  false=区域居中（{@link #centerPopupInRegion}）；旋转重排按此标志重新定位 */
        protected boolean dockLeftInRegion = false;
        /** 自定义旋转重排逻辑；非空时 {@link #relayoutForOrientation} 完全交由其处理，跳过默认限宽 */
        protected Runnable customRelayout;
        // ── 收缩到底部横条模式（经 enableCollapse 启用，视图与几何由本类静态辅助解算）──
        /** 底部收缩横条：与聊天输入框同高同位、保持对话框原宽/原位/原背景贴图的横条，点击恢复原始尺寸；
         *  作为包装层后加子视图，child 0 始终保持为对话框内容 */
        protected View collapseBar;
        /** 收缩态横条几何快照：展开可见时取对话框内容的原宽与左缘，横条不再拉伸为整屏宽 */
        protected int collapseBarWidth;
        protected int collapseBarLeft;
        /** 展开态收缩把手：优先为内容布局 XML 声明的 btn_dialog_collapse（位置由布局
         *  声明，免程序化贴位），内容未声明时才是包装层左上角的程序化「▼」小按钮 */
        protected View collapseHandle;
        /** 收缩按钮来自 dialog 布局 XML（btn_dialog_collapse）：不参与程序化把手贴位 */
        protected boolean handleInContent;
        /** 把手嵌入目标：该弹窗标题视图（可空/可 GONE），可见时把手贴其左侧，否则左上角悬浮 */
        protected View collapseTitleView;
        protected boolean collapsible;
        protected boolean collapsed;
        /** 收缩前对话框被拖拽的平移量快照：收缩时暂存并归零，让横条统一锚定到
         *  layout_game_right 底部中央、与聊天输入框同高同位；展开时还原到子视图平移，
         *  对话框仍回到被拖到的位置（横竖屏、任意拖拽位置一致，用户规格） */
        protected float preCollapseTX;
        protected float preCollapseTY;
        /** 本次手势 DOWN 是否落在收缩横条/把手上：是则整体禁用拖拽逻辑 */
        protected boolean touchOnCollapseWidget;
        /** 收缩横条的布局补偿监听目标（旋转重建后聊天输入框/区域尚未布局时挂一次性重锚，按实例去重） */
        protected final List<View> collapseAnchorWatches = new ArrayList<>();

        DragFrameLayoutBase(Context context, SharedPreferences prefs, String dialogId) {
            super(context);
            this.prefs = prefs;
            this.dialogId = dialogId;
            setClipChildren(false);
        }

        void setHostPopup(PopupWindow popup) {
            this.hostPopup = popup;
        }

        void setPassThroughTarget(View target) {
            this.passThroughTarget = target;
        }

        /** 启用/禁用本层拖拽：禁用时拖拽逻辑整体旁路，已有的拖拽平移归零回锚定位 */
        void setDragEnabled(boolean enabled) {
            this.dragEnabled = enabled;
            if (!enabled) {
                dragging = false;
                clearDragOffset();
            }
        }

        /** 把拖拽平移量施加/清零到全部子视图（层自身保持铺满窗口，命中与转发不受拖拽影响） */
        protected void applyDragOffsetToChildren() {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.setTranslationX(dragTX);
                child.setTranslationY(dragTY);
            }
        }

        /** 拖拽平移彻底归零（含子视图与层自身的历史残留） */
        protected void clearDragOffset() {
            dragTX = 0f;
            dragTY = 0f;
            startDragTX = 0f;
            startDragTY = 0f;
            setTranslationX(0f);
            setTranslationY(0f);
            applyDragOffsetToChildren();
        }

        void setDesignSize(int w, int h, float createdF) {
            this.designW = w;
            this.designH = h;
            this.createdFactor = createdF > 0f ? createdF : 1f;
        }

        void setSwapForPortrait(boolean swap) {
            this.swapForPortrait = swap;
        }

        void setCustomRelayout(Runnable handler) {
            this.customRelayout = handler;
        }

        /** 记录居中区域（供旋转后按新视图树同 id 区域重新居中） */
        void setCenterRegion(View region) {
            setCenterRegion(region, false);
        }

        /** 记录居中/停靠区域与停靠模式（dockLeft=true 贴区域左缘，false 居中） */
        void setCenterRegion(View region, boolean dockLeft) {
            if (region != null) {
                this.centerRegionId = region.getId();
                this.centerRegionRef = new java.lang.ref.WeakReference<>(region);
                this.dockLeftInRegion = dockLeft;
            }
        }

        /**
         * 启用收缩模式：包装层底部加与聊天输入框同高同位、保持对话框原宽与原背景贴图的
         * 横条；收缩把手优先复用内容布局 XML 声明的 btn_dialog_collapse（各 dialog 自带，
         * 位置由布局声明），内容未声明时才在左上角加程序化「▼」把手；收缩态内容 GONE 仅剩横条，
         * 横条以外触摸照旧透传给 Activity，供玩家临时确认场地，点击横条恢复。横条与把手作为
         * 后加子视图，child 0 始终是对话框内容，{@link #centerPopupInRegion} 的 margin 居中与
         * 旋转限宽逻辑不受影响。
         */
        void makeCollapsible(View titleView, String title) {
            if (getChildCount() == 0) return;
            final View content = getChildAt(0);
            collapsible = true;
            collapseTitleView = titleView;

            collapseBar = createCollapseBar(getContext(), title);
            styleBarWithDialogContent(collapseBar, content);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    barMetrics(getContext())[2],
                    Gravity.TOP | Gravity.START);
            collapseBar.setVisibility(GONE);
            // 点横条手动恢复：键盘抬起期间一律不响应（用户规格）——此刻展开会让铺满窗口
            // 的弹窗重新盖住键盘区吞掉输入法触摸（真机结论），且键盘收起时还原序列本就会
            // 自动展开输入法接管的收缩并复原窗口，抬起期间点击既失效又无必要；
            // 输入法收起（ImePopupAdjuster.isImeUp()==false）后横条恢复可点
            collapseBar.setOnClickListener(v -> {
                if (ImePopupAdjuster.isImeUp()) return;
                setCollapsed(false);
            });
            addView(collapseBar, blp);

            // 收缩触发器：内容声明的 btn_dialog_collapse 作把手（点击即收缩），
            // 未声明时回落程序化「▼」把手并同步贴位
            View xmlBtn = content.findViewById(R.id.btn_dialog_collapse);
            if (xmlBtn != null) {
                handleInContent = true;
                collapseHandle = xmlBtn;
            } else {
                collapseHandle = createCollapseHandle(getContext());
                FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.START);
                hlp.leftMargin = dp(getContext(), 2);
                hlp.topMargin = dp(getContext(), 2);
                addView(collapseHandle, hlp);
            }
            collapseHandle.setOnClickListener(v -> setCollapsed(true));

            // 内容经居中 margin / 旋转重解宽度而移动，把手跟随同步
            content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, ob, olr) -> syncCollapseHandle());
            // 标题自身尺寸/可见变化（如 setText、横竖屏重建）时也需重算把手位置
            if (titleView != null) {
                titleView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, ob, olr) -> syncCollapseHandle());
            }
            syncCollapseHandle();
        }

        void setCollapseTitle(String title) {
            setBarTitle(collapseBar, title);
        }

        protected void setCollapsed(boolean doCollapse) {
            if (!collapsible || getChildCount() == 0) return;
            View content = getChildAt(0);
            if (doCollapse) {
                // 收缩一律归零拖拽残留平移：横条若随拖拽偏移一起平移，
                // refreshCollapseAnchors 的横向居中虽按子视图屏幕坐标补偿、但纵向用 bottomMargin 相对
                // 已平移的内容锚定，会把横条偏离 layout_game_right 底部中央与聊天输入框（尤其纵向）。
                // 故先记下平移量再归零，让横条在净零平移下精确锚定到底部中央/与聊天输入框同高同位；
                // 展开时还原平移，对话框仍回到被拖到的原位置（横竖屏一致，用户规格）
                preCollapseTX = dragTX;
                preCollapseTY = dragTY;
                dragTX = 0f;
                dragTY = 0f;
                applyDragOffsetToChildren();
                refreshCollapseAnchors();
            } else {
                dragTX = preCollapseTX;
                dragTY = preCollapseTY;
                applyDragOffsetToChildren();
            }
            collapsed = doCollapse;
            content.setVisibility(doCollapse ? GONE : VISIBLE);
            collapseHandle.setVisibility(doCollapse ? GONE : VISIBLE);
            collapseBar.setVisibility(doCollapse ? VISIBLE : GONE);
            if (!doCollapse) {
                syncCollapseHandle();
            }
        }
        // ── 输入法抬升联动：自动最小化/复原（供 ImePopupAdjuster 程序化驱动）──
        /** 当前收缩态是否由输入法联动触发（区别于 dialog 自带把手的用户手动收缩）：
         *  true 时横条底边不再对齐聊天输入框底边，而是由 {@link #anchorCollapseBarBottom}
         *  按「键盘抬起后的输入框上沿」实时锚定 */
        protected boolean imeAutoCollapsed;

        /**
         * 为输入法抬升自动最小化：不可收缩的弹窗（决斗记录/录像保存等）动态补装横条与把手；
         * 已是用户手动收缩态的不重复收缩、只接管横条重锚（imeAutoCollapsed 保持 false，
         * 还原时不自动展开——用户规格：已最小化的 dialog 随键盘一并抬高、收起后保持收缩态
         * 贴回降位的输入框上方）。横条底边贴到 {@code barBottomScreenY}（= 抬高后的聊天输入框
         * 上沿 − 安全间隙），键盘动画期间可由调用方反复幂等调用。注意：弹窗窗口盖住键盘区域
         * 会吞掉输入法触摸（真机结论，见类头注释），收缩后调用方
         * （{@code ImePopupAdjuster.stepMinimize}）还须把窗口底边同步收缩到本目标线——
         * 横条居于窗口内容底缘，窗口缩了横条依旧可见可点。
         */
        boolean collapseForIme(int barBottomScreenY) {
            if (getChildCount() == 0) return false;
            if (!collapsible) makeCollapsible(null, null);
            if (!collapsible) return false;
            if (!collapsed) {
                imeAutoCollapsed = true;
                setCollapsed(true);
            }
            anchorCollapseBarBottom(barBottomScreenY);
            return true;
        }

        /** 横条底边锚定到指定屏幕 y（默认锚定即 barMetrics 的「聊天输入框上沿 − 安全间隙」，
         *  抬升期按实测输入框上沿覆盖）：先按默认逻辑重解横条高/宽/横向居中，再以底部锚定
         *  覆盖 bottomMargin——只改包装层子视图 margin，不触碰窗口矩形 */
        protected void anchorCollapseBarBottom(int bottomScreenY) {
            if (collapseBar == null) return;
            refreshCollapseAnchors();
            FrameLayout.LayoutParams blp = (FrameLayout.LayoutParams) collapseBar.getLayoutParams();
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            int margin = loc[1] + getHeight() - bottomScreenY;
            if (margin < 0) margin = 0;
            if (blp.bottomMargin != margin) {
                blp.bottomMargin = margin;
                collapseBar.setLayoutParams(blp);
            }
        }

        /** 输入法收起：撤销输入法接管的自动最小化，展开回原始尺寸（内容布局从未被改动，
         *  一步展开）；用户手动收缩的（imeAutoCollapsed=false）不展开，保持收缩态由重锚贴回 */
        boolean expandFromIme() {
            if (!imeAutoCollapsed) return false;
            imeAutoCollapsed = false;
            if (collapsed) setCollapsed(false);
            return true;
        }
        /**
         * 横条与聊天输入框对齐并在 layout_game_right 区域内水平居中。垂直用包装层底部锚定
         *（Gravity.BOTTOM + bottomMargin=gap，gap 为「横条底边（= 输入框上沿 − 安全间隙）到
         *  窗口底边的距离」），
         * 与真实窗口底边对齐而不依赖包装层当前测得高度：旋转重建瞬间（onConfigurationChanged
         * 内同步跑 relayoutForOrientation）包装层/区域/聊天输入框尚未完成布局，旧的按
         * decor+屏高反推 topMargin 的写法会以旧方向屏高为基准算出越界的 topMargin，把横条
         * 顶出窗口底边之外而整条看不见（表现为横屏最小化转竖屏后横条消失、无法点击还原）。
         * 改底部锚定后即便此刻度量退化横条也稳定显示在屏幕底部，横向按区域实时坐标居中并
         * 限幅到屏宽（区域未布局时退回快照亦不越屏），配合一次性重锚监听在布局完成后精确对齐
         */
        protected void refreshCollapseAnchors() {
            if (collapseBar == null) return;
            int[] m = barMetrics(getContext());
            FrameLayout.LayoutParams blp = (FrameLayout.LayoutParams) collapseBar.getLayoutParams();
            blp.height = m[2];
            // 内容可见已布局时取快照（含旋转重缩放系数：横条保持展开态弹窗的可视宽度）；
            // 收缩态内容 GONE 不参与布局，沿用上一次的快照值
            if (getChildCount() > 0) {
                View content = getChildAt(0);
                if (content.getVisibility() == VISIBLE && content.getWidth() > 0) {
                    float sx = content.getScaleX();
                    collapseBarWidth = Math.round(content.getWidth() * sx);
                    collapseBarLeft = Math.max(0, Math.round(
                            content.getLeft() - content.getWidth() * (sx - 1f) / 2f));
                }
            }
            // 水平：在区域内居中（旋转重建后按新坐标重解）；无宽度快照时取区域宽；
            // 区域不可用才退回内容原左缘快照；两种情形都限幅到屏宽，杜绝转屏后横条整条偏出屏外
            int barW = collapseBarWidth;
            int left;
            View region = resolveCollapseRegion();
            if (region != null) {
                if (barW <= 0 || barW > region.getWidth()) barW = region.getWidth();
                int[] regLoc = new int[2];
                region.getLocationInWindow(regLoc);
                View decor = resolveActivityDecorView(getContext());
                int[] decorLoc = new int[2];
                if (decor != null) decor.getLocationOnScreen(decorLoc);
                int[] myLoc = new int[2];
                getLocationOnScreen(myLoc);
                left = decorLoc[0] + regLoc[0] + (region.getWidth() - barW) / 2 - myLoc[0];
            } else {
                left = Math.max(0, collapseBarLeft);
            }
            if (left < 0) left = 0;
            if (left + barW > m[0]) left = Math.max(0, m[0] - barW);
            blp.width = barW > 0 ? barW : ViewGroup.LayoutParams.MATCH_PARENT;
            blp.leftMargin = blp.width == ViewGroup.LayoutParams.MATCH_PARENT ? 0 : left;
            // 垂直：底部锚定贴聊天输入框上方（bottomMargin=gap，横条底边=输入框上沿−安全间隙），
            // 不依赖包装层高度，免疫旋转时窗口高度滞后导致的横条被顶出屏外
            blp.gravity = Gravity.BOTTOM | Gravity.START;
            blp.topMargin = 0;
            blp.bottomMargin = m[3];
            collapseBar.setLayoutParams(blp);
            requestCollapseAnchorRetry();
        }

        /** 横条水平居中参照的区域：优先记录的居中区域，旋转重建旧实例脱离视图树后按 id 重解析；
         *  未登记/不可用时回退 layout_game_right；仍未布局返回 null 由调用方降级 */
        protected View resolveCollapseRegion() {
            View region = centerRegionRef != null ? centerRegionRef.get() : null;
            if (region == null || !region.isAttachedToWindow()) {
                Activity act = resolveActivity(getContext());
                int id = centerRegionId != View.NO_ID ? centerRegionId : R.id.layout_game_right;
                region = act != null ? act.findViewById(id) : null;
            }
            return region != null && region.getWidth() > 0 && region.getHeight() > 0 ? region : null;
        }

        /** 旋转重建视图树时聊天输入框/区域常尚未完成布局，度量退化为兜底值：
         *  给未布局的目标挂一次性布局监听补重锚，目标布局完成后横条才真正与
         *  聊天输入框同高同位、在区域内居中 */
        protected void requestCollapseAnchorRetry() {
            if (!collapsible) return;
            Activity act = resolveActivity(getContext());
            if (act == null) return;
            watchLayoutOnce(act.findViewById(R.id.et_chat_input));
            watchLayoutOnce(act.findViewById(R.id.layout_game_right));
        }
        /** 给目标挂一次性「布局完成后重锚横条」监听（按实例去重，触发即摘） */
        protected void watchLayoutOnce(View target) {
            if (target == null || target.getHeight() > 0 || collapseAnchorWatches.contains(target)) return;
            collapseAnchorWatches.add(target);
            target.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b,
                                           int oldL, int oldT, int oldR, int oldB) {
                    if (b - t <= 0) return;
                    collapseAnchorWatches.remove(v);
                    v.removeOnLayoutChangeListener(this);
                    refreshCollapseAnchors();
                }
            });
        }

        /** 把手位置：XML 声明的 btn_dialog_collapse 由布局自行定位不参与贴位；
         *  程序化把手优先贴标题左侧（标题可见且已布局），否则贴内容左上角 */
        protected void syncCollapseHandle() {
            if (handleInContent || collapseHandle == null || getChildCount() == 0) return;
            View content = getChildAt(0);
            // 标题可见时把手移到标题行左侧、垂直居中，颜色随标题（positionHandleBesideTitle 内部处理）
            if (positionHandleBesideTitle(collapseHandle, content, collapseTitleView)) {
                return;
            }
            FrameLayout.LayoutParams hlp =
                    (FrameLayout.LayoutParams) collapseHandle.getLayoutParams();
            int inset = dp(getContext(), 2);
            // 把手贴缩放后内容的可视左上角（pivot 默认在中心，可视边界由缩放比例外扩/内缩）
            float sx = content.getScaleX();
            float sy = content.getScaleY();
            int handleW = collapseHandle.getWidth();
            int handleH = collapseHandle.getHeight();
            int left = Math.round(
                    content.getLeft() - content.getWidth() * (sx - 1f) / 2f) + inset;
            int top = Math.round(
                    content.getTop() - content.getHeight() * (sy - 1f) / 2f) + inset;
            // 钉在本层可视范围内，避免旋转瞬时坐标不一致把手被推到屏外而“看不见”
            hlp.leftMargin = Math.max(0,
                    Math.min(Math.max(0, getWidth() - handleW), left));
            hlp.topMargin = Math.max(0,
                    Math.min(Math.max(0, getHeight() - handleH), top));
            hlp.gravity = Gravity.TOP | Gravity.START;
            collapseHandle.setLayoutParams(hlp);
        }
        /**
         * 屏幕旋转后按新屏宽重新解算并应用弹窗显示宽度；若记录了居中区域，旋转重建后
         * 旧区域实例已脱离视图树，按 id 从当前 Activity 视图树重新解析同 id 新实例并重算居中偏移。
         */
        void relayoutForOrientation() {
            // 旋转一律重新按区域定位：先归零拖拽残留的平移（含子视图，与 centerPopupInRegion/dock 同语义），
            // 否则旧方向的平移叠加新 margin 会把内容与把手推出可视区（标题缺顶、「▼」消失、询问窗无法取消）
            clearDragOffset();
            preCollapseTX = 0f;
            preCollapseTY = 0f;
            // 子视图尺寸依赖外部解算的弹窗（如选卡/卡片确认按区域宽烘焙卡图）→ 交由自定义重排全权处理
            if (customRelayout != null) {
                customRelayout.run();
                if (collapsible) {
                    post(this::refreshCollapseAnchors);
                    post(this::syncCollapseHandle);
                }
                return;
            }
            if (getChildCount() == 0) return;
            View content = getChildAt(0);
            int[] fitted = orientedFitSize(getContext(), designW, designH, swapForPortrait);
            // 按当前方向等比系数相对创建系数的比例整体重缩放弹窗：内容以创建方向
            // 系数（如竖屏 1.0）inflate 且旋转后无法重 inflate，缩放后竖屏创建的弹窗
            // 转横屏即按横屏高度比例显示；限幅使缩放后的可视尺寸不超出当前屏幕；
            // swapForPortrait（正方形重排）与铺宽内容（designW<=0）不参与，保持原行为
            float s = 1f;
            if (!swapForPortrait && designW > 0 && createdFactor > 0f) {
                s = DialogScale.factor(getContext()) / createdFactor;
                if (Float.compare(s, 1f) != 0) {
                    DisplayMetrics m = DialogScale.screenMetrics(getContext());
                    int refW = fitted[0] > 0 ? fitted[0] : designW;
                    if (refW > 0) s = Math.min(s, (float) m.widthPixels / refW);
                    int refH = fitted[1] > 0 ? fitted[1]
                            : (content.getHeight() > 0 ? content.getHeight() : 0);
                    if (refH > 0) s = Math.min(s, (float) m.heightPixels / refH);
                    if (s < 0.5f) s = 0.5f;
                    else if (s > 2f) s = 2f;
                }
            }
            content.setScaleX(s);
            content.setScaleY(s);
            ViewGroup.LayoutParams raw = content.getLayoutParams();
            if (raw instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) raw;
                flp.width = fitted[0];
                // 具体高度才按比例重解；WRAP_CONTENT/MATCH_PARENT 保持不变交由内容自适应
                if (designH > 0) flp.height = fitted[1];
                content.setLayoutParams(flp);
            }
            content.requestLayout();
            if (centerRegionId != View.NO_ID && hostPopup != null) {
                View region = centerRegionRef != null ? centerRegionRef.get() : null;
                if (region == null || !region.isAttachedToWindow()) {
                    Activity act = resolveActivity(getContext());
                    region = act != null ? act.findViewById(centerRegionId) : null;
                }
                if (region != null) {
                    if (dockLeftInRegion) dockPopupInRegionLeft(hostPopup, region);
                    else centerPopupInRegion(hostPopup, region);
                }
            }
            if (collapsible) {
                refreshCollapseAnchors();
                post(this::syncCollapseHandle);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (passThroughTarget == null) {
                passThroughTarget = resolveActivityDecorView(getContext());
            }
            ACTIVE_LAYERS.remove(this);
            // 运行时实例恒为壳类 DragFrameLayout（唯一实例化入口），登记到壳类型列表需向下转型
            ACTIVE_LAYERS.add((DragFrameLayout) this);
            if (collapsible) {
                post(this::refreshCollapseAnchors);
                post(this::syncCollapseHandle);
            }
        }

        /**
         * 输入法弹出/收起使本全窗口包装层高度变化（PopupWindow ADJUST_RESIZE 下窗口被 IME 压缩/还原）：
         * 按当前几何重新解算区域定位——弹出时内容随可见区域上移、露出被键盘遮挡的输入框，收起时窗口
         * 还原并重新贴回区域中心，杜绝「输入法把 dialog 顶起后不回位」。区域锚定与贴左缘各自的重算与
         * {@link #relayoutForOrientation} 中的区域再解析同语义（弱引用失效时按 id 从当前视图树重取）。
         * 仅处理高度变化（宽度变化交由旋转重排逻辑负责），且收缩态内容已隐藏时不动，避免误重置。
         */
        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (h != oldh && !collapsed
                    && centerRegionId != View.NO_ID && hostPopup != null && hostPopup.isShowing()) {
                reanchorToRegion();
            }
        }

        /** 按当前区域几何重新解算本弹窗内容定位（输入法升降、可见区域变化后回到应有位置） */
        protected void reanchorToRegion() {
            View region = centerRegionRef != null ? centerRegionRef.get() : null;
            if (region == null || !region.isAttachedToWindow()) {
                Activity act = resolveActivity(getContext());
                region = act != null ? act.findViewById(centerRegionId) : null;
            }
            if (region == null) return;
            if (dockLeftInRegion) dockPopupInRegionLeft(hostPopup, region);
            else centerPopupInRegion(hostPopup, region);
        }
}

