package cn.garymb.ygomobile.ui.dialogs.duel;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import cn.garymb.ygomobile.utils.YGOUtil;

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

    /** sys1510「玩家选择了：[%ls]」：桌面版 HINT_OPSELECTED/HINT_ZONE 用 GetSysString(1510)（duelclient.cpp L1464/L1562） */
    public static String formatSelected(String what) {
        return sysFormat(1510, "玩家选择了：%s", what);
    }

    /** sys1511「玩家宣言了：[%ls]」：桌面版 HINT_RACE/ATTRIB/CODE 用 GetSysString(1511)（duelclient.cpp L1482/L1493/L1503） */
    public static String formatDeclared(String what) {
        return sysFormat(1511, "玩家宣言了：%s", what);
    }

    /** HINT_OPSELECTED：desc 经 DataManager.getDesc 解析（系统字符串或卡片脚本提示）；返回记录的文本供居中 AC 动画复用（duelclient.cpp L1464） */
    public static String addOpSelectedLog(int desc) {
        String what = DataManager.get().getDesc(desc, "");
        if (!what.isEmpty()) {
            String text = formatSelected(what);
            addLog(text);
            return text;
        }
        return null;
    }

    /** HINT_RACE：宣告种族记入日志（对齐 gframe 用 sys1511「玩家宣言了」，duelclient.cpp L1482）；返回记录文本供 AC 动画复用 */
    public static String addSelectedRaceLog(int raceMask) {
        String text = formatDeclared(formatRace(raceMask));
        addLog(text);
        return text;
    }

    /** HINT_ATTRIB：宣告属性记入日志（对齐 gframe 用 sys1511「玩家宣言了」，duelclient.cpp L1493）；返回记录文本供 AC 动画复用 */
    public static String addSelectedAttributeLog(int attrMask) {
        String text = formatDeclared(formatAttribute(attrMask));
        addLog(text);
        return text;
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

    /**
     * sys 字符串模板格式化：模板取自字符串表，缺失时使用兜底文本 def。
     * 委托 DataManager.formatSystemString（内部 cFormat）处理 strings.conf 的 C 宽字符格式符 %ls：
     * 裸 String.format 遇 %ls 会抛 UnknownFormatConversionException('l')，导致回退成含字面 "%s" 的 def，
     * 这正是日志条目残留 "%s" 的根因（对齐 YesOrNoDialog.sysFormat 惯例）。
     */
    public static String sysFormat(int index, String def, Object... args) {
        return DataManager.get().formatSystemString(index, def, args);
    }

    private final YGOProActivity activity;
    private final LogAdapter adapter;
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;
    private ListView lvLog;

    public DuelLogDialog(YGOProActivity activity) {
        this.activity = activity;
        this.adapter = new LogAdapter(DialogScale.wrap(activity));
        build();
    }

    private void build() {
        View root = LayoutInflater.from(DialogScale.wrap(activity)).inflate(R.layout.dialog_duel_log, null);
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

        // 铺满整屏的转发层（对齐 YesOrNoDialog 等决斗内弹窗的 ACTIVE_LAYERS 触摸转发/点击置顶）：
        // 使决斗记录与连锁询问等模态弹窗可同时交互——点记录仍命中记录，点被记录遮挡的弹窗会把
        // 该弹窗提到最前。非获焦 + 窗外不拦截 + 不处理返回键 + 消费 ACTION_OUTSIDE：外部点击与
        // 返回键都不会关闭它，仅「确定」按钮 dismiss（用户规格）。内容尺寸 187x300，位置贴右上角。
        int designW = dp(187);
        int designH = dp(300);
        popupWindow = new PopupWindow(DialogScale.wrap(activity));
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setFocusable(false);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setTouchInterceptor((v, event) ->
                event.getAction() == android.view.MotionEvent.ACTION_OUTSIDE);
        popupWindow.setOnDismissListener(() -> showingInstance = null);

        draggableHelper = new DraggablePopupHelper(DialogScale.wrap(activity), "game_dialog_duel_log");
        // root 此时无父视图 → 走全窗口包装：popup 尺寸置 MATCH_PARENT 铺满整屏，root 作为内容子视图
        draggableHelper.setupDraggablePopup(popupWindow, root, designW, designH);
        draggableHelper.setDragEnabled(popupWindow, false); // 固定右上角，不可拖动
        // 旋转后按新视图树重新贴 layout_game_right 右上角（区域按 id 解析，交由自定义重排全权处理）
        draggableHelper.registerOrientationRelayout(popupWindow, this::anchorTopRightInGameRight);
    }

    /** 把铺满整屏的记录浮层内容贴到 layout_game_right 右上角；区域未就绪由锚定方法内部延后处理 */
    private void anchorTopRightInGameRight() {
        if (popupWindow == null || activity.isFinishing() || activity.isDestroyed()) return;
        View gameRight = activity.findViewById(R.id.layout_game_right);
        if (gameRight != null) {
            DraggablePopupHelper.anchorPopupInRegionTopRight(popupWindow, gameRight);
        }
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
        // 铺满整屏的转发层窗口直接贴在 Activity decor 上（popup 尺寸已为 MATCH_PARENT），
        // 位置由内容的 margin 锚定到 layout_game_right 右上角，区域未布局时锚定内部延后处理
        if (draggableHelper != null) {
            draggableHelper.showPopup(popupWindow, decor);
        } else {
            popupWindow.showAtLocation(decor, Gravity.NO_GRAVITY, 0, 0);
        }
        anchorTopRightInGameRight();
        // 输入法抬升联动（用户规格）：抬键盘时本面板底边贴聊天输入框上沿（贴区域顶缘，
        // 故只收缩窗口不改为贴底），收起后回到 layout_game_right 右上角，逻辑统一在 ImePopupAdjuster
        ImePopupAdjuster.attach(popupWindow);
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
        return DialogScale.dpToPx(activity, value);
    }

    private static class LogAdapter extends BaseAdapter {
        /** 携带卡代码的条目高亮显示（暗示可点击查看卡信息） */
        private static final int COLOR_CARD = YGOUtil.c(R.color.duel_log_card_text);
        private static final int COLOR_NORMAL = YGOUtil.c(R.color.white);

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