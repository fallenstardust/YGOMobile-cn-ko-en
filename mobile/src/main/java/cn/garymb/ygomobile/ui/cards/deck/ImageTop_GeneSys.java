package cn.garymb.ygomobile.ui.cards.deck;

import static cn.garymb.ygomobile.Constants.ASSET_GENESYS_LIMIT_PNG;


import android.content.Context;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.utils.BitmapUtil;

public class ImageTop_GeneSys {
    public final List<Bitmap> geneSysLimit;

    public ImageTop_GeneSys(Context context) {
        this(BitmapUtil.getBitmapFormAssets(context, ASSET_GENESYS_LIMIT_PNG, 0, 0));

    }

    public ImageTop_GeneSys(Bitmap img) {
        if (img != null) {

            geneSysLimit = new ArrayList<>();
            int width = img.getWidth();
            int height = img.getHeight();
            int itemWidth = width / 10;    // 每个小图标的宽度
            int itemHeight = height / 10;  // 每个小图标的高度

            // 按10行10列切割图片
            for (int row = 0; row < 10; row++) {
                for (int col = 0; col < 10; col++) {
                    int x = col * itemWidth;      // 当前列的起始x坐标
                    int y = row * itemHeight;     // 当前行的起始y坐标
                    Bitmap item = Bitmap.createBitmap(img, x, y, itemWidth, itemHeight);
                    geneSysLimit.add(item);//注意索引从0开始，图标号从1开始
                }
            }
        } else {
            geneSysLimit = null;
        }
        BitmapUtil.destroy(img);
    }


    public void clear() {
        for (Bitmap bitmap : geneSysLimit) {
            BitmapUtil.destroy(bitmap);
        }
    }
}
