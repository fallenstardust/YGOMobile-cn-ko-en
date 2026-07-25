package cn.garymb.ygomobile.render;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.loader.ImageLoader;
import ocgcore.enums.CardLocation;

public class GameFieldView extends View implements Choreographer.FrameCallback {
    private static final String TAG = "GameFieldView";

    private static final float FIELD_ASPECT_RATIO = 16f / 9f;
    private static final int ZONE_ROWS = 8;
    private static final int ZONE_COLS = 8;

    private static final float FIELD_PERSPECTIVE_ROT_X = 25f;
    private static final float HAND_CARD_FAN_ANGLE = 3f;
    private static final float HAND_CARD_OVERLAP_RATIO = 0.65f;
    private static final float PILE_OFFSET_Z = 0.8f;
    private static final float CARD_ELEVATION = 4f;

    // gframe 场地坐标范围（materials.cpp：X -0.8~8.7，Y -3.9~3.9，+Y 朝向我方）
    private static final float FIELD_X_MIN = -0.8f;
    private static final float FIELD_X_MAX = 8.7f;
    private static final float FIELD_Y_MIN = -3.9f;
    private static final float FIELD_Y_MAX = 3.9f;

    // 卡片在场地坐标系中的尺寸（gframe 格子 1.1×1.2，卡面略小于格子）
    private static final float CARD_W_F = 0.8f;
    private static final float CARD_H_F = 1.16f;
    private static final float PI_F = 3.1415926f;

    private GameField field;
    private ImageLoader imageLoader;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cmdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float fieldWidth, fieldHeight;
    private float zoneWidth, zoneHeight;
    private float cardWidth, cardHeight;
    private float offsetX, offsetY;

    private float fieldCenterX, fieldCenterY;
    private float fieldTop, fieldBottom, fieldLeft, fieldRight;

    private int selectedPlayer = -1;
    private int selectedLocation = -1;
    private int selectedSequence = -1;
    private int highlightFieldMask = 0;

    private int displayLp0 = 8000;
    private int displayLp1 = 8000;
    private ValueAnimator lpAnimator0;
    private ValueAnimator lpAnimator1;

    private List<ChainLine> chainLines = new ArrayList<>();
    private List<CardAnimState> animatingCards = new ArrayList<>();

    private OnCardClickListener cardClickListener;

    private SciFiRenderer sciFiRenderer;
    private long animTimeMs = 0;

    private boolean animationRunning = false;

    private static class ChainLine {
        float x1, y1, x2, y2;
        int color;

