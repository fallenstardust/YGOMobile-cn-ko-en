package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardStatusTipHelper;

import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;

public class CardSelectDialog {

    public static final int SLOT_COUNT = 5;

    public static final int MODE_SELECT = 0;
    public static final int MODE_UNSELECT = 1;
    public static final int MODE_SUM = 2;
    public static final int MODE_SORT = 3;

    // 位置标签配色，映射 client_field.h CARD_LIST_* 常量
    private static final int COLOR_DEFAULT = YGOUtil.c(R.color.colorMain);   // CARD_LIST_DEFAULT_BACKGROUND_COLOR
    private static final int COLOR_SELECTED = YGOUtil.c(R.color.card_list_selected_bg);  // CARD_LIST_SELECTED_BACKGROUND_COLOR
    private static final int COLOR_OPPONENT = YGOUtil.c(R.color.card_list_opponent_gray);  // CARD_LIST_OPPONENT_BACKGROUND_COLOR

    // 卡图宽高比 177:254：ImageView 高度 = 宽度 × CARD_ASPECT
    private static final float CARD_ASPECT = 254f / 177f;
    // 对话框宽度取 layout_game_right 实际宽度的四分之三
    private static final float DIALOG_WIDTH_RATIO = 0.75f;
    /**
     * 竖屏整体放大系数（宽与文本同比）：竖屏时 layout_game_right 即整屏宽，按 {@link #DIALOG_WIDTH_RATIO}
     * (0.75) 解算后弹窗与每张卡图明显小于横屏（横屏 layout_game_right 为长边、绝对宽更大）。按需求把竖屏弹窗
     * 再放大——宽度以整屏两侧收进为准（0.75×1.25≈0.94，两侧各收进约 3%），卡图随宽度变大，标题/卡位文本/
     * 确定按钮文本按同系数增大，消除旋转后「卡片图和它上面的文本被缩得更小」的观感。横屏恒为 1.0（行为不变）。
     */
    private static final float PORTRAIT_SCALE = 1.25f;

    public static class CardItem {
        public final int code;
        public final int controler;
        public final int location;
        public final int sequence;
        public final int subSeq;
        public final int selectSeq;
        public final int opParam;
        public final int op1;
        public final int op2;

        public CardItem(int code, int controler, int location, int sequence, int subSeq, int selectSeq) {
            this(code, controler, location, sequence, subSeq, selectSeq, 0);
        }

        public CardItem(int code, int controler, int location, int sequence, int subSeq, int selectSeq, int opParam) {
            this.code = code;
            this.controler = controler;
            this.location = location;
            this.sequence = sequence;
            this.subSeq = subSeq;
            this.selectSeq = selectSeq;
            this.opParam = opParam;
            int o1 = opParam & 0xffff;
            int o2 = (opParam >> 16) & 0xffff;
            if ((o2 & 0x8000) != 0) {
                o1 = opParam & 0x7fffffff;
                o2 = 0;
            }
            this.op1 = o1;
            this.op2 = o2;
        }
    }

    public interface OnCardSelectListener {
        default void onCardsSelected(List<Integer> selectedIndices) {
        }

        default void onCardClicked(int index) {
        }

        default void onSorted(int[] respBuf) {
        }

        default void onCancel() {
        }
    }

    public interface OnDismissListener {
        void onDismiss();
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

    private int mode = MODE_SELECT;
    private String title = "选择卡片";
    private List<CardItem> cards = new ArrayList<>();
    private List<CardItem> mustCards = new ArrayList<>();
    private boolean[] selected = new boolean[0];
    private int minSelect = 0;
    private int maxSelect = 1;
    private boolean cancelable = false;
    private boolean finishable = false;
    private int sumValue = 0;
    private int sumMode = 0;
    private int localPlayer = -1;
    private boolean showValues = false;
    private int pageOffset = 0;
    /**
     * item.controler 是否为协议侧索引：ShowDialogUtil 由通讯解析构建 → true（默认）；
     * CmdMenuDialog 由场上模型构建（已是视角侧）→ false。视角侧转换见 {@link #viewControler(int)}。
     */
    private boolean controlerProtocolSide = true;

