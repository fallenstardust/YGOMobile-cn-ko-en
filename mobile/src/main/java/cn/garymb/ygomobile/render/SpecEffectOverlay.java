package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayDeque;

import cn.garymb.ygomobile.lite.R;
import ocgcore.DataManager;
import ocgcore.StringManager;

/**
 * 决斗场特效覆盖层：移植 gframe drawing.cpp Game::DrawSpec() 的 showcard 各分支，
 * 在 layout_game_right 区域中央绘制卡片大图展示、发动遮罩光带、效果无效图标、
 * 特殊召唤放大、计数器数字、召唤翻面、猜拳手势、阶段/胜负文字等动态动画。
 *
 * 图层约定（对齐 RPSDialog）：GameFieldView 是 setZOrderOnTop(true) 的 GLSurfaceView，
 * GL 曲面合成在 Activity 窗口之上，普通 View 会被场地遮挡，故本层使用全屏透明、
 * 不拦截触摸的 PopupWindow 承载 Canvas 绘制视图（SpecEffectView），稳定显示在 GL 曲面之上。
 *
 * 动画驱动：SpecEffectView 内 Choreographer 逐帧回调，按 dt*60*speed 推进 showcarddif/showcardp。
 */
public class SpecEffectOverlay {

    // === showcard 效果类型（值对齐 drawing.cpp DrawSpec 的 case） ===
    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_ACTIVATE = 1;    // 发动：遮罩自上/左揭开卡片大图
    public static final int EFFECT_ACTIVATE2 = 2;   // 发动：遮罩继续向右消失
    public static final int EFFECT_NEGATED = 3;     // 无效：中央缩小无效图标
    public static final int EFFECT_FADEIN = 4;      // 淡入
    public static final int EFFECT_SPSUMMON = 5;    // 特殊召唤：放大 + 淡入
    public static final int EFFECT_COUNTER = 6;     // 计数器：数字图标
    public static final int EFFECT_SUMMON = 7;      // 普通/反转召唤：翻面进入
    public static final int EFFECT_RPS = 100;       // 猜拳手势
    public static final int EFFECT_TEXT = 101;      // 阶段/胜负文字
    /** 居中动作消息文本（wACMessage/stACMessage）：12sp 小字 + ygopro_base_background + 展开动画 */
    public static final int EFFECT_ACTION_TEXT = 102;

    // === EFFECT_TEXT 的 showcardcode（对齐 drawing.cpp case 101 与 duelclient.cpp） ===
    public static final int TEXT_YOU_WIN = 1;
    public static final int TEXT_YOU_LOSE = 2;
    public static final int TEXT_DRAW_GAME = 3;
    public static final int TEXT_DRAW_PHASE = 4;
    public static final int TEXT_STANDBY_PHASE = 5;
    public static final int TEXT_MAIN_PHASE_1 = 6;
    public static final int TEXT_BATTLE_PHASE = 7;
    public static final int TEXT_MAIN_PHASE_2 = 8;
    public static final int TEXT_END_PHASE = 9;
    public static final int TEXT_NEXT_TURN = 10;
    public static final int TEXT_DUEL_START = 11;

    /** case 101 文字表（index = showcardcode，0 位占位不用） */
    private static final String[] TEXT_TABLE = {
            "", "You Win!", "You Lose!", "Draw Game", "Draw Phase", "Standby Phase",
            "Main Phase 1", "Battle Phase", "Main Phase 2", "End Phase",
            "Next Players Turn", "Duel Start", "Duel1 Start", "Duel2 Start", "Duel3 Start"
    };

