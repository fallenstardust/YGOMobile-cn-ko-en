package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.Map;

import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardLocation;
import ocgcore.enums.CardType;

/**
 * 卡片列表弹窗（CardSelectDialog / CardDisplayDialog）的「按下查看详情、按住看通讯状态」辅助器。
 * <p>
 * 交互（对齐 event_handler.cpp L1110-1158 的 EGET_ELEMENT_HOVERED / LEFT 语义：
 * 悬停即显示卡片详情，同时按需显示状态标签，离开隐藏标签）：
 * - ACTION_DOWN：立即回调 onCardPressed（宿主据此在 CardDetailPanel 显示该卡详情），并启动 300ms 计时；
 * - 按住满 {@link #HOLD_THRESHOLD_MS}：在该卡下边缘下方悬浮显示通讯状态标签；
 * - ACTION_UP：仅当计时未触发（未超过 0.3 秒）才回调 onSlotClick 视为选中，
 * 「是否已触发」而非「标签是否显示」作为判据 —— 无状态文字的卡（如卡组卡）长按同样不视为点击；
 * - ACTION_CANCEL（含拖动弹窗时包装层补发的 CANCEL）：清定时器、隐藏标签、不触发选中。
 * <p>
 * 标签容器：不额外包裹内容层（FrameLayout 以 WRAP_CONTENT 测量时会把标签高度并入弹窗高度），
 * 而是把标签挂到 DraggablePopupHelper 的包装层（DragFrameLayout，铺满窗口且 clipChildren=false）
 * 作为第 2 个子 View —— 该层所有命中/居中判定只看 getChildAt(0)，故不影响弹窗行为与尺寸。
 * <p>
 * 标签文字逐行镜像 ClientField::ShowCardInfoInList（event_handler.cpp L2919-2964）。
 */
public class CardStatusTipHelper {

    /**
     * 按下超过该时长即视为「查看状态」，抬手不再触发选中
     */
    public static final long HOLD_THRESHOLD_MS = 300L;

    /**
     * 标签最大宽度：gframe SetStaticText(stCardListTip, 320 * xScale, ...)
     */
    private static final int TIP_MAX_WIDTH_DP = 320;
    /**
     * 标签背景：game.cpp L1409 stCardListTip 背景 0x6011113d（CARD_LIST_SELECTED_BACKGROUND_COLOR）
     */
    private static final int TIP_BACKGROUND = 0x6011113D;
    private static final float TIP_TEXT_SIZE_SP = 11f;
    /** 场上/手卡悬浮气泡字体：比列表内 cardtip 小一号 */
    private static final float FIELD_TIP_TEXT_SIZE_SP = TIP_TEXT_SIZE_SP - 1f;
    private static final int TIP_PADDING_DP = 4;
    /**
     * 左右留白：对应 gframe 钳制用的 10 * xScale
     */
    private static final int TIP_EDGE_MARGIN_DP = 4;
    private static final int TIP_ANCHOR_GAP_DP = 2;
    private static final int MAX_SLOTS = 16;

    // 按下动画（参考 CGUIButton::draw 按下偏移语义：无 PressedImage 时整图按
    // EGDS_BUTTON_PRESSED_IMAGE_OFFSET 下沉，形成"陷下去"的反馈）：
    // Android 侧用 轻微下沉 + 微缩放 + 变暗 近似，抬手复原
    private static final float PRESS_SCALE = 0.95f;
    private static final float PRESS_ALPHA = 0.82f;
    private static final float PRESS_OFFSET_DP = 2f;
    private static final long PRESS_DURATION_MS = 90L;
    private static final long RELEASE_DURATION_MS = 150L;

    /**
     * TYPE_RITUAL|TYPE_FUSION|TYPE_SYNCHRO|TYPE_XYZ|TYPE_LINK|TYPE_SPSUMMON
     */
    private static final long SP_SUMMON_TYPE_MASK =
            CardType.Ritual.getId() | CardType.Fusion.getId() | CardType.Synchro.getId()
                    | CardType.Xyz.getId() | CardType.Sp_Summon.getId() | CardType.Link.getId();

