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
import ocgcore.enums.CardRace;

/**
 * 宣言种族对话框（移植 gframe wANRace，game.cpp L938-947）：
 * 26 个勾选框对应 chkRace[RACES_COUNT]，标签经 sysString(1020+i) 获取；
 * 可见性按 MSG_ANNOUNCE_RACE 的 available 掩码过滤（duelclient.cpp L3996-4014）；
 * 勾选数达到 announce_count 时自动发送种族位掩码（event_handler.cpp CHECK_RACE L1003-1015）。
 */
public class AnnounceRaceDialog {

    private static final int DIALOG_WIDTH_DP = 300;

    /** sysString 缺失时的兜底文本，顺序与 CardRace.values()（STRING_ID_RACE 1020+i）一致 */
    private static final String[] FALLBACK_NAMES = {
            "战士", "魔法师", "天使", "恶魔", "不死", "机械", "水", "炎", "岩石",
            "鸟兽", "植物", "昆虫", "雷", "龙", "兽", "兽战士", "恐龙",
            "鱼", "海龙", "爬虫", "超能力", "幻神兽", "创造神", "幻龙", "电子界", "幻想魔"
    };

    public interface OnRaceSelectedListener {
        void onRaceSelected(int raceMask);
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

    private String title = "选择种族";
    private int availableMask = ~0;
    private int announceCount = 1;

    private TextView tvTitle;
    private final CheckBox[] chkRaces = new CheckBox[CardRace.values().length];

    private OnRaceSelectedListener listener;
    private OnDismissListener dismissListener;

    public AnnounceRaceDialog(Context context) {
        this.context = context;
    }

    public AnnounceRaceDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    /** MSG_ANNOUNCE_RACE 的 available 掩码：仅对应位为 1 的种族可见（duelclient.cpp L4000-4005） */
    public AnnounceRaceDialog setAvailableMask(int mask) {
        this.availableMask = mask;
        return this;
    }

    /** 宣言数量 announce_count：勾选数达到即自动发送（event_handler.cpp L1011） */
    public AnnounceRaceDialog setAnnounceCount(int count) {
        this.announceCount = count;
        return this;
    }

    public AnnounceRaceDialog setOnRaceSelectedListener(OnRaceSelectedListener l) {
        this.listener = l;
        return this;
    }

    public AnnounceRaceDialog setOnDismissListener(OnDismissListener l) {
        this.dismissListener = l;
        return this;
    }

    /** XML 中固定的 26 个种族勾选框，顺序与 CardRace.values() 一致 */
    private static final int[] CHECKBOX_IDS = {
            R.id.cb_anrace_warrior, R.id.cb_anrace_spellcaster, R.id.cb_anrace_fairy,
            R.id.cb_anrace_fiend, R.id.cb_anrace_zombie, R.id.cb_anrace_machine,
            R.id.cb_anrace_aqua, R.id.cb_anrace_pyro, R.id.cb_anrace_rock,
            R.id.cb_anrace_wingedbeast, R.id.cb_anrace_plant, R.id.cb_anrace_insect,
            R.id.cb_anrace_thunder, R.id.cb_anrace_dragon, R.id.cb_anrace_beast,
            R.id.cb_anrace_beastwarrior, R.id.cb_anrace_dinosaur, R.id.cb_anrace_fish,
            R.id.cb_anrace_seaserpent, R.id.cb_anrace_reptile, R.id.cb_anrace_psychic,
            R.id.cb_anrace_divinebeast, R.id.cb_anrace_creatorgod, R.id.cb_anrace_wyrm,
            R.id.cb_anrace_cyberse, R.id.cb_anrace_illusion
    };

    private void build() {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_announce_race, null);
        tvTitle = root.findViewById(R.id.tv_anrace_title);
        tvTitle.setText(title);

        CardRace[] races = CardRace.values();
        for (int i = 0; i < races.length; i++) {
            CheckBox cb = root.findViewById(CHECKBOX_IDS[i]);
            cb.setText(mStringManager.getSystemString(races[i].getLanguageIndex(), FALLBACK_NAMES[i]));
            cb.setVisibility((races[i].value() & availableMask) != 0 ? View.VISIBLE : View.GONE);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckedChanged());
            chkRaces[i] = cb;
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

        draggableHelper = new DraggablePopupHelper(context, "announce_race");
        draggableHelper.setupDraggablePopup(popupWindow, root,
                dp(DIALOG_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** event_handler.cpp CHECK_RACE L1003-1015：统计勾选掩码与数量，数量达标即应答并关闭 */
    private void onCheckedChanged() {
        CardRace[] races = CardRace.values();
        int mask = 0;
        int count = 0;
        for (int i = 0; i < chkRaces.length; i++) {
            if (chkRaces[i] != null && chkRaces[i].isChecked()) {
                mask |= (int) races[i].value();
                count++;
            }
        }
        if (count > 0 && count == announceCount) {
            playButtonSound();
            dismiss();
            if (listener != null) listener.onRaceSelected(mask);
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