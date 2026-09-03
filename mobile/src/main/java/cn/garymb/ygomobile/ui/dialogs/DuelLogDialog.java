package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.lite.R;

import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardAttribute;
import ocgcore.enums.CardRace;

/**
 * 决斗日志对话框（对应桌面版 gframe wLogs，game.cpp L624-633）：
 * 标题取自 sysString(1271)「消息记录」，底部「清空」按钮对齐 BUTTON_CLEAR_LOG（sysString(1304)），
 * 「确定」按钮对齐 BUTTON_CLOSE_LOG（sysString(1211)）。
 * 日志由通讯解析端通过静态 addLog 追加（对齐 Game::AddLog，game.cpp L2270-2276）；
 * 列表项点击：携带卡代码（logParam>0）时展示对应卡信息（对齐 event_handler.cpp LISTBOX_LOG L2175-2182）。
 * 记录在整个对局期间保留，退出对战（hideGameUI）时统一清空（对齐 CloseDuelWindow）。
 */
public class DuelLogDialog {

    private static final int SYS_TITLE = 1271;       // 消息记录
    private static final int SYS_CLEAR = 1304;       // 清空
    private static final int SYS_CLOSE = 1211;       // 确定

    /**
     * 公共字符串管理器：声明时初始化，供类内静态/实例函数统一调用
     * （对齐 YGOProActivity/CardDetailPanel 的 mStringManager 惯例；
     * 因 formatRace/formatAttribute/sysFormat 为静态方法，故声明为 static）
     */
    public static final StringManager mStringManager = DataManager.get().getStringManager();

    public static class LogItem {
        public final String text;
        public final int cardCode;

        public LogItem(String text, int cardCode) {
            this.text = text;
            this.cardCode = cardCode;
        }
    }

    /** 日志在对局期间持续累积，独立于对话框是否显示（对齐 lstLog/logParam 生命周期） */
    private static final List<LogItem> logItems = new ArrayList<>();
    private static DuelLogDialog showingInstance;

    /** 对齐 Game::AddLog(msg, param)：追加一条日志，可携带卡代码供点击查看卡信息 */
    public static void addLog(String text, int cardCode) {
        if (text == null || text.isEmpty()) return;
        logItems.add(new LogItem(text, cardCode));
        if (showingInstance != null) showingInstance.refresh();
    }

    public static void addLog(String text) {
        addLog(text, 0);
    }

    /** 对齐 BUTTON_CLEAR_LOG：清空日志 */
    public static void clearLogs() {
        logItems.clear();
        if (showingInstance != null) showingInstance.refresh();
    }

    // === 决斗日志文本工具（自 GameEngine 迁入，供通讯解析端调用，对齐桌面版 duelclient.cpp 各 AddLog 调用点） ===

    /** "已选择：%s" 模板：桌面版使用 GetSysString(1510) */
    public static String formatSelected(String what) {
        return sysFormat(1510, "已选择：%s", what);
    }

    /** HINT_OPSELECTED：desc 经 DataManager.getDesc 解析（系统字符串或卡片脚本提示） */
    public static void addOpSelectedLog(int desc) {
        String what = DataManager.get().getDesc(desc, "");
        if (!what.isEmpty()) {
            addLog(formatSelected(what));
        }
    }

    /** HINT_RACE：宣告种族选择记入日志 */
    public static void addSelectedRaceLog(int raceMask) {
        addLog(formatSelected(formatRace(raceMask)));
    }

    /** HINT_ATTRIB：宣告属性选择记入日志 */
    public static void addSelectedAttributeLog(int attrMask) {
        addLog(formatSelected(formatAttribute(attrMask)));
    }

    /** 对齐 dataManager.FormatRace：种族位掩码转字符串表名称 */
    private static String formatRace(int mask) {
        StringBuilder sb = new StringBuilder();
        for (CardRace race : CardRace.values()) {
            if ((mask & race.value()) != 0) {
                if (sb.length() > 0) sb.append('/');
                sb.append(mStringManager.getSystemString(race.getLanguageIndex(), race.name()));
            }
        }
        return sb.length() > 0 ? sb.toString() : String.valueOf(mask);
    }

    /** 对齐 dataManager.FormatAttribute：属性位掩码转字符串表名称 */
    private static String formatAttribute(int mask) {
        StringBuilder sb = new StringBuilder();
        for (CardAttribute attr : CardAttribute.values()) {
            if ((mask & attr.getId()) != 0) {
                if (sb.length() > 0) sb.append('/');
                sb.append(mStringManager.getSystemString(attr.getLanguageIndex(), attr.name()));
            }
        }
        return sb.length() > 0 ? sb.toString() : String.valueOf(mask);
    }

    /** sys 字符串模板格式化：模板取自字符串表，缺失时使用兜底文本 */
    public static String sysFormat(int index, String def, Object... args) {
        try {
            return String.format(java.util.Locale.US,
                    mStringManager.getSystemString(index, def), args);
        } catch (Exception e) {
            return def;
        }
    }

    private final YGOProActivity activity;
    private final LogAdapter adapter;
    private PopupWindow popupWindow;
    private ListView lvLog;

