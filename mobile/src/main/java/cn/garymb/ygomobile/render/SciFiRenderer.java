package cn.garymb.ygomobile.render;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

public class SciFiRenderer {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);    private final Paint cardShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int CYAN_GLOW =0xFF00E5FF;
    private static final int DARK_BG = 0xCC001020;
    private static final int PANEL_BG = 0xDD001830;

    public SciFiRenderer() {
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(2f);
        glowPaint.setColor(CYAN_GLOW);
        glowPaint.setMaskFilter(new BlurMaskFilter(6, BlurMaskFilter.Blur.OUTER));

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f);
        borderPaint.setColor(CYAN_GLOW);

        bgPaint.setStyle(Paint.Style.FILL);

        shadowPaint.setColor(0x80000000);
        shadowPaint.setMaskFilter(new BlurMaskFilter(8, BlurMaskFilter.Blur.NORMAL));

        textGlowPaint.setColor(Color.WHITE);
        textGlowPaint.setShadowLayer(4, 0, 0, CYAN_GLOW);

        cardShadowPaint.setColor(0x60000000);
        cardShadowPaint.setMaskFilter(new BlurMaskFilter(6, BlurMaskFilter.Blur.NORMAL));
    }

    public void drawGlowingPanel(Canvas canvas, RectF rect, float cornerRadius) {
        canvas.save();
        canvas.drawRoundRect(rect.left + 3, rect.top + 3, rect.right + 3, rect.bottom + 3,
                cornerRadius, cornerRadius, shadowPaint);

        bgPaint.setColor(PANEL_BG);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, glowPaint);

        borderPaint.setColor(0x8000E5FF);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint);
        canvas.restore();
    }

    public void drawCardShadow(Canvas canvas, float x, float y, float w, float h) {
        canvas.save();
        canvas.drawRect(x + 4, y + 4, x + w + 4, y + h + 4, cardShadowPaint);
        canvas.restore();
    }

    public void drawCardBevel(Canvas canvas, float x, float y, float w, float h, int borderColor) {
        Paint bevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bevelPaint.setStyle(Paint.Style.STROKE);
        bevelPaint.setStrokeWidth(2f);
        bevelPaint.setColor(borderColor);
        canvas.drawRect(x, y, x + w, y + h, bevelPaint);

        Paint innerGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerGlow.setStyle(Paint.Style.STROKE);
        innerGlow.setStrokeWidth(1f);
        innerGlow.setColor(0x40FFFFFF);
        canvas.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, innerGlow);
    }

    public void drawGlowingText(Canvas canvas, String text, float x, float y, Paint textPaint) {
        canvas.save();
        canvas.drawText(text, x, y, textGlowPaint);
        canvas.drawText(text, x, y, textPaint);
        canvas.restore();
    }

    public void drawStarfieldBackground(Canvas canvas, float width, float height, long timeMs) { bgPaint.setColor(0xFF000814);
        canvas.drawRect(0, 0, width, height, bgPaint);

        Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float seed = 12345.6789f;
        for (int i = 0; i < 80; i++) {
            seed = (seed * 16807) % 2147483647;
            float sx = (seed % width);
            seed = (seed * 16807) % 2147483647;
            float sy = (seed % height);
            seed = (seed * 16807) % 2147483647;
            float size = 0.5f + (seed % 20) / 20f * 2f;
            float twinkle = (float) (0.3 + 0.7 * Math.abs(Math.sin(timeMs * 0.001 + i * 0.5)));
            int alpha = (int) (twinkle * 200);
            starPaint.setColor(Color.argb(alpha, 200, 220, 255));
            canvas.drawCircle(sx, sy, size, starPaint);
        }

        Paint nebulaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nebulaPaint.setShader(new RadialGradient(
                width * 0.3f, height * 0.4f, width * 0.5f,
                0x15004080, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, nebulaPaint);

        nebulaPaint.setShader(new RadialGradient(
                width * 0.7f, height * 0.6f, width * 0.4f,
                0x10002060, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, nebulaPaint);
    }

    public void drawFieldGrid(Canvas canvas, float left, float top, float right, float bottom, int cols, int rows, long timeMs) {
        Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.5f);
        float pulse = (float) (0.3 + 0.2 * Math.sin(timeMs * 0.002));
        gridPaint.setColor(Color.argb((int) (pulse * 255), 0, 180, 220));

        float cellW = (right - left) / cols;
        float cellH = (bottom - top) / rows;

        for (int i = 0; i <= cols; i++) {
            float x = left + i * cellW;
            canvas.drawLine(x, top, x, bottom, gridPaint);
        }
        for (int j = 0; j <= rows; j++) {
            float y = top + j * cellH;
            canvas.drawLine(left, y, right, y, gridPaint);
        }
    }

    public void drawZoneHighlight(Canvas canvas, RectF zone, int color, long timeMs) {
        float pulse = (float) (0.4 + 0.3 * Math.sin(timeMs * 0.004));
        Paint hlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hlPaint.setStyle(Paint.Style.FILL);
        hlPaint.setColor(Color.argb((int) (pulse * 255), Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawRect(zone, hlPaint);
    }

    public void drawCountBadge(Canvas canvas, float cx, float cy, String text, int bgColor) {
        Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float radius = Math.max(12, text.length() * 6 + 4);
        badgePaint.setColor(bgColor);
        canvas.drawCircle(cx, cy, radius, badgePaint);

        badgePaint.setStyle(Paint.Style.STROKE);
        badgePaint.setColor(CYAN_GLOW);
        badgePaint.setStrokeWidth(1f);
        canvas.drawCircle(cx, cy, radius, badgePaint);

        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTextSize(radius * 0.8f);
        tp.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, cx, cy + radius * 0.3f, tp);
    }

    public int getCardBorderColor(int cardType, boolean isFaceUp) {
        if (!isFaceUp) return 0xFF4A3728;        if ((cardType & 0x01) != 0) return 0xFFFFD700;
        if ((cardType & 0x02) != 0) return 0xFF00FF88;
        if ((cardType & 0x04) != 0) return 0xFFFF6600;
        if ((cardType & 0x40) != 0) return 0xFFAA00FF;
        if ((cardType & 0x80) != 0) return 0xFF00CCFF;
        if ((cardType & 0x800000) != 0) return 0xFF0088FF;
 return 0xFF00E5FF;
    }
}