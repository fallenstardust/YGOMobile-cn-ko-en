package cn.garymb.ygomobile.game.gamefield;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;

/**
 * 「持续适用中效果」下拉面板（点击头像角上的金色感叹号 → 向下展开列表，
 * 行左为卡片图缩小版、行右为 cdb str 效果文字）。
 * <p>双方同时展开（用户新规格，替换旧「只显示被点击那一侧」，也替换已放弃的
 * 「把双方条目混进被点击侧一个列表里」）：点击任一侧徽标都把「我方」与「对方」两份列表
 * <b>一起</b>显示，但各自贴本侧血条面板下方、各自带本侧标题与独立滚动区，互不混排——
 * 混排会把对方条目压进我方列表，读起来分不清归属（用户原话「这样很乱」）。
 * 只有一方有条目时另一侧窗口根本不建立，两侧都空即整体收起。
 * <p>图层：必须走 PopupWindow——GameFieldView 是 {@code setZOrderOnTop(true)} 的
 * GLSurfaceView，GL 曲面合成在 Activity 窗口之上，作为 layout_game_right 子 View 的面板
 * 会被场地/卡片纹理遮挡（同 LP 变化浮字、分侧聊天行的图层约定）。
 * <p>行点击（用户新规格）：转交 {@link OnCardClick}（按条目自身 side），由 GameTopInfoManager
 * 按卡码合成临时卡走 ClientCard 展示态路径刷 carddetailpanel（带已打勾的序号段落标记）；
 * 面板不收起，便于连续点看。
 * <p>字号（用户新规格）：正文与标题固定 7sp（不随 HUD 系数放大，否则长句在窄面板里根本放不下），
 * 仅卡图缩略版随 HUD 系数等比。
 * <p>生命周期：由 {@link GameTopInfoManager} 持有单实例（内部两 {@link Panel} 各带一窗口）。
 * 两侧窗口都 {@code setOutsideTouchable(false)}：旧的单面板版靠「点窗口外」收起，而两侧同展时
 * 点其中一份列表恰好落在另一份的边界外，沿用会造成「想滑对方列表却把两份一起关掉」；改由
 * 再点任一侧徽标、或两侧条目都清空时整体收起（任一面板被外部收掉时也走 {@link #dismiss()}
 * 一并收口，不留「只剩一半」的中间态）；HUD 隐藏（{@code GameTopInfoManager#hide()}）时一并 dismiss 防窗口泄漏。
 * <p>刷新（用户新规格：刷新不再整窗重建）：逐侧独立判条目指纹——该侧窗口还在展示且条目与缩放
 * 都没变就一律不动（连重绘也不做，不打断滚动），条目变了也只重建该侧行并用
 * {@code PopupWindow.update} 改尺寸与位置，按重建前的滚动位置回贴；该侧条目清空只关该侧窗口。
 * <p>缩放：卡图/内边距全部乘 HUD 等比系数 f（与血条面板同源），平板与手机上占比恒定。
 */
public final class ApplyingEffectsPopup {

    /** 列表最大高度占 layout_game_right 高的比例（超出走 ScrollView 滚动，不盖住整个决斗场） */
    private static final float MAX_HEIGHT_FRACTION = 0.42f;
    /** 卡图缩略版基准尺寸（dp，f=1）：按 177:254 原比例，随 HUD 系数等比放大 */
    private static final float CARD_W_DP = 20f;
    private static final float CARD_H_DP = 29f;
    /** 正文/标题字号（sp）：硬 7sp 不随 HUD 缩放（用户新规格：长句要在窄面板里放得下） */
    private static final float TEXT_SIZE_SP = 7f;
    /** 行内文字右侧留白基准（dp，随 HUD 系数）：卡图与文字之间的间隙不在这里，
     *  由 item_applying_effect.xml 的 marginStart 2dp 单独控制（用户新规格：间隔缩小） */
    private static final float ROW_TEXT_PAD_DP = 5f;

    /** 行点击回调（用户新规格：点列表上的卡 → carddetailpanel 显示该卡详情与已标记序号文本） */
    public interface OnCardClick {
        void onCardClick(int code, int side);
    }

    /**
     * 单侧面板的全部状态：窗口 + 骨架视图 + 刷新基准。两侧各一份，故 content/tvTitle 等
     * 必须逐侧独立 inflate——同一 View 只能有一个父级，共用一份骨架会被第二个 PopupWindow 拒绝。
     */
    private static final class Panel {
        /** 本地半场索引（0=我方 1=对方），决定标题文案与行点击归属 */
        final int side;
        PopupWindow window;
        View content;
        TextView tvTitle;
        LinearLayout rows;
        ScrollView scroll;
        /** 上一次落地到窗口里的条目指纹与缩放/宽度基准（原地刷新据此判断要不要重绘） */
        String shownKeys = "";
        float shownScale;
        int shownWidth;

        Panel(int side) {
            this.side = side;
        }
    }

