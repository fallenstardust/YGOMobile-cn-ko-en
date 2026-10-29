package cn.garymb.ygomobile.ui.dialogs.duel;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.util.SparseArray;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.render.card.CardDetailPanel;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.DialogScale;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;


/**
 * Public entry of the split inheritance chain (YesOrNoDialogBase <- this).
 * Keeps the original class name and public API: the fluent builder setters
 * (they return this very type), the content build() layout assembly,
 * show()/show(anchor) presentation, the solo-mode ask hint and every
 * static query factory driven by the duel protocol messages.
 */
public class YesOrNoDialog extends YesOrNoDialogBase {

    public YesOrNoDialog(Context context) {
        super(context);
    }

    public YesOrNoDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    /** 在标题首行之前追加一行（纯单人询问提示）；原标题为空时直接作为标题 */
    public YesOrNoDialog prependTitle(String prefix) {
        if (prefix == null || prefix.isEmpty()) return this;
        this.title = (this.title == null || this.title.isEmpty())
                ? prefix : prefix + "\n" + this.title;
        return this;
    }

    /** 设置标题文字颜色（如纯单人询问按被询问方着色）；0 表示沿用布局默认 */
    public YesOrNoDialog setTitleTextColor(int color) {
        this.titleColor = color;
        return this;
    }

    public YesOrNoDialog setMessage(CharSequence message) {
        this.message = message;
        return this;
    }

    /**
     * 设置消息文本，并将其中的卡名按卡片类型着色（怪兽=黄、魔法=淡绿、陷阱=淡粉）。
     * message 为已把 [%ls] 替换成卡名后的完整文本；cardName 为其中要着色的卡名子串；
     * code 用于查询卡片类型。非卡片或无法识别类型时按普通文本显示。
     */
    public YesOrNoDialog setMessageWithCardName(String message, int code, String cardName) {
        this.message = colorizeCardName(message, code, cardName);
        return this;
    }

    /** 设置要显示的卡片代码，当消息包含该卡名称时会在左侧显示卡片图片 */
    public YesOrNoDialog setCardCode(int code) {
        this.cardCode = code;
        return this;
    }

    /** 设置可发动的链式卡片列表（用于连锁询问对话框） */
    public YesOrNoDialog setChainCards(List<CardChainItem> chainCards) {
        this.chainCards = chainCards != null ? new ArrayList<>(chainCards) : null;
        return this;
    }

    /** 设置消息文本背景色（如删除确认用 colorNavy 高亮）；0 表示不设背景 */
    public YesOrNoDialog setMessageBackgroundColor(int color) {
        this.messageBgColor = color;
        return this;
    }

    /** 设置消息文本对齐方式（如 Gravity.CENTER）；-1 表示沿用布局默认 */
    public YesOrNoDialog setMessageGravity(int gravity) {
        this.messageGravity = gravity;
        return this;
    }

    public YesOrNoDialog setType(int type) {
        this.type = type;
        return this;
    }

    public YesOrNoDialog setPositiveButtonText(String text) {
        this.positiveText = text;
        return this;
    }

    public YesOrNoDialog setNegativeButtonText(String text) {
        this.negativeText = text;
        return this;
    }

    public YesOrNoDialog setPositiveButton(View.OnClickListener listener) {
        this.positiveListener = listener;
        return this;
    }

    public YesOrNoDialog setNegativeButton(View.OnClickListener listener) {
        this.negativeListener = listener;
        return this;
    }

    public YesOrNoDialog setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    public YesOrNoDialog setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    public YesOrNoDialog setContentView(View view) {
        this.customContentView = view;
        return this;
    }

    public YesOrNoDialog setContentView(int layoutId) {
        this.customContentView = LayoutInflater.from(DialogScale.wrap(context)).inflate(layoutId, null);
        return this;
    }

    public YesOrNoDialog setSoftInputMode(int mode) {
        this.softInputMode = mode;
        return this;
    }

    /** 弹窗按指定区域的宽高居中显示（如 layout_game_right），而非整个 Activity 窗口 */
    public YesOrNoDialog setCenterInView(View region) {
        this.centerInView = region;
        return this;
    }

    /** 列表选项内容容器（dialog_game_select 的 layout_options），由 setOptionsContentView() 初始化 */
    protected LinearLayout optionsContainer;

