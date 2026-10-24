package cn.garymb.ygomobile.ui.dialogs.duel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.card.CardStatusTipHelper;

import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;

/**
 * Card select popup dialog, middle layer of the split inheritance
 * chain (CardSelectDialogBase <- CardSelectDialogMid <- CardSelectDialog).
 * Owns the popup construction pass, the dialog width / card image /
 * font scaling pipeline with its orientation relayout, the slot click
 * state machine, the sum state refresh and the slot rendering.
 */
abstract class CardSelectDialogMid extends CardSelectDialogBase {

    protected CardSelectDialogMid(Context context, ImageLoader imageLoader) {
        super(context, imageLoader);
    }

    protected void build() {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_card_select, null);
        tvTitle = root.findViewById(R.id.tv_card_select_title);
        sbPage = root.findViewById(R.id.sb_card_page);
        btnOk = root.findViewById(R.id.btn_card_select_ok);

        slotViews[0] = root.findViewById(R.id.slot_card_0);
        tvPositions[0] = root.findViewById(R.id.tv_card_pos_0);
        ivCards[0] = root.findViewById(R.id.iv_card_0);
        slotViews[1] = root.findViewById(R.id.slot_card_1);
        tvPositions[1] = root.findViewById(R.id.tv_card_pos_1);
        ivCards[1] = root.findViewById(R.id.iv_card_1);
        slotViews[2] = root.findViewById(R.id.slot_card_2);
        tvPositions[2] = root.findViewById(R.id.tv_card_pos_2);
        ivCards[2] = root.findViewById(R.id.iv_card_2);
        slotViews[3] = root.findViewById(R.id.slot_card_3);
        tvPositions[3] = root.findViewById(R.id.tv_card_pos_3);
        ivCards[3] = root.findViewById(R.id.iv_card_3);
        slotViews[4] = root.findViewById(R.id.slot_card_4);
        tvPositions[4] = root.findViewById(R.id.tv_card_pos_4);
        ivCards[4] = root.findViewById(R.id.iv_card_4);

