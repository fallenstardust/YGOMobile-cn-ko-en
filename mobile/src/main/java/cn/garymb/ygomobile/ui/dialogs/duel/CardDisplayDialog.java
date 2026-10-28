package cn.garymb.ygomobile.ui.dialogs.duel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;

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

public class CardDisplayDialog extends CardDisplayDialogBase {

    public CardDisplayDialog(Context context, ImageLoader imageLoader) {
        super(context, imageLoader);
    }

    /** 供外部（EngineCallbackDelegate.onFieldChanged / ReplayListener.onReplayFieldChanged）
     *  在主线程主动刷新所有带 supplier 的展示中弹窗；无 supplier 的弹窗自动从列表中摘除 */
    public static void refreshLiveDialogs() {
        for (int i = LIVE_DIALOGS.size() - 1; i >= 0; i--) {
            CardDisplayDialog d = LIVE_DIALOGS.get(i);
            if (d == null || d.liveSupplier == null || !d.isShowing()) {
                LIVE_DIALOGS.remove(i);
                continue;
            }
            d.replaceCards(d.liveSupplier.obtain());
        }
    }

    public CardDisplayDialog setTitle(String title) {
        this.titlePrefix = title;
        updateTitle();
        return this;
    }

    public CardDisplayDialog setCards(List<CardItem> cardList) {
        this.cards = new ArrayList<>();
        if (cardList != null) {
            // 显示顺序整体反序：传入序列的末位排在第一槽
            //（该列表多来自场上/手卡收集，反序后与决斗场视觉方向一致；
            // 点击回调传出 CardItem 对象、不携带列表下标，反序不影响任何协议语义）
            for (int i = cardList.size() - 1; i >= 0; i--) {
                this.cards.add(cardList.get(i));
            }
        }
        this.pageOffset = 0;
        updateTitle(); // 卡片数量变化时更新标题
        return this;
    }

    /** 我方协议玩家索引，用于区分对方卡标签背景色（对齐 SetCardListLabel） */
    public CardDisplayDialog setLocalPlayer(int localPlayer) {
        this.localPlayer = localPlayer;
        return this;
    }

    public CardDisplayDialog setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    public CardDisplayDialog setCardClickListener(OnCardClick listener) {
        this.cardClickListener = listener;
        return this;
    }

    /** 声明 CardItem.controler 的口径（true=协议侧，false=本地视角侧），影响详情与状态标签的取卡 */
    public CardDisplayDialog setControlerProtocolSide(boolean protocolSide) {
        this.controlerProtocolSide = protocolSide;
        return this;
    }

    /** 安装实时观察 supplier：弹窗展示期间，field 变化会触发重新拉取列表并就地刷新。
     *  仅 CmdMenuDialog 堆叠区查看列表入口使用（观战/录像），普通确认弹窗不安装。
     *  同时会在每次 pullFresh() 后自动更新标题中的卡片数量统计。
     */
    public CardDisplayDialog observeLive(CardItemSupplier supplier) {
        this.liveSupplier = supplier;
        return this;
    }

    /** 就地替换卡片列表（不重建 popup / 不重启动画）；保留当前页位（在新页范内时）。
     *  传入与 setCards 同语义的顺位列表，内部仍取反序。 */
    public void replaceCards(List<CardItem> newCards) {
        this.cards = new ArrayList<>();
        if (newCards != null) {
            for (int i = newCards.size() - 1; i >= 0; i--) this.cards.add(newCards.get(i));
        }
        int maxPage = Math.max(0, this.cards.size() - SLOT_COUNT);
        if (pageOffset > maxPage) pageOffset = maxPage;
        if (pageOffset < 0) pageOffset = 0;
        if (sbPage != null) {
            if (this.cards.size() > SLOT_COUNT) {
                sbPage.setMax(maxPage);
                sbPage.setVisibility(View.VISIBLE);
                sbPage.setProgress(pageOffset);
            } else {
                sbPage.setMax(0);
                sbPage.setProgress(0);
                sbPage.setVisibility(View.GONE);
            }
        }
        if (isShowing()) refreshSlots();
        // 实时更新标题中的卡片数量
        updateTitle();
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
            refreshSlots();
            try {
                // 默认居中于 layout_game_right 区域而非整个窗口（与 YesOrNoDialog 同模式，
                // 拖拽保存过位置时 showPopup 走 NO_GRAVITY 绝对坐标，不受此 margin 影响）
                if (context instanceof android.app.Activity) {
                    View region = ((android.app.Activity) context).findViewById(R.id.layout_game_right);
                    DraggablePopupHelper.centerPopupInRegion(popupWindow, region);
                }
                if (draggableHelper != null) {
                    draggableHelper.showPopup(popupWindow, anchor);
                } else {
                    popupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
                }
                // 输入法抬升联动（用户规格）：抬键盘时本弹窗底边贴聊天输入框上沿，
                // 收起后回到 layout_game_right 中央，逻辑统一在 ImePopupAdjuster
                ImePopupAdjuster.attach(popupWindow);
                // 对话框显示期间禁用决斗场阶段按钮
                if (context instanceof YGOProActivity) {
                    ((YGOProActivity) context).notifyGameDialogShown(this);
                }
                if (liveSupplier != null && !LIVE_DIALOGS.contains(this)) {
                    LIVE_DIALOGS.add(this);
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
}
