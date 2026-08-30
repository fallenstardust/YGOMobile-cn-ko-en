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
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;

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
 * 快捷模式（count<=12 且全部值 ∈[1,12]）隐藏下拉框、点击按钮选中，否则隐藏按钮组（duelclient.cpp L4051-4093）。
 * 响应为选中项索引（event_handler.cpp BUTTON_ANNUMBER_OK L480-484：SetResponseI(cbANNumber->getSelected())）。
 */
public class AnnounceNumberDialog {

    private static final int DIALOG_WIDTH_DP = 300;
    /** 快捷按钮数量，对齐 btnANNumber[12]（game.cpp L908） */
    private static final int GRID_COUNT = 12;

    public interface OnNumberSelectedListener {
        void onNumberSelected(int selectedIndex);
    }

    public interface OnDismissListener {
        void onDismiss();
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
    private LinearLayout layoutButtons;
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

        for (int row = 0; row < GRID_COUNT / 3; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 3; col++) {
                final int idx = row * 3 + col;
                Button btn = new Button(context);
                btn.setText(String.valueOf(idx + 1));
                btn.setTextColor(Color.WHITE);
                btn.setTextSize(12);
                btn.setBackgroundColor(0xFF335577);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                btn.setLayoutParams(lp);
                btn.setOnClickListener(v -> onGridButtonClicked(idx));
                gridButtons[idx] = btn;
                line.addView(btn);
            }
            layoutButtons.addView(line);
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
            gridButtons[i].setBackgroundColor(i == hit ? 0xFF5577AA : 0xFF335577);
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