    private final Activity activity;
    private PopupWindow window;
    private FrameLayout rootLayer;
    private SpecEffectView view;
    /** 弹幕宿主容器（仅系统/观战消息）：位于 PopupWindow 内、双方 LP 血条正下方横带，
     *  自右向左滚动；对局玩家聊天不走弹幕，见 selfChatLayer/oppChatLayer */
    private FrameLayout danmakuLayer;
    /** 我方/对方聊天行容器：各自 LP 血条正下方纵向 LinearLayout，自上而下追加 */
    private LinearLayout selfChatLayer;
    private LinearLayout oppChatLayer;
    /** 居中动作消息文本区域容器（对齐 layout_game_right 窗口矩形），内部 TextView 随文字自适应居中 */
    private FrameLayout actionTextHost;
    private TextView actionText;
    /** 动作消息文本是否在屏（展开/停留/收起全程）：占用串行队列与动画屏障 */
    private boolean actionTextActive;
    private Runnable actionTextCloser;
    /** layout_game_right 窗口坐标区域（画布特效与动作消息文本的共同基准） */
    private int regionLeft, regionTop, regionW, regionH;
    private float speed = 1f;
    /** 特效请求队列：保证动画串行播放——上一段完全结束后再播下一段 */
    private final ArrayDeque<EffectRequest> queue = new ArrayDeque<>();
    /** 特效队列排空（无动画播放）回调：供 GameEngine 重开消息闸门 */
    private OnIdleListener idleListener;

    public SpecEffectOverlay(Activity activity) {
        this.activity = activity;
    }

    /** 动画倍率：1=原速，2=2 倍速（与 GameFieldView.setAnimationSpeed 语义一致） */
    public void setAnimationSpeed(float multiplier) {
        this.speed = Math.max(0.25f, multiplier);
        if (view != null) view.speed = this.speed;
    }

    /** 设置特效队列排空回调（在 UI 线程触发）：队列中所有动画播放完毕、当前空闲时调用一次 */
    public void setOnIdleListener(OnIdleListener l) {
        this.idleListener = l;
    }

    /**
     * 特效层是否仍在播放：队列有待播动画，或当前视图正在逐帧绘制（running）。
     * 供 GameEngine 统一动画屏障即时查询（idle 回调是排空瞬间的快路径，本方法是任意时刻的状态查询）。
     */
    public boolean isBusy() {
        return !queue.isEmpty() || actionTextActive || (view != null && view.running);
    }

    // ==================== 对外触发的各 case 动画 ====================

    /** case 1→2：发动效果，布局中央卡片大图 + tMask 遮罩光带自左向右揭开（MSG_CHAINING / HINT_EFFECT） */
    public void showActivate(int code) {
        enqueue(new EffectRequest(EFFECT_ACTIVATE, code, 0, 0, null, null, 0));
    }

    /** case 3：效果无效，卡片大图中央出现缩小的无效图标（MSG_CHAIN_NEGATED / MSG_CHAIN_DISABLED） */
    public void showNegated(int code) {
        enqueue(new EffectRequest(EFFECT_NEGATED, code, 0, 20, null, null, 0));
    }

    /** case 4：卡片大图淡入 */
    public void showFadeIn(int code) {
        enqueue(new EffectRequest(EFFECT_FADEIN, code, 0, 20, null, null, 0));
    }

    /** case 5：特殊召唤，卡片大图自中心放大并淡入（MSG_SPSUMMONING）。
     *  C++ showcarddif 初值为 1（第 7 参才是 difInit；此前误把 1 传入 param 槽导致 dif 初值为 0） */
    public void showSpecialSummon(int code) {
        enqueue(new EffectRequest(EFFECT_SPSUMMON, code, 0, 20, null, null, 1));
    }

    /** case 6：计数器，卡片大图 + 中央缩小的数字图标（MSG_COUNTER CHINT_TURN，number 取 0~24） */
    public void showCounter(int code, int number) {
        enqueue(new EffectRequest(EFFECT_COUNTER, code, number, 20, null, null, 0));
    }

    /** case 7：普通/反转召唤，卡片大图翻面进入（MSG_SUMMONING / MSG_FLIPSUMMONING） */
    public void showSummon(int code) {
        enqueue(new EffectRequest(EFFECT_SUMMON, code, 0, 0, null, null, 0));
    }

