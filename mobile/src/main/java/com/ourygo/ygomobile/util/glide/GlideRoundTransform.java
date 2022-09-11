package com.ourygo.ygomobile.util.glide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.Nullable;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;
import com.ourygo.ygomobile.util.OYUtil;

import java.security.MessageDigest;

import cn.garymb.ygomobile.lite.R;

public class GlideRoundTransform extends BitmapTransformation
 {
     private static int radius = 0;

     public GlideRoundTransform(Context context) {
         this(context, OYUtil.dp(R.dimen.corner_radius));
     }

     public GlideRoundTransform(Context context, int dp) {
         super();
         radius=dp;
//         this.radius = Resources.getSystem().getDisplayMetrics().density * dp;
     }

     @Override
     protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) {
//         return TransformationUtils.roundedCorners(pool,toTransform,radius);
         int height=600;
         int width=(int)(height*(((double)outWidth)/outHeight));
         Bitmap bitmap = TransformationUtils.centerCrop(pool, toTransform,width,height);
         return roundCrop(pool, bitmap);
     }

     private static Bitmap roundCrop(BitmapPool pool, Bitmap source) {
         if (source == null) return null;

         Bitmap result = pool.get(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
         if (result == null) {
             result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
         }

         Canvas canvas = new Canvas(result);
         Paint paint = new Paint();
         paint.setShader(new BitmapShader(source, BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP));
         paint.setAntiAlias(true);
         RectF rectF = new RectF(0f, 0f, source.getWidth(), source.getHeight());
         canvas.drawRoundRect(rectF, radius, radius, paint);
         return result;
     }

     public String getId() {
         return getClass().getName() + Math.round(radius);
     }



     @Override
     public int hashCode() {
         return radius;
     }

     @Override
     public boolean equals(@Nullable Object obj) {
         if (obj==null)
             return false;
         return hashCode()==((GlideRoundTransform)obj).hashCode();
     }

     @Override
     public void updateDiskCacheKey(MessageDigest messageDigest) {
         messageDigest.update(getId().getBytes());
     }
}
