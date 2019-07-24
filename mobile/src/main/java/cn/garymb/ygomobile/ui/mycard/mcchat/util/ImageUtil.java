package cn.garymb.ygomobile.ui.mycard.mcchat.util;

import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.lite.R;

public class ImageUtil {

    public static void setAvatar(Context context, String url, final ImageView im) {
        if (url != null) {
            Glide.with(context)
                    .load(Uri.parse(url))
                    .asBitmap()
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .placeholder(R.drawable.avatar)
                    .into(im);
        }
    }


    public static void setImage(Context context, String url, final ImageView im) {
        if (url != null) {
            Glide.with(context)
                    .load(url)
                    .asBitmap()
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .placeholder(R.drawable.unknown)
                    .into(im);
        }
    }

    public static void setGrayImage(int key, ImageView imageView) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        imageView.setColorFilter(filter);
    }

    public static void reImageColor(int key,ImageView imageView) {
        imageView.setColorFilter(null);
    }


}