    /**
     * 采用“列表选择”内容视图（复用 dialog_game_select 布局）：隐藏其标题/提示/底部按钮，
     * 仅保留 layout_options 选项容器；随后通过 {@link #addOption} 逐项添加按钮。
     */
    public YesOrNoDialog setOptionsContentView() {
        View content = LayoutInflater.from(DialogScale.wrap(context)).inflate(R.layout.dialog_game_select, null);
        content.findViewById(R.id.tv_select_title).setVisibility(View.GONE);
        content.findViewById(R.id.tv_select_hint).setVisibility(View.GONE);
        content.findViewById(R.id.layout_select_buttons).setVisibility(View.GONE);
        optionsContainer = content.findViewById(R.id.layout_options);
        return setContentView(content);
    }

    /**
     * 向列表内容视图追加一个选项按钮（统一样式：白色文字、底部间距 4px）。
     * 点击后先执行 onClick 再自动关闭对话框（对齐原 ShowDialogUtil 各列表项行为）。
     */
    public YesOrNoDialog addOption(CharSequence text, int backgroundColor, Runnable onClick) {
        if (optionsContainer == null) setOptionsContentView();
        Button btn = new Button(DialogScale.wrap(context));
        btn.setText(text);
        btn.setTextColor(YGOUtil.c(R.color.white));
        btn.setBackgroundColor(backgroundColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 4;
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
            dismiss();
        });
        optionsContainer.addView(btn);
        return this;
    }

    /** 预设为"是/否询问"样式（对齐 gframe wQuery）：TYPE_YES_NO + 按钮"是"/"否" + 不可取消。 */
    public YesOrNoDialog asYesNo() {
        return setType(TYPE_YES_NO)
                .setPositiveButtonText(sysText(1213, "是"))
                .setNegativeButtonText(sysText(1214, "否"))
                .setCancelable(false);
    }
    
    /** 是否启用左右互换「是/否」按钮位置，启用后左边显示"否"右边显示"是"（用户规格）*/
    public YesOrNoDialog setSwapButtonPositions(boolean swap) {
        this.swapButtonPositions = swap;
        return this;
    }
    protected void build() {
        float density = context.getResources().getDisplayMetrics().density * DialogScale.factor(context);
        int dialogWidth = (int) (280 * density);

        LinearLayout root = (LinearLayout) LayoutInflater.from(DialogScale.wrap(context))
                .inflate(R.layout.dialog_yes_or_no, null);

        TextView tvTitle = root.findViewById(R.id.tv_yes_no_title);
        ScrollView scrollView = root.findViewById(R.id.yes_no_scroll);
        TextView tvMessage = root.findViewById(R.id.tv_yes_no_message);
        LinearLayout buttonArea = root.findViewById(R.id.yes_no_button_area);
        Button btnPositive = root.findViewById(R.id.btn_yes_no_positive);
        Button btnNegative = root.findViewById(R.id.btn_yes_no_negative);

        // 卡图堆栈（dialog_yes_or_no 结构：ll_yes_no_card_stack 直接作为消息行左侧子视图，
        // 首张 iv_yes_no_card_image 常驻、其余按 message 中的卡名动态叠压追加）
        LinearLayout contentRow = (LinearLayout) scrollView.getParent();
        LinearLayout cardStack = root.findViewById(R.id.ll_yes_no_card_stack);

        // 连锁卡片横向列表（ll_chain_card_list 位于 hsv_yes_no_chain_cards 内，同行消息区右侧）
        HorizontalScrollView chainCardsScrollView = root.findViewById(R.id.hsv_yes_no_chain_cards);
        LinearLayout chainCardList = root.findViewById(R.id.ll_chain_card_list);

        if (title != null && !title.isEmpty()) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(colorizeBracketedCardNames(title));
            if (titleColor != 0) tvTitle.setTextColor(titleColor);
        } else {
            tvTitle.setVisibility(View.GONE);
        }

        // ── Content area ─────────────────────────────────────────
        boolean hasChainCards = chainCards != null && !chainCards.isEmpty();
        // 左侧卡图（用户规格）：message 中出现多个卡名时全部出图，横向排列且每张压住前一张
        // 的 1/3，之后才显示消息文本；无卡名时整个堆栈隐藏。攻击宣言/直接攻击等时点没有
        // MSG_CHAINING（cardCode=0），堆栈内按文本里的 [XXX] 卡名逐个回退解析，故左侧仍出图。
        buildMentionedCardStack(cardStack);

        tvMessage.setText(colorizeBracketedCardNames(message));
        if (messageBgColor != 0) {
            tvMessage.setBackgroundColor(messageBgColor);
        }
        if (messageGravity != -1) {
            tvMessage.setGravity(messageGravity);
        }

        // ── Chain cards list area ─────────────────────────────────
        if (hasChainCards) {
            chainCardsScrollView.setVisibility(View.VISIBLE);
            chainCardList.removeAllViews();

            ImageLoader chainImageLoader = new ImageLoader();
            // 同一张卡（同 code+控制者+区域+序列）有多个可发动效果时只显示 1 个图，不重复
            Set<String> seenChainCards = new HashSet<>();
            for (CardChainItem item : chainCards) {
                String cardKey = item.code + "/" + item.controler + "/" + item.location + "/" + item.sequence;
                if (!seenChainCards.add(cardKey)) continue;

                View cardItemView = LayoutInflater.from(DialogScale.wrap(context))
                        .inflate(R.layout.item_chain_card, chainCardList, false);

                ImageView ivCard = cardItemView.findViewById(R.id.iv_chain_card_image);
                TextView tvPos = cardItemView.findViewById(R.id.tv_chain_card_position);

                chainImageLoader.bindImage(ivCard, item.code, ImageLoader.Type.middle);
                tvPos.setText(getLocationText(item.location, item.sequence));
                bindCardDetailClick(cardItemView, item.code);

                chainCardList.addView(cardItemView);
            }
            // 连锁列表独立成一行：放在内容区（消息行所在纵向 LinearLayout）下方、按钮区上方全宽展示
            LinearLayout contentBlock = (LinearLayout) contentRow.getParent();
            if (chainCardsScrollView.getParent() == contentRow) {
                contentRow.removeView(chainCardsScrollView);
            }
            if (chainCardsScrollView.getParent() != contentBlock) {
                LinearLayout.LayoutParams chainLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                chainLp.topMargin = (int) (4 * density);
                contentBlock.addView(chainCardsScrollView, chainLp);
            }
        } else {
            chainCardsScrollView.setVisibility(View.GONE);
        }

        // ── Button area ──────────────────────────────────────────
        boolean needsButtons = customContentView == null || positiveListener != null
                || negativeListener != null || type == TYPE_YES_NO;
        if (!needsButtons) {
            buttonArea.setVisibility(View.GONE);
        } else {
            btnPositive.setText(positiveText);
            btnPositive.setTextColor(YGOUtil.c(R.color.neon_green));
            btnNegative.setText(negativeText);
            if (type == TYPE_YES_NO) {
                btnNegative.setVisibility(View.VISIBLE);
            } else {
                btnNegative.setVisibility(View.GONE);
                LinearLayout.LayoutParams positiveLp =
                        (LinearLayout.LayoutParams) btnPositive.getLayoutParams();
                positiveLp.setMarginEnd(0);
            }
            
            // 交换正负按钮位置：按 setSwapButtonPositions 设置；
            // 用户规格——启用后左「否」右「是」；点击事件仍绑定在原按钮对象上，
            // btnPositive→positiveListener（「是」语义）、btnNegative→negativeListener（「否」语义）不变。
            if (swapButtonPositions && type == TYPE_YES_NO) {
                LinearLayout.LayoutParams lpPositive = (LinearLayout.LayoutParams) btnPositive.getLayoutParams();
                LinearLayout.LayoutParams lpNegative = (LinearLayout.LayoutParams) btnNegative.getLayoutParams();
                // 等宽：两个按钮各占一半（此前 XML 已设 weight=1，此处再兜底防止外部改动）
                lpPositive.weight = 1.0f;
                lpNegative.weight = 1.0f;
                // 20dp 间隙留给「位于左边的按钮」的 marginEnd——swap 后左边是 btnNegative
                int twentyDp = (int) (density * 20 + 0.5f);
                lpPositive.setMarginEnd(0);
                lpNegative.setMarginEnd(twentyDp);
                // 真正交换父 LinearLayout 中的物理子 View 顺序：
                // XML 默认 btnPositive(index=0) 左、btnNegative(index=1) 右；
                // swap 后需要 btnNegative 在左、btnPositive 在右，仅调 marginEnd/weight 不生效
                buttonArea.removeView(btnPositive);
                buttonArea.addView(btnPositive, buttonArea.getChildCount());
            }
            
            btnPositive.setOnClickListener(v -> {
                if (positiveListener != null) positiveListener.onClick(v);
                dismiss();
            });
            btnNegative.setOnClickListener(v -> {
                if (negativeListener != null) negativeListener.onClick(v);
                dismiss();
            });
        }

        contentView = root;

        // ── PopupWindow setup ────────────────────────────────────
        popupWindow = new PopupWindow(contentView, dialogWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // 始终非触摸模态：弹窗窗口仅包裹内容大小，若随 cancelable 置为模态
        // （连锁询问窗 asYesNo 即 cancelable=false）会吞掉全屏所有触摸，导致询问期间
        // 点击场上/手卡/墓地/除外/额外无法显示详情与查看列表；窗外点击产生的
        // ACTION_OUTSIDE 由下方 setTouchInterceptor 消费，不会因本改动误关不可取消弹窗
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setSoftInputMode(softInputMode);
        popupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) return true;
            return false;
        });
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(() -> {
            sShowingDialogs.remove(this);
            // EFFECT_YN 询问窗关闭（应答/被新询问接力关闭/决斗结束）：摸掉本窗置入的
            // 卡角呼吸绿点引用，绿点与询问同存续
            clearAskedEffectCard();
            // 对话框隐藏后恢复决斗场阶段按钮
            if (context instanceof YGOProActivity) {
                ((YGOProActivity) context).notifyGameDialogHidden(this);
            }
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "game_dialog_" + title);
        draggableHelper.setupDraggablePopup(popupWindow, root, dialogWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        // 收缩模式：有标题时把手贴标题左侧、横条用标题；无标题（tvTitle 已 GONE）时把手
        // 维持左上角悬浮、横条改显示 message 文本，恢复原 dialog 后 message 回内容区、横条消失
        String collapseBarTitle = (title != null && !title.isEmpty())
                ? title : (message != null ? message.toString() : "");
        DraggablePopupHelper.enableCollapse(popupWindow, tvTitle, collapseBarTitle);
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
                // 未显式指定居中区域的路径（卡组/计数器选择等）回退到 layout_game_right，
                // 保证竖屏下弹窗居中在决斗场区域而非整屏（用户规格）
                View centerRegion = centerInView;
                if (centerRegion == null && context instanceof YGOProActivity) {
                    centerRegion = ((YGOProActivity) context).findViewById(R.id.layout_game_right);
                }
                DraggablePopupHelper.centerPopupInRegion(popupWindow, centerRegion);
                if (draggableHelper != null) {
                    draggableHelper.showPopup(popupWindow, anchor);
                } else {
                    popupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
                }
                // 输入法抬升联动（用户规格）：抬键盘时本弹窗底边贴聊天输入框上沿，
                // 收起后回到 layout_game_right 中央，逻辑统一在 ImePopupAdjuster
                ImePopupAdjuster.attach(popupWindow);
                // 对齐 C++ SoundManager::PlayDialogSound：询问/确认类（wQuery/wSurrender/
                // wOptions/wAN* 等）弹 question 音效，纯消息类（wMessage）弹 info 音效
                if (context instanceof YGOProActivity) {
                    cn.garymb.ygomobile.audio.SoundManager sm =
                            ((YGOProActivity) context).getSoundManager();
                    if (sm != null) {
                        sm.playSoundEffect(type == TYPE_YES_NO
                                ? cn.garymb.ygomobile.audio.SoundManager.SFX.QUESTION
                                : cn.garymb.ygomobile.audio.SoundManager.SFX.INFO);
                    }
                }
                if (popupWindow.isShowing()) {
                    // 显示成功后登记进「正在显示」集合（PopupWindow.dismiss 回调注销）：
                    // 询问工厂弹新窗前据此关闭旧窗保证同屏至多一个；撤回点击据此整体隐藏、被拒时恢复
                    sShowingDialogs.add(this);
                }
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
    /**
     * 纯单人模式（{@link GameEngine#soloMode}）专用：为询问窗标题首行加「询问-玩家名」提示，
     * 并按被询问方着色（我方 holo_blue_bright、对方 holo_orange_bright）。
     * <p>
     * selectingPlayer 为协议首字节 selecting_player（发出询问时点明的 seat），
     * 经 {@link GameEngine#localPlayer(int)} 折算到本地视角（0=我方、1=对方）后取名。
     * 联机 / 回放 / 残局不进入本方法；selectingPlayer &lt; 0（报文残缺、无从判定被询问方）
     * 时同样不加提示，避免把询问错标到另一方。
     */
    protected static void applySoloAskHint(YGOProActivity activity, YesOrNoDialog dialog, int selectingPlayer) {
        GameEngine engine = activity.getEngine();
        if (engine == null || !engine.soloMode || selectingPlayer < 0) return;
        int local = engine.localPlayer(selectingPlayer & 1);
        String name = engine.displayName(local);
        // 席位名尚未回显（未选卡组且昵称空）时回落到我方/对方，保证提示不缺失
        if (name == null || name.isEmpty()) name = local == 0 ? "我方" : "对方";
        dialog.prependTitle("询问-" + name)
                .setTitleTextColor(YGOUtil.c(local == 0
                        ? R.color.holo_blue_bright : R.color.holo_orange_bright));
    }
    /** 是/否确认弹窗（MSG_SELECT_YESNO / MSG_SELECT_EFFECTYN）：是=1 否=0 */
    public static YesOrNoDialog showYesNoQuery(YGOProActivity activity, String message) {
        return showYesNoQuery(activity, message, 0, null, -1);
    }

    /** 是/否确认弹窗（可带卡名着色）：cardCode>0 且 cardName 非空时按卡片类型着色 */
    public static YesOrNoDialog showYesNoQuery(YGOProActivity activity, String message, int cardCode, String cardName) {
        return showYesNoQuery(activity, message, cardCode, cardName, -1);
    }

    /**
     * 是/否确认弹窗：selectingPlayer 为协议 selecting_player（被询问 seat，0/1），
     * 仅纯单人模式据此加「询问-玩家名」标题提示；传 -1 表示不加。
     * 返回建好的窗实例供 EFFECT_YN 工厂追加被询问卡登记（setAskedEffectCard）。
     */
    public static YesOrNoDialog showYesNoQuery(YGOProActivity activity, String message, int cardCode,
                                      String cardName, int selectingPlayer) {
        // 单窗询问：弹新询问窗前关闭仍在显示的全部旧询问窗（含上一条连锁询问的残留窗），
        // 保证同屏至多一个询问，纯单人模式按协议顺序先当前回合玩家、后对方逐个询问
        dismissShowingDialogs();
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        if (cardCode > 0 && cardName != null && !cardName.isEmpty()) {
            dialog.setMessageWithCardName(message, cardCode, cardName);
        } else {
            dialog.setMessage(message);
        }
        // 读取 swapButtonPositions 设置：启用时左边"否"右边"是"
        boolean swapPos = AppsSettings.get().getIntSettings("swapButtonPositions", 0) == 1;
        dialog.setCardCode(cardCode)
                .asYesNo()
                .setSwapButtonPositions(swapPos)
                .setPositiveButton(v -> activity.sendResponseInt(1))
                .setNegativeButton(v -> activity.sendResponseInt(0))
                .setCenterInView(gameDialogRegion(activity))
                .setOnDismissListener(() -> panel(activity).setCurrentDialog(null));
        // 收起早前选择会话可能残留可见的 cancelOrFinish：是/否询问期间不参与，
        // 也不留一个点击后无法正确代答的旧按钮（旧代码在此弹「否」字按钮属残留交互）
        panel(activity).hideCancelOrFinishButton();
        applySoloAskHint(activity, dialog, selectingPlayer);
        dialog.show();
        return dialog;
    }

    /**
     * MSG_SELECT_YESNO 静态工厂（供 ShowDialogUtil.showYesNoDialog 委托调用）：
     * duelclient.cpp L1902-1910 player(1) + desc(4)，desc 走 GetDesc（系统字符串或卡片脚本提示文字），
     * 再交既有 showYesNoQuery 弹出是/否确认窗（是=1 否=0）。
     */
    public static void showYesNoDialog(YGOProActivity activity, ByteBuffer data) {
        int descId = 0;
        int selectingPlayer = -1;
        if (data != null && data.remaining() >= 5) {
            selectingPlayer = data.get() & 0xFF; // selecting_player
            descId = data.getInt();
        }
        showYesNoQuery(activity, DataManager.get().getDesc(descId, "是否发动效果？"), 0, null, selectingPlayer);
    }

    /**
     * 对齐 duelclient.cpp L49：select_effectyn_id{95,96,97,218,219,220}。
     * 这些 desc 对应的系统字符串各含单个 %ls，用卡名替换。
     */
    protected static final Set<Integer> SELECT_EFFECTYN_ID =
            new HashSet<>(Arrays.asList(95, 96, 97, 218, 219, 220));

    /**
     * MSG_SELECT_EFFECTYN：按 desc 组装「是否发动效果」询问文本，再走是/否确认弹窗。
     * 由 ShowDialogUtil.showEffectYnDialog 委托调用（YGOProActivity selectType==12）。
     */
    public static void showEffectYnDialog(YGOProActivity activity, ByteBuffer data) {
        // duelclient.cpp L1868-1895：player(1) code(4) c(1) l(1) s(1) flag(1) desc(4)
        if (data == null || data.remaining() < 13) {
            showYesNoQuery(activity, sysText(94, "是否现在使用这张卡的效果？"));
            return;
        }
        int selectingPlayer = data.get() & 0xFF; // selecting_player
        int code = data.getInt();
        int controller = data.get() & 0xFF;      // c（发动卡控制者，协议 seat）
        int location = data.get() & 0xFF;  // l
        int sequence = data.get() & 0xFF;  // s
        data.get();                        // flag
        int desc = data.getInt();
        String cardName = activity.getCardDisplayName(code);
        DataManager dm = DataManager.get();
        String locationName = dm.formatLocation(location, sequence);
        String message;
        if (desc == 0) {
            // duelclient.cpp MSG_SELECT_EFFECTYN L1886-1889：desc==0 → textBuffer = event_string + "\n" + sys200。
            // event_string 即最近事件的时点文案（召唤之际=sys1603「[%ls] 召唤中」、召唤成功=sys1604「怪兽召唤成功」、
            // 反转召唤成功=sys1608、特殊召唤成功=sys1606…），故询问窗首行按通讯具体显示对应时点，再拼接「是否在[..] 发动[..] 的效果？」
            message = prependChainEvent(activity,
                    sysFormat(200, "是否在[%s] 发动 [%s] 的效果？", locationName, cardName));
        } else if (desc == 221) {
            // duelclient.cpp MSG_SELECT_EFFECTYN L1890-1893：desc==221 → event_string + "\n" + sys221 + "\n" + sys223
            message = prependChainEvent(activity,
                    sysFormat(221, "是否在 [%s] 发动 [%ls] 的诱发类效果？", locationName, cardName)
                            + "\n" + sysText(223, "稍后将询问其他可以发动的效果。"));
        } else if (SELECT_EFFECTYN_ID.contains(desc)) {
            // sys95/96/97/218/219/220：单个 %ls 填卡名
            message = sysFormat(desc, "是否使用 [%s] 的效果？", cardName);
        } else {
            // 其余 desc 走 GetDesc；C++ 用 L"%ls" 打印，即把结果当数据而非格式串，故这里不做替换
            String raw = dm.getDesc(desc, "");
            message = raw.isEmpty() ? "是否发动「" + cardName + "」的效果？" : raw;
        }
        showYesNoQuery(activity, message, code, cardName, selectingPlayer)
                // 用户规格：诱发效果被询问是否发动时，被询问的卡角显示呼吸绿点，
                // 点该卡或点本窗卡图都出对应序号段落发光（登记段落文档序+留存归属坐标）
                .setAskedEffectCard(controller, code, location, sequence, desc);
    }

    /** SIDE 换卡组：列出卡组目录下的 .ydk 供选择，选中后交 ShowDialogUtil 载入并发送 */
    public static void showDeckSelectDialog(YGOProActivity activity) {
        File deckDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_DECK_PATH);
        File[] deckFiles = deckDir.exists()
                ? deckDir.listFiles((dir, name) -> name.endsWith(Constants.YDK_FILE_EX))
                : null;
        List<String> deckNames = new ArrayList<>();
        if (deckFiles != null && deckFiles.length > 0) {
            for (File f : deckFiles) deckNames.add(f.getName().replace(Constants.YDK_FILE_EX, ""));
        } else {
            deckNames.add("（暂无卡组文件）");
        }
        final File[] finalDeckFiles = deckFiles;
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("选择卡组").setOptionsContentView();
        for (int i = 0; i < deckNames.size(); i++) {
            final int pos = i;
            dialog.addOption(deckNames.get(i), YGOUtil.c(R.color.option_bg_blue), () -> {
                if (finalDeckFiles != null && pos < finalDeckFiles.length) {
                    activity.getDialogUtil().loadAndSendDeck(finalDeckFiles[pos]);
                }
            });
        }
        dialog.setType(TYPE_MESSAGE)
                .setPositiveButtonText("取消")
                .setPositiveButton(v -> activity.getEngine().disconnect())
                .setCancelable(false);
        dialog.show();
    }

    /**
     * 连锁是/否询问窗（对齐 gframe wQuery + stQMessage）：不再罗列可发动卡片按钮，改由场上高亮卡片承载点击发动。
     * 「是」进入场上点击模式，「否」放弃连锁；chainQueryDialog 保留引用以支持「取消操作」重新弹出。
     * cardCode>0 或询问文本（event_string）含卡名时，消息行左侧 ll_yes_no_card_stack 按卡名
     * 出现顺序叠压显示全部卡图（每张压住前一张的 1/3）；
     * chainItems 非空时右侧 ll_chain_card_list 横向展示可以连锁的卡片。
     */
    public static void showChainQuery(YGOProActivity activity, String queryText,
                                      int cardCode, List<CardChainItem> chainItems, int selectingPlayer) {
        // 单窗询问：先关闭仍在显示的旧询问窗，再建新窗——服务端对连锁询问按席位串行下发
        // （当前回合玩家先答，答“否”后才下发对方的），此处保证两窗不叠加、按序逐个弹出
        dismissShowingDialogs();
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        chainQueryDialog = dialog;
        panel(activity).setCurrentDialog(dialog);
        dialog.setMessage(prependChainEvent(activity, queryText))
                .setCardCode(cardCode)
                .setChainCards(chainItems)
                .asYesNo()
                .setPositiveButton(v -> activity.getDialogUtil().enterChainFieldMode(true))
                .setNegativeButton(v -> activity.getDialogUtil().finishChainPass())
                .setCenterInView(gameDialogRegion(activity))
                .setOnDismissListener(() -> panel(activity).setCurrentDialog(null));
        applySoloAskHint(activity, dialog, selectingPlayer);
        dialog.show();
    }

    /**
     * panelmode（含 LOCATION_OVERLAY 连锁项）兜底：overlay 单元无法在场上单独点击，
     * 仍按列表对话框呈现全部可连锁项，点击项直接应答其索引（对齐 gframe ShowChainCard）。
     */
    public static void showChainListDialog(YGOProActivity activity, boolean contiExist, boolean selectTrigger,
                                           boolean chainForced, List<String> chainOptions, List<Integer> chainFlags,
                                           List<CardChainItem> chainItems, int selectingPlayer) {
        // 单窗询问：弹列表窗前关闭仍在显示的旧询问窗，避免与上一条询问的残留窗叠加
        dismissShowingDialogs();
        String title;
        if (chainForced) {
            title = sysText(contiExist ? 556 : 550,
                    contiExist ? "请选择要发动/处理的效果" : "请选择要发动的效果");
        } else if (selectTrigger) {
            title = prependChainEvent(activity,
                    sysText(222, "是否要发动诱发类效果？") + "\n" + sysText(223, "稍后将询问其他可以发动的效果。"));
        } else {
            title = prependChainEvent(activity, sysText(203, "是否要进行连锁？"));
        }
        final boolean forced = chainForced;
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        panel(activity).setCurrentDialog(dialog);
        dialog.setTitle(title).setOptionsContentView()
                .setChainCards(chainItems); // Add horizontal card list
        for (int i = 0; i < chainOptions.size(); i++) {
            final int idx = i;
            int bg = (chainFlags.get(i) & 0x100) != 0 ? YGOUtil.c(R.color.option_bg_red) : YGOUtil.c(R.color.option_bg_blue);
            dialog.addOption(chainOptions.get(i), bg, () -> {
                activity.getDialogUtil().clearChainSelect();
                panel(activity).setSelectType(-1);
                activity.sendResponseInt(idx);
            });
        }
        dialog.setType(TYPE_MESSAGE)
                .setPositiveButtonText(forced ? "" : sysText(204, "不连锁"))
                .setPositiveButton(v -> {
                    if (forced) return;
                    panel(activity).hideCancelOrFinishButton();
                    activity.getDialogUtil().clearChainSelect();
                    panel(activity).setSelectType(-1);
                    activity.sendResponseInt(-1);
                })
                .setCancelable(false)
                .setCenterInView(gameDialogRegion(activity))
                .setOnDismissListener(() -> {
                    panel(activity).hideCancelOrFinishButton();
                    panel(activity).setCurrentDialog(null);
                });
        if (!forced) panel(activity).showCancelOrFinishButton(sysText(1295, "取消操作"));
        applySoloAskHint(activity, dialog, selectingPlayer);
        dialog.show();
    }

    /**
     * MSG_SELECT_CHAIN 且 count == 0 时的询问（sys201 + sys202）：
     * 是 → 仅显示「取消操作」按钮；否 → 放弃连锁（应答 -1）。
     */
    public static void showChainEmptyQuery(YGOProActivity activity, int selectingPlayer) {
        // 单窗询问：先关闭仍在显示的旧询问窗再建新窗
        dismissShowingDialogs();
        chainForcedMode = false;
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        chainQueryDialog = dialog;
        panel(activity).setCurrentDialog(dialog);
        dialog.setMessage(sysText(201, "此时没有可以发动的效果") + "\n" + sysText(202, "是否要确认场上的情况？"))
                .asYesNo()
                .setPositiveButton(v -> {
                    CardDetailPanel pp = panel(activity);
                    if (pp != null) {
                        pp.setSelectType(16);
                        pp.showCancelOrFinishButton(sysText(1295, "取消操作"));
                    }
                })
                .setNegativeButton(v -> activity.getDialogUtil().finishChainPass())
                .setCenterInView(gameDialogRegion(activity))
                .setOnDismissListener(() -> panel(activity).setCurrentDialog(null));
        applySoloAskHint(activity, dialog, selectingPlayer);
        dialog.show();
    }

    /** MSG_SELECT_COUNTER：列出 1..counterCount 供选择取除总数（playerop.cpp L608-637） */
    public static void showCounterSelectDialog(YGOProActivity activity, ByteBuffer data) {
        if (data == null || data.remaining() < 6) {
            activity.sendResponseInt(0);
            return;
        }
        data.get();                                    // selecting_player
        int counterType = data.getShort() & 0xFFFF;
        int counterCount = data.getShort() & 0xFFFF;
        int cardCount = data.get() & 0xFF;
        final List<Integer> cardCounters = new ArrayList<>();
        for (int i = 0; i < cardCount && data.remaining() >= 9; i++) {
            data.getInt();
            data.get();
            data.get();
            data.get();
            cardCounters.add(data.getShort() & 0xFFFF);
        }
        if (cardCounters.isEmpty() || counterCount <= 0) {
            activity.sendResponseInt(0);
            return;
        }
        DataManager dm = DataManager.get();
        String title = dm.formatSystemString(204, "请取除%d个 [%s]", counterCount, dm.getCounterName(counterType));
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(title).setOptionsContentView();
        for (int i = 1; i <= counterCount; i++) {
            final int val = i;
            dialog.addOption(String.valueOf(i), YGOUtil.c(R.color.option_bg_blue), () -> sendCounterResponse(activity, cardCounters, val));
        }
        dialog.setCancelable(false).setCenterInView(gameDialogRegion(activity));
        dialog.show();
    }

}
