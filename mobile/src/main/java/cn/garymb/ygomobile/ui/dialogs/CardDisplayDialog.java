package cn.garymb.ygomobile.ui.dialogs;

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
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.ClientCard;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardStatusTipHelper;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;

public class CardDisplayDialog {

    public static final int SLOT_COUNT = 5;

    // 卡图宽高比 177:254：ImageView 高度 = 宽度 × CARD_ASPECT
    private static final float CARD_ASPECT = 254f / 177f;
    // 对话框宽度取 layout_game_right 实际宽度的四分之三
    private static final float DIALOG_WIDTH_RATIO = 0.75f;
    /**
     * 竖屏整体放大系数（宽与文本同比）：与 CardSelectDialog 一致。竖屏时 layout_game_right 即整屏宽，按
     * {@link #DIALOG_WIDTH_RATIO} (0.75) 解算后弹窗与每张卡图明显小于横屏（横屏 layout_game_right 为长边、绝对宽更大）。
     * 按需求把竖屏弹窗再放大——宽度以整屏两侧收进为准（0.75×1.25≈0.94，两侧各收进约 3%），卡图随宽度变大，
     * 标题/卡位文本/确定按钮文本按同系数增大，消除旋转后「卡片图和它上面的文本被缩得更小」的观感。
     */
    private static final float PORTRAIT_SCALE = 1.25f;
    /**
     * 横屏整体缩小系数（与 CardSelectDialog 一致）：按需求把横屏弹窗再等比例小一号，
     * 弹窗宽、卡图、标题/卡位文本/确定按钮字号同乘本系数，列表观感更紧凑。
     */
    private static final float LANDSCAPE_SCALE = 0.9f;
    /** 每个卡槽的水平外边距（dp），与 dialog_card_display.xml 中槽位 layout_marginHorizontal 一致，由原 4dp 收紧为 2dp */
    private static final float SLOT_MARGIN_DP = 2f;
    // 位置标签配色，映射 client_field.h CARD_LIST_* 常量
    private static final int COLOR_DEFAULT = YGOUtil.c(R.color.colorMain);   // CARD_LIST_DEFAULT_BACKGROUND_COLOR
    private static final int COLOR_OPPONENT = YGOUtil.c(R.color.card_list_opponent_gray);  // CARD_LIST_OPPONENT_BACKGROUND_COLOR

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
    private static final List<CardDisplayDialog> LIVE_DIALOGS = new ArrayList<>();

    /** 供外部（EngineCallbackDelegate.onFieldChanged / ReplayListener.onReplayFieldChanged）
     *  在主线程主动刷新所有带 supplier 的展示中弹窗；无 supplier 的弹窗自动从列表中摘除 */
    public static void refreshLiveDialogs() {
        for (int i = LIVE_DIALOGS.size() - 1; i >= 0; i--) {
            CardDisplayDialog d = LIVE_DIALOGS.get(i);
            if (d == null || d.liveSupplier == null || !d.isShowing()) {
                LIVE_DIALOGS.remove(i);
                continue;
            }
            d.replaceCards(d.liveSupplier.obtain());
        }
    }

    private final Context context;
    private final ImageLoader imageLoader;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 公共字符串管理器：初始化后可供整个类调用（对齐 CardDetailPanel.mStringManager 惯例） */
    public final StringManager mStringManager = DataManager.get().getStringManager();

    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;
    /** 按下查看详情 / 按住悬浮通讯状态标签（每次 build 随包装层重建） */
    private CardStatusTipHelper tipHelper;

    private List<CardItem> cards = new ArrayList<>();
    private String titlePrefix = "卡片确认"; // 默认区域名称前缀（不依赖 conf，实际调用均会 setTitle 覆盖）
    private int pageOffset = 0;
    private int localPlayer = -1;
    /**
     * item.controler 口径：true=协议侧（ShowDialogUtil），false=本地视角侧（CmdMenuDialog）。
     */
    private boolean controlerProtocolSide = true;

    private TextView tvTitle;
    private SeekBar sbPage;
    private View btnOk;
    private final View[] slotViews = new View[SLOT_COUNT];
    private final TextView[] tvPositions = new TextView[SLOT_COUNT];
    private final ImageView[] ivCards = new ImageView[SLOT_COUNT];
    /** 单卡槽步进（px，卡宽 + 左右外边距 {@link #SLOT_MARGIN_DP}dp×2）：滚动条翻页动画的位移基准，随卡图在 {@link #applyCardImageSize(int)} 烘培 */
    private int slotStepPx = 0;
    /**
     * 标题/卡位文本/确定按钮的基准字号（px，取自 XML，横竖屏 density 相同故一致）：首次 {@link #applyContentScale()}
     * 时捕获，此后按当前方向系数以绝对字号回写，避免横竖屏来回旋转时系数叠乘使文本越放越大。
     */
    private float baseTitlePx;
    private float basePosPx;
    private float baseOkPx;

