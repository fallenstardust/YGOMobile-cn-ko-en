package cn.garymb.ygomobile.ui.dialogs.duel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.card.CardStatusTipHelper;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;


/**
 * Base layer of the split inheritance chain (CardDisplayDialogBase <-
 * CardDisplayDialog): owns the shared constants, the nested CardItem /
 * listener types, all popup state and the build / sizing / card-graphics /
 * slot-refresh engine plus dismiss / isShowing primitives. The live-refresh
 * static, the builder-style setters (which return the concrete dialog type)
 * and the show entry points live in the leaf class.
 */
abstract class CardDisplayDialogBase {

    public static final int SLOT_COUNT = 5;

    // 卡图宽高比 177:254：ImageView 高度 = 宽度 × CARD_ASPECT
    protected static final float CARD_ASPECT = 254f / 177f;
    // 对话框宽度取 layout_game_right 实际宽度的四分之三（横屏沿用）
    protected static final float DIALOG_WIDTH_RATIO = 0.75f;
    /**
     * 竖屏弹窗宽度比例（用户规格：宽度与文本系数解耦，落在屏宽内、两侧保留少量留白）：
     * 竖屏下 {@code layout_game_right} 即整屏宽，本比例直接就是屏宽占比，0.96 即两侧各留约 2%。
     * 旧写法宽度 = 0.75 × {@link #PORTRAIT_SCALE}（文本系数同时拉宽），提到 0.82×1.25≈1.03
     * 已被屏宽限幅到满屏（两侧无留白）；现宽度只看本比例、文本只看 {@link #PORTRAIT_SCALE}，
     * 两者互不拉扯。横屏仍用 {@link #DIALOG_WIDTH_RATIO} × {@link #LANDSCAPE_SCALE}，行为不变；
     * 与 {@link CardSelectDialogBase} 同一口径。
     */
    protected static final float PORTRAIT_WIDTH_RATIO = 0.96f;
    /**
     * 竖屏文本放大系数（用户规格：由 1.25 调小，与 CardSelectDialog 一致）：现只作用于
     * 标题/卡位文本/确定按钮字号，不再参与宽度解算（见 {@link #PORTRAIT_WIDTH_RATIO}）。
     * 调小后卡位标签更容易装进卡宽（少缩字/少省略），标签行与标题行也更矮、整组更紧凑。
     */
    protected static final float PORTRAIT_SCALE = 1.1f;
    /**
     * 横屏整体缩小系数（与 CardSelectDialog 一致）：按需求把横屏弹窗再等比例小一号，
     * 弹窗宽、卡图、标题/卡位文本/确定按钮字号同乘本系数，列表观感更紧凑。
     */
    protected static final float LANDSCAPE_SCALE = 0.9f;
    /** 每个卡槽的水平外边距（dp），与 dialog_card_display.xml 中槽位 layout_marginHorizontal 一致，由原 4dp 收紧为 2dp */
    protected static final float SLOT_MARGIN_DP = 2f;
    // 位置标签配色，映射 client_field.h CARD_LIST_* 常量
    protected static final int COLOR_DEFAULT = YGOUtil.c(R.color.colorMain);   // CARD_LIST_DEFAULT_BACKGROUND_COLOR
    protected static final int COLOR_OPPONENT = YGOUtil.c(R.color.card_list_opponent_gray);  // CARD_LIST_OPPONENT_BACKGROUND_COLOR

    public static class CardItem {
        public final int code;
        public final int controler;
        public final int location;
        public final int sequence;
        public final int subSeq;
        /** 里侧表示除外标记：由调用方以「除外区且 !isFaceUp()」判定，查看列表下半部呼吸卡背用 */
        public final boolean facedownRemoved;

        public CardItem(int code, int controler, int location, int sequence, int subSeq) {
            this(code, controler, location, sequence, subSeq, false);
        }

        public CardItem(int code, int controler, int location, int sequence, int subSeq,
                boolean facedownRemoved) {
            this.code = code;
            this.controler = controler;
            this.location = location;
            this.sequence = sequence;
            this.subSeq = subSeq;
            this.facedownRemoved = facedownRemoved;
        }
    }

    public interface OnCardClick {
        void onCardClick(CardItem item);
    }

    public interface OnDismissListener {
        void onDismiss();
    }

    /**
     * 实时观察提供器：观战/录像堆叠区列表弹窗打开后，宿主区域卡片增删/换位时，
     * 每次 field 变化都会重新调用本接口获取最新的 CardItem 列表并就地刷新 UI。
     */
    public interface CardItemSupplier {
        List<CardItem> obtain();
    }

