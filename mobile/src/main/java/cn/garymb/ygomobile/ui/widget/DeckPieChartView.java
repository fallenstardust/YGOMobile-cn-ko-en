package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.loader.ImageLoader;
import ocgcore.DataManager;
import ocgcore.data.Card;

public class DeckPieChartView extends View {
    private ImageLoader imageLoader;
    private List<PieSlice> pieSlices;
    private Paint slicePaint;
    private Paint textPaint;
    private Paint linePaint;
    private Paint centerPaint;
    private Paint imagePaint;

    private RectF pieRect;
    private float centerX;
    private float centerY;
    private float radius;

    private OnPieChartClickListener mListener;

    private Map<String, Bitmap> imageCache;

    private static final float MIN_PERCENTAGE = 1.0f;
    private static final int[] COLORS = {
            0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFE91E63,
            0xFFFFEB3B, 0xFF9C27B0, 0xFF00BCD4, 0xFFFF5722,
            0xFF795548, 0xFF607D8B, 0xFF8BC34A, 0xFFCDDC39
    };

    public interface OnPieChartClickListener {
        void onPieChartClick();
    }

    public void setOnPieChartClickListener(OnPieChartClickListener listener) {
        this.mListener = listener;
    }

    public DeckPieChartView(Context context) {
        this(context, null);
    }

    public DeckPieChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeckPieChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        imageLoader = new ImageLoader();
        pieSlices = new ArrayList<>();
        imageCache = new HashMap<>();

        slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        slicePaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(16);
        textPaint.setTextAlign(Paint.Align.LEFT);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(2);
        linePaint.setStyle(Paint.Style.STROKE);

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(Color.parseColor("#00000000"));
        centerPaint.setStyle(Paint.Style.FILL);

        imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        imagePaint.setFilterBitmap(true);
        imagePaint.setDither(true);