    private OnDismissListener dismissListener;
    private OnCardClick cardClickListener;
    /** 实时观察提供器（非空则弹窗在 show/dismiss 时维护 LIVE_DIALOGS 名单，且 field 变化会重拉列表） */
    private CardItemSupplier liveSupplier;

    // 卡背缓存：对齐 image_manager.cpp tButtonFacedown[0/1]（我方 cover.jpg / 对方 cover2.jpg）
    private static Bitmap coverSelf;
    private static Bitmap coverOpponent;
    // 里侧除外标记位图缓存（下半部1/4竖向渐变卡背，见 makeFacedownMark）：按玩家侧区分
    private static Bitmap markSelf;
    private static Bitmap markOpponent;
    /** 每槽位里侧除外呼吸动画句柄：换卡/关窗时取消并清理 foreground */
    private final ValueAnimator[] facedownAnims = new ValueAnimator[SLOT_COUNT];

    public CardDisplayDialog(Context context, ImageLoader imageLoader) {
        this.context = context;
        this.imageLoader = imageLoader;
    }

    public CardDisplayDialog setTitle(String title) {
        this.titlePrefix = title;
        updateTitle();
        return this;
    }

    /** 更新标题为「区域名称 (卡片数量)」格式 */
    private void updateTitle() {
        if (tvTitle == null) return;
        String text = titlePrefix + "(" + cards.size() + ")";
        tvTitle.setText(text);
        // 收缩横条与弹窗内标题同源，卡片数量变化时同步文案
        DraggablePopupHelper.setCollapseBarTitle(popupWindow, text);
    }

    public CardDisplayDialog setCards(List<CardItem> cardList) {
        this.cards = new ArrayList<>();
        if (cardList != null) {
            // 显示顺序整体反序：传入序列的末位排在第一槽
            //（该列表多来自场上/手卡收集，反序后与决斗场视觉方向一致；
            // 点击回调传出 CardItem 对象、不携带列表下标，反序不影响任何协议语义）
            for (int i = cardList.size() - 1; i >= 0; i--) {
                this.cards.add(cardList.get(i));
            }
        }
        this.pageOffset = 0;
        updateTitle(); // 卡片数量变化时更新标题
        return this;
    }

    /** 我方协议玩家索引，用于区分对方卡标签背景色（对齐 SetCardListLabel） */
    public CardDisplayDialog setLocalPlayer(int localPlayer) {
        this.localPlayer = localPlayer;
        return this;
    }

    public CardDisplayDialog setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    public CardDisplayDialog setCardClickListener(OnCardClick listener) {
        this.cardClickListener = listener;
        return this;
    }

    /** 声明 CardItem.controler 的口径（true=协议侧，false=本地视角侧），影响详情与状态标签的取卡 */
    public CardDisplayDialog setControlerProtocolSide(boolean protocolSide) {
        this.controlerProtocolSide = protocolSide;
        return this;
    }

    /** 安装实时观察 supplier：弹窗展示期间，field 变化会触发重新拉取列表并就地刷新。
     *  仅 CmdMenuDialog 堆叠区查看列表入口使用（观战/录像），普通确认弹窗不安装。
     *  同时会在每次 pullFresh() 后自动更新标题中的卡片数量统计。
     */
    public CardDisplayDialog observeLive(CardItemSupplier supplier) {
        this.liveSupplier = supplier;
        return this;
    }

    /** 就地替换卡片列表（不重建 popup / 不重启动画）；保留当前页位（在新页范内时）。
     *  传入与 setCards 同语义的顺位列表，内部仍取反序。 */
    public void replaceCards(List<CardItem> newCards) {
        this.cards = new ArrayList<>();
        if (newCards != null) {
            for (int i = newCards.size() - 1; i >= 0; i--) this.cards.add(newCards.get(i));
        }
        int maxPage = Math.max(0, this.cards.size() - SLOT_COUNT);
        if (pageOffset > maxPage) pageOffset = maxPage;
        if (pageOffset < 0) pageOffset = 0;
        if (sbPage != null) {
            if (this.cards.size() > SLOT_COUNT) {
                sbPage.setMax(maxPage);
                sbPage.setVisibility(View.VISIBLE);
                sbPage.setProgress(pageOffset);
            } else {
                sbPage.setMax(0);
                sbPage.setProgress(0);
                sbPage.setVisibility(View.GONE);
            }
        }
        if (isShowing()) refreshSlots();
        // 实时更新标题中的卡片数量
        updateTitle();
    }

