package cn.garymb.ygomobile.ui.cards.deck;

import static cn.garymb.ygomobile.Constants.ASSET_ATTR_RACE;
import static cn.garymb.ygomobile.Constants.ASSET_LIMIT_PNG;

import android.content.Context;
import android.graphics.Bitmap;

import cn.garymb.ygomobile.utils.BitmapUtil;

public class ImageTop {
    public final Bitmap forbidden;
    public final Bitmap limit;
    public final Bitmap semiLimit;
    public final Bitmap credits;
    public final Bitmap otOcg;
    public final Bitmap otTcg;
    public final Bitmap otSc;

    public ImageTop(Context context) {
        this(BitmapUtil.getBitmapFormAssets(context, ASSET_LIMIT_PNG, 0, 0),
                BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "ot_ocg.png", 0, 0),
                BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "ot_tcg.png", 0, 0),
                BitmapUtil.getBitmapFormAssets(context, ASSET_ATTR_RACE + "ot_sc.png", 0, 0));
    }

    public ImageTop(Bitmap img) {
        this(img, null, null, null);
    }

    public ImageTop(Bitmap img, Bitmap otOcg, Bitmap otTcg, Bitmap otSc) {
        if (img != null) {
            int width = img.getWidth();
            int height = img.getHeight();
            forbidden = Bitmap.createBitmap(img, 0, 0, width / 2, height / 2);
            limit = Bitmap.createBitmap(img, width / 2, 0, width / 2, height / 2);
            semiLimit = Bitmap.createBitmap(img, width / 2, 0, width / 2, height / 2);
            credits = Bitmap.createBitmap(img, 0, height / 2, width / 2, height / 2);
        } else {
            forbidden = null;
            limit = null;
            semiLimit = null;
            credits = null;
        }
        this.otOcg = otOcg;
        this.otTcg = otTcg;
        this.otSc = otSc;
        BitmapUtil.destroy(img);
    }

    public void clear() {
        BitmapUtil.destroy(forbidden);
        BitmapUtil.destroy(limit);
        BitmapUtil.destroy(semiLimit);
        BitmapUtil.destroy(credits);
        BitmapUtil.destroy(otOcg);
        BitmapUtil.destroy(otTcg);
        BitmapUtil.destroy(otSc);
    }
}
