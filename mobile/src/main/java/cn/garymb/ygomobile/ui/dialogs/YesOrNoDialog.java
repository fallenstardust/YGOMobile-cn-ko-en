package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

public class YesOrNoDialog {

    public static final int TYPE_MESSAGE = 0;
    public static final int TYPE_YES_NO = 1;

    // 卡片名字着色（对齐 YGO 卡框配色：怪兽=黄、魔法=淡绿、陷阱=淡粉）
    private static final int CARD_NAME_COLOR_MONSTER = 0xFFFFD700; // 怪兽卡：黄色
    private static final int CARD_NAME_COLOR_SPELL = 0xFF90EE90;   // 魔法卡：淡绿色
    private static final int CARD_NAME_COLOR_TRAP = 0xFFFFB6C1;    // 陷阱卡：淡粉色

    private final Context context;
    private PopupWindow popupWindow;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String title = "";
    private CharSequence message = "";
    private int type = TYPE_MESSAGE;

    private String positiveText = "确定";
    private String negativeText = "取消";

    private View.OnClickListener positiveListener;
    private View.OnClickListener negativeListener;
    private OnDismissListener dismissListener;

    private boolean cancelable = true;
    private View contentView;
    private View customContentView;
    private int softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
    private DraggablePopupHelper draggableHelper;
    private int messageBgColor = 0;
    private int messageGravity = -1;
    // 指定弹窗居中区域（如 layout_game_right）；null = 按整个窗口居中（原行为）
    private View centerInView;

    public interface OnDismissListener {
        void onDismiss();
    }

    public YesOrNoDialog(Context context) {
        this.context = context;
    }

    public YesOrNoDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    public YesOrNoDialog setMessage(CharSequence message) {
        this.message = message;
        return this;
    }

    /**
     * 设置消息文本，并将其中的卡名按卡片类型着色（怪兽=黄、魔法=淡绿、陷阱=淡粉）。
     * message 为已把 [%ls] 替换成卡名后的完整文本；cardName 为其中要着色的卡名子串；
     * code 用于查询卡片类型。非卡片或无法识别类型时按普通文本显示。
     */
    public YesOrNoDialog setMessageWithCardName(String message, int code, String cardName) {
        this.message = colorizeCardName(message, code, cardName);
        return this;
    }

    public YesOrNoDialog setType(int type) {
        this.type = type;
        return this;
    }

    public YesOrNoDialog setPositiveButtonText(String text) {
        this.positiveText = text;
        return this;
    }

    public YesOrNoDialog setNegativeButtonText(String text) {
        this.negativeText = text;
        return this;
    }

    public YesOrNoDialog setPositiveButton(View.OnClickListener listener) {
        this.positiveListener = listener;
        return this;
    }

    public YesOrNoDialog setNegativeButton(View.OnClickListener listener) {
        this.negativeListener = listener;
        return this;
    }

    public YesOrNoDialog setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    public YesOrNoDialog setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    public YesOrNoDialog setContentView(View view) {
        this.customContentView = view;
        return this;
    }

    public YesOrNoDialog setContentView(int layoutId) {
        this.customContentView = LayoutInflater.from(context).inflate(layoutId, null);
        return this;
    }

    public YesOrNoDialog setSoftInputMode(int mode) {
        this.softInputMode = mode;
        return this;
    }

    public YesOrNoDialog setMessageBackgroundColor(int color) {
        this.messageBgColor = color;
        return this;
    }

    public YesOrNoDialog setMessageGravity(int gravity) {
        this.messageGravity = gravity;
        return this;
    }

    /** 弹窗按指定区域的宽高居中显示（如 layout_game_right），而非整个 Activity 窗口 */
    public YesOrNoDialog setCenterInView(View region) {
        this.centerInView = region;
        return this;
    }

