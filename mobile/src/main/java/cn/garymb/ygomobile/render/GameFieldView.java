package cn.garymb.ygomobile.render;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.loader.ImageLoader;
import ocgcore.enums.CardLocation;
import ocgcore.enums.CardPosition;

public class GameFieldView extends View {
    private static final String TAG = "GameFieldView";

    private static final float FIELD_ASPECT_RATIO = 16f / 9f;
    private static final int ZONE_ROWS = 7;
    private static final int ZONE_COLS = 8;

    private GameField field;
    private ImageLoader imageLoader;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint();

    private float fieldWidth, fieldHeight;
    private float zoneWidth, zoneHeight;
    private float cardWidth, cardHeight;
    private float offsetX, offsetY;

    private int selectedPlayer = -1;
    private int selectedLocation = -1;
    private int selectedSequence = -1;
    private int highlightFieldMask = 0;

    private OnCardClickListener cardClickListener;

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
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setShadowLayer(2, 1, 1, Color.BLACK);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        selectedPaint.setColor(Color.YELLOW);
        selectedPaint.setStyle(Paint.Style.STROKE);
        selectedPaint.setStrokeWidth(3);

        dimPaint.setColor(Color.argb(128, 0, 0, 0));
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
        cardWidth = zoneWidth * 0.85f;
        cardHeight = cardWidth * 1.457f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (field == null) return;

        canvas.save();
        canvas.translate(offsetX, offsetY);

        drawFieldBackground(canvas);
        drawZones(canvas, 1, true);
        drawZones(canvas, 0, false);
        drawHandCards(canvas, 1, true);
        drawHandCards(canvas, 0, false);
        drawExtraInfo(canvas);
        drawSelection(canvas);