    /** case 100：猜拳结果，双手势图自上下向中央靠拢（hand 取 1/2/3 = 剪刀/石头/布） */
    public void showRpsResult(int myHand, int oppHand) {
        // 对齐 duelclient.cpp L529：showcardcode = (res1-1) + ((res2-1)<<16)
        int packed = (clamp(myHand - 1, 0, 2)) | ((clamp(oppHand - 1, 0, 2)) << 16);
        enqueue(new EffectRequest(EFFECT_RPS, 0, packed, 0, null, null, 50));
    }

    /** case 101：按 showcardcode 显示阶段/胜负文字（淡入→停留→淡出） */
    public void showText(int textCode) {
        showText(textCode, null, textCode == TEXT_YOU_WIN || textCode == TEXT_YOU_LOSE ? 110 : 30);
    }

    /**
     * case 101：MSG_WIN 胜负文字，停留 110 帧对齐 C++。
     * 胜利说明对齐 duelclient.cpp L1596-1602：reason<0x10 → "[败者名] 原因"，否则仅"原因"。
     */
    public void showWinText(int textCode, int reason, String vicName) {
        showText(textCode, buildVictoryString(reason, vicName), 110);
    }

    /** 组装胜利说明文本（对齐 duelclient.cpp MSG_WIN 的 vic_buf 构造），无对应文本时返回 null（不显示底框） */
    private String buildVictoryString(int reason, String vicName) {
        StringManager sm = DataManager.get().getStringManager();
        if (sm == null) return null;
        String vic = sm.getVictoryString(reason, "");
        if (vic == null || vic.isEmpty()) return null;
        // reason < 0x10：普通胜利，前缀败者名；否则为特殊效果胜利，仅显示原因文本
        if (reason < 0x10 && vicName != null && !vicName.isEmpty()) {
            return "[" + vicName + "] " + vic;
        }
        return vic;
    }

    /** case 101：自定义文字（如需本地化阶段名，可由调用方传入 StringManager 结果） */
    public void showCustomText(String text) {
        enqueue(new EffectRequest(EFFECT_TEXT, 0, 0, 0, text, null, 30));
    }

    /**
     * 居中动作消息文本（MSG_HINT 宣言类，对齐 duelclient.cpp wACMessage 弹出）：
     * 12sp 小字 TextView + ygopro_base_background 底框，popup_open 展开 → 停留 → popup_close 收起，
     * 总时长 holdFrames=40 帧（17ms/帧，对齐 WaitFrameSignal(40)，按动画倍率速除），汇入串行特效队列。
     */
    public void showActionMessage(String text) {
        if (text == null || text.isEmpty()) return;
        enqueue(new EffectRequest(EFFECT_ACTION_TEXT, 0, 0, 0, text, null, 40));
    }

    private void showText(int textCode, String subText, int holdLen) {
        String text = (textCode >= 0 && textCode < TEXT_TABLE.length) ? TEXT_TABLE[textCode] : "";
        enqueue(new EffectRequest(EFFECT_TEXT, textCode, 0, 0, text, subText, holdLen));
    }

    /**
     * 立即结束并清空当前特效与待播队列。不无条件 dismiss——弹幕/聊天行仍在屏时保留
     * PopupWindow（弹幕与特效共用同一窗口），仅在全空闲时收口关闭；聊天行由
     * clearChatRowLayers/对局结束流程清理。
     */
    public void hide() {
        queue.clear();
        cancelActionText();
        if (view != null) view.stop();
        dismissWindowIfIdle();
    }

    /** 对局结束 / Activity 销毁时调用，无条件释放 PopupWindow 防止窗口泄漏 */
    public void release() {
        queue.clear();
        cancelActionText();
        if (view != null) view.stop();
        dismissWindow();
        window = null;
        view = null;
        rootLayer = null;
        danmakuLayer = null;
        selfChatLayer = null;
        oppChatLayer = null;
        actionTextHost = null;
        actionText = null;
    }

