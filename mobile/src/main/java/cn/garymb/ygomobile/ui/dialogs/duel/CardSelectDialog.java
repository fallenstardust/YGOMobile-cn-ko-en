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

public class CardSelectDialog extends CardSelectDialogMid {

    public CardSelectDialog(Context context, ImageLoader imageLoader) {
        super(context, imageLoader);
    }

    public CardSelectDialog setMode(int mode) {
        this.mode = mode;
        return this;
    }

    public CardSelectDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    public CardSelectDialog setCards(List<CardItem> cardList) {
        this.cards = cardList != null ? cardList : new ArrayList<>();
        this.selected = new boolean[this.cards.size()];
        this.clickOrder.clear();
        this.sortList = new int[this.cards.size()];
        this.sortCounter = 0;
        this.displayOrder = new int[0];
        this.pageOffset = 0;
        return this;
    }

    public CardSelectDialog setMustCards(List<CardItem> mustList) {
        this.mustCards = mustList != null ? mustList : new ArrayList<>();
        return this;
    }

    public CardSelectDialog setPreSelected(boolean[] flags) {
        if (flags != null && flags.length == selected.length) {
            for (int i = 0; i < selected.length; i++) {
                selected[i] = flags[i];
            }
        }
        return this;
    }

    public CardSelectDialog setSelectRange(int min, int max) {
        this.minSelect = Math.max(0, min);
        this.maxSelect = Math.max(this.minSelect, max);
        return this;
    }

    public CardSelectDialog setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    public CardSelectDialog setFinishable(boolean finishable) {
        this.finishable = finishable;
        return this;
    }

    public CardSelectDialog setSumValue(int value, int sumMode) {
        this.sumValue = value;
        this.sumMode = sumMode;
        return this;
    }

    public CardSelectDialog setLocalPlayer(int localPlayer) {
        this.localPlayer = localPlayer;
        return this;
    }

    public CardSelectDialog setValueVisible(boolean showValues) {
        this.showValues = showValues;
        return this;
    }

    /** 声明 CardItem.controler 的口径（true=协议侧，false=本地视角侧），影响详情与状态标签的取卡 */
    public CardSelectDialog setControlerProtocolSide(boolean protocolSide) {
        this.controlerProtocolSide = protocolSide;
        return this;
    }

    public CardSelectDialog setListener(OnCardSelectListener listener) {
        this.listener = listener;
        return this;
    }

    public CardSelectDialog setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
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
            buildDisplayOrder();
            refreshSlots();
            switch (mode) {
                case MODE_SORT:
                    btnOk.setVisibility(View.INVISIBLE);
                    refreshTitle();
                    break;
                case MODE_UNSELECT:
                    btnOk.setVisibility(finishable ? View.VISIBLE : View.INVISIBLE);
                    if (finishable) {
                        btnOk.setText("完成");
                    }
                    refreshTitle();
                    break;
                case MODE_SUM:
                    updateSumState();
                    break;
                default:
                    int sel = getSelectedCount();
                    refreshTitle();
                    btnOk.setVisibility(sel >= minSelect ? View.VISIBLE : View.INVISIBLE);
                    break;
            }
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
                // 收起后回到 layout_game_right 中央，逻辑统一在 ImePopupAdjuster；
                // 已收缩为底部横条时工具类解算发现内容不可见会自动跳过
                ImePopupAdjuster.attach(popupWindow);
                // 对话框显示期间禁用决斗场阶段按钮
                if (context instanceof YGOProActivity) {
                    ((YGOProActivity) context).notifyGameDialogShown(this);
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
