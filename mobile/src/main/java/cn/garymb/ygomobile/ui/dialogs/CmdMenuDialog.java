package cn.garymb.ygomobile.ui.dialogs;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import ocgcore.DataManager;
import ocgcore.enums.CardType;

/**
 * 卡片命令菜单（对应桌面版 gframe 的 wCmdMenu）：
 * 点击场上/手卡卡片时，以点击位置作为菜单左下角弹出，尺寸较小；
 * 点击菜单以外区域自动关闭。菜单项由卡片 cmdFlag 动态生成。
 * 无标题栏/滚动区：弹窗总高度随菜单项数量动态变化；
 * 不追加“取消”项（点击菜单外或返回键即关闭）；
 * 菜单构建逻辑（showCardCommandMenu）已迁入本类：
 * 卡片信息直接显示到 CardDetailPanel，菜单仅承载可执行命令；
 * 按钮文字统一只从 StringManager 的系统字符串索引获取（与 gframe ShowMenu 一致），不附加其他文本符号。
 */
public class CmdMenuDialog {

    private static final int MENU_WIDTH_DP = 120;

    /** 命令上下文（与 GameFieldController.CMD_CONTEXT_* 保持同值） */
    public static final int CMD_CONTEXT_IDLE = 1;
    public static final int CMD_CONTEXT_BATTLE = 2;

    /** 系统字符串索引（对应 strings.conf #actions 段，与 gframe wCmdMenu 按钮文本一致） */
    private static final int SYS_ACTIVATE = 1150;    // 发动
    private static final int SYS_SUMMON = 1151;      // 召唤
    private static final int SYS_SPSUMMON = 1152;    // 特殊召唤
    private static final int SYS_SET = 1153;         // 盖放
    private static final int SYS_FLIP = 1154;        // 反转召唤
    private static final int SYS_TO_DEFENSE = 1155;  // 守备表示
    private static final int SYS_TO_ATTACK = 1156;   // 攻击表示
    private static final int SYS_ATTACK = 1157;      // 攻击
    private static final int SYS_SET_MONSTER = 1159; // 怪兽卡设置到魔陷区

    private final YGOProActivity activity;
    private final PopupWindow popupWindow;
    private final View contentView;
    private final LinearLayout layoutItems;