    /** 当前正在展示的实时观察弹窗（弱引用不合适——弹窗自己强保持内容视图）；
     *  在 show/dismiss 时维护，在 field 变化时由 {@link #refreshLiveDialogs()} 统一刷。*/
    protected static final List<CardDisplayDialog> LIVE_DIALOGS = new ArrayList<>();

    /** 全部正在展示的本类弹窗（含未安装 liveSupplier 的普通确认窗）：面板只持有最近一个
     *  实例的引用，被后续弹窗覆盖引用的旧实例会漏关；决斗退出/回放结束时按本名单统一收口 */
    protected static final List<CardDisplayDialog> ALL_DIALOGS = new ArrayList<>();

    protected final Context context;
    protected final ImageLoader imageLoader;
    protected final Handler handler = new Handler(Looper.getMainLooper());

    /** 公共字符串管理器：初始化后可供整个类调用（对齐 CardDetailPanel.mStringManager 惯例） */
    public final StringManager mStringManager = DataManager.get().getStringManager();

    protected PopupWindow popupWindow;
    protected DraggablePopupHelper draggableHelper;
    /** 按下查看详情 / 按住悬浮通讯状态标签（每次 build 随包装层重建） */
    protected CardStatusTipHelper tipHelper;

    protected List<CardItem> cards = new ArrayList<>();
    protected String titlePrefix = "卡片确认"; // 默认区域名称前缀（不依赖 conf，实际调用均会 setTitle 覆盖）
    protected int pageOffset = 0;
    protected int localPlayer = -1;
    /**
     * item.controler 口径：true=协议侧（ShowDialogUtil），false=本地视角侧（CmdMenuDialog）。
     */
    protected boolean controlerProtocolSide = true;

    protected TextView tvTitle;
    protected SeekBar sbPage;
    protected View btnOk;
    protected final View[] slotViews = new View[SLOT_COUNT];
    protected final TextView[] tvPositions = new TextView[SLOT_COUNT];
    protected final ImageView[] ivCards = new ImageView[SLOT_COUNT];
    /** 单卡槽步进（px，卡宽 + 左右外边距 {@link #SLOT_MARGIN_DP}dp×2）：滚动条翻页动画的位移基准，随卡图在 {@link #applyCardImageSize(int)} 烘培 */
    protected int slotStepPx = 0;
    /**
     * 卡位标签可用宽（px）= 烘培后的固定槽宽，随 {@link #applyCardImageSize(int)} 更新：
     * 标签只能在此宽度内缩字，不得反向撑宽槽位（否则五槽总宽溢出弹窗，最右第 5 张卡与其
     * 上方标签被裁掉一半，用户反馈的竖屏只显示一半的根因）。
     */
    protected int slotLabelMaxPx = 0;
    /**
     * 皮肤宿主（TipHostLayout）：对话框背景贴图（sdialogl 九宫格）已由 {@code createTipHost}
     * 从内容根上移到宿主，因此内容被内缩的量就是宿主的左右内边距，卡宽反推必须以它为准
     *（见 {@link #containerWidthFor(int)}），不能硬编 16dp。
     */
    protected View contentHost;
    /**
     * 上一次已应用到卡图/槽位的内容可用宽（px）：插槽行是 match_parent，布局后的实测宽即真实可用宽；
     * 除宿主内边距外仍可能有行/根的 margin 等隐藏内缩，故实测值与已应用值不符时按实测重烘一次
     *（见 {@link #bindContainerWidthCorrection()}），确保五槽总宽恒等于可用宽。
     */
    protected int appliedContainerW = 0;
    /**
     * 标题/卡位文本/确定按钮的基准字号（px，取自 XML，横竖屏 density 相同故一致）：首次 {@link #applyContentScale()}
     * 时捕获，此后按当前方向系数以绝对字号回写，避免横竖屏来回旋转时系数叠乘使文本越放越大。
     */
    protected float baseTitlePx;
    protected float basePosPx;
    protected float baseOkPx;

    protected OnDismissListener dismissListener;
    protected OnCardClick cardClickListener;
    /** 实时观察提供器（非空则弹窗在 show/dismiss 时维护 LIVE_DIALOGS 名单，且 field 变化会重拉列表） */
    protected CardItemSupplier liveSupplier;

    // 卡背缓存：对齐 image_manager.cpp tButtonFacedown[0/1]（我方 cover.jpg / 对方 cover2.jpg）
    protected static Bitmap coverSelf;
    protected static Bitmap coverOpponent;
    // 里侧除外标记位图缓存（下半部1/4竖向渐变卡背，见 makeFacedownMark）：按玩家侧区分
    protected static Bitmap markSelf;
    protected static Bitmap markOpponent;
    /** 每槽位里侧除外呼吸动画句柄：换卡/关窗时取消并清理 foreground */
    protected final ValueAnimator[] facedownAnims = new ValueAnimator[SLOT_COUNT];