    private final Activity activity;
    private final ImageLoader imageLoader;
    private final Panel panelSelf;
    private final Panel panelOpp;
    /** 行点击回调（由 GameTopInfoManager 注入） */
    private OnCardClick onCardClick;

    public ApplyingEffectsPopup(Activity activity, ImageLoader imageLoader) {
        this.activity = activity;
        this.imageLoader = imageLoader;
        panelSelf = new Panel(0);
        panelOpp = new Panel(1);
    }

    /** 注入行点击回调（展开列表时点击卡片 → 卡详面板显示该卡） */
    public void setOnCardClick(OnCardClick listener) {
        onCardClick = listener;
    }

    /** 指定半场的面板当前是否在展示（徽标点击据此判断收起还是展开） */
    public boolean isShowing(int side) {
        PopupWindow w = panel(side).window;
        return w != null && w.isShowing();
    }

    /** 两侧任一面板在展示（整体展开态判据） */
    public boolean isShowing() {
        return isShowing(0) || isShowing(1);
    }

    /**
     * 同时展开（或原地刷新）双方列表：我方条目贴我方血条面板下方、对方条目贴对方血条面板下方，
     * 各自带本侧标题（用户新规格）。某侧条目为空则该侧窗口收起，不出现空面板。
     *
     * @param anchor    定位锚（layout_top_info）；两侧面板都贴其下缘
     * @param selfPanel 我方血条面板（layout_player_panel），决定我方面板横向区间
     * @param oppPanel  对方血条面板（layout_opponent_panel），决定对方面板横向区间
     * @param selfItems 我方条目（{@link ContinuousEffectApplier#forSide}，可为空）
     * @param oppItems  对方条目（同上，可为空）
     * @param hudScale  HUD 等比系数
     */
    public void show(View anchor, View selfPanel, View oppPanel,
                     List<ContinuousEffectApplier.Applying> selfItems,
                     List<ContinuousEffectApplier.Applying> oppItems, float hudScale) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        float f = hudScale <= 0 ? 1f : hudScale;
        showPanel(panelSelf, anchor, selfPanel,
                selfItems == null ? Collections.<ContinuousEffectApplier.Applying>emptyList() : selfItems, f);
        showPanel(panelOpp, anchor, oppPanel,
                oppItems == null ? Collections.<ContinuousEffectApplier.Applying>emptyList() : oppItems, f);
    }

    /**
     * 单侧展开/原地刷新（对齐 C++ 的 stTip 展示与 {@code WaitFrameSignal} 之后才继续的观感：
     * 只做展示不参与对局推进）。条目空 / 锚视图缺失即收掉本侧窗口，不显示空面板。
     */
    private void showPanel(Panel p, View anchor, View barPanel,
                           List<ContinuousEffectApplier.Applying> items, float f) {
        if (anchor == null || barPanel == null || items.isEmpty()) {
            closePanel(p);
            return;
        }
        int[] anchorLoc = new int[2];
        int[] panelLoc = new int[2];
        anchor.getLocationInWindow(anchorLoc);
        barPanel.getLocationInWindow(panelLoc);
        int width = barPanel.getWidth();
        int avail = anchor.getRootView() != null ? anchor.getRootView().getHeight() : 0;
        if (width <= 0 || avail <= 0) return;
        String key = itemsKey(items);
        boolean live = p.window != null && p.window.isShowing();
        // 该侧窗口还在且条目与缩放都没变就一律不动（连重绘也不做，不打断玩家滚动）
        if (live && key.equals(p.shownKeys) && Math.abs(f - p.shownScale) < 0.001f
                && width == p.shownWidth) return;
        ensureContent(p);
        int scrollY = live && p.scroll != null ? p.scroll.getScrollY() : 0;
        p.tvTitle.setText(p.side == 0
                ? R.string.applying_effects_self : R.string.applying_effects_opponent);
        p.tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP);
        buildRows(p, items, f);
        p.content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = Math.min(p.content.getMeasuredHeight(), Math.round(avail * MAX_HEIGHT_FRACTION));
        int x = panelLoc[0];
        // 面板贴顶部信息条下缘向下展开（我方血条在下半屏 → 面板在其下方；对方同理在其面板下方）
        int y = anchorLoc[1] + anchor.getHeight();
        if (live) {
            p.window.update(x, y, width, height);
            p.shownKeys = key;
            p.shownScale = f;
            p.shownWidth = width;
            // 行重建会连带重排版面，把玩家原本滚到的位置在下一帧贴回去（新内容更短时 ScrollView 自行夹取）
            if (p.scroll != null && scrollY > 0) {
                final int restore = scrollY;
                p.scroll.post(() -> p.scroll.scrollTo(0, restore));
            }
            return;
        }
        closePanel(p);
        p.window = new PopupWindow(p.content, width, height);
        p.window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // 两侧同展时不能开 outsideTouchable：点一份列表恰好落在另一份的边界外，会把两份一起收起，
        // 玩家根本没法滑另一侧；改为窗口只响应自身范围内的触控，窗口外触控照常落到决斗场（旧单面板版
        // 靠点窗口外收起，本版由再点徽标 / 条目清空 / hide() 收口）
        p.window.setOutsideTouchable(false);
        p.window.setTouchable(true);
        p.window.setFocusable(false);
        // 被外部（系统/HUD 收起等）收掉时两侧一并收口，不留一半；程序性关闭已在 closePanel 里
        // 先把本监听摘掉，不会由此触发连带关闭
        p.window.setOnDismissListener(() -> dismiss());
        try {
            p.window.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
            p.shownKeys = key;
            p.shownScale = f;
            p.shownWidth = width;
        } catch (Exception ignored) {
            // 窗口 token 失效（Activity 正在销毁/旋转中）：本次展开放弃，下一次刷新重建
            p.window = null;
            p.shownKeys = "";
        }
    }

    /**
     * 关闭单侧窗口并清其刷新基准（该侧条目清空时走此，另一侧不受影响）。
     * <p>先摘 onDismiss 再 dismiss：PopupWindow 的 onDismiss 是 post 出去的，等到它跑时已无法区分
     * 「程序性关闭本侧」与「玩家点了窗口外」，若不摘掉就会把另一侧还有效的列表一起关掉。
     */
    private void closePanel(Panel p) {
        PopupWindow w = p.window;
        p.window = null;
        p.shownKeys = "";
        p.shownScale = 0f;
        p.shownWidth = 0;
        if (w == null) return;
        try {
            w.setOnDismissListener(null);
            if (w.isShowing()) w.dismiss();
        } catch (Exception ignored) {
        }
    }

    /** 收起两侧并清除原地刷新基准（再点任一侧徽标、两侧条目清空、HUD 隐藏与旋转重绑均经此） */
    public void dismiss() {
        closePanel(panelSelf);
        closePanel(panelOpp);
    }

    /** 按条目重建本侧列表行（卡图缩略版随 HUD 系数、正文固定 7sp），整行可点转卡详 */
    private void buildRows(Panel p, List<ContinuousEffectApplier.Applying> items, float f) {
        p.rows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        for (ContinuousEffectApplier.Applying item : items) {
            View row = inflater.inflate(R.layout.item_applying_effect, p.rows, false);
            ImageView iv = row.findViewById(R.id.iv_applying_effect_card);
            TextView tv = row.findViewById(R.id.tv_applying_effect_text);
            int w = Math.round(CARD_W_DP * f * density);
            int h = Math.round(CARD_H_DP * f * density);
            ViewGroup.LayoutParams iLP = iv.getLayoutParams();
            iLP.width = w;
            iLP.height = h;
            iv.setLayoutParams(iLP);
            imageLoader.bindImage(iv, item.code, ImageLoader.Type.small);
            tv.setText(item.text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP);
            int pad = Math.round(ROW_TEXT_PAD_DP * f * density);
            // 左侧不再加内边距：与卡图的间隙只走 XML 的 marginStart（2dp），
            // 旧写法 margin + 左内边距叠加把间隔拉成 10dp（用户新规格：间隔可缩小）
            tv.setPadding(0, 0, pad, 0);
            // 整行可点：按卡码+半场回调 GameTopInfoManager 刷卡详（带已发动序号段落标记）；
            // 面板保持展开，玩家可连续点若干张卡对比。side 取条目自身而非被点击侧，
            // 这样点对方列表里的卡仍按对方半场取账本标记（红勾）
            final int clickedCode = item.code;
            final int clickedSide = item.side;
            row.setClickable(true);
            row.setOnClickListener(v -> {
                if (onCardClick != null) onCardClick.onCardClick(clickedCode, clickedSide);
            });
            p.rows.addView(row);
        }
    }

    /** 条目集合指纹（卡码×段落×半场×回合×整局/本回合×文本长度）：完全一致才允许跳过重绘 */
    private static String itemsKey(List<ContinuousEffectApplier.Applying> items) {
        StringBuilder sb = new StringBuilder();
        for (ContinuousEffectApplier.Applying it : items) {
            sb.append(it.code).append(':').append(it.offset).append(':')
                    .append(it.side).append(':').append(it.turn).append(':')
                    .append(it.wholeDuel ? 'D' : 'T').append(':')
                    .append(it.text == null ? 0 : it.text.length()).append(';');
        }
        return sb.toString();
    }

    /** 惰性构建本侧面板骨架（横竖屏共用 layout_applying_effects；两侧各一份实例，避免同 View 双父级） */
    private void ensureContent(Panel p) {
        if (p.content != null) return;
        p.content = LayoutInflater.from(activity).inflate(R.layout.layout_applying_effects, null);
        p.tvTitle = p.content.findViewById(R.id.tv_applying_title);
        p.rows = p.content.findViewById(R.id.layout_applying_rows);
        p.scroll = p.content.findViewById(R.id.scroll_applying_effects);
    }

    private Panel panel(int side) {
        return side == 0 ? panelSelf : panelOpp;
    }
}
