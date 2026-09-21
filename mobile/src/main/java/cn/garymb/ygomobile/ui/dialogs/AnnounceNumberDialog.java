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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import ocgcore.DataManager;
import ocgcore.StringManager;

/**
 * 宣言数字对话框（移植 gframe wANNumber，game.cpp L899-915）：
 * 数值列表同时提供下拉框（cbANNumber）与 12 个快捷按钮（btnANNumber，game.cpp L908-913）；
 * 快捷模式（count<=12 且全部值 ∈[1,12]）隐藏下拉框、点击按钮选中，否则隐藏按钮组（duelclient.cpp L4051-4093）；
 * 不在可选值列表中的数字按钮置为不可用且文字变灰。
 * 响应为选中项索引（event_handler.cpp BUTTON_ANNUMBER_OK L480-484：SetResponseI(cbANNumber->getSelected())）。
 */
public class AnnounceNumberDialog {

    private static final int DIALOG_WIDTH_DP = 200;
    /** 快捷按钮数量，对齐 btnANNumber[12]（game.cpp L908） */
    private static final int GRID_COUNT = 12;

    /** XML 中固定的 12 个快捷数字按钮，索引 i 对应数字 i+1 */
    private static final int[] GRID_BUTTON_IDS = {
            R.id.btn_annumber_1, R.id.btn_annumber_2, R.id.btn_annumber_3,
            R.id.btn_annumber_4, R.id.btn_annumber_5, R.id.btn_annumber_6,
            R.id.btn_annumber_7, R.id.btn_annumber_8, R.id.btn_annumber_9,
            R.id.btn_annumber_10, R.id.btn_annumber_11, R.id.btn_annumber_12
    };

    public interface OnNumberSelectedListener {
        void onNumberSelected(int selectedIndex);
    }

    public interface OnDismissListener {
        void onDismiss();
    }

    /** 当前正在显示的宣言数字弹窗（去重用），由静态工厂 showAnnounceNumberDialog 维护 */
    private static AnnounceNumberDialog current;