        ChainLine(float x1, float y1, float x2, float y2, int color) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.color = color;
        }
    }

    public static class CardAnimState {
        public int player, location, sequence;
        public float translateX, translateY;
        public float rotation;
        public float alpha;
        public long startTime, duration;
        public boolean finished;
    }

    public interface OnCardClickListener {
        void onCardClick(int player, int location, int sequence);

        void onZoneClick(int player, int location, int sequence);

        void onFieldLongPress(int player, int location, int sequence);
    }

    public GameFieldView(Context context) {
        super(context);
        init();
    }

    public GameFieldView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        sciFiRenderer = new SciFiRenderer();

        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setShadowLayer(2, 1, 1, Color.BLACK);

        selectedPaint.setColor(Color.YELLOW);
        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setStrokeWidth(3);

        dimPaint.setColor(Color.argb(128, 0, 0, 0));

        highlightPaint.setColor(Color.argb(80, 0, 255, 100));
        highlightPaint.setStyle(Paint.Style.FILL);

        cmdPaint.setColor(Color.argb(100, 255, 255, 0));
        cmdPaint.setStyle(Paint.Style.STROKE);
        cmdPaint.setStrokeWidth(2);

        chainPaint.setColor(Color.argb(200, 255, 100, 100));
        chainPaint.setStyle(Paint.Style.STROKE);
        chainPaint.setStrokeWidth(3);

        overlayPaint.setColor(Color.argb(180, 100, 100, 255));
        overlayPaint.setTextSize(10);

        lpPaint.setColor(Color.WHITE);
        lpPaint.setTextSize(20);
        lpPaint.setTypeface(Typeface.DEFAULT_BOLD);
        lpPaint.setShadowLayer(3, 1, 1, Color.BLACK);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setField(GameField field) {
        this.field = field;
        invalidate();
    }

    public void setImageLoader(ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
    }

    public void setCardClickListener(OnCardClickListener listener) {
        this.cardClickListener = listener;
    }

    public void setHighlightFieldMask(int mask) {
        this.highlightFieldMask = mask;
        invalidate();
    }

    public void addChainLine(float x1, float y1, float x2, float y2, int color) {
        chainLines.add(new ChainLine(x1, y1, x2, y2, color));
        invalidate();
    }

    public void clearChainLines() {
        chainLines.clear();
        invalidate();
    }

    public void animateLpChange(int player, int fromLp, int toLp) {
        if (player == 0) {
            if (lpAnimator0 != null && lpAnimator0.isRunning()) lpAnimator0.cancel();
            lpAnimator0 = ValueAnimator.ofInt(fromLp, toLp);
            lpAnimator0.setDuration(800);
            lpAnimator0.setInterpolator(new AccelerateDecelerateInterpolator());
            lpAnimator0.addUpdateListener(a -> {
                displayLp0 = (int) a.getAnimatedValue();
                invalidate();
            });
            lpAnimator0.start();
        } else {
            if (lpAnimator1 != null && lpAnimator1.isRunning()) lpAnimator1.cancel();
            lpAnimator1 = ValueAnimator.ofInt(fromLp, toLp);
            lpAnimator1.setDuration(800);
            lpAnimator1.setInterpolator(new AccelerateDecelerateInterpolator());
            lpAnimator1.addUpdateListener(a -> {
                displayLp1 = (int) a.getAnimatedValue();
                invalidate();
            });
            lpAnimator1.start();
        }
    }

    public void setSelectedCard(int player, int location, int sequence) {
        this.selectedPlayer = player;
        this.selectedLocation = location;
        this.selectedSequence = sequence;
        invalidate();
    }

    public void clearSelection() {
        selectedPlayer = -1;
        selectedLocation = -1;
        selectedSequence = -1;
        highlightFieldMask = 0;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateDimensions(w, h);
    }

    private void calculateDimensions(int w, int h) {
        float viewAspect = (float) w / h;
        if (viewAspect > FIELD_ASPECT_RATIO) {
            fieldHeight = h;
            fieldWidth = h * FIELD_ASPECT_RATIO;
        } else {
            fieldWidth = w;
            fieldHeight = w / FIELD_ASPECT_RATIO;
        }
        offsetX = (w - fieldWidth) / 2f;
        offsetY = (h - fieldHeight) / 2f;

        zoneWidth = fieldWidth / ZONE_COLS;
        zoneHeight = fieldHeight / ZONE_ROWS;
        cardWidth = zoneWidth * 0.82f;
        cardHeight = cardWidth * 1.457f;

        fieldCenterX = fieldWidth / 2f;
        fieldCenterY = fieldHeight / 2f;

        float fieldMargin = fieldHeight * 0.12f;
        fieldTop = fieldMargin;
        fieldBottom = fieldHeight - fieldMargin;
        fieldLeft = fieldWidth * 0.05f;
        fieldRight = fieldWidth * 0.95f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (field == null) return;

        animTimeMs = System.currentTimeMillis();

        field.updateCardAnimation(1);

        canvas.save();
        canvas.translate(offsetX, offsetY);

        drawStarfieldBackground(canvas);
        drawFieldWithPerspective(canvas);
        drawDuelHud(canvas);

        canvas.restore();

        if (hasActiveAnimations()) {
            postInvalidateOnAnimation();
        }
    }

    private boolean hasActiveAnimations() {
        for (int p = 0; p < 2; p++) {
            if (hasListAnimation(field.players[p].deck)) return true;
            if (hasListAnimation(field.players[p].hand)) return true;
            if (hasListAnimation(field.players[p].monsterZone)) return true;
            if (hasListAnimation(field.players[p].spellZone)) return true;
            if (hasListAnimation(field.players[p].grave)) return true;
            if (hasListAnimation(field.players[p].removed)) return true;
            if (hasListAnimation(field.players[p].extra)) return true;
        }
        return hasListAnimation(field.overlayCards);
    }

    private boolean hasListAnimation(List<GameField.ClientCard> list) {
        for (GameField.ClientCard c : list) {
            if (c != null && (c.is_moving || c.is_fading)) return true;
        }
        return false;
    }

    private void drawStarfieldBackground(Canvas canvas) {
        sciFiRenderer.drawStarfieldBackground(canvas, fieldWidth, fieldHeight, animTimeMs);
    }

    private void drawFieldWithPerspective(Canvas canvas) {
        canvas.save();

        Matrix perspectiveMatrix = PerspectiveHelper.createPerspectiveMatrix(
                fieldCenterX, fieldCenterY, FIELD_PERSPECTIVE_ROT_X, 0, -80);
        canvas.concat(perspectiveMatrix);

        drawFieldBoard(canvas);
        drawZoneSlots(canvas);
        drawAllCards(canvas);
        drawPileBadges(canvas);
        drawMonsterStatuses(canvas);

        canvas.restore();

        drawChainLines(canvas);
        drawSelection(canvas);
        drawCmdHighlights(canvas);
    }

    // === Game::DrawMisc 的 2D 移植（1024×640 虚拟坐标，等价 C++ Resize） ===

    private static final int[] LP_BAR_COLORS = {
            0xFF00E676, 0xFF40C4FF, 0xFFFFD740, 0xFFE040FB, 0xFFFF6E40
    };

    private float rx(float v) {
        return v / 1024f * fieldWidth;
    }

    private float ry(float v) {
        return v / 640f * fieldHeight;
    }

    private void drawDuelHud(Canvas canvas) {
        GameField.DuelInfo di = field.dInfo;
        int maxLP = field.isTag ? Math.max(di.startLp / 2, 1) : di.startLp;
        if (maxLP <= 0) maxLP = 8000;

        // LP 条（drawing.cpp L935-971：P0 左→右填充，P1 右→左镜像，5 色分层循环）
        drawLpBar(canvas, di.lp[0], maxLP, rx(390), ry(14), rx(625), ry(40), true);
        drawLpBar(canvas, di.lp[1], maxLP, rx(695), ry(14), rx(930), ry(40), false);

        // 回合方 LP 条边框高亮（L995-1001：回合玩家彩色，非回合玩家灰色）
        Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(Math.max(2f, ry(3)));
        boolean p0turn = field.currentPlayer == 0;
        frame.setColor(p0turn ? 0xFF00E5FF : 0xFF555F6A);
        canvas.drawRect(rx(388), ry(12), rx(627), ry(42), frame);
        frame.setColor(p0turn ? 0xFF555F6A : 0xFFFF5252);
        canvas.drawRect(rx(693), ry(12), rx(932), ry(42), frame);

        // LP 数值（L1026-1027）
        Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
        num.setColor(Color.WHITE);
        num.setTypeface(Typeface.DEFAULT_BOLD);
        num.setShadowLayer(3, 1, 1, Color.BLACK);
        num.setTextSize(ry(19));
        num.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.valueOf(di.lp[0]), rx(507), ry(35), num);
        canvas.drawText(String.valueOf(di.lp[1]), rx(812), ry(35), num);

        // 回合数（L1050-1055：灰白渐变矩形 + 数字）
        Paint grad = new Paint();
        grad.setShader(new LinearGradient(0, ry(10), 0, ry(30),
                0x00000000, 0xFFFFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRect(rx(632), ry(10), rx(688), ry(30), grad);
        grad.setShader(new LinearGradient(0, ry(30), 0, ry(50),
                0xFFFFFFFF, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(rx(632), ry(30), rx(688), ry(50), grad);
        Paint turn = new Paint(Paint.ANTI_ALIAS_FLAG);
        turn.setTypeface(Typeface.DEFAULT_BOLD);
        turn.setTextSize(ry(26));
        turn.setTextAlign(Paint.Align.CENTER);
        turn.setColor(0x8000FFFF);
        canvas.drawText(String.valueOf(field.turnCount), rx(661), ry(39), turn);
        turn.setColor(0x80000000);
        canvas.drawText(String.valueOf(field.turnCount), rx(660), ry(38), turn);

        // 计时 + 卡数（L1004-1023）
        Paint info = new Paint(Paint.ANTI_ALIAS_FLAG);
        info.setTypeface(Typeface.DEFAULT_BOLD);
        info.setShadowLayer(2, 1, 1, Color.BLACK);
        info.setTextSize(ry(16));
        info.setTextAlign(Paint.Align.CENTER);
        if (di.timeLimit > 0) {
            drawClockIcon(canvas, rx(586), ry(59), ry(9));
            drawClockIcon(canvas, rx(704), ry(59), ry(9));
            info.setColor(di.timeColor[0]);
            canvas.drawText(String.valueOf(di.timeLeft[0]), rx(610), ry(64), info);
            info.setColor(di.timeColor[1]);
            canvas.drawText(String.valueOf(di.timeLeft[1]), rx(728), ry(64), info);

            drawMiniCover(canvas, rx(537), ry(51), rx(550), ry(70));
            drawMiniCover(canvas, rx(745), ry(51), rx(758), ry(70));
            info.setColor(di.cardCountColor[0]);
            canvas.drawText(String.valueOf(di.cardCount[0]), rx(562), ry(64), info);
            info.setColor(di.cardCountColor[1]);
            canvas.drawText(String.valueOf(di.cardCount[1]), rx(769), ry(64), info);
        } else {
            drawMiniCover(canvas, rx(588), ry(48), rx(601), ry(68));
            drawMiniCover(canvas, rx(697), ry(48), rx(710), ry(68));
            info.setColor(di.cardCountColor[0]);
            canvas.drawText(String.valueOf(di.cardCount[0]), rx(612), ry(64), info);
            info.setColor(di.cardCountColor[1]);
            canvas.drawText(String.valueOf(di.cardCount[1]), rx(722), ry(64), info);
        }

        // LP 变化浮动文字（L982-988：P0 在下半场、P1 在上半场，随 lpccolor 淡出）
        if (field.lpcstring != null && !field.lpcstring.isEmpty()) {
            Paint lpc = new Paint(Paint.ANTI_ALIAS_FLAG);
            lpc.setTypeface(Typeface.DEFAULT_BOLD);
            lpc.setTextSize(ry(42));
            lpc.setTextAlign(Paint.Align.CENTER);
            lpc.setShadowLayer(4, 2, 2, field.lpccolor & 0xFF000000 | 0x00FFFFFF);
            lpc.setColor(field.lpccolor);
            float cy = field.lpplayer == 0 ? ry(495) : ry(185);
            canvas.drawText(field.lpcstring, rx(660), cy, lpc);
        }
    }

    /** LP 条：lp≥maxLP 时分层——整条底色为上一层颜色，再叠加当前层比例（5 色循环） */
    private void drawLpBar(Canvas canvas, int lp, int maxLP, float l, float t, float r, float b, boolean ltr) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF101820);
        canvas.drawRect(l, t, r, b, p);
        if (lp <= 0) return;
        if (lp >= maxLP) {
            int layerCount = lp / maxLP;
            int partialLP = lp % maxLP;
            p.setColor(LP_BAR_COLORS[(layerCount - 1) % 5]);
            canvas.drawRect(l, t, r, b, p);
            if (partialLP > 0) {
                p.setColor(LP_BAR_COLORS[layerCount % 5]);
                float w = (r - l) * partialLP / maxLP;
                if (ltr) canvas.drawRect(l, t, l + w, b, p);
                else canvas.drawRect(r - w, t, r, b, p);
            }
        } else {
            p.setColor(LP_BAR_COLORS[0]);
            float w = (r - l) * lp / maxLP;
            if (ltr) canvas.drawRect(l, t, l + w, b, p);
            else canvas.drawRect(r - w, t, r, b, p);
        }
    }

    private void drawClockIcon(Canvas canvas, float cx, float cy, float radius) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, radius * 0.16f));
        p.setColor(0xFFDDDDDD);
        canvas.drawCircle(cx, cy, radius, p);
        canvas.drawLine(cx, cy, cx, cy - radius * 0.6f, p);
        canvas.drawLine(cx, cy, cx + radius * 0.45f, cy, p);
    }

    private void drawMiniCover(Canvas canvas, float l, float t, float r, float b) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF3A2820);
        canvas.drawRect(l, t, r, b, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(0xFF8A6C50);
        canvas.drawRect(l, t, r, b, p);
    }

    // === Game::DrawStatus 的移植（drawing.cpp L1057-1085 / L1198-1222） ===

    private void drawMonsterStatuses(Canvas canvas) {
        for (int p = 0; p < 2; p++) {
            for (GameField.ClientCard c : field.players[p].monsterZone) {
                if (c == null || c.is_moving) continue;
                if (p == 0) {
                    if (c.code == 0) continue;// 我方：code!=0 即画
                } else {
                    if ((c.position & GameField.POS_FACEUP) == 0) continue; // 对方：仅表侧
                }
                drawCardStatus(canvas, c);
            }
        }
    }

    private void drawCardStatus(Canvas canvas, GameField.ClientCard c) {
        RectF r = projectCard(c.curX, c.curY, c.curZ);
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setShadowLayer(2, 1, 1, Color.BLACK);
        tp.setTextSize(r.width() * 0.26f);
        float baseY = r.bottom + tp.getTextSize();
        float cx = r.centerX();

        String atk = (c.atkString != null && !c.atkString.isEmpty())
                ? c.atkString : String.valueOf(c.attack);
        int atkColor = c.attack > c.baseAttack ? 0xFFFFFF00
                : c.attack < c.baseAttack ? 0xFFFF2090 : 0xFFFFFFFF;

        // 分隔符 "/"
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setColor(0xFFFFFFFF);
        canvas.drawText("/", cx, baseY, tp);

        // 攻击力（右对齐到分隔符左侧）
        tp.setTextAlign(Paint.Align.RIGHT);
        tp.setColor(atkColor);
        canvas.drawText(atk, cx - tp.getTextSize() * 0.25f, baseY, tp);

        tp.setTextAlign(Paint.Align.LEFT);
        if (c.isLink()) {
            // 连接怪：只画连接数（青色）
            String lk = (c.linkString != null && !c.linkString.isEmpty())
                    ? c.linkString : ("L" + c.link);
            tp.setColor(0xFF99FFFF);
            canvas.drawText(lk, cx + tp.getTextSize() * 0.25f, baseY, tp);
        } else {
            // 防御力
            String def = (c.defString != null && !c.defString.isEmpty())
                    ? c.defString : String.valueOf(c.defense);
            tp.setColor(c.defense > c.baseDefense ? 0xFFFFFF00
                    : c.defense < c.baseDefense ? 0xFFFF2090 : 0xFFFFFFFF);
            canvas.drawText(def, cx + tp.getTextSize() * 0.25f, baseY, tp);

            // 等级/阶级：XYZ 紫、调整黄、其他白
            String lv = (c.lvString != null && !c.lvString.isEmpty())
                    ? c.lvString : ("★" + (c.isXyz() ? c.rank : c.level));
            tp.setColor(c.isXyz() ? 0xFFFF80FF
                    : (c.type & 0x1000) != 0 ? 0xFFFFFF00 : 0xFFFFFFFF);
            canvas.drawText(lv, r.left, r.top - tp.getTextSize() * 0.3f, tp);
        }
    }

    /**
     * 场地底板：发光面板 + 网格（对应 gframe DrawBackImage/DrawField 的 2D 等效）
     */
    private void drawFieldBoard(Canvas canvas) {
        RectF board = new RectF(fieldLeft, fieldTop, fieldRight, fieldBottom);
        sciFiRenderer.drawGlowingPanel(canvas, board, 12f);
        sciFiRenderer.drawFieldGrid(canvas, fieldLeft, fieldTop, fieldRight, fieldBottom,
                ZONE_COLS, ZONE_ROWS, animTimeMs);
    }

    // === 场地坐标 →屏幕投影 ===

    private float fieldUnitX() {
        return (fieldRight - fieldLeft) / (FIELD_X_MAX - FIELD_X_MIN);
    }

    private float fieldUnitY() {
        return (fieldBottom - fieldTop) / (FIELD_Y_MAX - FIELD_Y_MIN);
    }

    /**
     * Z 高度换算为向上抬升 + 轻微放大，模拟卡片离开场地表面
     */
    private RectF projectCard(float fx, float fy, float fz) {
        float cx = mapFieldX(fx);
        float cy = mapFieldY(fy) - fz * fieldUnitY() * 0.6f;
        float scale = 1f + fz * 0.08f;
        float w = CARD_W_F * fieldUnitX() * scale;
        float h = CARD_H_F * fieldUnitY() * scale;
        return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    // === Game::DrawCards 的绘制顺序 ===

    private void drawAllCards(Canvas canvas) {
        for (GameField.ClientCard c : field.overlayCards) {
            if (c == null) continue;
            GameField.ClientCard ol = c.overlayTarget;
            if (c.aniFrame > 0) {
                drawClientCard(canvas, c);
            } else if (ol != null && ol.location == 0x04) {
                if (c.sequence < GameField.MAX_LAYER_COUNT) drawClientCard(canvas, c);
            } else {
                drawClientCard(canvas, c);
            }
        }
        for (int p = 0; p < 2; p++) {
            for (GameField.ClientCard c : field.players[p].monsterZone) drawClientCard(canvas, c);
            for (GameField.ClientCard c : field.players[p].spellZone) drawClientCard(canvas, c);
            for (GameField.ClientCard c : field.players[p].deck) drawClientCard(canvas, c);
            for (GameField.ClientCard c : field.players[p].hand) drawClientCard(canvas, c);
            for (GameField.ClientCard c : field.players[p].grave) drawClientCard(canvas, c);
            for (GameField.ClientCard c : field.players[p].removed) drawClientCard(canvas, c);
            for (GameField.ClientCard c : field.players[p].extra) drawClientCard(canvas, c);
        }
    }

    // === Game::DrawCard 的 2D 等效实现 ===

    private void drawClientCard(Canvas canvas, GameField.ClientCard pcard) {
        if (pcard == null) return;

        RectF dst = projectCard(pcard.curX, pcard.curY, pcard.curZ);
        float cx = dst.centerX();
        float cy = dst.centerY();

        // m22 = cos(rX)·cos(rY)：>0 正面可见（drawing.cpp 判定），
        // |cos| 作为翻转/倾斜时的压缩比，形成翻面动画
        float cosX = (float) Math.cos(pcard.curRotX);
        float cosY = (float) Math.cos(pcard.curRotY);
        boolean showFront = cosX * cosY > 0f;
        float wScale = Math.max(0.04f, Math.abs(cosY));
        float hScale = Math.max(0.04f, Math.abs(cosX));

        canvas.save();
        canvas.translate(cx, cy);
        // rZ：对方 π→180°，守备 ∓π/2→∓90°
        canvas.rotate((float) Math.toDegrees(pcard.curRotZ));
        canvas.scale(wScale, hScale);

        RectF local = new RectF(-dst.width() / 2f, -dst.height() / 2f,
                dst.width() / 2f, dst.height() / 2f);

        int alpha = (int) Math.max(0, Math.min(255, pcard.curAlpha));
        Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setAlpha(alpha);

        if (!pcard.is_moving) {
            sciFiRenderer.drawCardShadow(canvas, local.left, local.top, local.width(), local.height());
        }

        if (showFront) {
            // 移动中卡码为0时用 chain_code 兜底（drawing.cpp L617）
            int code = pcard.code;
            if (code == 0 && pcard.is_moving) code = pcard.chain_code;
            Bitmap bmp = null;
            if (code > 0) {
                bmp = TextureLoader.get().loadBitmapScaled(
                        "../pics/" + code + ".jpg", (int) local.width(), (int) local.height());
            }
            if (bmp != null) {
                canvas.drawBitmap(bmp, null, local, cardPaint);
                int borderColor = sciFiRenderer.getCardBorderColor(pcard.type, true);
                sciFiRenderer.drawCardBevel(canvas, local.left, local.top,
                        local.width(), local.height(), borderColor);
            } else {
                Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                bg.setShader(new LinearGradient(local.left, local.top, local.left, local.bottom,
                        0xFF2A1A0A, 0xFF1A0A00, Shader.TileMode.CLAMP));
                bg.setAlpha(alpha);
                canvas.drawRect(local, bg);
                if (code > 0) {
                    textPaint.setTextSize(local.width() * 0.22f);
                    String s = String.valueOf(code);
                    canvas.drawText(s, -textPaint.measureText(s) / 2f, 0, textPaint);
                }
            }
        } else {
            Bitmap coverBmp = TextureLoader.get().getCardCover();
            if (coverBmp != null) {
                canvas.drawBitmap(coverBmp, null, local, cardPaint);
            } else {
                Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                bg.setShader(new LinearGradient(local.left, local.top, local.right, local.bottom,
                        0xFF3A2820, 0xFF1A1008, Shader.TileMode.CLAMP));
                bg.setAlpha(alpha);
                canvas.drawRect(local, bg);
            }
            sciFiRenderer.drawCardBevel(canvas, local.left, local.top,
                    local.width(), local.height(), 0xFF4A3728);
        }

        // 移动中跳过装饰（drawing.cpp：if(pcard->is_moving) return;）
        if (!pcard.is_moving) {
            drawCardDecorations(canvas, pcard, local);
        }
        canvas.restore();
    }

    private void drawCardDecorations(Canvas canvas, GameField.ClientCard pcard, RectF r) {
        if (pcard.is_selectable) {
            Paint sel = new Paint(Paint.ANTI_ALIAS_FLAG);
            sel.setStyle(Paint.Style.STROKE);
            sel.setStrokeWidth(3f);
            float pulse = pcard.is_selected ? 1f
                    : (float) (0.5 + 0.5 * Math.sin(animTimeMs * 0.005));
            sel.setColor(Color.argb((int) (pulse * 255), 255, 255, 0));
            canvas.drawRect(r.left - 2, r.top - 2, r.right + 2, r.bottom + 2, sel);
        }
        if (pcard.is_highlighting) {
            Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
            hl.setStyle(Paint.Style.STROKE);
            hl.setStrokeWidth(3f);
            hl.setColor(0xFF00FFFF);
            canvas.drawRect(r.left - 2, r.top - 2, r.right + 2, r.bottom + 2, hl);
        }
        if (pcard.is_showequip || pcard.is_showtarget || pcard.is_showchaintarget) {
            Paint mk = new Paint(Paint.ANTI_ALIAS_FLAG);
            mk.setStyle(Paint.Style.STROKE);
            mk.setStrokeWidth(2f);
            mk.setColor(0xFFFF4444);
            float pulse = (float) (0.5 + 0.5 * Math.sin(animTimeMs * 0.006));
            mk.setAlpha((int) (pulse * 255));
            canvas.drawRect(r.left - 1, r.top - 1, r.right + 1, r.bottom + 1, mk);
        }
        if (pcard.isMonster() && pcard.isFaceUp() && (pcard.location & 0x04) != 0) {
            textPaint.setTextSize(r.width() * 0.18f);
            textPaint.setColor(Color.WHITE);
            String atkDef = pcard.atkString + "/" + pcard.defString;
            float tw = textPaint.measureText(atkDef);
            Paint bg = new Paint();
            bg.setColor(0x80000000);
            canvas.drawRect(-tw / 2f - 2, r.bottom - r.width() * 0.22f,
                    tw / 2f + 2, r.bottom, bg);
            canvas.drawText(atkDef, -tw / 2f, r.bottom - 3, textPaint);
        }
        if (pcard.overlayed != null && !pcard.overlayed.isEmpty()) {
            sciFiRenderer.drawCountBadge(canvas,
                    r.right - r.width() * 0.15f, r.bottom - r.height() * 0.1f,
                    "×" + pcard.overlayed.size(), 0xCC4444CC);
        }
    }

    // === 区域框（materials.cpp 顶点）===

    private RectF getZoneRectLocalF(int player, int location, int sequence) {
        float cxF, cyF;
        if (location == 0x04) {
            if (player == 0) {
                cxF = sequence < 5 ? 1.75f + 1.1f * sequence : (sequence == 5 ? 2.85f : 5.05f);
                cyF = sequence < 5 ? 1.4f : 0f;
            } else {
                cxF = sequence < 5 ? 6.15f - 1.1f * sequence : (sequence == 5 ? 5.05f : 2.85f);
                cyF = sequence < 5 ? -1.4f : 0f;
            }
        } else if (location == 0x08) {
            if (player == 0) {
                cxF = sequence < 5 ? 1.75f + 1.1f * sequence : 0.6f;
                cyF = sequence < 5 ? 2.6f : 2.0f;
            } else {
                cxF = sequence < 5 ? 6.15f - 1.1f * sequence : 7.3f;
                cyF = sequence < 5 ? -2.6f : -2.0f;
            }
        } else {
            RectF pile = getPileRectLocal(player, location);
            return pile;
        }
        float w = CARD_W_F * fieldUnitX();
        float h = CARD_H_F * fieldUnitY();
        float cx = mapFieldX(cxF);
        float cy = mapFieldY(cyF);
        return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    private void drawZoneSlots(Canvas canvas) {
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < 7; i++) {
                RectF r = getZoneRectLocalF(p, 0x04, i);
                if (r != null) drawZoneSlot(canvas, r.left, r.top, r.width(), r.height());
            }
            for (int i = 0; i <= 5; i++) {
                RectF r = getZoneRectLocalF(p, 0x08, i);
                if (r != null) drawZoneSlot(canvas, r.left, r.top, r.width(), r.height());
            }
            for (int loc : new int[]{0x01, 0x10, 0x20, 0x40}) {
                RectF r = getPileRectLocal(p, loc);
                if (r != null) drawZoneSlot(canvas, r.left, r.top, r.width(), r.height());
            }
        }
    }

    private void drawPileBadges(Canvas canvas) {
        int[][] pileColors = {
                {0x01, 0xFF00FF88}, {0x10, 0xFFFF8800},
                {0x20, 0xFFAA0044}, {0x40, 0xFF8800AA}};
        for (int p = 0; p < 2; p++) {
            for (int[] pc : pileColors) {
                RectF r = getPileRectLocal(p, pc[0]);
                if (r == null) continue;
                int count = field.getCardCount(p, pc[0]);
                if (count > 0) {
                    sciFiRenderer.drawCountBadge(canvas, r.centerX(), r.centerY(),
                            String.valueOf(count), pc[1]);
                }
                Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                labelPaint.setColor(0xAA00E5FF);
                labelPaint.setTextSize(r.width() * 0.2f);
                labelPaint.setTextAlign(Paint.Align.CENTER);
                float labelY = (p == 0) ? r.bottom + r.width() * 0.25f : r.top - r.width() * 0.1f;
                canvas.drawText(getPileLabel(pc[0]), r.centerX(), labelY, labelPaint);
            }
        }
    }

    private void drawZoneSlot(Canvas canvas, float x, float y, float w, float h) {
        Paint slotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        slotPaint.setStyle(Paint.Style.STROKE);
        slotPaint.setStrokeWidth(1f);
        float pulse = (float) (0.15 + 0.1 * Math.sin(animTimeMs * 0.002));
        slotPaint.setColor(Color.argb((int) (pulse * 255), 0, 200, 240));
        canvas.drawRect(x, y, x + w, y + h, slotPaint);

        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setStyle(Paint.Style.FILL);
        innerPaint.setColor(0x0800E5FF);
        canvas.drawRect(x, y, x + w, y + h, innerPaint);
    }

    private void drawPileZone3D(Canvas canvas, int player, boolean flipped) {
        // 坐标取自 materials.cpp vFieldDeck/vFieldGrave/vFieldRemove/vFieldExtra（MR4，rule=1）
        drawCardPile(canvas, player, CardLocation.Deck.value(), 0xFF00FF88);
        drawCardPile(canvas, player, CardLocation.Extra.value(), 0xFF8800AA);
        drawCardPile(canvas, player, CardLocation.Grave.value(), 0xFFFF8800);
        drawCardPile(canvas, player, CardLocation.Removed.value(), 0xFFAA0044);
    }

    private float mapFieldX(float fx) {
        return fieldLeft + (fx - FIELD_X_MIN) / (FIELD_X_MAX - FIELD_X_MIN) * (fieldRight - fieldLeft);
    }

    private float mapFieldY(float fy) {
        return fieldTop + (fy - FIELD_Y_MIN) / (FIELD_Y_MAX - FIELD_Y_MIN) * (fieldBottom - fieldTop);
    }

    private RectF getPileRectLocal(int player, int location) {
        float x1, y1, x2, y2;
        if (player == 0) {
            if (location == CardLocation.Deck.value()) {
                x1 = 6.9f;
                y1 = 2.7f;
                x2 = 7.7f;
                y2 = 3.9f;
            } else if (location == CardLocation.Grave.value()) {
                x1 = 6.9f;
                y1 = 1.4f;
                x2 = 7.7f;
                y2 = 2.6f;
            } else if (location == CardLocation.Removed.value()) {
                x1 = 6.9f;
                y1 = 0.1f;
                x2 = 7.7f;
                y2 = 1.3f;
            } else if (location == CardLocation.Extra.value()) {
                x1 = 0.2f;
                y1 = 2.7f;
                x2 = 1.0f;
                y2 = 3.9f;
            } else {
                return null;
            }
        } else {
            if (location == CardLocation.Deck.value()) {
                x1 = 0.2f;
                y1 = -3.9f;
                x2 = 1.0f;
                y2 = -2.7f;
            } else if (location == CardLocation.Grave.value()) {
                x1 = 0.2f;
                y1 = -2.6f;
                x2 = 1.0f;
                y2 = -1.4f;
            } else if (location == CardLocation.Removed.value()) {
                x1 = 0.2f;
                y1 = -1.3f;
                x2 = 1.0f;
                y2 = -0.1f;
            } else if (location == CardLocation.Extra.value()) {
                x1 = 6.9f;
                y1 = -3.9f;
                x2 = 7.7f;
                y2 = -2.7f;
            } else {
                return null;
            }
        }
        return new RectF(mapFieldX(x1), mapFieldY(y1), mapFieldX(x2), mapFieldY(y2));
    }

    private void drawCardPile(Canvas canvas, int player, int location, int indicatorColor) {
        RectF r = getPileRectLocal(player, location);
        if (r == null) return;

        float x = r.left;
        float y = r.top;
        float pileW = r.width();
        float pileH = r.height();

        drawZoneSlot(canvas, x, y, pileW, pileH);

        int count = field.getCardCount(player, location);
        if (count > 0) {
            // 模拟 client_field GetCardLocation 中 Z = 0.01f + 0.01f * sequence 的堆叠抬升
            int maxVisible = Math.min(count, 5);
            float lift = pileH * 0.012f;
            for (int i = 0; i < maxVisible; i++) {
                float dx = i * lift;
                float dy = -i * lift;
                Bitmap coverBmp = TextureLoader.get().getCardCover();
                if (coverBmp != null) {
                    Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    coverPaint.setAlpha(200 - i * 20);
                    sciFiRenderer.drawCardShadow(canvas, x + dx, y + dy, pileW, pileH);
                    canvas.drawBitmap(coverBmp, null,
                            new RectF(x + dx, y + dy, x + pileW + dx, y + pileH + dy),
                            coverPaint);
                }
            }

            sciFiRenderer.drawCountBadge(canvas,
                    x + pileW / 2f, y + pileH / 2f,
                    String.valueOf(count), indicatorColor);
        }

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xAA00E5FF);
        labelPaint.setTextSize(pileW * 0.2f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        String label = getPileLabel(location);
        float labelY = (player == 0) ? y + pileH + pileW * 0.25f : y - pileW * 0.1f;
        canvas.drawText(label, x + pileW / 2f, labelY, labelPaint);
    }

    private String getPileLabel(int location) {
        if (location == CardLocation.Deck.value()) return "DECK";
        if (location == CardLocation.Grave.value()) return "GRAVE";
        if (location == CardLocation.Removed.value()) return "BANISH";
        if (location == CardLocation.Extra.value()) return "EXTRA";
        return "";
    }

    private void drawHandCards3D(Canvas canvas, int player, boolean flipped) {
        List<GameField.ClientCard> hand = field.players[player].hand;
        int handCount = 0;
        for (GameField.ClientCard card : hand) {
            if (card != null) handCount++;
        }
        if (handCount == 0) return;

        float handCardW = cardWidth * 1.1f;
        float handCardH = cardHeight * 1.1f;
        float spacing = handCardW * HAND_CARD_OVERLAP_RATIO;
        float totalHandWidth = handCount * spacing;
        if (totalHandWidth > fieldWidth * 0.8f) {
            spacing = (fieldWidth * 0.8f) / handCount;
            totalHandWidth = fieldWidth * 0.8f;
        }
        float startX = (fieldWidth - totalHandWidth) / 2f;

        float handY;
        if (flipped) {
            handY = fieldTop - handCardH * 0.5f;
        } else {
            handY = fieldBottom - handCardH * 0.3f;
        }

        int idx = 0;
        for (int i = 0; i < hand.size(); i++) {
            GameField.ClientCard card = hand.get(i);
            if (card == null) continue;

            float x = startX + idx * spacing;
            float y = handY;

            float centerOffset = (idx - (handCount - 1) / 2f);
            float fanAngle = centerOffset * HAND_CARD_FAN_ANGLE;
            float liftY = -Math.abs(centerOffset) * 1.5f;
            float elevZ = CARD_ELEVATION + Math.abs(centerOffset) * 0.5f;

            if (card.is_selectable) {
                liftY -= 8f;
                elevZ += 4f;
            }

            canvas.save();
            float pivotX = x + handCardW / 2f;
            float pivotY = y + handCardH;
            canvas.rotate(fanAngle, pivotX, pivotY);

            drawCard3D(canvas, card, x, y + liftY, handCardW, handCardH, flipped, elevZ);

            canvas.restore();
            idx++;
        }
    }

    private void drawCard3D(Canvas canvas, GameField.ClientCard card,
                            float x, float y, float w, float h, boolean flipped, float elevation) {
        canvas.save();

        sciFiRenderer.drawCardShadow(canvas, x, y, w, h);

        float cx = x + w / 2f;
        float cy = y + h / 2f;

        if (flipped) {
            canvas.rotate(180, cx, cy);
        }

        boolean isDef = !card.isAttack();
        if (isDef) {
            canvas.rotate(90, cx, cy);
            float temp = w;
            w = h;
            h = temp;
            x = cx - w / 2f;
            y = cy - h / 2f;
        }

        RectF dst = new RectF(x, y, x + w, y + h);

        if (card.isFaceUp() && card.code > 0 && imageLoader != null) {
            java.io.File imgFile = imageLoader.getImageFile(card.code);
            if (imgFile != null) {
                Bitmap cardBmp = TextureLoader.get().loadBitmapScaled(
                        "../pics/" + card.code + ".jpg", (int) w, (int) h);
                if (cardBmp != null) {
                    canvas.drawBitmap(cardBmp, null, dst, paint);
                    int borderColor = sciFiRenderer.getCardBorderColor(card.type, true);
                    sciFiRenderer.drawCardBevel(canvas, x, y, w, h, borderColor);
                    drawCardOverlayInfo(canvas, card, x, y, w, h);
                    canvas.restore();
                    return;
                }
            }
        }

        if (card.isFaceUp()) {
            Paint cardBg = new Paint(Paint.ANTI_ALIAS_FLAG);
            cardBg.setShader(new LinearGradient(x, y, x, y + h,
                    0xFF2A1A0A, 0xFF1A0A00, Shader.TileMode.CLAMP));
            canvas.drawRect(dst, cardBg);
            textPaint.setTextSize(w * 0.25f);
            String codeStr = String.valueOf(card.code);
            canvas.drawText(codeStr,
                    x + w / 2f - textPaint.measureText(codeStr) / 2f,
                    y + h / 2f, textPaint);
            int borderColor = sciFiRenderer.getCardBorderColor(card.type, true);
            sciFiRenderer.drawCardBevel(canvas, x, y, w, h, borderColor);
        } else {
            Bitmap coverBmp = TextureLoader.get().getCardCover();
            if (coverBmp != null) {
                canvas.drawBitmap(coverBmp, null, dst, paint);
            } else {
                Paint coverBg = new Paint(Paint.ANTI_ALIAS_FLAG);
                coverBg.setShader(new LinearGradient(x, y, x + w, y + h,
                        0xFF3A2820, 0xFF1A1008, Shader.TileMode.CLAMP));
                canvas.drawRect(dst, coverBg);
            }
            sciFiRenderer.drawCardBevel(canvas, x, y, w, h, 0xFF4A3728);
        }

        drawCardOverlayInfo(canvas, card, x, y, w, h);
        canvas.restore();
    }

    private void drawCardOverlayInfo(Canvas canvas, GameField.ClientCard card,
                                     float x, float y, float w, float h) {
        if (card.is_selectable) {
            Paint selGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
            selGlow.setStyle(Paint.Style.STROKE);
            selGlow.setStrokeWidth(3f);
            float pulse = (float) (0.5 + 0.5 * Math.sin(animTimeMs * 0.005));
            selGlow.setColor(Color.argb((int) (pulse * 255), 255, 255, 0));
            selGlow.setShadowLayer(6, 0, 0, 0xFFFF0000);
            canvas.drawRect(x - 2, y - 2, x + w + 2, y + h + 2, selGlow);
        }

        if (card.isMonster() && card.isFaceUp()) {
            textPaint.setTextSize(w * 0.18f);
            textPaint.setColor(Color.WHITE);
            String atkDef = card.atkString + "/" + card.defString;

            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(0x80000000);
            float textW = textPaint.measureText(atkDef);
            canvas.drawRect(x + w / 2f - textW / 2f - 2, y + h - w * 0.22f,
                    x + w / 2f + textW / 2f + 2, y + h, bgPaint);
            canvas.drawText(atkDef,
                    x + w / 2f - textW / 2f,
                    y + h - 3, textPaint);
        }

        if (card.overlayCards != null && !card.overlayCards.isEmpty()) {
            sciFiRenderer.drawCountBadge(canvas,
                    x + w - w * 0.15f, y + h - h * 0.1f,
                    "×" + card.overlayCards.size(), 0xCC4444CC);
        }

        if (card.equipCard != null) {
            Paint equipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            equipPaint.setColor(0xCCCCFF00);
            equipPaint.setTextSize(w * 0.25f);
            canvas.drawText("⚔", x + 2, y + w * 0.25f, equipPaint);
        }

        if (card.is_showequip || card.is_showtarget || card.is_showchaintarget) {
            Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            markerPaint.setStyle(Paint.Style.STROKE);
            markerPaint.setStrokeWidth(2f);
            markerPaint.setColor(0xFFFF4444);
            float pulse = (float) (0.5 + 0.5 * Math.sin(animTimeMs * 0.006));
            markerPaint.setAlpha((int) (pulse * 255));
            canvas.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, markerPaint);
        }
    }

    private void drawCmdHighlights(Canvas canvas) {
        if (field == null) return;
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < GameField.MAX_MONSTER_ZONE; i++) {
                GameField.ClientCard card = field.getCard(p, 0x04, i);
                if (card != null && card.cmdFlag != 0) {
                    RectF r = getZoneRect(p, 0x04, i);
                    if (r != null) {
                        cmdPaint.setColor(getCmdColor(card.cmdFlag));
                        canvas.drawRect(r, cmdPaint);
                    }
                }
            }
            for (int i = 0; i < GameField.MAX_SPELL_ZONE; i++) {
                GameField.ClientCard card = field.getCard(p, 0x08, i);
                if (card != null && card.cmdFlag != 0) {
                    RectF r = getZoneRect(p, 0x08, i);
                    if (r != null) {
                        cmdPaint.setColor(getCmdColor(card.cmdFlag));
                        canvas.drawRect(r, cmdPaint);
                    }
                }
            }
        }
    }

    private int getCmdColor(int flag) {
        if ((flag & 0x0040) != 0) return Color.argb(100, 255, 50, 50);
        if ((flag & 0x0001) != 0) return Color.argb(100, 255, 200, 50);
        if ((flag & 0x0004) != 0) return Color.argb(100, 50, 200, 255);
        if ((flag & 0x0002) != 0) return Color.argb(100, 50, 255, 100);
        return Color.argb(80, 255, 255, 100);
    }

    private void drawChainLines(Canvas canvas) {
        for (ChainLine line : chainLines) {
            chainPaint.setColor(line.color);
            canvas.drawLine(line.x1, line.y1, line.x2, line.y2, chainPaint);
            float angle = (float) Math.atan2(line.y2 - line.y1, line.x2 - line.x1);
            float arrowLen = 8;
            canvas.drawLine(line.x2, line.y2,
                    line.x2 - arrowLen * (float) Math.cos(angle - 0.4f),
                    line.y2 - arrowLen * (float) Math.sin(angle - 0.4f), chainPaint);
            canvas.drawLine(line.x2, line.y2,
                    line.x2 - arrowLen * (float) Math.cos(angle + 0.4f),
                    line.y2 - arrowLen * (float) Math.sin(angle + 0.4f), chainPaint);
        }
    }

    private void drawSelection(Canvas canvas) {
        if (selectedPlayer < 0 || selectedLocation < 0 || selectedSequence < 0) return;

        RectF cardRect = getCardRect(selectedPlayer, selectedLocation, selectedSequence);
        if (cardRect != null) {
            Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            selPaint.setStyle(Paint.Style.STROKE);
            selPaint.setStrokeWidth(3f);
            selPaint.setColor(0xFFFFFF00);
            selPaint.setShadowLayer(8, 0, 0, 0xFFFF0000);
            canvas.drawRect(cardRect, selPaint);
        }
    }

    private RectF getZoneRect(int player, int location, int sequence) {
        return getZoneRectLocalF(player, location, sequence);
    }

    private RectF getCardRect(int player, int location, int sequence) {
        RectF r = getZoneRect(player, location, sequence);
        if (r != null) {
            r.offset(offsetX, offsetY);
        }
        return r;
    }

    private void drawFieldHighlight(Canvas canvas) {
        if (highlightFieldMask == 0) return;
        int mask = highlightFieldMask;

        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                RectF r = getZoneRect(0, 0x04, i);
                if (r != null) sciFiRenderer.drawZoneHighlight(canvas, r, 0x00FF64, animTimeMs);
            }
        }
        for (int i = 0; i < 6; i++) {
            if ((mask & (1 << (8 + i))) != 0) {
                RectF r = getZoneRect(0, 0x08, i);
                if (r != null) sciFiRenderer.drawZoneHighlight(canvas, r, 0x00FF64, animTimeMs);
            }
        }

        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << (16 + i))) != 0) {
                RectF r = getZoneRect(1, 0x04, i);
                if (r != null) sciFiRenderer.drawZoneHighlight(canvas, r, 0x00FF64, animTimeMs);
            }
        }
        for (int i = 0; i < 6; i++) {
            if ((mask & (1 << (24 + i))) != 0) {
                RectF r = getZoneRect(1, 0x08, i);
                if (r != null) sciFiRenderer.drawZoneHighlight(canvas, r, 0x00FF64, animTimeMs);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX() - offsetX;
            float y = event.getY() - offsetY;
            handleTap(x, y);
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleTap(float x, float y) {
        if (cardClickListener == null || field == null) return;

        // 卡片/区域绘制在透视矩阵内，触点需做逆透视变换才能与绘制位置对齐
        Matrix pm = PerspectiveHelper.createPerspectiveMatrix(
                fieldCenterX, fieldCenterY, FIELD_PERSPECTIVE_ROT_X, 0, -80);
        Matrix inv = new Matrix();
        if (pm.invert(inv)) {
            float[] pt = {x, y};
            inv.mapPoints(pt);
            x = pt[0];
            y = pt[1];
        }

        if (highlightFieldMask != 0) {
            for (int player = 0; player < 2; player++) {
                int[] locations = {0x04, 0x08};
                for (int loc : locations) {
                    int maxZones = (loc == 0x04) ? GameField.MAX_MONSTER_ZONE : GameField.MAX_SPELL_ZONE;
                    for (int i = 0; i < maxZones; i++) {
                        RectF r = getZoneRect(player, loc, i);
                        if (r != null && r.contains(x, y)) {
                            int bitPos = getZoneBitPos(player, loc, i);
                            if (bitPos >= 0 && (highlightFieldMask & (1 << bitPos)) != 0) {
                                cardClickListener.onZoneClick(player, loc, i);
                                return;
                            }
                        }
                    }
                }
            }
        }

        for (int player = 0; player < 2; player++) {
            for (int loc : new int[]{0x04, 0x08}) {
                int maxZones = (loc == 0x04) ? GameField.MAX_MONSTER_ZONE : GameField.MAX_SPELL_ZONE;
                for (int i = 0; i < maxZones; i++) {
                    RectF r = getZoneRect(player, loc, i);
                    if (r != null && r.contains(x, y)) {
                        GameField.ClientCard card = field.getCard(player, loc, i);
                        if (card != null) {
                            setSelectedCard(player, loc, i);
                            cardClickListener.onCardClick(player, loc, i);
                            return;
                        }
                    }
                }
            }

            boolean flipped = (player == 1);
            List<GameField.ClientCard> hand = field.players[player].hand;
            for (int i = hand.size() - 1; i >= 0; i--) {
                GameField.ClientCard card = hand.get(i);
                if (card == null) continue;
                RectF hr = projectCard(card.curX, card.curY, card.curZ);
                if (hr.contains(x, y)) {
                    setSelectedCard(player, 0x02, i);
                    cardClickListener.onCardClick(player, 0x02, i);
                    return;
                }
            }

            if (checkPileTap(player, x, y, CardLocation.Deck.value())) return;
            if (checkPileTap(player, x, y, CardLocation.Extra.value())) return;
            if (checkPileTap(player, x, y, CardLocation.Grave.value())) return;
            if (checkPileTap(player, x, y, CardLocation.Removed.value())) return;
        }
        clearSelection();
    }

    private boolean checkPileTap(int player, float x, float y, int location) {
        // handleTap 的坐标已是场地局部坐标，必须用不带 offset 的矩形
        RectF r = getPileRectLocal(player, location);
        if (r != null && r.contains(x, y)) {
            int count = field.getCardCount(player, location);
            if (count > 0) {
                int seq = count - 1;
                setSelectedCard(player, location, seq);
                cardClickListener.onCardClick(player, location, seq);
                return true;
            }
        }
        return false;
    }

    private RectF getPileRect(int player, int location) {
        RectF r = getPileRectLocal(player, location);
        if (r == null) return null;
        r.offset(offsetX, offsetY);
        return r;
    }

    private int getZoneBitPos(int player, int location, int sequence) {
        int base = (player == 0) ? 0 : 16;
        if (location == 0x04) return base + sequence;
        if (location == 0x08) {
            if (sequence < 6) return base + 8 + sequence;
            if (sequence == 6) return base + 14;
            if (sequence == 7) return base + 15;
        }
        return -1;
    }

    public void syncDisplayLp() {
        if (field == null) return;
        if (lpAnimator0 != null && lpAnimator0.isRunning()) {
            lpAnimator0.cancel();
        }
        if (lpAnimator1 != null && lpAnimator1.isRunning()) {
            lpAnimator1.cancel();
        }
        displayLp0 = field.players[0].lp;
        displayLp1 = field.players[1].lp;
        invalidate();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (field != null && hasActiveAnimations()) {
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            animationRunning = false;
        }
    }

    public void startAnimationLoop() {
        if (!animationRunning) {
            animationRunning = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

}
