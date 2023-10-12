package com.ourygo.ygomobile.util

import android.graphics.Bitmap
import android.view.View
import androidx.palette.graphics.Palette
import androidx.palette.graphics.Palette.PaletteAsyncListener

/**
 * Create By feihua  On 2021/10/24
 */
object PaletteUtil {
    /**
     * 设置图片主色调
     *
     * @param bitmap
     * @return
     */
    fun setPaletteColor(bitmap: Bitmap?, view: View) {
        if (bitmap == null) {
            return
        }
        Palette.from(bitmap).maximumColorCount(10).generate(object : PaletteAsyncListener {
            override fun onGenerated(palette: Palette?) {
//                List<Palette.Swatch> list = palette.getSwatches();
//                int colorSize = 0;
//                Palette.Swatch maxSwatch = null;
//                for (int i = 0; i < list.size(); i++) {
//                    Palette.Swatch swatch = list.get(i);
//                    if (swatch != null) {
//                        int population = swatch.getPopulation();
//                        if (colorSize < population) {
//                            colorSize = population;
//                            maxSwatch = swatch;
//                        }
//                    }
//                }
                palette?.let {
                    val s = it.dominantSwatch //独特的一种
                    val s1 = it.vibrantSwatch //获取到充满活力的这种色调
                    val s2 = it.darkVibrantSwatch //获取充满活力的黑
                    val s3 = it.lightVibrantSwatch //获取充满活力的亮
                    val s4 = it.mutedSwatch //获取柔和的色调
                    val s5 = it.darkMutedSwatch //获取柔和的黑
                    val s6 = it.lightMutedSwatch //获取柔和的亮
                    var ss = s1
                    if (ss == null) ss = s
                    if (ss == null) ss = s2
                    if (ss == null) ss = s3
                    if (ss == null) ss = s4
                    if (ss == null) ss = s5
                    if (ss == null) ss = s6
                    ss?.let { color ->
                        view.setBackgroundColor(color.rgb)
                    }
                }
            }
        })
    }
}