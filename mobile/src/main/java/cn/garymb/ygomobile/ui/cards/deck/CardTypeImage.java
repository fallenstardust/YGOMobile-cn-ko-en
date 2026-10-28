package cn.garymb.ygomobile.ui.cards.deck;

import static cn.garymb.ygomobile.Constants.ASSET_CARDTYPE_PNG;

import android.content.Context;
import android.graphics.Bitmap;

import cn.garymb.ygomobile.utils.BitmapUtil;

/**
 * 卡组种类统计图标：加载 textures/cardtype.png 并裁切为7个种类分块，
 * 裁切区域对齐C++ drawing.cpp 中 imageManager.tCardType 的 recti：
 * 怪兽(0,0) 魔法(1,0) 陷阱(2,0) 融合(0,1) 同调(1,1) XYZ(2,1) 连接(0,2)。
 * 贴图按3×3网格用分数坐标裁切，与ImageTop一致且与图片分辨率无关。
 */
public class CardTypeImage {
    public final Bitmap monster;
    public final Bitmap spell;
    public final Bitmap trap;
    public final Bitmap fusion;
    public final Bitmap synchro;
    public final Bitmap xyz;
    public final Bitmap link;

    public CardTypeImage(Context context) {
        this(BitmapUtil.getBitmapFormAssets(context, ASSET_CARDTYPE_PNG, 0, 0));
    }

    public CardTypeImage(Bitmap img) {
        if (img != null) {
            int cellW = img.getWidth() / 3;
            int cellH = img.getHeight() / 3;
            monster = Bitmap.createBitmap(img, 0, 0, cellW, cellH);
            spell = Bitmap.createBitmap(img, cellW, 0, cellW, cellH);
            trap = Bitmap.createBitmap(img, cellW * 2, 0, cellW, cellH);
            fusion = Bitmap.createBitmap(img, 0, cellH, cellW, cellH);
            synchro = Bitmap.createBitmap(img, cellW, cellH, cellW, cellH);
            xyz = Bitmap.createBitmap(img, cellW * 2, cellH, cellW, cellH);
            link = Bitmap.createBitmap(img, 0, cellH * 2, cellW, cellH);
        } else {
            monster = null;
            spell = null;
            trap = null;
            fusion = null;
            synchro = null;
            xyz = null;
            link = null;
        }
        BitmapUtil.destroy(img);
    }

    public void clear() {
        BitmapUtil.destroy(monster);
        BitmapUtil.destroy(spell);
        BitmapUtil.destroy(trap);
        BitmapUtil.destroy(fusion);
        BitmapUtil.destroy(synchro);
        BitmapUtil.destroy(xyz);
        BitmapUtil.destroy(link);
    }
}