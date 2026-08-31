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
import android.widget.CheckBox;
import android.widget.PopupWindow;
import android.widget.TextView;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.enums.CardAttribute;

/**
 * 宣言属性对话框（移植 gframe wANAttribute，game.cpp L928-937）：
 * 7 个勾选框对应 chkAttribute[ATTRIBUTES_COUNT]，标签经 sysString(1010+i) 获取；
 * 可见性按 MSG_ANNOUNCE_ATTRIB 的 available 掩码过滤（duelclient.cpp L4015-4033）；
 * 勾选数达到 announce_count 时自动发送属性位掩码（event_handler.cpp CHECK_ATTRIBUTE L989-1001）。
 */
public class AnnounceAttributeDialog {

    private static final int DIALOG_WIDTH_DP = 300;

    /** sysString 缺失时的兜底文本，顺序与 CardAttribute.values()（STRING_ID_ATTRIBUTE 1010+i）一致 */
    private static final String[] FALLBACK_NAMES = {
            "地", "水", "炎", "风", "光", "暗", "神"
    };

    public interface OnAttributeSelectedListener {
        void onAttributeSelected(int attributeMask);
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

    private String title = "选择属性";
    private int availableMask = ~0;
    private int announceCount = 1;

    private TextView tvTitle;
    private final CheckBox[] chkAttributes = new CheckBox[CardAttribute.values().length];

    private OnAttributeSelectedListener listener;
    private OnDismissListener dismissListener;

    public AnnounceAttributeDialog(Context context) {
        this.context = context;
    }

    public AnnounceAttributeDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    /** MSG_ANNOUNCE_ATTRIB 的 available 掩码：仅对应位为 1 的属性可见（duelclient.cpp L4019-4024） */
    public AnnounceAttributeDialog setAvailableMask(int mask) {
        this.availableMask = mask;
        return this;
    }

    /** 宣言数量 announce_count：勾选数达到即自动发送（event_handler.cpp L997） */
    public AnnounceAttributeDialog setAnnounceCount(int count) {
        this.announceCount = count;
        return this;
    }

    public AnnounceAttributeDialog setOnAttributeSelectedListener(OnAttributeSelectedListener l) {
        this.listener = l;
        return this;
    }

    public AnnounceAttributeDialog setOnDismissListener(OnDismissListener l) {
        this.dismissListener = l;
        return this;
    }

    /** XML 中固定的 7 个属性勾选框，顺序与 CardAttribute.values() 一致 */
    private static final int[] CHECKBOX_IDS = {
            R.id.cb_anattrib_earth, R.id.cb_anattrib_water, R.id.cb_anattrib_fire,
            R.id.cb_anattrib_wind, R.id.cb_anattrib_light, R.id.cb_anattrib_dark,
            R.id.cb_anattrib_divine
    };

    private void build() {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_announce_attribute, null);
        tvTitle = root.findViewById(R.id.tv_anattrib_title);
        tvTitle.setText(title);

        CardAttribute[] attrs = CardAttribute.values();
        for (int i = 0; i < attrs.length; i++) {
            CheckBox cb = root.findViewById(CHECKBOX_IDS[i]);
            cb.setText(mStringManager.getSystemString(attrs[i].getLanguageIndex(), FALLBACK_NAMES[i]));
            cb.setVisibility((attrs[i].getId() & availableMask) != 0 ? View.VISIBLE : View.GONE);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckedChanged());
            chkAttributes[i] = cb;
        }

        popupWindow = new PopupWindow(root, dp(DIALOG_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(() -> {
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "announce_attribute");
        draggableHelper.setupDraggablePopup(popupWindow, root,
                dp(DIALOG_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** event_handler.cpp CHECK_ATTRIBUTE L989-1001：统计勾选掩码与数量，数量达标即应答并关闭 */
    private void onCheckedChanged() {
        CardAttribute[] attrs = CardAttribute.values();
        int mask = 0;
        int count = 0;
        for (int i = 0; i < chkAttributes.length; i++) {
            if (chkAttributes[i] != null && chkAttributes[i].isChecked()) {
                mask |= (int) attrs[i].getId();
                count++;
            }
        }
        if (count > 0 && count == announceCount) {
            playButtonSound();
            dismiss();
            if (listener != null) listener.onAttributeSelected(mask);
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