    private final List<Integer> clickOrder = new ArrayList<>();
    private int[] sortList = new int[0];
    private int sortCounter = 0;
    /**
     * 显示序 → 协议序索引的排布表（长度 = 显示总数），在 {@link #show(View)} 时按初始
     * 选择状态构建一次，本次会话内固定不变（避免点选时卡片跳动）。
     * 规则：沿用原有「协议序整体反转」的相对顺序，但把已选中的可选卡稳定地挪到列表末尾
     */
    private int[] displayOrder = new int[0];

    private TextView tvTitle;
    private SeekBar sbPage;
    private Button btnOk;
    private final View[] slotViews = new View[SLOT_COUNT];
    private final TextView[] tvPositions = new TextView[SLOT_COUNT];
    private final ImageView[] ivCards = new ImageView[SLOT_COUNT];
    /** 单卡槽步进（px，卡宽 + 左右外边距 4dp×2）：滚动条翻页动画的位移基准，随卡图在 {@link #applyCardImageSize(int)} 烘焙 */
    private int slotStepPx = 0;
    /**
     * 标题/卡位文本/确定按钮的基准字号（px，取自 XML，横竖屏 density 相同故一致）：首次 {@link #applyContentScale()}
     * 时捕获，此后按当前方向系数以绝对字号回写，避免横竖屏来回旋转时系数叠乘使文本越放越大。
     */
    private float baseTitlePx;
    private float basePosPx;
    private float baseOkPx;

    private OnCardSelectListener listener;
    private OnDismissListener dismissListener;

    // 卡背缓存：对齐 image_manager.cpp tButtonFacedown[0/1]（我方 cover.jpg / 对方 cover2.jpg）
    private static Bitmap coverSelf;
    private static Bitmap coverOpponent;

    public CardSelectDialog(Context context, ImageLoader imageLoader) {
        this.context = context;
        this.imageLoader = imageLoader;
    }

    public CardSelectDialog setMode(int mode) {
        this.mode = mode;
        return this;
    }

    public CardSelectDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    public CardSelectDialog setCards(List<CardItem> cardList) {
        this.cards = cardList != null ? cardList : new ArrayList<>();
        this.selected = new boolean[this.cards.size()];
        this.clickOrder.clear();
        this.sortList = new int[this.cards.size()];
        this.sortCounter = 0;
        this.displayOrder = new int[0];
        this.pageOffset = 0;
        return this;
    }

    public CardSelectDialog setMustCards(List<CardItem> mustList) {
        this.mustCards = mustList != null ? mustList : new ArrayList<>();
        return this;
    }

    public CardSelectDialog setPreSelected(boolean[] flags) {
        if (flags != null && flags.length == selected.length) {
            for (int i = 0; i < selected.length; i++) {
                selected[i] = flags[i];
            }
        }
        return this;
    }

    public CardSelectDialog setSelectRange(int min, int max) {
        this.minSelect = Math.max(0, min);
        this.maxSelect = Math.max(this.minSelect, max);
        return this;
    }

    public CardSelectDialog setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    public CardSelectDialog setFinishable(boolean finishable) {
        this.finishable = finishable;
        return this;
    }

    public CardSelectDialog setSumValue(int value, int sumMode) {
        this.sumValue = value;
        this.sumMode = sumMode;
        return this;
    }

    public CardSelectDialog setLocalPlayer(int localPlayer) {
        this.localPlayer = localPlayer;
        return this;
    }

    public CardSelectDialog setValueVisible(boolean showValues) {
        this.showValues = showValues;
        return this;
    }

    /** 声明 CardItem.controler 的口径（true=协议侧，false=本地视角侧），影响详情与状态标签的取卡 */
    public CardSelectDialog setControlerProtocolSide(boolean protocolSide) {
        this.controlerProtocolSide = protocolSide;
        return this;
    }

    public CardSelectDialog setListener(OnCardSelectListener listener) {
        this.listener = listener;
        return this;
    }

