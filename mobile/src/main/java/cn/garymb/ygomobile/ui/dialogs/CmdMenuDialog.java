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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
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
    public static final int CMD_CONTEXT_CHAIN = 3;

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

    /** 「查看」入口文本：与项目内其他中文提示一致直接内联 */
    private static final String VIEW_TEXT = "查看";

    // 位置卡片列表的三种模式（查看 / 发动 / 特殊召唤）
    private static final int MODE_VIEW = 0;
    private static final int MODE_ACTIVATE = 1;
    private static final int MODE_SPSUMMON = 2;
    private static final int LOC_MZONE = 0x04;
    private static final int LOC_OVERLAY = 0x80;

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
        // 菜单隐藏后恢复决斗场阶段按钮
        popupWindow.setOnDismissListener(() -> activity.notifyGameDialogHidden(this));
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
        // 菜单显示期间禁用决斗场阶段按钮
        activity.notifyGameDialogShown(this);
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
        showCardCommandMenu(card, engine, cmdContext, anchorView, tapX, tapY, false);
    }

    /**
     * @param viewButton 为 true 时（点击持有超量素材的怪兽 / 卡组 / 额外 / 墓地 / 除外），
     *                   菜单始终提供「查看」入口且不因无命令而自动关闭；卡片信息改由「查看」按钮触发。
     */
    public void showCardCommandMenu(GameField.ClientCard card, GameEngine engine, int cmdContext,
                                    View anchorView, float tapX, float tapY, boolean viewButton) {
        if (card == null || engine == null) return;

        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (viewButton) {
            // 点击持有超量素材的怪兽 / 卡组 / 额外 / 墓地 / 除外——
            // 查看/发动/特殊召唤按钮均打开 CardDisplayDialog 列出该位置对应卡片
            buildPositionMenu(card, engine, cmdContext, options, actions);
        } else {
            if (card.code > 0) activity.showCardInfoPanel(card);
            buildCardCommandMenu(card, engine, cmdContext, options, actions);
        }

        if (options.isEmpty()) {
            dismiss();
            return;
        }
        setItems(options, actions);
        show(anchorView, tapX, tapY);
    }

    /** 单卡命令菜单（非堆叠/超量入口）：按 card.cmdFlag 逐条构建可执行命令 */
    private void buildCardCommandMenu(GameField.ClientCard card, GameEngine engine, int cmdContext,
                                      List<String> options, List<Runnable> actions) {
        int flag = card.cmdFlag;
        boolean battlePhase = (cmdContext == CMD_CONTEXT_BATTLE);
        boolean idlePhase = (cmdContext == CMD_CONTEXT_IDLE);
        boolean chainPhase = (cmdContext == CMD_CONTEXT_CHAIN);

        String activateText = sysString(SYS_ACTIVATE);

        if ((flag & GameEngine.COMMAND_ACTIVATE) != 0) {
            for (GameEngine.CmdCardInfo info : engine.activatableCards) {
                if (info.card != card) continue;
                final int idx = info.index;
                if (chainPhase) {
                    options.add(info.desc > 0 ? DataManager.get().getDesc(info.desc, activateText) : activateText);
                    actions.add(() -> activity.getDialogUtil().activateChainOption(idx));
                } else {
                    options.add(info.desc > 0 ? sysString(info.desc, activateText) : activateText);
                    if (battlePhase) {
                        actions.add(() -> activity.sendResponseInt(idx << 16));
                    } else {
                        actions.add(() -> activity.sendResponseInt((idx << 16) + 5));
                    }
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
                        stringId = SYS_FLIP;
                    } else if (card.isAttack()) {
                        stringId = SYS_TO_DEFENSE;
                    } else {
                        stringId = SYS_TO_ATTACK;
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
                    boolean isMonster = (card.type & CardType.Monster.getId()) != 0;
                    options.add(sysString(isMonster ? SYS_SET_MONSTER : SYS_SET));
                    final int ssetIdx = idx;
                    actions.add(() -> activity.sendResponseInt((ssetIdx << 16) + 4));
                }
            }
        }
    }

    /**
     * 堆叠区 / 超量怪兽入口的「位置命令」菜单。
     * 查看 → 列出该位置全部卡片；发动 / 特殊召唤 → 列出该位置对应可操作卡片，
     * 单击即向通讯发送对应响应进入下一步。
     */
    private void buildPositionMenu(GameField.ClientCard card, GameEngine engine, int cmdContext,
                                   List<String> options, List<Runnable> actions) {
        options.add(VIEW_TEXT);
        actions.add(() -> showViewList(card, engine));

        final List<GameEngine.CmdCardInfo> actList = matchCmdCards(engine.activatableCards, card);
        if (!actList.isEmpty()) {
            options.add(sysString(SYS_ACTIVATE));
            actions.add(() -> showCmdList(card, engine, cmdContext, actList, MODE_ACTIVATE));
        }

        final List<GameEngine.CmdCardInfo> spList = matchCmdCards(engine.spsummonableCards, card);
        if (!spList.isEmpty()) {
            options.add(sysString(SYS_SPSUMMON));
            actions.add(() -> showCmdList(card, engine, cmdContext, spList, MODE_SPSUMMON));
        }
    }

    /** 过滤出与被点位置匹配的命令卡：超量怪兽按格序列匹配，堆叠区匹配整堆（controler+location） */
    private static List<GameEngine.CmdCardInfo> matchCmdCards(List<GameEngine.CmdCardInfo> src,
                                                              GameField.ClientCard card) {
        List<GameEngine.CmdCardInfo> out = new ArrayList<>();
        if (src == null) return out;
        boolean mzone = (card.location == LOC_MZONE);
        for (GameEngine.CmdCardInfo info : src) {
            GameField.ClientCard c = info.card;
            if (c == null) continue;
            if (c.controler != card.controler || c.location != card.location) continue;
            if (mzone && c.sequence != card.sequence) continue;
            out.add(info);
        }
        return out;
    }

    /** 查看列表：超量怪兽 → 其素材；堆叠区 → 该区全部卡片 */
    private void showViewList(GameField.ClientCard card, GameEngine engine) {
        GameField field = engine.getField();
        List<CardDisplayDialog.CardItem> items = new ArrayList<>();
        String title;
        if (card.location == LOC_MZONE) {
            for (int i = 0; i < card.overlayed.size(); i++) {
                GameField.ClientCard m = card.overlayed.get(i);
                if (m == null) continue;
                items.add(new CardDisplayDialog.CardItem(m.code, card.controler, LOC_OVERLAY, card.sequence, i));
            }
            title = sidePrefix(card.controler) + "超量素材";
        } else if (field != null) {
            List<GameField.ClientCard> list = field.players[card.controler].getLocationList(card.location);
            if (list != null) {
                for (GameField.ClientCard c : list) {
                    if (c == null) continue;
                    items.add(new CardDisplayDialog.CardItem(c.code, card.controler, card.location, c.sequence, 0));
                }
            }
            title = sidePrefix(card.controler) + pileName(card.location);
        } else {
            title = VIEW_TEXT;
        }
        showCardListDialog(title, items, null, MODE_VIEW, 0, engine);
    }

    /** 发动 / 特殊召唤列表：单击对应卡片即向通讯发送响应 */
    private void showCmdList(GameField.ClientCard card, GameEngine engine, int cmdContext,
                             List<GameEngine.CmdCardInfo> infos, int mode) {
        List<CardDisplayDialog.CardItem> items = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (GameEngine.CmdCardInfo info : infos) {
            GameField.ClientCard c = info.card;
            int code = info.code != 0 ? info.code : (c != null ? c.code : 0);
            int loc = c != null ? c.location : card.location;
            int seq = c != null ? c.sequence : card.sequence;
            items.add(new CardDisplayDialog.CardItem(code, card.controler, loc, seq, 0));
            indices.add(info.index);
        }
        String title = (mode == MODE_SPSUMMON) ? sysString(SYS_SPSUMMON) : sysString(SYS_ACTIVATE);
        showCardListDialog(title, items, indices, mode, cmdContext, engine);
    }

    /**
     * 构建并弹出 CardDisplayDialog：单击 → 查看模式显示卡片详情 / 命令模式发送响应；
     * 长按 → 显示该卡在通讯中的实时状态信息（Toast）。
     */
    private void showCardListDialog(String title, List<CardDisplayDialog.CardItem> items,
                                    List<Integer> indices, int mode, int cmdContext, GameEngine engine) {
        if (items == null || items.isEmpty()) return;
        ImageLoader loader = activity.getImageLoader();
        CardDetailPanel panel = activity.getCardDetailPanel();
        final CardDisplayDialog dialog = new CardDisplayDialog(activity, loader);
        if (panel != null) panel.setCardDisplayDialog(dialog);
        dialog.setTitle(title)
                .setCards(items)
                .setLocalPlayer(0)   // item.controler 已是视角侧（0=我方）
                .setCardClickListener(item -> {
                    if (mode == MODE_VIEW) {
                        activity.showCardInfoPanel(clientCardFromItem(item));
                    } else {
                        int pos = items.indexOf(item);
                        if (pos >= 0 && indices != null && pos < indices.size()) {
                            sendCmdResponse(mode, cmdContext, indices.get(pos));
                        }
                        dialog.dismiss();
                    }
                })
                .setCardLongClickListener(item -> {
                    showLiveStatus(engine, item);
                    return true;
                })
                .setOnDismissListener(() -> {
                    if (panel != null) panel.setCardDisplayDialog(null);
                })
                .show();
    }

    /** 发送命令响应：特召=(idx<<16)+1；发动按上下文 idle=(idx<<16)+5 / battle=idx<<16 / chain 走连锁收尾 */
    private void sendCmdResponse(int mode, int cmdContext, int idx) {
        if (mode == MODE_SPSUMMON) {
            activity.sendResponseInt((idx << 16) + 1);
        } else if (cmdContext == CMD_CONTEXT_CHAIN) {
            activity.getDialogUtil().activateChainOption(idx);
        } else if (cmdContext == CMD_CONTEXT_BATTLE) {
            activity.sendResponseInt(idx << 16);
        } else {
            activity.sendResponseInt((idx << 16) + 5);
        }
    }

    private GameField.ClientCard clientCardFromItem(CardDisplayDialog.CardItem item) {
        GameField.ClientCard c = new GameField.ClientCard();
        c.code = item.code & 0x7fffffff;
        c.controler = item.controler;
        c.location = item.location;
        c.sequence = item.sequence;
        c.position = 0x1;
        return c;
    }

    /** 长按：从场地模型解析对应实时卡片，展示通讯中获取的状态信息 */
    private void showLiveStatus(GameEngine engine, CardDisplayDialog.CardItem item) {
        GameField.ClientCard c = resolveLiveCard(engine, item);
        String msg;
        if (c == null) {
            msg = "无通讯状态信息";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(c.code > 0 ? ("[" + c.code + "]") : "[???]");
            if (!nz(c.lvString).isEmpty()) sb.append("  ").append(c.lvString);
            if (!nz(c.atkString).isEmpty()) sb.append("  ATK ").append(c.atkString);
            if (!nz(c.defString).isEmpty()) sb.append(" / DEF ").append(c.defString);
            if (!nz(c.linkString).isEmpty()) sb.append("  ").append(c.linkString);
            if (!nz(c.lscString).isEmpty()) sb.append("  刻度 ").append(c.lscString).append("/").append(nz(c.rscString));
            sb.append("  表示 ").append(positionText(c.position));
            if (c.counters != null && !c.counters.isEmpty()) sb.append("  指示物 ").append(c.counters.size()).append(" 种");
            msg = sb.toString();
        }
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
    }

    private GameField.ClientCard resolveLiveCard(GameEngine engine, CardDisplayDialog.CardItem item) {
        if (engine == null) return null;
        GameField field = engine.getField();
        if (field == null) return null;
        if (item.location == LOC_OVERLAY) {
            GameField.ClientCard xyz = field.getCard(item.controler, LOC_MZONE, item.sequence);
            if (xyz != null && item.subSeq >= 0 && item.subSeq < xyz.overlayed.size()) {
                return xyz.overlayed.get(item.subSeq);
            }
            return null;
        }
        return field.getCard(item.controler, item.location, item.sequence);
    }

    private static String positionText(int pos) {
        boolean faceup = (pos & 0x5) != 0;
        boolean attack = (pos & 0x3) != 0;
        return (faceup ? "表侧" : "里侧") + (attack ? "攻击" : "守备");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
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

    /** 标题标注是哪一方（card.controler 已是视角侧，0=我方） */
    private static String sidePrefix(int controler) {
        return controler == 0 ? "我方" : "对方";
    }

    /** 卡组/额外/墓地/除外区名称（与 YGOProActivity.getLocationName 一致） */
    private static String pileName(int location) {
        switch (location) {
            case 0x01: return "卡组";
            case 0x40: return "额外卡组";
            case 0x10: return "墓地";
            case 0x20: return "除外区";
            default: return "卡片";
        }
    }
}