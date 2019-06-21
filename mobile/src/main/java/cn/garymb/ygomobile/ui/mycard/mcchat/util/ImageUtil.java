package cn.garymb.ygomobile.ui.mycard.mcchat.util;

import android.content.Context;
import android.net.Uri;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import cn.garymb.ygomobile.lite.R;

public class ImageUtil {
    public static void tuxian(Context context, String url, final ImageView im) {
        if (url != null) {
            Glide.with(context)
                    .load(Uri.parse(url))
                    .asBitmap()
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .placeholder(R.drawable.avatar)
                    .into(im);
        }
    }

}