    public CardSelectDialog setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    public int getSelectedCount() {
        int count = 0;
        for (boolean b : selected) {
            if (b) count++;
        }
        if (mode == MODE_SUM) count += mustCards.size();
        return count;
    }

    public int getMinSelect() {
        return minSelect;
    }

    public boolean isCancelable() {
        return cancelable;
    }

    public boolean isReady() {
        if (mode == MODE_SUM) {
            int optCount = getSelectedCount() - mustCards.size();
            return optCount >= minSelect && optCount <= maxSelect && checkSumValue();
        }
        return getSelectedCount() >= minSelect;
    }

    public void confirm() {
        if (mode == MODE_SELECT || mode == MODE_SUM) {
            if (!isReady()) return;
            if (listener != null) listener.onCardsSelected(new ArrayList<>(clickOrder));
        } else if (mode == MODE_UNSELECT) {
            // event_handler.cpp BUTTON_CARD_SEL_OK (MSG_SELECT_UNSELECT_CARD L968-971)：
            // 完成按钮 = 发送 -1，仅关闭弹窗会导致协议挂起
            if (listener != null) listener.onCancel();
        }
        dismiss();
    }

    private int getDisplayCount() {
        return cards.size() + mustCards.size();
    }

    /**
     * 显示序 → 协议序索引：沿用协议序整体反转的相对顺序（协议末位卡排在第一），
     * 并把已选中的可选卡稳定地排到列表末尾（见 {@link #buildDisplayOrder()}）。
     * selected/sortList/点击回调/协议响应全部经此换算取协议索引，排布仅影响槽位顺序，
     * 不影响发送协议的索引语义。排布表未构建时兜底为纯反转。
     */
    private int displayToProto(int displayIdx) {
        int count = getDisplayCount();
        if (displayIdx < 0 || displayIdx >= count) return -1;
        if (displayOrder != null && displayOrder.length == count) return displayOrder[displayIdx];
        return count - 1 - displayIdx;
    }

    /**
     * 按初始选择状态构建显示排布：先保持既有「协议序整体反转」的相对顺序，再把已选中的
     * 可选卡（非必选卡）稳定挪到末尾。必选卡与未选卡留在前段相对顺序不变，因此无预选的普通
     * 选择弹窗排布与旧行为完全一致；仅取消选择/带预选的重弹弹窗会把已选卡沉到列表最后。
     */
    private void buildDisplayOrder() {
        int count = getDisplayCount();
        int m = mustCards.size();
        int[] ordered = new int[count];
        int w = 0;
        // 前段：未挪到末尾的卡（必选卡 + 未选可选卡），保持协议序反转的相对顺序
        for (int d = 0; d < count; d++) {
            int p = count - 1 - d;
            if (!isMovedToLast(p, m)) ordered[w++] = p;
        }
        // 末段：已选中的可选卡，同样按协议序反转的相对顺序追加
        for (int d = 0; d < count; d++) {
            int p = count - 1 - d;
            if (isMovedToLast(p, m)) ordered[w++] = p;
        }
        displayOrder = ordered;
    }

    /** 该协议序是否为「需挪到末尾」的已选可选卡（必选卡不算，保持在原位） */
    private boolean isMovedToLast(int proto, int mustCount) {
        if (proto < mustCount) return false;
        int sel = proto - mustCount;
        return sel >= 0 && sel < selected.length && selected[sel];
    }

    /** 显示序 → 卡片项：经 displayToProto 反转换回协议序（必选卡在前、可选卡在后），越界返回 null */
    private CardItem getItemAt(int displayIdx) {
        int idx = displayToProto(displayIdx);
        if (idx < 0) return null;
        if (idx < mustCards.size()) return mustCards.get(idx);
        idx -= mustCards.size();
        return idx < cards.size() ? cards.get(idx) : null;
    }

