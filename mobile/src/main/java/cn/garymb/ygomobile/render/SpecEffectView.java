package cn.garymb.ygomobile.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Choreographer;
import android.view.View;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.utils.BitmapUtil;

/**
 * Canvas 逐帧绘制视图：内部状态对应 gframe 的 showcard / showcardcode / showcarddif / showcardp，
 * 坐标以 layout_game_right 区域为基准，卡片大图居中显示。由 SpecEffectOverlay 创建并驱动。
 */
class SpecEffectView extends View {

    // 卡面在 640 高虚拟空间中的占比（drawing.cpp：top=150、CARD_IMG_HEIGHT=287、CARD_IMG_WIDTH=200）
    private static final float V_CARD_TOP = 150f;
    private static final float V_SPACE_H = 640f;
    // 虚拟空间宽高同基准：保证横屏不受宽度约束、竖屏按宽度自动缩小
    private static final float V_SPACE_W = 640f;
    private static final float CARD_VW = 200f;
    private static final float CARD_VH = 287f;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix flipMatrix = new Matrix();
    private final Map<String, Bitmap> skinCache = new HashMap<>();

    private int effectType = SpecEffectOverlay.EFFECT_NONE;
    private int cardCode = 0;
    private int param = 0;       // 计数器数字 / 猜拳手势打包 / 文字 code
    private String text = null;
    private String subText = null;
    private float dif = 0f;      // showcarddif
    private float p = 0f;        // showcardp
    private float holdFrames = 0f;
    private float holdCount = 0f;
    private float cardWaitFrames = 0f;   // 卡图迟迟未解码完成的累计帧数（兜底防止动画/闸门永久卡住）
    float speed = 1f;
    boolean running = false;
    private boolean frameScheduled = false;
    private long lastNs = 0L;

    // layout_game_right 区域（窗口坐标）与派生的卡片大图表象
    private int regionLeft, regionTop, regionW, regionH;
    private float cardW, cardH, cardLeft, cardTop, cardRight, cardBottom, cx, s;
    private float textCenterY;
    private float vOriginY;      // 虚拟空间 y=0 对应的窗口坐标（宽度受限缩小时垂直居中）

    private SpecEffectOverlay.OnFinishListener finishListener;

    SpecEffectView(android.content.Context context) {
        super(context);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(false);
        shadowPaint.setColor(Color.BLACK);
        shadowPaint.setTextAlign(Paint.Align.CENTER);
        shadowPaint.setFakeBoldText(false);
    }

