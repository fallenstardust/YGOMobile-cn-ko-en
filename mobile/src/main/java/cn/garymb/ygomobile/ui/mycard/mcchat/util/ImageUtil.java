package cn.garymb.ygomobile.ui.mycard.mcchat.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;
import com.bumptech.glide.request.transition.Transition;
import com.ourygo.ygomobile.util.PaletteUtil;
import com.ourygo.ygomobile.util.glide.GlideRoundTransform;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

public class ImageUtil {

    //    public static final RequestBuilder options=new RequestBuilder().transform(DrawableTransitionOptions.withCrossFade(500));
    public static final DrawableCrossFadeFactory drawableCrossFadeFactory = new DrawableCrossFadeFactory.Builder(50).setCrossFadeEnabled(true).build();

    public static void setAvatar(Context context, String url, final ImageView im) {
        if (url != null) {
            GlideCompat.with(context)
                    .load(Uri.parse(url))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .placeholder(R.drawable.avatar)
                    .into(im);
        }
    }


    public static void setImage(Context context, String url, final ImageView im) {
        if (url != null) {
            GlideCompat.with(context)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .placeholder(R.drawable.ic_image_load)
                    .into(im);
        }
    }


    public static void setImageAndBackground(Context context, String url, final ImageView im) {
        if (url != null) {
            Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .transition(BitmapTransitionOptions.withCrossFade(3000))

                    .transform(new GlideRoundTransform(context))

                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            im.setImageBitmap(resource);
                            PaletteUtil.setPaletteColor(resource, im);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {

                        }
                    });
        }
    }


    public static void setGrayImage(int key, ImageView imageView) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        imageView.setColorFilter(filter);
    }

    public static void reImageColor(int key, ImageView imageView) {
        imageView.setColorFilter(null);
    }


}