    /**
     * MSG_ANNOUNCE_NUMBER 静态工厂（供 ShowDialogUtil.showAnnounceNumberDialog 委托调用）：
     * duelclient.cpp L4051-4093 count(1)+count×value(4)。响应为选中项索引，缺省标题 565。
     */
    public static void showAnnounceNumberDialog(YGOProActivity activity, ByteBuffer data) {
        if (data == null || data.remaining() < 1) {
            activity.sendResponseInt(0);
            return;
        }
        int count = data.get() & 0xFF;
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            values.add(data.getInt());
        }
        if (values.isEmpty()) {
            activity.sendResponseInt(0);
            return;
        }
        if (current != null) current.dismiss();
        AnnounceNumberDialog dialog = new AnnounceNumberDialog(activity);
        current = dialog;
        dialog.setTitle(selectTitleText(activity, 565, "选择数字"))
                .setValues(values)
                .setOnNumberSelectedListener(activity::sendResponseInt)
                .setOnDismissListener(() -> current = null);
        dialog.show();
    }

    /** 退出对战时关闭可能残留的宣言数字弹窗（由 ShowDialogUtil.dismissAnnounceDialogs 调用） */
    public static void dismissCurrent() {
        if (current != null) {
            current.dismiss();
            current = null;
        }
    }

    /**
     * 选择类标题：优先用 selectHint，消费后清零（链式访问，不导入 game 类）。
     */
    private static String selectTitleText(YGOProActivity activity, int defIndex, String defText) {
        int hint = 0;
        if (activity.getEngine() != null && activity.getEngine().getField() != null) {
            hint = activity.getEngine().getField().selectHint;
            activity.getEngine().getField().selectHint = 0;
        }
        return DataManager.get().getStringManager()
                .getSystemString(hint > 0 ? hint : defIndex, defText);
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 公共字符串管理器：初始化后可供整个类调用（对齐 CardDetailPanel.mStringManager 惯例） */
    public final StringManager mStringManager = DataManager.get().getStringManager();

    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;

    private String title = "选择数字";
    private List<Integer> values = new ArrayList<>();
    private boolean quickMode = false;
    private int selectedIndex = -1;

    private TextView tvTitle;
    private Spinner spNumber;
    private GridLayout layoutButtons;
    private Button btnOk;
    private final Button[] gridButtons = new Button[GRID_COUNT];


    private OnNumberSelectedListener listener;
    private OnDismissListener dismissListener;

    public AnnounceNumberDialog(Context context) {
        this.context = context;
    }

    public AnnounceNumberDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    public AnnounceNumberDialog setValues(List<Integer> valueList) {
        this.values = valueList != null ? valueList : new ArrayList<>();
        // duelclient.cpp L4056-4075：count<=12 且所有值 ∈[1,12] 才启用快捷按钮模式
        quickMode = !values.isEmpty() && values.size() <= GRID_COUNT;
        for (int v : values) {
            if (v <= 0 || v > GRID_COUNT) {
                quickMode = false;
                break;
            }
        }
        return this;
    }

    public AnnounceNumberDialog setOnNumberSelectedListener(OnNumberSelectedListener l) {
        this.listener = l;
        return this;
    }

    public AnnounceNumberDialog setOnDismissListener(OnDismissListener l) {
        this.dismissListener = l;
        return this;
    }

    private void build() {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_announce_number, null);
        tvTitle = root.findViewById(R.id.tv_announce_number_title);
        spNumber = root.findViewById(R.id.sp_announce_number);
        layoutButtons = root.findViewById(R.id.layout_annumber_buttons);
        btnOk = root.findViewById(R.id.btn_annumber_ok);

        tvTitle.setText(title);
        btnOk.setText(mStringManager.getSystemString(1211, "确定"));

        List<String> texts = new ArrayList<>();
        for (int v : values) {
            texts.add(String.valueOf(v));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
                android.R.layout.simple_spinner_item, texts) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.WHITE);
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(Color.BLACK);
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spNumber.setAdapter(adapter);
        spNumber.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedIndex = position;
                updateGridHighlight();
                if (!quickMode) btnOk.setEnabled(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedIndex = -1;
                btnOk.setEnabled(false);
            }
        });

        for (int i = 0; i < GRID_COUNT; i++) {
            final int idx = i;
            Button btn = root.findViewById(GRID_BUTTON_IDS[i]);
            btn.setText(String.valueOf(idx + 1));
            btn.setOnClickListener(v -> onGridButtonClicked(idx));
            // 不在可选值列表中的数字：按钮置为不可用，文字变灰（对齐禁用按钮文字颜色规范）
            if (isNumberAllowed(idx + 1)) {
                btn.setEnabled(true);
                btn.setTextColor(Color.WHITE);
            } else {
                btn.setEnabled(false);
                btn.setTextColor(Color.GRAY);
            }
            gridButtons[idx] = btn;
        }

        spNumber.setSelection(0);
        if (quickMode) {
            spNumber.setVisibility(View.GONE);
            selectedIndex = -1;
            btnOk.setEnabled(false);
        } else {
            layoutButtons.setVisibility(View.GONE);
            selectedIndex = 0;
            btnOk.setEnabled(true);
        }

        btnOk.setOnClickListener(v -> {
            if (selectedIndex < 0 || selectedIndex >= values.size()) return;
            playButtonSound();
            int idx = selectedIndex;
            dismiss();
            if (listener != null) listener.onNumberSelected(idx);
        });

        popupWindow = new PopupWindow(root, dp(DIALOG_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(() -> {
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "announce_number");
        draggableHelper.setupDraggablePopup(popupWindow, root,
                dp(DIALOG_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** 数字 n 是否在本次 MSG_ANNOUNCE_NUMBER 下发的可选值列表中 */
    private boolean isNumberAllowed(int n) {
        for (int v : values) {
            if (v == n) return true;
        }
        return false;
    }

    /** event_handler.cpp BUTTON_ANNUMBER_* L455-478：按钮选中下拉框中值相同的项并启用确定 */
    private void onGridButtonClicked(int numberValue) {
        playButtonSound();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == numberValue + 1) {
                selectedIndex = i;
                spNumber.setSelection(i);
                break;
            }
        }
        updateGridHighlight();
        btnOk.setEnabled(selectedIndex >= 0);
    }

    /** 选中项对应的按钮高亮（对齐 setPressed 视觉），其余恢复默认色 */
    private void updateGridHighlight() {
        int hit = -1;
        if (selectedIndex >= 0 && selectedIndex < values.size()) {
            int v = values.get(selectedIndex);
            if (v >= 1 && v <= GRID_COUNT) hit = v - 1;
        }
        for (int i = 0; i < GRID_COUNT; i++) {
            gridButtons[i].setSelected(i == hit);
        }
    }

    private void playButtonSound() {
        if (context instanceof YGOProActivity) {
            SoundManager sm = ((YGOProActivity) context).getSoundManager();
            if (sm != null) sm.playSoundEffect(SoundManager.SFX.BUTTON);
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
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