    /** 将 message 中出现的卡名按卡片类型着色，返回可显示的 CharSequence */
    private CharSequence colorizeCardName(String message, int code, String cardName) {
        if (message == null || message.isEmpty() || cardName == null || cardName.isEmpty()) {
            return message;
        }
        int color = cardNameColor(code);
        if (color == 0) return message;
        SpannableString span = new SpannableString(message);
        int from = 0;
        int idx;
        while ((idx = message.indexOf(cardName, from)) >= 0) {
            span.setSpan(new ForegroundColorSpan(color), idx, idx + cardName.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = idx + cardName.length();
        }
        return span;
    }

    /**
     * 依卡片类型返回卡名颜色：魔法=淡绿、陷阱=淡粉、怪兽=黄；无法识别返回 0（不着色）。
     * 陷阱怪兽同时带 Trap|Monster 位，优先按陷阱着色，对齐卡框配色。
     */
    private int cardNameColor(int code) {
        if (code <= 0) return 0;
        Card card = DataManager.get().getCardManager().getCard(code);
        if (card == null) return 0;
        if (card.isType(CardType.Spell)) return CARD_NAME_COLOR_SPELL;
        if (card.isType(CardType.Trap)) return CARD_NAME_COLOR_TRAP;
        if (card.isType(CardType.Monster)) return CARD_NAME_COLOR_MONSTER;
        return 0;
    }

    private void build() {
        float density = context.getResources().getDisplayMetrics().density;
        int dialogWidth = (int) (280 * density);

        LinearLayout root = (LinearLayout) LayoutInflater.from(context)
                .inflate(R.layout.dialog_yes_or_no, null);

        TextView tvTitle = root.findViewById(R.id.tv_yes_no_title);
        ScrollView scrollView = root.findViewById(R.id.yes_no_scroll);
        TextView tvMessage = root.findViewById(R.id.tv_yes_no_message);
        FrameLayout customContainer = root.findViewById(R.id.yes_no_custom_content);
        LinearLayout buttonArea = root.findViewById(R.id.yes_no_button_area);
        Button btnPositive = root.findViewById(R.id.btn_yes_no_positive);
        Button btnNegative = root.findViewById(R.id.btn_yes_no_negative);

        if (title != null && !title.isEmpty()) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(title);
        } else {
            tvTitle.setVisibility(View.GONE);
        }

        // ── Content area ─────────────────────────────────────────
        if (customContentView != null) {
            scrollView.setVisibility(View.GONE);
            customContainer.setVisibility(View.VISIBLE);
            customContainer.addView(customContentView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            tvMessage.setText(message);
            if (messageBgColor != 0) {
                tvMessage.setBackgroundColor(messageBgColor);
            }
            if (messageGravity != -1) {
                tvMessage.setGravity(messageGravity);
            }
        }

        // ── Button area ──────────────────────────────────────────
        boolean needsButtons = customContentView == null || positiveListener != null
                || negativeListener != null || type == TYPE_YES_NO;
        if (!needsButtons) {
            buttonArea.setVisibility(View.GONE);
        } else {
            btnPositive.setText(positiveText);
            btnNegative.setText(negativeText);
            if (type == TYPE_YES_NO) {
                btnNegative.setVisibility(View.VISIBLE);
            } else {
                btnNegative.setVisibility(View.GONE);
                LinearLayout.LayoutParams positiveLp =
                        (LinearLayout.LayoutParams) btnPositive.getLayoutParams();
                positiveLp.setMarginEnd(0);
            }
            btnPositive.setOnClickListener(v -> {
                if (positiveListener != null) positiveListener.onClick(v);
                dismiss();
            });
            btnNegative.setOnClickListener(v -> {
                if (negativeListener != null) negativeListener.onClick(v);
                dismiss();
            });
        }

        contentView = root;

        // ── PopupWindow setup ────────────────────────────────────
        popupWindow = new PopupWindow(contentView, dialogWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(cancelable);
        popupWindow.setFocusable(true);
        popupWindow.setSoftInputMode(softInputMode);
        popupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) return true;
            return false;
        });
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(() -> {
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "game_dialog_" + title);
        draggableHelper.setupDraggablePopup(popupWindow, root, dialogWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT);
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
            try {
                DraggablePopupHelper.centerPopupInRegion(popupWindow, centerInView);
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

    public View getContentView() {
        return customContentView != null ? customContentView : contentView;
    }
}