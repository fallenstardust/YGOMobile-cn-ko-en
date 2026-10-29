package cn.garymb.ygomobile.render.spec;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayDeque;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import ocgcore.DataManager;
import ocgcore.StringManager;

/**
 * 决斗场特效覆盖层：移植 gframe drawing.cpp Game::DrawSpec() 的 showcard 各分支，
 * 在 layout_game_right 区域中央绘制卡片大图展示、发动遮罩光带、效果无效图标、
 * 特殊召唤放大、计数器数字、召唤翻面、猜拳手势、阶段/胜负文字等动态动画。
 *
 * 图层约定（对齐 RPSDialog）：GameFieldView 是 setZOrderOnTop(true) 的 GLSurfaceView，
 * GL 曲面合成在 Activity 窗口之上，普通 View 会被场地遮挡，故本层用不拦截触摸的透明
 * PopupWindow 承载 Canvas 绘制视图（SpecEffectView），稳定显示在 GL 曲面之上。窗口原点恒
 * 固定于 (0,0)、绘制坐标一律用绝对窗口坐标（动画位置与整屏实现逐像素一致），仅把窗口高度
 * 按当前可见内容的底边收放（见 updateWindowFootprint），避免常驻整屏透明悬浮窗被 ColorOS
 * 判为「正在当前页面悬浮显示、可能造成操作无响应」而反复弹窗提示（误点会中断决斗、有被判强退风险）。
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
    /** 提示带容器 + 单行提示文本（对齐 gframe stHintMsg / 原布局 tv_hint_message）：迁至本 PopupWindow
     *  层后显示在 GL 曲面之上、血条下方全屏宽居中，随窗口 SOFT_INPUT_ADJUST_NOTHING 不被输入法顶高；
     *  决斗场不再为其在相机上锚线预留带位（对方手卡可与提示重叠，提示浮于其上，用户规格） */
    private FrameLayout hintHost;
    private TextView hintText;
    /** 居中动作消息文本区域容器（对齐 layout_game_right 窗口矩形），内部 TextView 随文字自适应居中 */
    private FrameLayout actionTextHost;
    private TextView actionText;
    /** 动作消息文本是否在屏（展开/停留/收起全程）：占用串行队列与动画屏障 */
    private boolean actionTextActive;
    private Runnable actionTextCloser;
    /** layout_game_right 窗口坐标区域（画布特效与动作消息文本的共同基准） */
    private int regionLeft, regionTop, regionW, regionH;
    /**
     * 血条下方各带的静置期锚点（键为 {@code BAND_*}，值为窗口坐标带顶）：只由
     * {@link ImePopupAdjuster#isChatInputAtRest()} 为真时的实测值写入，输入法抬高聊天
     * 输入框期间一律沿用此缓存，使聊天/弹幕/提示带不随窗口被键盘压缩而整体上移（用户规格）
     */
    private final android.util.SparseIntArray bandTopLatch = new android.util.SparseIntArray();
    /**
     * 本次抬高周期内的临时锚点：仅在静置缓存缺失（抬高期间才出现的第一条聊天/提示）时
     * 使用，作用是让后续同周期内的带顶不再逐轮漂移；绝不写入 {@link #bandTopLatch}，
     * 否则被键盘压缩的锚点会被当成静置基准永久保留，输入框降回后记录就再也回不到血条下
     *（用户反馈的「滞留在屏幕顶部」根因）
     */
    private final android.util.SparseIntArray bandTopLiftPin = new android.util.SparseIntArray();
    /** 血条下方各带编号（公开供 HUD 侧比较当前锚点后决定是否重锚，见 {@link #getBandTop}） */
    public static final int BAND_SELF_CHAT = 1;
    public static final int BAND_OPP_CHAT = 2;
    public static final int BAND_DANMAKU = 3;
    public static final int BAND_HINT = 4;
    private float speed = 1f;
    /** 特效请求队列：保证动画串行播放——上一段完全结束后再播下一段 */
    private final ArrayDeque<EffectRequest> queue = new ArrayDeque<>();
    /**
     * 重排/快进抑制标志（回放「上一步」渲染冻结屏障的特效侧组成）：置位期间
     * {@link #enqueue} 一律丢弃，任何派发线程/时序遗漏都无法绕过本层入队大图与文字；
     * 置位瞬间同步停止当前动画并清空队列（覆盖层是独立窗口，不受 GL 曲面 renderHold 影响）。
     * volatile：投喂线程经主线程 post 设置，与派发链同线程但防 JIT 提升旧读
     */
    private volatile boolean suppressed;
    /** 特效队列排空（无动画播放）回调：供 GameEngine 重开消息闸门 */
    private OnIdleListener idleListener;

    // ── 覆盖层窗口按「内容底边」收高（规避 ColorOS 整屏悬浮窗遮挡提醒，且不改动任何绘制坐标）──
    // 窗口原点恒为 (0,0)，view.setRegion 与各子层 margin 全部沿用绝对窗口坐标（与原整屏实现逐像素一致），
    // 只是把窗口高度从整屏收到「当前可见内容的最低底边」：特效在播时覆盖到 layout_game_right 下沿，
    // 仅弹幕/聊天在屏时收到顶部横带，全空闲时 dismiss。原点不动 → 动画位置/样式完全不变。
    private boolean footprintPending;                        // 合并同一帧内多次内容变化为一次重排

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
     * 重排抑制开关（由 EngineCallbackDelegate 在 onReplayRenderHold 中随 GL 渲染冻结同批切换）：
     * {@code true} 停止当前动画、清空待播队列并拒绝一切新入队；{@code false} 恢复正常接收。
     * 弹幕/聊天行不走 enqueue，不受影响。
     */
    public void setSuppressed(boolean value) {
        suppressed = value;
        if (value) {
            queue.clear();
            cancelActionText();
            if (view != null) view.stop();
            dismissWindowIfIdle();
        }
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
        hintHost = null;
        hintText = null;
        actionTextHost = null;
        actionText = null;
    }

    // ==================== 队列驱动：动画串行播放 ====================

    /** 入队一个特效请求：当前空闲则立即播放，否则排队等待前一段动画结束后再播；抑制期一律丢弃 */
    private void enqueue(EffectRequest req) {
        if (suppressed) return;
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
            // 再按当前弹幕/聊天是否仍在屏收放窗口：全空闲则关闭，仍有内容则收到内容底边（不再常驻整屏）
            if (idleListener != null) idleListener.onIdle();
            updateWindowFootprint();
            return;
        }
        updateRegion();
        if (req.type == EFFECT_ACTION_TEXT) {
            playActionText(req);
            return;
        }
        view.startCard(req.type, req.code, req.param, req.holdFrames,
                req.text, req.subText, req.difInit);
        // 卡片/特效动画即将在 layout_game_right 区域绘制：先把窗口高度扩展到覆盖该区域下沿
        updateWindowFootprint();
    }

    /**
     * 全空闲（队列空、动画未播、动作文本不在屏、无在屏弹幕/聊天行）时关闭覆盖层，
     * 否则把窗口高度收到当前内容底边——统一交由 updateWindowFootprint 处理。
     */
    private void dismissWindowIfIdle() {
        updateWindowFootprint();
    }

    /** 合并同一帧内多次内容变化为一次收放，post 到 Activity decor（子层刚 addView 量得尺寸后重算） */
    private void scheduleFootprint() {
        if (footprintPending || window == null || activity.isFinishing()) return;
        footprintPending = true;
        View decor = activity.getWindow().getDecorView();
        if (decor == null) {
            footprintPending = false;
            return;
        }
        decor.post(() -> {
            footprintPending = false;
            updateWindowFootprint();
        });
    }

    /**
     * 依当前可见内容（特效动画覆盖 layout_game_right 下沿 / 弹幕带底边 / 两侧聊天行底边）计算
     * 窗口高度并应用：底部无内容则 dismiss。窗口原点与宽度处理保持绝对坐标语义——x 恒 0、宽度取
     * 屏宽（弹幕横带本就全屏宽），只纵向收到内容底边，故 view.setRegion 与子层 margin 的绘制位置
     * 与整屏实现完全一致，动画样式与位置不变。
     */
    private void updateWindowFootprint() {
        if (window == null || activity.isFinishing()) return;
        boolean effectActive = (view != null && view.running) || actionTextActive || !queue.isEmpty();
        int bottom = 0;
        if (effectActive && regionH > 0) bottom = regionTop + regionH;
        bottom = Math.max(bottom, layerBottom(danmakuLayer));
        bottom = Math.max(bottom, layerBottom(selfChatLayer));
        bottom = Math.max(bottom, layerBottom(oppChatLayer));
        bottom = Math.max(bottom, layerBottom(hintHost));
        if (bottom <= 0) {
            dismissWindow();
            return;
        }
        if (!window.isShowing()) {
            showWindow();   // 显示后再由后续布局回调收到内容高，避免对未显示窗口 update
            return;
        }
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int h = Math.min(bottom + (int) (2 * dm.density), dm.heightPixels);
        try {
            window.update(0, 0, dm.widthPixels, h);
        } catch (Exception ignored) {
        }
    }

    /** 某弹幕/聊天子层当前占用的绝对底边（未显示/无内容返回 0）；优先用实测高，退回布局参数高 */
    private int layerBottom(View layer) {
        if (layer == null || layer.getVisibility() != View.VISIBLE) return 0;
        if (!(layer instanceof ViewGroup) || ((ViewGroup) layer).getChildCount() == 0) return 0;
        ViewGroup.LayoutParams raw = layer.getLayoutParams();
        int top = 0, h = 0;
        if (raw instanceof FrameLayout.LayoutParams) {
            top = ((FrameLayout.LayoutParams) raw).topMargin;
            h = ((FrameLayout.LayoutParams) raw).height;
        }
        int measured = layer.getHeight() > 0 ? layer.getHeight() : (h > 0 ? h : 0);
        return top + measured;
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
            // 提示带：全屏宽横带，内部 TextView 自适应宽并水平居中（对齐原布局 tv_hint_message gravity=center），
            // 单行尾部省略；顶边由 showHint 按血条底边（窗口坐标）运行时设定，与分侧聊天行同带区
            hintHost = new FrameLayout(activity);
            hintHost.setVisibility(View.GONE);
            hintText = new TextView(activity);
            hintText.setTextColor(Color.WHITE);
            hintText.setGravity(Gravity.CENTER);
            hintText.setSingleLine(true);
            hintText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            hintText.setShadowLayer(1f, 1f, 1f, Color.BLACK);
            hintText.setBackgroundResource(R.drawable.btn_sci_fi_bg);
            int hp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2,
                    activity.getResources().getDisplayMetrics());
            hintText.setPadding(hp, hp, hp, hp);
            hintHost.addView(hintText, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            rootLayer.addView(hintHost, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START));
            window = new PopupWindow(rootLayer,
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // 纯展示层：不抢焦点、不拦截触摸，事件穿透到下层游戏 UI
            window.setFocusable(false);
            window.setOutsideTouchable(false);
            window.setTouchable(false);
            // 不随输入法 resize/pan：本层以绝对窗口坐标定位聊天行/弹幕，主 Activity 为 adjustResize，
            // 默认透明弹窗会跟随键盘整体顶高，导致决斗中聊天位置被抬高（用户规格：保持原位不动）
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            // 弹幕/聊天子层内容增减改变其尺寸时，合并到下一帧按新的内容底边收放窗口高度
            View.OnLayoutChangeListener contentWatcher =
                    (v, l, t, r, b, ol, ot, obl, obr) -> scheduleFootprint();
            danmakuLayer.addOnLayoutChangeListener(contentWatcher);
            selfChatLayer.addOnLayoutChangeListener(contentWatcher);
            oppChatLayer.addOnLayoutChangeListener(contentWatcher);
            hintHost.addOnLayoutChangeListener(contentWatcher);
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
            // 锚点一律写明为屏幕左上角：NO_GRAVITY 下窗口框由父帧（Activity 内容帧）推导，
            // 输入法弹起把父帧底边压缩时本层可能被重排上移，表现为血条下方聊天记录被抬到
            // 屏幕顶部；写明 TOP|START 后父帧底边变化不影响本层顶缘（用户规格：聊天不随输入法抬升）
            window.showAtLocation(decor, Gravity.TOP | Gravity.START, 0, 0);
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
        // 区域宽度变化 = 旋转/分屏（输入法压缩只动高度）：旧的静置锚点与抬升临时锚点都不再适用，
        // 清空待按新布局重采
        if (regionW != gr.getWidth()) {
            bandTopLatch.clear();
            bandTopLiftPin.clear();
        }
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
        // 动作文本覆盖 layout_game_right 区域：确保窗口高度覆盖到该区域下沿（原点/坐标不变）
        updateWindowFootprint();
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
     * 带顶锚点按输入法状态取用（用户规格：血条下方的聊天记录不随聊天输入框抬高而上移）：
     * <ul>
     * <li>输入框已几何落在静置位：按实测算出的带顶下发并记入静置缓存（下次抬键盘沿用它）；</li>
     * <li>输入框被抬高（或键盘动画途中）：一律只用静置缓存，不把压缩后的实测值写回缓存；
     *  缓存缺失时仅拿本次实测值作临时锚点（同一抬升周期内不再逐轮漂移），输入框降回后由
     *  {@link #getBandTop} 比较→ HUD 侧重锚修正；</li>
     * <li>键盘动画途中且无缓存：照实测下发，但不写缓存（窗口尚未落定，不能当基准）。</li>
     * </ul>
     *
     * @param band     带号（{@code BAND_*}）
     * @param wantTop  本次按实测算出的带顶（窗口坐标）
     * @return 实际下发的带顶
     */
    private int pinBandTop(int band, int wantTop) {
        int safe = Math.max(0, wantTop);
        if (ImePopupAdjuster.isChatInputAtRest()) {
            if (safe > 0) {
                bandTopLatch.put(band, safe);
                bandTopLiftPin.delete(band);
            }
            return safe;
        }
        int latched = bandTopLatch.get(band, 0);
        if (latched > 0) return latched;
        if (!ImePopupAdjuster.isChatInputLifted()) return safe;
        int pin = bandTopLiftPin.get(band, 0);
        if (pin > 0) return pin;
        if (safe > 0) bandTopLiftPin.put(band, safe);
        return safe;
    }

    /**
     * 该带当前实际下发的带顶（窗口坐标）；带未创建/参数不可得返回 -1。供 HUD 侧在聊天输入框
     * 降回静置位后比较「实测血条底边与当前锚点是否已一致」，一致则跳过重建（见重锚路径）。
     */
    public int getBandTop(int band) {
        View layer = bandLayer(band);
        if (layer == null) return -1;
        ViewGroup.LayoutParams raw = layer.getLayoutParams();
        return raw instanceof FrameLayout.LayoutParams
                ? ((FrameLayout.LayoutParams) raw).topMargin : -1;
    }

    /**
     * 只记录静置锚点、不动任何带也不创建窗口：HUD 侧在输入框落定回位后随时回灌（即使当时
     * 并无在屏聊天/弹幕/提示），使下一次抬键盘期间新出现的第一条记录也有正确的静置基准可用，
     * 而不是退回到被键盘压缩后的实测值（用户反馈的「顶在屏幕顶端」）。
     * 非静置期（仍抬高或键盘动画途中）一律不采信，不污染已有基准。
     */
    public void rememberBandAnchor(int band, int topPx) {
        if (topPx <= 0) return;
        if (!ImePopupAdjuster.isChatInputAtRest()) return;
        bandTopLatch.put(band, topPx);
        bandTopLiftPin.delete(band);
    }

    /** 带号到宿主容器视图 */
    private View bandLayer(int band) {
        switch (band) {
            case BAND_SELF_CHAT: return selfChatLayer;
            case BAND_OPP_CHAT: return oppChatLayer;
            case BAND_DANMAKU: return danmakuLayer;
            case BAND_HINT: return hintHost;
            default: return null;
        }
    }

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
        lp.topMargin = pinBandTop(BAND_DANMAKU, bandTopPx >= 0 ? bandTopPx : regionTop);
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
        lp.topMargin = pinBandTop(selfSide ? BAND_SELF_CHAT : BAND_OPP_CHAT, topPx);
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

    // ==================== 提示带（stHintMsg，血条下方全屏宽居中） ====================

    /**
     * 在覆盖层显示单行操作/通讯提示（对齐 gframe stHintMsg / 原布局 tv_hint_message）：
     * 横带取窗口坐标 leftPx/topPx/widthPx（一般为顶部 HUD 的左/底边/宽＝原提示栏所在区，保持
     * “相同位置且居中”），内部 TextView 自适应宽并水平居中；widthPx<=0 时回退全屏宽。textSizeSp
     * 由调用方按横竖屏给定。显示在 GL 曲面之上，随窗口 SOFT_INPUT_ADJUST_NOTHING 不被输入法顶高；
     * 与对方手卡重叠时提示浮于其上（用户接受遮挡关系反转）。
     */
    public void showHint(String text, int leftPx, int topPx, int widthPx, float textSizeSp) {
        if (activity.isFinishing() || text == null || text.isEmpty()) {
            hideHint();
            return;
        }
        obtainView();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) hintHost.getLayoutParams();
        lp.leftMargin = Math.max(0, leftPx);
        lp.topMargin = pinBandTop(BAND_HINT, topPx);
        lp.width = widthPx > 0 ? widthPx : ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        hintHost.setLayoutParams(lp);
        hintText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        hintText.setText(text);
        hintHost.setVisibility(View.VISIBLE);
        updateWindowFootprint();
    }

    /** 隐藏提示带（原布局以 INVISIBLE 恒占位防抖，本层无布局占位概念：直接 GONE 并不影响相机锚线） */
    public void hideHint() {
        if (hintHost != null) hintHost.setVisibility(View.GONE);
        dismissWindowIfIdle();
    }

    /**
     * 命中测试：返回窗口坐标 (winX, winY) 落在某条血条下方聊天行 TextView 内的实例，无则 null。
     * 供决斗内「长按聊天行复制该行文本」的全局触摸观察器使用——聊天行宿主 PopupWindow 事件
     * 穿透（setTouchable(false)），触摸由 Activity 派发后据窗口坐标经此命中（弹窗与 Activity 窗口
     * 同原点 (0,0)，故 dispatchTouchEvent 的 getX/getY 与 getLocationInWindow 同基准可直接比较）。
     */
    public TextView hitTestChatRow(float winX, float winY) {
        TextView hit = hitTestRowIn(selfChatLayer, winX, winY);
        return hit != null ? hit : hitTestRowIn(oppChatLayer, winX, winY);
    }

    private static TextView hitTestRowIn(LinearLayout layer, float winX, float winY) {
        if (layer == null || layer.getVisibility() != View.VISIBLE) return null;
        int[] loc = new int[2];
        for (int i = 0; i < layer.getChildCount(); i++) {
            View child = layer.getChildAt(i);
            if (child.getWidth() <= 0 || child.getHeight() <= 0) continue;
            child.getLocationInWindow(loc);
            if (winX >= loc[0] && winX <= loc[0] + child.getWidth()
                    && winY >= loc[1] && winY <= loc[1] + child.getHeight()) {
                if (child instanceof TextView) return (TextView) child;
            }
        }
        return null;
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
