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

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.render.CardDetailPanel;
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

    /** 设置消息文本背景色（如删除确认用 colorNavy 高亮）；0 表示不设背景 */
    public YesOrNoDialog setMessageBackgroundColor(int color) {
        this.messageBgColor = color;
        return this;
    }

    /** 设置消息文本对齐方式（如 Gravity.CENTER）；-1 表示沿用布局默认 */
    public YesOrNoDialog setMessageGravity(int gravity) {
        this.messageGravity = gravity;
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

    /** 弹窗按指定区域的宽高居中显示（如 layout_game_right），而非整个 Activity 窗口 */
    public YesOrNoDialog setCenterInView(View region) {
        this.centerInView = region;
        return this;
    }

    /** 列表选项内容容器（dialog_game_select 的 layout_options），由 setOptionsContentView() 初始化 */
    private LinearLayout optionsContainer;

    /**
     * 采用“列表选择”内容视图（复用 dialog_game_select 布局）：隐藏其标题/提示/底部按钮，
     * 仅保留 layout_options 选项容器；随后通过 {@link #addOption} 逐项添加按钮。
     */
    public YesOrNoDialog setOptionsContentView() {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_game_select, null);
        content.findViewById(R.id.tv_select_title).setVisibility(View.GONE);
        content.findViewById(R.id.tv_select_hint).setVisibility(View.GONE);
        content.findViewById(R.id.layout_select_buttons).setVisibility(View.GONE);
        optionsContainer = content.findViewById(R.id.layout_options);
        return setContentView(content);
    }

    /**
     * 向列表内容视图追加一个选项按钮（统一样式：白色文字、底部间距 4px）。
     * 点击后先执行 onClick 再自动关闭对话框（对齐原 ShowDialogUtil 各列表项行为）。
     */
    public YesOrNoDialog addOption(CharSequence text, int backgroundColor, Runnable onClick) {
        if (optionsContainer == null) setOptionsContentView();
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackgroundColor(backgroundColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 4;
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
            dismiss();
        });
        optionsContainer.addView(btn);
        return this;
    }

    /** 预设为“是/否询问”样式（对齐 gframe wQuery）：TYPE_YES_NO + 按钮“是”/“否” + 不可取消。 */
    public YesOrNoDialog asYesNo() {
        return setType(TYPE_YES_NO)
                .setPositiveButtonText("是")
                .setNegativeButtonText("否")
                .setCancelable(false);
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

    // ══════════════════════════════════════════════════════════════════════
    // 统一对话框工厂：原 ShowDialogUtil 中所有基于 YesOrNoDialog 的弹窗构建迁移至此。
    // 游戏规则回调（连锁进场 enterChainFieldMode / 放弃连锁 finishChainPass /
    // 清理高亮 clearChainSelect / 卡组载入 loadAndSendDeck）仍留在 ShowDialogUtil，
    // 经 activity.getDialogUtil() 调用，避免把协议状态机塞进 UI 组件。
    // ══════════════════════════════════════════════════════════════════════

    /** 当前连锁是/否询问窗（对齐 gframe wQuery）；保留引用以支持「取消操作」重新弹出 */
    private static YesOrNoDialog chainQueryDialog;
    /** 当前连锁是否为强制连锁（chain_forced）：强制时不弹询问窗、不允许取消/放弃 */
    private static boolean chainForcedMode;

    public static YesOrNoDialog getChainQueryDialog() { return chainQueryDialog; }
    public static void setChainQueryDialog(YesOrNoDialog dialog) { chainQueryDialog = dialog; }
    public static boolean isChainForcedMode() { return chainForcedMode; }
    public static void setChainForcedMode(boolean forced) { chainForcedMode = forced; }

    /** 关闭并清空连锁询问窗、复位强制连锁标志（断线/结束/退出场上模式时调用） */
    public static void dismissChainQuery() {
        if (chainQueryDialog != null) {
            chainQueryDialog.dismiss();
            chainQueryDialog = null;
        }
        chainForcedMode = false;
    }

    // ── 私有静态桥接 ──
    private static CardDetailPanel panel(YGOProActivity activity) { return activity.getCardDetailPanel(); }
    private static String sysText(int index, String defText) {
        return DataManager.get().getStringManager().getSystemString(index, defText);
    }
    private static String sysFormat(int index, String defText, Object... args) {
        return DataManager.get().formatSystemString(index, defText, args);
    }
    private static View gameDialogRegion(YGOProActivity activity) {
        return activity.findViewById(R.id.layout_game_right);
    }

    /**
     * 在连锁询问文本前拼接 event_string（对齐 duelclient.cpp MSG_SELECT_CHAIN L2176/2178：
     * stQMessage = event_string + "\n" + 询问语）。event_string 为空时原样返回。
     */
    private static String prependChainEvent(YGOProActivity activity, String text) {
        GameEngine engine = activity.getEngine();
        GameField field = engine != null ? engine.getField() : null;
        String event = field != null ? field.eventString : null;
        return (event == null || event.isEmpty()) ? text : event + "\n" + text;
    }

    /** 是/否确认弹窗（MSG_SELECT_YESNO / MSG_SELECT_EFFECTYN）：是=1 否=0 */
    public static void showYesNoQuery(YGOProActivity activity, String message) {
        showYesNoQuery(activity, message, 0, null);
    }

    /** 是/否确认弹窗（可带卡名着色）：cardCode>0 且 cardName 非空时按卡片类型着色 */
    public static void showYesNoQuery(YGOProActivity activity, String message, int cardCode, String cardName) {
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        if (cardCode > 0 && cardName != null && !cardName.isEmpty()) {
            dialog.setMessageWithCardName(message, cardCode, cardName);
        } else {
            dialog.setMessage(message);
        }
        dialog.asYesNo()
                .setPositiveButton(v -> {
                    activity.sendResponseInt(1);
                    panel(activity).hideCancelOrFinishButton();
                })
                .setNegativeButton(v -> {
                    activity.sendResponseInt(0);
                    panel(activity).hideCancelOrFinishButton();
                })
                .setCenterInView(gameDialogRegion(activity))
                .setOnDismissListener(() -> {
                    panel(activity).hideCancelOrFinishButton();
                    panel(activity).setCurrentDialog(null);
                });
        panel(activity).showCancelOrFinishButton("否");
        dialog.show();
    }

    /**
     * 对齐 duelclient.cpp L49：select_effectyn_id{95,96,97,218,219,220}。
     * 这些 desc 对应的系统字符串各含单个 %ls，用卡名替换。
     */
    private static final Set<Integer> SELECT_EFFECTYN_ID =
            new HashSet<>(Arrays.asList(95, 96, 97, 218, 219, 220));

    /**
     * MSG_SELECT_EFFECTYN：按 desc 组装「是否发动效果」询问文本，再走是/否确认弹窗。
     * 由 ShowDialogUtil.showEffectYnDialog 委托调用（YGOProActivity selectType==12）。
     */
    public static void showEffectYnDialog(YGOProActivity activity, ByteBuffer data) {
        // duelclient.cpp L1868-1895：player(1) code(4) c(1) l(1) s(1) flag(1) desc(4)
        if (data == null || data.remaining() < 13) {
            showYesNoQuery(activity, sysText(94, "是否现在使用这张卡的效果？"));
            return;
        }
        data.get();                        // selecting_player
        int code = data.getInt();
        data.get();                        // c（控制者）
        int location = data.get() & 0xFF;  // l
        int sequence = data.get() & 0xFF;  // s
        data.get();                        // flag
        int desc = data.getInt();
        String cardName = activity.getCardDisplayName(code);
        DataManager dm = DataManager.get();
        String locationName = dm.formatLocation(location, sequence);
        String message;
        if (desc == 0) {
            // sys200「是否在[%ls]发动[%ls]的效果？」→ FormatLocation(l,s) + 卡名
            message = sysFormat(200, "是否在[%s]发动[%s]的效果？", locationName, cardName);
        } else if (desc == 221) {
            // sys221「是否在[%ls]发动[%ls]的诱发类效果？」+ 换行 + sys223
            message = sysFormat(221, "是否在[%s]发动[%ls]的诱发类效果？", locationName, cardName)
                    + "\n" + sysText(223, "稍后将询问其他可以发动的效果。");
        } else if (SELECT_EFFECTYN_ID.contains(desc)) {
            // sys95/96/97/218/219/220：单个 %ls 填卡名
            message = sysFormat(desc, "是否使用[%s]的效果？", cardName);
        } else {
            // 其余 desc 走 GetDesc；C++ 用 L"%ls" 打印，即把结果当数据而非格式串，故这里不做替换
            String raw = dm.getDesc(desc, "");
            message = raw.isEmpty() ? "是否发动「" + cardName + "」的效果？" : raw;
        }
        showYesNoQuery(activity, message, code, cardName);
    }

    /** SIDE 换卡组：列出卡组目录下的 .ydk 供选择，选中后交 ShowDialogUtil 载入并发送 */
    public static void showDeckSelectDialog(YGOProActivity activity) {
        File deckDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_DECK_PATH);
        File[] deckFiles = deckDir.exists()
                ? deckDir.listFiles((dir, name) -> name.endsWith(Constants.YDK_FILE_EX))
                : null;
        List<String> deckNames = new ArrayList<>();
        if (deckFiles != null && deckFiles.length > 0) {
            for (File f : deckFiles) deckNames.add(f.getName().replace(Constants.YDK_FILE_EX, ""));
        } else {
            deckNames.add("（暂无卡组文件）");
        }
        final File[] finalDeckFiles = deckFiles;
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("选择卡组").setOptionsContentView();
        for (int i = 0; i < deckNames.size(); i++) {
            final int pos = i;
            dialog.addOption(deckNames.get(i), 0xFF335577, () -> {
                if (finalDeckFiles != null && pos < finalDeckFiles.length) {
                    activity.getDialogUtil().loadAndSendDeck(finalDeckFiles[pos]);
                }
            });
        }
        dialog.setType(TYPE_MESSAGE)
                .setPositiveButtonText("取消")
                .setPositiveButton(v -> activity.getEngine().disconnect())
                .setCancelable(false);
        dialog.show();
    }

    /**
     * 连锁是/否询问窗（对齐 gframe wQuery + stQMessage）：不再罗列可发动卡片按钮，改由场上高亮卡片承载点击发动。
     * 「是」进入场上点击模式，「否」放弃连锁；chainQueryDialog 保留引用以支持「取消操作」重新弹出。
     */
    public static void showChainQuery(YGOProActivity activity, String queryText) {
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        chainQueryDialog = dialog;
        panel(activity).setCurrentDialog(dialog);
        dialog.setMessage(prependChainEvent(activity, queryText))
                .asYesNo()
                .setPositiveButton(v -> activity.getDialogUtil().enterChainFieldMode(true))
                .setNegativeButton(v -> activity.getDialogUtil().finishChainPass())
                .setCenterInView(gameDialogRegion(activity))
                .setOnDismissListener(() -> panel(activity).setCurrentDialog(null));
        dialog.show();
    }

    /**
     * panelmode（含 LOCATION_OVERLAY 连锁项）兜底：overlay 单元无法在场上单独点击，
     * 仍按列表对话框呈现全部可连锁项，点击项直接应答其索引（对齐 gframe ShowChainCard）。
     */
    public static void showChainListDialog(YGOProActivity activity, boolean contiExist, boolean selectTrigger,
                                           boolean chainForced, List<String> chainOptions, List<Integer> chainFlags) {
        String title;
        if (chainForced) {
            title = sysText(contiExist ? 556 : 550,
                    contiExist ? "请选择要发动/处理的效果" : "请选择要发动的效果");
        } else if (selectTrigger) {
            title = prependChainEvent(activity,
                    sysText(222, "是否要发动诱发类效果？") + "\n" + sysText(223, "稍后将询问其他可以发动的效果。"));
        } else {
            title = prependChainEvent(activity, sysText(203, "是否要进行连锁？"));
        }
        final boolean forced = chainForced;
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        panel(activity).setCurrentDialog(dialog);
        dialog.setTitle(title).setOptionsContentView();
        for (int i = 0; i < chainOptions.size(); i++) {
            final int idx = i;
            int bg = (chainFlags.get(i) & 0x100) != 0 ? 0xFFAA3333 : 0xFF335577;
            dialog.addOption(chainOptions.get(i), bg, () -> {
                activity.getDialogUtil().clearChainSelect();
                panel(activity).setSelectType(-1);
                activity.sendResponseInt(idx);
            });
        }
        dialog.setType(TYPE_MESSAGE)
                .setPositiveButtonText(forced ? "" : sysText(204, "不连锁"))
                .setPositiveButton(v -> {
                    if (forced) return;
                    panel(activity).hideCancelOrFinishButton();
                    activity.getDialogUtil().clearChainSelect();
                    panel(activity).setSelectType(-1);
                    activity.sendResponseInt(-1);
                })
                .setCancelable(false)
                .setCenterInView(gameDialogRegion(activity))
                .setOnDismissListener(() -> {
                    panel(activity).hideCancelOrFinishButton();
                    panel(activity).setCurrentDialog(null);
                });
        if (!forced) panel(activity).showCancelOrFinishButton(sysText(1295, "取消操作"));
        dialog.show();
    }

    /**
     * MSG_SELECT_CHAIN 且 count == 0 时的询问（sys201 + sys202）：
     * 是 → 仅显示「取消操作」按钮；否 → 放弃连锁（应答 -1）。
     */
    public static void showChainEmptyQuery(YGOProActivity activity) {
        chainForcedMode = false;
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        chainQueryDialog = dialog;
        panel(activity).setCurrentDialog(dialog);
        dialog.setMessage(sysText(201, "此时没有可以发动的效果") + "\n" + sysText(202, "是否要确认场上的情况？"))
                .asYesNo()
                .setPositiveButton(v -> {
                    CardDetailPanel pp = panel(activity);
                    if (pp != null) {
                        pp.setSelectType(16);
                        pp.showCancelOrFinishButton(sysText(1295, "取消操作"));
                    }
                })
                .setNegativeButton(v -> activity.getDialogUtil().finishChainPass())
                .setCenterInView(gameDialogRegion(activity))
                .setOnDismissListener(() -> panel(activity).setCurrentDialog(null));
        dialog.show();
    }

    /** MSG_SELECT_COUNTER：列出 1..counterCount 供选择取除总数（playerop.cpp L608-637） */
    public static void showCounterSelectDialog(YGOProActivity activity, ByteBuffer data) {
        if (data == null || data.remaining() < 6) {
            activity.sendResponseInt(0);
            return;
        }
        data.get();                                    // selecting_player
        int counterType = data.getShort() & 0xFFFF;
        int counterCount = data.getShort() & 0xFFFF;
        int cardCount = data.get() & 0xFF;
        final List<Integer> cardCounters = new ArrayList<>();
        for (int i = 0; i < cardCount && data.remaining() >= 9; i++) {
            data.getInt();
            data.get();
            data.get();
            data.get();
            cardCounters.add(data.getShort() & 0xFFFF);
        }
        if (cardCounters.isEmpty() || counterCount <= 0) {
            activity.sendResponseInt(0);
            return;
        }
        DataManager dm = DataManager.get();
        String title = dm.formatSystemString(204, "请取除%d个[%s]", counterCount, dm.getCounterName(counterType));
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(title).setOptionsContentView();
        for (int i = 1; i <= counterCount; i++) {
            final int val = i;
            dialog.addOption(String.valueOf(i), 0xFF335577, () -> sendCounterResponse(activity, cardCounters, val));
        }
        dialog.setCancelable(false).setCenterInView(gameDialogRegion(activity));
        dialog.show();
    }

    /** playerop.cpp L624-637：按列表顺序贪心分摊玩家选定的取除总数 */
    private static void sendCounterResponse(YGOProActivity activity, List<Integer> cardCounters, int total) {
        ByteBuffer buf = ByteBuffer.allocate(2 * cardCounters.size());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int remain = total;
        for (int i = 0; i < cardCounters.size(); i++) {
            int take = Math.min(remain, cardCounters.get(i));
            buf.putShort((short) take);
            remain -= take;
        }
        activity.getEngine().sendResponse(buf.array());
    }
}