    public DuelLogDialog(YGOProActivity activity) {
        this.activity = activity;
        this.adapter = new LogAdapter(activity);
        build();
    }

    private void build() {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_duel_log, null);
        lvLog = root.findViewById(R.id.lv_duel_log);
        Button btnClear = root.findViewById(R.id.btn_duel_log_clear);
        Button btnClose = root.findViewById(R.id.btn_duel_log_close);

        btnClear.setText(mStringManager.getSystemString(SYS_CLEAR, "清空"));
        btnClose.setText(mStringManager.getSystemString(SYS_CLOSE, "确定"));

        lvLog.setAdapter(adapter);
        lvLog.setOnItemClickListener((parent, view, position, id) -> {
            // 对齐 LISTBOX_LOG：携带卡代码的条目点击后展示卡信息
            if (position < 0 || position >= logItems.size()) return;
            adapter.setSelected(position);
            int code = logItems.get(position).cardCode;
            if (code > 0) {
                Card card = DataManager.get().getCardManager().getCard(code);
                if (card != null) {
                    activity.getCardDetailPanel().showCard(card);
                }
            }
        });
        btnClear.setOnClickListener(v -> {
            SoundManager soundManager = activity.getSoundManager();
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            clearLogs();
        });
        btnClose.setOnClickListener(v -> {
            SoundManager soundManager = activity.getSoundManager();
            if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
            dismiss();
        });

        // 宽度为默认 280dp 的 2/3；初始位置锚定 layout_game_right 右上角（见 show()）
        popupWindow = new PopupWindow(root, dp(187), dp(300), true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(() -> showingInstance = null);
    }

    /** 对齐 imgLog 开关：已显示则隐藏，否则显示 */
    public void toggle() {
        if (isShowing()) {
            dismiss();
        } else {
            show();
        }
    }

    public void show() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (popupWindow == null || popupWindow.isShowing()) return;
        View decor = activity.getWindow().getDecorView();
        if (decor.getWindowToken() == null) return;
        showingInstance = this;
        refresh();
        View gameRight = activity.findViewById(R.id.layout_game_right);
        if (gameRight != null && gameRight.getWidth() > 0 && gameRight.getHeight() > 0) {
            showAtGameRightTopRight(gameRight);
        } else if (gameRight != null) {
            // 决斗 UI 刚切为可见的同帧宽度尚为 0，等布局完成后再显示
            gameRight.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            gameRight.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            if (showingInstance != DuelLogDialog.this) return; // 等待期间已被取消
                            showAtGameRightTopRight(gameRight);
                        }
                    });
        } else {
            // 极端兜底：找不到锚点时屏幕居中
            popupWindow.showAtLocation(decor, Gravity.CENTER, 0, 0);
        }
    }

    /** 对齐 OptionDialog 定位惯例：弹窗右上角与 layout_game_right 右上角重合 */
    private void showAtGameRightTopRight(View gameRight) {
        int[] loc = new int[2];
        gameRight.getLocationInWindow(loc);
        int x = loc[0] + gameRight.getWidth() - popupWindow.getWidth();
        if (x < loc[0]) x = loc[0]; // 弹窗宽于区域时钳制回区域内
        int y = loc[1];
        popupWindow.showAtLocation(gameRight, Gravity.NO_GRAVITY, x, y);
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        showingInstance = null;
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    private void refresh() {
        if (logItems.isEmpty()) {
            adapter.selectedPosition = -1;
        }
        adapter.notifyDataSetChanged();
        if (lvLog != null) {
            lvLog.clearChoices();
            // 最新日志滚动到底部（对齐桌面端追加式记录）
            lvLog.post(() -> lvLog.setSelection(Math.max(0, logItems.size() - 1)));
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static class LogAdapter extends BaseAdapter {
        /** 携带卡代码的条目高亮显示（暗示可点击查看卡信息） */
        private static final int COLOR_CARD = 0xFFFFD27F;
        private static final int COLOR_NORMAL = 0xFFFFFFFF;

        /** 当前点击选中的条目位置；-1 表示无选中 */
        private int selectedPosition = -1;

        private final LayoutInflater inflater;

        LogAdapter(Context context) {
            inflater = LayoutInflater.from(context);
        }

        /** 点击条目：更新选中位置并重绘，原选中项自动失去焦点恢复原样式 */
        void setSelected(int position) {
            if (selectedPosition == position) return;
            selectedPosition = position;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return logItems.size();
        }

        @Override
        public LogItem getItem(int position) {
            return logItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView == null) {
                tv = (TextView) inflater.inflate(R.layout.item_duel_log, parent, false);
            } else {
                tv = (TextView) convertView;
            }
            LogItem item = logItems.get(position);
            tv.setText(item.text);
            if (position == selectedPosition) {
                // 选中态：背景 ygopro_line_color、文字 colorNavy
                tv.setBackgroundResource(R.color.ygopro_line_color);
                tv.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.colorNavy));
            } else {
                // 失去焦点：背景恢复透明，文字恢复原配色
                tv.setBackgroundResource(android.R.color.transparent);
                tv.setTextColor(item.cardCode > 0 ? COLOR_CARD : COLOR_NORMAL);
            }
            return tv;
        }
    }
}