        canvas.restore();
    }

    private void drawFieldBackground(Canvas canvas) {
        Bitmap fieldBg = TextureLoader.get().getFieldTexture(false);
        if (fieldBg != null) {
            Rect src = new Rect(0, 0, fieldBg.getWidth(), fieldBg.getHeight());
            RectF dst = new RectF(0, 0, fieldWidth, fieldHeight);
            canvas.drawBitmap(fieldBg, src, dst, paint);
        } else {
            paint.setColor(Color.argb(200, 0, 60, 0));
            canvas.drawRect(0, 0, fieldWidth, fieldHeight, paint);
        }
    }

    private void drawZones(Canvas canvas, int player, boolean flipped) {
        float baseY = flipped ? 0 : fieldHeight * 0.5f;
        float rowH = fieldHeight * 0.5f / 3;

        drawZoneRow(canvas, player, CardLocation.MonsterZone.value(),
                field.getCardCount(player, CardLocation.MonsterZone.value()),
                0, baseY + rowH, flipped);
        drawZoneRow(canvas, player, CardLocation.SpellZone.value(),
                field.getCardCount(player, CardLocation.SpellZone.value()),
                0, baseY, flipped);

        drawPileZone(canvas, player, CardLocation.Deck.value(),
                flipped ? fieldWidth - zoneWidth * 0.8f : zoneWidth * 0.1f,
                baseY + rowH, flipped);
        drawPileZone(canvas, player, CardLocation.Extra.value(),
                flipped ? fieldWidth - zoneWidth * 1.8f : zoneWidth * 1.1f,
                baseY + rowH, flipped);
        drawPileZone(canvas, player, CardLocation.Grave.value(),
                flipped ? zoneWidth * 0.1f : fieldWidth - zoneWidth * 0.8f,
                baseY, flipped);
        drawPileZone(canvas, player, CardLocation.Removed.value(),
                flipped ? zoneWidth * 1.1f : fieldWidth - zoneWidth * 1.8f,
                baseY, flipped);
    }

    private void drawZoneRow(Canvas canvas, int player, int location, int count,
                              float startX, float y, boolean flipped) {
        int maxZones = (location == CardLocation.MonsterZone.value())
                ? GameField.MAX_MONSTER_ZONE : GameField.MAX_SPELL_ZONE;
        float totalWidth = maxZones * zoneWidth;
        float sx = (fieldWidth - totalWidth) / 2f;

        for (int i = 0; i < maxZones; i++) {
            float x = sx + i * zoneWidth;
            float cx = x + (zoneWidth - cardWidth) / 2f;
            float cy = y;

            paint.setColor(Color.argb(40, 255, 255, 255));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(cx, cy, cx + cardWidth, cy + cardHeight, paint);

            GameField.ClientCard card = field.getCard(player, location, i);
            if (card != null) {
                drawCard(canvas, card, cx, cy, cardWidth, cardHeight, flipped);
            }
        }
    }

    private void drawPileZone(Canvas canvas, int player, int location,
                               float x, float y, boolean flipped) {
        int count = field.getCardCount(player, location);
        paint.setColor(Color.argb(40, 255, 255, 255));
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(x, y, x + cardWidth, y + cardHeight, paint);

        if (count > 0) {
            Bitmap coverBmp = TextureLoader.get().getCardCover();
            if (coverBmp != null) {
                Rect src = new Rect(0, 0, coverBmp.getWidth(), coverBmp.getHeight());
                RectF dst = new RectF(x, y, x + cardWidth, y + cardHeight);
                canvas.drawBitmap(coverBmp, src, dst, paint);
            }
            textPaint.setTextSize(cardWidth * 0.3f);
            canvas.drawText(String.valueOf(count),
                    x + cardWidth / 2f - textPaint.measureText(String.valueOf(count)) / 2f,
                    y + cardHeight / 2f, textPaint);
        }
    }

    private void drawHandCards(Canvas canvas, int player, boolean flipped) {
        List<GameField.ClientCard> hand = field.players[player].hand;
        int handCount = 0;
        for (GameField.ClientCard card : hand) {
            if (card != null) handCount++;
        }
        if (handCount == 0) return;

        float handY = flipped ? -cardHeight * 0.3f : fieldHeight - cardHeight * 0.7f;
        float totalHandWidth = handCount * cardWidth * 0.7f;
        float startX = (fieldWidth - totalHandWidth) / 2f;

        int idx = 0;
        for (int i = 0; i < hand.size(); i++) {
            GameField.ClientCard card = hand.get(i);
            if (card == null) continue;
            float x = startX + idx * cardWidth * 0.7f;
            boolean faceUp = card.isPublic || (player == clientSelfType());
            drawCard(canvas, card, x, handY, cardWidth, cardHeight, flipped);
            idx++;
        }
    }

    private int clientSelfType() {
        return 0;
    }

    private void drawCard(Canvas canvas, GameField.ClientCard card,
                           float x, float y, float w, float h, boolean flipped) {
        canvas.save();

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
                    canvas.restore();
                    return;
                }
            }
        }

        if (card.isFaceUp()) {
            paint.setColor(Color.argb(200, 200, 180, 100));
            canvas.drawRect(dst, paint);
            textPaint.setTextSize(w * 0.25f);
            String codeStr = String.valueOf(card.code);
            canvas.drawText(codeStr,
                    x + w / 2f - textPaint.measureText(codeStr) / 2f,
                    y + h / 2f, textPaint);
        } else {
            Bitmap coverBmp = TextureLoader.get().getCardCover();
            if (coverBmp != null) {
                canvas.drawBitmap(coverBmp, null, dst, paint);
            } else {
                paint.setColor(Color.argb(200, 100, 80, 50));
                canvas.drawRect(dst, paint);
            }
        }

        if (card.isMonster() && card.isFaceUp()) {
            textPaint.setTextSize(w * 0.2f);
            String atkDef = card.attack + "/" + card.defense;
            canvas.drawText(atkDef,
                    x + w / 2f - textPaint.measureText(atkDef) / 2f,
                    y + h - 4, textPaint);
        }

        canvas.restore();
    }

    private void drawExtraInfo(Canvas canvas) {
        textPaint.setTextSize(zoneWidth * 0.4f);

        String lp0 = "LP: " + field.players[0].lp;
        canvas.drawText(lp0, 10, fieldHeight - 10, textPaint);

        String lp1 = "LP: " + field.players[1].lp;
        canvas.drawText(lp1, 10, textPaint.getTextSize() + 5, textPaint);

        String turnInfo = "Turn " + field.turnCount;
        canvas.drawText(turnInfo,
                fieldWidth - textPaint.measureText(turnInfo) - 10,
                fieldHeight / 2f + textPaint.getTextSize() / 2f, textPaint);
    }

    private void drawSelection(Canvas canvas) {
        if (selectedPlayer < 0 || selectedLocation < 0 || selectedSequence < 0) return;

        RectF cardRect = getCardRect(selectedPlayer, selectedLocation, selectedSequence);
        if (cardRect != null) {
            canvas.drawRect(cardRect, selectedPaint);
        }
    }

    private RectF getCardRect(int player, int location, int sequence) {
        // Simplified - returns approximate card position
        boolean flipped = (player == 1);
        float baseY = flipped ? 0 : fieldHeight * 0.5f;
        float rowH = fieldHeight * 0.5f / 3f;

        if (location == CardLocation.MonsterZone.value() || location == CardLocation.SpellZone.value()) {
            int maxZones = (location == CardLocation.MonsterZone.value())
                    ? GameField.MAX_MONSTER_ZONE : GameField.MAX_SPELL_ZONE;
            float totalWidth = maxZones * zoneWidth;
            float sx = (fieldWidth - totalWidth) / 2f;
            float rowY = (location == CardLocation.MonsterZone.value())
                    ? baseY + rowH : baseY;
            float x = sx + sequence * zoneWidth + (zoneWidth - cardWidth) / 2f;
            return new RectF(x + offsetX, rowY + offsetY, x + cardWidth + offsetX, rowY + cardHeight + offsetY);
        }
        return null;
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

        for (int player = 0; player < 2; player++) {
            boolean flipped = (player == 1);
            float baseY = flipped ? 0 : fieldHeight * 0.5f;
            float rowH = fieldHeight * 0.5f / 3f;

            int[] locations = {CardLocation.MonsterZone.value(), CardLocation.SpellZone.value()};
            int maxZones = GameField.MAX_MONSTER_ZONE;
            float totalWidth = maxZones * zoneWidth;
            float sx = (fieldWidth - totalWidth) / 2f;

            for (int loc : locations) {
                float rowY = (loc == CardLocation.MonsterZone.value()) ? baseY + rowH : baseY;
                int zones = (loc == CardLocation.MonsterZone.value())
                        ? GameField.MAX_MONSTER_ZONE : GameField.MAX_SPELL_ZONE;
                for (int i = 0; i < zones; i++) {
                    float cx = sx + i * zoneWidth;
                    if (x >= cx && x <= cx + zoneWidth && y >= rowY && y <= rowY + cardHeight) {
                        setSelectedCard(player, loc, i);
                        cardClickListener.onCardClick(player, loc, i);
                        return;
                    }
                }
            }

            float handY = flipped ? -cardHeight * 0.3f : fieldHeight - cardHeight * 0.7f;
            List<GameField.ClientCard> hand = field.players[player].hand;
            int handCount = 0;
            for (GameField.ClientCard card : hand) {
                if (card != null) handCount++;
            }
            if (handCount > 0) {
                float totalHandWidth = handCount * cardWidth * 0.7f;
                float handStartX = (fieldWidth - totalHandWidth) / 2f;
                int idx = 0;
                for (int i = 0; i < hand.size(); i++) {
                    if (hand.get(i) == null) continue;
                    float hx = handStartX + idx * cardWidth * 0.7f;
                    if (x >= hx && x <= hx + cardWidth && y >= handY && y <= handY + cardHeight) {
                        setSelectedCard(player, CardLocation.Hand.value(), i);
                        cardClickListener.onCardClick(player, CardLocation.Hand.value(), i);
                        return;
                    }
                    idx++;
                }
            }

            int[] pileLocations = {
                    CardLocation.Deck.value(), CardLocation.Extra.value(),
                    CardLocation.Grave.value(), CardLocation.Removed.value()
            };
            float[] pileXs = {
                    flipped ? fieldWidth - zoneWidth * 0.8f : zoneWidth * 0.1f,
                    flipped ? fieldWidth - zoneWidth * 1.8f : zoneWidth * 1.1f,
                    flipped ? zoneWidth * 0.1f : fieldWidth - zoneWidth * 0.8f,
                    flipped ? zoneWidth * 1.1f : fieldWidth - zoneWidth * 1.8f
            };
            float[] pileYs = {baseY + rowH, baseY + rowH, baseY, baseY};
            for (int p = 0; p < pileLocations.length; p++) {
                if (x >= pileXs[p] && x <= pileXs[p] + cardWidth
                        && y >= pileYs[p] && y <= pileYs[p] + cardHeight) {
                    cardClickListener.onZoneClick(player, pileLocations[p], 0);
                    return;
                }
            }
        }
        clearSelection();
    }
}