        pieRect = new RectF();
    }

    public void setData(List<DeckData> dataList, int totalMatches) {
        pieSlices.clear();

        if (dataList == null || dataList.isEmpty() || totalMatches == 0) {
            invalidate();
            return;
        }

        List<DeckData> sortedList = new ArrayList<>(dataList);
        sortedList.sort((d1, d2) -> Integer.compare(d2.count, d1.count));

        List<DeckData> top10Decks = new ArrayList<>();
        int otherCount = 0;

        for (int i = 0; i < sortedList.size(); i++) {
            DeckData data = sortedList.get(i);
            if (i < 10) {
                top10Decks.add(data);
            } else {
                otherCount += data.count;
            }
        }

        if (otherCount > 0) {
            float otherPercentage = (float) otherCount / totalMatches * 100;
            if (otherPercentage >= MIN_PERCENTAGE) {
                top10Decks.add(new DeckData("others", otherCount));
            }
        }

        float currentAngle = 0;
        int colorIndex = 0;

        for (DeckData data : top10Decks) {
            float percentage = (float) data.count / totalMatches * 100;

            if (percentage >= MIN_PERCENTAGE) {
                float sweepAngle = (float) data.count / totalMatches * 360;

                PieSlice slice = new PieSlice();
                slice.name = data.name;
                slice.count = data.count;
                slice.percentage = percentage;
                slice.startAngle = currentAngle;
                slice.sweepAngle = sweepAngle;
                slice.color = COLORS[colorIndex % COLORS.length];

                loadCardImageForSlice(slice.name);

                pieSlices.add(slice);
                currentAngle += sweepAngle;
                colorIndex++;
            }
        }

        invalidate();
    }

    private void loadCardImageForSlice(String sliceName) {
        if (sliceName == null || "others".equals(sliceName)) {
            return;
        }

        try {
            SparseArray<Card> allCards = DataManager.get().getCardManager().getAllCards();

            Card matchedCard = null;
            for (int i = 0; i < allCards.size(); i++) {
                Card card = allCards.valueAt(i);
                if (card != null && card.Name != null) {
                    if(card.Name.startsWith(sliceName)) {
                        matchedCard = card;
                        break;
                    }
                }
            }

            if (matchedCard != null) {
                ImageView tempImageView = new ImageView(getContext());
                tempImageView.setLayoutParams(new LayoutParams(1, 1));

                imageLoader.bindImage(tempImageView, matchedCard, ImageLoader.Type.small);

                Log.d("DeckPieChartView", "已调用bindImage, 等待加载完成: " + sliceName + " " + matchedCard.Name);

                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> {
                    Drawable drawable = tempImageView.getDrawable();
                    if (drawable != null) {
                        Bitmap bitmap = drawableToBitmap(drawable);
                        if (bitmap != null) {
                            imageCache.put(sliceName, bitmap);
                            invalidate();
                        }
                    } else {
                        handler.postDelayed(() -> {
                            Drawable d = tempImageView.getDrawable();
                            if (d != null) {
                                Bitmap bitmap = drawableToBitmap(d);
                                if (bitmap != null) {
                                    imageCache.put(sliceName, bitmap);
                                    invalidate();
                                }
                            }
                        }, 10);
                    }
                }, 10);
            }
        } catch (Exception e) {
            Log.e("DeckPieChartView", "加载图片错误: ", e);
        }
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            return null;
        }

        try {
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();

            if (width <= 0 || height <= 0) {
                width = 177;
                height = 254;
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);

            return bitmap;
        } catch (Exception e) {
            Log.e("DeckPieChartView", "drawableToBitmap失败: ", e);
            return null;
        }
    }

    public void clearAllImages() {
        for (Bitmap bitmap : imageCache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        imageCache.clear();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(w, h) / 2f - 80;

        float left = centerX - radius;
        float top = centerY - radius;
        float right = centerX + radius;
        float bottom = centerY + radius;

        pieRect.set(left, top, right, bottom);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (pieSlices.isEmpty()) {
            return;
        }

        drawPieSlices(canvas);
        drawLabels(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (mListener != null && !pieSlices.isEmpty()) {
                float x = event.getX();
                float y = event.getY();

                float dx = x - centerX;
                float dy = y - centerY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance <= radius && distance >= radius * 0.4f) {
                    mListener.onPieChartClick();
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    private void drawPieSlices(Canvas canvas) {
        for (PieSlice slice : pieSlices) {
            Bitmap bitmap = imageCache.get(slice.name);

            if (bitmap != null && !bitmap.isRecycled()) {
                drawImageSlice(canvas, slice, bitmap);
            } else {
                slicePaint.setColor(slice.color);
                canvas.drawArc(pieRect, slice.startAngle, slice.sweepAngle, true, slicePaint);
            }
        }

        canvas.drawCircle(centerX, centerY, radius * 0.4f, centerPaint);
    }

    private void drawImageSlice(Canvas canvas, PieSlice slice, Bitmap bitmap) {
        Path slicePath = new Path();
        slicePath.moveTo(centerX, centerY);
        slicePath.arcTo(pieRect, slice.startAngle, slice.sweepAngle);
        slicePath.close();

        canvas.save();
        canvas.clipPath(slicePath);

        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        float scale = Math.max(radius * 2 / bitmapWidth, radius * 2 / bitmapHeight);
        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;

        float midAngle = slice.startAngle + slice.sweepAngle / 2;
        double radian = Math.toRadians(midAngle);

        float imageCenterX = centerX + (float) (Math.cos(radian) * radius * 0.3f);
        float imageCenterY = centerY + (float) (Math.sin(radian) * radius * 0.3f);

        float left = imageCenterX - scaledWidth / 2;
        float top = imageCenterY - scaledHeight / 2;

        canvas.drawBitmap(bitmap, left, top, imagePaint);

        canvas.restore();

        slicePaint.setColor(slice.color);
        slicePaint.setAlpha(60);
        canvas.drawArc(pieRect, slice.startAngle, slice.sweepAngle, true, slicePaint);
        slicePaint.setAlpha(255);
    }

    private void drawLabels(Canvas canvas) {
        for (PieSlice slice : pieSlices) {
            float midAngle = slice.startAngle + slice.sweepAngle / 2;
            double radian = Math.toRadians(midAngle);

            float innerX = centerX + (float) (Math.cos(radian) * radius * 0.5f);
            float innerY = centerY + (float) (Math.sin(radian) * radius * 0.5f);

            float outerX = centerX + (float) (Math.cos(radian) * radius * 1.15f);
            float outerY = centerY + (float) (Math.sin(radian) * radius * 1.15f);

            float textX;
            float textY;

            if (outerX > centerX) {
                textX = outerX + 10;
                textPaint.setTextAlign(Paint.Align.LEFT);
            } else {
                textX = outerX - 10;
                textPaint.setTextAlign(Paint.Align.RIGHT);
            }

            textY = outerY;

            linePaint.setColor(slice.color);
            linePaint.setStrokeWidth(3);
            canvas.drawLine(innerX, innerY, outerX, outerY, linePaint);

            linePaint.setStrokeWidth(1.5f);
            float endX = outerX + (outerX > centerX ? 30 : -30);
            canvas.drawLine(outerX, outerY, endX, outerY, linePaint);

            String labelText = String.format("%s %.1f%%", slice.name, slice.percentage);
            canvas.drawText(labelText, textX, textY, textPaint);
        }
    }

    public void clearData() {
        pieSlices.clear();
        clearAllImages();
        invalidate();
    }

    public static class DeckData {
        public String name;
        public int count;

        public DeckData(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    private static class PieSlice {
        String name;
        int count;
        float percentage;
        float startAngle;
        float sweepAngle;
        int color;
    }
}