    /**
     * Constructor body anchor kept verbatim so the final fields are
     * assigned exactly once from the leaf forwarding constructor.
     */
    protected CardDisplayDialogBase(Context context, ImageLoader imageLoader) {
        this.context = context;
        this.imageLoader = imageLoader;
    }

    /** 更新标题为「区域名称 (卡片数量)」格式 */
    protected void updateTitle() {
        if (tvTitle == null) return;
        String text = titlePrefix + "(" + cards.size() + ")";
        tvTitle.setText(text);
        // 收缩横条与弹窗内标题同源，卡片数量变化时同步文案
        DraggablePopupHelper.setCollapseBarTitle(popupWindow, text);
    }

    protected void build() {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_card_display, null);
        tvTitle = root.findViewById(R.id.tv_card_display_title);
        sbPage = root.findViewById(R.id.sb_display_page);
        btnOk = root.findViewById(R.id.btn_card_display_ok);

        slotViews[0] = root.findViewById(R.id.slot_display_0);
        tvPositions[0] = root.findViewById(R.id.tv_display_pos_0);
        ivCards[0] = root.findViewById(R.id.iv_display_0);
        slotViews[1] = root.findViewById(R.id.slot_display_1);
        tvPositions[1] = root.findViewById(R.id.tv_display_pos_1);
        ivCards[1] = root.findViewById(R.id.iv_display_1);
        slotViews[2] = root.findViewById(R.id.slot_display_2);
        tvPositions[2] = root.findViewById(R.id.tv_display_pos_2);
        ivCards[2] = root.findViewById(R.id.iv_display_2);
        slotViews[3] = root.findViewById(R.id.slot_display_3);
        tvPositions[3] = root.findViewById(R.id.tv_display_pos_3);
        ivCards[3] = root.findViewById(R.id.iv_display_3);
        slotViews[4] = root.findViewById(R.id.slot_display_4);
        tvPositions[4] = root.findViewById(R.id.tv_display_pos_4);
        ivCards[4] = root.findViewById(R.id.iv_display_4);

