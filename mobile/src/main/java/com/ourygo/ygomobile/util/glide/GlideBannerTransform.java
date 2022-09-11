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

public class GlideBannerTransform extends BitmapTransformation
 {

     public GlideBannerTransform() {
         super();
     }

     @Override
     protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) {
//         return TransformationUtils.roundedCorners(pool,toTransform,radius);
         int height=outHeight;
         int width=height*3;
         Bitmap bitmap = TransformationUtils.centerCrop(pool, toTransform,width,height);
         return bitmap;
     }


     public String getId() {
         return getClass().getName() + hashCode();
     }



//     @Override
//     public int hashCode() {
//         return radius;
//     }
//
//     @Override
//     public boolean equals(@Nullable Object obj) {
//         if (obj==null)
//             return false;
//         return hashCode()==((GlideBannerTransform)obj).hashCode();
//     }


     @Override
     public int hashCode() {
         return super.hashCode();
     }

     @Override
     public boolean equals(@Nullable Object obj) {
         return super.equals(obj);
     }

     @Override
     public void updateDiskCacheKey(MessageDigest messageDigest) {
         messageDigest.update(getId().getBytes());
     }
}
