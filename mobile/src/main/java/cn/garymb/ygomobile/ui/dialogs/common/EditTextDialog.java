package cn.garymb.ygomobile.ui.dialogs.common;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.LinkedHashSet;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import ocgcore.DataManager;
import ocgcore.StringManager;

/**
 * 通用文本输入弹窗：标题 + 单行输入框 + 「确定/取消」，决斗内外所有需要玩家输入一段文本的
 * 场景统一走它（保存录像的文件名、重命名录像、新建/重命名卡组与分类）。由原录像保存专用的
 * {@code ReplaySaveDialog} 抽象而来：那个类把「录像文件：」标题与「保存/取消」按钮写死，
 * 其余输入场景只能各写一份，而借用 YesOrNoDialog 塞 EditText 又拿不到输入框（该类从未把
 * {@code setContentView} 传入的自定义视图挂进布局，弹窗只剩标题与按钮，形同虚影）。
 * <p>
 * 交互与 gframe 的输入框语义一致：预填内容默认整段选中（直接打字即覆盖），输入法完成键
 * 等同点「确定」；校验不通过（{@link OnInputActionListener#onConfirm} 返回 false，如空名、重名）
 * 时弹窗保持打开并重新聚焦输入框，玩家改字重输，不必重来一遍。
 */
public class EditTextDialog {

    /** 弹窗宽（dp）：沿用原录像保存弹窗的 310dp */
    private static final float DIALOG_WIDTH_DP = 310f;

    public interface OnInputActionListener {
        /**
         * 点「确定」或输入法完成键，text 为已 trim 的输入内容。
         *
         * @return true 关闭弹窗；false 表示输入不合规，弹窗保持显示并重新聚焦输入框
         */
        boolean onConfirm(String text);

        /** 点「取消」，或返回键/边缘手势导致的误关（按取消定性，保证调用方一定收到一次回执） */
        void onCancel();
    }

    /** 在显实例登记：决斗结束/断线/退出界面收口时据此统一关闭，不残留遮挡返回的界面 */
    private static final LinkedHashSet<EditTextDialog> sShowing = new LinkedHashSet<>();

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;
    private EditText etInput;
    private TextView tvTitle;
    private OnInputActionListener actionListener;

    private String title = "";
    private String initialText = "";
    private String hint = "";
    private int inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    /** null 取系统串 1302「确定」 */
    private String positiveText;
    /** null 取系统串 1212「取消」，空串则隐藏负按钮（单键弹窗） */
    private String negativeText;
    private boolean selectAllOnShow = true;
    private boolean showImeOnShow = true;
    // 指定弹窗居中区域（如 layout_game_right）；null = 决斗界面下回退到该区域，其余按整窗居中
    private View centerInView;
    /** 本次关闭是否已由「确定/取消」定性或被程序化收口：未定性时误关需补发 onCancel */
    private boolean resolved;

    public EditTextDialog(Context context) {
        this.context = context;
    }

    public EditTextDialog setTitle(CharSequence text) {
        this.title = text == null ? "" : text.toString();
        return this;
    }

    /** 输入框预填内容（重命名/保存录像的默认名） */
    public EditTextDialog setText(String text) {
        this.initialText = text == null ? "" : text;
        return this;
    }

    public EditTextDialog setHint(String hint) {
        this.hint = hint == null ? "" : hint;
        return this;
    }

    /** 输入类型，默认单行文本无联想（数字输入传 {@code InputType.TYPE_CLASS_NUMBER}） */
    public EditTextDialog setInputType(int inputType) {
        this.inputType = inputType;
        return this;
    }

    public EditTextDialog setPositiveButtonText(String text) {
        this.positiveText = text;
        return this;
    }

    /** 负按钮文字；传空串隐藏负按钮 */
    public EditTextDialog setNegativeButtonText(String text) {
        this.negativeText = text;
        return this;
    }

    /** 预填内容是否整段选中（默认 true，直接打字即覆盖旧名） */
    public EditTextDialog setSelectAllOnShow(boolean selectAll) {
        this.selectAllOnShow = selectAll;
        return this;
    }

    /** 显示后是否自动唤起输入法（默认 true） */
    public EditTextDialog setShowImeOnShow(boolean showIme) {
        this.showImeOnShow = showIme;
        return this;
    }

    /** 弹窗按指定区域的宽高居中显示（如 layout_game_right），而非整个 Activity 窗口 */
    public EditTextDialog setCenterInView(View region) {
        this.centerInView = region;
        return this;
    }