    public CmdMenuDialog(YGOProActivity activity) {
        this.activity = activity;
        contentView = LayoutInflater.from(activity).inflate(R.layout.popup_window_cmd_menu, null);
        layoutItems = contentView.findViewById(R.id.layout_cmd_menu_items);

        popupWindow = new PopupWindow(contentView, dp(MENU_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
    }

    /**
     * 菜单项与动作一一对应；不追加“取消”项（点击菜单外/返回键即关闭），
     * 弹窗总高度随条目数量动态变化
     */
    public void setItems(List<String> labels, List<Runnable> actions) {
        layoutItems.removeAllViews();
        int count = Math.min(labels.size(), actions.size());
        for (int i = 0; i < count; i++) {
            final Runnable action = actions.get(i);
            Button btn = createItemButton(labels.get(i));
            btn.setOnClickListener(v -> {
                dismiss();
                if (action != null) action.run();
            });
            layoutItems.addView(btn);
        }
    }

    /**
     * 以点击位置为菜单左下角显示
     *
     * @param anchorView 接收点击的场地 View（用于把触点换算为屏幕坐标）
     * @param tapX       相对 anchorView 的点击 X
     * @param tapY       相对 anchorView 的点击 Y
     */
    public void show(View anchorView, float tapX, float tapY) {
        if (anchorView == null || anchorView.getWindowToken() == null) return;
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }

        View decor = activity.getWindow().getDecorView();
        int screenW = decor.getWidth() > 0 ? decor.getWidth()
                : activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = decor.getHeight() > 0 ? decor.getHeight()
                : activity.getResources().getDisplayMetrics().heightPixels;

        int widthPx = dp(MENU_WIDTH_DP);
        // 按自然高度测量：总高度随菜单项数量动态增减（无标题/滚动区）
        contentView.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int menuHeight = contentView.getMeasuredHeight();
        // 仅封顶屏幕高度防止窗口出界（常规项数下即内容自然高度）
        if (menuHeight > screenH) menuHeight = screenH;
        popupWindow.setHeight(menuHeight);

        int[] loc = new int[2];
        anchorView.getLocationOnScreen(loc);
        int clickX = loc[0] + (int) tapX;
        int clickY = loc[1] + (int) tapY;

        // 点击点=菜单左下角：底边对齐触点；上方放不下则翻转到触点下方显示，并钳制四边
        int left = clickX;
        int top = clickY - menuHeight;
        if (top < 0) top = clickY;
        if (left + widthPx > screenW) left = screenW - widthPx;
        if (left < 0) left = 0;
        if (top + menuHeight > screenH) top = screenH - menuHeight;
        if (top < 0) top = 0;

        popupWindow.showAtLocation(decor, Gravity.TOP | Gravity.START, left, top);
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    public void dismiss() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    /**
     * 根据卡片 cmdFlag 构建命令菜单并弹出（由 GameFieldController.showCardCommandMenu 迁移）。
     * 卡片信息直接同步到 CardDetailPanel，菜单仅承载可执行命令；
     * 按钮文字统一只取 StringManager 对应索引的字符串，不附加其他文本符号。
     *
     * @param card        被点击的卡片
     * @param engine      游戏引擎（命令列表来源）
     * @param cmdContext  {@link #CMD_CONTEXT_IDLE} / {@link #CMD_CONTEXT_BATTLE}
     * @param anchorView  接收点击的场地 View
     * @param tapX        相对 anchorView 的点击 X
     * @param tapY        相对 anchorView 的点击 Y
     */
    public void showCardCommandMenu(GameField.ClientCard card, GameEngine engine, int cmdContext,
                                    View anchorView, float tapX, float tapY) {
        if (card == null || engine == null) return;

        if (card.code > 0) {
            activity.showCardInfoPanel(card);
        }

        int flag = card.cmdFlag;
        boolean battlePhase = (cmdContext == CMD_CONTEXT_BATTLE);
        boolean idlePhase = (cmdContext == CMD_CONTEXT_IDLE);
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        String activateText = sysString(SYS_ACTIVATE);

        if ((flag & GameEngine.COMMAND_ACTIVATE) != 0) {
            for (GameEngine.CmdCardInfo info : engine.activatableCards) {
                if (info.card != card) continue;
                // desc 本身即系统字符串索引；无 desc 或索引缺失时回退到“发动”（同样取自字符串表）
                String label = info.desc > 0 ? sysString(info.desc, activateText) : activateText;
                options.add(label);
                final int idx = info.index;
                if (battlePhase) {
                    actions.add(() -> activity.sendResponseInt(idx << 16));
                } else {
                    actions.add(() -> activity.sendResponseInt((idx << 16) + 5));
                }
            }
        }

        if ((flag & GameEngine.COMMAND_ATTACK) != 0) {
            int idx = findCmdIndex(engine.attackableCards, card);
            if (idx >= 0) {
                options.add(sysString(SYS_ATTACK));
                final int attackIdx = idx;
                actions.add(() -> activity.sendResponseInt((attackIdx << 16) + 1));
            }
        }

        if (idlePhase) {
            if ((flag & GameEngine.COMMAND_SUMMON) != 0) {
                int idx = findCmdIndex(engine.summonableCards, card);
                if (idx >= 0) {
                    options.add(sysString(SYS_SUMMON));
                    final int summonIdx = idx;
                    actions.add(() -> activity.sendResponseInt(summonIdx << 16));
                }
            }

            if ((flag & GameEngine.COMMAND_SPSUMMON) != 0) {
                int idx = findCmdIndex(engine.spsummonableCards, card);
                if (idx >= 0) {
                    options.add(sysString(SYS_SPSUMMON));
                    final int spIdx = idx;
                    actions.add(() -> activity.sendResponseInt((spIdx << 16) + 1));
                }
            }

            if ((flag & GameEngine.COMMAND_REPOS) != 0) {
                int idx = findCmdIndex(engine.reposableCards, card);
                if (idx >= 0) {
                    int stringId;
                    if ((card.position & 0xA) != 0) {
                        stringId = SYS_FLIP;         // 里侧 → 反转召唤
                    } else if (card.isAttack()) {
                        stringId = SYS_TO_DEFENSE;   // 攻击 → 守备表示
                    } else {
                        stringId = SYS_TO_ATTACK;    // 守备 → 攻击表示
                    }
                    options.add(sysString(stringId));
                    final int reposIdx = idx;
                    actions.add(() -> activity.sendResponseInt((reposIdx << 16) + 2));
                }
            }

            if ((flag & GameEngine.COMMAND_MSET) != 0) {
                int idx = findCmdIndex(engine.msetableCards, card);
                if (idx >= 0) {
                    options.add(sysString(SYS_SET));
                    final int msetIdx = idx;
                    actions.add(() -> activity.sendResponseInt((msetIdx << 16) + 3));
                }
            }

            if ((flag & GameEngine.COMMAND_SSET) != 0) {
                int idx = findCmdIndex(engine.ssetableCards, card);
                if (idx >= 0) {
                    // 与 gframe ShowMenu 一致：魔陷卡用 1153（盖放），怪兽卡用 1159
                    boolean isMonster = (card.type & CardType.Monster.getId()) != 0;
                    options.add(sysString(isMonster ? SYS_SET_MONSTER : SYS_SET));
                    final int ssetIdx = idx;
                    actions.add(() -> activity.sendResponseInt((ssetIdx << 16) + 4));
                }
            }
        }

        // 无可用命令项时关闭已显示的旧菜单，不残留上一次的可操作按钮（卡片信息已在面板展示）
        if (options.isEmpty()) {
            dismiss();
            return;
        }

        setItems(options, actions);
        show(anchorView, tapX, tapY);
    }

    private static int findCmdIndex(List<GameEngine.CmdCardInfo> list, GameField.ClientCard card) {
        for (GameEngine.CmdCardInfo info : list) {
            if (info.card == card) return info.index;
        }
        return -1;
    }

    private static String sysString(int index) {
        return DataManager.get().getStringManager().getSystemString(index, "");
    }

    private static String sysString(int index, String def) {
        return DataManager.get().getStringManager().getSystemString(index, def);
    }

    private Button createItemButton(String text) {
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setAllCaps(false);
        btn.setBackgroundResource(R.drawable.button3_bg);
        btn.setMinHeight(dp(28));
        btn.setPadding(dp(6), dp(2), dp(6), dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        btn.setLayoutParams(lp);
        return btn;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}