    // ==================== 队列驱动：动画串行播放 ====================

    /** 入队一个特效请求：当前空闲则立即播放，否则排队等待前一段动画结束后再播 */
    private void enqueue(EffectRequest req) {
        queue.offer(req);
        obtainView();     // 确保 PopupWindow/View 就绪并显示
        pumpQueue();
    }

    /** 仅在无动画播放时取出队首请求开播；队列已空则通知引擎并关闭覆盖层。由 onFinish 逐段驱动 */
    private void pumpQueue() {
        if (view == null || view.running || actionTextActive) return;
        EffectRequest req = queue.poll();
        if (req == null) {
            // 先通知引擎队列已排空（引擎可能立即派发下一条消息并入队新动画），
            // 通知后若仍无任何动画/活跃弹幕在屏才关闭覆盖层，避免「关闭→立即重开」闪烁
            if (idleListener != null) idleListener.onIdle();
            dismissWindowIfIdle();
            return;
        }
        updateRegion();
        if (req.type == EFFECT_ACTION_TEXT) {
            playActionText(req);
            return;
        }
        view.startCard(req.type, req.code, req.param, req.holdFrames,
                req.text, req.subText, req.difInit);
    }

    /** 全空闲（队列空、画布动画未播、动作文本不在屏、无活跃弹幕且无在屏聊天行）时关闭覆盖层 */
    private void dismissWindowIfIdle() {
        if (view == null) return;
        if (queue.isEmpty() && !view.running && !actionTextActive
                && (danmakuLayer == null || danmakuLayer.getChildCount() == 0)
                && (selfChatLayer == null || selfChatLayer.getChildCount() == 0)
                && (oppChatLayer == null || oppChatLayer.getChildCount() == 0)) {
            dismissWindow();
        }
    }

    // ==================== PopupWindow 承载 ====================