    public interface OnCardPressed {
        void onCardPressed(int slot);
    }

    public interface OnSlotClick {
        void onSlotClick(int slot);
    }

    public interface TipProvider {
        String getTipText(int slot);
    }

    private final Context context;
    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ViewGroup overlay;
    private final View[] anchors = new View[MAX_SLOTS];

    private TextView tipView;
    private OnCardPressed pressedListener;
    private OnSlotClick clickListener;
    private TipProvider tipProvider;

    private int holdingSlot = -1;
    private boolean holdFired;

    private final Runnable holdTask = new Runnable() {
        @Override
        public void run() {
            holdFired = true;
            showTip();
        }
    };

    public CardStatusTipHelper(Context context, View contentRoot) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
        this.overlay = findTipOverlay(contentRoot);
    }

    /**
     * 自内容层往上找第一个 FrameLayout 祖先（即 DraggablePopupHelper 的全窗口包装层）
     */
    private static ViewGroup findTipOverlay(View contentRoot) {
        if (contentRoot == null) return null;
        ViewParent p = contentRoot.getParent();
        while (p != null) {
            if (p instanceof FrameLayout) return (ViewGroup) p;
            if (!(p instanceof View)) break;
            p = ((View) p).getParent();
        }
        // 找不到 FrameLayout 时：如果 contentRoot 本身就是 ViewGroup，取它的第一个子 View 继续尝试
        if (contentRoot instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) contentRoot;
            if (vg.getChildCount() > 0) {
                View child = vg.getChildAt(0);
                return findTipOverlay(child);
            }
        }
        return null;
    }

    public void setOnCardPressedListener(OnCardPressed l) {
        this.pressedListener = l;
    }

    public void setOnSlotClickListener(OnSlotClick l) {
        this.clickListener = l;
    }

    public void setTipProvider(TipProvider p) {
        this.tipProvider = p;
    }

    /**
     * 接管槽位触摸。listener 对全部事件返回 true，View 自身的点击/长按机制完全不参与，
     * 按下态由 setPressed 手动维护，避免 onTouchEvent 在缺少 DOWN 的情况下误触发 performClick。
     * slotView 显式置为可点击，保证 ACTION_DOWN 一定投递到本 View（"按下卡片图"的直接命中面），
     * 并对 anchor（卡片图）施加简单的按下/抬起动画。
     */
    public void attach(final int slot, View slotView, View anchor) {
        if (slotView == null || slot < 0 || slot >= MAX_SLOTS) return;
        anchors[slot] = anchor != null ? anchor : slotView;
        slotView.setClickable(true);
        slotView.setFocusable(true);
        final View pressView = anchors[slot];
        slotView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        applyPressEffect(pressView, true);
                        onDown(slot);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        applyPressEffect(pressView, false);
                        onUp(slot);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        applyPressEffect(pressView, false);
                        onCancel();
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
    }

    /**
     * 简单按下动画：按下下沉若干 dp + 微缩放 + 变暗，抬手/取消复原
     */
    private void applyPressEffect(View v, boolean pressed) {
        if (v == null) return;
        v.animate().cancel();
        if (pressed) {
            v.animate()
                    .scaleX(PRESS_SCALE).scaleY(PRESS_SCALE)
                    .translationY(PRESS_OFFSET_DP * density)
                    .alpha(PRESS_ALPHA)
                    .setDuration(PRESS_DURATION_MS)
                    .start();
        } else {
            v.animate()
                    .scaleX(1f).scaleY(1f)
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(RELEASE_DURATION_MS)
                    .start();
        }
    }

    private void onDown(int slot) {
        hide();
        holdingSlot = slot;
        holdFired = false;
        if (pressedListener != null) pressedListener.onCardPressed(slot);
        handler.postDelayed(holdTask, HOLD_THRESHOLD_MS);
    }

    private void onUp(int slot) {
        handler.removeCallbacks(holdTask);
        boolean held = holdFired;
        holdFired = false;
        holdingSlot = -1;
        hide();
        if (!held && clickListener != null) clickListener.onSlotClick(slot);
    }

    private void onCancel() {
        holdFired = false;
        holdingSlot = -1;
        hide();
    }

    /**
     * 翻页 / 刷新槽位 / 关闭弹窗时调用
     */
    public void hide() {
        handler.removeCallbacks(holdTask);
        if (tipView != null) tipView.setVisibility(View.GONE);
    }

    private void showTip() {
        int slot = holdingSlot;
        if (overlay == null || tipProvider == null || slot < 0 || slot >= MAX_SLOTS) return;
        View anchor = anchors[slot];
        if (anchor == null || !anchor.isShown()) return;
        String text = tipProvider.getTipText(slot);
        // 对应 C++ if(str.length() > 0)：无内容时不显示标签，但按住仍不视为点击
        if (text == null || text.isEmpty()) return;

        TextView tv = ensureTipView();
        tv.setText(text);
        tv.setVisibility(View.VISIBLE);
        int maxW = (int) (TIP_MAX_WIDTH_DP * density + 0.5f);
        tv.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int w = tv.getMeasuredWidth();

        int[] aLoc = new int[2];
        anchor.getLocationOnScreen(aLoc);
        int[] oLoc = new int[2];
        overlay.getLocationOnScreen(oLoc);
        float margin = TIP_EDGE_MARGIN_DP * density;
        // x = 锚点水平中心、y = 锚点下边缘（gframe L2952-2953）
        float cx = aLoc[0] + anchor.getWidth() / 2f - oLoc[0];
        float left = cx - w / 2f;
        float minLeft = margin;
        float maxLeft = overlay.getWidth() - w - margin;
        if (maxLeft < minLeft) maxLeft = minLeft;
        if (left < minLeft) left = minLeft;
        if (left > maxLeft) left = maxLeft;
        float top = aLoc[1] + anchor.getHeight() - oLoc[1] + TIP_ANCHOR_GAP_DP * density;
        tv.setX(left);
        tv.setY(top);
    }

    private TextView ensureTipView() {
        if (tipView != null) return tipView;
        int maxW = (int) (TIP_MAX_WIDTH_DP * density + 0.5f);
        TextView tv = new TextView(context);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TIP_TEXT_SIZE_SP);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(TIP_BACKGROUND);
        int p = (int) (TIP_PADDING_DP * density + 0.5f);
        tv.setPadding(p, p, p, p);
        // maxWidth 保证包装层以 AT_MOST(窗口宽) 复核测量时也不会超过 320dp，位置保持
        tv.setMaxWidth(maxW);
        tv.setVisibility(View.GONE);
        tv.setClickable(false);
        tv.setLongClickable(false);
        tv.setFocusable(false);
        overlay.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tipView = tv;
        return tv;
    }

    /**
     * 列表项 → 场上实时卡片。location 含 LOCATION_OVERLAY 时按「超量怪兽格 + 素材序号」解析
     * （协议与 CmdMenuDialog 两种写法都覆盖：0x84 之类带基址的标志，以及单独的 0x80）。
     */
    public static GameField.ClientCard resolveLiveCard(GameField field, int viewControler,
                                                       int location, int sequence, int subSeq) {
        if (field == null) return null;
        int overlayBit = CardLocation.Overlay.value();
        if ((location & overlayBit) != 0) {
            int base = location & 0x7f;
            if (base == 0) base = CardLocation.MonsterZone.value();
            GameField.ClientCard xyz = field.getCard(viewControler, base, sequence);
            if (xyz != null && subSeq >= 0 && subSeq < xyz.overlayed.size()) {
                return xyz.overlayed.get(subSeq);
            }
            return null;
        }
        return field.getCard(viewControler, location, sequence);
    }

    /**
     * 逐行镜像 ClientField::ShowCardInfoInList（event_handler.cpp L2919-2948）：
     * 卡名 → 225 叠放素材 → 224 正规特殊召唤 → *desc_hints → 216 在连锁%d发动 → 217 被连锁选择为对象。
     * 卡组内的卡只保留卡名（C++ location != LOCATION_DECK 才继续）。
     */
    public static String buildStatusText(GameField field, GameField.ClientCard card) {
        if (card == null) return null;
        DataManager dm = DataManager.get();
        StringBuilder sb = new StringBuilder();
        if (card.code != 0) {
            String name0 = dm.getName(card.code);
            sb.append(name0);
            // 通讯中该卡当前被视为的卡名（QUERY_ALIAS）：与卡表本名不同才补一行「(别名)」，
            // 镜像 event_handler.cpp L1680-1683 / L1704-1707 的 GetName(alias) 括号显示
            if (card.alias != 0) {
                String aliasName = dm.getName(card.alias);
                if (aliasName != null && !aliasName.isEmpty() && !aliasName.equals(name0)) {
                    sb.append("\n(").append(aliasName).append(")");
                }
            }
        }
        if (card.location != CardLocation.Deck.value()) {
            appendStatDiffLines(sb, dm, card);
            if (card.overlayTarget != null) {
                appendLine(sb, dm.formatSystemString(225, "叠放于[%s](%d)下",
                        dm.getName(card.overlayTarget.code), card.overlayTarget.sequence + 1));
            }
            if ((card.status & GameField.STATUS_PROC_COMPLETE) != 0
                    && (card.type & SP_SUMMON_TYPE_MASK) != 0) {
                appendLine(sb, dm.getStringManager()
                        .getSystemString(224, "已用正规方法特殊召唤"));
            }
            if (card.descHints != null) {
                for (Map.Entry<Integer, Integer> e : card.descHints.entrySet()) {
                    appendLine(sb, "*" + dm.getDesc(e.getKey(), ""));
                }
            }
            if (field != null && field.chains != null) {
                for (int i = 0; i < field.chains.size(); i++) {
                    GameField.ChainInfo chit = field.chains.get(i);
                    if (chit == null) continue;
                    if (card == chit.chainCard) {
                        appendLine(sb, dm.formatSystemString(216, "在连锁%d发动", i + 1));
                    }
                    if (chit.chainCard != null && chit.targets != null
                            && chit.targets.contains(card)) {
                        appendLine(sb, dm.formatSystemString(217, "被连锁%d的[%s]选择为对象",
                                i + 1, dm.getName(chit.chainCard.code)));
                    }
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 通讯当前值与卡表原始值的差异行（详情面板只显原值，差异全部落在本标签，
     * 语义对应 drawing.cpp Game::DrawStatus L1200-1224：当前值≠基础值时以变色文字
     * 显示在卡图上；纯文本无法着色，以「当前(原X)↑/↓」标注）
     */
    private static void appendStatDiffLines(StringBuilder sb, DataManager dm,
                                            GameField.ClientCard card) {
        // type==0 表示未收到该卡 query（暗卡等），无可比对
        if (card.code == 0 || card.type == 0) return;
        Card cd = dm.getCardManager().getCard(card.code);
        if (cd == null || !cd.isType(CardType.Monster)) return;
        StringManager sm = dm.getStringManager();
        // 等级 / 阶级 / LINK 数值（对应 C++ lvstring / linkstring）
        if (cd.isLink()) {
            if (card.link > 0 && card.link != cd.getLinkNumber()) {
                appendLine(sb, "LINK-" + card.link + "(原LINK-" + cd.getLinkNumber() + ")");
            }
        } else if (cd.isType(CardType.Xyz)) {
            if (card.rank > 0 && card.rank != cd.getStar()) {
                appendLine(sb, "阶级 " + card.rank + "(原" + cd.getStar() + ")");
            }
        } else {
            if (card.level > 0 && card.level != cd.getStar()) {
                appendLine(sb, "等级 " + card.level + "(原" + cd.getStar() + ")");
            }
        }
        if (card.attribute != 0 && card.attribute != cd.Attribute) {
            appendLine(sb, "属性 " + sm.getAttributeString(card.attribute)
                    + "(原" + sm.getAttributeString(cd.Attribute) + ")");
        }
        if (card.race != 0 && card.race != cd.Race) {
            appendLine(sb, "种族 " + sm.getRaceString(card.race)
                    + "(原" + sm.getRaceString(cd.Race) + ")");
        }
        // 攻/守：client_card.h attack/defense vs base_attack/base_defense，
        // 原始表值即未修正前的基准，当前值不同即显示差异行
        if (card.attack != cd.Attack) {
            appendLine(sb, "攻击力 " + statValue(card.attack) + "(原" + statValue(cd.Attack) + ")"
                    + statDelta(card.attack, cd.Attack));
        }
        if (!cd.isLink() && card.defense != cd.Defense) {
            appendLine(sb, "守备力 " + statValue(card.defense) + "(原" + statValue(cd.Defense) + ")"
                    + statDelta(card.defense, cd.Defense));
        }
        boolean lsDiff = card.lScale > 0 && card.lScale != cd.LeftScale;
        boolean rsDiff = card.rScale > 0 && card.rScale != cd.RightScale;
        if (lsDiff || rsDiff) {
            int lsc = lsDiff ? card.lScale : cd.LeftScale;
            int rsc = rsDiff ? card.rScale : cd.RightScale;
            appendLine(sb, "灵摆刻度 " + lsc + "/" + rsc
                    + "(原" + cd.LeftScale + "/" + cd.RightScale + ")");
        }
    }

    /**
     * 数值显示：负数按「?」处理（cards.db 中 -2/-3 表示 ?），同 CardDetailPanel 惯例
     */
    private static String statValue(int v) {
        return v < 0 ? "?" : String.valueOf(v);
    }

    private static String statDelta(int cur, int base) {
        if (cur < 0 || base < 0) return "";
        return cur > base ? "↑" : "↓";
    }

    private static void appendLine(StringBuilder sb, String text) {
        if (text == null || text.isEmpty()) return;
        if (sb.length() > 0) sb.append('\n');
        sb.append(text);
    }

    /**
     * 场上 / 手卡长按的同款悬浮标签。
     * <p>
     * GameFieldView 是 setZOrderOnTop(true) 的 GLSurfaceView，GL 曲面合成在 Activity 窗口之上，
     * 挂在 content 层的普通 View（即便抬高 elevation / bringToFront）仍会被卡片遮挡。故与
     * SpecEffectOverlay 采用同一图层约定：用全屏透明、不拦截触摸的 PopupWindow（独立窗口，位于
     * GL 曲面之上）承载气泡标签，稳定显示在卡片前面，不再被遮挡。
     * <p>
     * 锚定：我方卡片把气泡放在卡片「上边缘」之上；对方卡片对其视角是倒置 180° 的，其卡名/信息
     * 落在视觉下缘，故把气泡放在「下边缘上方」。卡片上下边缘由 GameFieldView.getCardScreenBounds
     * 投影得到的实际绘制四边形给出（bounds 为空时回退到触点）。字体比列表内 cardtip 小一号。
     * 两个对话框内的 cardtip 走 showTip() 挂弹窗包装层的路径，与此无关，位置与字号均保持不变。
     */
    public static final class FieldTip {
        private static PopupWindow sWindow;

        public static void show(Activity activity, View anchor, String text,
                                float tapX, float tapY, float[] bounds, boolean mine) {
            hide();
            if (activity == null || anchor == null || text == null || text.isEmpty()) return;
            View decor = activity.getWindow().getDecorView();
            if (decor == null || decor.getWindowToken() == null) return;

            final float density = activity.getResources().getDisplayMetrics().density;

            final TextView tv = new TextView(activity);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, FIELD_TIP_TEXT_SIZE_SP);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.card_status_tip_bg);
            int pad = (int) (TIP_PADDING_DP * density + 0.5f);
            tv.setPadding(pad, pad, pad, pad);
            tv.setMaxWidth((int) (TIP_MAX_WIDTH_DP * density + 0.5f));
            tv.setText(text);

            final FrameLayout root = new FrameLayout(activity);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.START | Gravity.TOP;
            root.addView(tv, lp);

            PopupWindow window = new PopupWindow(root,
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // 纯展示层：不抢焦点、不拦截触摸，事件穿透到下层决斗场
            window.setFocusable(false);
            window.setOutsideTouchable(false);
            window.setTouchable(false);
            try {
                window.showAtLocation(decor, Gravity.NO_GRAVITY, 0, 0);
            } catch (Exception ignored) {
                return;
            }
            sWindow = window;

            // 待 PopupWindow 完成测量布局后再定位（此时 root 有真实宽高可做越界翻转钳制）
            final float[] fb = bounds;
            final boolean fm = mine;
            root.post(new Runnable() {
                @Override
                public void run() {
                    positionTip(tv, root, anchor, tapX, tapY, fb, fm, density);
                }
            });
        }

        private static void positionTip(TextView tv, FrameLayout root, View anchor,
                                        float tapX, float tapY, float[] bounds, boolean mine,
                                        float density) {
            if (sWindow == null) return;   // 已 hide()，post 回调延后触发的兜底
            int[] aLoc = new int[2];
            int[] rLoc = new int[2];
            anchor.getLocationInWindow(aLoc);
            root.getLocationInWindow(rLoc);
            float margin = TIP_EDGE_MARGIN_DP * density;
            float gap = 8f * density;
            int w = tv.getWidth() > 0 ? tv.getWidth() : tv.getMeasuredWidth();
            int h = tv.getHeight() > 0 ? tv.getHeight() : tv.getMeasuredHeight();
            int rootW = root.getWidth();
            int rootH = root.getHeight();

            // anchor(GameFieldView) 视图内坐标 + anchor 窗口偏移 = 窗口坐标；
            // root 铺满窗口，减去 root 窗口偏移即得 root 内坐标
            float cxWin;
            float edgeWin;
            if (bounds != null && bounds.length >= 3) {
                cxWin = aLoc[0] + bounds[0];
                // bounds[1]=卡片视觉顶边, bounds[2]=卡片视觉底边
                // 我方锚上边缘、对方锚下边缘（其卡面对我方倒置 180°，信息在视觉下缘）
                edgeWin = aLoc[1] + (mine ? bounds[1] : bounds[2]);
            } else {
                cxWin = aLoc[0] + tapX;
                edgeWin = aLoc[1] + tapY;
            }
            float cx = cxWin - rLoc[0];
            float edgeY = edgeWin - rLoc[1];

            float left = cx - w / 2f;
            float maxLeft = rootW - w - margin;
            if (maxLeft < margin) maxLeft = margin;
            if (left < margin) left = margin;
            if (left > maxLeft) left = maxLeft;

            // 默认把气泡放在参考边缘「之上」（气泡底边贴边缘上方 gap）；
            // 上方放不下则翻到边缘下方，再越界则贴边钳制
            float top = edgeY - gap - h;
            if (top < margin) top = edgeY + gap;
            if (top + h > rootH - margin) top = rootH - margin - h;
            if (top < margin) top = margin;

            tv.setX(left);
            tv.setY(top);
        }

        public static void hide() {
            if (sWindow != null) {
                try {
                    if (sWindow.isShowing()) sWindow.dismiss();
                } catch (Exception ignored) {
                }
                sWindow = null;
            }
        }
    }
}