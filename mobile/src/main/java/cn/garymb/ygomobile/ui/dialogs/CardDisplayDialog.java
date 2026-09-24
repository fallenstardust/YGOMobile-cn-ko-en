package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardStatusTipHelper;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import ocgcore.DataManager;
import ocgcore.StringManager;

public class CardDisplayDialog {

    public static final int SLOT_COUNT = 5;

    // 卡图宽高比 177:254：ImageView 高度 = 宽度 × CARD_ASPECT
    private static final float CARD_ASPECT = 254f / 177f;
    // 对话框宽度取 layout_game_right 实际宽度的四分之三
    private static final float DIALOG_WIDTH_RATIO = 0.75f;
    // 位置标签配色，映射 client_field.h CARD_LIST_* 常量
    private static final int COLOR_DEFAULT = 0xFF2196F3;   // CARD_LIST_DEFAULT_BACKGROUND_COLOR
    private static final int COLOR_OPPONENT = 0xFF5A5A5A;  // CARD_LIST_OPPONENT_BACKGROUND_COLOR

    public static class CardItem {
        public final int code;
        public final int controler;
        public final int location;
        public final int sequence;
        public final int subSeq;

        public CardItem(int code, int controler, int location, int sequence, int subSeq) {
            this.code = code;
            this.controler = controler;
            this.location = location;
            this.sequence = sequence;
            this.subSeq = subSeq;
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
    private String title = "卡片确认";
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

    private OnDismissListener dismissListener;
    private OnCardClick cardClickListener;
    /** 实时观察提供器（非空则弹窗在 show/dismiss 时维护 LIVE_DIALOGS 名单，且 field 变化会重拉列表） */
    private CardItemSupplier liveSupplier;

    // 卡背缓存：对齐 image_manager.cpp tButtonFacedown[0/1]（我方 cover.jpg / 对方 cover2.jpg）
    private static Bitmap coverSelf;
    private static Bitmap coverOpponent;

    public CardDisplayDialog(Context context, ImageLoader imageLoader) {
        this.context = context;
        this.imageLoader = imageLoader;
    }

    public CardDisplayDialog setTitle(String title) {
        this.title = title;
        return this;
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
     *  仅 CmdMenuDialog 堆叠区查看列表入口使用（观战/录像），普通确认弹窗不安装。 */
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

        tvTitle.setText(title);
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
            LIVE_DIALOGS.remove(this);
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "card_display");
        int dialogWidth = resolveDialogWidth();
        applyCardImageSize(dialogWidth);
        draggableHelper.setupDraggablePopup(popupWindow, root,
                dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);

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
                    pageOffset = progress;
                    refreshSlots();
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
        return Math.round(w * DIALOG_WIDTH_RATIO);
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

    private int dp2px(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
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
                    boolean opponent = localPlayer >= 0 && item.controler != localPlayer;
                    Bitmap cover = getCoverBitmap(opponent);
                    if (cover != null) {
                        ivCards[i].setImageBitmap(cover);
                    } else {
                        ivCards[i].setImageResource(R.drawable.unknown);
                    }
                }
            } else {
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