package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.BitmapUtil;
import ocgcore.DataManager;
import ocgcore.StringManager;

/**
 * 决斗场特效覆盖层：移植 gframe drawing.cpp Game::DrawSpec() 的 showcard 各分支，
 * 在 layout_game_right 区域中央绘制卡片大图展示、发动遮罩光带、效果无效图标、
 * 特殊召唤放大、计数器数字、召唤翻面、猜拳手势、阶段/胜负文字等动态动画。
 *
 * 图层约定（对齐 RPSDialog）：GameFieldView 是 setZOrderOnTop(true) 的 GLSurfaceView，
 * GL 曲面合成在 Activity 窗口之上，普通 View 会被场地遮挡，故本层使用全屏透明、
 * 不拦截触摸的 PopupWindow 承载 Canvas 绘制视图，稳定显示在 GL 曲面之上。
 *
 * 动画驱动：Choreographer 逐帧回调，按 dt*60*speed 推进 showcarddif/showcardp，
 * 刷新率无关且与 GameFieldView 时间驱动动画速度一致。
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
    private SpecEffectView view;
    private float speed = 1f;
    /** 特效请求队列：保证动画串行播放——上一段完全结束后再播下一段，避免多段动画互相打断/同时播出 */
    private final ArrayDeque<EffectRequest> queue = new ArrayDeque<>();
    /** 特效队列排空（无动画播放）回调：供 GameEngine 重开消息闸门，实现「动画播完再弹窗」的串行序列 */
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
     * 供 GameEngine 统一动画屏障即时查询——与 OnIdleListener 互补：idle 是「排空瞬间」的快路径通知，
     * 本方法是任意时刻的状态查询（闸门轮询器每 16ms 调用一次，派发每条消息后也调用一次）。
     */
    public boolean isBusy() {
        return !queue.isEmpty() || (view != null && view.running);
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

    /** case 5：特殊召唤，卡片大图自中心放大并淡入（MSG_SPSUMMONING） */
    public void showSpecialSummon(int code) {
        // C++ showcarddif 初值为 1（第 7 参才是 difInit；此前误把 1 传入 param 槽导致 dif 初值为 0）
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
     * case 101：MSG_WIN 胜负文字，停留时长对齐 C++ 的 110 帧。
     * reason 为通讯中的 victory reason（MSG_WIN 第二字节），winnerName 为胜者名（可空）。
     * 胜利说明对齐 duelclient.cpp L1596-1602：reason<0x10 → "[胜者名] 原因"，否则仅"原因"，
     * 原因文本取自 strings.conf 的 !victory 段（StringManager.getVictoryString）。
     */
    public void showWinText(int textCode, int reason, String winnerName) {
        showText(textCode, buildVictoryString(reason, winnerName), 110);
    }

    /** 组装胜利说明文本（对齐 duelclient.cpp MSG_WIN 的 vic_buf 构造），无对应文本时返回 null（不显示底框） */
    private String buildVictoryString(int reason, String winnerName) {
        StringManager sm = DataManager.get().getStringManager();
        if (sm == null) return null;
        String vic = sm.getVictoryString(reason, "");
        if (vic == null || vic.isEmpty()) return null;
        // reason < 0x10：普通胜利，前缀胜者名；否则为特殊效果胜利，仅显示原因文本
        if (reason < 0x10 && winnerName != null && !winnerName.isEmpty()) {
            return "[" + winnerName + "] " + vic;
        }
        return vic;
    }

    /** case 101：自定义文字（如需本地化阶段名，可由调用方传入 StringManager 结果） */
    public void showCustomText(String text) {
        enqueue(new EffectRequest(EFFECT_TEXT, 0, 0, 0, text, null, 30));
    }

    private void showText(int textCode, String subText, int holdLen) {
        String text = (textCode >= 0 && textCode < TEXT_TABLE.length) ? TEXT_TABLE[textCode] : "";
        enqueue(new EffectRequest(EFFECT_TEXT, textCode, 0, 0, text, subText, holdLen));
    }

    /** 立即结束并清空当前特效与待播队列 */
    public void hide() {
        queue.clear();
        if (view != null) view.stop();
        dismissWindow();
    }

    /** 对局结束 / Activity 销毁时调用，释放 PopupWindow 防止窗口泄漏 */
    public void release() {
        hide();
        window = null;
        view = null;
    }

    // ==================== 队列驱动：动画串行播放 ====================

    /** 入队一个特效请求：当前空闲则立即播放，否则排队等待前一段动画结束后再播 */
    private void enqueue(EffectRequest req) {
        queue.offer(req);
        obtainView();     // 确保 PopupWindow/View 就绪并显示
        pumpQueue();
    }

    /** 仅在无动画播放时取出队首请求开播；队列已空则通知引擎并关闭覆盖层。由 onFinish 逐段驱动，形成序列 */
    private void pumpQueue() {
        if (view == null || view.running) return;
        EffectRequest req = queue.poll();
        if (req == null) {
            // 先通知引擎队列已排空（引擎可能在同一调用栈内立即派发下一条消息并入队新动画），
            // 通知后若仍无动画播放，才关闭覆盖层，避免「关闭→立即重开」的闪烁
            if (idleListener != null) idleListener.onIdle();
            if (queue.isEmpty() && (view == null || !view.running)) dismissWindow();
            return;
        }
        updateRegion(view);
        view.startCard(req.type, req.code, req.param, req.holdFrames,
                req.text, req.subText, req.difInit);
    }

    // ==================== PopupWindow 承载 ====================

    private SpecEffectView obtainView() {
        if (view == null) {
            view = new SpecEffectView(activity);
            view.speed = speed;
            // 一段动画自然结束 → 驱动队列中的下一段（队列空则关闭覆盖层）
            view.setOnFinishListener(this::pumpQueue);
            window = new PopupWindow(view,
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
        if (decor == null || decor.getWindowToken() == null) return;
        try {
            window.showAtLocation(decor, Gravity.NO_GRAVITY, 0, 0);
        } catch (Exception ignored) {
        }
    }

    private void dismissWindow() {
        try {
            if (window != null && window.isShowing()) window.dismiss();
        } catch (Exception ignored) {
        }
    }

    /** 计算 layout_game_right 在窗口坐标系中的区域，供特效居中对齐 */
    private void updateRegion(SpecEffectView v) {
        View gr = activity.findViewById(R.id.layout_game_right);
        if (gr == null || gr.getWidth() <= 0 || gr.getHeight() <= 0) return;
        int[] loc = new int[2];
        gr.getLocationInWindow(loc);
        v.setRegion(loc[0], loc[1], gr.getWidth(), gr.getHeight());
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

    /** 一次特效播放请求（队列元素），字段与 SpecEffectView.startCard 参数一一对应 */
    private static final class EffectRequest {
        final int type, code, param;
        final float holdFrames, difInit;
        final String text, subText;

        EffectRequest(int type, int code, int param, float holdFrames,
                      String text, String subText, float difInit) {
            this.type = type;
            this.code = code;
            this.param = param;
            this.holdFrames = holdFrames;
            this.text = text;
            this.subText = subText;
            this.difInit = difInit;
        }
    }

    // ==================== 绘制视图 ====================

    /**
     * Canvas 逐帧绘制视图：内部状态对应 gframe 的 showcard / showcardcode / showcarddif / showcardp，
     * 坐标以 layout_game_right 区域为基准，卡片大图居中显示。
     */
    private class SpecEffectView extends View {

        // 卡面在 640 高虚拟空间中的占比（drawing.cpp：top=150、CARD_IMG_HEIGHT=287、CARD_IMG_WIDTH=200）
        private static final float V_CARD_TOP = 150f;
        private static final float V_SPACE_H = 640f;
        private static final float CARD_VW = 200f;
        private static final float CARD_VH = 287f;

        private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix flipMatrix = new Matrix();
        private final Map<String, Bitmap> skinCache = new HashMap<>();

        private int effectType = EFFECT_NONE;
        private int cardCode = 0;
        private int param = 0;       // 计数器数字 / 猜拳手势打包 / 文字 code
        private String text = null;
        private String subText = null;
        private float dif = 0f;      // showcarddif
        private float p = 0f;        // showcardp
        private float holdFrames = 0f;
        private float holdCount = 0f;
        private float cardWaitFrames = 0f;   // 卡图迟迟未解码完成的累计帧数（兜底防止动画/闸门永久卡住）
        private float speed = 1f;
        private boolean running = false;
        private boolean frameScheduled = false;
        private long lastNs = 0L;

        // layout_game_right 区域（窗口坐标）与派生的卡片大图表象
        private int regionLeft, regionTop, regionW, regionH;
        private float cardW, cardH, cardLeft, cardTop, cardRight, cardBottom, cx, s;
        private float textCenterY;

        private OnFinishListener finishListener;

        SpecEffectView(android.content.Context context) {
            super(context);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(false);
            shadowPaint.setColor(Color.BLACK);
            shadowPaint.setTextAlign(Paint.Align.CENTER);
            shadowPaint.setFakeBoldText(false);
        }

        void setOnFinishListener(OnFinishListener l) {
            this.finishListener = l;
        }

        void setRegion(int left, int top, int w, int h) {
            this.regionLeft = left;
            this.regionTop = top;
            this.regionW = w;
            this.regionH = h;
            computeGeometry();
        }

        private void computeGeometry() {
            if (regionW <= 0 || regionH <= 0) return;
            cardH = regionH * (CARD_VH / V_SPACE_H);
            cardW = cardH * (CARD_VW / CARD_VH);
            cx = regionLeft + regionW / 2f;
            cardLeft = cx - cardW / 2f;
            cardRight = cx + cardW / 2f;
            cardTop = regionTop + regionH * (V_CARD_TOP / V_SPACE_H);
            cardBottom = cardTop + cardH;
            s = cardH / CARD_VH;                 // 虚拟单位 → 像素（宽高同比例）
            textCenterY = regionTop + regionH * (330f / V_SPACE_H);
        }

        /** 启动一个特效（对齐 duelclient.cpp 各分支的初值设置） */
        void startCard(int type, int code, int param, float holdFrames,
                       String text, String subText, float difInit) {
            this.effectType = type;
            this.cardCode = code;
            this.param = param;
            this.holdFrames = holdFrames;
            this.holdCount = 0f;
            this.text = text;
            this.subText = subText;
            this.dif = difInit;
            this.p = 0f;
            computeGeometry();
            startLoop();
            invalidate();
        }

        void stop() {
            running = false;
            effectType = EFFECT_NONE;
            dif = 0f;
            p = 0f;
            holdCount = 0f;
            invalidate();
        }

        private void startLoop() {
            running = true;
            lastNs = 0L;
            scheduleFrame();
        }

        /** 确保同一时刻只有一个 Choreographer 回调在排队，避免动画切换（同帧 finish→start）时重复投递 */
        private void scheduleFrame() {
            if (!frameScheduled) {
                frameScheduled = true;
                Choreographer.getInstance().postFrameCallback(frameCallback);
            }
        }

        private void finish() {
            running = false;
            effectType = EFFECT_NONE;
            dif = 0f;
            p = 0f;
            holdCount = 0f;
            cardWaitFrames = 0f;
            invalidate();
            if (finishListener != null) finishListener.onFinish();
        }

        private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                frameScheduled = false;
                if (!running) return;
                float dt = lastNs == 0 ? 1f / 60f : (frameTimeNanos - lastNs) / 1e9f;
                lastNs = frameTimeNanos;
                if (dt > 0.1f) dt = 0.1f;
                step(dt * 60f * speed);
                invalidate();
                if (running) scheduleFrame();
            }
        };

        /** 按帧推进动画状态（对齐 DrawSpec 每帧对 showcarddif/showcardp 的递增） */
        private void step(float fr) {
            if (effectType == EFFECT_NONE) return;
            // 卡片大图未解码完成前不推进（对齐 C++ if(showimg==NULL) return）
            if (needsCard(effectType) && card() == null) {
                // 卡图始终解码失败（如卡片不存在）时不能无限等待，否则 GameEngine 消息闸门被永久关闭：
                // 累计约 40 帧后强制结束本段动画，交还控制权（正常卡图解码极快，不会误触发）
                cardWaitFrames += fr;
                if (cardWaitFrames >= 40f) finish();
                return;
            }
            cardWaitFrames = 0f;
            boolean done = false;
            switch (effectType) {
                case EFFECT_ACTIVATE:
                    dif += 15 * fr;
                    if (dif >= CARD_VH) {
                        effectType = EFFECT_ACTIVATE2;
                        dif = 0;
                    }
                    break;
                case EFFECT_ACTIVATE2:
                    dif += 15 * fr;
                    if (dif >= CARD_VW) done = true;
                    break;
                case EFFECT_NEGATED:
                case EFFECT_COUNTER:
                    if (dif < 64) dif += 4 * fr;
                    else done = true;
                    break;
                case EFFECT_FADEIN:
                    if (dif < 255) dif += 17 * fr;
                    else done = true;
                    break;
                case EFFECT_SPSUMMON:
                    if (dif < 127) dif += 9 * fr;
                    else done = true;
                    break;
                case EFFECT_SUMMON:
                    dif += 9 * fr;
                    if (dif > 90) dif = 90;
                    p += fr;
                    if (p >= 60) done = true;
                    break;
                case EFFECT_RPS:
                    if (p < 60) {
                        float dy = -0.333333f * p + 10f;
                        p += fr;
                        if (p < 30) dif += dy * fr;
                    } else done = true;
                    break;
                case EFFECT_TEXT:
                    p += fr;
                    if (p >= dif + 10) done = true;
                    break;
            }
            if (done) {
                if (holdFrames > 0) {
                    holdCount += fr;
                    if (holdCount >= holdFrames) finish();
                } else {
                    finish();
                }
            }
        }

        private boolean needsCard(int type) {
            return type >= EFFECT_ACTIVATE && type <= EFFECT_SUMMON;
        }

        private Bitmap card() {
            return cardCode > 0 ? TextureLoader.get().getCardBitmap(cardCode) : null;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (effectType == EFFECT_NONE || regionW <= 0 || regionH <= 0) return;
            switch (effectType) {
                case EFFECT_ACTIVATE:
                    drawActivate1(canvas);
                    break;
                case EFFECT_ACTIVATE2:
                    drawActivate2(canvas);
                    break;
                case EFFECT_NEGATED:
                    drawNegated(canvas);
                    break;
                case EFFECT_FADEIN:
                    drawFadeIn(canvas);
                    break;
                case EFFECT_SPSUMMON:
                    drawSpSummon(canvas);
                    break;
                case EFFECT_COUNTER:
                    drawCounter(canvas);
                    break;
                case EFFECT_SUMMON:
                    drawSummonFlip(canvas);
                    break;
                case EFFECT_RPS:
                    drawRps(canvas);
                    break;
                case EFFECT_TEXT:
                    drawPhaseText(canvas);
                    break;
            }
            bmpPaint.setAlpha(255);
        }

        // --- case 1：卡片大图 + 遮罩光带自左揭开 ---
        private void drawActivate1(Canvas canvas) {
            Bitmap card = card();
            if (card == null) return;
            canvas.drawBitmap(card, null, cardRect(), bmpPaint);
            Bitmap mask = skin("mask.png");
            if (mask != null) {
                float maskScale = mask.getWidth() / CARD_VH;
                int srcL = clamp((int) ((CARD_VH - dif) * maskScale), 0, mask.getWidth());
                float over = dif > CARD_VW ? dif - CARD_VW : 0;
                int srcR = clamp((int) ((CARD_VH - over) * maskScale), 0, mask.getWidth());
                if (srcR > srcL) {
                    float dstR = cardLeft + Math.min(dif, CARD_VW) * s;
                    Rect src = new Rect(srcL, 0, srcR, mask.getHeight());
                    RectF dst = new RectF(cardLeft, cardTop, dstR, cardBottom);
                    canvas.drawBitmap(mask, src, dst, bmpPaint);
                }
            }
        }

        // --- case 2：遮罩继续向右消失 ---
        private void drawActivate2(Canvas canvas) {
            Bitmap card = card();
            if (card == null) return;
            canvas.drawBitmap(card, null, cardRect(), bmpPaint);
            Bitmap mask = skin("mask.png");
            if (mask != null) {
                float maskScale = mask.getWidth() / CARD_VH;
                int srcR = clamp((int) ((CARD_VW - dif) * maskScale), 0, mask.getWidth());
                if (srcR > 0) {
                    Rect src = new Rect(0, 0, srcR, mask.getHeight());
                    RectF dst = new RectF(cardLeft + dif * s, cardTop, cardRight, cardBottom);
                    canvas.drawBitmap(mask, src, dst, bmpPaint);
                }
            }
        }

        // --- case 3：中央缩小无效图标 ---
        private void drawNegated(Canvas canvas) {
            Bitmap card = card();
            if (card == null) return;
            canvas.drawBitmap(card, null, cardRect(), bmpPaint);
            Bitmap neg = skin("negated.png");
            if (neg != null) {
                Rect src = new Rect(0, 0, neg.getWidth(), neg.getHeight());
                canvas.drawBitmap(neg, src, shrinkRect(), bmpPaint);
            }
        }

        // --- case 4：淡入 ---
        private void drawFadeIn(Canvas canvas) {
            Bitmap card = card();
            if (card == null) return;
            bmpPaint.setAlpha(clamp((int) dif, 0, 255));
            // C++ 使用 154~404 的矩形（略小于整卡）
            RectF dst = new RectF(cardLeft, cardTop + 4 * s, cardRight, cardTop + 254 * s);
            canvas.drawBitmap(card, null, dst, bmpPaint);
            bmpPaint.setAlpha(255);
        }

        // --- case 5：特殊召唤放大 + 淡入 ---
        private void drawSpSummon(Canvas canvas) {
            Bitmap card = card();
            if (card == null) return;
            // (dif<<25)>>24 == dif*2，透明度以两倍速升至不透明
            bmpPaint.setAlpha(clamp((int) (dif * 2), 0, 255));
            float hw = dif * 0.69685f * s;
            float hh = dif * s;
            float ccx = cardLeft + 100 * s;   // regionX(660)
            float ccy = cardTop + 127 * s;    // regionY(277)
            RectF dst = new RectF(ccx - hw, ccy - hh, ccx + hw, ccy + hh);
            canvas.drawBitmap(card, null, dst, bmpPaint);
            bmpPaint.setAlpha(255);
        }

        // --- case 6：计数器数字（number.png 5×5 图集，param 选格） ---
        private void drawCounter(Canvas canvas) {
            Bitmap card = card();
            if (card == null) return;
            canvas.drawBitmap(card, null, cardRect(), bmpPaint);
            Bitmap num = skin("number.png");
            if (num != null) {
                int cell = clamp(param, 0, 24);
                int cw = num.getWidth() / 5, ch = num.getHeight() / 5;
                int col = cell % 5, row = cell / 5;
                Rect src = new Rect(col * cw, row * ch, (col + 1) * cw, (row + 1) * ch);
                canvas.drawBitmap(num, src, shrinkRect(), bmpPaint);
            }
        }

        // --- case 7：翻面进入（透视四边形，Matrix.setPolyToPoly 复现 Draw2DImageQuad） ---
        private void drawSummonFlip(Canvas canvas) {
            Bitmap card = card();
            if (card == null) return;
            float y = (float) Math.sin(dif * Math.PI / 180.0) * CARD_VH * s;
            float baseY = cardTop + 254 * s;
            float spread = (cardH - y) * 0.3f;
            float[] src = {0, 0, card.getWidth(), 0, 0, card.getHeight(), card.getWidth(), card.getHeight()};
            float[] dst = {
                    cardLeft - spread, baseY - y,   // 左上
                    cardRight + spread, baseY - y,  // 右上
                    cardLeft, baseY,                // 左下
                    cardRight, baseY                // 右下
            };
            flipMatrix.setPolyToPoly(src, 0, dst, 0, 4);
            canvas.drawBitmap(card, flipMatrix, bmpPaint);
        }

        // --- case 100：猜拳双手势自上下向中央靠拢 ---
        private void drawRps(Canvas canvas) {
            int myIdx = clamp((param >> 16) & 0x3, 0, 2);
            int oppIdx = clamp(param & 0x3, 0, 2);
            Bitmap my = skin("f" + (myIdx + 1) + ".jpg");
            Bitmap opp = skin("f" + (oppIdx + 1) + ".jpg");
            float handH = regionH * (128f / V_SPACE_H);
            float handW = handH * (89f / 128f);
            float left = cx - handW / 2f, right = cx + handW / 2f;
            float myTop = regionTop + (dif / V_SPACE_H) * regionH;
            float oppTop = regionTop + ((540f - dif) / V_SPACE_H) * regionH;
            if (my != null)
                canvas.drawBitmap(my, null, new RectF(left, myTop, right, myTop + handH), bmpPaint);
            if (opp != null)
                canvas.drawBitmap(opp, null, new RectF(left, oppTop, right, oppTop + handH), bmpPaint);
        }

        // --- case 101：阶段/胜负文字，淡入→停留→淡出并横向滑入滑出 ---
        private void drawPhaseText(Canvas canvas) {
            String str = text != null ? text : "";
            float alpha, off;
            if (p < 10) {
                alpha = p / 10f;
                off = -(1f - p / 10f) * 0.36f * regionW;
            } else if (p < dif) {
                alpha = 1f;
                off = 0f;
            } else if (p < dif + 10) {
                float t = (p - dif) / 10f;
                alpha = 1f - t;
                off = t * 0.36f * regionW;
            } else {
                alpha = 0f;
                off = 0f;
            }
            int a = clamp((int) (alpha * 255), 0, 255);
            if (a <= 0) return;
            float size = regionH * 0.09f;   // 略微缩小阶段文字
            float x = cx + off;
            textPaint.setTextSize(size);
            textPaint.setAlpha(a);
            shadowPaint.setTextSize(size);
            shadowPaint.setAlpha(a);
            float baseline = textCenterY - (textPaint.ascent() + textPaint.descent()) / 2f;
            canvas.drawText(str, x + 2, baseline + 2, shadowPaint);
            canvas.drawText(str, x, baseline, textPaint);

            // 胜利说明（vic_string）：胜负文字下方半透明底框 + 文本（对齐 drawing.cpp L1486-1491）
            if (subText != null && subText.length() > 0
                    && (cardCode == TEXT_YOU_WIN || cardCode == TEXT_YOU_LOSE)) {
                // 胜负说明文字缩小（原 0.045），并整体上移更靠近上方的 YOU WIN/YOU LOSE（原 0.09）
                float subSize = regionH * 0.036f;
                textPaint.setTextSize(subSize);
                float subW = Math.max(textPaint.measureText(subText) + 24, regionW * 0.2f);
                float subY = baseline + regionH * 0.066f;
                bmpPaint.setAlpha(a);
                RectF box = new RectF(cx - subW / 2f, subY - subSize, cx + subW / 2f, subY + subSize * 0.6f);
                Paint boxPaint = new Paint();
                boxPaint.setColor(0xA0000000);
                boxPaint.setAlpha((int) (a * 0.63f));
                canvas.drawRect(box, boxPaint);
                shadowPaint.setTextSize(subSize);
                canvas.drawText(subText, cx + 1, subY + 1, shadowPaint);
                canvas.drawText(subText, cx, subY, textPaint);
                bmpPaint.setAlpha(255);
            }
        }

        // 整卡显示矩形（case 1/2/3/6）
        private RectF cardRect() {
            return new RectF(cardLeft, cardTop, cardRight, cardBottom);
        }

        // 无效/计数器图标的缩小矩形：对齐 drawing.cpp [660±(130-dif), (141+dif)~(397-dif)]
        private RectF shrinkRect() {
            return new RectF(
                    cardLeft + (dif - 30) * s,
                    cardTop + (dif - 9) * s,
                    cardLeft + (230 - dif) * s,
                    cardTop + (247 - dif) * s);
        }

        /**
         * 带 alpha 的皮肤贴图（mask/negated/number/hand）：TextureLoader 默认按 RGB_565 解码会丢失
         * 透明通道，故此处经 BitmapUtil 按默认 ARGB_8888 解码，优先 core skin 目录，回退 assets。
         */
        private Bitmap skin(String name) {
            Bitmap b = skinCache.get(name);
            if (b != null && !b.isRecycled()) return b;
            try {
                b = BitmapUtil.getBitmapFromFile(
                        new File(AppsSettings.get().getCoreSkinPath(), name).getAbsolutePath(), 0, 0);
            } catch (Throwable ignored) {
            }
            if (b == null) {
                b = BitmapUtil.getBitmapFormAssets(getContext(), "data/textures/" + name, 0, 0);
            }
            if (b != null) skinCache.put(name, b);
            return b;
        }
    }
}