    public EditTextDialog setOnInputActionListener(OnInputActionListener listener) {
        this.actionListener = listener;
        return this;
    }

    private void build() {
        float density = context.getResources().getDisplayMetrics().density * DialogScale.factor(context);
        int dialogWidth = (int) (DIALOG_WIDTH_DP * density);

        LinearLayout root = (LinearLayout) LayoutInflater.from(DialogScale.wrap(context))
                .inflate(R.layout.dialog_edit_text, null);
        tvTitle = root.findViewById(R.id.tv_edit_text_title);
        etInput = root.findViewById(R.id.et_edit_text_input);
        Button btnPositive = root.findViewById(R.id.btn_edit_text_positive);
        Button btnNegative = root.findViewById(R.id.btn_edit_text_negative);

        if (title.isEmpty()) {
            tvTitle.setVisibility(View.GONE);
        } else {
            tvTitle.setText(title);
        }
        etInput.setInputType(inputType);
        etInput.setHint(hint);
        etInput.setText(initialText);

        StringManager sm = DataManager.get().getStringManager();
        btnPositive.setText(positiveText != null
                ? positiveText : sm.getSystemString(1302, "确定"));
        String neg = negativeText != null ? negativeText : sm.getSystemString(1212, "取消");
        if (neg.isEmpty()) {
            // 单键弹窗：20dp 间隙属两键之间的空档，只剩一键时归零，否则按钮偏心
            btnNegative.setVisibility(View.GONE);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) btnPositive.getLayoutParams();
            lp.setMarginEnd(0);
            btnPositive.setLayoutParams(lp);
        } else {
            btnNegative.setText(neg);
        }