    /** 显示序 → 是否已选：必选卡恒视为已选（对齐 client_field.cpp 必选卡高亮） */
    private boolean isSelectedAt(int displayIdx) {
        int idx = displayToProto(displayIdx);
        if (idx < 0) return false;
        if (idx < mustCards.size()) return true;
        idx -= mustCards.size();
        return idx >= 0 && idx < selected.length && selected[idx];
    }

    private GameEngine engine() {
        if (context instanceof YGOProActivity) return ((YGOProActivity) context).getEngine();
        return null;
    }

    /** 槽位 → 卡片项：槽位先加 pageOffset 换算为显示序，越界返回 null（空槽/翻页尾部） */
    private CardItem itemAtSlot(int slot) {
        int index = pageOffset + slot;
        if (index < 0 || index >= getDisplayCount()) return null;
        return getItemAt(index);
    }

    /** 协议侧 controler → 本地视角侧（localPlayer 为对合映射，视角侧口径时原样返回） */
    private int viewControler(int controler) {
        if (!controlerProtocolSide) return controler & 1;
        GameEngine eng = engine();
        return eng != null ? eng.localPlayer(controler & 1) : (controler & 1);
    }

    /**
     * 按下即在 CardDetailPanel 显示卡片详情：详情面板只显卡表原始数据（完全由 code 决定，
     * 对齐 game.cpp Game::ShowCardInfo(int code) 按 code 查数据库），因此直接采用与
     * updateSlotView 卡图绑定同源的 item.code & 0x7fffffff 从 CardManager 获取——
     * 场上实时卡（resolveLiveCard）暗卡时 code==0，不能用它判断，否则能看见卡图却只显示未知/卡背；
     * code==0（该槽显示的是卡背图）时对齐 C++ ClearCardInfo 显示卡背默认面板
     */
    private void showDetailAtSlot(int slot) {
        CardItem item = itemAtSlot(slot);
        if (item == null || !(context instanceof YGOProActivity)) return;
        GameField.ClientCard card = new GameField.ClientCard();
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
        GameField.ClientCard card = CardStatusTipHelper.resolveLiveCard(field,
                viewControler(item.controler), item.location, item.sequence, item.subSeq);
        if (card == null && (item.code & 0x7fffffff) != 0) {
            card = new GameField.ClientCard();
            card.code = item.code & 0x7fffffff;
        }
        return CardStatusTipHelper.buildStatusText(field, card);
    }

    private void build() {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_card_select, null);
        tvTitle = root.findViewById(R.id.tv_card_select_title);
        sbPage = root.findViewById(R.id.sb_card_page);
        btnOk = root.findViewById(R.id.btn_card_select_ok);

        slotViews[0] = root.findViewById(R.id.slot_card_0);
        tvPositions[0] = root.findViewById(R.id.tv_card_pos_0);
        ivCards[0] = root.findViewById(R.id.iv_card_0);
        slotViews[1] = root.findViewById(R.id.slot_card_1);
        tvPositions[1] = root.findViewById(R.id.tv_card_pos_1);
        ivCards[1] = root.findViewById(R.id.iv_card_1);
        slotViews[2] = root.findViewById(R.id.slot_card_2);
        tvPositions[2] = root.findViewById(R.id.tv_card_pos_2);
        ivCards[2] = root.findViewById(R.id.iv_card_2);
        slotViews[3] = root.findViewById(R.id.slot_card_3);
        tvPositions[3] = root.findViewById(R.id.tv_card_pos_3);
        ivCards[3] = root.findViewById(R.id.iv_card_3);
        slotViews[4] = root.findViewById(R.id.slot_card_4);
        tvPositions[4] = root.findViewById(R.id.tv_card_pos_4);
        ivCards[4] = root.findViewById(R.id.iv_card_4);

