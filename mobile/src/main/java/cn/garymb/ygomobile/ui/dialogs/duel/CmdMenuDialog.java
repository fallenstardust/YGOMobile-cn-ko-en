package cn.garymb.ygomobile.ui.dialogs.duel;

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
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.GameFieldController;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.card.CardDetailPanel;
import cn.garymb.ygomobile.render.gamefieldview.GameFieldView;
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
public class CmdMenuDialog extends CmdMenuDialogBase {

    public CmdMenuDialog(YGOProActivity activity) {
        super(activity);
    }

    /**
     * 菜单项与动作一一对应；不追加“取消”项（点击菜单外即关闭；返回键按用户规格不响应），
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

        // 用户规格（新）：仅在是/否询问窗（YesOrNoDialog）显示时，点击正带黄色虚线行进轮廓
        // 的可选卡（is_selectable 且未选中，SelectionOutlineRenderer 绘虚线，即连锁/效果
        // 询问候选卡）不显示「发动」按钮——此时应由询问窗应答，菜单再给发动入口冗余且误导；
        // 其余按钮、其余卡片与询问窗不显示时全部保持原有行为不变
        if (YesOrNoDialog.isShowingAny() && card.is_selectable && !card.is_selected) {
            String activateText = sysString(SYS_ACTIVATE);
            int idx = options.indexOf(activateText);
            if (idx >= 0) {
                options.remove(idx);
                actions.remove(idx);
            }
        }

        if (options.isEmpty()) {
            dismiss();
            return;
        }
        setItems(options, actions);
        show(anchorView, card, tapX, tapY);
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
}