    private void build() {
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
        popupWindow = new PopupWindow(root,
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
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "card_display");
        int dialogWidth = resolveDialogWidth();
        applyCardImageSize(dialogWidth);
        draggableHelper.setupDraggablePopup(popupWindow, root,
                dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        // 宽度按 layout_game_right 区域实时解算且据此烘焙每张卡图，通用重排无法同步子视图尺寸：
        // 注册自定义旋转重排，切换方向时重解区域宽、重烘焙卡图、重设弹窗宽并重新居中
        draggableHelper.registerOrientationRelayout(popupWindow, this::applyOrientationRelayout);
        // 收缩模式：点内容左上角「▼」把手缩为与聊天输入框同高的底部横条，确认场地后点横条恢复
        DraggablePopupHelper.enableCollapse(popupWindow, tvTitle, titlePrefix);

        // 按下即在 CardDetailPanel 显示详情；按住超过 0.3 秒悬浮显示通讯状态标签且抬手不视为点击
        tipHelper = new CardStatusTipHelper(context, root);
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
        // 竖屏按系数放大标题/卡位文本/确定按钮字号（横屏 1.0 不变），与已放大的弹窗宽度、卡图同步
        applyContentScale();
    }

    /**
     * 对话框宽度 = layout_game_right 实际宽度 × 3/4；
     * 取不到时依次降级为窗口 decorView 宽度、屏幕宽度
     */
    private int resolveDialogWidth() {
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
        return Math.round(w * DIALOG_WIDTH_RATIO * contentScale());
    }

    /** 当前方向的内容缩放系数：横屏 {@link #LANDSCAPE_SCALE}（等比例小一号），竖屏 {@link #PORTRAIT_SCALE} */
    private float contentScale() {
        return DialogScale.isLandscape(context) ? LANDSCAPE_SCALE : PORTRAIT_SCALE;
    }

    /**
     * 按当前方向把标题/卡位文本/确定按钮字号回写为「基准 × 系数」的绝对值（系数见 {@link #contentScale()}）。
     * 首次调用捕获基准（此时视图仍为 XML 原始字号），此后绝对回写，旋转往复不叠乘。build() 与旋转重排均调用。
     * btnOk 本类声明为 View，仅当其为 TextView 时回写字号。
     */
    private void applyContentScale() {
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
     * 按对话框宽度反推每张卡宽（扣除根内边距 16dp×2 与每槽水平外边距 {@link #SLOT_MARGIN_DP}dp×2），
     * 高度按 177:254 卡图比例计算，保证 ImageView 宽高与卡片比例一致；
     * 槽位为 wrap_content，固定宽度后空槽位 GONE 时其余槽位尺寸不变并整体居中
     */
    private void applyCardImageSize(int dialogWidthPx) {
        int containerW = dialogWidthPx - 2 * dp2px(16);
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
    }

    /**
     * 翻页丝滑过渡：拖动滚动条换卡重绑后，把整行按「翻页数×单卡槽步进」向滑动的反方向
     * 位移（钳制在 ±2 张内）再减速回零，使窗口内容视觉上随滑块连续滑入，消除瞬时换卡
     * 的僵硬感；连续快拖取消上一段动画从最新位移重启。Glide 内存缓存命中时卡图立即就位，
     * 动画期间不会看到占位图。
     */
    private void animateSlotsShift(int delta) {
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

    private int dp2px(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * 屏幕旋转后重排本弹窗：宽度是 <code>layout_game_right</code> 区域宽度的函数，每张卡图
     * 尺寸又据此宽度烘焙，故须重新解算区域宽、重烘焙卡图、按当前屏宽限宽弹窗，最后在区域
     * 布局完成后重新居中；通用重排仅改弹窗宽不动卡图会致横竖屏切换后卡片被裁剪或显示过小。
     */
    private void applyOrientationRelayout() {
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
    private Bitmap getCoverBitmap(boolean opponent) {
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

    private Bitmap decodeCover(String name) {
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
    private boolean isOpponentSide(CardItem item) {
        return localPlayer >= 0 && item.controler != localPlayer;
    }

    /**
     * 里侧除外标记位图：整张 177×254 透明底，下半部 1/4 区域绘制的是对应玩家卡背自身
     * 底部 1/4 的裁切内容（源矩形裁剪而非整张卡背压低高度，对齐用户「显示卡背底部1/4部分」），
     * 再以 DST_IN + 竖向 LinearGradient（顶全透明→底全不透明）擦出「从上往下渐变显现」，
     * 前景叠在卡图上时与卡面 bounds 精确贴合（iv 宽高已烘焙为 177:254，fitCenter 填满）
     */
    private static Bitmap makeFacedownMark(Bitmap cover) {
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
    private void markFacedown(int slot, CardItem item) {
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

    private void cancelFacedownMark(int slot) {
        if (facedownAnims[slot] != null) {
            facedownAnims[slot].cancel();
            facedownAnims[slot] = null;
        }
        if (ivCards[slot] != null) ivCards[slot].setForeground(null);
    }

    private void cancelAllFacedownMarks() {
        for (int i = 0; i < SLOT_COUNT; i++) cancelFacedownMark(i);
    }

    private void refreshSlots() {
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

    private String formatLocation(CardItem item) {
        if (item.location == 0x80) {
            return getLocationName(item.location, item.sequence) + "[" + (item.sequence + 1) + "](" + (item.subSeq + 1) + ")";
        }
        return getLocationName(item.location, item.sequence) + "[" + (item.sequence + 1) + "]";
    }

    /**
     * 移植 data_manager.cpp FormatLocation：STRING_ID_LOCATION=1000 按位索引；
     * LOCATION_SZONE 按 sequence 区分魔陷区/场地魔法区/灵摆区
     */
    private String getLocationName(int location, int sequence) {
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

    private GameEngine engine() {
        if (context instanceof YGOProActivity) return ((YGOProActivity) context).getEngine();
        return null;
    }

    private CardItem itemAtSlot(int slot) {
        int index = pageOffset + slot;
        if (index < 0 || index >= cards.size()) return null;
        return cards.get(index);
    }

    /** 协议侧 controler → 本地视角侧（localPlayer 为对合映射，视角侧口径时原样返回） */
    private int viewControler(int controler) {
        if (!controlerProtocolSide) return controler & 1;
        GameEngine eng = engine();
        return eng != null ? eng.localPlayer(controler & 1) : (controler & 1);
    }

    /** 抬手且未超过按住阈值：视为点击，交由宿主决定发送响应等 */
    private void onSlotClicked(int slot) {
        CardItem item = itemAtSlot(slot);
        if (item != null && cardClickListener != null) {
            cardClickListener.onCardClick(item);
        }
    }

    /** 按下即在 CardDetailPanel 显示卡片详情：与 CardSelectDialog 同——详情按 code 查卡表
     * （game.cpp Game::ShowCardInfo(int code)），直接采用与卡图同源的 item.code，
     * 场上实时卡暗卡时 code==0 不可依赖；code==0 显示卡背默认面板
     */
    private void showDetailAtSlot(int slot) {
        CardItem item = itemAtSlot(slot);
        if (item == null || !(context instanceof YGOProActivity)) return;
        ClientCard card = new ClientCard();
        card.code = item.code & 0x7fffffff;
        card.controler = viewControler(item.controler);
        card.location = item.location & 0x7f;
        card.sequence = item.sequence;
        ((YGOProActivity) context).showCardInfoPanel(card);
    }

    /** 按住悬浮标签文字：通讯状态（ShowCardInfoInList 等价物） */
    private String statusTextAtSlot(int slot) {
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

    public void show() {
        show(null);
    }

    public void show(View anchorView) {
        build();
        if (popupWindow == null) return;
        Runnable showAction = () -> {
            if (popupWindow == null || popupWindow.isShowing()) return;
            View anchor = anchorView;
            if (anchor == null && context instanceof android.app.Activity) {
                android.app.Activity act = (android.app.Activity) context;
                if (!act.isFinishing() && !act.isDestroyed()) {
                    anchor = act.getWindow().getDecorView();
                }
            }
            if (anchor == null || anchor.getWindowToken() == null) return;
            refreshSlots();
            try {
                // 默认居中于 layout_game_right 区域而非整个窗口（与 YesOrNoDialog 同模式，
                // 拖拽保存过位置时 showPopup 走 NO_GRAVITY 绝对坐标，不受此 margin 影响）
                if (context instanceof android.app.Activity) {
                    View region = ((android.app.Activity) context).findViewById(R.id.layout_game_right);
                    DraggablePopupHelper.centerPopupInRegion(popupWindow, region);
                }
                if (draggableHelper != null) {
                    draggableHelper.showPopup(popupWindow, anchor);
                } else {
                    popupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
                }
                // 对话框显示期间禁用决斗场阶段按钮
                if (context instanceof YGOProActivity) {
                    ((YGOProActivity) context).notifyGameDialogShown(this);
                }
                if (liveSupplier != null && !LIVE_DIALOGS.contains(this)) {
                    LIVE_DIALOGS.add(this);
                }
            } catch (Exception e) {
                // Token expired or window already showing
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showAction.run();
        } else {
            handler.post(showAction);
        }
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