        tvTitle.setText(buildTitleText());
        popupWindow = new PopupWindow(root,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(() -> {
            // 对话框隐藏后恢复决斗场阶段按钮
            if (context instanceof YGOProActivity) {
                ((YGOProActivity) context).notifyGameDialogHidden(this);
            }
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "card_select");
        int dialogWidth = resolveDialogWidth();
        applyCardImageSize(dialogWidth);
        draggableHelper.setupDraggablePopup(popupWindow, root,
                dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        // 宽度按 layout_game_right 区域实时解算且据此烘焙每张卡图，通用重排无法同步子视图尺寸：
        // 注册自定义旋转重排，切换方向时重解区域宽、重烘焙卡图、重设弹窗宽并重新居中
        draggableHelper.registerOrientationRelayout(popupWindow, this::applyOrientationRelayout);
        // 收缩模式：点内容左上角「▼」把手缩为与聊天输入框同高的底部横条，确认场地后点横条恢复
        DraggablePopupHelper.enableCollapse(popupWindow, tvTitle, buildTitleText());

        // 直接以内容根构建，包装层由 findTipOverlay 向上解析
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
        btnOk.setOnClickListener(v -> confirm());
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

    /** 当前方向的内容放大系数：横屏 1.0（行为不变），竖屏 {@link #PORTRAIT_SCALE} */
    private float contentScale() {
        return DialogScale.isLandscape(context) ? 1f : PORTRAIT_SCALE;
    }

    /**
     * 按当前方向把标题/卡位文本/确定按钮字号回写为「基准 × 系数」的绝对值（系数见 {@link #contentScale()}）。
     * 首次调用捕获基准（此时视图仍为 XML 原始字号），此后绝对回写，旋转往复不叠乘。build() 与旋转重排均调用。
     */
    private void applyContentScale() {
        if (tvTitle == null || tvPositions[0] == null || btnOk == null) return;
        if (baseTitlePx <= 0) {
            baseTitlePx = tvTitle.getTextSize();
            basePosPx = tvPositions[0].getTextSize();
            baseOkPx = btnOk.getTextSize();
        }
        float s = contentScale();
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTitlePx * s);
        for (TextView tv : tvPositions) {
            if (tv != null) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, basePosPx * s);
        }
        btnOk.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseOkPx * s);
    }