        updateTitle();
        // cardtip 宿主：标签落在对话框内容层内部（见 CardStatusTipHelper.createTipHost），
        // 拖动弹窗时标签与卡片同层平移，移动过程中始终精准贴住卡片，不再停旧位等抬手重算
        FrameLayout tipHost = CardStatusTipHelper.createTipHost(context, root);
        // 背景贴图已移到宿主，内容内缩量以宿主内边距为准（卡宽反推用）
        contentHost = tipHost;
        popupWindow = new PopupWindow(tipHost,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        // 输入法弹出时让本弹窗窗口被压缩、内容随之上移露出输入框（ADJUST_NOTHING 会把键盘压在
        // 全屏弹窗后面导致无法输入）；键盘收起后由 DragFrameLayout.onSizeChanged 按区域重新解算定位，
        // 使 dialog 回到原位，不再出现「顶出屏幕找不回」。
        popupWindow.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setOnDismissListener(() -> {
            // 对话框隐藏后恢复决斗场阶段按钮
            if (context instanceof YGOProActivity) {
                ((YGOProActivity) context).notifyGameDialogHidden(this);
            }
            // 关窗先停掉里侧除外呼吸动画并清理 foreground，防动画持有已脱离的视图
            cancelAllFacedownMarks();
            LIVE_DIALOGS.remove(this);
            ALL_DIALOGS.remove(this);
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "card_display");
        int dialogWidth = resolveDialogWidth();
        applyCardImageSize(dialogWidth);
        draggableHelper.setupDraggablePopup(popupWindow, tipHost,
                dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        // 宽度按 layout_game_right 区域实时解算且据此烘焙每张卡图，通用重排无法同步子视图尺寸：
        // 注册自定义旋转重排，切换方向时重解区域宽、重烘焙卡图、重设弹窗宽并重新居中
        draggableHelper.registerOrientationRelayout(popupWindow, this::applyOrientationRelayout);
        // 收缩模式：点内容左上角「▼」把手缩为与聊天输入框同高的底部横条，确认场地后点横条恢复
        DraggablePopupHelper.enableCollapse(popupWindow, tvTitle, titlePrefix);

        // 点击即在 CardDetailPanel 显示详情，并同时常显该卡的通讯状态标签（抬手不收起，
        // 直到点击列表中其他卡片或翻页/刷新；按满 0.3 秒仍不视为点击）。标签作为内容层
        // 宿主的子视图，与卡片同层平移，拖动弹窗时始终精准贴住卡片
        tipHelper = new CardStatusTipHelper(context, tipHost, tipHost);
        tipHelper.setOnCardPressedListener(this::showDetailAtSlot);
        tipHelper.setOnSlotClickListener(this::onSlotClicked);
        tipHelper.setTipProvider(this::statusTextAtSlot);
        for (int i = 0; i < SLOT_COUNT; i++) {
            // 触摸面与按下动画均落在卡片图上（"按下卡片图"直接命中）
            tipHelper.attach(i, ivCards[i], ivCards[i]);
        }
        sbPage.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int delta = progress - pageOffset;
                    pageOffset = progress;
                    refreshSlots();
                    animateSlotsShift(delta);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        btnOk.setOnClickListener(v -> dismiss());
        // 行布局完成后按实测可用宽纠正卡宽/槽宽（推算宿主内边距之外仍可能有隐藏内缩）
        bindContainerWidthCorrection();
        // 竖屏按系数放大标题/卡位文本/确定按钮字号（横屏 1.0 不变），与已放大的弹窗宽度、卡图同步
        applyContentScale();
    }

    /**
     * 对话框宽度 = layout_game_right 实际宽度 × 比例：横屏 {@link #DIALOG_WIDTH_RATIO} ×
     * {@link #LANDSCAPE_SCALE}（0.75×0.9，行为不变），竖屏直接用 {@link #PORTRAIT_WIDTH_RATIO}
     *（用户规格：宽度与文本系数解耦，落在屏宽内、两侧保留约 2% 留白，不再叠 {@link #PORTRAIT_SCALE}）；
     * 取不到时依次降级为窗口 decorView 宽度、屏幕宽度。
     * <p>结果一律按当前屏宽限幅（与 {@code DraggablePopupHelper.fitSizeToScreen} 同口径）：本宽既作弹窗
     * 设计宽又用于反推每张卡宽与槽宽，包装层实际显示宽会被限到屏宽，此处不限则短屏/分屏下按未限幅宽
     * 烘培的五槽总宽超出真实内容宽，卡片与标签被裁。
     */
    protected int resolveDialogWidth() {
        int w = 0;
        if (context instanceof android.app.Activity) {
            android.app.Activity act = (android.app.Activity) context;
            if (!act.isFinishing() && !act.isDestroyed()) {
                View gameRight = act.findViewById(R.id.layout_game_right);
                if (gameRight != null) {
                    w = gameRight.getWidth();
                }
                if (w <= 0) {
                    w = act.getWindow().getDecorView().getWidth();
                }
            }
        }
        if (w <= 0) {
            w = context.getResources().getDisplayMetrics().widthPixels;
        }
        int dialogW = DialogScale.isLandscape(context)
                ? Math.round(w * DIALOG_WIDTH_RATIO * LANDSCAPE_SCALE)
                : Math.round(w * PORTRAIT_WIDTH_RATIO);
        int screenW = DialogScale.screenMetrics(context).widthPixels;
        if (screenW > 0 && dialogW > screenW) dialogW = screenW;
        return dialogW;
    }

    /** 当前方向的内容缩放系数：横屏 {@link #LANDSCAPE_SCALE}（等比例小一号），竖屏 {@link #PORTRAIT_SCALE} */
    protected float contentScale() {
        return DialogScale.isLandscape(context) ? LANDSCAPE_SCALE : PORTRAIT_SCALE;
    }

    /**
     * 按当前方向把标题/卡位文本/确定按钮字号回写为「基准 × 系数」的绝对值（系数见 {@link #contentScale()}）。
     * 首次调用捕获基准（此时视图仍为 XML 原始字号），此后绝对回写，旋转往复不叠乘。build() 与旋转重排均调用。
     * btnOk 本类声明为 View，仅当其为 TextView 时回写字号。
     */
    protected void applyContentScale() {
        if (tvTitle == null || tvPositions[0] == null) return;
        if (baseTitlePx <= 0) {
            baseTitlePx = tvTitle.getTextSize();
            basePosPx = tvPositions[0].getTextSize();
            if (btnOk instanceof TextView) baseOkPx = ((TextView) btnOk).getTextSize();
        }
        float s = contentScale();
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTitlePx * s);
        for (TextView tv : tvPositions) {
            if (tv != null) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, basePosPx * s);
        }
        if (btnOk instanceof TextView && baseOkPx > 0) {
            ((TextView) btnOk).setTextSize(TypedValue.COMPLEX_UNIT_PX, baseOkPx * s);
        }
    }

    /**
     * 按对话框宽度反推每张卡宽：可用宽 = 弹窗宽 − 皮肤宿主左右内边距（见
     * {@link #containerWidthFor(int)}，旧写法硬编 16dp×2 会把卡烘得过大导致两端被裁），
     * 再扣除每槽水平外边距 {@link #SLOT_MARGIN_DP}dp×2 后五等分；高度按 177:254 卡图比例计算，
     * 保证 ImageView 宽高与卡片比例一致；槽位宽度同步钉为卡宽（原 wrap_content），空槽位 GONE 时
     * 已显示槽的尺寸与间距不变、整组居中。
     */
    protected void applyCardImageSize(int dialogWidthPx) {
        applySlotSizes(containerWidthFor(dialogWidthPx));
    }

