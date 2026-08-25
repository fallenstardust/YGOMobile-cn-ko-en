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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;

public class CardSelectDialog {

    public static final int SLOT_COUNT = 5;

    public static final int MODE_SELECT = 0;
    public static final int MODE_UNSELECT = 1;
    public static final int MODE_SUM = 2;
    public static final int MODE_SORT = 3;

    private static final int COLOR_DEFAULT = 0xFF335577;
    private static final int COLOR_SELECTED = 0xFF00AA44;
    private static final int COLOR_OPPONENT = 0xFFAA4444;

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

    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;

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

    private final List<Integer> clickOrder = new ArrayList<>();
    private int[] sortList = new int[0];
    private int sortCounter = 0;

    private TextView tvTitle;
    private SeekBar sbPage;
    private Button btnOk;
    private final View[] slotViews = new View[SLOT_COUNT];
    private final TextView[] tvPositions = new TextView[SLOT_COUNT];
    private final ImageView[] ivCards = new ImageView[SLOT_COUNT];

    private OnCardSelectListener listener;
    private OnDismissListener dismissListener;

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
        }
        dismiss();
    }

    private int getDisplayCount() {
        return cards.size() + mustCards.size();
    }

    private CardItem getItemAt(int displayIdx) {
        if (displayIdx < mustCards.size()) return mustCards.get(displayIdx);
        return cards.get(displayIdx - mustCards.size());
    }

    private boolean isSelectedAt(int displayIdx) {
        if (displayIdx < mustCards.size()) return true;
        int idx = displayIdx - mustCards.size();
        return idx < selected.length && selected[idx];
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

        draggableHelper = new DraggablePopupHelper(context, "card_select");
        int dialogWidth = resolveDialogWidth();
        applyCardImageSize(dialogWidth);
        draggableHelper.setupDraggablePopup(popupWindow, root,
                dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);

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
        btnOk.setOnClickListener(v -> confirm());
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

    /**
     * 标题与选择状态共用同一个 title TextView：基础标题 + 已选数量/进度后缀
     */
    private void updateTitle(String statusText) {
        if (tvTitle == null) return;
        if (statusText == null || statusText.isEmpty()) {
            tvTitle.setText(title);
        } else {
            tvTitle.setText(title + "  " + statusText);
        }
    }

    private void onSlotClicked(int slot) {
        int index = pageOffset + slot;
        if (index >= getDisplayCount()) return;
        switch (mode) {
            case MODE_UNSELECT: {
                selected[index] = !selected[index];
                updateSlotView(slot, index);
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
                    updateSlotView(slot, index);
                } else {
                    sortCounter++;
                    sortList[index] = sortCounter;
                    updateSlotView(slot, index);
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
                updateTitle("点击顺序: " + sortCounter + "/" + cards.size());
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
                updateSlotView(slot, index);
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
                updateSlotView(slot, index);
                if (listener != null) listener.onCardClicked(index);
                int sel = getSelectedCount();
                updateTitle("已选: " + sel + "/" + maxSelect);
                if (sel >= maxSelect) {
                    confirm();
                } else {
                    btnOk.setVisibility(sel >= minSelect ? View.VISIBLE : View.GONE);
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
        updateTitle(sumText());
        if (ready && optCount >= maxSelect) {
            confirm();
        } else {
            btnOk.setVisibility(ready ? View.VISIBLE : View.GONE);
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

    private String sumText() {
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
        return "当前值: " + cur + "/" + target + " (已选" + all.size() + "张)";
    }

    private void refreshSlots() {
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
                slotViews[i].setVisibility(View.INVISIBLE);
            }
        }
    }

    private void updateSlotView(int slot, int displayIdx) {
        CardItem item = getItemAt(displayIdx);
        boolean isSelected = isSelectedAt(displayIdx);
        if (mode == MODE_SORT && sortList[displayIdx] != 0) {
            tvPositions[slot].setText(String.valueOf(sortList[displayIdx]));
            tvPositions[slot].setBackgroundColor(COLOR_SELECTED);
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
        if (item.code > 0) {
            imageLoader.bindImage(ivCards[slot], item.code, ImageLoader.Type.small);
        } else {
            ivCards[slot].setImageResource(R.drawable.unknown);
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
            switch (mode) {
                case MODE_SORT:
                    btnOk.setVisibility(View.GONE);
                    updateTitle("点击顺序: 0/" + cards.size());
                    break;
                case MODE_UNSELECT:
                    btnOk.setVisibility(finishable ? View.VISIBLE : View.GONE);
                    if (finishable) {
                        btnOk.setText("完成");
                    }
                    updateTitle("已选: " + getSelectedCount() + "/" + maxSelect);
                    break;
                case MODE_SUM:
                    updateSumState();
                    break;
                default:
                    int sel = getSelectedCount();
                    updateTitle("已选: " + sel + "/" + maxSelect);
                    btnOk.setVisibility(sel >= minSelect ? View.VISIBLE : View.GONE);
                    break;
            }
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