package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeckPieChartView extends View {

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

                pieSlices.add(slice);
                currentAngle += sweepAngle;
                colorIndex++;
            }
        }

        invalidate();
    }

    public void setImageForSlice(String sliceName, Bitmap bitmap) {
        if (sliceName != null && bitmap != null) {
            imageCache.put(sliceName, bitmap);
            invalidate();
        }
    }

    public void setImageForSliceFromResource(String sliceName, int resourceId) {
        if (sliceName == null) return;
        
        Glide.with(getContext())
                .asBitmap()
                .load(resourceId)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        imageCache.put(sliceName, resource);
                        invalidate();
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
    }

    public void setImageForSliceFromFile(String sliceName, String filePath) {
        if (sliceName == null || filePath == null) return;
        
        Glide.with(getContext())
                .asBitmap()
                .load(filePath)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        imageCache.put(sliceName, resource);
                        invalidate();
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
    }

    public void setImageForSliceFromUrl(String sliceName, String url) {
        if (sliceName == null || url == null) return;
        
        Glide.with(getContext())
                .asBitmap()
                .load(url)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        imageCache.put(sliceName, resource);
                        invalidate();
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
    }

    public void removeImageForSlice(String sliceName) {
        if (sliceName != null) {
            Bitmap bitmap = imageCache.remove(sliceName);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            invalidate();
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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (pieSlices.isEmpty()) {
            return;
        }

        drawPieSlices(canvas);
        drawLabels(canvas);
        drawImages(canvas);
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
            slicePaint.setColor(slice.color);
            canvas.drawArc(pieRect, slice.startAngle, slice.sweepAngle, true, slicePaint);
        }

        canvas.drawCircle(centerX, centerY, radius * 0.4f, centerPaint);
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

    private void drawImages(Canvas canvas) {
        for (PieSlice slice : pieSlices) {
            Bitmap bitmap = imageCache.get(slice.name);
            if (bitmap == null || bitmap.isRecycled()) {
                continue;
            }

            float midAngle = slice.startAngle + slice.sweepAngle / 2;
            double radian = Math.toRadians(midAngle);

            float imageRadius = radius * 0.65f;
            float imageX = centerX + (float) (Math.cos(radian) * imageRadius);
            float imageY = centerY + (float) (Math.sin(radian) * imageRadius);

            Path clipPath = new Path();
            float clipRadius = radius * 0.12f;
            clipPath.addCircle(imageX, imageY, clipRadius, Path.Direction.CW);
            
            canvas.save();
            canvas.clipPath(clipPath);

            float bitmapWidth = bitmap.getWidth();
            float bitmapHeight = bitmap.getHeight();
            float scale = Math.min(clipRadius * 2 / bitmapWidth, clipRadius * 2 / bitmapHeight);
            float scaledWidth = bitmapWidth * scale;
            float scaledHeight = bitmapHeight * scale;

            float left = imageX - scaledWidth / 2;
            float top = imageY - scaledHeight / 2;

            canvas.drawBitmap(bitmap, left, top, imagePaint);
            canvas.restore();
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