    /**
     * 弹窗内容真实可用宽（px）= 弹窗宽 − 皮肤宿主左右内边距。
     * <p>旧写法把内缩硬编为 16dp×2，而 sdialogl 九宫格声明的内容内缩大于该值，五槽总宽因此
     * 超出真实可用宽，居中的插槽行两侧各溢出约 1/4 卡宽——最左与最右两张卡（及其上方卡位标签）
     * 只剩中间三张的 3/4（用户反馈）。宿主尚未就绪时仍按 16dp×2 兜底，随后的
     * {@link #bindContainerWidthCorrection()} 会用行实测宽再纠正一次。
     */
    protected int containerWidthFor(int dialogWidthPx) {
        int hPad = contentHost != null
                ? contentHost.getPaddingLeft() + contentHost.getPaddingRight()
                : 2 * dp2px(16);
        return Math.max(0, dialogWidthPx - hPad);
    }

    /**
     * 按真实可用宽反推卡宽并烘培卡图/槽位：
     * <ul>
     * <li>卡图与卡位标签：宽 = 可用宽五等分（已减槽外边距），高按 177:254 反推，保证宽高与卡片比例一致；</li>
     * <li>槽位：宽同步钉为卡宽（原 wrap_content）——标签（如「魔法陷阱区[6]」、灵摆区「[7](2)」尾号）
     *  超宽时只在槽内缩字而不撑宽槽位，否则整行溢出、两端卡片被裁（用户反馈）；同时因为宽是固定的，
     *  不足五张时空槽 GONE 后已显示槽宽与间距完全不变，行中已有 {@code gravity="center"} 把不足的五张
     *  整组居中，对齐 C++ {@code ResizeCardSelectButtons} 的「尺寸不变、整组居中」（若改用 weight 等分，
     *  空槽消失后余下槽会变宽，卡间距随之拉开）。</li>
     * </ul>
     */
    protected void applySlotSizes(int containerW) {
        appliedContainerW = containerW;
        int cardW = (containerW - SLOT_COUNT * 2 * dp2px(SLOT_MARGIN_DP)) / SLOT_COUNT;
        if (cardW <= 0) return;
        slotStepPx = cardW + 2 * dp2px(SLOT_MARGIN_DP);
        int cardH = Math.round(cardW * CARD_ASPECT);
        for (ImageView iv : ivCards) {
            ViewGroup.LayoutParams lp = iv.getLayoutParams();
            if (lp != null) {
                lp.width = cardW;
                lp.height = cardH;
                iv.setLayoutParams(lp);
            }
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            View slot = slotViews[i];
            if (slot != null) {
                ViewGroup.LayoutParams slp = slot.getLayoutParams();
                if (slp != null && slp.width != cardW) {
                    slp.width = cardW;
                    slot.setLayoutParams(slp);
                }
            }
            TextView tv = tvPositions[i];
            if (tv != null) {
                ViewGroup.LayoutParams tlp = tv.getLayoutParams();
                if (tlp != null && tlp.width != cardW) {
                    tlp.width = cardW;
                    tv.setLayoutParams(tlp);
                }
            }
        }
        slotLabelMaxPx = cardW;
        fitAllPositionLabels();
    }

