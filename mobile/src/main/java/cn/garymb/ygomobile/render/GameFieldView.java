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

        drawFieldZone(canvas, 1, true);
        drawFieldZone(canvas, 0, false);

        drawPileZone3D(canvas, 0, false);
        drawPileZone3D(canvas, 1, true);

        canvas.restore();

        drawHandCards3D(canvas, 1, true);
        drawHandCards3D(canvas, 0, false);

        drawChainLines(canvas);
        drawSelection(canvas);
        drawCmdHighlights(canvas);
    }

    private void drawFieldBoard(Canvas canvas) {
        Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardPaint.setColor(0x40002040);
        boardPaint.setStyle(Paint.Style.FILL);

        RectF boardRect = new RectF(fieldLeft, fieldTop, fieldRight, fieldBottom);
        canvas.drawRoundRect(boardRect, 8, 8, boardPaint);

        Bitmap fieldBg = TextureLoader.get().getFieldTexture(false);
        if (fieldBg != null) {
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setAlpha(120);
            canvas.drawBitmap(fieldBg, null, boardRect, bgPaint);
        }

        sciFiRenderer.drawFieldGrid(canvas, fieldLeft, fieldTop, fieldRight, fieldBottom,
                7, 6, animTimeMs);

        Paint borderGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderGlow.setStyle(Paint.Style.STROKE);
        borderGlow.setStrokeWidth(2f);
        borderGlow.setColor(0xFF00E5FF);
        borderGlow.setShadowLayer(8, 0, 0, 0xFF00E5FF);
        canvas.drawRoundRect(boardRect, 8, 8, borderGlow);

        Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setStyle(Paint.Style.STROKE);
        dividerPaint.setStrokeWidth(1f);
        dividerPaint.setColor(0x6000E5FF);
        canvas.drawLine(fieldLeft, fieldCenterY, fieldRight, fieldCenterY, dividerPaint);
    }

    private void drawFieldZone(Canvas canvas, int player, boolean flipped) {
        float halfFieldH = (fieldBottom - fieldTop) / 2f;
        float baseY = flipped ? fieldTop : fieldCenterY;

        float monsterRowY = flipped ? baseY + halfFieldH * 0.35f : baseY + halfFieldH * 0.35f;
        float spellRowY = flipped ? baseY + halfFieldH * 0.05f : baseY + halfFieldH * 0.65f;

        drawMonsterZoneRow(canvas, player, monsterRowY, flipped);
        drawSpellZoneRow(canvas, player, spellRowY, flipped);
    }

    private void drawMonsterZoneRow(Canvas canvas, int player, float y, boolean flipped) {
        int maxZones = 5;
        float totalWidth = maxZones * zoneWidth * 1.05f;
        float sx = (fieldWidth - totalWidth) / 2f;

        for (int i = 0; i < maxZones; i++) {
            float x = sx + i * zoneWidth * 1.05f;
            float cx = x + (zoneWidth - cardWidth) / 2f;

            drawZoneSlot(canvas, cx, y, cardWidth, cardHeight);

            GameField.ClientCard card = field.getCard(player, CardLocation.MonsterZone.value(), i);
            if (card != null) {
                drawCard3D(canvas, card, cx, y, cardWidth, cardHeight, flipped, CARD_ELEVATION);
            }
        }

        float extraZoneW = zoneWidth * 0.9f;
        float extraZoneH = cardHeight * 0.9f;
        float extraY = flipped ? y - extraZoneH * 1.2f : y + cardHeight * 1.1f;

        for (int i = 5; i <= 6; i++) {
            float ex = (i == 5) ? sx - extraZoneW * 0.5f : sx + totalWidth - extraZoneW * 0.5f;
            drawZoneSlot(canvas, ex, extraY, extraZoneW, extraZoneH);

            GameField.ClientCard card = field.getCard(player, CardLocation.MonsterZone.value(), i);
            if (card != null) {
                drawCard3D(canvas, card, ex, extraY, extraZoneW, extraZoneH, flipped, CARD_ELEVATION);
            }
        }
    }

    private void drawSpellZoneRow(Canvas canvas, int player, float y, boolean flipped) {
        int maxZones = 5;
        float totalWidth = maxZones * zoneWidth * 1.05f;
        float sx = (fieldWidth - totalWidth) / 2f;

        for (int i = 0; i < maxZones; i++) {
            float x = sx + i * zoneWidth * 1.05f;
            float cx = x + (zoneWidth - cardWidth) / 2f;

            drawZoneSlot(canvas, cx, y, cardWidth, cardHeight);

            GameField.ClientCard card = field.getCard(player, CardLocation.SpellZone.value(), i);
            if (card != null) {
                drawCard3D(canvas, card, cx, y, cardWidth, cardHeight, flipped, CARD_ELEVATION);
            }
        }

        float fieldSpellX = sx - zoneWidth * 1.2f;
        float pendulumX = sx + totalWidth + zoneWidth * 0.2f;
        drawZoneSlot(canvas, fieldSpellX, y, cardWidth * 0.9f, cardHeight * 0.9f);
        drawZoneSlot(canvas, pendulumX, y, cardWidth * 0.9f, cardHeight * 0.9f);

        GameField.ClientCard fsCard = field.getCard(player, CardLocation.SpellZone.value(), 5);
        if (fsCard != null) {
            drawCard3D(canvas, fsCard, fieldSpellX, y, cardWidth * 0.9f, cardHeight * 0.9f, flipped, CARD_ELEVATION);
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
        float halfFieldH = (fieldBottom - fieldTop) / 2f;

        float deckX, deckY, graveX, graveY, removeX, removeY, extraX, extraY;

        if (!flipped) {
            deckX = fieldRight - zoneWidth * 1.2f;
            deckY = fieldCenterY + halfFieldH * 0.35f;
            extraX = fieldLeft + zoneWidth * 0.1f;
            extraY = deckY;
            graveX = fieldRight - zoneWidth * 1.2f;
            graveY = fieldCenterY + halfFieldH * 0.05f;
            removeX = fieldRight - zoneWidth * 2.5f;
            removeY = graveY;
        } else {
            deckX = fieldLeft + zoneWidth * 0.2f;
            deckY = fieldTop + halfFieldH * 0.35f;
            extraX = fieldRight - zoneWidth * 1.2f;
            extraY = deckY;
            graveX = fieldLeft + zoneWidth * 0.2f;
            graveY = fieldTop + halfFieldH * 0.65f;
            removeX = fieldLeft + zoneWidth * 1.5f;
            removeY = graveY;
        }

        drawCardPile(canvas, player, CardLocation.Deck.value(), deckX, deckY, flipped, 0xFF00AA44);
        drawCardPile(canvas, player, CardLocation.Extra.value(), extraX, extraY, flipped, 0xFF8800AA);
        drawCardPile(canvas, player, CardLocation.Grave.value(), graveX, graveY, flipped, 0xFFAA4400);
        drawCardPile(canvas, player, CardLocation.Removed.value(), removeX, removeY, flipped, 0xFFAA0044);
    }

    private void drawCardPile(Canvas canvas, int player, int location,
                              float x, float y, boolean flipped, int indicatorColor) {
        int count = field.getCardCount(player, location);
        float pileW = cardWidth * 0.95f;
        float pileH = cardHeight * 0.95f;

        drawZoneSlot(canvas, x, y, pileW, pileH);

        if (count > 0) {
            int maxVisible = Math.min(count, 5);
            for (int i = 0; i < maxVisible; i++) {
                float offsetX = i * 0.8f;
                float offsetY = -i * 0.8f;
                Bitmap coverBmp = TextureLoader.get().getCardCover();
                if (coverBmp != null) {
                    Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    coverPaint.setAlpha(200 - i * 20);
                    sciFiRenderer.drawCardShadow(canvas, x + offsetX, y + offsetY, pileW, pileH);
                    canvas.drawBitmap(coverBmp, null,
                            new RectF(x + offsetX, y + offsetY, x + pileW + offsetX, y + pileH + offsetY),
                            coverPaint);
                }
            }

            sciFiRenderer.drawCountBadge(canvas,
                    x + pileW / 2f, y + pileH / 2f,
                    String.valueOf(count), indicatorColor);
        }

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xAA00E5FF);
        labelPaint.setTextSize(cardWidth * 0.2f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        String label = getPileLabel(location);
        canvas.drawText(label, x + pileW / 2f, y + pileH + cardWidth * 0.25f, labelPaint);
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
        boolean flipped = (player == 1);
        float halfFieldH = (fieldBottom - fieldTop) / 2f;
        float baseY = flipped ? fieldTop : fieldCenterY;

        if (location == 0x04) {
            int maxZones = 5;
            float totalWidth = maxZones * zoneWidth * 1.05f;
            float sx = (fieldWidth - totalWidth) / 2f;
            float rowY = baseY + halfFieldH * 0.35f;
            float x = sx + sequence * zoneWidth * 1.05f + (zoneWidth - cardWidth) / 2f;
            return new RectF(x, rowY, x + cardWidth, rowY + cardHeight);
        }
        if (location == 0x08) {
            int maxZones = 5;
            float totalWidth = maxZones * zoneWidth * 1.05f;
            float sx = (fieldWidth - totalWidth) / 2f;
            float rowY = flipped ? baseY + halfFieldH * 0.05f : baseY + halfFieldH * 0.65f;
            if (sequence < 5) {
                float x = sx + sequence * zoneWidth * 1.05f + (zoneWidth - cardWidth) / 2f;
                return new RectF(x, rowY, x + cardWidth, rowY + cardHeight);
            }
        }
        return null;
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
                }            }

            boolean flipped = (player == 1);
            List<GameField.ClientCard> hand = field.players[player].hand;
            int handCount = 0;
            for (GameField.ClientCard card : hand) {
                if (card != null) handCount++;
            }
            if (handCount > 0) {
                float handCardW = cardWidth * 1.1f;
                float spacing = handCardW * HAND_CARD_OVERLAP_RATIO;
                float totalHandWidth = handCount * spacing;
                if (totalHandWidth > fieldWidth * 0.8f) {
                    spacing = (fieldWidth * 0.8f) / handCount;
                    totalHandWidth = fieldWidth * 0.8f;
                }
                float handStartX = (fieldWidth - totalHandWidth) / 2f;
                float handY = flipped ? fieldTop - cardHeight * 0.5f : fieldBottom - cardHeight * 0.3f;

                int idx = 0;
                for (int i = 0; i < hand.size(); i++) {
                    if (hand.get(i) == null) continue;
                    float hx = handStartX + idx * spacing;
                    if (x >= hx && x <= hx + handCardW && y >= handY && y <= handY + cardHeight * 1.1f) {
                        setSelectedCard(player, 0x02, i);
                        cardClickListener.onCardClick(player, 0x02, i);
                        return;
                    }
                    idx++;
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
        RectF r = getPileRect(player, location);
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
        boolean flipped = (player == 1);
        float halfFieldH = (fieldBottom - fieldTop) / 2f;
        float pileW = cardWidth * 0.95f;
        float pileH = cardHeight * 0.95f;

        float x, y;
        if (!flipped) {
            switch (location) {
                case 0x01:
                    x = fieldRight - zoneWidth * 1.2f;
                    y = fieldCenterY + halfFieldH * 0.35f;
                    break;
                case 0x40:
                    x = fieldLeft + zoneWidth * 0.1f;
                    y = fieldCenterY + halfFieldH * 0.35f;
                    break;
                case 0x10:
                    x = fieldRight - zoneWidth * 1.2f;
                    y = fieldCenterY + halfFieldH * 0.05f;
                    break;
                case 0x20:
                    x = fieldRight - zoneWidth * 2.5f;
                    y = fieldCenterY + halfFieldH * 0.05f;
                    break;
                default: return null;
 }
        } else {
            switch (location) {
                case 0x01:
                    x = fieldLeft + zoneWidth * 0.2f;
                    y = fieldTop + halfFieldH * 0.35f;
                    break;
                case 0x40:
                    x = fieldRight - zoneWidth * 1.2f;
                    y = fieldTop + halfFieldH * 0.35f;
                    break;
                case 0x10:
                    x = fieldLeft + zoneWidth * 0.2f;
                    y = fieldTop + halfFieldH * 0.65f;
                    break;
                case 0x20:
                    x = fieldLeft + zoneWidth * 1.5f;
                    y = fieldTop + halfFieldH * 0.65f;
                    break;
                default: return null;
            }
        }
        return new RectF(x + offsetX, y + offsetY, x + pileW + offsetX, y + pileH + offsetY);
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
            invalidate(); Choreographer.getInstance().postFrameCallback(this);
        } else {
 animationRunning = false;
        }
    }

    public void startAnimationLoop() { if (!animationRunning) {
            animationRunning = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

}