    void setOnFinishListener(SpecEffectOverlay.OnFinishListener l) {
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
        // 竖屏：大图/阶段文字按区域宽度自动缩小（取高/宽两基准的较小者）；横屏不触发限宽
        s = Math.min(regionH / V_SPACE_H, regionW / V_SPACE_W);
        // 缩小时内容在区域内垂直居中（卡片大图/阶段文字不再钉顶部），横屏时偏移为 0
        vOriginY = regionTop + (regionH - s * V_SPACE_H) / 2f;
        cardH = s * CARD_VH;
        cardW = s * CARD_VW;
        cx = regionLeft + regionW / 2f;
        cardLeft = cx - cardW / 2f;
        cardRight = cx + cardW / 2f;
        cardTop = vOriginY + s * V_CARD_TOP;
        cardBottom = cardTop + cardH;
        textCenterY = vOriginY + s * 330f;
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
        effectType = SpecEffectOverlay.EFFECT_NONE;
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
        effectType = SpecEffectOverlay.EFFECT_NONE;
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
        if (effectType == SpecEffectOverlay.EFFECT_NONE) return;
        // 卡片大图未解码完成前不推进（对齐 C++ if(showimg==NULL) return）；
        // 约 40 帧仍未解码则强制结束，防止 GameEngine 消息闸门被永久关闭
        if (needsCard(effectType) && card() == null) {
            cardWaitFrames += fr;
            if (cardWaitFrames >= 40f) finish();
            return;
        }
        cardWaitFrames = 0f;
        boolean done = false;
        switch (effectType) {
            case SpecEffectOverlay.EFFECT_ACTIVATE:
                dif += 15 * fr;
                if (dif >= CARD_VH) {
                    effectType = SpecEffectOverlay.EFFECT_ACTIVATE2;
                    dif = 0;
                }
                break;
            case SpecEffectOverlay.EFFECT_ACTIVATE2:
                dif += 15 * fr;
                if (dif >= CARD_VW) done = true;
                break;
            case SpecEffectOverlay.EFFECT_NEGATED:
            case SpecEffectOverlay.EFFECT_COUNTER:
                if (dif < 64) dif += 4 * fr;
                else done = true;
                break;
            case SpecEffectOverlay.EFFECT_FADEIN:
                if (dif < 255) dif += 17 * fr;
                else done = true;
                break;
            case SpecEffectOverlay.EFFECT_SPSUMMON:
                if (dif < 127) dif += 9 * fr;
                else done = true;
                break;
            case SpecEffectOverlay.EFFECT_SUMMON:
                dif += 9 * fr;
                if (dif > 90) dif = 90;
                p += fr;
                if (p >= 60) done = true;
                break;
            case SpecEffectOverlay.EFFECT_RPS:
                if (p < 60) {
                    float dy = -0.333333f * p + 10f;
                    p += fr;
                    if (p < 30) dif += dy * fr;
                } else done = true;
                break;
            case SpecEffectOverlay.EFFECT_TEXT:
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
        return type >= SpecEffectOverlay.EFFECT_ACTIVATE && type <= SpecEffectOverlay.EFFECT_SUMMON;
    }

    private Bitmap card() {
        return cardCode > 0 ? TextureLoader.get().getCardBitmap(cardCode) : null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (effectType == SpecEffectOverlay.EFFECT_NONE || regionW <= 0 || regionH <= 0) return;
        switch (effectType) {
            case SpecEffectOverlay.EFFECT_ACTIVATE:
                drawActivate1(canvas);
                break;
            case SpecEffectOverlay.EFFECT_ACTIVATE2:
                drawActivate2(canvas);
                break;
            case SpecEffectOverlay.EFFECT_NEGATED:
                drawNegated(canvas);
                break;
            case SpecEffectOverlay.EFFECT_FADEIN:
                drawFadeIn(canvas);
                break;
            case SpecEffectOverlay.EFFECT_SPSUMMON:
                drawSpSummon(canvas);
                break;
            case SpecEffectOverlay.EFFECT_COUNTER:
                drawCounter(canvas);
                break;
            case SpecEffectOverlay.EFFECT_SUMMON:
                drawSummonFlip(canvas);
                break;
            case SpecEffectOverlay.EFFECT_RPS:
                drawRps(canvas);
                break;
            case SpecEffectOverlay.EFFECT_TEXT:
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

    // --- case 4：淡入（C++ 使用 154~404 的矩形，略小于整卡） ---
    private void drawFadeIn(Canvas canvas) {
        Bitmap card = card();
        if (card == null) return;
        bmpPaint.setAlpha(clamp((int) dif, 0, 255));
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
        // 底边锚定 cardBottom：dif=90 时顶边 = cardBottom - cardH = cardTop，终态矩形与 cardRect() 重合
        float baseY = cardBottom;
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
        float handH = s * 128f;
        float handW = handH * (89f / 128f);
        float left = cx - handW / 2f, right = cx + handW / 2f;
        float myTop = vOriginY + dif * s;
        float oppTop = vOriginY + (540f - dif) * s;
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
        float size = s * 0.09f * V_SPACE_H;   // 阶段文字高度基准 0.09×640，经 s 派生随宽度缩放
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
                && (cardCode == SpecEffectOverlay.TEXT_YOU_WIN || cardCode == SpecEffectOverlay.TEXT_YOU_LOSE)) {
            float subSize = s * 0.036f * V_SPACE_H;   // 胜负说明缩小并上移贴近上方主文字
            textPaint.setTextSize(subSize);
            float subW = Math.max(textPaint.measureText(subText) + 24, regionW * 0.2f);
            float subY = baseline + s * 0.066f * V_SPACE_H;
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
     * 带 alpha 的皮肤贴图（mask/negated/number/hand）：经 BitmapUtil 按 ARGB_8888 解码保留透明通道，
     * 优先 core skin 目录，回退 assets。
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

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