    /**
     * 按插槽行的布局实测宽纠正卡宽：行是 match_parent，其实测宽就是内容可用宽，与推算值
     * 不符（皮肤九宫格内缩、行/根 margin 等推算不到）时按实测重烘，保证五槽总宽恒等于可用宽
     * （既等宽又不溢出）。差值小于 3px 不重烘，避免与布局回调互相触发。
     */
    protected void bindContainerWidthCorrection() {
        if (slotViews[0] == null || !(slotViews[0].getParent() instanceof View)) return;
        View row = (View) slotViews[0].getParent();
        row.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            if (w <= 0 || Math.abs(w - appliedContainerW) < 3) return;
            applySlotSizes(w);
            row.requestLayout();
        });
    }

    /**
     * 卡位标签按固定槽宽自适应：先回写当前方向的基准字号（与 {@link #applyContentScale()} 同源，
     * 旋转往复不叠乘），再按实测文本宽超槽宽的比例缩小本槽字号（下限为基准的 60%），仍装不下则
     * 尾部省略号截断。基准取 {@code basePosPx}，上一次缩小的结果不会被当作新基准越缩越小。
     */
    protected void fitPositionLabel(int slot) {
        TextView tv = slot >= 0 && slot < SLOT_COUNT ? tvPositions[slot] : null;
        if (tv == null || slotLabelMaxPx <= 0) return;
        float base = basePosPx > 0 ? basePosPx * contentScale() : tv.getTextSize();
        if (base <= 0) return;
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, base);
        tv.setSingleLine(true);
        CharSequence text = tv.getText();
        int len = text == null ? 0 : text.length();
        if (len == 0) {
            tv.setEllipsize(null);
            return;
        }
        float w = tv.getPaint().measureText(text, 0, len);
        if (w > slotLabelMaxPx) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(base * 0.6f, base * slotLabelMaxPx / w));
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        } else {
            tv.setEllipsize(null);
        }
    }

    /** 逐槽重取标签字号（旋转重排改变了弹窗宽与基准字号后必须重做一次） */
    protected void fitAllPositionLabels() {
        for (int i = 0; i < SLOT_COUNT; i++) fitPositionLabel(i);
    }

    /**
     * 翻页丝滑过渡：拖动滚动条换卡重绑后，把整行按「翻页数×单卡槽步进」向滑动的反方向
     * 位移（钳制在 ±2 张内）再减速回零，使窗口内容视觉上随滑块连续滑入，消除瞬时换卡
     * 的僵硬感；连续快拖取消上一段动画从最新位移重启。Glide 内存缓存命中时卡图立即就位，
     * 动画期间不会看到占位图。
     */
    protected void animateSlotsShift(int delta) {
        if (delta == 0 || slotStepPx <= 0 || slotViews[0] == null
                || !(slotViews[0].getParent() instanceof View)) {
            return;
        }
        View row = (View) slotViews[0].getParent();
        float shift = Math.copySign(Math.min(Math.abs(delta), 2) * (float) slotStepPx, delta);
        row.animate().cancel();
        row.setTranslationX(shift);
        row.animate().translationX(0f).setDuration(160)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    protected int dp2px(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * 屏幕旋转后重排本弹窗：宽度是 <code>layout_game_right</code> 区域宽度的函数，每张卡图
     * 尺寸又据此宽度烘焙，故须重新解算区域宽、重烘焙卡图、按当前屏宽限宽弹窗，最后在区域
     * 布局完成后重新居中；通用重排仅改弹窗宽不动卡图会致横竖屏切换后卡片被裁剪或显示过小。
     */
    protected void applyOrientationRelayout() {
        if (popupWindow == null) return;
        final View content = popupWindow.getContentView();
        if (!(content instanceof ViewGroup) || ((ViewGroup) content).getChildCount() == 0) return;
        final View inner = ((ViewGroup) content).getChildAt(0);
        final android.app.Activity act = context instanceof android.app.Activity
                ? (android.app.Activity) context : null;
        final View region = act != null ? act.findViewById(R.id.layout_game_right) : null;
        Runnable apply = () -> {
            int width = resolveDialogWidth();
            applyCardImageSize(width);
            // 旋转后按新方向系数回写字号（绝对回写不叠乘），使文本随卡图/弹窗宽同步缩放
            applyContentScale();
            // 槽宽与卡位标签可用宽随新方向重烘，标签按新宽重新缩字（否则横转竖后仍按旧宽排布）
            fitAllPositionLabels();
            ViewGroup.LayoutParams raw = inner.getLayoutParams();
            if (raw instanceof android.widget.FrameLayout.LayoutParams) {
                android.widget.FrameLayout.LayoutParams flp =
                        (android.widget.FrameLayout.LayoutParams) raw;
                int[] fitted = DraggablePopupHelper.fitSizeToScreen(context, width,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                flp.width = fitted[0];
                inner.setLayoutParams(flp);
            }
            inner.requestLayout();
            if (region != null) {
                DraggablePopupHelper.centerPopupInRegion(popupWindow, region);
            }
        };
        // 旋转后区域宽高尚未更新，post 一帧让新视图树完成 measure/layout 再按实际区域宽重解
        if (region != null) region.post(apply);
        else inner.post(apply);
    }

    /**
     * 卡背图（core skin 路径，与 CardDetailPanel/TextureLoader 惯例一致）；
     * 对方卡优先 cover2.jpg，缺失时回退 cover.jpg（对齐 image_manager.cpp L280-282）
     */
    protected Bitmap getCoverBitmap(boolean opponent) {
        if (coverSelf == null || coverSelf.isRecycled()) {
            coverSelf = decodeCover(Constants.CORE_SKIN_COVER);
        }
        if (opponent) {
            if (coverOpponent == null || coverOpponent.isRecycled()) {
                coverOpponent = decodeCover(Constants.CORE_SKIN_COVER2);
            }
            if (coverOpponent != null) return coverOpponent;
        }
        return coverSelf;
    }

    protected Bitmap decodeCover(String name) {
        try {
            File file = new File(AppsSettings.get().getCoreSkinPath(), name);
            if (file.exists()) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (Throwable ignored) {
            // 解码失败按缺失处理，回退 unknown 占位图
        }
        return null;
    }

    /** 该卡项相对本地玩家是否处于对方侧（对齐 SetCardListLabel 的控侧判据） */
    protected boolean isOpponentSide(CardItem item) {
        return localPlayer >= 0 && item.controler != localPlayer;
    }

    /**
     * 里侧除外标记位图：整张 177×254 透明底，下半部 1/4 区域绘制的是对应玩家卡背自身
     * 底部 1/4 的裁切内容（源矩形裁剪而非整张卡背压低高度，对齐用户「显示卡背底部1/4部分」），
     * 再以 DST_IN + 竖向 LinearGradient（顶全透明→底全不透明）擦出「从上往下渐变显现」，
     * 前景叠在卡图上时与卡面 bounds 精确贴合（iv 宽高已烘焙为 177:254，fitCenter 填满）
     */
    protected static Bitmap makeFacedownMark(Bitmap cover) {
        int w = 177;
        int h = 254;
        int top = h * 3 / 4;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.save();
        cv.clipRect(0, top, w, h);
        // 源矩形取卡背图自身底部 1/4，目标矩形铺满标记下半部 1/4（宽高比一致，等比不拉伸）
        cv.drawBitmap(cover,
                new Rect(0, cover.getHeight() * 3 / 4, cover.getWidth(), cover.getHeight()),
                new Rect(0, top, w, h), null);
        cv.restore();
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        p.setShader(new LinearGradient(0, top, 0, h, 0x00FFFFFF, 0xFFFFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, top, w, h, p);
        return bmp;
    }

    /**
     * 给槽位卡图设置/清除里侧除外标记：foreground 叠渐变卡背位图，
     * alpha 以 90↔230 呼吸往返（REVERSE 无限循环）表达「从上往下渐变显示卡背」的视觉提示
     */
    protected void markFacedown(int slot, CardItem item) {
        cancelFacedownMark(slot);
        if (item == null || item.location != 0x20 || !item.facedownRemoved) return;
        boolean opponent = isOpponentSide(item);
        Bitmap cover = getCoverBitmap(opponent);
        if (cover == null) return;
        if (opponent) {
            if (markOpponent == null || markOpponent.isRecycled()) {
                markOpponent = makeFacedownMark(cover);
            }
        } else {
            if (markSelf == null || markSelf.isRecycled()) {
                markSelf = makeFacedownMark(cover);
            }
        }
        Bitmap mark = opponent ? markOpponent : markSelf;
        if (mark == null) return;
        // 每槽独立 BitmapDrawable：呼吸 alpha 互不干扰
        final BitmapDrawable fg = new BitmapDrawable(context.getResources(), mark);
        ImageView iv = ivCards[slot];
        iv.setForeground(fg);
        ValueAnimator anim = ValueAnimator.ofInt(90, 230);
        anim.setDuration(1100);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(a -> fg.setAlpha((int) a.getAnimatedValue()));
        anim.start();
        facedownAnims[slot] = anim;
    }

    protected void cancelFacedownMark(int slot) {
        if (facedownAnims[slot] != null) {
            facedownAnims[slot].cancel();
            facedownAnims[slot] = null;
        }
        if (ivCards[slot] != null) ivCards[slot].setForeground(null);
    }

    protected void cancelAllFacedownMarks() {
        for (int i = 0; i < SLOT_COUNT; i++) cancelFacedownMark(i);
    }

    protected void refreshSlots() {
        if (tipHelper != null) tipHelper.hide();
        if (cards.size() > SLOT_COUNT) {
            sbPage.setMax(cards.size() - SLOT_COUNT);
            sbPage.setVisibility(View.VISIBLE);
        } else {
            sbPage.setMax(0);
            sbPage.setProgress(0);
            sbPage.setVisibility(View.GONE);
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            int index = pageOffset + i;
            if (index < cards.size()) {
                slotViews[i].setVisibility(View.VISIBLE);
                CardItem item = cards.get(index);
                tvPositions[i].setText(formatLocation(item));
                // client_field.cpp SetCardListLabel：对方卡用对方背景色，我方卡默认色
                if (localPlayer >= 0 && item.controler != localPlayer) {
                    tvPositions[i].setBackgroundColor(COLOR_OPPONENT);
                } else {
                    tvPositions[i].setBackgroundColor(COLOR_DEFAULT);
                }
                // 标签宽度受固定槽宽约束：超宽只缩本槽字号，不撑槽（五张卡等宽完整显示的硬约束）
                fitPositionLabel(i);
                // code 可能带 0x80000000 翻面标志位，掩码后再使用（对齐 duelclient.cpp code & 0x7fffffff）
                int code = item.code & 0x7fffffff;
                if (code > 0) {
                    imageLoader.bindImage(ivCards[i], code, ImageLoader.Type.small);
                } else {
                    // client_field.cpp ShowLocationCard：code==0 显示按控制者区分的卡背
                    Bitmap cover = getCoverBitmap(isOpponentSide(item));
                    if (cover != null) {
                        ivCards[i].setImageBitmap(cover);
                    } else {
                        ivCards[i].setImageResource(R.drawable.unknown);
                    }
                }
                // 里侧表示除外的卡：卡面下半部1/4叠加呼吸渐变的对应玩家卡背标记
                markFacedown(i, item);
            } else {
                cancelFacedownMark(i);
                slotViews[i].setVisibility(View.GONE);
            }
        }
    }

    protected String formatLocation(CardItem item) {
        if (item.location == 0x80) {
            return getLocationName(item.location, item.sequence) + "[" + (item.sequence + 1) + "](" + (item.subSeq + 1) + ")";
        }
        return getLocationName(item.location, item.sequence) + "[" + (item.sequence + 1) + "]";
    }

    /**
     * 移植 data_manager.cpp FormatLocation：STRING_ID_LOCATION=1000 按位索引；
     * LOCATION_SZONE 按 sequence 区分魔陷区/场地魔法区/灵摆区
     */
    protected String getLocationName(int location, int sequence) {
        if (location == 0x08) {
            if (sequence < 5) {
                return mStringManager.getSystemString(1003, "魔陷区");
            } else if (sequence == 5) {
                return mStringManager.getSystemString(1008, "场地魔法区");
            } else {
                return mStringManager.getSystemString(1009, "灵摆区");
            }
        }
        int stringId = 0;
        for (int i = 0; i < 10; i++) {
            if ((0x1 << i) == location) {
                stringId = 1000 + i;
                break;
            }
        }
        if (stringId != 0) {
            return mStringManager.getSystemString(stringId, "区域" + location);
        }
        return "区域" + location;
    }

    protected GameEngine engine() {
        if (context instanceof YGOProActivity) return ((YGOProActivity) context).getEngine();
        return null;
    }

    protected CardItem itemAtSlot(int slot) {
        int index = pageOffset + slot;
        if (index < 0 || index >= cards.size()) return null;
        return cards.get(index);
    }

    /** 协议侧 controler → 本地视角侧（localPlayer 为对合映射，视角侧口径时原样返回） */
    protected int viewControler(int controler) {
        if (!controlerProtocolSide) return controler & 1;
        GameEngine eng = engine();
        return eng != null ? eng.localPlayer(controler & 1) : (controler & 1);
    }

    /** 抬手且未超过按住阈值：视为点击，交由宿主决定发送响应等 */
    protected void onSlotClicked(int slot) {
        CardItem item = itemAtSlot(slot);
        if (item != null && cardClickListener != null) {
            cardClickListener.onCardClick(item);
        }
    }

    /** 按下即在 CardDetailPanel 显示卡片详情：与 CardSelectDialog 同——详情按 code 查卡表
     * （game.cpp Game::ShowCardInfo(int code)），直接采用与卡图同源的 item.code，
     * 场上实时卡暗卡时 code==0 不可依赖；code==0 显示卡背默认面板
     */
    protected void showDetailAtSlot(int slot) {
        CardItem item = itemAtSlot(slot);
        if (item == null || !(context instanceof YGOProActivity)) return;
        ClientCard card = new ClientCard();
        card.code = item.code & 0x7fffffff;
        card.controler = viewControler(item.controler);
        card.location = item.location & 0x7f;
        card.sequence = item.sequence;
        ((YGOProActivity) context).showCardInfoPanel(card);
    }

    /** 常显标签文字：通讯状态（ShowCardInfoInList 等价物），点击即取、换卡即换文字 */
    protected String statusTextAtSlot(int slot) {
        CardItem item = itemAtSlot(slot);
        if (item == null) return null;
        GameEngine eng = engine();
        GameField field = eng != null ? eng.getField() : null;
        ClientCard card = CardStatusTipHelper.resolveLiveCard(field,
                viewControler(item.controler), item.location, item.sequence, item.subSeq);
        if (card == null && (item.code & 0x7fffffff) != 0) {
            card = new ClientCard();
            card.code = item.code & 0x7fffffff;
        }
        return CardStatusTipHelper.buildStatusText(field, card);
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            try {
                popupWindow.dismiss();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }
}