        tvTitle.setText(buildTitleText());
        popupWindow = new PopupWindow(root,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        // 输入法弹出时让本弹窗窗口被压缩、内容随之上移露出输入框（ADJUST_NOTHING 会把键盘压在
        // 全屏弹窗后面导致无法输入）；键盘收起后由 DragFrameLayout.onSizeChanged 按区域重新解算定位，
        // 使 dialog 回到原位，不再出现「顶出屏幕找不回」。
        popupWindow.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setOnDismissListener(() -> {
            // 对话框隐藏后恢复决斗场阶段按钮
            if (context instanceof YGOProActivity) {
                ((YGOProActivity) context).notifyGameDialogHidden(this);
            }
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "card_select");
        int dialogWidth = resolveDialogWidth();
        applyCardImageSize(dialogWidth);
        draggableHelper.setupDraggablePopup(popupWindow, root,
                dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        // 宽度按 layout_game_right 区域实时解算且据此烘焙每张卡图，通用重排无法同步子视图尺寸：
        // 注册自定义旋转重排，切换方向时重解区域宽、重烘焙卡图、重设弹窗宽并重新居中
        draggableHelper.registerOrientationRelayout(popupWindow, this::applyOrientationRelayout);
        // 收缩模式：点内容左上角「▼」把手缩为与聊天输入框同高的底部横条，确认场地后点横条恢复
        DraggablePopupHelper.enableCollapse(popupWindow, tvTitle, buildTitleText());

        // 直接以内容根构建，包装层由 findTipOverlay 向上解析
        tipHelper = new CardStatusTipHelper(context, root);
        tipHelper.setOnCardPressedListener(this::showDetailAtSlot);
        tipHelper.setOnSlotClickListener(this::onSlotClicked);
        tipHelper.setTipProvider(this::statusTextAtSlot);
        for (int i = 0; i < SLOT_COUNT; i++) {
            // 触摸面与按下动画均落在卡片图上（"按下卡片图"直接命中）
            tipHelper.attach(i, ivCards[i], ivCards[i]);
        }
        sbPage.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int delta = progress - pageOffset;
                    pageOffset = progress;
                    refreshSlots();
                    animateSlotsShift(delta);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        btnOk.setOnClickListener(v -> confirm());
        // 竖屏按系数放大标题/卡位文本/确定按钮字号（横屏 1.0 不变），与已放大的弹窗宽度、卡图同步
        applyContentScale();
    }

    /**
     * 对话框宽度 = layout_game_right 实际宽度 × 3/4；
     * 取不到时依次降级为窗口 decorView 宽度、屏幕宽度
     */
    protected int resolveDialogWidth() {
        int w = 0;
        if (context instanceof android.app.Activity) {
            android.app.Activity act = (android.app.Activity) context;
            if (!act.isFinishing() && !act.isDestroyed()) {
                View gameRight = act.findViewById(R.id.layout_game_right);
                if (gameRight != null) {
                    w = gameRight.getWidth();
                }
                if (w <= 0) {
                    w = act.getWindow().getDecorView().getWidth();
                }
            }
        }
        if (w <= 0) {
            w = context.getResources().getDisplayMetrics().widthPixels;
        }
        return Math.round(w * DIALOG_WIDTH_RATIO * contentScale());
    }

    /**
     * 按当前方向把标题/卡位文本/确定按钮字号回写为「基准 × 系数」的绝对值（系数见 {@link #contentScale()}）。
     * 首次调用捕获基准（此时视图仍为 XML 原始字号），此后绝对回写，旋转往复不叠乘。build() 与旋转重排均调用。
     */
    protected void applyContentScale() {
        if (tvTitle == null || tvPositions[0] == null || btnOk == null) return;
        if (baseTitlePx <= 0) {
            baseTitlePx = tvTitle.getTextSize();
            basePosPx = tvPositions[0].getTextSize();
            baseOkPx = btnOk.getTextSize();
        }
        float s = contentScale();
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTitlePx * s);
        for (TextView tv : tvPositions) {
            if (tv != null) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, basePosPx * s);
        }
        btnOk.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseOkPx * s);
    }

    /**
     * 按对话框宽度反推每张卡宽（扣除根内边距 16dp×2 与每槽水平外边距 {@link #SLOT_MARGIN_DP}dp×2），
     * 高度按 177:254 卡图比例计算，保证 ImageView 宽高与卡片比例一致；
     * 槽位为 wrap_content，固定宽度后空槽位 GONE 时其余槽位尺寸不变并整体居中
     */
    protected void applyCardImageSize(int dialogWidthPx) {
        int containerW = dialogWidthPx - 2 * dp2px(16);
        int cardW = (containerW - SLOT_COUNT * 2 * dp2px(SLOT_MARGIN_DP)) / SLOT_COUNT;
        if (cardW <= 0) return;
        slotStepPx = cardW + 2 * dp2px(SLOT_MARGIN_DP);
        int cardH = Math.round(cardW * CARD_ASPECT);
        for (ImageView iv : ivCards) {
            ViewGroup.LayoutParams lp = iv.getLayoutParams();
            if (lp != null) {
                lp.width = cardW;
                lp.height = cardH;
                iv.setLayoutParams(lp);
            }
        }
    }

    /**
     * 翻页丝滑过渡：拖动滚动条换卡重绑后，把整行按「翻页数×单卡槽步进」向滑动的反方向
     * 位移（钳制在 ±2 张内）再减速回零，使窗口内容视觉上随滑块连续滑入，消除瞬时换卡
     * 的僵硬感；连续快拖取消上一段动画从最新位移重启。Glide 内存缓存命中时卡图立即就位，
     * 动画期间不会看到占位图。
     */
    protected void animateSlotsShift(int delta) {
        if (delta == 0 || slotStepPx <= 0 || slotViews[0] == null
                || !(slotViews[0].getParent() instanceof View)) {
            return;
        }
        View row = (View) slotViews[0].getParent();
        float shift = Math.copySign(Math.min(Math.abs(delta), 2) * (float) slotStepPx, delta);
        row.animate().cancel();
        row.setTranslationX(shift);
        row.animate().translationX(0f).setDuration(160)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    /**
     * 屏幕旋转后重排本弹窗：宽度是 <code>layout_game_right</code> 区域宽度的函数，每张卡图
     * 尺寸又据此宽度烘焙，故须重新解算区域宽、重烘焙卡图、按当前屏宽限宽弹窗，最后在区域
     * 布局完成后重新居中；通用重排仅改弹窗宽不动卡图会致横竖屏切换后卡片被裁剪或显示过小。
     */
    protected void applyOrientationRelayout() {
        if (popupWindow == null) return;
        final View content = popupWindow.getContentView();
        if (!(content instanceof ViewGroup) || ((ViewGroup) content).getChildCount() == 0) return;
        final View inner = ((ViewGroup) content).getChildAt(0);
        final android.app.Activity act = context instanceof android.app.Activity
                ? (android.app.Activity) context : null;
        final View region = act != null ? act.findViewById(R.id.layout_game_right) : null;
        Runnable apply = () -> {
            int width = resolveDialogWidth();
            applyCardImageSize(width);
            // 旋转后按新方向系数回写字号（绝对回写不叠乘），使文本随卡图/弹窗宽同步缩放
            applyContentScale();
            ViewGroup.LayoutParams raw = inner.getLayoutParams();
            if (raw instanceof android.widget.FrameLayout.LayoutParams) {
                android.widget.FrameLayout.LayoutParams flp =
                        (android.widget.FrameLayout.LayoutParams) raw;
                int[] fitted = DraggablePopupHelper.fitSizeToScreen(context, width,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                flp.width = fitted[0];
                inner.setLayoutParams(flp);
            }
            inner.requestLayout();
            if (region != null) {
                DraggablePopupHelper.centerPopupInRegion(popupWindow, region);
            }
        };
        // 旋转后区域宽高尚未更新，post 一帧让新视图树完成 measure/layout 再按实际区域宽重解
        if (region != null) region.post(apply);
        else inner.post(apply);
    }

    /**
     * 标题为单段文本「hint(进度)」：括号内进度随每次选择刷新，不再在标题后单独拼接「已选: x/y」后缀
     *（对齐 gframe 把数量并入标题文本本身的做法：duelclient.cpp L1968 选卡 hint(min-max)、
     * client_field.cpp L1109 合计 hint(cur/target) 均整段 setText）。
     */
    protected void refreshTitle() {
        if (tvTitle == null) return;
        String text = buildTitleText();
        tvTitle.setText(text);
        // 收缩横条与弹窗内标题同源，选择进度变化时同步文案
        DraggablePopupHelper.setCollapseBarTitle(popupWindow, text);
    }

    /** 完整标题文本：基础提示语 + 括号内实时进度 */
    protected String buildTitleText() {
        return title + "(" + titleProgress() + ")";
    }

    /** 括号内进度：选卡/反选=已选/上限，排序=点击序/总数，合计=当前值/目标值 */
    protected String titleProgress() {
        switch (mode) {
            case MODE_SORT:
                return sortCounter + "/" + cards.size();
            case MODE_SUM:
                return sumProgress();
            default:
                return getSelectedCount() + "/" + maxSelect;
        }
    }

    protected void onSlotClicked(int slot) {
        int displayIdx = pageOffset + slot;
        int index = displayToProto(displayIdx);
        if (index < 0) return;
        switch (mode) {
            case MODE_UNSELECT: {
                selected[index] = !selected[index];
                updateSlotView(slot, displayIdx);
                if (listener != null) listener.onCardClicked(index);
                dismiss();
                break;
            }
            case MODE_SORT: {
                if (sortList[index] != 0) {
                    int sel = sortList[index];
                    sortList[index] = 0;
                    sortCounter--;
                    for (int i = 0; i < sortList.length; i++) {
                        if (sortList[i] > sel) sortList[i]--;
                    }
                    updateSlotView(slot, displayIdx);
                } else {
                    sortCounter++;
                    sortList[index] = sortCounter;
                    updateSlotView(slot, displayIdx);
                    if (sortCounter == sortList.length) {
                        int[] respBuf = new int[sortList.length];
                        for (int i = 0; i < sortList.length; i++) {
                            respBuf[i] = sortList[i] - 1;
                        }
                        if (listener != null) listener.onSorted(respBuf);
                        dismiss();
                        return;
                    }
                }
                refreshTitle();
                break;
            }
            case MODE_SUM: {
                if (index < mustCards.size()) {
                    Toast.makeText(context, "必选卡不可取消选择", Toast.LENGTH_SHORT).show();
                    return;
                }
                int realIdx = index - mustCards.size();
                if (selected[realIdx]) {
                    selected[realIdx] = false;
                    clickOrder.remove((Integer) realIdx);
                } else {
                    selected[realIdx] = true;
                    clickOrder.add(realIdx);
                }
                updateSlotView(slot, displayIdx);
                if (listener != null) listener.onCardClicked(index);
                updateSumState();
                break;
            }
            default: {
                if (selected[index]) {
                    selected[index] = false;
                    clickOrder.remove((Integer) index);
                } else {
                    selected[index] = true;
                    clickOrder.add(index);
                }
                updateSlotView(slot, displayIdx);
                if (listener != null) listener.onCardClicked(index);
                int sel = getSelectedCount();
                refreshTitle();
                if (sel >= maxSelect) {
                    confirm();
                } else {
                    btnOk.setVisibility(sel >= minSelect ? View.VISIBLE : View.INVISIBLE);
                }
                break;
            }
        }
    }

    protected void updateSumState() {
        int optCount = 0;
        for (boolean b : selected) {
            if (b) optCount++;
        }
        boolean countOk = optCount >= minSelect && optCount <= maxSelect;
        boolean sumOk = checkSumValue();
        boolean ready = countOk && sumOk;
        refreshTitle();
        if (ready && optCount >= maxSelect) {
            confirm();
        } else {
            btnOk.setVisibility(ready ? View.VISIBLE : View.INVISIBLE);
        }
    }

    protected void refreshSlots() {
        if (tipHelper != null) tipHelper.hide();
        int displayCount = getDisplayCount();
        if (displayCount > SLOT_COUNT) {
            sbPage.setMax(displayCount - SLOT_COUNT);
            sbPage.setVisibility(View.VISIBLE);
        } else {
            sbPage.setMax(0);
            sbPage.setProgress(0);
            sbPage.setVisibility(View.GONE);
        }
        if (pageOffset > sbPage.getMax()) {
            pageOffset = sbPage.getMax();
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            int index = pageOffset + i;
            if (index < displayCount) {
                slotViews[i].setVisibility(View.VISIBLE);
                updateSlotView(i, index);
            } else {
                slotViews[i].setVisibility(View.GONE);
            }
        }
    }

    protected void updateSlotView(int slot, int displayIdx) {
        CardItem item = getItemAt(displayIdx);
        if (item == null) return;
        boolean isSelected = isSelectedAt(displayIdx);
        if (mode == MODE_SORT) {
            // client_field.cpp ShowSelectCard MSG_SORT_CARD 分支：标签只显示排序数字或空文本
            // sortList 以协议序索引存储，显示序需经 displayToProto 换算
            int proto = displayToProto(displayIdx);
            tvPositions[slot].setText(proto >= 0 && sortList[proto] != 0
                    ? String.valueOf(sortList[proto]) : "");
            tvPositions[slot].setBackgroundColor(COLOR_DEFAULT);
        } else {
            tvPositions[slot].setText(labelText(item));
            if (isSelected) {
                tvPositions[slot].setBackgroundColor(COLOR_SELECTED);
            } else if (localPlayer >= 0 && item.controler != localPlayer) {
                tvPositions[slot].setBackgroundColor(COLOR_OPPONENT);
            } else {
                tvPositions[slot].setBackgroundColor(COLOR_DEFAULT);
            }
        }
        // code 可能带 0x80000000 翻面标志位，掩码后再使用（对齐 duelclient.cpp code & 0x7fffffff）
        int code = item.code & 0x7fffffff;
        if (code > 0) {
            imageLoader.bindImage(ivCards[slot], code, ImageLoader.Type.small);
        } else {
            // client_field.cpp ShowSelectCard：code==0 显示按控制者区分的卡背
            boolean opponent = localPlayer >= 0 && item.controler != localPlayer;
            Bitmap cover = getCoverBitmap(opponent);
            if (cover != null) {
                ivCards[slot].setImageBitmap(cover);
            } else {
                ivCards[slot].setImageResource(R.drawable.unknown);
            }
        }
    }
}
