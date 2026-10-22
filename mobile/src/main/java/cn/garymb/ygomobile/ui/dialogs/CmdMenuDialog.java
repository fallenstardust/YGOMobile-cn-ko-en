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
import cn.garymb.ygomobile.game.ClientCard;
import cn.garymb.ygomobile.game.GameFieldController;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.render.GameFieldView;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.enums.CardType;

/**
 * 卡片命令菜单（对应桌面版 gframe 的 wCmdMenu）：
 * 点击场上/手卡卡片时，以点击位置作为菜单左下角弹出，尺寸较小；
 * 点击菜单以外区域自动关闭。菜单项由卡片 cmdFlag 动态生成。
 * 无标题栏/滚动区：弹窗总高度随菜单项数量动态变化；
 * 不追加“取消”项（点击菜单外或返回键即关闭）；
 * 菜单构建逻辑（showCardCommandMenu）已迁入本类：
 * 菜单仅承载可执行命令；卡片详情刷新由调用方 GameFieldController 延后到下一帧
 *（菜单优先渲染，避免全卡表扫描等同步重操作拖慢弹窗体感）；
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
    private static final int SYS_RESET = 1162;       // 表示重置(Reset Effect)
    private static final int SYS_OPERATION = 1161;   // 效果处理（gframe btnOperation，game.cpp L979）
    private static final int SYS_SHOW_LIST = 1158;   // 查看列表（对齐 game.cpp btnShowList）
    private static final int SYS_SELECT_OPTION = 555;// 请选择一项
    private static final int SYS_SELECT_MONSTER = 509;// 选择怪兽（对齐 event_handler.cpp BUTTON_CMD_SPSUMMON list_command 标题）

    /** client_field.h EDESC_*：duelclient.cpp L2120-2121 连锁项 flag 分流 */
    private static final int EDESC_OPERATION = 0x1;  // 继续（不弹发动菜单）
    private static final int EDESC_RESET = 0x2;      // 表示重置（走 btnReset）

    /** 「查看列表」入口：文字取系统字符串 1158（对齐 gframe game.cpp btnShowList），
     *  排列在发动/特殊召唤下方（对齐 event_handler.cpp ShowMenu 中 btnShowList 的固定槽位） */
    private String viewListText() {
        return sysString(SYS_SHOW_LIST, "查看列表");
    }

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
    /** 本次 dismiss 是否由菜单项动作触发：点菜单项关闭不收起状态标签，
     *  点菜单以外/返回键关闭则联动隐藏（视同「点击卡片以外的位置」） */
    private boolean dismissedByAction;

    public CmdMenuDialog(YGOProActivity activity) {
        this.activity = activity;
        contentView = LayoutInflater.from(activity).inflate(R.layout.popup_window_cmd_menu, null);
        layoutItems = contentView.findViewById(R.id.layout_cmd_menu_items);

        popupWindow = new PopupWindow(contentView, dp(MENU_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
        // 命令菜单不走 DraggablePopupHelper 全屏包装，单独应用同一 Android 12+ 键盘分层
        // 修复（用户规格：聊天输入框与输入法显示在所有弹窗前面）：菜单窗口压到键盘之下，
        // 菜单弹出期间点底部聊天输入框时键盘仍可盖在菜单之上并正常敲击
        cn.garymb.ygomobile.utils.DraggablePopupHelper.applyImeLayeringFix(popupWindow);
        // 菜单隐藏后恢复决斗场阶段按钮；非菜单项触发的关闭同时收起卡片状态标签
        popupWindow.setOnDismissListener(() -> {
            activity.notifyGameDialogHidden(this);
            if (!dismissedByAction) {
                GameFieldController ctl = activity.getFieldCtl();
                if (ctl != null) ctl.hideFieldStatusTip();
            }
        });
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
                dismissedByAction = true;
                dismiss();
                if (action != null) action.run();
            });
            layoutItems.addView(btn);
        }
    }

    /**
     * 以点击位置为菜单左下角显示（无对应卡片实体时的回退路径，如中央 conti_act 效果处理菜单）。
     *
     * @param anchorView 接收点击的场地 View（用于把触点换算为屏幕坐标）
     * @param tapX       相对 anchorView 的点击 X
     * @param tapY       相对 anchorView 的点击 Y
     */
    public void show(View anchorView, float tapX, float tapY) {
        show(anchorView, null, tapX, tapY);
    }

    /**
     * 弹出命令菜单。传入 {@code card} 时按该卡在 GameFieldView 上的实际绘制位置动态定位：
     * 横屏场上/手卡，以及双方卡组/额外/墓地/除外（不随朝向改变）——菜单底边落在卡片上半部中横线
     * （向上展开），水平以卡片视觉中心 {@code anchorCX} 居中（{@code left = anchorCX - widthPx/2}），
     * 靠屏幕左右边时拉回屏内；竖屏场上/手卡——显示后按上屏真实布局回校，使「最下面的按钮」的左下角
     * 精确对齐卡片矩形中心（屏幕坐标），从卡片中央往右上出现，与横屏观感一致（用户规格）。
     * {@code card} 为空时回退到以触点为菜单左下角的旧行为。
     *
     * @param anchorView 接收点击的场地 View（GameFieldView，用于投影卡片包围盒 / 换算屏幕坐标）
     * @param card       被点击的卡片；非空时按其实际屏幕位置定位菜单
     * @param tapX       相对 anchorView 的点击 X（无卡片包围盒时的回退锚点）
     * @param tapY       相对 anchorView 的点击 Y（无卡片包围盒时的回退锚点）
     */
    public void show(View anchorView, ClientCard card, float tapX, float tapY) {
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

        // 卡片实际屏幕包围盒：GameFieldView.getCardScreenBounds 返回视图内坐标
        // {centerX, 视觉顶边, 视觉底边}，加 anchorView 屏幕偏移换算为屏幕坐标；
        // 取不到（相机未就绪 / 非 GameFieldView / 无卡片）则回退到触点
        int anchorCX = loc[0] + (int) tapX;
        int cardTopY = loc[1] + (int) tapY;
        int cardBottomY = cardTopY;
        boolean useCardBounds = false;
        if (card != null && anchorView instanceof GameFieldView) {
            float[] bounds = ((GameFieldView) anchorView)
                    .getCardScreenBounds(card.controler, card.location, card.sequence);
            if (bounds != null && bounds.length >= 3) {
                anchorCX = loc[0] + (int) bounds[0];
                cardTopY = loc[1] + (int) bounds[1];
                cardBottomY = loc[1] + (int) bounds[2];
                useCardBounds = true;
            }
        }

        int left;
        int top;
        // 竖屏场上/手卡的对齐走「显示后按真实布局回校」：记录卡片矩形中心（屏幕坐标）作为基准，
        // 待 PopupWindow 真正上屏布局完成后，用最后一个按钮的实际屏幕左下角反推位移，消除
        // 测量高度与实显高度之差、窗口坐标基准与屏幕坐标基准之差、按钮背景内缩等一切误差。
        boolean realignButtonCorner = false;
        int alignCenterX = 0;
        int alignCenterY = 0;
        if (useCardBounds) {
            // Java GameField 实际几何，按朝向与卡片类型分流：
            //  • 横屏场上/手卡，以及双方卡组/额外/墓地/除外（用户规格：四类堆叠区不随朝向改变）——
            //    菜单底边落在卡片上半部中横线 cardTopY + cardH/4（cardH = 底边 - 顶边），向上展开，
            //    按钮列出现在卡片上半部；水平以卡片视觉中心 anchorCX 居中（left = anchorCX - widthPx/2），
            //    靠屏幕左右边时拉回屏内保证可见。
            //  • 竖屏场上/手卡——先按内边距估算，显示后再按上屏真实布局回校，使最下面的按钮的左下角
            //    精确落在卡片矩形中心（anchorCX / cardCenterY），菜单从卡片中央往右上出现，与横屏观感一致。
            int cardH = cardBottomY - cardTopY;
            boolean portrait = activity.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_PORTRAIT;
            boolean pileZone = card != null && isPileLocation(card.location);
            if (portrait && !pileZone) {
                int cardCenterY = cardTopY + cardH / 2;
                // 先按根布局内边距/按钮下边距估算一个尽量接近的初始位置（随后仍以上屏真实布局回校为准），
                // 减小回校时的位移量与可能的视觉跳动。
                int leftInset = contentView instanceof ViewGroup
                        ? ((ViewGroup) contentView).getPaddingLeft() : 0;
                int bottomInset = contentView instanceof ViewGroup
                        ? ((ViewGroup) contentView).getPaddingBottom() : 0;
                if (layoutItems.getChildCount() > 0) {
                    View last = layoutItems.getChildAt(layoutItems.getChildCount() - 1);
                    ViewGroup.LayoutParams p = last.getLayoutParams();
                    if (p instanceof ViewGroup.MarginLayoutParams) {
                        bottomInset += ((ViewGroup.MarginLayoutParams) p).bottomMargin;
                    }
                }
                left = anchorCX - leftInset;
                if (left + widthPx > screenW) left = screenW - widthPx;
                if (left < 0) left = 0;
                top = (cardCenterY + bottomInset) - menuHeight;
                if (top < 0) top = 0;
                if (top + menuHeight > screenH) top = screenH - menuHeight;
                // 记录基准点（屏幕坐标），显示后据最后一个按钮的真实屏幕位置精确对齐到卡片矩形中心
                realignButtonCorner = true;
                alignCenterX = anchorCX;
                alignCenterY = cardCenterY;
            } else {
                int anchorY = cardTopY + cardH / 4;
                left = anchorCX - widthPx / 2;
                if (left < 0) left = 0;
                if (left + widthPx > screenW) left = screenW - widthPx;
                top = anchorY - menuHeight;
                if (top < 0) top = 0;
            }
        } else {
            // 回退：无卡片包围盒（如中央 conti_act 堆叠菜单），保留旧行为：以触点为菜单左下角，
            // 上方不够则翻到触点下方，并保留屏幕边缘 clamp。
            left = anchorCX;
            top = cardTopY - menuHeight;
            if (top < 0) top = cardTopY;
            if (left + widthPx > screenW) left = screenW - widthPx;
            if (left < 0) left = 0;
            if (top + menuHeight > screenH) top = screenH - menuHeight;
            if (top < 0) top = 0;
        }

        popupWindow.showAtLocation(decor, Gravity.TOP | Gravity.START, left, top);
        // 新一轮显示：复位「菜单项动作触发关闭」标记
        dismissedByAction = false;
        // 菜单显示期间禁用决斗场阶段按钮
        activity.notifyGameDialogShown(this);

        // 竖屏场上/手卡：待 PopupWindow 完成上屏布局后，读取「最后一个按钮」的真实屏幕左下角，
        // 反推位移把该角精确对齐到卡片矩形中心（屏幕坐标）。位移以真实位置差计算，自动抵消
        // 测量/实显高度差、窗口/屏幕坐标基准差、按钮背景内缩等误差；仅在结果仍位于屏内时校正。
        if (realignButtonCorner && layoutItems.getChildCount() > 0) {
            final View lastBtn = layoutItems.getChildAt(layoutItems.getChildCount() - 1);
            final int fLeft = left;
            final int fTop = top;
            final int fMenuH = menuHeight;
            final int fWidth = widthPx;
            final int fScreenW = screenW;
            final int fScreenH = screenH;
            final int fAlignX = alignCenterX;
            final int fAlignY = alignCenterY;
            lastBtn.post(() -> {
                if (!popupWindow.isShowing()) return;
                int[] b = new int[2];
                lastBtn.getLocationOnScreen(b);
                int dx = fAlignX - b[0];
                int dy = fAlignY - (b[1] + lastBtn.getHeight());
                if (dx == 0 && dy == 0) return;
                int nl = fLeft + dx;
                int nt = fTop + dy;
                if (nl < 0) nl = 0;
                if (nl + fWidth > fScreenW) nl = fScreenW - fWidth;
                if (nt < 0) nt = 0;
                if (nt + fMenuH > fScreenH) nt = fScreenH - fMenuH;
                if (nl != fLeft || nt != fTop) {
                    popupWindow.update(nl, nt, -1, -1);
                }
            });
        }
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
     * 菜单仅承载可执行命令；卡片详情刷新由调用方（转发壳）延后到下一帧，本方法内
     * 不再在弹出前同步执行 showCardInfoPanel（曾是菜单变慢的主因）；
     * 按钮文字统一只取 StringManager 对应索引的字符串，不附加其他文本符号。
     *
     * @param card        被点击的卡片
     * @param engine      游戏引擎（命令列表来源）
     * @param cmdContext  {@link #CMD_CONTEXT_IDLE} / {@link #CMD_CONTEXT_BATTLE}
     * @param anchorView  接收点击的场地 View
     * @param tapX        相对 anchorView 的点击 X
     * @param tapY        相对 anchorView 的点击 Y
     */
    public void showCardCommandMenu(ClientCard card, GameEngine engine, int cmdContext,
                                    View anchorView, float tapX, float tapY) {
        showCardCommandMenu(card, engine, cmdContext, anchorView, tapX, tapY, false);
    }

    /**
     * @param viewButton 为 true 时（点击持有超量素材的怪兽 / 卡组 / 额外 / 墓地 / 除外），
     *                   菜单始终提供「查看」入口且不因无命令而自动关闭。
     */
    public void showCardCommandMenu(ClientCard card, GameEngine engine, int cmdContext,
                                    View anchorView, float tapX, float tapY, boolean viewButton) {
        if (card == null || engine == null) return;

        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (viewButton) {
            // 点击持有超量素材的怪兽 / 卡组 / 额外 / 墓地 / 除外——
            // 查看/发动/特殊召唤按钮均打开 CardDisplayDialog 列出该位置对应卡片
            buildPositionMenu(card, engine, cmdContext, options, actions);
        } else {
            buildCardCommandMenu(card, engine, cmdContext, options, actions);
        }

        if (options.isEmpty()) {
            dismiss();
            return;
        }
        setItems(options, actions);
        show(anchorView, card, tapX, tapY);
    }

    /** 单卡命令菜单（非堆叠/超量入口）：按 card.cmdFlag 逐条构建可执行命令 */
    private void buildCardCommandMenu(ClientCard card, GameEngine engine, int cmdContext,
                                      List<String> options, List<Runnable> actions) {
        int flag = card.cmdFlag;
        boolean idlePhase = (cmdContext == CMD_CONTEXT_IDLE);

        String activateText = sysString(SYS_ACTIVATE);

        // 卡片有多个可发动效果时也只生成一个「发动」按钮，按钮文字仅取系统字符串 1150，
        // 不再拼接该卡的脚本提示文字；点击后用 OptionDialog 列出各效果文字供选择。
        // 对齐 event_handler.cpp BUTTON_CMD_ACTIVATE L501-538：
        //   flag & EDESC_OPERATION → continue（由「继续/放弃」链路处理）
        //   select_options.size()==1 时直接应答，否则 command_card = menu_card; ShowSelectOption()
        List<GameEngine.CmdCardInfo> activateList = new ArrayList<>();
        if ((flag & GameEngine.COMMAND_ACTIVATE) != 0) {
            for (GameEngine.CmdCardInfo info : engine.activatableCards) {
                if (info.card != card) continue;
                if ((info.flag & EDESC_OPERATION) != 0) continue;
                if ((info.flag & EDESC_RESET) != 0) continue;
                activateList.add(info);
            }
        }
        if (!activateList.isEmpty()) {
            options.add(activateText);
            final List<GameEngine.CmdCardInfo> chosen = activateList;
            actions.add(() -> showActivateOptions(cmdContext, chosen));
        }

        // duelclient.cpp L2121 / event_handler.cpp BUTTON_CMD_RESET L501-538：
        // EDESC_RESET 项只由「表示重置」处理，Java 无 COMMAND_RESET 标记，故单独收集，避免无处可点导致通讯卡住
        List<GameEngine.CmdCardInfo> resetList = new ArrayList<>();
        if ((flag & GameEngine.COMMAND_ACTIVATE) != 0) {
            for (GameEngine.CmdCardInfo info : engine.activatableCards) {
                if (info.card != card) continue;
                if ((info.flag & EDESC_RESET) == 0) continue;
                resetList.add(info);
            }
        }
        if (!resetList.isEmpty()) {
            options.add(sysString(SYS_RESET, "表示重置"));
            final List<GameEngine.CmdCardInfo> chosen = resetList;
            actions.add(() -> showActivateOptions(cmdContext, chosen));
        }

        if ((flag & GameEngine.COMMAND_ATTACK) != 0) {
            int idx = findCmdIndex(engine.attackableCards, card);
            if (idx >= 0) {
                options.add(sysString(SYS_ATTACK));
                final int attackIdx = idx;
                // 点击「攻击宣言」瞬间即把攻击者写入 selectAttacking：覆盖「CMD 应答 → 玩家选攻击对象 →
                // 服务端下发 MSG_ATTACK」这段时间窗口，让 CardOverlayRenderer 立刻抑制该卡上的
                // attack.png 浮动箭头（对齐用户反馈：绿色攻击弧动画出现时 attack 箭头须同步隐藏）。
                // MSG_ATTACK 到达后由 DuelEventHandler.onAttack 无缝接续，转入 arcAttacker 后清零。
                final ClientCard atkCard = card;
                actions.add(() -> {
                    engine.field.selectAttacking = atkCard;
                    activity.sendResponseInt((attackIdx << 16) + 1);
                });
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
     * 发动 / 特殊召唤 → 列出该位置对应可操作卡片，单击即向通讯发送对应响应进入下一步；
     * 「查看列表」统一排列在发动/特殊召唤下方，列出该位置全部卡片。
     */
    private void buildPositionMenu(ClientCard card, GameEngine engine, int cmdContext,
                                   List<String> options, List<Runnable> actions) {
        final List<GameEngine.CmdCardInfo> actList = matchCmdCards(engine.activatableCards, card);
        if (!actList.isEmpty()) {
            if (isPileLocation(card.location)) {
                // 堆叠区（墓地/除外/卡组/额外）点「发动」：始终先弹该区所有可发动卡片的列表，
                // 单击某张卡后再按该卡可发动效果数分流（单效果直接应答、多效果弹 OptionDialog 选效果），
                // 对齐 gframe event_handler.cpp BUTTON_CMD_ACTIVATE 的 list_command 分支（L539-585）
                // → ShowSelectCard 选卡 → BUTTON_CARD_*（list_command==COMMAND_ACTIVATE）选效果。
                options.add(sysString(SYS_ACTIVATE));
                final List<GameEngine.CmdCardInfo> infos = actList;
                actions.add(() -> showPileActivateList(card, cmdContext, infos));
            } else if (allSameCard(actList)) {
                // 场上单张卡（如带素材的超量怪兽）的多个可发动效果：用 OptionDialog 列出各效果脚本提示文字，
                // 而不是用 CardDisplayDialog 显示多张相同卡图（视觉上无法区分发动的是哪个效果）。
                final List<GameEngine.CmdCardInfo> chosen = filterActivatable(actList);
                if (!chosen.isEmpty()) {
                    options.add(sysString(SYS_ACTIVATE));
                    actions.add(() -> showActivateOptions(cmdContext, chosen));
                }
            } else {
                options.add(sysString(SYS_ACTIVATE));
                final List<GameEngine.CmdCardInfo> infos = actList;
                actions.add(() -> showCmdList(card, engine, cmdContext, infos, MODE_ACTIVATE));
            }
        }

        final List<GameEngine.CmdCardInfo> spList = matchCmdCards(engine.spsummonableCards, card);
        if (!spList.isEmpty()) {
            options.add(sysString(SYS_SPSUMMON));
            // 对齐 event_handler.cpp BUTTON_CMD_SPSUMMON 的 list_command 分支（L628-665）：
            // 堆叠区（卡组/墓地/除外/额外）点「特殊召唤」始终以「选择怪兽」列表弹窗
            // 列出该区所有可特殊召唤的卡供玩家逐个点选，即使只有一张也要展示列表，
            // 不能走 allSameCard→showOptionListForMode 的直接应答捷径（否则点特殊召唤无任何列表出现）。
            final List<GameEngine.CmdCardInfo> chosen = spList;
            actions.add(() -> showCmdList(card, engine, cmdContext, chosen, MODE_SPSUMMON));
        }

        // 场上超量怪兽（带素材走本菜单而非 buildCardCommandMenu）在战斗阶段可攻击时，
        // 须与单卡菜单一样列出「攻击」，编码同为 (attackableCards索引<<16)+1
        // （对齐 event_handler.cpp COMMAND_ATTACK → myvideo 攻击应答）
        if (card.location == LOC_MZONE && (card.cmdFlag & GameEngine.COMMAND_ATTACK) != 0) {
            int idx = findCmdIndex(engine.attackableCards, card);
            if (idx >= 0) {
                options.add(sysString(SYS_ATTACK));
                final int attackIdx = idx;
                // 与 buildCardCommandMenu 中「攻击宣言」一致：应答瞬间先置位 selectAttacking，
                // 让攻击者卡上的 attack.png 在选择攻击对象窗口期就已隐藏，MSG_ATTACK 到达后
                // 由 arcAttacker 接续抑制。
                final ClientCard atkCard = card;
                actions.add(() -> {
                    engine.field.selectAttacking = atkCard;
                    activity.sendResponseInt((attackIdx << 16) + 1);
                });
            }
        }

        options.add(viewListText());
        actions.add(() -> showViewList(card, engine));
    }

    /** 是否列表中全部命令项都属于同一张卡（多则同卡多效果，少则堆叠区不同卡） */
    private static boolean allSameCard(List<GameEngine.CmdCardInfo> infos) {
        if (infos.size() <= 1) return true;
        ClientCard c0 = infos.get(0).card;
        for (GameEngine.CmdCardInfo info : infos) {
            if (info.card != c0) return false;
        }
        return true;
    }

    /** 过滤掉 EDESC_OPERATION / EDESC_RESET 项（与 buildCardCommandMenu 发动收集口径一致） */
    private static List<GameEngine.CmdCardInfo> filterActivatable(List<GameEngine.CmdCardInfo> src) {
        List<GameEngine.CmdCardInfo> out = new ArrayList<>();
        for (GameEngine.CmdCardInfo info : src) {
            if ((info.flag & EDESC_OPERATION) != 0) continue;
            if ((info.flag & EDESC_RESET) != 0) continue;
            out.add(info);
        }
        return out;
    }

    /** 过滤出与被点位置匹配的命令卡：超量怪兽按格序列匹配，堆叠区匹配整堆（controler+location） */
    private static List<GameEngine.CmdCardInfo> matchCmdCards(List<GameEngine.CmdCardInfo> src,
                                                              ClientCard card) {
        List<GameEngine.CmdCardInfo> out = new ArrayList<>();
        if (src == null) return out;
        boolean mzone = (card.location == LOC_MZONE);
        for (GameEngine.CmdCardInfo info : src) {
            ClientCard c = info.card;
            if (c == null) continue;
            if (c.controler != card.controler || c.location != card.location) continue;
            if (mzone && c.sequence != card.sequence) continue;
            out.add(info);
        }
        return out;
    }

    /** 查看列表：超量怪兽 → 其素材；堆叠区 → 该区全部卡片。
     *  回放态卡组/额外直接从录像头部（ReplayReader 解出的双方全部卡片 code）构造列表，
     *  不依赖实况 ClientCard 是否已回填；墓地/除外等动态区实况卡码为 0（里侧移动）时用
     *  前卡码 chain_code 兜底。实时决斗/观战不受影响（getReplayZoneCodes 非回放态返回 null）。 */
    private void showViewList(ClientCard card, GameEngine engine) {
        GameField field = engine.getField();
        List<CardDisplayDialog.CardItem> items = new ArrayList<>();
        String title;
        final GameEngine eng = engine;
        final int obsCtrl = card.controler;
        final int obsLoc = card.location;
        final boolean obsIsXyz = (card.location == LOC_MZONE);
        final int obsHostSeq = obsIsXyz ? card.sequence : -1;
        if (obsIsXyz) {
            for (int i = 0; i < card.overlayed.size(); i++) {
                ClientCard m = card.overlayed.get(i);
                if (m == null) continue;
                items.add(new CardDisplayDialog.CardItem(m.code, card.controler, LOC_OVERLAY, card.sequence, i));
            }
            title = sidePrefix(card.controler) + "超量素材";
        } else if (field != null) {
            List<Integer> auth = eng.getReplayZoneCodes(obsCtrl, obsLoc);
            if (auth != null && !auth.isEmpty()) {
                for (int i = 0; i < auth.size(); i++) {
                    Integer code = auth.get(i);
                    items.add(new CardDisplayDialog.CardItem(code == null ? 0 : code,
                            obsCtrl, obsLoc, i, 0));
                }
            } else {
                List<ClientCard> list = field.players[card.controler].getLocationList(card.location);
                if (list != null) {
                    for (ClientCard c : list) {
                        if (c == null) continue;
                        // 回放态里侧移动实况卡码可为 0：用前卡码 chain_code 兜底；实况不兜底保持原行为
                        int code = c.code != 0 ? c.code : (eng.replayMode ? c.chain_code : 0);
                        // 除外区里侧表示的卡携带半部呼吸卡背标记（见 CardDisplayDialog.markFacedown）
                        items.add(new CardDisplayDialog.CardItem(code, card.controler, card.location, c.sequence, 0,
                                card.location == 0x20 && !c.isFaceUp()));
                    }
                }
            }
            title = sidePrefix(card.controler) + pileName(card.location);
        } else {
            title = viewListText();
        }
        final GameField liveField = field;
        CardDisplayDialog.CardItemSupplier supplier = null;
        if (liveField != null && (obsIsXyz || isPileLocation(obsLoc))) {
            supplier = () -> {
                List<CardDisplayDialog.CardItem> fresh = new ArrayList<>();
                if (obsIsXyz) {
                    ClientCard host = liveField.getCard(obsCtrl & 1, LOC_MZONE, obsHostSeq);
                    if (host != null) {
                        for (int i = 0; i < host.overlayed.size(); i++) {
                            ClientCard m = host.overlayed.get(i);
                            if (m == null) continue;
                            fresh.add(new CardDisplayDialog.CardItem(m.code, obsCtrl, LOC_OVERLAY, obsHostSeq, i));
                        }
                    }
                } else {
                    List<Integer> auth = eng.getReplayZoneCodes(obsCtrl, obsLoc);
                    if (auth != null && !auth.isEmpty()) {
                        for (int i = 0; i < auth.size(); i++) {
                            Integer code = auth.get(i);
                            fresh.add(new CardDisplayDialog.CardItem(code == null ? 0 : code,
                                    obsCtrl, obsLoc, i, 0));
                        }
                        return fresh;
                    }
                    List<ClientCard> list = liveField.players[obsCtrl & 1].getLocationList(obsLoc);
                    if (list != null) {
                        for (ClientCard c : list) {
                            if (c == null) continue;
                            int code = c.code != 0 ? c.code : (eng.replayMode ? c.chain_code : 0);
                            // 与初始 items 同源：除外区里侧卡实时刷新时保持呼吸卡背标记
                            fresh.add(new CardDisplayDialog.CardItem(code, obsCtrl, obsLoc, c.sequence, 0,
                                    obsLoc == 0x20 && !c.isFaceUp()));
                        }
                    }
                }
                return fresh;
            };
        }
        showCardListDialog(title, items, null, MODE_VIEW, 0, engine, supplier);
    }

    /** 回放态点击堆叠区：不经命令菜单，直接弹该区卡片正面列表（含实时刷新），
     *  与菜单内「查看」共用实现（card.controler / card.location 均为视角侧口径） */
    public void showPileViewList(ClientCard card, GameEngine engine) {
        showViewList(card, engine);
    }

    /** 堆叠区判断：卡组 0x01 / 墓地 0x10 / 除外 0x20 / 额外 0x40（与 GameFieldController.onCardClick isPile 同口径） */
    private static boolean isPileLocation(int location) {
        return location == 0x01 || location == 0x10 || location == 0x20 || location == 0x40;
    }

    /** 发动 / 特殊召唤列表：单击对应卡片即向通讯发送响应 */
    private void showCmdList(ClientCard card, GameEngine engine, int cmdContext,
                             List<GameEngine.CmdCardInfo> infos, int mode) {
        // 按卡去重保序：gframe sort+unique 语义，同一卡多条命令只展示一次
        java.util.Map<ClientCard, List<GameEngine.CmdCardInfo>> byCard =
                new java.util.LinkedHashMap<>();
        for (GameEngine.CmdCardInfo info : infos) {
            if (info.card == null) continue;
            List<GameEngine.CmdCardInfo> list = byCard.get(info.card);
            if (list == null) {
                list = new ArrayList<>();
                byCard.put(info.card, list);
            }
            list.add(info);
        }
        List<CardDisplayDialog.CardItem> items = new ArrayList<>();
        final List<List<GameEngine.CmdCardInfo>> cardIndices = new ArrayList<>();
        for (java.util.Map.Entry<ClientCard, List<GameEngine.CmdCardInfo>> e : byCard.entrySet()) {
            ClientCard c = e.getKey();
            int code = c.code;
            // 优先使用第一条记录的 code（如从额外卡组特殊召唤时由引擎指定码值）
            for (GameEngine.CmdCardInfo info : e.getValue()) {
                if (info.code != 0) { code = info.code; break; }
            }
            int loc = c.location;
            int seq = c.sequence;
            items.add(new CardDisplayDialog.CardItem(code, card.controler, loc, seq, 0));
            cardIndices.add(e.getValue());
        }
        if (items.isEmpty()) return;
        // 特殊召唤列表标题对齐 gframe「选择怪兽」(509)；发动列表沿用「发动」(1150)
        String title = (mode == MODE_SPSUMMON)
                ? sysString(SYS_SELECT_MONSTER, "选择怪兽") : sysString(SYS_ACTIVATE);
        showCmdListDialog(title, items, cardIndices, mode, cmdContext, engine);
    }

    /**
     * 堆叠区「发动」列表：弹出该区所有可发动卡片（按卡去重），单击某张卡后按该卡可发动效果数分流——
     * 单效果直接应答、多效果弹 OptionDialog 列出各效果脚本提示文字供选择。
     * 对齐 gframe event_handler.cpp BUTTON_CMD_ACTIVATE 的 list_command 分支
     *（ShowSelectCard 列卡 → BUTTON_CARD_* 选卡 → 单效果 SetResponseI / 多效果 ShowSelectOption）。
     */
    private void showPileActivateList(ClientCard card, int cmdContext,
                                       List<GameEngine.CmdCardInfo> infos) {
        java.util.Map<ClientCard, List<GameEngine.CmdCardInfo>> byCard =
                new java.util.LinkedHashMap<>();
        for (GameEngine.CmdCardInfo info : infos) {
            if (info.card == null) continue;
            List<GameEngine.CmdCardInfo> list = byCard.get(info.card);
            if (list == null) {
                list = new ArrayList<>();
                byCard.put(info.card, list);
            }
            list.add(info);
        }
        List<CardDisplayDialog.CardItem> items = new ArrayList<>();
        final List<List<GameEngine.CmdCardInfo>> cardEffects = new ArrayList<>();
        for (java.util.Map.Entry<ClientCard, List<GameEngine.CmdCardInfo>> e : byCard.entrySet()) {
            ClientCard c = e.getKey();
            int code = c.code;
            for (GameEngine.CmdCardInfo info : e.getValue()) {
                if (info.code != 0) { code = info.code; break; }
            }
            items.add(new CardDisplayDialog.CardItem(code, card.controler, c.location, c.sequence, 0));
            cardEffects.add(filterActivatable(e.getValue()));
        }
        if (items.isEmpty()) return;
        ImageLoader loader = activity.getImageLoader();
        CardDetailPanel panel = activity.getCardDetailPanel();
        final CardDisplayDialog dialog = new CardDisplayDialog(activity, loader);
        if (panel != null) panel.setCardDisplayDialog(dialog);
        dialog.setTitle(sysString(SYS_ACTIVATE))
                .setCards(items)
                .setLocalPlayer(0)
                .setControlerProtocolSide(false)
                .setCardClickListener(item -> {
                    int pos = items.indexOf(item);
                    dialog.dismiss();
                    if (pos < 0 || pos >= cardEffects.size()) return;
                    List<GameEngine.CmdCardInfo> effects = cardEffects.get(pos);
                    if (effects.size() == 1) {
                        sendCmdResponse(MODE_ACTIVATE, cmdContext, effects.get(0).index);
                    } else if (effects.size() > 1) {
                        showActivateOptions(cmdContext, effects);
                    }
                })
                .setOnDismissListener(() -> {
                    if (panel != null) panel.setCardDisplayDialog(null);
                });
        dialog.show();
    }

    /**
     * 中央 conti_act（待效果结算）堆叠被点击：菜单仅一个「效果处理」按钮
     *（对齐 gframe event_handler.cpp POSITION_HINT 悬停 ShowMenu(COMMAND_OPERATION) → btnOperation，
     * 按钮文字取系统字符串 1161）。点击后进入选卡列表 {@link #showContiOperationList}。
     *
     * @param cmdContext 当前命令上下文（idle/battle/chain），决定最终应答编码
     */
    public void showContiOperationMenu(GameEngine engine, int cmdContext,
                                       View anchorView, float tapX, float tapY) {
        if (engine == null || anchorView == null) return;
        GameField field = engine.getField();
        if (field == null || !field.contiAct || field.contiCards.isEmpty()) return;
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        options.add(sysString(SYS_OPERATION, "效果处理"));
        actions.add(() -> showContiOperationList(engine, cmdContext));
        setItems(options, actions);
        show(anchorView, tapX, tapY);
    }

    /**
     * 效果处理选卡列表（对齐 gframe BUTTON_CMD_ACTIVATE case POSITION_HINT L567-584：
     * selectable_cards = conti_cards 去重 → ShowSelectCard(is_continuous)，列表标题取 1161）：
     * 单击某张卡 → 在 engine.activatableCards 中收集该卡的 EDESC_OPERATION 项
     *（对应 L822-832：list_command==COMMAND_OPERATION 时仅保留 OPERATION 项），
     * 单效果直接应答、多效果弹 OptionDialog 选效果，应答编码按上下文分流（L833-841，
     * 复用 {@link #sendCmdResponse}：idle=(idx<<16)+5 / battle=idx<<16 / chain=idx）。
     */
    private void showContiOperationList(GameEngine engine, int cmdContext) {
        GameField field = engine.getField();
        if (field == null) return;
        // 去重保序（gframe sort+unique：同一卡的多个待处理效果在列表中只占一张卡位）
        List<ClientCard> uniq = new ArrayList<>();
        for (ClientCard c : field.contiCards) {
            if (c != null && !uniq.contains(c)) uniq.add(c);
        }
        List<CardDisplayDialog.CardItem> items = new ArrayList<>();
        for (ClientCard c : uniq) {
            int code = c.code != 0 ? c.code : c.chain_code;
            items.add(new CardDisplayDialog.CardItem(code, c.controler, c.location, c.sequence, 0));
        }
        if (items.isEmpty()) return;
        ImageLoader loader = activity.getImageLoader();
        CardDetailPanel panel = activity.getCardDetailPanel();
        final CardDisplayDialog dialog = new CardDisplayDialog(activity, loader);
        if (panel != null) panel.setCardDisplayDialog(dialog);
        dialog.setTitle(sysString(SYS_OPERATION, "效果处理"))
                .setCards(items)
                .setLocalPlayer(0)
                .setControlerProtocolSide(false)
                .setCardClickListener(item -> {
                    int pos = items.indexOf(item);
                    dialog.dismiss();
                    if (pos < 0 || pos >= uniq.size()) return;
                    ClientCard card = uniq.get(pos);
                    List<GameEngine.CmdCardInfo> ops = new ArrayList<>();
                    for (GameEngine.CmdCardInfo info : engine.activatableCards) {
                        if (info.card == card && (info.flag & EDESC_OPERATION) != 0) ops.add(info);
                    }
                    if (ops.size() == 1) {
                        sendCmdResponse(MODE_ACTIVATE, cmdContext, ops.get(0).index);
                    } else if (ops.size() > 1) {
                        showActivateOptions(cmdContext, ops);
                    }
                })
                .setOnDismissListener(() -> {
                    if (panel != null) panel.setCardDisplayDialog(null);
                });
        dialog.show();
    }

    /**
     * 发动：单条直接应答，多条弹 OptionDialog 列出各效果脚本提示文字
     *（对齐 client_field.cpp ShowSelectOption + event_handler.cpp BUTTON_OPTION → SetResponseSelectedOption）。
     */
    private void showActivateOptions(int cmdContext, List<GameEngine.CmdCardInfo> infos) {
        showOptionListForMode(cmdContext, infos, MODE_ACTIVATE);
    }

    /**
     * 单卡多命令（发动 / 特殊召唤）选择：仅 1 项直接应答，多项弹 OptionDialog 列出各效果
     * 脚本提示文字供选择；应答编码统一走 sendCmdResponse(mode, …)（与单卡菜单一致）。
     */
    private void showOptionListForMode(int cmdContext, List<GameEngine.CmdCardInfo> infos, int mode) {
        if (infos == null || infos.isEmpty()) return;
        if (infos.size() == 1) {
            sendCmdResponse(mode, cmdContext, infos.get(0).index);
            return;
        }
        String fallback = sysString(mode == MODE_SPSUMMON ? SYS_SPSUMMON : SYS_ACTIVATE);
        List<String> texts = new ArrayList<>();
        for (GameEngine.CmdCardInfo info : infos) {
            texts.add(info.desc > 0 ? DataManager.get().getDesc(info.desc, fallback) : fallback);
        }
        final List<GameEngine.CmdCardInfo> targets = infos;
        new OptionDialog(activity)
                .setTitle(sysString(SYS_SELECT_OPTION, "请选择一项"))
                .setOptions(texts)
                .setOnOptionSelectedListener(index -> {
                    if (index >= 0 && index < targets.size()) {
                        sendCmdResponse(mode, cmdContext, targets.get(index).index);
                    }
                })
                .show();
    }

    /**
     * 构建并弹出 CardDisplayDialog：单击 → 命令模式发送响应；
     * 按下即在 CardDetailPanel 显示详情、按住悬浮显示通讯状态标签（由 CardDisplayDialog 内部接管）。
     */
    private void showCardListDialog(String title, List<CardDisplayDialog.CardItem> items,
                                    List<Integer> indices, int mode, int cmdContext, GameEngine engine) {
        showCardListDialog(title, items, indices, mode, cmdContext, engine, null);
    }

    private void showCardListDialog(String title, List<CardDisplayDialog.CardItem> items,
                                    List<Integer> indices, int mode, int cmdContext, GameEngine engine,
                                    CardDisplayDialog.CardItemSupplier liveSupplier) {
        if (items == null || items.isEmpty()) return;
        ImageLoader loader = activity.getImageLoader();
        CardDetailPanel panel = activity.getCardDetailPanel();
        final CardDisplayDialog dialog = new CardDisplayDialog(activity, loader);
        if (panel != null) panel.setCardDisplayDialog(dialog);
        dialog.setTitle(title)
                .setCards(items)
                .setLocalPlayer(0)   // item.controler 已是视角侧（0=我方）
                .setControlerProtocolSide(false)
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
                .setOnDismissListener(() -> {
                    if (panel != null) panel.setCardDisplayDialog(null);
                });
        if (liveSupplier != null) dialog.observeLive(liveSupplier);
        dialog.show();
    }

    /**
     * 按卡去重后的特殊召唤/发动列表：一张卡对应多条命令时，点击后按多效果选项分流
     */
    private void showCmdListDialog(String title, List<CardDisplayDialog.CardItem> items,
                                   List<List<GameEngine.CmdCardInfo>> cardInfos, int mode, int cmdContext, GameEngine engine) {
        if (items == null || items.isEmpty()) return;
        ImageLoader loader = activity.getImageLoader();
        CardDetailPanel panel = activity.getCardDetailPanel();
        final CardDisplayDialog dialog = new CardDisplayDialog(activity, loader);
        if (panel != null) panel.setCardDisplayDialog(dialog);
        dialog.setTitle(title)
                .setCards(items)
                .setLocalPlayer(0)
                .setControlerProtocolSide(false)
                .setCardClickListener(item -> {
                    if (mode == MODE_VIEW) {
                        activity.showCardInfoPanel(clientCardFromItem(item));
                    } else {
                        int pos = items.indexOf(item);
                        if (pos >= 0 && cardInfos != null && pos < cardInfos.size()) {
                            List<GameEngine.CmdCardInfo> infos = cardInfos.get(pos);
                            // 特殊召唤：同一怪兽若有多种特殊召唤方式，核心的 idlecmd 段会把同一张卡按每个
                            // EFFECT_SPSUMMON_PROC 多次入列（ocgcore processor.cpp L2185-2191），但该段每条记录
                            // 都不带 desc（CommandDataParser 写死 0）。应答索引只决定「召唤哪张卡」，真正的召唤
                            // 方式由核心在收到应答后另发 MSG_SELECT_OPTION，携带各方式 description（脚本 cdb 提示
                            // 文字或 strings.conf 系统字符串），交 OptionDialog 用 DataManager.getDesc 解析成不同文本。
                            // 故此处一律取该卡首条记录直接应答，绝不能用 desc 恒为 0 的本段记录弹客户端 OptionDialog
                            //（那样会显示多个文本相同的「特殊召唤」按钮，玩家无法区分）。对齐 event_handler.cpp
                            // BUTTON_CARD COMMAND_SPSUMMON 分支 L809-817：while(spsummonable_cards[index]!=card) index++;
                            // SetResponseI((index<<16)+1)。
                            if (mode == MODE_SPSUMMON) {
                                sendCmdResponse(mode, cmdContext, infos.get(0).index);
                            } else if (infos.size() == 1) {
                                sendCmdResponse(mode, cmdContext, infos.get(0).index);
                            } else if (infos.size() > 1) {
                                showOptionListForMode(cmdContext, infos, mode);
                            }
                        }
                        dialog.dismiss();
                    }
                })
                .setOnDismissListener(() -> {
                    if (panel != null) panel.setCardDisplayDialog(null);
                });
        dialog.show();
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

    private ClientCard clientCardFromItem(CardDisplayDialog.CardItem item) {
        ClientCard c = new ClientCard();
        c.code = item.code & 0x7fffffff;
        c.controler = item.controler;
        c.location = item.location;
        c.sequence = item.sequence;
        c.position = 0x1;
        return c;
    }

    /** 标题标注是哪一方（card.controler 已是视角侧，0=我方）：文字走系统字符串 102/103 */
    private static String sidePrefix(int controler) {
        return controler == 0 ? sysString(102, "我方") : sysString(103, "对方");
    }

    /** 卡组/额外/墓地/除外区名称（走系统字符串 1000/1006/1004/1005，与 gframe STRING_LOCATION 一致） */
    private static String pileName(int location) {
        switch (location) {
            case 0x01: return sysString(1000, "卡组");
            case 0x40: return sysString(1006, "额外卡组");
            case 0x10: return sysString(1004, "墓地");
            case 0x20: return sysString(1005, "除外区");
            default: return "卡片";
        }
    }

    private static int findCmdIndex(List<GameEngine.CmdCardInfo> list, ClientCard card) {
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
        btn.setTextColor(YGOUtil.c(R.color.white));
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
        return (int) (value * activity.getResources().getDisplayMetrics().density);
    }
}