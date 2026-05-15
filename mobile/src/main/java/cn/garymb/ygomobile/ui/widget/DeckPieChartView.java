package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.BitmapUtil;
import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

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
        if (sliceName == null) {
            return;
        }

        int fixedCardId = 0;
        if ("迷之卡组".equals(sliceName)) {
            fixedCardId = 27288416;
        }

        Card matchedCard = null;
        Bitmap bitmap = null;

        if ("others".equals(sliceName)) {
            // 对于 "others"，使用指定的封面图片
            try {
                String coverPath = Constants.ASSET_COVER + "running_dealer_cover.jpg";
                bitmap = BitmapUtil.getBitmapFormAssets(getContext(), coverPath, 0, 0);
                
                if (bitmap != null) {
                    imageCache.put(sliceName, cropBitmap(bitmap));
                    postInvalidate();
                } else {
                    Log.w("DeckPieChartView", "未找到 others 封面图片: " + coverPath);
                }
            } catch (Exception e) {
                Log.e("DeckPieChartView", "加载 others 封面图片失败", e);
            }
            return;
        }

        if (fixedCardId != 0) {
            matchedCard = DataManager.get().getCardManager().getCard(fixedCardId);
        } else {
            SparseArray<Card> allCards = DataManager.get().getCardManager().getAllCards();
            int matchPriority = 0;

            for (int i = 0; i < allCards.size(); i++) {
                Card card = allCards.valueAt(i);
                if (card != null && card.Name != null) {
                    boolean isMonster = card.isType(CardType.Monster);
                    boolean startsWith = card.Name.startsWith(sliceName);
                    boolean contains = card.Name.contains(sliceName);

                    int currentPriority = 0;

                    if (startsWith && isMonster) {
                        currentPriority = 4;
                    } else if (startsWith) {
                        currentPriority = 3;
                    } else if (contains && isMonster) {
                        currentPriority = 2;
                    } else if (contains) {
                        currentPriority = 1;
                    }

                    if (currentPriority > matchPriority) {
                        matchedCard = card;
                        matchPriority = currentPriority;
                        if (matchPriority == 4) {
                            break;
                        }
                    }
                }
            }
        }

        if (matchedCard != null) {
            long cardCode = matchedCard.Code;
            bitmap = loadBitmapFromZips(cardCode);

            if (bitmap != null) {
                imageCache.put(sliceName, bitmap);
                postInvalidate();
            } else {
                Log.w("DeckPieChartView", "未找到卡片图片: code=" + cardCode);
            }
        } else {
            Log.w("DeckPieChartView", "未找到匹配的卡片: " + sliceName);
        }
    }

    private Bitmap loadBitmapFromZips(long cardCode) {
        File[] files = new File(AppsSettings.get().getResourcePath()).listFiles();

        for (String ext : Constants.IMAGE_EX) {
            String targetFileName = cardCode + ext;

            for (File file : files) {
                if (!file.isFile() || !file.getName().toLowerCase().endsWith(".zip")) {
                    continue;
                }

                ZipFile zipFile = null;
                try {
                    zipFile = new ZipFile(file);
                    ZipEntry entry = zipFile.getEntry("pics/" + targetFileName);

                    if (entry == null) {
                        entry = zipFile.getEntry(targetFileName);
                    }

                    if (entry != null) {
                        InputStream inputStream = zipFile.getInputStream(entry);
                        Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
                        inputStream.close();

                        if (originalBitmap != null) {
                            Bitmap croppedBitmap = cropBitmap(originalBitmap);

                            if (!originalBitmap.isRecycled()) {
                                originalBitmap.recycle();
                            }

                            if (croppedBitmap != null) {
                                return croppedBitmap;
                            } else {
                                return originalBitmap;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w("DeckPieChartView", "读取zip失败: " + file.getName(), e);
                } finally {
                    if (zipFile != null) {
                        try {
                            zipFile.close();
                        } catch (Exception e) {}
                    }
                }
            }
        }

        return null;
    }

    private Bitmap cropBitmap(Bitmap original) {
        if (original == null) {
            return null;
        }

        int left = 22;
        int top = 47;
        int right = 155;
        int bottom = 156;

        return Bitmap.createBitmap(original, left, top, right - left, bottom - top);
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
        
        // 根据视图大小动态计算边距和半径
        float minDimension = Math.min(w, h);
        float margin;
        
        if (minDimension < 300) {
            // 小尺寸视图，使用较小边距
            margin = minDimension * 0.15f;
        } else if (minDimension < 600) {
            // 中等尺寸视图，使用适中边距
            margin = minDimension * 0.2f;
        } else {
            // 大尺寸视图，使用较大边距但限制最大值
            margin = Math.min(minDimension * 0.25f, 150);
        }
        
        radius = minDimension / 2f - margin;

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

                // 动态计算可点击区域，基于当前饼图半径
                float innerRadius = radius * 0.4f;
                if (distance <= radius && distance >= innerRadius) {
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

        float midAngle = slice.startAngle + slice.sweepAngle / 2;
        double radian = Math.toRadians(midAngle);

        float imageCenterX = centerX + (float) (Math.cos(radian) * radius * 0.3f);
        float imageCenterY = centerY + (float) (Math.sin(radian) * radius * 0.3f);

        // 动态计算目标大小，基于饼图半径
        float targetSize = radius * 1.8f; // 调整为半径的1.8倍，适应不同大小的饼图

        float scale = 1.0f;
        if (bitmapWidth < targetSize || bitmapHeight < targetSize) {
            float scaleX = targetSize / bitmapWidth;
            float scaleY = targetSize / bitmapHeight;
            scale = Math.max(scaleX, scaleY);
        }

        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;

        float left = imageCenterX - scaledWidth / 3.0f;
        float top = imageCenterY - scaledHeight / 3.5f;

        RectF destRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
        canvas.drawBitmap(bitmap, null, destRect, imagePaint);

        canvas.restore();

        Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        edgePaint.setColor(Color.WHITE);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(1);

        Path edgePath = new Path();
        edgePath.moveTo(centerX, centerY);
        edgePath.arcTo(pieRect, slice.startAngle, slice.sweepAngle);
        edgePath.close();

        canvas.drawPath(edgePath, edgePaint);
    }

    private void drawLabels(Canvas canvas) {
        for (PieSlice slice : pieSlices) {
            float midAngle = slice.startAngle + slice.sweepAngle / 2;
            double radian = Math.toRadians(midAngle);

            float innerX = centerX + (float) (Math.cos(radian) * radius);
            float innerY = centerY + (float) (Math.sin(radian) * radius);

            // 动态计算标签线的外部位置，基于饼图半径
            float labelLineExtension = radius * 0.15f; // 标签线延伸长度为半径的15%
            float outerX = centerX + (float) (Math.cos(radian) * (radius + labelLineExtension));
            float outerY = centerY + (float) (Math.sin(radian) * (radius + labelLineExtension));

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
