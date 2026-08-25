package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
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

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;

public class CardDisplayDialog {

    public static final int SLOT_COUNT = 5;

    // 卡图宽高比 177:254：ImageView 高度 = 宽度 × CARD_ASPECT
    private static final float CARD_ASPECT = 254f / 177f;
    // 对话框宽度取 layout_game_right 实际宽度的四分之三
    private static final float DIALOG_WIDTH_RATIO = 0.75f;

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

    private final Context context;
    private final ImageLoader imageLoader;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;

    private List<CardItem> cards = new ArrayList<>();
    private String title = "卡片确认";
    private int pageOffset = 0;

    private TextView tvTitle;
    private SeekBar sbPage;
    private View btnOk;
    private final View[] slotViews = new View[SLOT_COUNT];
    private final TextView[] tvPositions = new TextView[SLOT_COUNT];
    private final ImageView[] ivCards = new ImageView[SLOT_COUNT];

    private OnDismissListener dismissListener;
    private OnCardClick cardClickListener;

    public CardDisplayDialog(Context context, ImageLoader imageLoader) {
        this.context = context;
        this.imageLoader = imageLoader;
    }

    public CardDisplayDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    public CardDisplayDialog setCards(List<CardItem> cardList) {
        this.cards = cardList != null ? cardList : new ArrayList<>();
        this.pageOffset = 0;
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
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "card_display");
        int dialogWidth = resolveDialogWidth();
        applyCardImageSize(dialogWidth);
        draggableHelper.setupDraggablePopup(popupWindow, root,
                dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);

        for (int i = 0; i < SLOT_COUNT; i++) {
            final int slot = i;
            slotViews[i].setOnClickListener(v -> {
                int index = pageOffset + slot;
                if (index < cards.size() && cardClickListener != null) {
                    cardClickListener.onCardClick(cards.get(index));
                }
            });
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
     * 高度按 177:254 卡图比例计算，保证 ImageView 宽高与卡片比例一致
     */
    private void applyCardImageSize(int dialogWidthPx) {
        int containerW = dialogWidthPx - 2 * dp2px(16);
        int cardW = (containerW - SLOT_COUNT * 2 * dp2px(4)) / SLOT_COUNT;
        if (cardW <= 0) return;
        int cardH = Math.round(cardW * CARD_ASPECT);
        for (ImageView iv : ivCards) {
            ViewGroup.LayoutParams lp = iv.getLayoutParams();
            if (lp != null) {
                lp.height = cardH;
                iv.setLayoutParams(lp);
            }
        }
    }

    private int dp2px(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void refreshSlots() {
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
                if (item.code > 0) {
                    imageLoader.bindImage(ivCards[i], item.code, ImageLoader.Type.small);
                } else {
                    ivCards[i].setImageResource(R.drawable.unknown);
                }
            } else {
                slotViews[i].setVisibility(View.INVISIBLE);
            }
        }
    }

    private String formatLocation(CardItem item) {
        if (item.location == 0x80) {
            return getLocationName(item.location) + "[" + (item.sequence + 1) + "](" + (item.subSeq + 1) + ")";
        }
        return getLocationName(item.location) + "[" + (item.sequence + 1) + "]";
    }

    private String getLocationName(int location) {
        switch (location) {
            case 0x01:
                return "卡组";
            case 0x02:
                return "手牌";
            case 0x04:
                return "怪兽区";
            case 0x08:
                return "魔陷区";
            case 0x10:
                return "墓地";
            case 0x20:
                return "除外";
            case 0x40:
                return "额外";
            case 0x80:
                return "超量素材";
            default:
                return "区域" + location;
        }
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
                if (draggableHelper != null) {
                    draggableHelper.showPopup(popupWindow, anchor);
                } else {
                    popupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
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