    /**
     * 按对话框宽度反推每张卡宽（扣除根内边距 16dp×2 与每槽水平外边距 4dp×2），
     * 高度按 177:254 卡图比例计算，保证 ImageView 宽高与卡片比例一致；
     * 槽位为 wrap_content，固定宽度后空槽位 GONE 时其余槽位尺寸不变并整体居中
     */
    private void applyCardImageSize(int dialogWidthPx) {
        int containerW = dialogWidthPx - 2 * dp2px(16);
        int cardW = (containerW - SLOT_COUNT * 2 * dp2px(4)) / SLOT_COUNT;
        if (cardW <= 0) return;
        slotStepPx = cardW + 2 * dp2px(4);
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

    /**
     * 标题为单段文本「hint(进度)」：括号内进度随每次选择刷新，不再在标题后单独拼接「已选: x/y」后缀
     *（对齐 gframe 把数量并入标题文本本身的做法：duelclient.cpp L1968 选卡 hint(min-max)、
     * client_field.cpp L1109 合计 hint(cur/target) 均整段 setText）。
     */
    private void refreshTitle() {
        if (tvTitle == null) return;
        String text = buildTitleText();
        tvTitle.setText(text);
        // 收缩横条与弹窗内标题同源，选择进度变化时同步文案
        DraggablePopupHelper.setCollapseBarTitle(popupWindow, text);
    }

    /** 完整标题文本：基础提示语 + 括号内实时进度 */
    private String buildTitleText() {
        return title + "(" + titleProgress() + ")";
    }

    /** 括号内进度：选卡/反选=已选/上限，排序=点击序/总数，合计=当前值/目标值 */
    private String titleProgress() {
        switch (mode) {
            case MODE_SORT:
                return sortCounter + "/" + cards.size();
            case MODE_SUM:
                return sumProgress();
            default:
                return getSelectedCount() + "/" + maxSelect;
        }
    }

    private void onSlotClicked(int slot) {
        int displayIdx = pageOffset + slot;
        int index = displayToProto(displayIdx);
        if (index < 0) return;
        switch (mode) {
            case MODE_UNSELECT: {
                selected[index] = !selected[index];
                updateSlotView(slot, displayIdx);
                if (listener != null) listener.onCardClicked(index);
                dismiss();
                break;
            }
            case MODE_SORT: {
                if (sortList[index] != 0) {
                    int sel = sortList[index];
                    sortList[index] = 0;
                    sortCounter--;
                    for (int i = 0; i < sortList.length; i++) {
                        if (sortList[i] > sel) sortList[i]--;
                    }
                    updateSlotView(slot, displayIdx);
                } else {
                    sortCounter++;
                    sortList[index] = sortCounter;
                    updateSlotView(slot, displayIdx);
                    if (sortCounter == sortList.length) {
                        int[] respBuf = new int[sortList.length];
                        for (int i = 0; i < sortList.length; i++) {
                            respBuf[i] = sortList[i] - 1;
                        }
                        if (listener != null) listener.onSorted(respBuf);
                        dismiss();
                        return;
                    }
                }
                refreshTitle();
                break;
            }
            case MODE_SUM: {
                if (index < mustCards.size()) {
                    Toast.makeText(context, "必选卡不可取消选择", Toast.LENGTH_SHORT).show();
                    return;
                }
                int realIdx = index - mustCards.size();
                if (selected[realIdx]) {
                    selected[realIdx] = false;
                    clickOrder.remove((Integer) realIdx);
                } else {
                    selected[realIdx] = true;
                    clickOrder.add(realIdx);
                }
                updateSlotView(slot, displayIdx);
                if (listener != null) listener.onCardClicked(index);
                updateSumState();
                break;
            }
            default: {
                if (selected[index]) {
                    selected[index] = false;
                    clickOrder.remove((Integer) index);
                } else {
                    selected[index] = true;
                    clickOrder.add(index);
                }
                updateSlotView(slot, displayIdx);
                if (listener != null) listener.onCardClicked(index);
                int sel = getSelectedCount();
                refreshTitle();
                if (sel >= maxSelect) {
                    confirm();
                } else {
                    btnOk.setVisibility(sel >= minSelect ? View.VISIBLE : View.INVISIBLE);
                }
                break;
            }
        }
    }

    private void updateSumState() {
        int optCount = 0;
        for (boolean b : selected) {
            if (b) optCount++;
        }
        boolean countOk = optCount >= minSelect && optCount <= maxSelect;
        boolean sumOk = checkSumValue();
        boolean ready = countOk && sumOk;
        refreshTitle();
        if (ready && optCount >= maxSelect) {
            confirm();
        } else {
            btnOk.setVisibility(ready ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private boolean checkSumValue() {
        List<int[]> values = new ArrayList<>();
        for (CardItem m : mustCards) {
            values.add(new int[]{m.op1, m.op2});
        }
        for (int idx : clickOrder) {
            if (idx >= 0 && idx < selected.length && selected[idx]) {
                CardItem c = cards.get(idx);
                values.add(new int[]{c.op1, c.op2});
            }
        }
        if (values.isEmpty()) return false;
        if (sumMode == 0) {
            return subsetSum(values, 0, sumValue);
        }
        int sum = 0;
        for (int[] v : values) {
            sum += (v[1] > 0 && v[0] > v[1]) ? v[1] : v[0];
        }
        return sum >= sumValue;
    }

    private boolean subsetSum(List<int[]> values, int index, int target) {
        if (target == 0) return true;
        if (index >= values.size() || target < 0) return false;
        int[] v = values.get(index);
        if (subsetSum(values, index + 1, target - v[0])) return true;
        if (v[1] > 0 && subsetSum(values, index + 1, target - v[1])) return true;
        return false;
    }

    /** 合计模式括号内进度：当前值区间/目标值（对齐 client_field.cpp L1109 hint(cur/target)） */
    private String sumProgress() {
        List<CardItem> all = new ArrayList<>(mustCards);
        for (int i = 0; i < cards.size(); i++) {
            if (selected[i]) all.add(cards.get(i));
        }
        int curL = 0, curH = 0;
        for (CardItem c : all) {
            int opmin = (c.op2 > 0 && c.op1 > c.op2) ? c.op2 : c.op1;
            curL += opmin;
            curH += Math.max(c.op1, c.op2);
        }
        String cur = (curL == curH) ? String.valueOf(curL) : curL + "-" + curH;
        String target = (sumMode == 0) ? String.valueOf(sumValue) : sumValue + "+";
        return cur + "/" + target;
    }

    private void refreshSlots() {
        if (tipHelper != null) tipHelper.hide();
        int displayCount = getDisplayCount();
        if (displayCount > SLOT_COUNT) {
            sbPage.setMax(displayCount - SLOT_COUNT);
            sbPage.setVisibility(View.VISIBLE);
        } else {
            sbPage.setMax(0);
            sbPage.setProgress(0);
            sbPage.setVisibility(View.GONE);
        }
        if (pageOffset > sbPage.getMax()) {
            pageOffset = sbPage.getMax();
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            int index = pageOffset + i;
            if (index < displayCount) {
                slotViews[i].setVisibility(View.VISIBLE);
                updateSlotView(i, index);
            } else {
                slotViews[i].setVisibility(View.GONE);
            }
        }
    }

    private void updateSlotView(int slot, int displayIdx) {
        CardItem item = getItemAt(displayIdx);
        if (item == null) return;
        boolean isSelected = isSelectedAt(displayIdx);
        if (mode == MODE_SORT) {
            // client_field.cpp ShowSelectCard MSG_SORT_CARD 分支：标签只显示排序数字或空文本
            // sortList 以协议序索引存储，显示序需经 displayToProto 换算
            int proto = displayToProto(displayIdx);
            tvPositions[slot].setText(proto >= 0 && sortList[proto] != 0
                    ? String.valueOf(sortList[proto]) : "");
            tvPositions[slot].setBackgroundColor(COLOR_DEFAULT);
        } else {
            tvPositions[slot].setText(labelText(item));
            if (isSelected) {
                tvPositions[slot].setBackgroundColor(COLOR_SELECTED);
            } else if (localPlayer >= 0 && item.controler != localPlayer) {
                tvPositions[slot].setBackgroundColor(COLOR_OPPONENT);
            } else {
                tvPositions[slot].setBackgroundColor(COLOR_DEFAULT);
            }
        }
        // code 可能带 0x80000000 翻面标志位，掩码后再使用（对齐 duelclient.cpp code & 0x7fffffff）
        int code = item.code & 0x7fffffff;
        if (code > 0) {
            imageLoader.bindImage(ivCards[slot], code, ImageLoader.Type.small);
        } else {
            // client_field.cpp ShowSelectCard：code==0 显示按控制者区分的卡背
            boolean opponent = localPlayer >= 0 && item.controler != localPlayer;
            Bitmap cover = getCoverBitmap(opponent);
            if (cover != null) {
                ivCards[slot].setImageBitmap(cover);
            } else {
                ivCards[slot].setImageResource(R.drawable.unknown);
            }
        }
    }

    private String labelText(CardItem item) {
        String text = formatLocation(item);
        if (showValues || mode == MODE_SUM) {
            if (item.op2 > 0) {
                text += " 值:" + item.op1 + "/" + item.op2;
            } else {
                text += " 值:" + item.op1;
            }
        }
        return text;
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
            buildDisplayOrder();
            refreshSlots();
            switch (mode) {
                case MODE_SORT:
                    btnOk.setVisibility(View.INVISIBLE);
                    refreshTitle();
                    break;
                case MODE_UNSELECT:
                    btnOk.setVisibility(finishable ? View.VISIBLE : View.INVISIBLE);
                    if (finishable) {
                        btnOk.setText("完成");
                    }
                    refreshTitle();
                    break;
                case MODE_SUM:
                    updateSumState();
                    break;
                default:
                    int sel = getSelectedCount();
                    refreshTitle();
                    btnOk.setVisibility(sel >= minSelect ? View.VISIBLE : View.INVISIBLE);
                    break;
            }
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