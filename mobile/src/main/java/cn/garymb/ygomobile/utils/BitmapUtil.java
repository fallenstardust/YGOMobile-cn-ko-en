package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SuppressWarnings("deprecation")
public class BitmapUtil {
    private final static long MAX_BITMAP;

    static {
        DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
        MAX_BITMAP = (dm.widthPixels * dm.heightPixels) / 5 * 4;
    }

    public static void destroy(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled())
            bitmap.recycle();
        bitmap = null;
    }

    public static void destroy(Drawable drawable) {
        if (drawable != null) {
            drawable.setCallback(null);
            if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                destroy(bitmapDrawable.getBitmap());
            }
            drawable = null;
        }
    }

    public static boolean saveBitmap(Bitmap bm, String file, int quality) {
        if (bm == null || file == null)
            return false;
        file = file.toLowerCase(Locale.US);
        File f = new File(file);
        File dir = f.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        } else {
            f.delete();
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            if (!file.endsWith("png"))
                bm.compress(Bitmap.CompressFormat.JPEG, quality, out);
            else
                bm.compress(Bitmap.CompressFormat.PNG, quality, out);
            out.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(out);
        }
        return true;
    }

    public static BitmapFactory.Options getImageInfo(InputStream inputStream) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // 为true里只读图片的信息，如果长宽，返回的bitmap为null
        options.inDither = false;
        BitmapFactory.decodeStream(inputStream, null, options);
        return options;
    }

    public static Bitmap getBitmapFormAssets(Context context, String name, int w, int h) {
        InputStream inputStream = null;
        Bitmap bitmap = null;
        AssetManager assetManager = context.getAssets();
        try {
            inputStream = assetManager.open(name);
            BitmapFactory.Options options = getImageInfo(inputStream);
            inputStream.close();
            inputStream = assetManager.open(name);
            bitmap = BitmapUtil.getBitmapByStream(inputStream, options.outWidth, options.outHeight, w, h);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (Exception e) {

        } finally {
            IOUtils.close(inputStream);
        }
        return bitmap;
    }

    public static Bitmap getBitmapFormZip(String file, String name, int w, int h) {
        ZipFile zipFile = null;
        InputStream inputStream = null;
        Bitmap bitmap = null;
        try {
            zipFile = new ZipFile(file);
            ZipEntry entry = zipFile.getEntry(name);
            if (entry != null) {
                inputStream = zipFile.getInputStream(entry);
                BitmapFactory.Options options = getImageInfo(inputStream);
                inputStream.close();
                inputStream = zipFile.getInputStream(entry);
                bitmap = BitmapUtil.getBitmapByStream(inputStream, options.outWidth, options.outHeight, w, h);
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (Exception e) {

        } finally {
            IOUtils.close(inputStream);
            IOUtils.closeZip(zipFile);
        }
        return bitmap;
    }

    public static Bitmap getBitmapFromFile(String path, int w, int h) {
        InputStream inputStream = null;
        Bitmap bmp = null;
        try {
            if (!new File(path).exists()) {
                return null;
            }
            inputStream = new FileInputStream(path);
            BitmapFactory.Options options = getImageInfo(inputStream);
            inputStream.close();
            inputStream = new FileInputStream(path);
            bmp = BitmapUtil.getBitmapByStream(inputStream, options.outWidth, options.outHeight, w, h);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(inputStream);
        }
        return bmp;
    }

    /***
     * @param drawinput
     * @param srcW      图片的宽
     * @param srcH      图片的高
     * @param dstW      目标的宽
     * @param dstH      目标的宽
     * @return
     */
    public static Bitmap getBitmapByStream(InputStream drawinput, float srcW, float srcH, float dstW, float dstH) {
        Bitmap b = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            if (dstW * dstH >= MAX_BITMAP) {
                options.inSampleSize = (int) ((srcH / dstH + srcW / dstW) / 2.0f);
            }
            options.inPurgeable = true;
            options.inInputShareable = true;
            b = BitmapFactory.decodeStream(drawinput, null, options);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return b;
    }

    public static Bitmap getBitmapFromView(View view) {
        if (view == null) return null;
        view.setDrawingCacheEnabled(true);
        view.destroyDrawingCache();
        view.buildDrawingCache(false);
        return view.getDrawingCache();
    }

    /***
     * @param isAutoScale 是否根据当前缩放
     * @param w           0的时候不缩放
     * @param h           0的时候不缩放
     * @return
     */
    public static Bitmap getBitmapFromView(View view, boolean isAutoScale,
                                           int rWidth, int rHeight, int w, int h) {
        view.setDrawingCacheEnabled(true);
        view.destroyDrawingCache();
        if (w > 0 && h > 0)
            onchildLayout(view, w, h);
        else if (rWidth > 0 && rHeight > 0) {
            onchildLayout(view, rWidth, rHeight);
        }
        view.buildDrawingCache(false);
        return view.getDrawingCache(isAutoScale);
    }

    static void onchildLayout(View view, int w, int h) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(w,
                View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(h,
                View.MeasureSpec.EXACTLY);

        view.measure(widthSpec, heightSpec);
        view.layout(0, 0, w, h);
    }

    public static Bitmap drawBg4Bitmap(int color, Bitmap orginBitmap) {
        Paint paint = new Paint();
        paint.setColor(color);
        Bitmap bitmap = Bitmap.createBitmap(orginBitmap.getWidth(),
                orginBitmap.getHeight(), orginBitmap.getConfig());
        Canvas canvas = new Canvas(bitmap);
        canvas.drawRect(0, 0, orginBitmap.getWidth(), orginBitmap.getHeight(), paint);
        canvas.drawBitmap(orginBitmap, 0, 0, paint);
        return bitmap;
    }

}