    private SpecEffectView obtainView() {
        if (view == null) {
            view = new SpecEffectView(activity);
            view.speed = speed;
            // 一段动画自然结束 → 驱动队列中的下一段（队列空则关闭覆盖层）
            view.setOnFinishListener(this::pumpQueue);
            // 根容器：画布视图（卡片/特效动画）+ 弹幕宿主 + 居中动作消息文本，三层叠加于同一 PopupWindow
            rootLayer = new FrameLayout(activity);
            rootLayer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            danmakuLayer = new FrameLayout(activity);
            danmakuLayer.setVisibility(View.GONE);
            rootLayer.addView(danmakuLayer, new FrameLayout.LayoutParams(0, 0,
                    Gravity.TOP | Gravity.START));
            actionTextHost = new FrameLayout(activity);
            actionTextHost.setVisibility(View.GONE);
            actionText = new TextView(activity);
            actionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            actionText.setTextColor(Color.WHITE);
            actionText.setGravity(Gravity.CENTER);
            actionText.setBackgroundResource(R.drawable.ygopro_base_background);
            int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6,
                    activity.getResources().getDisplayMetrics());
            actionText.setPadding(pad, pad / 2, pad, pad / 2);
            actionTextHost.addView(actionText, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            rootLayer.addView(actionTextHost, new FrameLayout.LayoutParams(0, 0,
                    Gravity.TOP | Gravity.START));
            // 分侧聊天行容器：我方/对方 LP 血条正下方各一纵向列表，位置由 obtainChatRowLayer 设定
            selfChatLayer = new LinearLayout(activity);
            selfChatLayer.setOrientation(LinearLayout.VERTICAL);
            selfChatLayer.setVisibility(View.GONE);
            rootLayer.addView(selfChatLayer, new FrameLayout.LayoutParams(0, 0,
                    Gravity.TOP | Gravity.START));
            oppChatLayer = new LinearLayout(activity);
            oppChatLayer.setOrientation(LinearLayout.VERTICAL);
            oppChatLayer.setVisibility(View.GONE);
            rootLayer.addView(oppChatLayer, new FrameLayout.LayoutParams(0, 0,
                    Gravity.TOP | Gravity.START));
            window = new PopupWindow(rootLayer,
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // 纯展示层：不抢焦点、不拦截触摸，事件穿透到下层游戏 UI
            window.setFocusable(false);
            window.setOutsideTouchable(false);
            window.setTouchable(false);
        }
        showWindow();
        return view;
    }

    private void showWindow() {
        if (window == null || window.isShowing()) return;
        View decor = activity.getWindow().getDecorView();
        if (decor == null) return;
        if (decor.getWindowToken() == null) {
            // 窗口 token 未就绪（首帧前/重建中）：延后一帧重试，避免弹幕宿主 View 宽高恒为 0
            decor.post(this::showWindow);
            return;
        }
        try {
            window.showAtLocation(decor, Gravity.NO_GRAVITY, 0, 0);
        } catch (Exception ignored) {
            // 显示失败同样下一帧重试一次（obtainView 每次触发都会再走 showWindow 入口）
            decor.post(this::showWindow);
        }
    }

    private void dismissWindow() {
        try {
            if (window != null && window.isShowing()) window.dismiss();
        } catch (Exception ignored) {
        }
    }

    /** 计算 layout_game_right 在窗口坐标系中的区域：画布特效居中对齐与动作消息文本定位的共同基准 */
    private void updateRegion() {
        View gr = activity.findViewById(R.id.layout_game_right);
        if (gr == null || gr.getWidth() <= 0 || gr.getHeight() <= 0) return;
        int[] loc = new int[2];
        gr.getLocationInWindow(loc);
        regionLeft = loc[0];
        regionTop = loc[1];
        regionW = gr.getWidth();
        regionH = gr.getHeight();
        if (view != null) view.setRegion(regionLeft, regionTop, regionW, regionH);
    }

    // ==================== 居中动作消息文本（wACMessage / stACMessage） ====================

    /** 播一段居中动作文本：展开（popup_open）→ 停留 → 收起（popup_close），总时长 40 帧/倍速 */
    private void playActionText(EffectRequest req) {
        if (actionText == null || actionTextHost == null || regionW <= 0) {
            pumpQueue();
            return;
        }
        actionTextActive = true;
        actionText.setText(req.text);
        FrameLayout.LayoutParams hostLp =
                (FrameLayout.LayoutParams) actionTextHost.getLayoutParams();
        hostLp.leftMargin = regionLeft;
        hostLp.topMargin = regionTop;
        hostLp.width = regionW;
        hostLp.height = regionH;
        actionTextHost.setLayoutParams(hostLp);
        actionTextHost.setVisibility(View.VISIBLE);
        float sp = Math.max(0.25f, speed);
        long total = Math.max(1L, Math.round(req.holdFrames * 17L / sp)); // 40 帧 ≈ 680ms
        long animDur = Math.min(Math.max(1L, Math.round(200L / sp)), total / 3);
        long hold = Math.max(0L, total - animDur * 2);
        Animation open = AnimationUtils.loadAnimation(activity, R.anim.popup_open);
        open.setDuration(animDur);
        actionText.startAnimation(open);
        if (actionTextCloser == null) {
            actionTextCloser = this::startActionTextClose;
        }
        actionTextHost.removeCallbacks(actionTextCloser);
        actionTextHost.postDelayed(actionTextCloser, animDur + hold);
    }

    /** 停留结束：播放收起动画（popup_close），动画播完即交还队列驱动下一段 */
    private void startActionTextClose() {
        if (!actionTextActive) return;
        float sp = Math.max(0.25f, speed);
        Animation close = AnimationUtils.loadAnimation(activity, R.anim.popup_close);
        close.setDuration(Math.max(1L, Math.round(200L / sp)));
        close.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) { }
            @Override public void onAnimationRepeat(Animation animation) { }
            @Override public void onAnimationEnd(Animation animation) { endActionText(); }
        });
        actionText.startAnimation(close);
    }

    private void endActionText() {
        if (!actionTextActive) return;
        actionTextActive = false;
        if (actionText != null) actionText.clearAnimation();
        if (actionTextHost != null) actionTextHost.setVisibility(View.GONE);
        pumpQueue();
    }

    /** 立即终止动作文本展示（清空队列/对局结束）：不驱动下一段，由调用方流程接管 */
    private void cancelActionText() {
        if (actionTextCloser != null && actionTextHost != null) {
            actionTextHost.removeCallbacks(actionTextCloser);
        }
        actionTextActive = false;
        if (actionText != null) actionText.clearAnimation();
        if (actionTextHost != null) actionTextHost.setVisibility(View.GONE);
    }

    // ==================== 弹幕宿主（观战发言 / 系统消息，drawspec 层） ====================

    /**
     * 返回弹幕宿主容器（PopupWindow 层，显示在 GL 曲面之上）：按调用方给定的带顶
     * 窗口坐标 bandTopPx（双方 LP 血条底边）全屏宽定位；bandTopPx<0 时回退 layout_game_right
     * 区域顶边，高度 bandHeightPx 由调用方按「行数 × 行高」给定。
     * 不再依赖 layout_top_info 的布局状态——历史 bug：top_info 未布局时弹幕落回
     * 受 GL 曲面遮挡的 layout_danmaku 且宽恒为 0，永不可见。
     */
    public FrameLayout obtainDanmakuLayer(int bandHeightPx, int bandTopPx) {
        if (activity.isFinishing()) return null;
        obtainView();
        updateRegion();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) danmakuLayer.getLayoutParams();
        lp.leftMargin = 0;
        lp.topMargin = Math.max(0, bandTopPx >= 0 ? bandTopPx : regionTop);
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = Math.max(1, bandHeightPx);
        danmakuLayer.setLayoutParams(lp);
        danmakuLayer.setVisibility(View.VISIBLE);
        return danmakuLayer;
    }

    /** 弹幕移除后调用：全空闲则关闭覆盖层（弹幕不占 isBusy() 消息闸门，不参与串行动画屏障） */
    public void notifyDanmakuRemoved() {
        dismissWindowIfIdle();
    }

    // ==================== 血条下方聊天行（对局玩家分侧聊天，非弹幕滚动） ====================

    /**
     * 取得指定侧的聊天行容器（VERTICAL LinearLayout，PopupWindow 层在 GL 曲面之上）：
     * 以窗口坐标 leftPx/topPx（该侧 LP 血条底边）/widthPx（血条宽）定位，每条聊天一个
     * TextView 自上而下追加。对局玩家（含同队 tag 队友）聊天走本容器；系统/观战消息走弹幕带。
     */
    public LinearLayout obtainChatRowLayer(boolean selfSide, int leftPx, int topPx, int widthPx) {
        if (activity.isFinishing()) return null;
        obtainView();
        LinearLayout layer = selfSide ? selfChatLayer : oppChatLayer;
        if (layer == null) return null;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) layer.getLayoutParams();
        lp.leftMargin = Math.max(0, leftPx);
        lp.topMargin = Math.max(0, topPx);
        lp.width = Math.max(1, widthPx);
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layer.setLayoutParams(lp);
        layer.setVisibility(View.VISIBLE);
        return layer;
    }

    /** 清空双方聊天行（停止聊天/离开决斗界面时调用），并尝试空闲收口关闭覆盖层 */
    public void clearChatRowLayers() {
        if (selfChatLayer != null) selfChatLayer.removeAllViews();
        if (oppChatLayer != null) oppChatLayer.removeAllViews();
        dismissWindowIfIdle();
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** 特效自然结束回调（提升到外层类，避免非静态内部类中出现隐式 static 声明） */
    interface OnFinishListener {
        void onFinish();
    }

    /** 特效队列排空、当前无动画播放时触发一次的回调 */
    public interface OnIdleListener {
        void onIdle();
    }
}