        btnPositive.setOnClickListener(v -> confirm());
        btnNegative.setOnClickListener(v -> cancel());
        // 输入法完成键与实体回车等同点「确定」（对齐 gframe 输入框 Enter 提交）
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean done = actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN);
            if (!done) return false;
            confirm();
            return true;
        });

        popupWindow = new PopupWindow(root, dialogWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        // 含 EditText 的弹窗必须 focusable，否则窗口不接收按键：点输入框唤不起输入法、无法改字
        // （同带输入框的 AnnounceCardDialog/SingleModeDialog 同一结论）
        popupWindow.setFocusable(true);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // 窗外触摸一律拦下不吃进内容，配合 outsideTouchable=false 使弹窗不会被点窗外误关
        // （口径同其他决斗弹窗）；返回键不在此列——系统 PopupDecorView 对 BACK 无条件 dismiss，
        // 内容层拦不到，故由下方 OnDismissListener 按取消定性补发回执
        popupWindow.setTouchInterceptor((v, event) -> event.getAction() == MotionEvent.ACTION_OUTSIDE);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(() -> {
            sShowing.remove(this);
            // 返回键/边缘手势误关（系统 PopupDecorView 对 BACK 无条件 dismiss，内容层拦不到）：
            // 按「取消」定性补发一次回执，否则调用方（如逐个处理待保存录像的队列）等不到结果而卡死
            if (!resolved) {
                resolved = true;
                if (actionListener != null) actionListener.onCancel();
            }
        });

        // 拖动位置按弹窗种类（标题）分别记忆，口径同 YesOrNoDialog 的 "game_dialog_" + title
        draggableHelper = new DraggablePopupHelper(context, "game_dialog_" + title);
        draggableHelper.setupDraggablePopup(popupWindow, root, dialogWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        // 收缩把手：本弹窗含输入框，登记进 ImePopupAdjuster 后一律不会被输入法联动自动最小化，
        // 只保留玩家手动收缩查看场地的入口（收缩成横条期间看不见输入框，展开即可继续输入）
        DraggablePopupHelper.enableCollapse(popupWindow, tvTitle, title);
    }

    public void show() {
        build();
        if (popupWindow == null) return;

        Runnable showAction = () -> {
            if (popupWindow == null || popupWindow.isShowing()) return;
            View anchor = activityAnchor();
            if (anchor == null) return;
            try {
                // 未显式指定居中区域时回退 layout_game_right，使竖屏下弹窗居中在决斗场区域而非整屏
                View region = centerInView;
                if (region == null && context instanceof YGOProActivity) {
                    region = ((YGOProActivity) context).findViewById(R.id.layout_game_right);
                }
                DraggablePopupHelper.centerPopupInRegion(popupWindow, region);
                if (draggableHelper != null) {
                    draggableHelper.showPopup(popupWindow, anchor);
                } else {
                    popupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
                }
                // 输入法抬升联动：抬键盘时本弹窗底边贴聊天输入框上沿，收起后回居中区域
                ImePopupAdjuster.attach(popupWindow);
                if (popupWindow.isShowing()) sShowing.add(this);
                playQuestionSound();
                if (showImeOnShow) {
                    prepareCaret();
                    showKeyboard();
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

    /** 光标与选区：预填内容整段选中（打字即覆盖），否则光标落到末尾 */
    private void prepareCaret() {
        if (etInput == null) return;
        etInput.requestFocus();
        int len = etInput.getText() == null ? 0 : etInput.getText().length();
        if (selectAllOnShow && len > 0) {
            etInput.setSelection(0, len);
        } else {
            etInput.setSelection(len);
        }
    }

    /** 显式唤起输入法：隐式请求被拒（触摸经弹窗包装层转发时系统不认作显式请求）再强制重试一次 */
    private void showKeyboard() {
        if (etInput == null) return;
        etInput.post(() -> {
            InputMethodManager imm = inputMethodManager();
            if (imm == null) return;
            try {
                etInput.requestFocus();
                if (!imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)) {
                    imm.showSoftInput(etInput, InputMethodManager.SHOW_FORCED);
                }
            } catch (Exception ignored) {
                // 窗口尚未附着 window token 时唤起失败，交由玩家点击输入框重试
            }
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = inputMethodManager();
        if (imm == null || etInput == null || etInput.getWindowToken() == null) return;
        try {
            imm.hideSoftInputFromWindow(etInput.getWindowToken(), 0);
        } catch (Exception ignored) {
            // 窗口已在销毁流程中
        }
    }

    private InputMethodManager inputMethodManager() {
        Object svc = context.getSystemService(Context.INPUT_METHOD_SERVICE);
        return svc instanceof InputMethodManager ? (InputMethodManager) svc : null;
    }

    private void confirm() {
        String text = etInput != null && etInput.getText() != null
                ? etInput.getText().toString().trim() : "";
        // 监听返回 false = 输入不合规：不关窗、不收键盘，玩家就地改字重输
        if (actionListener != null && !actionListener.onConfirm(text)) {
            prepareCaret();
            return;
        }
        resolved = true;
        hideKeyboard();
        dismiss();
    }

    private void cancel() {
        resolved = true;
        hideKeyboard();
        dismiss();
        if (actionListener != null) actionListener.onCancel();
    }

    /** 程序化关闭（收口/退出界面）：不触发任何回调，避免被当成玩家取消而走后续分支 */
    public void dismiss() {
        resolved = true;
        sShowing.remove(this);
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

    /** 当前输入内容（已 trim），供调用方在回调之外随时取读 */
    public String getInputText() {
        return etInput != null && etInput.getText() != null
                ? etInput.getText().toString().trim() : "";
    }

    /**
     * 收口全部在显输入弹窗（决斗结束/断线/退出界面/撤回与 match 局间换 side 调用，
     * 口径同 {@code YesOrNoDialog.dismissShowingDialogs}）：一律程序化关闭，不回调监听。
     *
     * @return 本次是否真的关掉了在显输入弹窗
     */
    public static boolean dismissAllOpen() {
        boolean closed = false;
        for (EditTextDialog dialog : new LinkedHashSet<>(sShowing)) {
            closed |= dialog.isShowing();
            dialog.dismiss();
        }
        sShowing.clear();
        return closed;
    }

    /** 是否有输入弹窗正在显示 */
    public static boolean isShowingAny() {
        return !sShowing.isEmpty();
    }

    /** 锚点：Activity 的 DecorView；界面正在销毁时返回 null（不再弹窗） */
    private View activityAnchor() {
        if (!(context instanceof android.app.Activity)) return null;
        android.app.Activity act = (android.app.Activity) context;
        if (act.isFinishing() || act.isDestroyed()) return null;
        View anchor = act.getWindow().getDecorView();
        return anchor != null && anchor.getWindowToken() != null ? anchor : null;
    }

    /** 弹输入窗响 question 提示音（口径同 YesOrNoDialog 的询问类，宿主非决斗界面则不响） */
    private void playQuestionSound() {
        if (!(context instanceof YGOProActivity)) return;
        SoundManager sm = ((YGOProActivity) context).getSoundManager();
        if (sm != null) sm.playSoundEffect(SoundManager.SFX.QUESTION);
    }

}
