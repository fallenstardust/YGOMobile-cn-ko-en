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

public class CardSelectDialog {

    public static final int SLOT_COUNT = 5;
    private static final int COLOR_DEFAULT = 0xFF335577;
    private static final int COLOR_SELECTED = 0xFF00AA44;

    public static class CardItem {
        public final int code;
        public final int controler;
        public final int location;
        public final int sequence;
        public final int subSeq;
        public final int selectSeq;

        public CardItem(int code, int controler, int location, int sequence, int subSeq, int selectSeq) {
            this.code = code;
            this.controler = controler;
            this.location = location;
            this.sequence = sequence;
            this.subSeq = subSeq;
            this.selectSeq = selectSeq;
        }
    }

    public interface OnCardSelectListener {
        void onCardsSelected(List<Integer> selectedIndices);

        void onCancel();
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
    private boolean[] selected;
    private int minSelect = 1;
    private int maxSelect = 1;
    private boolean cancelable = false;
    private int pageOffset = 0;

    private TextView tvTitle;
    private TextView tvCount;
    private SeekBar sbPage;
    private View btnOk;
    private View btnCancel;
    private final View[] slotViews = new View[SLOT_COUNT];
    private final TextView[] tvPositions = new TextView[SLOT_COUNT];
    private final ImageView[] ivCards = new ImageView[SLOT_COUNT];

    private OnCardSelectListener listener;
    private OnDismissListener dismissListener;

    public CardSelectDialog(Context context, ImageLoader imageLoader) {
        this.context = context;
        this.imageLoader = imageLoader;
    }

    public CardSelectDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    private String title = "选择卡片";

    public CardSelectDialog setCards(List<CardItem> cardList) {
        this.cards = cardList != null ? cardList : new ArrayList<>();
        this.selected = new boolean[this.cards.size()];
        this.pageOffset = 0;
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

    public CardSelectDialog setListener(OnCardSelectListener listener) {
        this.listener = listener;
        return this;
    }

    public CardSelectDialog setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    private void build() {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_card_select, null);
        tvTitle = root.findViewById(R.id.tv_card_select_title);
        tvCount = root.findViewById(R.id.tv_card_select_count);
        sbPage = root.findViewById(R.id.sb_card_page);
        btnOk = root.findViewById(R.id.btn_card_select_ok);
        btnCancel = root.findViewById(R.id.btn_card_select_cancel);

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

        tvTitle.setText(title);
        popupWindow = new PopupWindow(root,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(() -> {
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "card_select");
        draggableHelper.setupDraggablePopup(popupWindow, root,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        for (int i = 0; i < SLOT_COUNT; i++) {
            final int slot = i;
            slotViews[i].setOnClickListener(v -> onSlotClicked(slot));
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
        btnOk.setOnClickListener(v -> confirmSelection());
        btnCancel.setOnClickListener(v -> {
            if (listener != null) listener.onCancel();
            dismiss();
        });
    }

    private int getSlotIndex(int slot) {
        return pageOffset + slot;
    }

    private void onSlotClicked(int slot) {
        int index = getSlotIndex(slot);
        if (index >= cards.size()) return;
        selected[index] = !selected[index];
        updateSlotView(slot, index);
        int selCount = getSelectedCount();
        tvCount.setText("已选: " + selCount + "/" + maxSelect);
        if (selCount >= maxSelect) {
            confirmSelection();
        } else if (selCount >= minSelect) {
            btnOk.setVisibility(View.VISIBLE);
            btnCancel.setVisibility(View.GONE);
        } else {
            btnOk.setVisibility(View.GONE);
            btnCancel.setVisibility(cancelable ? View.VISIBLE : View.GONE);
        }
    }

    private int getSelectedCount() {
        int count = 0;
        for (boolean b : selected) {
            if (b) count++;
        }
        return count;
    }

    private void confirmSelection() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) indices.add(i);
        }
        if (listener != null) listener.onCardsSelected(indices);
        dismiss();
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
            int index = getSlotIndex(i);
            if (index < cards.size()) {
                slotViews[i].setVisibility(View.VISIBLE);
                updateSlotView(i, index);
            } else {
                slotViews[i].setVisibility(View.INVISIBLE);
            }
        }
    }

    private void updateSlotView(int slot, int index) {
        CardItem item = cards.get(index);
        tvPositions[slot].setText(formatLocation(item));
        tvPositions[slot].setBackgroundColor(selected[index] ? COLOR_SELECTED : COLOR_DEFAULT);
        if (item.code > 0) {
            imageLoader.bindImage(ivCards[slot], item.code, ImageLoader.Type.small);
        } else {
            ivCards[slot].setImageResource(R.drawable.unknown);
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
            int selCount = getSelectedCount();
            tvCount.setText("已选: " + selCount + "/" + maxSelect);
            btnOk.setVisibility(minSelect <= maxSelect && selCount >= minSelect ? View.VISIBLE : View.GONE);
            btnCancel.setVisibility(cancelable && selCount == 0 ? View.VISIBLE : View.GONE);
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